package com.group.exam.learn.service;

import com.group.exam.common.config.MinioConfig.MinioService;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.learn.entity.LearnMaterial;
import com.group.exam.learn.entity.LearnRecord;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考生端学习服务（LEARN-01/02/03/05）
 */
@Service
public class LearnAppService {

    private static final int FILE_URL_EXPIRE_SEC = 3600;

    private final LearnMaterialService materialService;
    private final LearnRecordService recordService;
    private final LearnCategoryService categoryService;
    private final LearnScopeChecker scopeChecker;
    private final MinioService minioService;

    public LearnAppService(LearnMaterialService materialService, LearnRecordService recordService,
                           LearnCategoryService categoryService, LearnScopeChecker scopeChecker,
                           MinioService minioService) {
        this.materialService = materialService;
        this.recordService = recordService;
        this.categoryService = categoryService;
        this.scopeChecker = scopeChecker;
        this.minioService = minioService;
    }

    /** LEARN-01 本人可见资料列表（已发布 + 可见范围命中，内存过滤后分页） */
    public PageResult<Map<String, Object>> pageVisible(Long catId, String keyword, String sortBy, String sortOrder,
                                                       long pageNum, long pageSize) {
        LoginUser user = SecurityUtils.getLoginUser();
        List<LearnMaterial> published = materialService.list(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LearnMaterial>()
                        .eq("audit_status", 2)
                        .in(catId != null, "category_id",
                                catId == null ? List.of() : categoryService.collectSubIds(catId))
                        .and(StringUtils.hasText(keyword), w -> w
                                .like("title", keyword).or().like("summary", keyword)));
        List<LearnMaterial> visible = published.stream()
                .filter(m -> scopeChecker.visible(m.getVisibleScope(), user.getOrgPath()))
                .sorted(comparator(sortBy, sortOrder))
                .collect(Collectors.toList());

        long total = visible.size();
        int from = (int) Math.min((pageNum - 1) * pageSize, total);
        int to = (int) Math.min(from + pageSize, total);
        List<LearnMaterial> pageList = visible.subList(from, to);

        Map<Long, LearnRecord> records = recordService.mapByMaterialIds(user.getUserId(),
                pageList.stream().map(LearnMaterial::getId).collect(Collectors.toSet()));
        Map<Long, String> catNames = categoryService.mapCatNames(
                pageList.stream().map(LearnMaterial::getCategoryId).collect(Collectors.toSet()));

        List<Map<String, Object>> items = new ArrayList<>();
        for (LearnMaterial m : pageList) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("materialId", m.getId());
            item.put("title", m.getTitle());
            item.put("materialType", LearnMaterialService.materialTypeOf(m.getFileType()));
            item.put("catName", catNames.get(m.getCategoryId()));
            // 封面走同源代理相对路径（/cover 端点），避免预签名 URL 内网地址外网不可达；
            // 文档/图文无封面时使用类型默认底图，与视频首帧封面视觉统一
            item.put("coverUrl", StringUtils.hasText(m.getCoverUrl())
                    || LearnMaterialService.defaultCoverOf(m.getFileType()) != null
                    ? "/api/v1/app/learn/materials/" + m.getId() + "/cover" : null);
            item.put("tags", m.getTags() == null ? List.of() : m.getTags());
            item.put("durationSec", m.getDurationSec());
            item.put("studyCount", m.getStudyCount());
            item.put("myProgress", myProgress(records.get(m.getId())));
            items.add(item);
        }
        return PageResult.of(total, items, pageNum, pageSize);
    }

    /** LEARN-02 资料详情 + 本人学习断点 */
    public Map<String, Object> detail(Long materialId) {
        LoginUser user = SecurityUtils.getLoginUser();
        LearnMaterial m = requireVisible(materialId, user);
        LearnRecord record = recordService.getRecord(user.getUserId(), materialId);
        Map<Long, String> catNames = categoryService.mapCatNames(List.of(m.getCategoryId()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("materialId", m.getId());
        data.put("title", m.getTitle());
        data.put("materialType", LearnMaterialService.materialTypeOf(m.getFileType()));
        data.put("summary", m.getSummary());
        data.put("contentHtml", m.getContent());
        data.put("catName", catNames.get(m.getCategoryId()));
        data.put("tags", m.getTags() == null ? List.of() : m.getTags());
        data.put("durationSec", m.getDurationSec());
        data.put("pageCount", m.getPageCount());

        Map<String, Object> myRecord = new LinkedHashMap<>();
        myRecord.put("progressPct", LearnRecordService.progressOf(record));
        myRecord.put("lastPosition", LearnRecordService.lastPositionOf(record, m.getFileType()));
        // t_learn_record 无逐页已读数组列，readPages 按契约返回空数组
        myRecord.put("readPages", List.of());
        myRecord.put("effectiveSec", LearnRecordService.effectiveSecOf(record));
        myRecord.put("status", LearnRecordService.statusOf(record));
        myRecord.put("completedTime", record == null ? null : record.getFinishTime());
        data.put("myRecord", myRecord);
        return data;
    }

    /** LEARN-03 获取资料文件地址（预签名 URL） */
    public Map<String, Object> fileAccess(Long materialId) {
        LoginUser user = SecurityUtils.getLoginUser();
        LearnMaterial m = requireVisible(materialId, user);
        Map<String, Object> data = new LinkedHashMap<>();
        if (StringUtils.hasText(m.getFileUrl())) {
            data.put("fileUrl", minioService.presignedUrl(m.getFileUrl(), FILE_URL_EXPIRE_SEC));
            data.put("expireSec", FILE_URL_EXPIRE_SEC);
            data.put("fileType", LearnMaterialService.extOf(m.getFileUrl()));
            // 视频备用流：同名 .webm（VP9/Opus）存在则一并下发，供缺少 H.264 解码能力的浏览器降级播放
            String webmName = m.getFileUrl().replaceAll("(?i)\\.mp4$", ".webm");
            if (!webmName.equals(m.getFileUrl()) && minioService.exists(webmName)) {
                data.put("altFileUrl", minioService.presignedUrl(webmName, FILE_URL_EXPIRE_SEC));
                data.put("altFileType", "webm");
            }
        } else {
            // 图文类型无文件，前端直接展示 contentHtml
            data.put("fileUrl", null);
            data.put("expireSec", 0);
            data.put("fileType", "richtext");
        }
        return data;
    }

    /**
     * LEARN-03b 资料文件在线预览流：服务端代理 MinIO 转发，inline 输出。
     * 解决客户端直链（含 .pdf 后缀的 MinIO 地址）被 IDM 等下载工具拦截、无法在线预览的问题。
     */
    public void streamFile(Long materialId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        LoginUser user = SecurityUtils.getLoginUser();
        LearnMaterial m = requireVisible(materialId, user);
        if (!StringUtils.hasText(m.getFileUrl())) {
            throw new BizException(ErrorCode.NOT_FOUND, "该资料无附件文件");
        }
        String ext = LearnMaterialService.extOf(m.getFileUrl()).toLowerCase();
        response.setContentType(switch (ext) {
            case "pdf" -> "application/pdf";
            case "mp4" -> "video/mp4";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        });
        response.setHeader("Content-Disposition", "inline; filename*=UTF-8''material-" + materialId + "." + ext);
        response.setHeader("Cache-Control", "private, max-age=3600");
        try (io.minio.GetObjectResponse in = minioService.getObject(m.getFileUrl())) {
            in.transferTo(response.getOutputStream());
            response.getOutputStream().flush();
        }
    }

    /**
     * LEARN-03e 封面图同源代理输出（image/jpeg）。
     * 列表封面若用预签名 URL 指向 MinIO 内网地址，外网/映射入口访问不可达，统一走本端点。
     */
    public void streamCover(Long materialId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        LoginUser user = SecurityUtils.getLoginUser();
        LearnMaterial m = requireVisible(materialId, user);
        String coverObject = StringUtils.hasText(m.getCoverUrl())
                ? m.getCoverUrl() : LearnMaterialService.defaultCoverOf(m.getFileType());
        if (coverObject == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该资料无封面图");
        }
        response.setContentType("image/jpeg");
        response.setHeader("Cache-Control", "private, max-age=3600");
        try (io.minio.GetObjectResponse in = minioService.getObject(coverObject)) {
            in.transferTo(response.getOutputStream());
            response.getOutputStream().flush();
        }
    }

    /**
     * LEARN-03d 视频流式播放（服务端代理 + HTTP Range 支持）。
     * 预签名 URL 指向 MinIO 内网地址，外网/映射地址访问的客户端不可达；
     * 统一走同源代理，mp4/webm 双格式由 fmt 参数选择。
     */
    public void streamVideo(Long materialId, String fmt,
                            jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        LoginUser user = SecurityUtils.getLoginUser();
        LearnMaterial m = requireVisible(materialId, user);
        if (!StringUtils.hasText(m.getFileUrl())) {
            throw new BizException(ErrorCode.NOT_FOUND, "该资料无附件文件");
        }
        String objectName = m.getFileUrl();
        String contentType = "video/mp4";
        if ("webm".equalsIgnoreCase(fmt)) {
            String webm = objectName.replaceAll("(?i)\\.mp4$", ".webm");
            if (webm.equals(objectName) || !minioService.exists(webm)) {
                throw new BizException(ErrorCode.NOT_FOUND, "该视频暂无 WebM 备用流");
            }
            objectName = webm;
            contentType = "video/webm";
        }
        long size = minioService.statSize(objectName);
        long start = 0;
        long end = size - 1;
        boolean partial = false;
        String range = request.getHeader("Range");
        if (range != null && range.startsWith("bytes=")) {
            try {
                String[] parts = range.substring(6).split("-");
                if (!parts[0].isEmpty()) {
                    start = Long.parseLong(parts[0].trim());
                }
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    end = Math.min(Long.parseLong(parts[1].trim()), size - 1);
                }
                partial = true;
            } catch (NumberFormatException ignored) {
                // 非法 Range 按全量处理
            }
        }
        if (start < 0 || start >= size || start > end) {
            response.setStatus(416);
            response.setHeader("Content-Range", "bytes */" + size);
            return;
        }
        long length = end - start + 1;
        response.setStatus(partial ? 206 : 200);
        response.setContentType(contentType);
        response.setHeader("Accept-Ranges", "bytes");
        if (partial) {
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
        }
        response.setContentLengthLong(length);
        response.setHeader("Cache-Control", "private, max-age=3600");
        try (io.minio.GetObjectResponse in = minioService.getObjectRange(objectName, start, length)) {
            in.transferTo(response.getOutputStream());
            response.getOutputStream().flush();
        }
    }

    /**
     * LEARN-03c PDF 逐页转 JPEG 页图输出。
     * 前端以 fetch 拉取图片字节转 blob 展示，全程不接触 PDF 文件流，
     * 避免 IDM 等下载工具按 application/pdf 嗅探拦截导致无法在线预览。
     */
    public void streamPageImage(Long materialId, int pageNo, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        LoginUser user = SecurityUtils.getLoginUser();
        LearnMaterial m = requireVisible(materialId, user);
        if (!StringUtils.hasText(m.getFileUrl())) {
            throw new BizException(ErrorCode.NOT_FOUND, "该资料无附件文件");
        }
        if (!"pdf".equalsIgnoreCase(LearnMaterialService.extOf(m.getFileUrl()))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅 PDF 资料支持页图预览");
        }
        PageImage page = renderPageImage(materialId, m.getFileUrl(), pageNo);
        response.setContentType("image/jpeg");
        response.setHeader("Cache-Control", "private, max-age=3600");
        response.setHeader("X-Total-Pages", String.valueOf(page.totalPages()));
        response.setContentLength(page.bytes().length);
        response.getOutputStream().write(page.bytes());
        response.getOutputStream().flush();
    }

    private record PageImage(byte[] bytes, int totalPages) {}

    /** 页图渲染缓存：key = materialId:pageNo，超出容量整体清空（资料少、页数少，足够用） */
    private static final int PAGE_CACHE_MAX = 500;
    private final Map<String, PageImage> pageImageCache = new java.util.concurrent.ConcurrentHashMap<>();

    private PageImage renderPageImage(Long materialId, String objectName, int pageNo) throws java.io.IOException {
        String key = materialId + ":" + pageNo;
        PageImage cached = pageImageCache.get(key);
        if (cached != null) {
            return cached;
        }
        byte[] pdfBytes;
        try (io.minio.GetObjectResponse in = minioService.getObject(objectName)) {
            pdfBytes = in.readAllBytes();
        }
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdfBytes)) {
            int total = doc.getNumberOfPages();
            if (pageNo < 1 || pageNo > total) {
                throw new BizException(ErrorCode.NOT_FOUND, "页码超出范围（共 " + total + " 页）");
            }
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            java.awt.image.BufferedImage image = renderer.renderImageWithDPI(pageNo - 1, 220, org.apache.pdfbox.rendering.ImageType.RGB);
            // 裁掉页面四周白边，让正文撑满显示宽度（手机端与在线图文排版一致，字体不再被页边距挤小）
            image = cropWhiteMargin(image);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "jpeg", out);
            PageImage img = new PageImage(out.toByteArray(), total);
            if (pageImageCache.size() >= PAGE_CACHE_MAX) {
                pageImageCache.clear();
            }
            pageImageCache.put(key, img);
            return img;
        }
    }

    /** 自动裁剪白边：扫描非白像素包围盒，四周各留少量内边距；空白页返回原图 */
    private static java.awt.image.BufferedImage cropWhiteMargin(java.awt.image.BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int top = 0;
        while (top < h && isWhiteRow(img, top, 0, w)) top++;
        if (top >= h) {
            return img;
        }
        int bottom = h - 1;
        while (bottom > top && isWhiteRow(img, bottom, 0, w)) bottom--;
        int left = 0;
        while (left < w && isWhiteCol(img, left, top, bottom + 1)) left++;
        int right = w - 1;
        while (right > left && isWhiteCol(img, right, top, bottom + 1)) right--;
        // 回留约 1.5% 宽度的内边距，避免文字贴边
        int padX = Math.round(w * 0.015f);
        int padY = Math.round(h * 0.01f);
        int x = Math.max(0, left - padX);
        int y = Math.max(0, top - padY);
        int cw = Math.min(w - x, right - left + 1 + padX * 2);
        int ch = Math.min(h - y, bottom - top + 1 + padY * 2);
        if (cw <= 0 || ch <= 0 || (x == 0 && y == 0 && cw == w && ch == h)) {
            return img;
        }
        return img.getSubimage(x, y, cw, ch);
    }

    /** 该行是否存在非白像素（任一通道 < 240 视为内容） */
    private static boolean isWhiteRow(java.awt.image.BufferedImage img, int y, int x0, int x1) {
        for (int x = x0; x < x1; x++) {
            int rgb = img.getRGB(x, y);
            if ((rgb & 0xFF) < 240 || ((rgb >> 8) & 0xFF) < 240 || ((rgb >> 16) & 0xFF) < 240) {
                return false;
            }
        }
        return true;
    }

    /** 该列在 [y0, y1) 范围内是否存在非白像素 */
    private static boolean isWhiteCol(java.awt.image.BufferedImage img, int x, int y0, int y1) {
        for (int y = y0; y < y1; y++) {
            int rgb = img.getRGB(x, y);
            if ((rgb & 0xFF) < 240 || ((rgb >> 8) & 0xFF) < 240 || ((rgb >> 16) & 0xFF) < 240) {
                return false;
            }
        }
        return true;
    }

    /** LEARN-05 断点续学进度 */
    public Map<String, Object> breakpoint(Long materialId) {
        LoginUser user = SecurityUtils.getLoginUser();
        LearnMaterial m = requireVisible(materialId, user);
        LearnRecord record = recordService.getRecord(user.getUserId(), materialId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("progressPct", LearnRecordService.progressOf(record));
        data.put("lastPosition", LearnRecordService.lastPositionOf(record, m.getFileType()));
        data.put("readPages", List.of());
        data.put("effectiveSec", LearnRecordService.effectiveSecOf(record));
        data.put("status", LearnRecordService.statusOf(record));
        return data;
    }

    /** 已发布校验（30001）+ 可见性校验（30002） */
    LearnMaterial requireVisible(Long materialId, LoginUser user) {
        LearnMaterial m = materialService.requirePublished(materialId);
        if (!scopeChecker.visible(m.getVisibleScope(), user.getOrgPath())) {
            throw new BizException(ErrorCode.MATERIAL_NOT_VISIBLE);
        }
        return m;
    }

    static Map<String, Object> myProgress(LearnRecord record) {
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("progressPct", LearnRecordService.progressOf(record));
        progress.put("effectiveSec", LearnRecordService.effectiveSecOf(record));
        progress.put("status", LearnRecordService.statusOf(record));
        return progress;
    }

    private Comparator<LearnMaterial> comparator(String sortBy, String sortOrder) {
        Comparator<LearnMaterial> cmp;
        if ("studyCount".equals(sortBy)) {
            cmp = Comparator.comparing(m -> m.getStudyCount() == null ? 0 : m.getStudyCount());
        } else {
            cmp = Comparator.comparing(LearnMaterial::getCreateTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        }
        return "asc".equalsIgnoreCase(sortOrder) ? cmp : cmp.reversed();
    }
}
