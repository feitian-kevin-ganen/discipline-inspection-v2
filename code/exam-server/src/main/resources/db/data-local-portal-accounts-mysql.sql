-- ============================================================================
-- 本机模拟国联门户账号（MySQL 8.0）
-- 用途：/portal/ 门户登录页按手机号匹配本地账号，再走模拟 ticket 单点登录。
-- 说明：密码仅用于本机直连登录，BCrypt 哈希对应密码见提交说明；门户登录不校验密码。
-- 幂等：按 emp_id 判断，已存在则跳过。
-- ============================================================================

-- 管理员：手机号 13822211100，国联员工ID 10536
INSERT INTO t_org_user
    (id, job_no, user_name, password_hash, phone, gender, org_id, org_path, position,
     account_status, source, user_group, emp_id, create_time, update_time, deleted)
SELECT 40, '10536', '门户管理员', '$2b$10$xbKXvnLgLU5qK2hPJaUcBef/olJDNGuvNjg/VRV3BpO7lJuMS.ssy',
       '13822211100', 1, 1, '/1/', '管理员', 1, 1, 9, '10536', NOW(6), NOW(6), 0
WHERE NOT EXISTS (SELECT 1 FROM t_org_user WHERE emp_id = '10536');

INSERT INTO t_org_user_role (user_id, role_id, create_time, update_time)
SELECT 40, 2, NOW(6), NOW(6)
WHERE EXISTS (SELECT 1 FROM t_org_user WHERE id = 40)
  AND NOT EXISTS (SELECT 1 FROM t_org_user_role WHERE user_id = 40 AND role_id = 2);

-- 员工（考生）：手机号 13585020876，国联员工ID 30
INSERT INTO t_org_user
    (id, job_no, user_name, password_hash, phone, gender, org_id, org_path, position,
     account_status, source, user_group, emp_id, create_time, update_time, deleted)
SELECT 41, '30', '门户员工', '$2b$10$xbKXvnLgLU5qK2hPJaUcBef/olJDNGuvNjg/VRV3BpO7lJuMS.ssy',
       '13585020876', 1, 4, '/1/2/4/', '员工', 1, 1, 9, '30', NOW(6), NOW(6), 0
WHERE NOT EXISTS (SELECT 1 FROM t_org_user WHERE emp_id = '30');

INSERT INTO t_org_user_role (user_id, role_id, create_time, update_time)
SELECT 41, 6, NOW(6), NOW(6)
WHERE EXISTS (SELECT 1 FROM t_org_user WHERE id = 41)
  AND NOT EXISTS (SELECT 1 FROM t_org_user_role WHERE user_id = 41 AND role_id = 6);

-- 超级管理员：手机号 18706197825（周云飞，工号 00001）
-- 用途：本地模拟门户登录后可看到全部菜单（含工作流配置）。
-- 幂等：按手机号定位，已授权则跳过。
UPDATE t_org_user SET account_status = 1, update_time = NOW(6)
WHERE phone = '18706197825' AND deleted = 0;

INSERT INTO t_org_user_role (user_id, role_id, create_time, update_time)
SELECT u.id, 1, NOW(6), NOW(6)
FROM t_org_user u
WHERE u.phone = '18706197825' AND u.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM t_org_user_role r WHERE r.user_id = u.id AND r.role_id = 1);
