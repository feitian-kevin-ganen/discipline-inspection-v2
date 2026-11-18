package com.group.exam.learn.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.config.MinioConfig.MinioService;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.learn.dto.MaterialScopeRequest;
import com.group.exam.learn.entity.LearnMaterial;
import com.group.exam.learn.mapper.LearnMaterialMapper;
import com.group.exam.system.service.OrgUnitService;
import com.group.exam.system.service.SysConfigService;
import com.group.exam.system.service.SysLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学习资料管理端服务（LIB-05~12）。
 * 状态机 audit_status：0草稿 → 1待审 →(审核通过)→ 2已发布 ↔ 3已下架；驳回 → 4。
 * API 状态码映射：DRAFT=0 PENDING_AUDIT=1 PUBLISHED=2（APPROVED 按 2 处理）OFFLINE=3 REJECTED=4。
 */
@Service
public class LearnMaterialService extends ServiceImpl<LearnMaterialMapper, LearnMaterial> {

    /** 1PDF 2Word 3PPT 4MP4视频 5图文 */
    public static final int FT_PDF = 1;
    public static final int FT_WORD = 2;
    public static final int FT_PPT = 3;
    public static final int FT_VIDEO = 4;
    public static final int FT_RICHTEXT = 5;

    private static final Set<String> DOC_EXTS = Set.of("pdf", "doc", "docx", "ppt", "pptx");
    private static final Set<String> VIDEO_EXTS = Set.of("mp4");
    private static final Set<String> COVER_EXTS = Set.of("jpg", "jpeg", "png");
    private static final long COVER_MAX_BYTES = 2L * 1024 * 1024;

    /** 文档/图文默认封面底图（MinIO 对象名），无手动封面时自动挂载，与视频首帧封面视觉统一 */
    public static final String DEFAULT_COVER_DOC = "cover/default/doc.jpg";
    public static final String DEFAULT_COVER_RICHTEXT = "cover/default/richtext.jpg";

    /** 按资料类型返回默认封面底图对象名；视频依赖抽帧故返回 null */
    public static String defaultCoverOf(Integer fileType) {
        if (fileType == null) {
            return null;
        }
        return switch (fileType) {
            case FT_PDF, FT_WORD, FT_PPT -> DEFAULT_COVER_DOC;
            case FT_RICHTEXT -> DEFAULT_COVER_RICHTEXT;
            default -> null;
        };
    }

    private final LearnCategoryService categoryService;
    private final LearnScopeChecker scopeChecker;
    private final SysConfigService sysConfigService;
    private final SysLogService sysLogService;
    private final OrgUnitService orgUnitService;
    private final MinioService minioService;

    /** ffmpeg 可执行文件路径，用于视频封面自动抽帧；默认取 PATH 中的 ffmpeg */
    @org.springframework.beans.factory.annotation.Value("${exam.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    public LearnMaterialService(LearnCategoryService categoryService, LearnScopeChecker scopeChecker,
                                SysConfigService sysConfigService, SysLogService sysLogService,
                                OrgUnitService orgUnitService, MinioService minioService) {
        this.categoryService = categoryService;
        this.scopeChecker = scopeChecker;
        this.sysConfigService = sysConfigService;
        this.sysLogService = sysLogService;
        this.orgUnitService = orgUnitService;
        this.minioService = minioService;
    }

    // ---------------------------------------------------------------- LIB-05 分页查询（管理端）

    public PageResult<Map<String, Object>> pageAdmin(Long catId, String keyword, String status, String materialType,
                                                     String sortBy, String sortOrder, long pageNum, long pageSize) {
        QueryWrapper<LearnMaterial> qw = new QueryWrapper<>();
        String prefix = SecurityUtils.dataScopePathPrefix();
        qw.likeRight(prefix != null, "upload_org_path", prefix);
        if (catId != null) {
            qw.in("category_id", categoryService.collectSubIds(catId));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like("title", kw).or().like("summary", kw)
                    .or().apply("CAST(tags AS CHAR) LIKE {0}", "%" + kw.replace("%", "\\%").replace("_", "\\_") + "%"));
        }
        Integer statusCode = statusToCode(status);
        if (statusCode != null) {
            qw.eq("audit_status", statusCode);
        }
        if (StringUtils.hasText(materialType)) {
            switch (materialType) {
                case "DOC" -> qw.in("file_type", FT_PDF, FT_WORD, FT_PPT);
                case "VIDEO" -> qw.eq("file_type", FT_VIDEO);
                case "RICHTEXT" -> qw.eq("file_type", FT_RICHTEXT);
                default -> throw new BizException(ErrorCode.PARAM_ERROR, "非法 materialType: " + materialType);
            }
        }
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        if ("studyCount".equals(sortBy)) {
            qw.orderBy(true, asc, "study_count");
        } else {
            // 默认按序号升序，序号为 0 的排最后，再按创建时间倒序
            qw.last("ORDER BY CASE WHEN sort_no > 0 THEN 0 ELSE 1 END, sort_no ASC, create_time DESC");
        }
        Page<LearnMaterial> page = page(new Page<>(pageNum, pageSize), qw);
        Map<Long, String> catNames = categoryService.mapCatNames(
                page.getRecords().stream().map(LearnMaterial::getCategoryId).collect(Collectors.toSet()));
        Map<Long, String> orgNames = orgUnitService.mapUnitNames(
                page.getRecords().stream().map(LearnMaterial::getUploadOrgId)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        return PageResult.of(page).map(m -> {
            Map<String, Object> item = toListItem(m, catNames);
            item.put("uploaderOrgName", orgNames.get(m.getUploadOrgId()));
            return item;
        });
    }

    // ---------------------------------------------------------------- LIB-06 上传/新建资料

    public Map<String, Object> upload(MultipartFile file, String title, Long catId, String materialType,
                                      String summary, String tags, MultipartFile cover, String contentHtml,
                                      Integer durationSec, String scopeType, String scopeOrgIds, Integer sortNo) {
        if (!StringUtils.hasText(title)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "资料标题不能为空");
        }
        categoryService.requireCategory(catId);
        LoginUser current = SecurityUtils.getLoginUser();

        LearnMaterial m = new LearnMaterial();
        m.setCategoryId(catId);
        m.setTitle(title.trim());
        m.setSortNo(sortNo == null ? 0 : sortNo);
        m.setSummary(summary);
        m.setTags(parseTags(tags));
        applyFile(m, file, materialType, contentHtml, durationSec);
        if (cover != null && !cover.isEmpty()) {
            m.setCoverUrl(uploadCover(cover));
        }
        // 文档/图文无手动封面时挂默认底图（视频封面由 applyFile 抽帧生成）
        if (!StringUtils.hasText(m.getCoverUrl())) {
            m.setCoverUrl(defaultCoverOf(m.getFileType()));
        }
        m.setVisibleScope(scopeChecker.buildScope(scopeType, parseOrgIds(scopeOrgIds)));
        m.setVersion(1);
        m.setStudyCount(0);
        m.setUploadOrgId(current.getOrgId());
        m.setUploadOrgPath(current.getOrgPath());
        m.setCreateBy(current.getUserId());
        applyInitialAuditStatus(m);
        save(m);
        sysLogService.log(1, "LIB", "上传资料", "上传资料[" + m.getTitle() + "(id=" + m.getId() + ")]", 1);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("materialId", m.getId());
        data.put("status", codeToStatus(m.getAuditStatus()));
        return data;
    }

    // ---------------------------------------------------------------- LIB-07 资料详情（管理端）

    public Map<String, Object> detail(Long materialId) {
        LearnMaterial m = requireInScope(materialId);
        Map<Long, String> catNames = categoryService.mapCatNames(List.of(m.getCategoryId()));
        Map<String, Object> detail = toListItem(m, catNames);
        detail.put("uploaderOrgName", orgUnitService.mapUnitNames(
                m.getUploadOrgId() == null ? List.of() : List.of(m.getUploadOrgId())).get(m.getUploadOrgId()));
        detail.put("summary", m.getSummary());
        detail.put("contentHtml", m.getContent());
        detail.put("fileUrl", StringUtils.hasText(m.getFileUrl()) ? minioService.presignedUrl(m.getFileUrl()) : null);
        detail.put("fileName", m.getFileName());
        // t_learn_material 无 file_md5 / audit_comment 列，按文档字段返回 null
        detail.put("fileMd5", null);
        detail.put("pageCount", m.getPageCount());
        detail.put("scopeType", scopeChecker.scopeTypeOf(m.getVisibleScope()));
        detail.put("scopeOrgIds", scopeChecker.scopeOrgIdsOf(m.getVisibleScope()));
        detail.put("auditBy", m.getAuditBy());
        detail.put("auditTime", m.getAuditTime());
        detail.put("auditComment", null);
        detail.put("version", m.getVersion());
        return detail;
    }

    // ---------------------------------------------------------------- LIB-08 编辑资料/版本更新

    public void updateMaterial(Long materialId, MultipartFile file, String title, Long catId, String materialType,
                               String summary, String tags, MultipartFile cover, String contentHtml,
                               Integer durationSec, String scopeType, String scopeOrgIds, Integer sortNo) {
        LearnMaterial m = requireInScope(materialId);
        if (StringUtils.hasText(title)) {
            m.setTitle(title.trim());
        }
        if (sortNo != null) {
            if (sortNo < 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "序号必须为非负整数");
            }
            m.setSortNo(sortNo);
        }
        if (catId != null) {
            categoryService.requireCategory(catId);
            m.setCategoryId(catId);
        }
        if (summary != null) {
            m.setSummary(summary);
        }
        if (tags != null) {
            m.setTags(parseTags(tags));
        }
        if (contentHtml != null) {
            m.setContent(contentHtml);
        }
        if (durationSec != null) {
            if (durationSec < 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "标称时长必须为非负整数秒");
            }
            m.setDurationSec(durationSec);
        }
        if (StringUtils.hasText(scopeType)) {
            m.setVisibleScope(scopeChecker.buildScope(scopeType, parseOrgIds(scopeOrgIds)));
        }
        if (file != null && !file.isEmpty()) {
            // 换版：version+1，断点进度保留，完成状态不回退
            String type = StringUtils.hasText(materialType) ? materialType : materialTypeOf(m.getFileType());
            applyFile(m, file, type, null, durationSec);
            m.setVersion(m.getVersion() + 1);
        }
        // 手动封面在文件处理之后赋值：优先于视频自动抽帧封面
        if (cover != null && !cover.isEmpty()) {
            m.setCoverUrl(uploadCover(cover));
        }
        // 老数据无封面时按类型补默认底图（文档/图文）
        if (!StringUtils.hasText(m.getCoverUrl())) {
            m.setCoverUrl(defaultCoverOf(m.getFileType()));
        }
        // 审核开关开启时，非超管/总管修改后需重新审核
        boolean auditRequired = sysConfigService.getBool("lib.auditRequired", true);
        if (auditRequired && !isSuperOrHq()) {
            m.setNeedAudit(1);
            m.setAuditStatus(1);
        }
        m.setUpdateBy(SecurityUtils.getUserId());
        updateById(m);
        sysLogService.log(1, "LIB", "编辑资料", "编辑资料[" + m.getTitle() + "(id=" + m.getId() + ")]", 1);
    }

    // ---------------------------------------------------------------- LIB-0x 管理端资料预览

    /** 预览文件地址（预签名）：视频返回 mp4 + 可能的 webm 备用流 */
    public Map<String, Object> previewFile(Long materialId) {
        LearnMaterial m = requireInScope(materialId);
        Map<String, Object> data = new LinkedHashMap<>();
        if (!StringUtils.hasText(m.getFileUrl())) {
            data.put("fileUrl", null);
            data.put("altFileUrl", null);
            data.put("ext", "");
            return data;
        }
        data.put("fileUrl", minioService.presignedUrl(m.getFileUrl(), 3600));
        data.put("ext", extOf(m.getFileUrl()));
        String webm = m.getFileUrl().replaceAll("(?i)\\.mp4$", ".webm");
        if (!webm.equals(m.getFileUrl()) && minioService.exists(webm)) {
            data.put("altFileUrl", minioService.presignedUrl(webm, 3600));
        } else {
            data.put("altFileUrl", null);
        }
        return data;
    }

    /** DOCX 在线预览 HTML（复用学习端 DocxHtmlRenderer） */
    public String previewDocHtml(Long materialId) {
        LearnMaterial m = requireInScope(materialId);
        if (!StringUtils.hasText(m.getFileUrl()) || !"docx".equalsIgnoreCase(extOf(m.getFileUrl()))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该格式暂不支持在线预览");
        }
        byte[] bytes;
        try (io.minio.GetObjectResponse in = minioService.getObject(m.getFileUrl())) {
            bytes = in.readAllBytes();
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件读取失败: " + e.getMessage());
        }
        try {
            com.group.exam.learn.support.DocxHtmlRenderer.DocxHtml rendered =
                    com.group.exam.learn.support.DocxHtmlRenderer.render(bytes);
            if (m.getPageCount() == null || m.getPageCount() <= 0) {
                m.setPageCount(rendered.pageCount());
                updateById(m);
            }
            return rendered.html();
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文档解析失败: " + e.getMessage());
        }
    }

    /** PDF 逐页转 JPEG（页图预览），总页数由响应头 X-Total-Pages 下发 */
    public void previewPageImage(Long materialId, int pageNo, jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        LearnMaterial m = requireInScope(materialId);
        if (!StringUtils.hasText(m.getFileUrl()) || !"pdf".equalsIgnoreCase(extOf(m.getFileUrl()))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅 PDF 资料支持页图预览");
        }
        byte[] pdfBytes;
        try (io.minio.GetObjectResponse in = minioService.getObject(m.getFileUrl())) {
            pdfBytes = in.readAllBytes();
        }
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdfBytes)) {
            int total = doc.getNumberOfPages();
            if (pageNo < 1 || pageNo > total) {
                throw new BizException(ErrorCode.NOT_FOUND, "页码超出范围（共 " + total + " 页）");
            }
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            java.awt.image.BufferedImage image = renderer.renderImageWithDPI(pageNo - 1, 150,
                    org.apache.pdfbox.rendering.ImageType.RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "jpeg", out);
            response.setContentType("image/jpeg");
            response.setHeader("Cache-Control", "private, max-age=3600");
            response.setHeader("X-Total-Pages", String.valueOf(total));
            response.setContentLength(out.size());
            response.getOutputStream().write(out.toByteArray());
            response.getOutputStream().flush();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "页图渲染失败: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- LIB-09 删除资料
    public void deleteMaterial(Long materialId) {
        LearnMaterial m = requireInScope(materialId);
        removeById(materialId);
        sysLogService.log(1, "LIB", "删除资料", "删除资料[" + m.getTitle() + "(id=" + m.getId() + ")]", 1);
    }

    // ---------------------------------------------------------------- LIB-10 资料审核

    public void audit(Long materialId, String action, String comment) {
        LearnMaterial m = getById(materialId);
        if (m == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "资料不存在");
        }
        if (m.getAuditStatus() == null || m.getAuditStatus() != 1) {
            throw new BizException(ErrorCode.MATERIAL_AUDIT_STATUS_INVALID);
        }
        // 审核人须为上传单位的上级管理员（orgPath 真前缀），超管/总管直通
        LoginUser current = SecurityUtils.getLoginUser();
        if (!isSuperOrHq()) {
            String myPath = current.getOrgPath();
            String upPath = m.getUploadOrgPath();
            boolean ancestor = myPath != null && upPath != null
                    && upPath.startsWith(myPath) && !upPath.equals(myPath);
            if (!ancestor) {
                throw new BizException(ErrorCode.FORBIDDEN, "仅上传单位的上级管理员可审核该资料");
            }
        }
        if ("APPROVE".equals(action)) {
            m.setAuditStatus(2);
        } else if ("REJECT".equals(action)) {
            if (!StringUtils.hasText(comment)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "驳回时审核意见必填");
            }
            m.setAuditStatus(4);
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "action 仅支持 APPROVE/REJECT");
        }
        m.setAuditBy(current.getUserId());
        m.setAuditTime(java.time.LocalDateTime.now());
        m.setUpdateBy(current.getUserId());
        updateById(m);
        sysLogService.log(1, "LIB", "APPROVE".equals(action) ? "资料审核通过" : "资料审核驳回",
                "资料[" + m.getTitle() + "(id=" + m.getId() + ")]"
                        + (StringUtils.hasText(comment) ? "，意见: " + comment : ""), 1);
    }

    // ---------------------------------------------------------------- LIB-11 发布/下架

    public void publish(Long materialId, String action) {
        LearnMaterial m = requireInScope(materialId);
        int status = m.getAuditStatus() == null ? 0 : m.getAuditStatus();
        switch (action) {
            case "PUBLISH" -> {
                if (status == 2) {
                    return; // 幂等：重复发布返回当前状态不报错
                }
                if (status == 3) {
                    m.setAuditStatus(2);
                } else if (status == 0 || status == 4) {
                    // 草稿/驳回直接发布：审核开关开启时仅超管/总管可跳过审核
                    if (sysConfigService.getBool("lib.auditRequired", true) && !isSuperOrHq()) {
                        throw new BizException(ErrorCode.MATERIAL_AUDIT_STATUS_INVALID, "资料须先提交审核");
                    }
                    m.setAuditStatus(2);
                } else {
                    throw new BizException(ErrorCode.MATERIAL_AUDIT_STATUS_INVALID);
                }
            }
            case "OFFLINE" -> {
                if (status == 3) {
                    return;
                }
                if (status != 2) {
                    throw new BizException(ErrorCode.MATERIAL_AUDIT_STATUS_INVALID);
                }
                m.setAuditStatus(3);
            }
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "action 仅支持 PUBLISH/OFFLINE");
        }
        m.setUpdateBy(SecurityUtils.getUserId());
        updateById(m);
        sysLogService.log(1, "LIB", "PUBLISH".equals(action) ? "发布资料" : "下架资料",
                "资料[" + m.getTitle() + "(id=" + m.getId() + ")]", 1);
    }

    // ---------------------------------------------------------------- LIB-12 设置可见范围

    public void updateScope(Long materialId, MaterialScopeRequest req) {
        LearnMaterial m = requireInScope(materialId);
        m.setVisibleScope(scopeChecker.buildScope(req.getScopeType(), req.getScopeOrgIds()));
        m.setUpdateBy(SecurityUtils.getUserId());
        updateById(m);
    }

    // ---------------------------------------------------------------- 公共方法（供学习域其他服务）

    /** 考生端取已发布资料，不存在或未发布抛 30001 */
    public LearnMaterial requirePublished(Long materialId) {
        LearnMaterial m = materialId == null ? null : getById(materialId);
        if (m == null || m.getAuditStatus() == null || m.getAuditStatus() != 2) {
            throw new BizException(ErrorCode.MATERIAL_NOT_FOUND);
        }
        return m;
    }

    public static String materialTypeOf(Integer fileType) {
        if (fileType == null) {
            return null;
        }
        return switch (fileType) {
            case FT_PDF, FT_WORD, FT_PPT -> "DOC";
            case FT_VIDEO -> "VIDEO";
            case FT_RICHTEXT -> "RICHTEXT";
            default -> String.valueOf(fileType);
        };
    }

    public static String codeToStatus(Integer auditStatus) {
        if (auditStatus == null) {
            return null;
        }
        return switch (auditStatus) {
            case 0 -> "DRAFT";
            case 1 -> "PENDING_AUDIT";
            case 2 -> "PUBLISHED";
            case 3 -> "OFFLINE";
            case 4 -> "REJECTED";
            default -> String.valueOf(auditStatus);
        };
    }

    public static Integer statusToCode(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status) {
            case "DRAFT" -> 0;
            case "PENDING_AUDIT" -> 1;
            case "APPROVED", "PUBLISHED" -> 2;
            case "OFFLINE" -> 3;
            case "REJECTED" -> 4;
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "非法 status: " + status);
        };
    }

    // ---------------------------------------------------------------- 内部工具

    private LearnMaterial requireInScope(Long materialId) {
        LearnMaterial m = materialId == null ? null : getById(materialId);
        if (m == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "资料不存在");
        }
        SecurityUtils.checkOrgScope(m.getUploadOrgPath());
        return m;
    }

    /** 上传后初始审核状态：审核开关开启且非超管/总管 → 1待审，否则直接 2已发布 */
    private void applyInitialAuditStatus(LearnMaterial m) {
        boolean auditRequired = sysConfigService.getBool("lib.auditRequired", true);
        if (auditRequired && !isSuperOrHq()) {
            m.setNeedAudit(1);
            m.setAuditStatus(1);
        } else {
            m.setNeedAudit(0);
            m.setAuditStatus(2);
        }
    }

    private boolean isSuperOrHq() {
        LoginUser user = SecurityUtils.getLoginUser();
        return user.isSuperAdmin() || user.hasRole("HQ_ADMIN");
    }

    /** 校验并上传资料文件/设置图文内容，回填 file_type/file_url/file_size/duration_sec/content */
    private void applyFile(LearnMaterial m, MultipartFile file, String materialType,
                           String contentHtml, Integer durationSec) {
        if (!StringUtils.hasText(materialType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "materialType 不能为空");
        }
        switch (materialType) {
            case "DOC" -> {
                if (file == null || file.isEmpty()) {
                    if (m.getFileType() == null) {
                        throw new BizException(ErrorCode.PARAM_ERROR, "DOC 类型资料须上传文件");
                    }
                    return;
                }
                String ext = extOf(file.getOriginalFilename());
                if (!DOC_EXTS.contains(ext)) {
                    throw new BizException(ErrorCode.FILE_TYPE_UNSUPPORTED, "文档仅支持 pdf/doc/docx/ppt/pptx");
                }
                long maxMB = sysConfigService.getInt("lib.docMaxMB", 50);
                if (file.getSize() > maxMB * 1024 * 1024) {
                    throw new BizException(ErrorCode.FILE_TOO_LARGE, "文档大小超过 " + maxMB + "MB 上限");
                }
                m.setFileType("pdf".equals(ext) ? FT_PDF : ("ppt".equals(ext) || "pptx".equals(ext) ? FT_PPT : FT_WORD));
                storeFile(m, file);
            }
            case "VIDEO" -> {
                if (file == null || file.isEmpty()) {
                    if (m.getFileType() == null) {
                        throw new BizException(ErrorCode.PARAM_ERROR, "VIDEO 类型资料须上传文件");
                    }
                    return;
                }
                String ext = extOf(file.getOriginalFilename());
                if (!VIDEO_EXTS.contains(ext)) {
                    throw new BizException(ErrorCode.FILE_TYPE_UNSUPPORTED, "视频仅支持 mp4");
                }
                long maxMB = sysConfigService.getInt("lib.videoMaxMB", 500);
                if (file.getSize() > maxMB * 1024 * 1024) {
                    throw new BizException(ErrorCode.FILE_TOO_LARGE, "视频大小超过 " + maxMB + "MB 上限");
                }
                if (durationSec == null && m.getDurationSec() == null) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "VIDEO 类型资料 durationSec 必填");
                }
                m.setFileType(FT_VIDEO);
                storeFile(m, file);
                // 未手动上传封面时自动从视频抽帧生成封面（手动封面在调用方后赋值，优先级更高）
                autoCoverFromVideo(m, file);
            }
            case "RICHTEXT" -> {
                if (!StringUtils.hasText(contentHtml) && !StringUtils.hasText(m.getContent())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "图文类型资料 contentHtml 必填");
                }
                m.setFileType(FT_RICHTEXT);
                if (StringUtils.hasText(contentHtml)) {
                    m.setContent(contentHtml);
                }
            }
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "非法 materialType: " + materialType);
        }
        if (durationSec != null) {
            if (durationSec < 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "标称时长必须为非负整数秒");
            }
            m.setDurationSec(durationSec);
        }
    }

    private void storeFile(LearnMaterial m, MultipartFile file) {
        String objectName = minioService.buildObjectName("material", file.getOriginalFilename());
        // 记录原始上传文件名（编辑弹窗「当前文件」展示用；对象名是 UUID，无法还原）
        m.setFileName(file.getOriginalFilename());
        java.nio.file.Path tmpIn = null;
        java.nio.file.Path tmpOut = null;
        try {
            // mp4 视频重封装 faststart（moov 元数据盒前置）：浏览器无需下载大量数据即可起播，
            // 避免"打开视频要等好一会儿"。stream copy 不转码、无画质损失、耗时约为文件大小的秒级拷贝。
            // ffmpeg 不可用或重封装失败时回退原样上传，不影响上传主流程（与封面抽帧同一策略）。
            if ("mp4".equalsIgnoreCase(extOf(file.getOriginalFilename()))) {
                try {
                    tmpIn = java.nio.file.Files.createTempFile("exam-up-", ".mp4");
                    file.transferTo(tmpIn);
                    tmpOut = java.nio.file.Paths.get(tmpIn.toString() + ".faststart.mp4");
                    Process p = new ProcessBuilder(ffmpegPath, "-y", "-i", tmpIn.toString(),
                            "-c", "copy", "-movflags", "faststart", tmpOut.toString())
                            .redirectErrorStream(true).start();
                    if (p.waitFor(300, java.util.concurrent.TimeUnit.SECONDS)
                            && p.exitValue() == 0 && java.nio.file.Files.exists(tmpOut)) {
                        try (InputStream in = java.nio.file.Files.newInputStream(tmpOut)) {
                            minioService.upload(objectName, in, java.nio.file.Files.size(tmpOut), "video/mp4");
                        }
                        m.setFileUrl(objectName);
                        m.setFileSize(java.nio.file.Files.size(tmpOut));
                        return;
                    }
                } catch (Exception ignored) {
                    // 重封装失败：继续走原样上传
                } finally {
                    if (tmpIn != null) {
                        java.nio.file.Files.deleteIfExists(tmpIn);
                    }
                    if (tmpOut != null) {
                        java.nio.file.Files.deleteIfExists(tmpOut);
                    }
                }
            }
            try (InputStream in = file.getInputStream()) {
                minioService.upload(objectName, in, file.getSize(), resolveContentType(file));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件上传失败: " + e.getMessage());
        }
        m.setFileUrl(objectName);
        m.setFileSize(file.getSize());
    }

    private String uploadCover(MultipartFile cover) {
        String ext = extOf(cover.getOriginalFilename());
        if (!COVER_EXTS.contains(ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_UNSUPPORTED, "封面仅支持 jpg/png");
        }
        if (cover.getSize() > COVER_MAX_BYTES) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE, "封面大小超过 2MB 上限");
        }
        String objectName = minioService.buildObjectName("cover", cover.getOriginalFilename());
        try (InputStream in = cover.getInputStream()) {
            return minioService.upload(objectName, in, cover.getSize(), cover.getContentType());
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "封面上传失败: " + e.getMessage());
        }
    }

    /**
     * 视频自动封面：ffmpeg 抽帧（默认 1s，输出过小疑似黑帧时后移到 8s 重试）上传 MinIO。
     * ffmpeg 不可用或抽帧失败仅记录日志，不影响资料上传/换版主流程。
     */
    private void autoCoverFromVideo(LearnMaterial m, MultipartFile file) {
        java.nio.file.Path tmpVideo = null;
        java.nio.file.Path tmpJpg = null;
        try {
            tmpVideo = java.nio.file.Files.createTempFile("exam-cover-", ".mp4");
            file.transferTo(tmpVideo);
            tmpJpg = java.nio.file.Files.createTempFile("exam-cover-", ".jpg");
            for (int seekSec : new int[]{1, 8}) {
                java.nio.file.Files.deleteIfExists(tmpJpg);
                Process p = new ProcessBuilder(ffmpegPath, "-y", "-ss", String.valueOf(seekSec),
                        "-i", tmpVideo.toString(), "-frames:v", "1", "-q:v", "3", tmpJpg.toString())
                        .redirectErrorStream(true).start();
                if (!p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    return;
                }
                if (p.exitValue() == 0 && java.nio.file.Files.exists(tmpJpg)
                        && java.nio.file.Files.size(tmpJpg) >= 5 * 1024) {
                    break;
                }
            }
            if (!java.nio.file.Files.exists(tmpJpg) || java.nio.file.Files.size(tmpJpg) < 1024) {
                return;
            }
            String objectName = minioService.buildObjectName("cover", "auto-cover.jpg");
            try (InputStream in = java.nio.file.Files.newInputStream(tmpJpg)) {
                minioService.upload(objectName, in, java.nio.file.Files.size(tmpJpg), "image/jpeg");
            }
            m.setCoverUrl(objectName);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(LearnMaterialService.class)
                    .warn("视频自动封面生成失败（不影响资料保存）: {}", e.getMessage());
        } finally {
            try {
                if (tmpVideo != null) {
                    java.nio.file.Files.deleteIfExists(tmpVideo);
                }
                if (tmpJpg != null) {
                    java.nio.file.Files.deleteIfExists(tmpJpg);
                }
            } catch (Exception ignore) {
                // 临时文件清理失败忽略
            }
        }
    }

    static String extOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /** 客户端未给准确 Content-Type（如 curl/部分网闸上传为 octet-stream）时按扩展名兜底，保证浏览器能识别视频/文档 */
    static String resolveContentType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null && !"application/octet-stream".equalsIgnoreCase(ct)) {
            return ct;
        }
        return switch (extOf(file.getOriginalFilename())) {
            case "mp4" -> "video/mp4";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "application/octet-stream";
        };
    }

    static List<String> parseTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String t : tags.split("[,，]")) {
            if (StringUtils.hasText(t)) {
                result.add(t.trim());
            }
        }
        return result.isEmpty() ? null : result;
    }

    static List<Long> parseOrgIds(String scopeOrgIds) {
        if (!StringUtils.hasText(scopeOrgIds)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : scopeOrgIds.split("[,，]")) {
            if (StringUtils.hasText(part)) {
                try {
                    ids.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException e) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "scopeOrgIds 含非法单位id: " + part);
                }
            }
        }
        return ids;
    }

    /** 列表项：{materialId, title, materialType, catId, catName, coverUrl, tags[], status, fileSize, durationSec, uploaderOrgName, studyCount, createdTime} */
    Map<String, Object> toListItem(LearnMaterial m, Map<Long, String> catNames) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("materialId", m.getId());
        item.put("title", m.getTitle());
        item.put("sortNo", m.getSortNo());
        item.put("materialType", materialTypeOf(m.getFileType()));
        item.put("catId", m.getCategoryId());
        item.put("catName", catNames.get(m.getCategoryId()));
        item.put("summary", m.getSummary());
        item.put("coverUrl", StringUtils.hasText(m.getCoverUrl()) ? minioService.presignedUrl(m.getCoverUrl()) : null);
        item.put("tags", m.getTags() == null ? List.of() : m.getTags());
        item.put("status", codeToStatus(m.getAuditStatus()));
        item.put("fileSize", m.getFileSize());
        item.put("durationSec", m.getDurationSec());
        item.put("uploaderOrgName", null);
        item.put("studyCount", m.getStudyCount());
        item.put("createdTime", m.getCreateTime());
        return item;
    }
}
