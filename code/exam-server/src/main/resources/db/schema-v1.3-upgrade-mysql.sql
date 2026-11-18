-- ============================================================================
-- v1.3 升级脚本（MySQL 8.0 / PolarDB for MySQL）
-- 适用：由 PostgreSQL 迁移而来、尚未合入 v1 测试机发布线的 exam_db
-- 内容：国联员工ID、AI 生题来源、学习资料原始文件名、AI 生题任务表
-- 注意：MySQL 不支持 ADD COLUMN IF NOT EXISTS，本脚本为一次性升级脚本。
-- ============================================================================

-- 国联 APP 单点登录：员工ID 与本地账号绑定
ALTER TABLE t_org_user
    ADD COLUMN emp_id VARCHAR(64) NULL DEFAULT NULL COMMENT '国联APP员工ID（单点登录匹配用）' AFTER unionid;
ALTER TABLE t_org_user
    ADD UNIQUE INDEX uk_org_user_emp_id (emp_id);

-- 题目来源：AI 生成 / 人工录入
ALTER TABLE t_ques_item
    ADD COLUMN gen_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT '题目来源 AI/MANUAL' AFTER source_chapter;

-- 学习资料原始上传文件名（展示用，历史数据可为空）
ALTER TABLE t_learn_material
    ADD COLUMN file_name VARCHAR(256) NULL COMMENT '原始上传文件名' AFTER file_url;

-- AI 辅助生题
CREATE TABLE IF NOT EXISTS t_ai_gen_task (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    material_id    BIGINT       NOT NULL COMMENT '学习资料id',
    material_title VARCHAR(256) NOT NULL COMMENT '学习资料标题（冗余）',
    bank_id        BIGINT       NULL COMMENT '采纳入库的目标题库',
    ques_count     INT          NULL DEFAULT 0 COMMENT '生成题目总数',
    type_summary   VARCHAR(512) NULL COMMENT '题目明细汇总（题型:数量；难度:数量）',
    status         VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
    error_msg      VARCHAR(1024) NULL COMMENT '失败原因',
    start_time     DATETIME(6)  NULL COMMENT '开始生成时间',
    end_time       DATETIME(6)  NULL COMMENT '结束时间',
    create_by      BIGINT       NULL,
    create_time    DATETIME(6)  NULL,
    update_time    DATETIME(6)  NULL,
    deleted        SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ai_task_material (material_id),
    KEY idx_ai_task_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI生题任务';

CREATE TABLE IF NOT EXISTS t_ai_gen_question (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id      BIGINT       NOT NULL COMMENT '生成任务id',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ADOPTED/DISCARDED',
    ques_type    VARCHAR(16)  NOT NULL COMMENT '题型 API 编码 SINGLE/MULTI/JUDGE/FILL/ESSAY',
    difficulty   VARCHAR(16)  NOT NULL COMMENT '难度 API 编码 EASY/MEDIUM/HARD',
    stem         TEXT         NOT NULL COMMENT '题干',
    options_json TEXT         NULL COMMENT '选择题选项 JSON',
    answer_json  TEXT         NULL COMMENT '答案 JSON',
    analysis     TEXT         NULL COMMENT '解析',
    adopt_item_id BIGINT      NULL COMMENT '采纳后对应的正式题目id',
    create_time  DATETIME(6)  NULL,
    update_time  DATETIME(6)  NULL,
    deleted      SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ai_q_task (task_id),
    KEY idx_ai_q_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI生成题目';
