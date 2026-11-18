-- ============================================================================
-- 纪检监察人员在线考试系统 初始化数据脚本 (data-init.sql)
-- 依据 design/03-数据库设计说明书.md 第 7 章整理
-- 幂等: 可重复执行（唯一约束 ON CONFLICT DO NOTHING / WHERE NOT EXISTS 保护）
-- 管理员初始密码: Admin@123 的 BCrypt 哈希（Spring Security BCryptPasswordEncoder
-- 兼容格式 $2a$10$...），首次登录强制修改（account_status=3）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 7.1 初始组织与账号
-- ----------------------------------------------------------------------------

-- 根组织（集团总部），id 由序列生成后回填 path，示例假定 id=1
INSERT INTO t_org_unit (parent_id, unit_name, unit_code, unit_type, path, level, sort_no, status, create_by)
VALUES (NULL, '集团总部', 'HQ', 1, '/1/', 1, 0, 1, NULL)
ON CONFLICT (unit_code) DO NOTHING;

-- 超级管理员（初始密码 Admin@123 的 BCrypt 哈希，首次登录强制修改）
INSERT INTO t_org_user (job_no, user_name, password_hash, org_id, org_path, account_status, source)
VALUES ('admin', '超级管理员', '$2a$10$rfa/8iuOLe4OJIkvjwY5VOkatgIm1iA4JHNX6pYjqYX7JnXTKQQ7y', 1, '/1/', 3, 1)
ON CONFLICT (job_no) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 7.2 初始角色（FR-ORG-05 / 需求书 2.2）
-- ----------------------------------------------------------------------------
INSERT INTO t_sys_role (role_code, role_name, data_scope, permissions) VALUES
('SUPER_ADMIN',   '超级管理员',   1, '["*"]'),
('HQ_ADMIN',      '总部管理员',   2, '["org:*","user:*","learn:*","ques:*","paper:*","exam:*","mark:*","stat:*","notice:*"]'),
('L2_ADMIN',      '二级管理员',   2, '["org:view","user:*","learn:*","exam:*","stat:view"]'),
('SUB_ADMIN',     '子企业管理员', 2, '["user:*","learn:*","exam:*","stat:view"]'),
('MARKER',        '阅卷教师',     3, '["mark:execute"]'),
('EXAMINEE',      '考生',         3, '["learn:study","exam:take","score:view"]')
ON CONFLICT (role_code) DO NOTHING;

-- 管理员授予超级管理员角色（文档第7章未含，为保障初始账号可用而补充，幂等）
INSERT INTO t_org_user_role (user_id, role_id)
SELECT u.id, r.id FROM t_org_user u, t_sys_role r
WHERE u.job_no = 'admin' AND r.role_code = 'SUPER_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 7.3 默认等级规则（需求书附录 7.1 / FR-PAPER-04）
-- ----------------------------------------------------------------------------
INSERT INTO t_grade_rule (rule_name, full_score, is_default, status, remark)
SELECT '默认等级规则', 100, 1, 1, '优[90,100] 良[80,90) 中[60,80) 差[0,60)'
WHERE NOT EXISTS (SELECT 1 FROM t_grade_rule WHERE is_default = 1);

INSERT INTO t_grade_rule_item (rule_id, grade_code, grade_name, min_score, max_score, lower_inclusive, upper_inclusive, sort_no)
SELECT r.id, v.* FROM t_grade_rule r
JOIN (VALUES
  ('A', '优', 90::numeric, 100::numeric, 1::smallint, 1::smallint, 1),
  ('B', '良', 80::numeric,  90::numeric, 1::smallint, 0::smallint, 2),
  ('C', '中', 60::numeric,  80::numeric, 1::smallint, 0::smallint, 3),
  ('D', '差',  0::numeric,  60::numeric, 1::smallint, 0::smallint, 4)
) AS v(grade_code, grade_name, min_score, max_score, li, ui, sort_no)
ON r.is_default = 1
ON CONFLICT (rule_id, grade_code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 7.4 系统参数（FR-SYS-01 等）
-- ----------------------------------------------------------------------------
INSERT INTO t_sys_config (config_key, config_value, value_type, config_group, remark) VALUES
('exam.passScoreDefault',     '60',    2, 'exam',   '默认及格分数线'),
('exam.switchScreenLimit',    '3',     2, 'exam',   '切屏次数上限'),
('exam.multiTerminal',        'KICK_OLD', 1, 'exam', '多终端策略：KICK_OLD 踢掉旧会话 / REJECT_NEW 拒绝新终端'),
('exam.late.limit.default',   '15',    2, 'exam',   '默认允许迟到时限(分钟)'),
('exam.remind.before.min',    '5',     2, 'exam',   '限时交卷提醒提前量(分钟)'),
('learn.completeThreshold',   '0.9',   2, 'learn',  '学习完成进度阈值(有效时长占标称比例)'),
('learn.heartbeatSec',        '15',    2, 'learn',  '学习心跳间隔(秒)'),
('learn.heartbeat.timeout',   '60',    2, 'learn',  '心跳超时截断阈值(秒)，超时不计时'),
('lib.docMaxMB',              '50',    2, 'lib',    '文档上传上限(MB)'),
('lib.videoMaxMB',            '500',   2, 'lib',    '视频上传上限(MB)'),
('lib.auditRequired',         'true',  3, 'lib',    '下级单位上传资料是否需上级审核'),
('ques.auditRequired',        'true',  3, 'ques',   '题目是否需审核后启用'),
('mark.double.enabled',       'false', 3, 'mark',   '是否启用双评仲裁(P1)'),
('exam.scoreSegmentDefault',  '[{"label":"0-59","min":0,"max":59.99},{"label":"60-69","min":60,"max":69.99},{"label":"70-79","min":70,"max":79.99},{"label":"80-89","min":80,"max":89.99},{"label":"90-100","min":90,"max":100}]', 4, 'stat', '分数段统计口径，可配置'),
('stat.rankPublic',           'false', 3, 'stat',   '个人单位内排名是否公开'),
('backup.retain.days',        '30',    2, 'sys',    '备份保留天数'),
('security.pwd.expire.days',  '90',    2, 'sys',    '密码有效期(天)，0为不过期'),
('security.loginFailLock',    '5',     2, 'sys',    '连续登录失败锁定次数')
ON CONFLICT (config_key) DO NOTHING;
