package com.group.exam.learn.service;

import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外链文章抓取与正文提取（图文资料"外链解析"）。
 * 抓取成功即把正文 HTML 固化到资料 content 字段，以后链接失效不影响已入库内容。
 */
@Service
public class UrlContentService {

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_BODY_BYTES = 3 * 1024 * 1024;
    /** 常见正文容器选择器，按命中文本量最大的取 */
    private static final List<String> CONTENT_SELECTORS = List.of(
            "article", ".article-content", ".article_content", ".article-body", ".article",
            "#article", ".post-content", ".entry-content", ".detail-content", ".detail",
            ".news-content", ".news_content", ".article-detail", ".text-content",
            ".contentMain", "#zoom",
            ".content", "#content", ".main-content", "main", ".TRS_Editor", ".Custom_UnionStyle");

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
        // 转成管理端富文本(wangEditor)能完整保留的形态，避免解析回填时丢失字体/字号/行距
        normalizeForEditor(content);

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

    private Element pickMainContent(Document doc) {
        Element best = null;
        int bestLen = 0;
        for (String sel : CONTENT_SELECTORS) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                int len = el.text().length();
                if (len > bestLen) {
                    best = el;
                    bestLen = len;
                }
            }
        }
        return best != null ? best : doc.body();
    }

    /** 清除脚本、样式、交互控件与行内事件；行内 style 走白名单净化，保留缩进/对齐/行距等排版 */
    private void clean(Element root) {
        root.select("script, style, iframe, frame, frameset, form, input, button, select, textarea, "
                + "noscript, link, meta, object, embed, video, audio, canvas, svg").remove();
        Elements all = root.getAllElements();
        for (Element el : all) {
            el.attributes().asList().stream()
                    .map(attr -> attr.getKey())
                    .filter(key -> key.startsWith("on"))
                    .forEach(key -> el.removeAttr(key));
            String style = el.attr("style");
            if (!style.isEmpty()) {
                String safe = sanitizeStyle(style);
                if (safe.isEmpty()) {
                    el.removeAttr("style");
                } else {
                    el.attr("style", safe);
                }
            }
        }
    }

    /** 允许保留的排版相关 CSS 属性（分段间距、换行行距、首行缩进、对齐、字号字重等） */
    private static final java.util.Set<String> STYLE_WHITELIST = java.util.Set.of(
            "text-indent", "text-align", "line-height", "letter-spacing",
            "font-size", "font-weight", "font-style", "font-family",
            "color", "background-color", "text-decoration", "text-decoration-line",
            "margin", "margin-top", "margin-bottom", "margin-left", "margin-right",
            "padding", "padding-top", "padding-bottom", "padding-left", "padding-right");

    /** 行内样式净化：仅保留白名单属性，剔除 url()/expression 等可注入项 */
    private String sanitizeStyle(String style) {
        StringBuilder sb = new StringBuilder();
        for (String decl : style.split(";")) {
            int colon = decl.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String prop = decl.substring(0, colon).trim().toLowerCase();
            String val = decl.substring(colon + 1).trim();
            if (!STYLE_WHITELIST.contains(prop) || val.isEmpty()) {
                continue;
            }
            String lv = val.toLowerCase();
            if (lv.contains("url(") || lv.contains("expression") || lv.contains("\"") || lv.contains("'")) {
                continue;
            }
            sb.append(prop).append(':').append(val).append(';');
        }
        return sb.toString();
    }

    /** 需要把字体样式下沉到内联 span 的块级标签 */
    private static final java.util.Set<String> BLOCK_TAGS = java.util.Set.of(
            "p", "div", "section", "article", "li", "td", "th", "blockquote",
            "h1", "h2", "h3", "h4", "h5", "h6");

    /**
     * 适配管理端富文本(wangEditor)的解析规则：
     * 1. 块级元素上的 font-family/font-size 会被编辑器丢弃，下沉为包裹子内容的内联 span；
     * 2. 编辑器行距仅接受无单位倍数，把 px/em/% 行距按同元素字号换算成倍数。
     */
    private void normalizeForEditor(Element root) {
        for (Element el : root.getAllElements()) {
            String style = el.attr("style");
            if (style.isEmpty()) {
                continue;
            }
            Map<String, String> props = parseStyle(style);
            if (props.isEmpty()) {
                continue;
            }
            // 行距 px/em/% → 无单位倍数（换算不了时保留原值）
            String lh = props.get("line-height");
            if (lh != null) {
                String ratio = toLineHeightRatio(lh, props.get("font-size"));
                if (ratio != null) {
                    props.put("line-height", ratio);
                }
            }
            // 块级元素：字体/字号下沉到内联 span（内联元素上编辑器本身就能解析，无需处理）
            if (BLOCK_TAGS.contains(el.tagName()) && !el.childNodes().isEmpty()) {
                String ff = props.remove("font-family");
                String fs = props.remove("font-size");
                if (ff != null || fs != null) {
                    String spanStyle = (ff != null ? "font-family:" + ff + ";" : "")
                            + (fs != null ? "font-size:" + fs + ";" : "");
                    // 仅包裹文本与内联格式节点：图片等被 span 包住会被编辑器当作文本样式丢弃
                    Element span = null;
                    for (org.jsoup.nodes.Node child : new java.util.ArrayList<>(el.childNodes())) {
                        if (isWrappableInline(child)) {
                            if (span == null) {
                                span = new Element("span").attr("style", spanStyle);
                            }
                            span.appendChild(child);
                        } else if (span != null) {
                            child.before(span);
                            span = null;
                        }
                    }
                    if (span != null) {
                        el.appendChild(span);
                    }
                }
            }
            el.attr("style", buildStyle(props));
        }
    }

    /** 可被字体 span 包裹的节点：文本或内联格式元素；图片/表格等被 span 包住会被编辑器丢弃 */
    private boolean isWrappableInline(org.jsoup.nodes.Node node) {
        if (node instanceof org.jsoup.nodes.TextNode) {
            return true;
        }
        if (node instanceof Element) {
            String tag = ((Element) node).tagName();
            return !java.util.Set.of("img", "table", "video", "audio", "iframe", "object", "embed").contains(tag);
        }
        return false;
    }

    private Map<String, String> parseStyle(String style) {
        Map<String, String> props = new LinkedHashMap<>();
        for (String decl : style.split(";")) {
            int colon = decl.indexOf(':');
            if (colon > 0) {
                props.put(decl.substring(0, colon).trim(), decl.substring(colon + 1).trim());
            }
        }
        return props;
    }

    private String buildStyle(Map<String, String> props) {
        StringBuilder sb = new StringBuilder();
        props.forEach((k, v) -> sb.append(k).append(':').append(v).append(';'));
        return sb.toString();
    }

    /** 行距换算成无单位倍数：px 需结合同元素字号，em 直接去单位，% 除以 100；无法换算返回 null */
    private String toLineHeightRatio(String lh, String fontSize) {
        try {
            if (lh.endsWith("px")) {
                if (fontSize == null || !fontSize.endsWith("px")) {
                    return null;
                }
                double lhPx = Double.parseDouble(lh.substring(0, lh.length() - 2).trim());
                double fsPx = Double.parseDouble(fontSize.substring(0, fontSize.length() - 2).trim());
                return fsPx > 0 ? formatRatio(lhPx / fsPx) : null;
            }
            if (lh.endsWith("em")) {
                return formatRatio(Double.parseDouble(lh.substring(0, lh.length() - 2).trim()));
            }
            if (lh.endsWith("%")) {
                return formatRatio(Double.parseDouble(lh.substring(0, lh.length() - 1).trim()) / 100);
            }
        } catch (NumberFormatException ignore) {
            return null;
        }
        return null;
    }

    private String formatRatio(double r) {
        return java.math.BigDecimal.valueOf(r).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private void absolutize(Element root, String tag, String attr) {        for (Element el : root.select(tag + "[" + attr + "]")) {
            String abs = el.absUrl(attr);
            if (!abs.isEmpty()) {
                el.attr(attr, abs);
            }
        }
    }
}
