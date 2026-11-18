-- ============================================================================
-- V1.2 增量升级脚本（在已部署 V1.0 库 exam_db 上执行）
-- 依据：《03-数据库设计说明书-v1.2.md》
-- 内容：V1.1 结构增量（t_org_user/t_ques_item/t_answer_sheet/t_answer_detail 加列，
--       新增 t_practice_record / t_notify_wx_log）+ V1.2 初始化数据
--       （11 部法规题库分类、exam.defaultDurationMin=10 等系统参数）
-- 全部幂等，可重复执行。
-- ============================================================================

BEGIN;

-- ---------- 1. t_org_user：分组与微信身份字段 ----------
ALTER TABLE t_org_user ADD COLUMN IF NOT EXISTS user_group SMALLINT NOT NULL DEFAULT 9;
ALTER TABLE t_org_user ADD COLUMN IF NOT EXISTS mp_openid  VARCHAR(64) NULL;
ALTER TABLE t_org_user ADD COLUMN IF NOT EXISTS unionid    VARCHAR(64) NULL;
COMMENT ON COLUMN t_org_user.user_group IS '用户分组(V1.1)：1派驻组 2子企业纪委 3纪检委员 9其他纪检相关人员';
COMMENT ON COLUMN t_org_user.mp_openid IS '微信小程序openid(V1.1)，微信登录绑定后写入，用于订阅消息推送寻址';
COMMENT ON COLUMN t_org_user.unionid IS '微信开放平台unionid(V1.1)，跨小程序/公众号统一身份，未绑定开放平台可为空';
CREATE INDEX IF NOT EXISTS idx_org_user_group ON t_org_user (user_group);
CREATE UNIQUE INDEX IF NOT EXISTS uk_org_user_mpopenid ON t_org_user (mp_openid) WHERE mp_openid IS NOT NULL;

-- ---------- 2. t_ques_item：来源制度/章节 ----------
ALTER TABLE t_ques_item ADD COLUMN IF NOT EXISTS source_law    VARCHAR(128) NULL;
ALTER TABLE t_ques_item ADD COLUMN IF NOT EXISTS source_chapter VARCHAR(128) NULL;
COMMENT ON COLUMN t_ques_item.source_law IS '来源制度法规名称(V1.1)，如《中国共产党纪律处分条例》，与题库法规分类对应';
COMMENT ON COLUMN t_ques_item.source_chapter IS '来源章节(V1.1)';
CREATE INDEX IF NOT EXISTS idx_ques_item_law ON t_ques_item (source_law, status);

-- ---------- 3. t_practice_record：练习记录（V1.1 新增表） ----------
CREATE TABLE IF NOT EXISTS t_practice_record (
    id            BIGSERIAL     PRIMARY KEY,
    user_id       BIGINT        NOT NULL,
    user_name     VARCHAR(64)   NOT NULL,
    org_id        BIGINT        NOT NULL,
    org_path      VARCHAR(512)  NOT NULL,
    org_name      VARCHAR(128)  NOT NULL,
    ques_item_id  BIGINT        NOT NULL,
    ques_type     SMALLINT      NOT NULL,
    bank_id       BIGINT        NULL,
    source_law    VARCHAR(128)  NULL,
    source_chapter VARCHAR(128) NULL,
    practice_mode SMALLINT      NOT NULL,
    user_answer   JSONB         NULL,
    is_right      SMALLINT      NOT NULL,
    duration_sec  INT           NULL,
    create_by     BIGINT        NULL,
    create_time   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_practice_mode CHECK (practice_mode IN (1, 2)),
    CONSTRAINT ck_practice_right CHECK (is_right IN (0, 1))
);
COMMENT ON TABLE  t_practice_record IS '练习记录(V1.1)：日常练习一题一行，即时判定，只增不改';
COMMENT ON COLUMN t_practice_record.org_path IS '练习发生时单位物化路径快照，调岗不回刷';
COMMENT ON COLUMN t_practice_record.practice_mode IS '练习模式：1分类练习(CATEGORY) 2随机抽题(RANDOM)';
COMMENT ON COLUMN t_practice_record.is_right IS '1对 0错（练习即时判定，无半对）';
CREATE INDEX IF NOT EXISTS idx_practice_user ON t_practice_record (user_id, create_time);
CREATE INDEX IF NOT EXISTS idx_practice_user_ques ON t_practice_record (user_id, ques_item_id, is_right);
CREATE INDEX IF NOT EXISTS idx_practice_orgpath ON t_practice_record (org_path varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_practice_law ON t_practice_record (source_law, create_time);

-- ---------- 4. t_answer_sheet：答题用时快照 ----------
ALTER TABLE t_answer_sheet ADD COLUMN IF NOT EXISTS duration_sec INT NULL;
COMMENT ON COLUMN t_answer_sheet.duration_sec IS '答题用时快照(秒,V1.1)：交卷时写入=submit_time-start_time(超时按场限时截断)，排名"同分按用时升序"以此为准';
CREATE INDEX IF NOT EXISTS idx_sheet_rank ON t_answer_sheet (session_id, total_score DESC, duration_sec ASC);
UPDATE t_answer_sheet SET duration_sec = GREATEST(0, EXTRACT(EPOCH FROM (submit_time - start_time))::int)
 WHERE submit_time IS NOT NULL AND start_time IS NOT NULL AND duration_sec IS NULL;

-- ---------- 5. t_answer_detail：选项乱序映射快照 ----------
ALTER TABLE t_answer_detail ADD COLUMN IF NOT EXISTS option_order JSONB NULL;
COMMENT ON COLUMN t_answer_detail.option_order IS '选项乱序映射快照(V1.1){"A":"C",...}展示key→原始key，判分映射还原用；填空/简答为NULL';

-- ---------- 6. t_notify_wx_log：微信推送记录（V1.1 新增表） ----------
CREATE TABLE IF NOT EXISTS t_notify_wx_log (
    id          BIGSERIAL     PRIMARY KEY,
    notice_id   BIGINT        NULL,
    scene       SMALLINT      NOT NULL,
    channel     SMALLINT      NOT NULL DEFAULT 1,
    receiver_id BIGINT        NOT NULL,
    mp_openid   VARCHAR(64)   NOT NULL,
    biz_type    VARCHAR(32)   NULL,
    biz_id      BIGINT        NULL,
    template_id VARCHAR(64)   NOT NULL,
    msg_data    JSONB         NULL,
    send_status SMALLINT      NOT NULL DEFAULT 0,
    errcode     INT           NULL,
    errmsg      VARCHAR(256)  NULL,
    retry_of    BIGINT        NULL,
    send_time   TIMESTAMPTZ   NULL,
    create_time TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_notify_wx_scene CHECK (scene BETWEEN 1 AND 4),
    CONSTRAINT ck_notify_wx_status CHECK (send_status BETWEEN 0 AND 3)
);
COMMENT ON TABLE  t_notify_wx_log IS '微信推送记录(V1.1)：订阅消息/模板消息每次推送一行，只增不改，重试追加新行';
CREATE INDEX IF NOT EXISTS idx_notify_wx_receiver ON t_notify_wx_log (receiver_id, create_time);
CREATE INDEX IF NOT EXISTS idx_notify_wx_biz ON t_notify_wx_log (biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_notify_wx_status ON t_notify_wx_log (send_status, create_time);

-- ---------- 7. t_ques_bank：11 部制度法规一级分类（V1.2，id 101~111） ----------
INSERT INTO t_ques_bank (id, parent_id, bank_name, path, level, sort_no, status, create_by)
SELECT v.id, NULL, v.bank_name, '/' || v.id || '/', 1, v.sort_no, 1, NULL
FROM (VALUES
  (101, '《纪检监察机关处理检举控告工作规则》',         1),
  (102, '《中国共产党纪律处分条例》',                   2),
  (103, '《中华人民共和国公职人员政务处分法》',          3),
  (104, '《中华人民共和国监察法》',                     4),
  (105, '《中华人民共和国监察法实施条例》',              5),
  (106, '《中国共产党问责条例》',                       6),
  (107, '《中国共产党党内监督条例》',                   7),
  (108, '《中国共产党纪律检查机关监督执纪工作规则》',    8),
  (109, '《中国共产党廉洁自律准则》',                   9),
  (110, '《国有企业领导人员廉洁从业若干规定》',         10),
  (111, '《国有企业执行八项规定的相关文件》',           11)
) AS v(id, bank_name, sort_no)
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('t_ques_bank','id'), GREATEST((SELECT MAX(id) FROM t_ques_bank), 1));

-- ---------- 8. t_sys_config：V1.1/V1.2 系统参数 ----------
INSERT INTO t_sys_config (config_key, config_value, value_type, config_group, remark) VALUES
('exam.defaultDurationMin',   '10',    2, 'exam',   '默认考试时长(分钟)，建场次预填可改；V1.2依据任务书正式稿由30调整为10，仍为管理员可配'),
('exam.defaultPaperStrategy', '{"mode":"RANDOM_PER_USER","rules":[{"type":"SINGLE","bankIds":[],"withChildren":true,"count":20,"score":5,"difficulty":{"BASIC":14,"ADVANCED":6}}],"shuffleQues":true,"shuffleOption":true}', 4, 'exam', 'V1.1本期默认试卷策略：20题单选×5分=100分，双乱序；bankIds为空表示全部法规分类'),
('practice.pageSize',         '10',    2, 'practice', 'V1.1练习单次抽题数量(分类/随机模式通用)'),
('practice.maxCount',         '50',    2, 'practice', 'V1.1练习单次抽题数量上限'),
('wx.mp.appid',               '<WX_APPID>', 1, 'wx',  'V1.1小程序appid(部署时填)'),
('wx.mp.secret',              '<WX_SECRET>', 1, 'wx', 'V1.1小程序secret(部署时填)'),
('wx.tpl.examPublish',        '<TPL_ID_1>', 1, 'wx',  'V1.1订阅消息模板id：考试发布'),
('wx.tpl.examRemind',         '<TPL_ID_2>', 1, 'wx',  'V1.1订阅消息模板id：考前/截止前提醒'),
('wx.tpl.scorePublish',       '<TPL_ID_3>', 1, 'wx',  'V1.1订阅消息模板id：成绩公布'),
('wx.notify.beforeStartMin',  '1440',  2, 'wx',     'V1.1考前提醒提前量(分钟)，默认24小时'),
('wx.notify.beforeEndMin',    '1440',  2, 'wx',     'V1.1截止前提醒提前量(分钟)，仅未交卷考生'),
('wx.notify.retryLimit',      '3',     2, 'wx',     'V1.1微信推送失败重试上限')
ON CONFLICT (config_key) DO NOTHING;

COMMIT;
