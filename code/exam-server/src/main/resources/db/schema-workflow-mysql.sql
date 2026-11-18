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

-- 流程中心扩展：抄送、表单、数据源、消息注册、节点超时、催办记录（2026-11-18）
CREATE TABLE IF NOT EXISTS flow_copy (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    proc_inst_id VARCHAR(64)  NOT NULL COMMENT '流程实例 ID',
    task_def_key VARCHAR(64)  DEFAULT NULL COMMENT '触发抄送的节点 key',
    task_name    VARCHAR(128) DEFAULT NULL COMMENT '触发抄送的节点名称',
    title        VARCHAR(255) DEFAULT NULL COMMENT '业务标题',
    from_user_id BIGINT       DEFAULT NULL COMMENT '抄送人用户 ID',
    to_user_id   BIGINT       NOT NULL COMMENT '接收人用户 ID',
    read_flag    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0=未读 1=已读',
    create_time  DATETIME     DEFAULT NULL,
    read_time    DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_copy_to (to_user_id, read_flag),
    KEY idx_copy_proc (proc_inst_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程抄送';

CREATE TABLE IF NOT EXISTS flow_form_def (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    form_key     VARCHAR(64)  NOT NULL COMMENT '表单唯一标识',
    name         VARCHAR(128) NOT NULL COMMENT '表单名称',
    schema_json  LONGTEXT     COMMENT '字段定义 JSON 数组',
    proc_def_key VARCHAR(64)  DEFAULT NULL COMMENT '绑定流程定义 key',
    version      INT          NOT NULL DEFAULT 1,
    status       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0=启用 1=停用',
    remark       VARCHAR(255) DEFAULT NULL,
    create_by    VARCHAR(64)  DEFAULT NULL,
    create_time  DATETIME     DEFAULT NULL,
    update_time  DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_form_key (form_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程表单模板';

CREATE TABLE IF NOT EXISTS flow_dblink (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(128) NOT NULL COMMENT '数据源名称',
    db_type        VARCHAR(32)  DEFAULT NULL COMMENT 'mysql/postgresql/oracle 等',
    jdbc_url       VARCHAR(512) DEFAULT NULL,
    username       VARCHAR(128) DEFAULT NULL,
    password       VARCHAR(256) DEFAULT NULL,
    allowed_tables VARCHAR(512) DEFAULT NULL COMMENT '白名单表（逗号分隔）',
    status         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0=启用 1=停用',
    create_time    DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_dblink_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程外部数据源';

CREATE TABLE IF NOT EXISTS flow_message_registry (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    message_ref VARCHAR(64)  NOT NULL COMMENT 'BPMN 消息引用名',
    channel     VARCHAR(32)  DEFAULT NULL COMMENT '通知渠道标识',
    topic       VARCHAR(128) DEFAULT NULL,
    description VARCHAR(255) DEFAULT NULL,
    create_time DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_ref (message_ref)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程消息注册';

CREATE TABLE IF NOT EXISTS flow_task_ext (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    proc_def_id           VARCHAR(128) NOT NULL COMMENT '流程定义 ID',
    task_def_key          VARCHAR(64)  NOT NULL COMMENT '节点 key',
    task_name             VARCHAR(128) DEFAULT NULL,
    timeout_hours         INT          NOT NULL DEFAULT 24 COMMENT '超时小时数',
    notify_interval_hours INT          DEFAULT NULL COMMENT '催办间隔小时数',
    create_time           DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_ext (proc_def_id, task_def_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程节点超时配置';

CREATE TABLE IF NOT EXISTS flow_notify_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    proc_inst_id VARCHAR(64)  DEFAULT NULL,
    task_id      VARCHAR(64)  DEFAULT NULL,
    notify_type  VARCHAR(32)  DEFAULT NULL COMMENT 'URGE/MESSAGE',
    channel      VARCHAR(32)  DEFAULT NULL COMMENT 'INNER 等',
    receiver_id  BIGINT       DEFAULT NULL,
    content      VARCHAR(512) DEFAULT NULL,
    send_status  VARCHAR(16)  DEFAULT NULL COMMENT 'SUCCESS/FAIL',
    fail_reason  VARCHAR(255) DEFAULT NULL,
    create_time  DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_notify_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程催办/消息发送记录';
