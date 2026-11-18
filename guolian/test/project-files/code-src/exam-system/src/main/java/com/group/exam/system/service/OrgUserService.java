package com.group.exam.system.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.group.exam.common.core.PageResult;
import com.group.exam.common.enums.ErrorCode;
import com.group.exam.common.exception.BizException;
import com.group.exam.common.security.LoginUser;
import com.group.exam.common.security.SecurityUtils;
import com.group.exam.system.dto.UserCreateRequest;
import com.group.exam.system.dto.UserImportRow;
import com.group.exam.system.dto.UserUpdateRequest;
import com.group.exam.system.entity.OrgUnit;
import com.group.exam.system.entity.OrgUser;
import com.group.exam.system.entity.OrgUserRole;
import com.group.exam.system.entity.SysRole;
import com.group.exam.system.mapper.OrgUserMapper;
import com.group.exam.system.mapper.OrgUserRoleMapper;
import com.group.exam.system.util.PasswordGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 人员/账号服务（人员管理 + 认证支撑）
 */
@Slf4j
@Service
public class OrgUserService extends ServiceImpl<OrgUserMapper, OrgUser> {

    /** 登录失败计数 key 前缀（AuthService 写入，解锁时清除） */
    public static final String LOGIN_FAIL_PREFIX = "exam:loginfail:";

    private final OrgUserRoleMapper orgUserRoleMapper;
    private final SysRoleService sysRoleService;
    private final OrgUnitService orgUnitService;
    private final SysLogService sysLogService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    public OrgUserService(OrgUserRoleMapper orgUserRoleMapper, SysRoleService sysRoleService,
                          OrgUnitService orgUnitService, SysLogService sysLogService,
                          TokenService tokenService, PasswordEncoder passwordEncoder,
                          StringRedisTemplate redisTemplate) {
        this.orgUserRoleMapper = orgUserRoleMapper;
        this.sysRoleService = sysRoleService;
        this.orgUnitService = orgUnitService;
        this.sysLogService = sysLogService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    // ---------------------------------------------------------------- 公共方法（供其他模块）

    public OrgUser getByJobNo(String jobNo) {
        if (!StringUtils.hasText(jobNo)) {
            return null;
        }
        return getOne(new QueryWrapper<OrgUser>().eq("job_no", jobNo), false);
    }

    /**
     * 国联APP ticket免密登录的用户匹配：国联员工ID 或 本地工号 任一命中即可。
     * 目标库未执行 v1.3 升级脚本（无 emp_id 列）时降级为仅工号匹配，
     * 降级查询显式排除 emp_id 列，避免裸 SQL 错误直接抛给前端。
     */
    private static final String[] COLUMNS_NO_EMP_ID = {
            "id", "job_no", "user_name", "password_hash", "phone", "gender",
            "org_id", "org_path", "position", "account_status", "pwd_update_time",
            "last_login_time", "last_login_ip", "source", "user_group",
            "mp_openid", "unionid", "create_by", "create_time", "update_by",
            "update_time", "deleted"};

    public OrgUser getByEmpIdOrJobNo(String empId) {
        if (!StringUtils.hasText(empId)) {
            return null;
        }
        try {
            return getOne(new QueryWrapper<OrgUser>().and(q ->
                    q.eq("emp_id", empId).or().eq("job_no", empId)), false);
        } catch (Exception e) {
            log.warn("按emp_id匹配失败（目标库可能未执行v1.3升级脚本），降级为仅工号匹配: {}", e.getMessage());
            return getOne(new QueryWrapper<OrgUser>().select(COLUMNS_NO_EMP_ID).eq("job_no", empId), false);
        }
    }

    /**
     * 按组织路径查询在册人员（account_status != 0 且 deleted = 0）。
     *
     * @param orgPaths     单位物化路径列表
     * @param withChildren true 含下级（path 前缀匹配），false 仅该单位本身
     */
    public List<OrgUser> listActiveByOrgPaths(List<String> orgPaths, boolean withChildren) {
        if (orgPaths == null || orgPaths.isEmpty()) {
            return List.of();
        }
        QueryWrapper<OrgUser> qw = new QueryWrapper<>();
        qw.ne("account_status", 0);
        qw.and(w -> {
            for (String path : orgPaths) {
                if (withChildren) {
                    w.or().likeRight("org_path", path);
                } else {
                    w.or().eq("org_path", path);
                }
            }
        });
        return list(qw);
    }

    /** 注意：listByIds(Collection<Long>) 直接继承自 ServiceImpl */

    /** 各单位在册人数：orgId → count */
    public Map<Long, Long> countGroupByOrg() {
        List<Map<String, Object>> rows = listMaps(new QueryWrapper<OrgUser>()
                .select("org_id", "COUNT(*) AS cnt")
                .groupBy("org_id"));
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("org_id")).longValue(), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }

    // ---------------------------------------------------------------- USER-01 分页查询

    public PageResult<Map<String, Object>> pageUsers(Long orgId, Boolean includeSub, String keyword,
                                                     String roleCode, Integer status, long pageNum, long pageSize) {
        QueryWrapper<OrgUser> qw = new QueryWrapper<>();
        String prefix = SecurityUtils.dataScopePathPrefix();
        qw.likeRight(prefix != null, "org_path", prefix);
        if (orgId != null) {
            OrgUnit unit = orgUnitService.getUnit(orgId);
            if (unit == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "组织单位不存在");
            }
            SecurityUtils.checkOrgScope(unit.getPath());
            if (includeSub == null || includeSub) {
                qw.likeRight("org_path", unit.getPath());
            } else {
                qw.eq("org_id", orgId);
            }
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like("user_name", keyword).or().like("job_no", keyword));
        }
        if (status != null) {
            qw.eq("account_status", status);
        }
        if (StringUtils.hasText(roleCode)) {
            SysRole role = sysRoleService.getByCode(roleCode);
            if (role == null) {
                return PageResult.of(0, List.of(), pageNum, pageSize);
            }
            qw.inSql("id", "SELECT user_id FROM t_org_user_role WHERE role_id = " + role.getId());
        }
        qw.orderByDesc("create_time");
        Page<OrgUser> page = page(new Page<>(pageNum, pageSize), qw);
        return PageResult.of(page).map(this::toListItem);
    }

    /** 列表项：{userId, name, jobNo, phone, gender, orgId, orgName, position, roles[], status, lastLoginTime} */
    public Map<String, Object> toListItem(OrgUser user) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("userId", user.getId());
        item.put("name", user.getUserName());
        item.put("jobNo", user.getJobNo());
        item.put("empId", user.getEmpId());
        item.put("phone", user.getPhone());
        item.put("gender", genderToCode(user.getGender()));
        item.put("orgId", user.getOrgId());
        OrgUnit unit = orgUnitService.getUnit(user.getOrgId());
        item.put("orgName", unit == null ? null : unit.getUnitName());
        item.put("position", user.getPosition());
        item.put("roles", sysRoleService.listRoleCodesByUserId(user.getId()));
        item.put("status", user.getAccountStatus());
        item.put("lastLoginTime", user.getLastLoginTime());
        return item;
    }

    // ---------------------------------------------------------------- USER-02 新增人员

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createUser(UserCreateRequest req) {
        if (getByJobNo(req.getJobNo()) != null) {
            throw new BizException(ErrorCode.JOB_NO_EXISTS);
        }
        OrgUnit unit = requireUnitInScope(req.getOrgId());
        List<SysRole> roles = validateRoles(req.getRoleCodes());

        String initialPassword = PasswordGenerator.generate();
        OrgUser user = new OrgUser();
        user.setJobNo(req.getJobNo());
        user.setUserName(req.getName());
        user.setPasswordHash(passwordEncoder.encode(initialPassword));
        user.setPhone(req.getPhone());
        user.setGender(genderToValue(req.getGender()));
        user.setOrgId(unit.getId());
        user.setOrgPath(unit.getPath());
        user.setPosition(req.getPosition());
        user.setAccountStatus(3);
        user.setSource(1);
        user.setCreateBy(SecurityUtils.getUserId());
        save(user);
        replaceRoles(user.getId(), roles);

        sysLogService.log(2, "USER", "新增人员",
                "新增人员[" + user.getUserName() + "(" + user.getJobNo() + ")]，单位[" + unit.getUnitName() + "]", 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getId());
        result.put("initialPassword", initialPassword);
        return result;
    }

    // ---------------------------------------------------------------- USER-03 人员详情

    public Map<String, Object> userDetail(Long userId) {
        OrgUser user = requireUserInScope(userId);
        Map<String, Object> detail = toListItem(user);
        // t_org_user 无身份证号/入职时间/备注字段，按文档字段返回 null
        detail.put("idCardMasked", null);
        detail.put("entryTime", null);
        detail.put("remark", null);
        detail.put("createdTime", user.getCreateTime());
        detail.put("updatedTime", user.getUpdateTime());
        return detail;
    }

    // ---------------------------------------------------------------- USER-04 编辑人员

    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long userId, UserUpdateRequest req) {
        OrgUser user = requireUserInScope(userId);
        if (req.getName() != null) {
            user.setUserName(req.getName());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (req.getGender() != null) {
            user.setGender(genderToValue(req.getGender()));
        }
        if (req.getPosition() != null) {
            user.setPosition(req.getPosition());
        }
        if (req.getEmpId() != null) {
            user.setEmpId(StringUtils.hasText(req.getEmpId()) ? req.getEmpId() : null);
        }
        if (req.getOrgId() != null && !req.getOrgId().equals(user.getOrgId())) {
            OrgUnit unit = requireUnitInScope(req.getOrgId());
            user.setOrgId(unit.getId());
            user.setOrgPath(unit.getPath());
        }
        user.setUpdateBy(SecurityUtils.getUserId());
        updateById(user);
        if (req.getRoleCodes() != null) {
            List<SysRole> roles = validateRoles(req.getRoleCodes());
            replaceRoles(userId, roles);
        }
        sysLogService.log(2, "USER", "编辑人员", "编辑人员[" + user.getUserName() + "(" + user.getJobNo() + ")]", 1);
    }

    // ---------------------------------------------------------------- USER-05 删除人员

    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        OrgUser user = requireUserInScope(userId);
        user.setAccountStatus(0);
        user.setUpdateBy(SecurityUtils.getUserId());
        updateById(user);
        removeById(userId);
        tokenService.revokeAllRefreshTokens(userId);
        sysLogService.log(2, "USER", "删除人员", "删除人员[" + user.getUserName() + "(" + user.getJobNo() + ")]", 1);
    }

    // ---------------------------------------------------------------- USER-06 状态变更

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Integer status) {
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "status 仅支持 1正常/0停用/2锁定");
        }
        OrgUser user = requireUserInScope(userId);
        user.setAccountStatus(status);
        user.setUpdateBy(SecurityUtils.getUserId());
        updateById(user);
        if (status == 0 || status == 2) {
            // 锁定/停用即刻吊销该用户全部 token（accessToken 过期后自然失效）
            tokenService.revokeAllRefreshTokens(userId);
        } else {
            // 启用/解锁时清除登录失败计数
            redisTemplate.delete(LOGIN_FAIL_PREFIX + user.getJobNo());
        }
        String op = switch (status) {
            case 0 -> "停用账号";
            case 2 -> "锁定账号";
            default -> "启用/解锁账号";
        };
        sysLogService.log(2, "USER", op, "人员[" + user.getUserName() + "(" + user.getJobNo() + ")]", 1);
    }

    // ---------------------------------------------------------------- USER-09 分配角色

    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<String> roleCodes) {
        OrgUser user = requireUserInScope(userId);
        List<SysRole> roles = validateRoles(roleCodes);
        replaceRoles(userId, roles);
        sysLogService.log(2, "USER", "分配角色",
                "人员[" + user.getUserName() + "(" + user.getJobNo() + ")] 角色=" + String.join(",", roleCodes), 1);
    }

    // ---------------------------------------------------------------- USER-10 重置密码

    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(Long userId) {
        OrgUser user = requireUserInScope(userId);
        String newPassword = PasswordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setAccountStatus(3);
        user.setPwdUpdateTime(LocalDateTime.now());
        user.setUpdateBy(SecurityUtils.getUserId());
        updateById(user);
        tokenService.revokeAllRefreshTokens(userId);
        sysLogService.log(2, "USER", "重置密码", "重置人员[" + user.getUserName() + "(" + user.getJobNo() + ")]密码", 1);
        return newPassword;
    }

    // ---------------------------------------------------------------- USER-07 批量导入

    public Map<String, Object> importUsers(MultipartFile file, String mode) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BizException(ErrorCode.FILE_TYPE_UNSUPPORTED, "仅支持 .xlsx 文件");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE, "文件大小超过 10MB 上限");
        }
        boolean upsert = "UPSERT".equalsIgnoreCase(mode);
        // 监听器读取并记录 Excel 物理行号（readRowHolder().getRowIndex() 0 基），空行不占位也不错位
        List<UserImportRow> rows = new ArrayList<>();
        List<Integer> excelRowNos = new ArrayList<>();
        try (InputStream in = file.getInputStream()) {
            EasyExcel.read(in, UserImportRow.class, new ReadListener<UserImportRow>() {
                @Override
                public void invoke(UserImportRow row, AnalysisContext context) {
                    rows.add(row);
                    excelRowNos.add(context.readRowHolder().getRowIndex() + 1);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // no-op
                }
            }).headRowNumber(2).ignoreEmptyRow(false).sheet().doRead();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Excel 解析失败，请使用下载的模板填写");
        }

        List<Map<String, Object>> failures = new ArrayList<>();
        int successCount = 0;
        int totalCount = 0;
        Set<String> seenJobNos = new HashSet<>();
        // 工号/单位编码预查，避免逐行查询
        Map<String, OrgUser> existingByJobNo = new HashMap<>();
        Map<String, OrgUnit> unitByCode = new HashMap<>();
        List<UserImportRow> effectiveRows = new ArrayList<>();
        Map<UserImportRow, Integer> rowNoMap = new IdentityHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            UserImportRow row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            rowNoMap.put(row, excelRowNos.get(i));
            effectiveRows.add(row);
        }
        totalCount = effectiveRows.size();
        Set<String> jobNos = effectiveRows.stream().map(r -> trim(r.getJobNo()))
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        if (!jobNos.isEmpty()) {
            for (OrgUser u : list(new QueryWrapper<OrgUser>().in("job_no", jobNos))) {
                existingByJobNo.put(u.getJobNo(), u);
            }
        }
        Set<String> orgCodes = effectiveRows.stream().map(r -> trim(r.getOrgCode()))
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        if (!orgCodes.isEmpty()) {
            for (OrgUnit u : orgUnitService.list(new QueryWrapper<OrgUnit>().in("unit_code", orgCodes))) {
                unitByCode.put(u.getUnitCode(), u);
            }
        }

        for (UserImportRow row : effectiveRows) {
            int rowNo = rowNoMap.get(row);
            String reason = validateRow(row, seenJobNos, existingByJobNo, unitByCode, upsert);
            if (reason != null) {
                failures.add(failure(rowNo, row, reason));
                continue;
            }
            try {
                OrgUnit unit = unitByCode.get(trim(row.getOrgCode()));
                OrgUser existing = existingByJobNo.get(trim(row.getJobNo()));
                if (existing != null) {
                    // UPSERT：按工号更新（不改密码与账号状态）
                    existing.setUserName(trim(row.getName()));
                    existing.setPhone(trim(row.getPhone()));
                    existing.setGender(genderToValue(trim(row.getGender())));
                    existing.setOrgId(unit.getId());
                    existing.setOrgPath(unit.getPath());
                    existing.setPosition(trim(row.getPosition()));
                    existing.setUpdateBy(SecurityUtils.getUserId());
                    updateById(existing);
                    replaceRoles(existing.getId(), validateRoles(parseRoleCodes(row.getRoleCodes())));
                } else {
                    OrgUser user = new OrgUser();
                    user.setJobNo(trim(row.getJobNo()));
                    user.setUserName(trim(row.getName()));
                    user.setPasswordHash(passwordEncoder.encode("Exam@" + trim(row.getJobNo())));
                    user.setPhone(trim(row.getPhone()));
                    user.setGender(genderToValue(trim(row.getGender())));
                    user.setOrgId(unit.getId());
                    user.setOrgPath(unit.getPath());
                    user.setPosition(trim(row.getPosition()));
                    user.setAccountStatus(3);
                    user.setSource(2);
                    user.setCreateBy(SecurityUtils.getUserId());
                    save(user);
                    replaceRoles(user.getId(), validateRoles(parseRoleCodes(row.getRoleCodes())));
                }
                successCount++;
            } catch (BizException e) {
                failures.add(failure(rowNo, row, e.getMessage()));
            } catch (Exception e) {
                failures.add(failure(rowNo, row, "入库失败: " + e.getMessage()));
            }
        }

        if (successCount > 0) {
            sysLogService.log(2, "USER", "批量导入人员",
                    "导入 " + totalCount + " 行，成功 " + successCount + " 行，失败 " + failures.size() + " 行", 1);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", totalCount);
        result.put("successCount", successCount);
        result.put("failCount", failures.size());
        result.put("failures", failures);
        return result;
    }

    private String validateRow(UserImportRow row, Set<String> seenJobNos, Map<String, OrgUser> existingByJobNo,
                               Map<String, OrgUnit> unitByCode, boolean upsert) {
        if (!StringUtils.hasText(trim(row.getName()))) {
            return "姓名必填";
        }
        if (!StringUtils.hasText(trim(row.getJobNo()))) {
            return "工号必填";
        }
        if (!StringUtils.hasText(trim(row.getPhone()))) {
            return "手机号必填";
        }
        if (!StringUtils.hasText(trim(row.getOrgCode()))) {
            return "单位编码必填";
        }
        if (!seenJobNos.add(trim(row.getJobNo()))) {
            return "工号在文件内重复";
        }
        OrgUnit unit = unitByCode.get(trim(row.getOrgCode()));
        if (unit == null) {
            return "单位编码不存在";
        }
        if (!SecurityUtils.inOrgScope(unit.getPath())) {
            return "单位不在数据权限范围内";
        }
        if (existingByJobNo.containsKey(trim(row.getJobNo())) && !upsert) {
            return "工号已存在";
        }
        try {
            validateRoles(parseRoleCodes(row.getRoleCodes()));
        } catch (BizException e) {
            return e.getMessage();
        }
        return null;
    }

    private Map<String, Object> failure(int rowNo, UserImportRow row, String reason) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("rowNo", rowNo);
        f.put("jobNo", trim(row.getJobNo()));
        f.put("name", trim(row.getName()));
        f.put("reason", reason);
        return f;
    }

    private boolean isBlankRow(UserImportRow row) {
        return !StringUtils.hasText(trim(row.getName())) && !StringUtils.hasText(trim(row.getJobNo()))
                && !StringUtils.hasText(trim(row.getPhone())) && !StringUtils.hasText(trim(row.getOrgCode()));
    }

    // ---------------------------------------------------------------- USER-08 导入模板

    public void writeImportTemplate(OutputStream out) throws IOException {
        List<List<String>> head = List.of(
                List.of("姓名", "必填"),
                List.of("工号", "必填，全局唯一"),
                List.of("手机号", "必填"),
                List.of("性别", "选填，M男/F女"),
                List.of("单位编码", "必填，须为已存在的单位编码"),
                List.of("职务", "选填"),
                List.of("角色编码", "选填，逗号分隔，默认 EXAMINEE"));
        List<List<Object>> example = List.of(List.of("张三", "JW10001", "13800000000", "M", "HQ", "纪检专员", "EXAMINEE"));
        EasyExcel.write(out).head(head).sheet("人员导入").doWrite(example);
    }

    public static String importTemplateFilename() {
        return URLEncoder.encode("人员导入模板.xlsx", StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- 内部工具

    private OrgUser requireUserInScope(Long userId) {
        OrgUser user = getById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "人员不存在");
        }
        SecurityUtils.checkOrgScope(user.getOrgPath());
        return user;
    }

    private OrgUnit requireUnitInScope(Long orgId) {
        OrgUnit unit = orgUnitService.getUnit(orgId);
        if (unit == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "所属单位不存在");
        }
        SecurityUtils.checkOrgScope(unit.getPath());
        return unit;
    }

    /** 校验角色存在性与授权权限（超管/总管任意，其他管理员仅可授 EXAMINEE/MARKER） */
    private List<SysRole> validateRoles(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "角色不能为空");
        }
        List<SysRole> roles = sysRoleService.listByCodes(roleCodes);
        if (roles.size() != new HashSet<>(roleCodes).size()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "存在无效的角色编码");
        }
        LoginUser current = SecurityUtils.getLoginUser();
        if (!current.hasRole("SUPER_ADMIN") && !current.hasRole("HQ_ADMIN")) {
            for (String code : roleCodes) {
                if (!"EXAMINEE".equals(code) && !"MARKER".equals(code)) {
                    throw new BizException(ErrorCode.FORBIDDEN, "仅可授予考生/阅卷教师角色");
                }
            }
        }
        return roles;
    }

    /** 整体替换人员角色 */
    private void replaceRoles(Long userId, List<SysRole> roles) {
        orgUserRoleMapper.delete(new QueryWrapper<OrgUserRole>().eq("user_id", userId));
        Long operatorId = null;
        try {
            operatorId = SecurityUtils.getUserId();
        } catch (Exception ignored) {
        }
        for (SysRole role : roles) {
            OrgUserRole ur = new OrgUserRole();
            ur.setUserId(userId);
            ur.setRoleId(role.getId());
            ur.setCreateBy(operatorId);
            orgUserRoleMapper.insert(ur);
        }
    }

    private List<String> parseRoleCodes(String raw) {
        if (!StringUtils.hasText(trim(raw))) {
            return List.of("EXAMINEE");
        }
        List<String> codes = new ArrayList<>();
        for (String part : trim(raw).split("[,，、/\\s]+")) {
            if (StringUtils.hasText(part)) {
                codes.add(part.trim());
            }
        }
        return codes.isEmpty() ? List.of("EXAMINEE") : codes;
    }

    /** M/男/1 → 1；F/女/2 → 2；其他 null */
    public static Integer genderToValue(String gender) {
        if (!StringUtils.hasText(gender)) {
            return null;
        }
        return switch (gender.trim().toUpperCase()) {
            case "M", "男", "1" -> 1;
            case "F", "女", "2" -> 2;
            default -> null;
        };
    }

    public static String genderToCode(Integer gender) {
        if (gender == null) {
            return null;
        }
        return switch (gender) {
            case 1 -> "M";
            case 2 -> "F";
            default -> null;
        };
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
