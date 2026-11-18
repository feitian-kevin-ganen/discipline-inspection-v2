package com.group.exam.question.controller;

import com.group.exam.common.core.PageResult;
import com.group.exam.common.core.Result;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.question.dto.AuditReq;
import com.group.exam.question.dto.DupCheckReq;
import com.group.exam.question.dto.DupHintVO;
import com.group.exam.question.dto.ImportResultVO;
import com.group.exam.question.dto.ItemCreateResp;
import com.group.exam.question.dto.ItemDetailVO;
import com.group.exam.question.dto.ItemPageVO;
import com.group.exam.question.dto.ItemSaveReq;
import com.group.exam.question.enums.QuesTypeEnum;
import com.group.exam.question.service.QuesDupCheckService;
import com.group.exam.question.service.QuesImportService;
import com.group.exam.question.service.QuesItemService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static com.group.exam.question.controller.QuesBankController.requireAdmin;

/**
 * 题目管理（QUES-05~13）
 */
@RestController
@RequestMapping("/api/v1/admin/ques/items")
public class QuesItemController {

    private final QuesItemService quesItemService;
    private final QuesDupCheckService quesDupCheckService;
    private final QuesImportService quesImportService;

    public QuesItemController(QuesItemService quesItemService,
                              QuesDupCheckService quesDupCheckService,
                              QuesImportService quesImportService) {
        this.quesItemService = quesItemService;
        this.quesDupCheckService = quesDupCheckService;
        this.quesImportService = quesImportService;
    }

    /** QUES-05 题目分页查询 */
    @GetMapping
    public Result<PageResult<ItemPageVO>> page(@RequestParam(required = false) Long bankId,
                                               @RequestParam(required = false) String quesType,
                                               @RequestParam(required = false) String difficulty,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String tag,
                                               @RequestParam(required = false) String genType,
                                               @RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "20") long pageSize) {
        requireAdmin();
        pageNum = Math.max(pageNum, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 200);
        return Result.ok(quesItemService.pageItems(bankId, quesType, difficulty, status, keyword, tag, genType, pageNum, pageSize));
    }

    /** QUES-06 新增题目 */
    @PostMapping
    public Result<ItemCreateResp> create(@Validated @RequestBody ItemSaveReq req) {
        requireAdmin();
        return Result.ok(quesItemService.create(req));
    }

    /** QUES-07 题目详情 */
    @GetMapping("/{itemId}")
    public Result<ItemDetailVO> detail(@PathVariable Long itemId) {
        requireAdmin();
        return Result.ok(quesItemService.detail(itemId));
    }

    /** QUES-08 编辑题目 */
    @PutMapping("/{itemId}")
    public Result<Void> update(@PathVariable Long itemId, @Validated @RequestBody ItemSaveReq req) {
        requireAdmin();
        quesItemService.update(itemId, req);
        return Result.ok();
    }

    /** QUES-09 删除/停用题目（hard=false 停用，hard=true 物理删除） */
    @DeleteMapping("/{itemId}")
    public Result<Void> delete(@PathVariable Long itemId, @RequestParam(defaultValue = "false") boolean hard) {
        requireAdmin();
        quesItemService.disableOrDelete(itemId, hard);
        return Result.ok();
    }

    /** QUES-10 Excel 批量导入题目；存在失败行时返回 20005，data 含行级错误明细 */
    @PostMapping("/import")
    public Result<ImportResultVO> importItems(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "bankId", required = false) Long bankId) {
        requireAdmin();
        if (bankId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请先选择归属题库分类");
        }
        ImportResultVO result = quesImportService.importExcel(file, bankId);
        if (result.getFailCount() > 0) {
            return Result.error(ErrorCode.IMPORT_VALIDATION_FAILED.getCode(),
                    ErrorCode.IMPORT_VALIDATION_FAILED.getMessage(), result);
        }
        return Result.ok(result);
    }

    /** QUES-11 下载题目导入模板 */
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        requireAdmin();
        quesImportService.writeTemplate(response);
    }

    /** QUES-12 题干相似度查重（纯提示，不阻断不入库） */
    @PostMapping("/dup-check")
    public Result<List<DupHintVO>> dupCheck(@Validated @RequestBody DupCheckReq req) {
        requireAdmin();
        Integer quesTypeCode = req.getQuesType() == null || req.getQuesType().isBlank()
                ? null : QuesTypeEnum.parse(req.getQuesType()).getCode();
        return Result.ok(quesDupCheckService.dupCheck(req.getStem(), quesTypeCode, req.getExcludeItemId()));
    }

    /** QUES-13 题目审核（ENABLE 启用 / REJECT 驳回至草稿 / DISABLE 停用） */
    @PostMapping("/{itemId}/audit")
    public Result<Void> audit(@PathVariable Long itemId, @Validated @RequestBody AuditReq req) {
        requireAdmin();
        quesItemService.audit(itemId, req.getAction(), req.getComment());
        return Result.ok();
    }
}
