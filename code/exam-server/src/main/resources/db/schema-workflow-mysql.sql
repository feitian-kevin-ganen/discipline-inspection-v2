-- 审批流：业务单据与流程实例绑定表（MySQL 8）
-- 其余流转数据由 Flowable 自带的 ACT_* 表维护（flowable.database-schema-update=true 自动建表）
CREATE TABLE IF NOT EXISTS flow_biz_instance (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_type      VARCHAR(64)  NOT NULL COMMENT '业务类型，同时是流程定义 key（ques_item_audit / material_audit）',
    biz_id        BIGINT       NOT NULL COMMENT '业务单据 ID（题目 ID / 资料 ID）',
    proc_inst_id  VARCHAR(64)  NOT NULL COMMENT 'Flowable 流程实例 ID',
    proc_def_key  VARCHAR(64)  DEFAULT NULL COMMENT '流程定义 key',
    title         VARCHAR(255) DEFAULT NULL COMMENT '单据标题（待办列表展示）',
    start_user_id BIGINT       DEFAULT NULL COMMENT '发起人用户 ID',
    status        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/APPROVED/REJECTED/STOPPED',
    start_time    DATETIME     DEFAULT NULL COMMENT '发起时间',
    end_time      DATETIME     DEFAULT NULL COMMENT '结束时间',
    create_time   DATETIME     DEFAULT NULL,
    update_time   DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_proc_inst (proc_inst_id),
    KEY idx_flow_biz (biz_type, biz_id, status),
    KEY idx_flow_start_user (start_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业务单据与流程实例绑定';
