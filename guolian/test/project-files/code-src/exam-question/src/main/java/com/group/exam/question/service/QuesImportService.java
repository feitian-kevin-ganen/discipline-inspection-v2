package com.group.exam.question.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.question.dto.ImportResultVO;
import com.group.exam.question.dto.ItemImportRow;
import com.group.exam.question.dto.ItemSaveReq;
import com.group.exam.question.entity.QuesBank;
import com.group.exam.question.entity.QuesItem;
import com.group.exam.question.enums.DifficultyEnum;
import com.group.exam.question.enums.QuesTypeEnum;
import com.group.exam.question.support.QuesItemAssembler;
import com.group.exam.question.support.StemTextUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 题目 Excel 批量导入（QUES-10）与导入模板下载（QUES-11）。
 *
 * <p>模板为单 sheet（题型列区分题型，列结构见 {@link ItemImportRow}），
 * 另附"填写说明" sheet。逐行校验（题型合法、选项与答案匹配、分类存在），
 * 行级错误汇总返回；合法行入库，初始状态按 ques.auditRequired（需审→1待审，免审→2启用）。
 */
@Service
public class QuesImportService {

    /** 导入文件大小上限 20MB（API 文档 QUES-10） */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private static final String[] OPTION_COLUMNS = {"A", "B", "C", "D", "E", "F"};

    private final QuesItemService quesItemService;
    private final QuesBankService quesBankService;
    private final SysConfigService sysConfigService;

    public QuesImportService(QuesItemService quesItemService,
                             QuesBankService quesBankService,
                             SysConfigService sysConfigService) {
        this.quesItemService = quesItemService;
        this.quesBankService = quesBankService;
        this.sysConfigService = sysConfigService;
    }

    /**
     * Excel 批量导入（QUES-10）
     *
     * @param file            .xlsx 文件
     * @param defaultBankId   默认归属分类（行内"题库分类"列可覆盖）
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importExcel(MultipartFile file, Long defaultBankId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "导入文件不能为空");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new BizException(ErrorCode.FILE_TYPE_UNSUPPORTED, "仅支持 .xlsx 格式导入（暂不支持 Word）");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE, "导入文件大小不能超过 20MB");
        }
        QuesBank defaultBank = defaultBankId == null ? null : quesBankService.getById(defaultBankId);
        if (defaultBank == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "默认题库分类不存在: " + defaultBankId);
        }

        List<ItemImportRow> rows = new ArrayList<>();
        List<Integer> excelRowNos = new ArrayList<>();
        try {
            // 监听器读取并记录 Excel 物理行号（0 基 +1），空行不占位也不错位
            EasyExcel.read(file.getInputStream(), ItemImportRow.class, new ReadListener<ItemImportRow>() {
                @Override
                public void invoke(ItemImportRow row, AnalysisContext context) {
                    rows.add(row);
                    excelRowNos.add(context.readRowHolder().getRowIndex() + 1);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // no-op
                }
            }).ignoreEmptyRow(false).sheet(0).doRead();
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Excel 解析失败，请使用导入模板: " + e.getMessage());
        }

        // 分类名 → 分类（行内覆盖默认分类时按名称精确匹配）
        Map<String, QuesBank> bankByName = new LinkedHashMap<>();
        for (QuesBank bank : quesBankService.list(new QueryWrapper<QuesBank>().select("id", "bank_name"))) {
            bankByName.putIfAbsent(bank.getBankName(), bank);
        }

        ImportResultVO result = new ImportResultVO();
        List<QuesItem> validItems = new ArrayList<>();
        Set<String> seenMd5 = new HashSet<>();
        int initialStatus = sysConfigService.initialItemStatus();
        Long operatorId = currentUserIdOrNull();

        for (int i = 0; i < rows.size(); i++) {
            ItemImportRow row = rows.get(i);
            int rowNo = excelRowNos.get(i); // Excel 物理行号
            if (isBlankRow(row)) {
                continue;
            }
            result.setTotalCount(result.getTotalCount() + 1);
            String stemSummary = StemTextUtils.summary(row.getStem(), 50);
            try {
                QuesItem item = buildItem(row, defaultBank, bankByName);
                if (!seenMd5.add(item.getStemMd5())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "题干在文件内重复");
                }
                item.setStatus(initialStatus);
                item.setCreateBy(operatorId);
                validItems.add(item);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (BizException e) {
                result.setFailCount(result.getFailCount() + 1);
                result.addFailure(rowNo, stemSummary, e.getMessage());
            } catch (Exception e) {
                result.setFailCount(result.getFailCount() + 1);
                result.addFailure(rowNo, stemSummary, "行解析失败: " + e.getMessage());
            }
        }
        if (!validItems.isEmpty()) {
            quesItemService.saveBatch(validItems);
        }
        return result;
    }

    /**
     * 下载导入模板（QUES-11）：sheet1 导入模板（表头），sheet2 填写说明
     */
    public void writeTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = URLEncoder.encode("题目导入模板.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        try (ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build()) {
            WriteSheet templateSheet = EasyExcel.writerSheet(0, "导入模板").head(ItemImportRow.class).build();
            writer.write(List.of(), templateSheet);

            List<List<String>> guide = new ArrayList<>();
            guide.add(List.of("题目导入填写说明"));
            guide.add(List.of("1. 题库分类：可空，空则归入上传时选择的默认分类；填写时须为已存在的分类名称。"));
            guide.add(List.of("2. 题型：单选/多选/判断/填空/简答（或 SINGLE/MULTI/JUDGE/FILL/ESSAY）。"));
            guide.add(List.of("3. 题干：必填，支持富文本 HTML。"));
            guide.add(List.of("4. 选项A~F：选择题至少填 2 个；判断/填空/简答无需填写。"));
            guide.add(List.of("5. 答案：单选填选项字母（如 A）；多选填多个字母（如 ABC 或 A,C）；"
                    + "判断填 T/F（或 对/错）；填空多个空用 || 分隔，同一空的同义答案用 | 分隔；简答直接填参考答案。"));
            guide.add(List.of("6. 难度：易/中/难（或 EASY/MEDIUM/HARD），可空，默认 中。"));
            guide.add(List.of("7. 知识点标签：多个标签用逗号分隔，可空。"));
            guide.add(List.of("8. 分值建议：数字，可空。"));
            WriteSheet guideSheet = EasyExcel.writerSheet(1, "填写说明").build();
            writer.write(guide, guideSheet);
        }
    }

    // ------------------------------------------------------------------

    private QuesItem buildItem(ItemImportRow row, QuesBank defaultBank, Map<String, QuesBank> bankByName) {
        ItemSaveReq req = new ItemSaveReq();
        // 分类
        QuesBank bank = defaultBank;
        if (row.getBankName() != null && !row.getBankName().isBlank()) {
            bank = bankByName.get(row.getBankName().trim());
            if (bank == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "题库分类不存在: " + row.getBankName().trim());
            }
        }
        req.setBankId(bank.getId());
        // 题型/难度
        QuesTypeEnum type = QuesTypeEnum.parse(row.getQuesType());
        req.setQuesType(type.getApiCode());
        DifficultyEnum difficulty = DifficultyEnum.parseOrNull(row.getDifficulty());
        req.setDifficulty((difficulty == null ? DifficultyEnum.MEDIUM : difficulty).getApiCode());
        // 题干
        if (row.getStem() == null || row.getStem().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "题干不能为空");
        }
        req.setStem(row.getStem().trim());
        req.setAnalysis(trimToNull(row.getAnalysis()));
        req.setSource(trimToNull(row.getSource()));
        // 选项与答案
        switch (type) {
            case SINGLE, MULTI -> buildChoice(row, req, type);
            case JUDGE -> buildJudge(row, req);
            case FILL -> buildFill(row, req);
            case ESSAY -> buildEssay(row, req);
        }
        // 标签
        if (row.getTags() != null && !row.getTags().isBlank()) {
            List<String> tags = new ArrayList<>();
            for (String t : row.getTags().split("[,，、;；]")) {
                if (!t.isBlank()) {
                    tags.add(t.trim());
                }
            }
            req.setTags(tags.isEmpty() ? null : tags);
        }
        // 分值建议
        if (row.getScoreSuggest() != null && !row.getScoreSuggest().isBlank()) {
            try {
                req.setScoreSuggest(new BigDecimal(row.getScoreSuggest().trim()));
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.PARAM_ERROR, "分值建议格式错误: " + row.getScoreSuggest());
            }
        }

        QuesItem item = new QuesItem();
        item.setBankId(req.getBankId());
        item.setStem(req.getStem());
        item.setAnalysis(req.getAnalysis());
        item.setScoreSuggest(req.getScoreSuggest());
        item.setKnowledgeTags(req.getTags());
        item.setSource(req.getSource());
        QuesItemAssembler.applyStorage(req, item);
        return item;
    }

    private void buildChoice(ItemImportRow row, ItemSaveReq req, QuesTypeEnum type) {
        String[] contents = {row.getOptionA(), row.getOptionB(), row.getOptionC(),
                row.getOptionD(), row.getOptionE(), row.getOptionF()};
        List<ItemSaveReq.OptionReq> options = new ArrayList<>();
        for (int i = 0; i < OPTION_COLUMNS.length; i++) {
            if (contents[i] != null && !contents[i].isBlank()) {
                ItemSaveReq.OptionReq opt = new ItemSaveReq.OptionReq();
                opt.setOptKey(OPTION_COLUMNS[i]);
                opt.setContent(contents[i].trim());
                options.add(opt);
            }
        }
        if (options.size() < 2) {
            throw new BizException(ErrorCode.PARAM_ERROR, "选项缺失（选择题至少 2 个选项）");
        }
        Set<String> answerKeys = parseAnswerLetters(row.getAnswer());
        Set<String> optionKeys = new HashSet<>();
        options.forEach(o -> optionKeys.add(o.getOptKey()));
        if (answerKeys.isEmpty() || !optionKeys.containsAll(answerKeys)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "答案未配置或与选项不匹配（须为选项字母）");
        }
        if (type == QuesTypeEnum.SINGLE && answerKeys.size() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单选题答案须为 1 个选项字母");
        }
        for (ItemSaveReq.OptionReq opt : options) {
            opt.setIsAnswer(answerKeys.contains(opt.getOptKey()));
        }
        req.setOptions(options);
    }

    private void buildJudge(ItemImportRow row, ItemSaveReq req) {
        String value = row.getAnswer() == null ? "" : row.getAnswer().trim();
        String normalized = switch (value) {
            case "T", "t", "对", "正确", "√", "是" -> "T";
            case "F", "f", "错", "错误", "×", "否" -> "F";
            default -> null;
        };
        if (normalized == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "答案未配置（判断题填 T/F 或 对/错）");
        }
        req.setAnswer(Map.of("value", normalized));
    }

    private void buildFill(ItemImportRow row, ItemSaveReq req) {
        String answer = row.getAnswer() == null ? "" : row.getAnswer().trim();
        if (answer.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "答案未配置（填空题多个空用 || 分隔）");
        }
        List<Map<String, Object>> blanks = new ArrayList<>();
        String[] parts = answer.split("\\|\\|", -1);
        int no = 1;
        for (String part : parts) {
            List<String> answers = new ArrayList<>();
            for (String syn : part.split("\\|", -1)) {
                if (!syn.isBlank()) {
                    answers.add(syn.trim());
                }
            }
            if (answers.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "填空题存在未配置答案的空（第 " + no + " 个）");
            }
            Map<String, Object> blank = new LinkedHashMap<>();
            blank.put("no", no++);
            blank.put("answers", answers);
            blank.put("ordered", false);
            blanks.add(blank);
        }
        req.setAnswer(Map.of("blanks", blanks));
        req.setFillConfig(QuesItemAssembler.defaultFillConfig());
    }

    private void buildEssay(ItemImportRow row, ItemSaveReq req) {
        String reference = row.getAnswer() == null ? "" : row.getAnswer().trim();
        if (reference.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "答案未配置（简答题需参考答案）");
        }
        req.setAnswer(Map.of("reference", reference));
    }

    /** 解析选择题答案字母：支持 "A"、"ABC"、"A,C"、"A，C" 等写法，兼容全角字母 */
    private Set<String> parseAnswerLetters(String answer) {
        Set<String> keys = new HashSet<>();
        if (answer == null) {
            return keys;
        }
        for (String token : answer.toUpperCase(java.util.Locale.ROOT).split("[,，、;；\\s]+")) {
            for (char c : token.toCharArray()) {
                // 全角 Ａ-Ｚ 转半角
                if (c >= 'Ａ' && c <= 'Ｚ') {
                    c = (char) (c - 'Ａ' + 'A');
                }
                if (c >= 'A' && c <= 'Z') {
                    keys.add(String.valueOf(c));
                }
            }
        }
        return keys;
    }

    private boolean isBlankRow(ItemImportRow row) {
        return (row.getStem() == null || row.getStem().isBlank())
                && (row.getQuesType() == null || row.getQuesType().isBlank());
    }

    private String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Long currentUserIdOrNull() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
