-- ============================================================================
-- 测试机：把手机号 18706197825 的账号提升为超级管理员（含工作流配置菜单）
-- 目标库：10.64.16.14:4886 exam_db（MySQL 8.0）
-- 幂等：按手机号定位账号，已授权则跳过；可重复执行。
-- 执行示例：
--   /root/migration/tools/mysql/mysql.sh -h10.64.16.14 -P4886 -uexam_admin -p'***' exam_db \
--     -e "source /root/incremental-deploy/grant-super-admin-18706197825.sql;"
-- 说明：门户 ticket 登录按 emp_id 匹配本地账号，手机号仅用于定位记录；
--       若查询结果为空，说明该账号尚未绑定 emp_id，需先在系统内建号或绑定国联员工ID。
-- ============================================================================

UPDATE t_org_user SET account_status = 1, update_time = NOW(6)
WHERE phone = '18706197825' AND deleted = 0;

INSERT INTO t_org_user_role (user_id, role_id, create_time, update_time)
SELECT u.id, 1, NOW(6), NOW(6)
FROM t_org_user u
WHERE u.phone = '18706197825' AND u.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM t_org_user_role r WHERE r.user_id = u.id AND r.role_id = 1);

SELECT u.id, u.job_no, u.user_name, u.phone, u.emp_id, u.account_status,
       GROUP_CONCAT(r.role_id ORDER BY r.role_id) AS role_ids
FROM t_org_user u
LEFT JOIN t_org_user_role r ON r.user_id = u.id
WHERE u.phone = '18706197825' AND u.deleted = 0
GROUP BY u.id, u.job_no, u.user_name, u.phone, u.emp_id, u.account_status;
