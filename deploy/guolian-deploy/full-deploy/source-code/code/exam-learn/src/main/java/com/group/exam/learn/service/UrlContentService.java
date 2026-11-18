package com.group.exam.learn.service;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外链文章抓取与正文提取（图文资料"外链解析"）。
 * 抓取成功即把正文 HTML 固化到资料 content 字段，以后链接失效不影响已入库内容。
 */
@Service
public class UrlContentService {

    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_BODY_BYTES = 3 * 1024 * 1024;
    /** 常见正文容器选择器，按命中文本量最大的取 */
    private static final List<String> CONTENT_SELECTORS = List.of(
            "article", ".article-content", ".article_content", ".article-body", ".article",
            "#article", ".post-content", ".entry-content", ".detail-content", ".content",
            "#content", ".main-content", "main", ".TRS_Editor", ".Custom_UnionStyle");

    /** 允许保留的排版标签（除此之外的标签一律解包，仅保留其子内容） */
    private static final java.util.Set<String> KEEP_TAGS = java.util.Set.of(
            "p", "br", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote",
            "table", "thead", "tbody", "tr", "td", "th", "img", "a", "strong", "b",
            "em", "i", "u", "s", "strike", "span", "hr", "figure", "figcaption", "section");
    /** 块级标签（div 含块级子元素时解包而非转 p） */
    private static final java.util.Set<String> BLOCK_TAGS = java.util.Set.of(
            "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote",
            "table", "section", "article", "header", "footer", "figure", "hr");
    /** 允许保留的样式属性（其余样式一律剥离） */
    private static final java.util.Set<String> KEEP_STYLES = java.util.Set.of(
            "text-align", "line-height", "color", "background-color",
            "font-weight", "font-style", "text-decoration", "text-decoration-line", "font-size");

    public Map<String, Object> fetch(String url) {
        URI uri = parseAndGuard(url);
        Document doc;
        try {
            doc = Jsoup.connect(uri.toString())
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .ignoreContentType(false)
                    .get();
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "链接抓取失败：" + e.getClass().getSimpleName() + "，请检查链接是否可公开访问");
        }

        Element content = pickMainContent(doc);
        clean(content);
        // 相对链接/图片地址转绝对地址，保证入库后可独立展示
        absolutize(content, "img", "src");
        absolutize(content, "a", "href");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", doc.title() == null ? "" : doc.title().trim());
        result.put("contentHtml", content.html());
        result.put("textLength", content.text().length());
        return result;
    }

    /** 协议与白名单校验，阻止指向内网/本机的地址（SSRF 防护） */
    private URI parseAndGuard(String url) {
        if (url == null || url.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "url 不能为空");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "url 格式不正确");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 http/https 链接");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "url 缺少主机名");
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                        || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "不允许抓取内网或本机地址");
                }
            }
        } catch (BizException be) {
            throw be;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "域名解析失败：" + host);
        }
        return uri;
    }

    /** 正文容器评分选取：含长文本段落数优先，文本长度次之，链接文本罚分（排除面包屑/导航外层容器） */
    private Element pickMainContent(Document doc) {
        Element best = null;
        long bestScore = -1;
        for (String sel : CONTENT_SELECTORS) {
            for (Element el : doc.select(sel)) {
                if (isExcludedContainer(el)) {
                    continue;
                }
                long pCount = 0;
                for (Element p : el.select("p")) {
                    if (p.text().trim().length() > 30) {
                        pCount++;
                    }
                }
                long linkTextLen = 0;
                for (Element a : el.select("a")) {
                    linkTextLen += a.text().length();
                }
                long score = pCount * 1000 + el.text().length() - linkTextLen * 3;
                if (score > bestScore) {
                    bestScore = score;
                    best = el;
                }
            }
        }
        return best != null ? best : doc.body();
    }

    private boolean isExcludedContainer(Element el) {
        String marker = (el.id() + " " + el.className()).toLowerCase();
        for (String bad : List.of("header", "footer", "nav", "sidebar", "menu", "banner", "topbar", "crumb")) {
            if (marker.contains(bad)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 规范化正文 HTML：删除脚本与交互控件；非白名单标签解包；纯文本 div 转 p；
     * 属性仅保留白名单样式（img 留 src/alt，a 留 href）；text-indent 缩进物化为段首两个全角空格，
     * 保证经任意富文本编辑器归一化后分段、换行、首行缩进仍保留。
     */
    private void clean(Element root) {
        root.select("script, style, iframe, frame, frameset, form, input, button, select, textarea, "
                + "noscript, link, meta, object, embed, video, audio, canvas, svg").remove();
        List<Element> all = new ArrayList<>(root.getAllElements());
        for (Element el : all) {
            if (el == root) {
                continue;
            }
            String tag = el.tagName().toLowerCase();
            if ("div".equals(tag)) {
                handleDiv(el);
            } else if (!KEEP_TAGS.contains(tag)) {
                el.unwrap();
            } else {
                scrubAttrs(el);
            }
        }
    }

    /** div 处理：含块级子元素或为空则解包；纯文本 div 转为 p 段落 */
    private void handleDiv(Element el) {
        boolean hasBlockChild = false;
        for (Element child : el.children()) {
            if (BLOCK_TAGS.contains(child.tagName().toLowerCase())) {
                hasBlockChild = true;
                break;
            }
        }
        if (hasBlockChild || el.text().isBlank()) {
            el.unwrap();
        } else {
            el.tagName("p");
            scrubAttrs(el);
        }
    }

    /** 清理解包后保留标签的属性：剥离 class、id、行内事件与 data-* 等，仅保留白名单样式与必要的链接属性 */
    private void scrubAttrs(Element el) {
        String tag = el.tagName().toLowerCase();
        String src = el.attr("src");
        String alt = el.attr("alt");
        String href = el.attr("href");
        String style = el.attr("style");
        el.clearAttributes();
        if ("img".equals(tag)) {
            if (!src.isEmpty()) {
                el.attr("src", src);
            }
            if (!alt.isEmpty()) {
                el.attr("alt", alt);
            }
        } else if ("a".equals(tag)) {
            if (!href.isEmpty()) {
                el.attr("href", href);
                el.attr("target", "_blank");
            }
        }
        String kept = filterStyle(el, style);
        if (!kept.isEmpty()) {
            el.attr("style", kept);
        }
    }

    /** 样式白名单过滤；text-indent>=1em 的段落段首插入两个全角空格后丢弃该样式 */
    private String filterStyle(Element el, String style) {
        if (style == null || style.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String decl : style.split(";")) {
            int idx = decl.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = decl.substring(0, idx).trim().toLowerCase();
            String val = decl.substring(idx + 1).trim();
            if (val.toLowerCase().endsWith("!important")) {
                val = val.substring(0, val.length() - "!important".length()).trim();
            }
            if ("text-indent".equals(key)) {
                if (indentEm(val) >= 1.0 && !el.text().startsWith("　")) {
                    el.prependChild(new TextNode("　　"));
                }
                continue;
            }
            if (KEEP_STYLES.contains(key)) {
                sb.append(key).append(':').append(val).append(';');
            }
        }
        return sb.toString();
    }

    /** text-indent 值换算为 em（px 按 16px=1em 折算），无法解析返回 0 */
    private double indentEm(String val) {
        try {
            String v = val.toLowerCase().trim();
            if (v.endsWith("em")) {
                return Double.parseDouble(v.substring(0, v.length() - 2).trim());
            }
            if (v.endsWith("px")) {
                return Double.parseDouble(v.substring(0, v.length() - 2).trim()) / 16.0;
            }
        } catch (NumberFormatException ignore) {
            // 无法解析视为无缩进
        }
        return 0;
    }

    private void absolutize(Element root, String tag, String attr) {
        for (Element el : root.select(tag + "[" + attr + "]")) {
            String abs = el.absUrl(attr);
            if (!abs.isEmpty()) {
                el.attr(attr, abs);
            }
        }
    }
}
