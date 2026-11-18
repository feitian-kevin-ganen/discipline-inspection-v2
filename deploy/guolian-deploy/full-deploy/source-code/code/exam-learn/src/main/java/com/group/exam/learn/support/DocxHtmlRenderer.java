package com.group.exam.learn.support;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * DOCX 转 HTML 渲染器（纯 Java，无原生依赖）。
 * 学习端 Word 资料在线预览：解析 word/document.xml 的段落/表格/图片/分页标记，
 * 输出带内联样式的 HTML（图片转 base64 data URI），由浏览器排版显示，
 * 避免服务端 AWT 渲染依赖服务器中文字体（测试机不一定安装）。
 */
public final class DocxHtmlRenderer {

    /** 渲染结果：HTML 片段 + 估算总页数（显式分页符 + Word 上次渲染分页标记 + 1） */
    public record DocxHtml(String html, int pageCount) {}

    private static final long MAX_ENTRY_BYTES = 100L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 300L * 1024 * 1024;

    private DocxHtmlRenderer() {
    }

    public static DocxHtml render(byte[] docxBytes) throws java.io.IOException {
        Map<String, byte[]> entries = readZip(docxBytes);
        byte[] documentXml = entries.get("word/document.xml");
        if (documentXml == null) {
            throw new java.io.IOException("DOCX 结构不完整：缺少 word/document.xml");
        }
        Map<String, String> rels = parseRels(entries.get("word/_rels/document.xml.rels"));

        Document doc = parseXml(documentXml);
        Element body = firstChildElement(doc.getDocumentElement(), "body");
        if (body == null) {
            throw new java.io.IOException("DOCX 结构不完整：缺少 w:body");
        }

        RenderContext ctx = new RenderContext(entries, rels);
        StringBuilder html = new StringBuilder(64 * 1024);
        html.append("<div class=\"docx-doc\" style=\"font-family:SimSun,'宋体',serif;font-size:16px;line-height:1.9;color:#333;\">");
        for (Element child : childElements(body)) {
            String tag = child.getLocalName() == null ? child.getNodeName() : child.getLocalName();
            switch (tag) {
                case "p" -> html.append(renderParagraph(child, ctx));
                case "tbl" -> html.append(renderTable(child, ctx));
                default -> {
                    // sectPr 等其他节点忽略
                }
            }
        }
        html.append("</div>");
        // 页数：优先 Word 渲染分页/显式分页标记；无标记（如程序生成文件）按正文篇幅估算（约 700 字/页）
        int pageCount = ctx.pageBreaks > 0
                ? ctx.pageBreaks + 1
                : Math.max(1, (int) Math.ceil(ctx.textChars / 700.0));
        return new DocxHtml(html.toString(), pageCount);
    }

    // ---------------- zip / xml 基础 ----------------

    private static Map<String, byte[]> readZip(byte[] bytes) throws java.io.IOException {
        Map<String, byte[]> entries = new HashMap<>();
        long total = 0;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName();
                // 只保留渲染需要的部件
                if (!name.equals("word/document.xml")
                        && !name.startsWith("word/media/")
                        && !name.equals("word/_rels/document.xml.rels")) {
                    continue;
                }
                byte[] data = zin.readNBytes((int) Math.min(MAX_ENTRY_BYTES, Integer.MAX_VALUE));
                total += data.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new java.io.IOException("DOCX 文件过大，拒绝渲染");
                }
                entries.put(name, data);
            }
        }
        return entries;
    }

    private static Document parseXml(byte[] xml) throws java.io.IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // 关闭 DTD / 外部实体，防 XXE
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception ex) {
            throw new java.io.IOException("DOCX XML 解析失败: " + ex.getMessage(), ex);
        }
    }

    /** document.xml.rels：rId → Target（图片相对 word/ 的路径） */
    private static Map<String, String> parseRels(byte[] relsXml) throws java.io.IOException {
        Map<String, String> map = new HashMap<>();
        if (relsXml == null) {
            return map;
        }
        Document doc = parseXml(relsXml);
        for (Element rel : childElements(doc.getDocumentElement())) {
            if (!"Relationship".equals(localName(rel))) {
                continue;
            }
            String id = rel.getAttribute("Id");
            String target = rel.getAttribute("Target");
            if (!id.isEmpty() && !target.isEmpty() && !target.startsWith("http")) {
                // Target 相对 word/ 目录，统一规整为 word/ 前缀
                String normalized = target.startsWith("/") ? target.substring(1) : "word/" + target;
                map.put(id, normalized.replace("//", "/"));
            }
        }
        return map;
    }

    // ---------------- 渲染上下文 ----------------

    private static final class RenderContext {
        final Map<String, byte[]> entries;
        final Map<String, String> rels;
        int pageBreaks = 0;
        /** 可见文本字符数（用于无分页标记时的页数估算） */
        int textChars = 0;

        RenderContext(Map<String, byte[]> entries, Map<String, String> rels) {
            this.entries = entries;
            this.rels = rels;
        }

        /** 解析 rId 对应的图片为 data URI；不支持的格式返回 null */
        String imageDataUri(String rId) {
            String path = rels.get(rId);
            if (path == null) {
                return null;
            }
            byte[] data = entries.get(path);
            if (data == null) {
                return null;
            }
            String mime = switch (extOf(path)) {
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "bmp" -> "image/bmp";
                case "webp" -> "image/webp";
                default -> null;
            };
            if (mime == null) {
                return null;
            }
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data);
        }
    }

    // ---------------- 段落 ----------------

    private static String renderParagraph(Element p, RenderContext ctx) {
        Element pPr = firstChildElement(p, "pPr");
        ParagraphStyle style = paragraphStyle(pPr);

        StringBuilder inner = new StringBuilder();
        for (Element child : childElements(p)) {
            String tag = localName(child);
            switch (tag) {
                case "r" -> inner.append(renderRun(child, ctx));
                case "hyperlink" -> {
                    StringBuilder linkText = new StringBuilder();
                    for (Element r : childElements(child, "r")) {
                        linkText.append(runText(r, ctx));
                    }
                    inner.append("<span style=\"color:#2d5a8a;text-decoration:underline;\">")
                            .append(escape(linkText.toString())).append("</span>");
                }
                default -> {
                    // pPr/bookmark 等忽略
                }
            }
        }
        String text = inner.toString();
        if (text.isEmpty() && !style.hasBlockStyle()) {
            return "<p style=\"margin:0;height:1em;\"></p>";
        }
        String prefix = style.bullet ? "<span style=\"margin-right:6px;\">•</span>" : "";
        return "<p style=\"" + style.css + "\">" + prefix + text + "</p>";
    }

    private static final class ParagraphStyle {
        String css = "";
        boolean bullet = false;

        boolean hasBlockStyle() {
            return !css.isEmpty() || bullet;
        }
    }

    private static ParagraphStyle paragraphStyle(Element pPr) {
        ParagraphStyle out = new ParagraphStyle();
        if (pPr == null) {
            return out;
        }
        StringBuilder css = new StringBuilder();
        css.append("margin:0 0 6px 0;");

        int headingLevel = -1;
        Element pStyle = firstChildElement(pPr, "pStyle");
        if (pStyle != null) {
            String val = attrVal(pStyle);
            if (val != null && val.toLowerCase().startsWith("heading")) {
                headingLevel = parseInt(val.substring(7), -1);
            }
        }
        Element outline = firstChildElement(pPr, "outlineLvl");
        if (outline != null) {
            headingLevel = parseInt(attrVal(outline), headingLevel);
        }
        if (headingLevel >= 0 && headingLevel <= 2) {
            int[] sizes = {26, 22, 18};
            css.append("font-size:").append(sizes[Math.min(headingLevel, 2)]).append("px;")
                    .append("font-weight:700;line-height:1.5;margin:14px 0 8px 0;");
        }

        Element jc = firstChildElement(pPr, "jc");
        if (jc != null) {
            String v = attrVal(jc);
            if ("center".equals(v)) {
                css.append("text-align:center;");
            } else if ("right".equals(v)) {
                css.append("text-align:right;");
            } else if ("both".equals(v)) {
                css.append("text-align:justify;");
            }
        }

        Element ind = firstChildElement(pPr, "ind");
        if (ind != null) {
            String firstChars = ind.getAttributeNS("*", "firstLineChars");
            if (firstChars != null && !firstChars.isEmpty()) {
                int v = parseInt(firstChars, 0);
                if (v > 0) {
                    css.append("text-indent:").append(v / 100.0).append("em;");
                }
            } else {
                String firstLine = ind.getAttributeNS("*", "firstLine");
                if (firstLine != null && !firstLine.isEmpty()) {
                    int v = parseInt(firstLine, 0);
                    if (v > 0) {
                        css.append("text-indent:").append(Math.round(v / 20.0 * 96 / 72)).append("px;");
                    }
                }
            }
            String leftChars = ind.getAttributeNS("*", "leftChars");
            if (leftChars != null && !leftLeftCharsEmpty(leftChars)) {
                int v = parseInt(leftChars, 0);
                if (v > 0) {
                    css.append("padding-left:").append(v / 100.0).append("em;");
                }
            }
        }

        Element spacing = firstChildElement(pPr, "spacing");
        if (spacing != null) {
            String line = attrVal(spacing, "line");
            String lineRule = attrVal(spacing, "lineRule");
            if (line != null && !line.isEmpty() && !"0".equals(line)) {
                int v = parseInt(line, 240);
                if ("auto".equals(lineRule) || lineRule == null || lineRule.isEmpty()) {
                    // 240 = 单倍行距
                    double lh = Math.max(1.0, v / 240.0);
                    css.append("line-height:").append(lh).append(";");
                } else {
                    // exact / atLeast：twips → px（1pt=1/20 twip，1px=96/72pt）
                    css.append("line-height:").append(Math.max(12, Math.round(v / 20.0 * 96 / 72))).append("px;");
                }
            }
            String after = attrVal(spacing, "after");
            if (after != null && !after.isEmpty() && parseInt(after, 0) > 0) {
                css.append("margin-bottom:").append(Math.max(2, Math.round(parseInt(after, 0) / 20.0 * 96 / 72))).append("px;");
            }
        }

        if (firstChildElement(pPr, "numPr") != null) {
            out.bullet = true;
        }

        out.css = css.toString();
        return out;
    }

    private static boolean leftLeftCharsEmpty(String v) {
        return v.isEmpty() || "0".equals(v);
    }

    // ---------------- 行内 run ----------------

    private static String renderRun(Element r, RenderContext ctx) {
        Element rPr = firstChildElement(r, "rPr");
        StringBuilder css = new StringBuilder();
        if (rPr != null) {
            if (onOff(firstChildElement(rPr, "b"))) {
                css.append("font-weight:700;");
            }
            if (onOff(firstChildElement(rPr, "i"))) {
                css.append("font-style:italic;");
            }
            Element u = firstChildElement(rPr, "u");
            if (u != null) {
                String v = attrVal(u);
                if (v == null || (!"none".equals(v) && !"0".equals(v))) {
                    css.append("text-decoration:underline;");
                }
            }
            Element sz = firstChildElement(rPr, "sz");
            if (sz != null) {
                int halfPt = parseInt(attrVal(sz), 0);
                if (halfPt > 0) {
                    css.append("font-size:").append(halfPt / 2.0).append("pt;");
                }
            }
            Element color = firstChildElement(rPr, "color");
            if (color != null) {
                String v = attrVal(color);
                if (v != null && v.matches("[0-9a-fA-F]{6}")) {
                    css.append("color:#").append(v).append(";");
                }
            }
            Element fonts = firstChildElement(rPr, "rFonts");
            if (fonts != null) {
                StringBuilder fam = new StringBuilder();
                for (String attr : new String[]{"eastAsia", "ascii", "hAnsi"}) {
                    String name = fonts.getAttributeNS("*", attr);
                    if (name != null && !name.isEmpty() && fam.indexOf("'" + name + "'") < 0) {
                        fam.append("'").append(name).append("',");
                    }
                }
                if (fam.length() > 0) {
                    css.append("font-family:").append(fam).append("SimSun,'宋体',serif;");
                }
            }
            Element vert = firstChildElement(rPr, "vertAlign");
            if (vert != null) {
                String v = attrVal(vert);
                if ("superscript".equals(v)) {
                    css.append("vertical-align:super;font-size:0.75em;");
                } else if ("subscript".equals(v)) {
                    css.append("vertical-align:sub;font-size:0.75em;");
                }
            }
        }

        String body = runContent(r, ctx);
        if (body.isEmpty()) {
            return "";
        }
        if (css.length() == 0) {
            return body;
        }
        return "<span style=\"" + css + "\">" + body + "</span>";
    }

    /** run 内部纯文本（超链接等场景用，不输出 HTML 结构） */
    private static String runText(Element r, RenderContext ctx) {
        StringBuilder sb = new StringBuilder();
        NodeList children = r.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && "t".equals(localName((Element) n))) {
                sb.append(((Element) n).getTextContent());
            }
        }
        return sb.toString();
    }

    private static String runContent(Element r, RenderContext ctx) {
        StringBuilder sb = new StringBuilder();
        NodeList children = r.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) n;
            String tag = localName(el);
            switch (tag) {
                case "t" -> {
                    String content = el.getTextContent();
                    ctx.textChars += content.trim().length();
                    sb.append(escape(content));
                }
                case "tab" -> sb.append("&emsp;");
                case "br" -> {
                    String type = el.getAttributeNS("*", "type");
                    if ("page".equals(type)) {
                        ctx.pageBreaks++;
                        sb.append("<div class=\"docx-page-break\" style=\"border-top:2px dashed #c0c4cc;margin:16px 0;\"></div>");
                    } else {
                        sb.append("<br>");
                    }
                }
                case "cr" -> sb.append("<br>");
                case "lastRenderedPageBreak" -> ctx.pageBreaks++;
                case "drawing", "pict" -> {
                    String img = renderImage(el, ctx);
                    if (img != null) {
                        sb.append(img);
                    }
                }
                default -> {
                    // 其他节点忽略
                }
            }
        }
        return sb.toString();
    }

    // ---------------- 图片 ----------------

    private static String renderImage(Element container, RenderContext ctx) {
        String rId = null;
        Long cx = null;
        // DrawingML：a:blip @r:embed；尺寸 wp:extent @cx/@cy（EMU，1px=9525EMU）
        NodeList blips = container.getElementsByTagNameNS("*", "blip");
        if (blips.getLength() > 0) {
            Element blip = (Element) blips.item(0);
            rId = blip.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed");
            if (rId == null || rId.isEmpty()) {
                rId = blip.getAttributeNS("*", "embed");
            }
        }
        if (rId == null || rId.isEmpty()) {
            // VML：v:imagedata @r:id
            NodeList imagedatas = container.getElementsByTagNameNS("*", "imagedata");
            if (imagedatas.getLength() > 0) {
                rId = ((Element) imagedatas.item(0))
                        .getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            }
        }
        if (rId == null || rId.isEmpty()) {
            return null;
        }
        NodeList extents = container.getElementsByTagNameNS("*", "extent");
        if (extents.getLength() > 0) {
            cx = parseLong(((Element) extents.item(0)).getAttribute("cx"), null);
        }
        String uri = ctx.imageDataUri(rId);
        if (uri == null) {
            return "<span style=\"color:#999;\">[图片]</span>";
        }
        StringBuilder style = new StringBuilder("max-width:100%;display:block;margin:8px auto;");
        if (cx != null && cx > 0) {
            long px = cx / 9525;
            if (px >= 40 && px <= 1600) {
                style.append("width:").append(px).append("px;");
            }
        }
        return "<img src=\"" + uri + "\" style=\"" + style + "\" alt=\"\">";
    }

    // ---------------- 表格 ----------------

    private static String renderTable(Element tbl, RenderContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"border-collapse:collapse;width:100%;margin:10px 0;font-size:0.95em;\">");
        for (Element tr : childElements(tbl, "tr")) {
            sb.append("<tr>");
            for (Element tc : childElements(tr, "tc")) {
                Element tcPr = firstChildElement(tc, "tcPr");
                StringBuilder tdStyle = new StringBuilder("border:1px solid #999;padding:4px 8px;vertical-align:top;");
                int span = 1;
                if (tcPr != null) {
                    Element gridSpan = firstChildElement(tcPr, "gridSpan");
                    span = gridSpan == null ? 1 : Math.max(1, parseInt(attrVal(gridSpan), 1));
                    Element vAlign = firstChildElement(tcPr, "vAlign");
                    if (vAlign != null && "center".equals(attrVal(vAlign))) {
                        tdStyle.append("text-align:center;");
                    }
                }
                if (span > 1) {
                    sb.append("<td colspan=\"").append(span).append("\" style=\"").append(tdStyle).append("\">");
                } else {
                    sb.append("<td style=\"").append(tdStyle).append("\">");
                }
                // 单元格内容：段落与嵌套表格
                for (Element child : childElements(tc)) {
                    String tag = localName(child);
                    if ("p".equals(tag)) {
                        sb.append(renderParagraph(child, ctx));
                    } else if ("tbl".equals(tag)) {
                        sb.append(renderTable(child, ctx));
                    }
                }
                sb.append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    // ---------------- DOM 工具 ----------------

    private static String localName(Element el) {
        String local = el.getLocalName();
        return local != null ? local : el.getNodeName();
    }

    private static List<Element> childElements(Element parent) {
        List<Element> out = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                out.add((Element) n);
            }
        }
        return out;
    }

    private static List<Element> childElements(Element parent, String local) {
        List<Element> out = new ArrayList<>();
        for (Element el : childElements(parent)) {
            if (local.equals(localName(el))) {
                out.add(el);
            }
        }
        return out;
    }

    private static Element firstChildElement(Element parent, String local) {
        for (Element el : childElements(parent)) {
            if (local.equals(localName(el))) {
                return el;
            }
        }
        return null;
    }

    /** w:* 元素的 w:val 属性（按 local name 取，兼容带/不带命名空间） */
    private static String attrVal(Element el) {
        return attrVal(el, "val");
    }

    private static String attrVal(Element el, String local) {
        String v = el.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", local);
        if (v == null || v.isEmpty()) {
            v = el.getAttributeNS("*", local);
        }
        if (v == null || v.isEmpty()) {
            v = el.getAttribute("w:" + local);
        }
        return v;
    }

    /** on/off 属性：元素存在且无 val / val 非 0|false|off 视为开 */
    private static boolean onOff(Element el) {
        if (el == null) {
            return false;
        }
        String v = attrVal(el);
        if (v == null || v.isEmpty()) {
            return true;
        }
        return !(v.equals("0") || v.equalsIgnoreCase("false") || v.equalsIgnoreCase("off"));
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Long parseLong(String s, Long def) {
        try {
            return s == null || s.isEmpty() ? def : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String extOf(String path) {
        int idx = path.lastIndexOf('.');
        return idx < 0 ? "" : path.substring(idx + 1).toLowerCase();
    }

    private static String escape(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
