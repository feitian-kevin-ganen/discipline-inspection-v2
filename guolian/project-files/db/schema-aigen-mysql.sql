-- ============================================================================
-- AI 辅助生题功能建表（V1.4）
-- t_ai_gen_task    生成任务（一个学习资料一个任务）
-- t_ai_gen_question 生成的题目（采纳后进入正式题库）
-- 兼容 MySQL 8.0 / PolarDB for MySQL；幂等（IF NOT EXISTS）。
-- ============================================================================

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
