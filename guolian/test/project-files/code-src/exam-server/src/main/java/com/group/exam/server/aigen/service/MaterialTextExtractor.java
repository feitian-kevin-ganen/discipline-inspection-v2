package com.group.exam.server.aigen.service;

import com.group.exam.common.config.MinioConfig.MinioService;
import com.group.exam.learn.entity.LearnMaterial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 学习资料正文提取（AI 生题用）：
 * - 图文 RICHTEXT：去 HTML 标签取纯文本；
 * - DOCX：解析 word/document.xml 取段落文本；
 * - PDF：pdfbox PDFTextStripper 提取文字版文本；提取结果过薄（<100字符）判定为扫描版，
 *   逐页渲染图片走模型平台 PaddleOCR 多模态识别（按 exam.ai-gen.ocr-max-pages 限页）。
 * - 视频：无正文，返回 null（按标题出题）。
 * 提取结果按资料id内存缓存（扫描件 OCR 耗时较长，避免重复调用）。
 */
@Component
public class MaterialTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(MaterialTextExtractor.class);
    /** PDF 提取字符少于此值视为扫描版，走 OCR */
    private static final int SCANNED_THRESHOLD = 100;

    private final MinioService minioService;
    private final GliaiModelClient modelClient;
    private final int textMaxChars;
    private final int ocrMaxPages;

    /** materialId → 正文缓存（null 结果不缓存） */
    private final Map<Long, String> cache = new ConcurrentHashMap<>();

    public MaterialTextExtractor(MinioService minioService, GliaiModelClient modelClient,
                                 @Value("${exam.ai-gen.text-max-chars:4500}") int textMaxChars,
                                 @Value("${exam.ai-gen.ocr-max-pages:10}") int ocrMaxPages) {
        this.minioService = minioService;
        this.modelClient = modelClient;
        this.textMaxChars = textMaxChars;
        this.ocrMaxPages = ocrMaxPages;
    }

    /** 提取资料正文（无正文返回 null），结果截断至 textMaxChars */
    public String extract(LearnMaterial m) {
        String cached = cache.get(m.getId());
        if (cached != null) {
            return cached;
        }
        String type = com.group.exam.learn.service.LearnMaterialService.materialTypeOf(m.getFileType());
        String text;
        if ("RICHTEXT".equals(type)) {
            text = stripHtml(m.getContent());
        } else {
            String ext = extOf(m.getFileUrl());
            if (!"pdf".equalsIgnoreCase(ext) && !"docx".equalsIgnoreCase(ext)) {
                return null; // 视频等无正文
            }
            byte[] bytes = download(m);
            if (bytes == null) {
                return null;
            }
            if ("docx".equalsIgnoreCase(ext)) {
                try {
                    text = docxText(bytes);
                } catch (Exception e) {
                    log.warn("docx 文本提取失败 materialId={}: {}", m.getId(), e.getMessage());
                    return null;
                }
            } else {
                try {
                    text = pdfText(bytes);
                } catch (Exception e) {
                    log.warn("pdf 文本提取失败 materialId={}: {}", m.getId(), e.getMessage());
                    return null;
                }
                if (thin(text)) {
                    // 扫描版：逐页 OCR
                    log.info("资料[{}] PDF 文字提取过薄（{}字符），判定扫描版，走 OCR（最多{}页）",
                            m.getId(), text == null ? 0 : text.replaceAll("\\s", "").length(), ocrMaxPages);
                    try {
                        text = ocrPdf(bytes);
                    } catch (Exception e) {
                        log.warn("PDF OCR 失败 materialId={}: {}", m.getId(), e.getMessage());
                        return null;
                    }
                }
            }
        }
        if (text == null || text.replaceAll("\\s", "").isEmpty()) {
            return null;
        }
        String t = text.trim();
        if (t.length() > textMaxChars) {
            t = t.substring(0, textMaxChars) + "……（正文较长，此处截断）";
        }
        cache.put(m.getId(), t);
        return t;
    }

    private byte[] download(LearnMaterial m) {
        if (m.getFileUrl() == null || m.getFileUrl().isBlank()) {
            return null;
        }
        try (io.minio.GetObjectResponse in = minioService.getObject(m.getFileUrl())) {
            return in.readAllBytes();
        } catch (Exception e) {
            log.warn("资料文件读取失败 materialId={}: {}", m.getId(), e.getMessage());
            return null;
        }
    }

    /** PDF 文字版文本提取 */
    private String pdfText(byte[] bytes) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(bytes)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    /** 扫描版 PDF：逐页渲染 JPEG 走 PaddleOCR，拼接识别文本（限前 ocrMaxPages 页） */
    private String ocrPdf(byte[] bytes) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(bytes)) {
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            int pages = Math.min(doc.getNumberOfPages(), ocrMaxPages);
            for (int i = 0; i < pages; i++) {
                java.awt.image.BufferedImage image = renderer.renderImageWithDPI(i, 150,
                        org.apache.pdfbox.rendering.ImageType.RGB);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(image, "jpeg", out);
                String pageText = modelClient.ocrImage(out.toByteArray());
                if (pageText != null && !pageText.isBlank()) {
                    sb.append(pageText.trim()).append('\n');
                }
                log.info("OCR 第 {}/{} 页完成，累计 {} 字符", i + 1, pages, sb.length());
            }
        }
        return sb.toString();
    }

    /** DOCX 段落文本提取：解析 word/document.xml，按段落收集 w:t */
    private String docxText(byte[] bytes) throws Exception {
        byte[] documentXml = null;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if ("word/document.xml".equals(e.getName())) {
                    documentXml = zin.readAllBytes();
                    break;
                }
            }
        }
        if (documentXml == null) {
            throw new IllegalStateException("DOCX 缺少 word/document.xml");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(documentXml));
        Element body = doc.getDocumentElement();
        StringBuilder sb = new StringBuilder();
        walk(body, sb);
        return sb.toString();
    }

    /** 递归收集文本：w:t 追加内容，段落/行末补换行 */
    private void walk(Node node, StringBuilder sb) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) n;
            String local = el.getLocalName() == null ? el.getNodeName() : el.getLocalName();
            if ("t".equals(local)) {
                sb.append(el.getTextContent());
            } else if ("tab".equals(local)) {
                sb.append(' ');
            } else if ("br".equals(local) || "cr".equals(local)) {
                sb.append('\n');
            } else {
                walk(el, sb);
            }
            if ("p".equals(local) || "tr".equals(local)) {
                sb.append('\n');
            }
        }
    }

    private String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        String text = html.replaceAll("<[^>]+>", " ").replaceAll("&nbsp;", " ")
                .replaceAll("&[a-zA-Z]+;", " ").replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? null : text;
    }

    private boolean thin(String text) {
        return text == null || text.replaceAll("\\s", "").length() < SCANNED_THRESHOLD;
    }

    private String extOf(String filename) {
        if (filename == null) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx + 1).toLowerCase();
    }
}
