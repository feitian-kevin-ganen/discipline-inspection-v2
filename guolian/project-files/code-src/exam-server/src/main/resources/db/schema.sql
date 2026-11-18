-- ============================================================================
-- 纪检监察人员在线考试系统 数据库结构脚本 (schema.sql)
-- 依据 design/03-数据库设计说明书.md 第 4 章逐表 DDL 提取整理
-- 数据库: PostgreSQL 16, 库名 exam_db, Schema public
-- 说明: 与文档一致，未加 IF NOT EXISTS，设计为在空库执行一次
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0. 通用触发器函数：update_time 自动维护（文档 1.5）
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION f_touch_update_time() RETURNS trigger AS $$
BEGIN
  NEW.update_time := now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- 1. 组织与人员域（文档 4.1）
-- ----------------------------------------------------------------------------

-- 4.1.1 t_org_unit 组织架构表
CREATE TABLE t_org_unit (
    id            BIGSERIAL    PRIMARY KEY,
    parent_id     BIGINT       NULL,
    unit_name     VARCHAR(128) NOT NULL,
    unit_code     VARCHAR(64)  NOT NULL,
    unit_type     SMALLINT     NOT NULL,
    path          VARCHAR(512) NOT NULL,
    level         INT          NOT NULL,
    leader_name   VARCHAR(64)  NULL,
    contact_phone VARCHAR(32)  NULL,
    sort_no       INT          NOT NULL DEFAULT 0,
    status        SMALLINT     NOT NULL DEFAULT 1,
    create_by     BIGINT       NULL,
    create_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_by     BIGINT       NULL,
    update_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted       SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_org_unit_code UNIQUE (unit_code),
    CONSTRAINT ck_org_unit_level CHECK (level >= 1 AND level <= 10)
);
COMMENT ON TABLE  t_org_unit IS '组织架构树（物化路径）';
COMMENT ON COLUMN t_org_unit.parent_id IS '上级单位id，根节点为NULL';
COMMENT ON COLUMN t_org_unit.unit_type IS '单位类型：1总部 2二级集团 3子企业 4部门';
COMMENT ON COLUMN t_org_unit.path IS '物化路径，如/1/3/8/，用于层级下钻与数据权限过滤';
COMMENT ON COLUMN t_org_unit.level IS '层级深度，根=1';
COMMENT ON COLUMN t_org_unit.status IS '状态：1启用 0停用';
COMMENT ON COLUMN t_org_unit.deleted IS '逻辑删除：0正常 1已删除';
CREATE INDEX idx_org_unit_parent ON t_org_unit (parent_id);
CREATE INDEX idx_org_unit_path ON t_org_unit (path varchar_pattern_ops);
CREATE INDEX idx_org_unit_type ON t_org_unit (unit_type);

-- 4.1.2 t_org_user 人员/账号表
CREATE TABLE t_org_user (
    id              BIGSERIAL    PRIMARY KEY,
    job_no          VARCHAR(64)  NOT NULL,
    user_name       VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(128) NOT NULL,
    phone           VARCHAR(32)  NULL,
    gender          SMALLINT     NULL,
    org_id          BIGINT       NOT NULL,
    org_path        VARCHAR(512) NOT NULL,
    position        VARCHAR(64)  NULL,
    account_status  SMALLINT     NOT NULL DEFAULT 1,
    pwd_update_time TIMESTAMPTZ  NULL,
    last_login_time TIMESTAMPTZ  NULL,
    last_login_ip   VARCHAR(64)  NULL,
    source          SMALLINT     NOT NULL DEFAULT 1,
    create_by       BIGINT       NULL,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_by       BIGINT       NULL,
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_org_user_jobno UNIQUE (job_no),
    CONSTRAINT fk_org_user_unit FOREIGN KEY (org_id) REFERENCES t_org_unit (id)
);
COMMENT ON TABLE  t_org_user IS '纪检监察人员/系统账号';
COMMENT ON COLUMN t_org_user.job_no IS '工号（登录账号），全局唯一';
COMMENT ON COLUMN t_org_user.password_hash IS '密码哈希（BCrypt含盐），禁止明文';
COMMENT ON COLUMN t_org_user.org_path IS '当前单位物化路径快照，用于数据权限过滤';
COMMENT ON COLUMN t_org_user.account_status IS '1正常 2锁定 3待修改初始密码 0停用';
COMMENT ON COLUMN t_org_user.source IS '1手工录入 2Excel导入 3HR同步(预留)';
CREATE INDEX idx_org_user_orgpath ON t_org_user (org_path varchar_pattern_ops);
CREATE INDEX idx_org_user_org ON t_org_user (org_id);
CREATE INDEX idx_org_user_status ON t_org_user (account_status);

-- 4.1.3 t_sys_role 角色表 / t_org_user_role 人员角色关联
CREATE TABLE t_sys_role (
    id          BIGSERIAL    PRIMARY KEY,
    role_code   VARCHAR(64)  NOT NULL,
    role_name   VARCHAR(64)  NOT NULL,
    data_scope  SMALLINT     NOT NULL DEFAULT 2,
    permissions JSONB        NOT NULL DEFAULT '[]',
    remark      VARCHAR(256) NULL,
    create_by   BIGINT       NULL,
    create_time TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_by   BIGINT       NULL,
    update_time TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);
COMMENT ON TABLE  t_sys_role IS '角色与权限';
COMMENT ON COLUMN t_sys_role.data_scope IS '数据权限：1全部 2本节点及下级 3仅本人';
COMMENT ON COLUMN t_sys_role.permissions IS '功能权限编码数组，如["exam:create","paper:audit"]';

CREATE TABLE t_org_user_role (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    role_id     BIGINT       NOT NULL,
    create_by   BIGINT       NULL,
    create_time TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_by   BIGINT       NULL,
    update_time TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES t_org_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES t_sys_role (id)
);
COMMENT ON TABLE t_org_user_role IS '人员-角色关联';
CREATE INDEX idx_user_role_role ON t_org_user_role (role_id);

-- ----------------------------------------------------------------------------
-- 2. 学习域（文档 4.2）
-- ----------------------------------------------------------------------------

-- 4.2.1 t_learn_category 资料分类树
CREATE TABLE t_learn_category (
    id            BIGSERIAL     PRIMARY KEY,
    parent_id     BIGINT        NULL,
    category_name VARCHAR(128)  NOT NULL,
    level         INT           NOT NULL DEFAULT 1,
    sort_no       INT           NOT NULL DEFAULT 0,
    visible_scope JSONB         NOT NULL DEFAULT '{"type":"ALL"}',
    status        SMALLINT      NOT NULL DEFAULT 1,
    create_by     BIGINT        NULL,
    create_time   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by     BIGINT        NULL,
    update_time   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted       SMALLINT      NOT NULL DEFAULT 0
);
COMMENT ON TABLE  t_learn_category IS '学习资料分类树';
COMMENT ON COLUMN t_learn_category.visible_scope IS '可见组织范围：{"type":"ALL"} | {"type":"ORG","orgPaths":["/1/3/"],"withChildren":true}';
CREATE INDEX idx_learn_category_parent ON t_learn_category (parent_id);

-- 4.2.2 t_learn_material 学习资料表
CREATE TABLE t_learn_material (
    id             BIGSERIAL     PRIMARY KEY,
    category_id    BIGINT        NOT NULL,
    title          VARCHAR(256)  NOT NULL,
    summary        VARCHAR(1024) NULL,
    cover_url      VARCHAR(512)  NULL,
    file_type      SMALLINT      NOT NULL,
    file_url       VARCHAR(512)  NULL,
    content        TEXT          NULL,
    file_size      BIGINT        NULL,
    page_count     INT           NULL,
    duration_sec   INT           NULL,
    tags           JSONB         NULL,
    apply_level    SMALLINT      NULL,
    visible_scope  JSONB         NOT NULL DEFAULT '{"type":"ALL"}',
    need_audit     SMALLINT      NOT NULL DEFAULT 0,
    audit_status   SMALLINT      NOT NULL DEFAULT 0,
    audit_by       BIGINT        NULL,
    audit_time     TIMESTAMPTZ   NULL,
    version        INT           NOT NULL DEFAULT 1,
    study_count    INT           NOT NULL DEFAULT 0,
    upload_org_id  BIGINT        NULL,
    upload_org_path VARCHAR(512) NULL,
    create_by      BIGINT        NULL,
    create_time    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by      BIGINT        NULL,
    update_time    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted        SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_learn_material_category FOREIGN KEY (category_id) REFERENCES t_learn_category (id)
);
COMMENT ON TABLE  t_learn_material IS '学习资料';
COMMENT ON COLUMN t_learn_material.file_type IS '1PDF 2Word 3PPT 4MP4视频 5图文';
COMMENT ON COLUMN t_learn_material.duration_sec IS '标称时长(秒)，学习完成判定阈值依据';
COMMENT ON COLUMN t_learn_material.audit_status IS '0草稿 1待审 2已发布 3已下架 4驳回';
COMMENT ON COLUMN t_learn_material.visible_scope IS '可见范围JSON：{"type":"ALL"}|{"type":"ORG","orgPaths":[...],"withChildren":true}';
CREATE INDEX idx_learn_material_category ON t_learn_material (category_id);
CREATE INDEX idx_learn_material_status ON t_learn_material (audit_status);
CREATE INDEX idx_learn_material_tags ON t_learn_material USING GIN (tags jsonb_path_ops);
CREATE INDEX idx_learn_material_scope ON t_learn_material USING GIN (visible_scope jsonb_path_ops);

-- 4.2.3 t_learn_record 学习记录表
CREATE TABLE t_learn_record (
    id                    BIGSERIAL     PRIMARY KEY,
    user_id               BIGINT        NOT NULL,
    user_name             VARCHAR(64)   NOT NULL,
    org_id                BIGINT        NOT NULL,
    org_path              VARCHAR(512)  NOT NULL,
    org_name              VARCHAR(128)  NOT NULL,
    material_id           BIGINT        NOT NULL,
    material_title        VARCHAR(256)  NOT NULL,
    category_id           BIGINT        NULL,
    progress              NUMERIC(5,2)  NOT NULL DEFAULT 0,
    read_page             INT           NOT NULL DEFAULT 0,
    play_position_sec     INT           NOT NULL DEFAULT 0,
    effective_duration_sec INT          NOT NULL DEFAULT 0,
    last_heartbeat_time   TIMESTAMPTZ   NULL,
    finish_status         SMALLINT      NOT NULL DEFAULT 0,
    finish_time           TIMESTAMPTZ   NULL,
    first_learn_time      TIMESTAMPTZ   NULL,
    last_learn_time       TIMESTAMPTZ   NULL,
    create_by             BIGINT        NULL,
    create_time           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by             BIGINT        NULL,
    update_time           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_learn_record_um UNIQUE (user_id, material_id)
);
COMMENT ON TABLE  t_learn_record IS '学习记录（进度+有效时长+心跳锚点），一人一份资料一条';
COMMENT ON COLUMN t_learn_record.org_path IS '学习发生时单位物化路径快照，调岗不回刷';
COMMENT ON COLUMN t_learn_record.effective_duration_sec IS '累计有效学习时长(秒)，心跳累计，失焦/暂停不计';
COMMENT ON COLUMN t_learn_record.finish_status IS '0未开始 1进行中 2已完成(进度与时长均达阈值)';
CREATE INDEX idx_learn_record_orgpath ON t_learn_record (org_path varchar_pattern_ops);
CREATE INDEX idx_learn_record_material ON t_learn_record (material_id);
CREATE INDEX idx_learn_record_user ON t_learn_record (user_id, finish_status);
CREATE INDEX idx_learn_record_lastlearn ON t_learn_record (last_learn_time);

-- 4.2.4 t_learn_task 学习任务表 / t_learn_task_user 任务人员快照
CREATE TABLE t_learn_task (
    id                   BIGSERIAL     PRIMARY KEY,
    task_name            VARCHAR(256)  NOT NULL,
    material_ids         JSONB         NOT NULL,
    required_duration_sec INT          NOT NULL DEFAULT 0,
    deadline             TIMESTAMPTZ   NOT NULL,
    target_scope         JSONB         NOT NULL,
    publish_org_id       BIGINT        NULL,
    publish_org_path     VARCHAR(512)  NULL,
    status               SMALLINT      NOT NULL DEFAULT 1,
    remark               VARCHAR(512)  NULL,
    create_by            BIGINT        NULL,
    create_time          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by            BIGINT        NULL,
    update_time          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted              SMALLINT      NOT NULL DEFAULT 0
);
COMMENT ON TABLE  t_learn_task IS '学习任务';
COMMENT ON COLUMN t_learn_task.material_ids IS '任务资料id数组，如[101,102]';
COMMENT ON COLUMN t_learn_task.required_duration_sec IS '要求有效学习时长(秒)';
COMMENT ON COLUMN t_learn_task.target_scope IS '目标范围：{"orgPaths":["/1/3/"],"withChildren":true,"userIds":[...]}';
COMMENT ON COLUMN t_learn_task.status IS '1进行中 2已结束 0已撤销';

CREATE TABLE t_learn_task_user (
    id            BIGSERIAL    PRIMARY KEY,
    task_id       BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    user_name     VARCHAR(64)  NOT NULL,
    org_id        BIGINT       NOT NULL,
    org_path      VARCHAR(512) NOT NULL,
    org_name      VARCHAR(128) NOT NULL,
    complete_status SMALLINT   NOT NULL DEFAULT 0,
    complete_time TIMESTAMPTZ  NULL,
    create_by     BIGINT       NULL,
    create_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_by     BIGINT       NULL,
    update_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_learn_task_user UNIQUE (task_id, user_id),
    CONSTRAINT fk_learn_task_user_task FOREIGN KEY (task_id) REFERENCES t_learn_task (id)
);
COMMENT ON TABLE  t_learn_task_user IS '学习任务人员快照（下达时按范围展开）';
COMMENT ON COLUMN t_learn_task_user.complete_status IS '0未完成 1已完成（完成判定依据t_learn_record聚合）';
CREATE INDEX idx_learn_task_user_user ON t_learn_task_user (user_id, complete_status);
CREATE INDEX idx_learn_task_user_orgpath ON t_learn_task_user (org_path varchar_pattern_ops);

-- ----------------------------------------------------------------------------
-- 3. 题库域（文档 4.3）
-- ----------------------------------------------------------------------------

-- 4.3.1 t_ques_bank 题库目录树
CREATE TABLE t_ques_bank (
    id          BIGSERIAL     PRIMARY KEY,
    parent_id   BIGINT        NULL,
    bank_name   VARCHAR(128)  NOT NULL,
    path        VARCHAR(512)  NOT NULL,
    level       INT           NOT NULL DEFAULT 1,
    sort_no     INT           NOT NULL DEFAULT 0,
    status      SMALLINT      NOT NULL DEFAULT 1,
    create_by   BIGINT        NULL,
    create_time TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by   BIGINT        NULL,
    update_time TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted     SMALLINT      NOT NULL DEFAULT 0
);
COMMENT ON TABLE  t_ques_bank IS '题库目录树（多级分类）';
COMMENT ON COLUMN t_ques_bank.path IS '物化路径，如/1/5/，抽题策略按分类含下级时使用';
CREATE INDEX idx_ques_bank_parent ON t_ques_bank (parent_id);
CREATE INDEX idx_ques_bank_path ON t_ques_bank (path varchar_pattern_ops);

-- 4.3.2 t_ques_item 题目表
CREATE TABLE t_ques_item (
    id           BIGSERIAL     PRIMARY KEY,
    bank_id      BIGINT        NOT NULL,
    ques_type    SMALLINT      NOT NULL,
    stem         TEXT          NOT NULL,
    stem_md5     VARCHAR(32)   NOT NULL,
    options      JSONB         NULL,
    answer       JSONB         NOT NULL,
    analysis     TEXT          NULL,
    difficulty   SMALLINT      NOT NULL DEFAULT 2,
    score_suggest NUMERIC(6,2) NULL,
    knowledge_tags JSONB       NULL,
    source       VARCHAR(128)  NULL,
    status       SMALLINT      NOT NULL DEFAULT 0,
    audit_by     BIGINT        NULL,
    audit_time   TIMESTAMPTZ   NULL,
    use_count    INT           NOT NULL DEFAULT 0,
    answer_count INT           NOT NULL DEFAULT 0,
    right_count  INT           NOT NULL DEFAULT 0,
    right_rate   NUMERIC(5,2)  NOT NULL DEFAULT 0,
    create_by    BIGINT        NULL,
    create_time  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by    BIGINT        NULL,
    update_time  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted      SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_ques_item_bank FOREIGN KEY (bank_id) REFERENCES t_ques_bank (id),
    CONSTRAINT ck_ques_item_type CHECK (ques_type BETWEEN 1 AND 5),
    CONSTRAINT ck_ques_item_difficulty CHECK (difficulty BETWEEN 1 AND 3)
);
COMMENT ON TABLE  t_ques_item IS '题目';
COMMENT ON COLUMN t_ques_item.ques_type IS '1单选 2多选 3判断 4填空 5简答';
COMMENT ON COLUMN t_ques_item.stem IS '题干，富文本HTML';
COMMENT ON COLUMN t_ques_item.options IS '选项JSONB：[{"key":"A","content":"..."}]';
COMMENT ON COLUMN t_ques_item.answer IS '答案JSONB：客观题{"keys":["A"]}；填空{"blanks":[["答1","同义"]],"config":{...}}；简答{"reference":"...","points":[...]}';
COMMENT ON COLUMN t_ques_item.stem_md5 IS '题干规范化MD5，查重辅助';
COMMENT ON COLUMN t_ques_item.status IS '0草稿 1待审 2启用 3停用，仅启用可组卷';
COMMENT ON COLUMN t_ques_item.right_rate IS '历次考试正确率%(冗余)';
CREATE INDEX idx_ques_item_bank ON t_ques_item (bank_id, status, ques_type, difficulty);
CREATE INDEX idx_ques_item_type ON t_ques_item (ques_type, difficulty, status);
CREATE INDEX idx_ques_item_stemmd5 ON t_ques_item (stem_md5);
CREATE INDEX idx_ques_item_tags ON t_ques_item USING GIN (knowledge_tags jsonb_path_ops);

-- ----------------------------------------------------------------------------
-- 4. 试卷与等级规则域（文档 4.4）
-- ----------------------------------------------------------------------------

-- 4.4.1 t_paper 试卷表
CREATE TABLE t_paper (
    id               BIGSERIAL     PRIMARY KEY,
    paper_name       VARCHAR(256)  NOT NULL,
    paper_type       SMALLINT      NOT NULL,
    total_score      NUMERIC(6,2)  NOT NULL DEFAULT 100,
    pass_score       NUMERIC(6,2)  NOT NULL DEFAULT 60,
    multi_judge_rule SMALLINT      NOT NULL DEFAULT 1,
    random_strategy  JSONB         NULL,
    same_paper       SMALLINT      NOT NULL DEFAULT 0,
    grade_rule_id    BIGINT        NULL,
    paper_status     SMALLINT      NOT NULL DEFAULT 0,
    version          INT           NOT NULL DEFAULT 1,
    create_by        BIGINT        NULL,
    create_time      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by        BIGINT        NULL,
    update_time      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted          SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_paper_type CHECK (paper_type IN (1, 2)),
    CONSTRAINT ck_paper_status CHECK (paper_status IN (0, 1, 2))
);
COMMENT ON TABLE  t_paper IS '试卷（固定卷/随机卷）';
COMMENT ON COLUMN t_paper.paper_type IS '1固定卷 2随机卷';
COMMENT ON COLUMN t_paper.random_strategy IS '随机抽题策略JSONB：按题型+题库分类+难度配置数量与分值';
COMMENT ON COLUMN t_paper.multi_judge_rule IS '多选判分：1全对得分 2漏选半分 3漏选不得分';
COMMENT ON COLUMN t_paper.same_paper IS '随机卷全考生同卷：1是 0一人一卷';
COMMENT ON COLUMN t_paper.paper_status IS '0草稿 1启用 2封存(被考试引用后不可改，只可复制新版本)';
CREATE INDEX idx_paper_status ON t_paper (paper_status, paper_type);

-- 4.4.2 t_paper_question 固定卷题目表
CREATE TABLE t_paper_question (
    id            BIGSERIAL    PRIMARY KEY,
    paper_id      BIGINT       NOT NULL,
    ques_id       BIGINT       NOT NULL,
    ques_type     SMALLINT     NOT NULL,
    score         NUMERIC(6,2) NOT NULL,
    sort_no       INT          NOT NULL DEFAULT 0,
    ques_snapshot JSONB        NOT NULL,
    create_by     BIGINT       NULL,
    create_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_by     BIGINT       NULL,
    update_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_paper_ques UNIQUE (paper_id, ques_id),
    CONSTRAINT fk_paper_ques_paper FOREIGN KEY (paper_id) REFERENCES t_paper (id)
);
COMMENT ON TABLE  t_paper_question IS '固定卷题目与分值快照';
COMMENT ON COLUMN t_paper_question.ques_snapshot IS '题目快照{stem,options,answer,analysis,difficulty,...}，封存后不受题库修改影响';
COMMENT ON COLUMN t_paper_question.score IS '本题在本卷的分值(NUMERIC(6,2)，支持半分)';
CREATE INDEX idx_paper_ques_paper ON t_paper_question (paper_id, sort_no);
CREATE INDEX idx_paper_ques_ques ON t_paper_question (ques_id);

-- 4.4.3 t_grade_rule / t_grade_rule_item 等级规则
CREATE TABLE t_grade_rule (
    id          BIGSERIAL    PRIMARY KEY,
    rule_name   VARCHAR(128) NOT NULL,
    full_score  NUMERIC(6,2) NOT NULL DEFAULT 100,
    is_default  SMALLINT     NOT NULL DEFAULT 0,
    status      SMALLINT     NOT NULL DEFAULT 1,
    remark      VARCHAR(256) NULL,
    create_by   BIGINT       NULL,
    create_time TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_by   BIGINT       NULL,
    update_time TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted     SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE  t_grade_rule IS '等级规则（优良中差，支持自定义）';
COMMENT ON COLUMN t_grade_rule.is_default IS '是否系统默认规则：1是(全局唯一一条) 0否';

CREATE TABLE t_grade_rule_item (
    id              BIGSERIAL    PRIMARY KEY,
    rule_id         BIGINT       NOT NULL,
    grade_code      VARCHAR(32)  NOT NULL,
    grade_name      VARCHAR(32)  NOT NULL,
    min_score       NUMERIC(6,2) NOT NULL,
    max_score       NUMERIC(6,2) NOT NULL,
    lower_inclusive SMALLINT     NOT NULL DEFAULT 1,
    upper_inclusive SMALLINT     NOT NULL DEFAULT 0,
    sort_no         INT          NOT NULL DEFAULT 0,
    create_by       BIGINT       NULL,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_by       BIGINT       NULL,
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_grade_item UNIQUE (rule_id, grade_code),
    CONSTRAINT fk_grade_item_rule FOREIGN KEY (rule_id) REFERENCES t_grade_rule (id),
    CONSTRAINT ck_grade_item_range CHECK (min_score <= max_score)
);
COMMENT ON TABLE  t_grade_rule_item IS '等级规则分数区间，默认: 优[90,100] 良[80,90) 中[60,80) 差[0,60)';
COMMENT ON COLUMN t_grade_rule_item.lower_inclusive IS '下边界是否闭区间：1含 0不含';
COMMENT ON COLUMN t_grade_rule_item.upper_inclusive IS '上边界是否闭区间：1含 0不含';
CREATE INDEX idx_grade_item_rule ON t_grade_rule_item (rule_id, sort_no);

-- ----------------------------------------------------------------------------
-- 5. 考试域（文档 4.5）
-- ----------------------------------------------------------------------------

-- 4.5.1 t_exam_session 考试场次表
CREATE TABLE t_exam_session (
    id             BIGSERIAL     PRIMARY KEY,
    session_name   VARCHAR(256)  NOT NULL,
    paper_id       BIGINT        NOT NULL,
    paper_name     VARCHAR(256)  NOT NULL,
    paper_version  INT           NOT NULL,
    paper_strategy_snapshot JSONB NULL,
    start_time     TIMESTAMPTZ   NOT NULL,
    end_time       TIMESTAMPTZ   NOT NULL,
    duration_min   INT           NOT NULL,
    late_limit_min INT           NOT NULL DEFAULT 0,
    switch_limit   INT           NOT NULL DEFAULT 3,
    allow_early_submit SMALLINT  NOT NULL DEFAULT 1,
    early_submit_after_min INT   NOT NULL DEFAULT 0,
    shuffle_question SMALLINT    NOT NULL DEFAULT 0,
    shuffle_option   SMALLINT    NOT NULL DEFAULT 0,
    session_status SMALLINT      NOT NULL DEFAULT 0,
    score_publish_time TIMESTAMPTZ NULL,
    related_session_id BIGINT    NULL,
    session_kind   SMALLINT      NOT NULL DEFAULT 1,
    create_org_id  BIGINT        NOT NULL,
    create_org_path VARCHAR(512) NOT NULL,
    create_by      BIGINT        NULL,
    create_time    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by      BIGINT        NULL,
    update_time    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted        SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_session_time CHECK (end_time > start_time),
    CONSTRAINT ck_session_status CHECK (session_status BETWEEN 0 AND 4)
);
COMMENT ON TABLE  t_exam_session IS '考试场次';
COMMENT ON COLUMN t_exam_session.paper_strategy_snapshot IS '试卷策略快照(抽题规则/及格线/判分规则/等级规则id)，下发后不受试卷变更影响';
COMMENT ON COLUMN t_exam_session.session_status IS '0未开始 1进行中 2已结束 3成绩已发布 4已取消';
COMMENT ON COLUMN t_exam_session.early_submit_after_min IS '开考N分钟后允许提前交卷(0不限制)，对应API earlySubmitAfterMin';
COMMENT ON COLUMN t_exam_session.shuffle_question IS '试题乱序开关:1开 0关，对应API shuffleQuestion';
COMMENT ON COLUMN t_exam_session.shuffle_option IS '选项乱序开关:1开 0关，对应API shuffleOption';
COMMENT ON COLUMN t_exam_session.session_kind IS '1正考 2补考，补考通过related_session_id关联正考';
COMMENT ON COLUMN t_exam_session.create_org_path IS '组织单位物化路径快照，分级组织考试与数据权限';
CREATE INDEX idx_exam_session_status ON t_exam_session (session_status, start_time);
CREATE INDEX idx_exam_session_orgpath ON t_exam_session (create_org_path varchar_pattern_ops);
CREATE INDEX idx_exam_session_paper ON t_exam_session (paper_id);

-- 4.5.2 t_exam_candidate 考生名单快照表
CREATE TABLE t_exam_candidate (
    id              BIGSERIAL     PRIMARY KEY,
    session_id      BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    user_name       VARCHAR(64)   NOT NULL,
    job_no          VARCHAR(64)   NOT NULL,
    org_id          BIGINT        NOT NULL,
    org_path        VARCHAR(512)  NOT NULL,
    org_name        VARCHAR(128)  NOT NULL,
    candidate_status SMALLINT     NOT NULL DEFAULT 0,
    source          SMALLINT      NOT NULL DEFAULT 1,
    notify_status   SMALLINT      NOT NULL DEFAULT 0,
    create_by       BIGINT        NULL,
    create_time     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by       BIGINT        NULL,
    update_time     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_candidate UNIQUE (session_id, user_id),
    CONSTRAINT fk_candidate_session FOREIGN KEY (session_id) REFERENCES t_exam_session (id)
);
COMMENT ON TABLE  t_exam_candidate IS '考生名单快照（人员+当时单位）';
COMMENT ON COLUMN t_exam_candidate.candidate_status IS '0未考 1已交卷 2缺考 3强制收卷';
COMMENT ON COLUMN t_exam_candidate.source IS '1范围生成 2手工追加；剔除走deleted=1';
COMMENT ON COLUMN t_exam_candidate.org_path IS '下发时单位物化路径快照，层级统计口径';
CREATE INDEX idx_candidate_session ON t_exam_candidate (session_id, candidate_status);
CREATE INDEX idx_candidate_user ON t_exam_candidate (user_id, session_id);
CREATE INDEX idx_candidate_orgpath ON t_exam_candidate (session_id, org_path varchar_pattern_ops);

-- 4.5.3 t_answer_sheet 答卷主表
CREATE TABLE t_answer_sheet (
    id             BIGSERIAL     PRIMARY KEY,
    session_id     BIGINT        NOT NULL,
    candidate_id   BIGINT        NOT NULL,
    user_id        BIGINT        NOT NULL,
    user_name      VARCHAR(64)   NOT NULL,
    job_no         VARCHAR(64)   NOT NULL,
    org_id         BIGINT        NOT NULL,
    org_path       VARCHAR(512)  NOT NULL,
    org_name       VARCHAR(128)  NOT NULL,
    paper_id       BIGINT        NOT NULL,
    paper_snapshot JSONB         NOT NULL,
    start_time     TIMESTAMPTZ   NULL,
    server_deadline TIMESTAMPTZ  NULL,
    submit_time    TIMESTAMPTZ   NULL,
    submit_type    SMALLINT      NULL,
    switch_count   INT           NOT NULL DEFAULT 0,
    last_ip        VARCHAR(64)   NULL,
    terminal       VARCHAR(32)   NULL,
    user_agent     VARCHAR(512)  NULL,
    session_token  VARCHAR(128)  NULL,
    objective_score NUMERIC(6,2) NULL,
    subjective_score NUMERIC(6,2) NULL,
    total_score    NUMERIC(6,2)  NULL,
    grade_rule_id  BIGINT        NULL,
    grade_code     VARCHAR(32)   NULL,
    grade_name     VARCHAR(32)   NULL,
    mark_status    SMALLINT      NOT NULL DEFAULT 0,
    sheet_status   SMALLINT      NOT NULL DEFAULT 1,
    create_by      BIGINT        NULL,
    create_time    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by      BIGINT        NULL,
    update_time    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_sheet_session_user UNIQUE (session_id, user_id),
    CONSTRAINT fk_sheet_session FOREIGN KEY (session_id) REFERENCES t_exam_session (id),
    CONSTRAINT fk_sheet_candidate FOREIGN KEY (candidate_id) REFERENCES t_exam_candidate (id)
);
COMMENT ON TABLE  t_answer_sheet IS '答卷主表（一人一场次一份）';
COMMENT ON COLUMN t_answer_sheet.paper_snapshot IS '考生实际试卷快照(题目/顺序/分值/乱序结果)，随机卷一人一卷还原与断线续考依据';
COMMENT ON COLUMN t_answer_sheet.server_deadline IS '服务端计时锚点=start_time+时长，倒计时以此校时，断线不延长';
COMMENT ON COLUMN t_answer_sheet.submit_type IS '1主动 2到时自动 3强制收卷 4切屏超限';
COMMENT ON COLUMN t_answer_sheet.session_token IS '答题会话令牌，同账号单终端在线控制';
COMMENT ON COLUMN t_answer_sheet.grade_name IS '成绩发布时等级快照，规则变更不回刷';
COMMENT ON COLUMN t_answer_sheet.mark_status IS '0答题中 1待判分 2客观已判 3阅卷中 4成绩已合成 5已发布';
CREATE INDEX idx_sheet_session_mark ON t_answer_sheet (session_id, mark_status);
CREATE INDEX idx_sheet_orgpath ON t_answer_sheet (session_id, org_path varchar_pattern_ops);
CREATE INDEX idx_sheet_user ON t_answer_sheet (user_id, create_time);
CREATE INDEX idx_sheet_candidate ON t_answer_sheet (candidate_id);

-- 4.5.4 t_answer_detail 逐题答案明细表
CREATE TABLE t_answer_detail (
    id          BIGSERIAL     PRIMARY KEY,
    sheet_id    BIGINT        NOT NULL,
    session_id  BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    ques_id     BIGINT        NOT NULL,
    ques_type   SMALLINT      NOT NULL,
    sort_no     INT           NOT NULL,
    ques_score  NUMERIC(6,2)  NOT NULL,
    user_answer JSONB         NULL,
    is_right    SMALLINT      NULL,
    score       NUMERIC(6,2)  NULL,
    mark_status SMALLINT      NOT NULL DEFAULT 0,
    answer_time TIMESTAMPTZ   NULL,
    create_by   BIGINT        NULL,
    create_time TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by   BIGINT        NULL,
    update_time TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_detail_sheet_ques UNIQUE (sheet_id, ques_id),
    CONSTRAINT fk_detail_sheet FOREIGN KEY (sheet_id) REFERENCES t_answer_sheet (id)
);
COMMENT ON TABLE  t_answer_detail IS '逐题答案明细（断线续考幂等保存、自动判分、题目维度统计）';
COMMENT ON COLUMN t_answer_detail.user_answer IS '考生答案JSONB：选择{"keys":["A"]}；填空{"blanks":["...",...]}；简答{"text":"..."}';
COMMENT ON COLUMN t_answer_detail.is_right IS '1对 0错 2半对(漏选半分) NULL未判';
COMMENT ON COLUMN t_answer_detail.mark_status IS '0未判 1自动已判 2人工已判 3待人工复核';
COMMENT ON COLUMN t_answer_detail.ques_score IS '本题满分快照';
CREATE INDEX idx_detail_sheet ON t_answer_detail (sheet_id);
CREATE INDEX idx_detail_session_ques ON t_answer_detail (session_id, ques_id);
CREATE INDEX idx_detail_mark ON t_answer_detail (mark_status, ques_type);

-- 4.5.5 t_mark_task 阅卷任务表
CREATE TABLE t_mark_task (
    id          BIGSERIAL     PRIMARY KEY,
    session_id  BIGINT        NOT NULL,
    sheet_id    BIGINT        NOT NULL,
    detail_id   BIGINT        NOT NULL,
    ques_id     BIGINT        NOT NULL,
    marker_id   BIGINT        NOT NULL,
    marker_name VARCHAR(64)   NOT NULL,
    round_no    SMALLINT      NOT NULL DEFAULT 1,
    mark_status SMALLINT      NOT NULL DEFAULT 0,
    score       NUMERIC(6,2)  NULL,
    comment     VARCHAR(1024) NULL,
    mark_time   TIMESTAMPTZ   NULL,
    assign_by   BIGINT        NULL,
    assign_time TIMESTAMPTZ   NOT NULL DEFAULT now(),
    create_by   BIGINT        NULL,
    create_time TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by   BIGINT        NULL,
    update_time TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_mark_task UNIQUE (detail_id, round_no),
    CONSTRAINT fk_mark_detail FOREIGN KEY (detail_id) REFERENCES t_answer_detail (id)
);
COMMENT ON TABLE  t_mark_task IS '主观题阅卷任务（含填空复核与双评仲裁）';
COMMENT ON COLUMN t_mark_task.round_no IS '评阅轮次：1一评 2二评 3仲裁';
COMMENT ON COLUMN t_mark_task.mark_status IS '0待阅 1已阅 2仲裁中 3已仲裁';
COMMENT ON COLUMN t_mark_task.score IS '阅卷打分 NUMERIC(6,2)，支持半分';
CREATE INDEX idx_mark_task_marker ON t_mark_task (marker_id, mark_status);
CREATE INDEX idx_mark_task_session ON t_mark_task (session_id, mark_status);
CREATE INDEX idx_mark_task_sheet ON t_mark_task (sheet_id);

-- ----------------------------------------------------------------------------
-- 6. 统计域（文档 4.6）
-- ----------------------------------------------------------------------------

-- 4.6.1 t_stat_exam_unit 场次×单位统计快照表
CREATE TABLE t_stat_exam_unit (
    id            BIGSERIAL     PRIMARY KEY,
    session_id    BIGINT        NOT NULL,
    org_id        BIGINT        NOT NULL,
    org_path      VARCHAR(512)  NOT NULL,
    org_name      VARCHAR(128)  NOT NULL,
    org_level     INT           NOT NULL,
    should_count  INT           NOT NULL DEFAULT 0,
    actual_count  INT           NOT NULL DEFAULT 0,
    absent_count  INT           NOT NULL DEFAULT 0,
    avg_score     NUMERIC(6,2)  NULL,
    max_score     NUMERIC(6,2)  NULL,
    min_score     NUMERIC(6,2)  NULL,
    pass_count    INT           NOT NULL DEFAULT 0,
    pass_rate     NUMERIC(5,2)  NULL,
    grade_dist    JSONB         NULL,
    segment_dist  JSONB         NULL,
    stat_time     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    create_by     BIGINT        NULL,
    create_time   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by     BIGINT        NULL,
    update_time   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_stat_exam_unit UNIQUE (session_id, org_id)
);
COMMENT ON TABLE  t_stat_exam_unit IS '场次×单位预聚合统计快照（成绩发布后生成，层级下钻/排名用）';
COMMENT ON COLUMN t_stat_exam_unit.grade_dist IS '等级分布JSONB：{"优":12,"良":30,"中":8,"差":2}';
COMMENT ON COLUMN t_stat_exam_unit.segment_dist IS '分数段分布JSONB：{"0-59":2,"60-69":8,"70-79":15,"80-89":20,"90-100":7}';
CREATE INDEX idx_stat_unit_orgpath ON t_stat_exam_unit (org_path varchar_pattern_ops);
CREATE INDEX idx_stat_unit_session ON t_stat_exam_unit (session_id, org_level);

-- ----------------------------------------------------------------------------
-- 7. 系统域（文档 4.7）
-- ----------------------------------------------------------------------------

-- 4.7.1 t_sys_config 系统参数表
CREATE TABLE t_sys_config (
    id           BIGSERIAL     PRIMARY KEY,
    config_key   VARCHAR(128)  NOT NULL,
    config_value TEXT          NOT NULL,
    value_type   SMALLINT      NOT NULL DEFAULT 1,
    config_group VARCHAR(64)   NOT NULL DEFAULT 'common',
    editable     SMALLINT      NOT NULL DEFAULT 1,
    remark       VARCHAR(256)  NULL,
    create_by    BIGINT        NULL,
    create_time  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by    BIGINT        NULL,
    update_time  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_sys_config_key UNIQUE (config_key)
);
COMMENT ON TABLE  t_sys_config IS '系统参数配置';
COMMENT ON COLUMN t_sys_config.value_type IS '1字符串 2数字 3布尔 4JSON';
COMMENT ON COLUMN t_sys_config.editable IS '是否允许界面修改：1是 0否(仅运维改库)';

-- 4.7.2 t_sys_notice 站内消息/通知表
CREATE TABLE t_sys_notice (
    id          BIGSERIAL     PRIMARY KEY,
    notice_type SMALLINT      NOT NULL,
    title       VARCHAR(256)  NOT NULL,
    content     TEXT          NULL,
    biz_type    VARCHAR(32)   NULL,
    biz_id      BIGINT        NULL,
    receiver_id BIGINT        NOT NULL,
    read_status SMALLINT      NOT NULL DEFAULT 0,
    read_time   TIMESTAMPTZ   NULL,
    channel     SMALLINT      NOT NULL DEFAULT 1,
    send_status SMALLINT      NOT NULL DEFAULT 1,
    create_by   BIGINT        NULL,
    create_time TIMESTAMPTZ   NOT NULL DEFAULT now(),
    update_by   BIGINT        NULL,
    update_time TIMESTAMPTZ   NOT NULL DEFAULT now()
);
COMMENT ON TABLE  t_sys_notice IS '站内消息/通知（一接收人一行）';
COMMENT ON COLUMN t_sys_notice.notice_type IS '1考试通知 2成绩发布 3学习任务提醒 9系统公告';
COMMENT ON COLUMN t_sys_notice.biz_type IS '关联业务：session/task等，biz_id为业务主键';
COMMENT ON COLUMN t_sys_notice.channel IS '通道：1站内 2短信(预留) 3企业微信(预留)';
CREATE INDEX idx_notice_receiver ON t_sys_notice (receiver_id, read_status, create_time);
CREATE INDEX idx_notice_biz ON t_sys_notice (biz_type, biz_id);

-- 4.7.3 t_sys_log 操作/审计/考试异常日志表（无 update_time，不挂触发器）
CREATE TABLE t_sys_log (
    id          BIGSERIAL     PRIMARY KEY,
    log_type    SMALLINT      NOT NULL,
    module      VARCHAR(64)   NULL,
    operation   VARCHAR(128)  NULL,
    content     TEXT          NULL,
    user_id     BIGINT        NULL,
    user_name   VARCHAR(64)   NULL,
    org_path    VARCHAR(512)  NULL,
    ip          VARCHAR(64)   NULL,
    result      SMALLINT      NOT NULL DEFAULT 1,
    create_time TIMESTAMPTZ   NOT NULL DEFAULT now()
);
COMMENT ON TABLE  t_sys_log IS '操作/审计/考试异常日志（只增不改，按月归档）';
COMMENT ON COLUMN t_sys_log.log_type IS '1操作日志 2审计日志(组织/人员/授权变更) 3考试异常(强制交卷/切屏超限/异地下线)';
COMMENT ON COLUMN t_sys_log.result IS '1成功 0失败';
CREATE INDEX idx_sys_log_type_time ON t_sys_log (log_type, create_time);
CREATE INDEX idx_sys_log_user ON t_sys_log (user_id, create_time);
CREATE INDEX idx_sys_log_orgpath ON t_sys_log (org_path varchar_pattern_ops);

-- 4.7.4 t_sys_login_log 登录日志表（无 update_time，不挂触发器）
CREATE TABLE t_sys_login_log (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NULL,
    job_no       VARCHAR(64)  NOT NULL,
    login_ip     VARCHAR(64)  NULL,
    terminal     VARCHAR(32)  NULL,
    user_agent   VARCHAR(512) NULL,
    login_result SMALLINT     NOT NULL,
    fail_reason  VARCHAR(256) NULL,
    login_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    logout_time  TIMESTAMPTZ  NULL
);
COMMENT ON TABLE  t_sys_login_log IS '登录日志（只增不改，按月归档）';
COMMENT ON COLUMN t_sys_login_log.login_result IS '1成功 0失败(密码错误/锁定/越权等)';
CREATE INDEX idx_login_log_user ON t_sys_login_log (user_id, login_time);
CREATE INDEX idx_login_log_time ON t_sys_login_log (login_time);

-- ----------------------------------------------------------------------------
-- 8. update_time 自动维护触发器（文档 1.5，覆盖所有含 update_time 的 23 张业务表；
--    t_sys_log / t_sys_login_log 无 update_time 字段，不挂触发器）
-- ----------------------------------------------------------------------------
CREATE TRIGGER trg_touch_org_unit         BEFORE UPDATE ON t_org_unit         FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_org_user         BEFORE UPDATE ON t_org_user         FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_sys_role         BEFORE UPDATE ON t_sys_role         FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_org_user_role    BEFORE UPDATE ON t_org_user_role    FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_learn_category   BEFORE UPDATE ON t_learn_category   FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_learn_material   BEFORE UPDATE ON t_learn_material   FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_learn_record     BEFORE UPDATE ON t_learn_record     FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_learn_task       BEFORE UPDATE ON t_learn_task       FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_learn_task_user  BEFORE UPDATE ON t_learn_task_user  FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_ques_bank        BEFORE UPDATE ON t_ques_bank        FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_ques_item        BEFORE UPDATE ON t_ques_item        FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_paper            BEFORE UPDATE ON t_paper            FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_paper_question   BEFORE UPDATE ON t_paper_question   FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_grade_rule       BEFORE UPDATE ON t_grade_rule       FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_grade_rule_item  BEFORE UPDATE ON t_grade_rule_item  FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_exam_session     BEFORE UPDATE ON t_exam_session     FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_exam_candidate   BEFORE UPDATE ON t_exam_candidate   FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_answer_sheet     BEFORE UPDATE ON t_answer_sheet     FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_answer_detail    BEFORE UPDATE ON t_answer_detail    FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_mark_task        BEFORE UPDATE ON t_mark_task        FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_stat_exam_unit   BEFORE UPDATE ON t_stat_exam_unit   FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_sys_config       BEFORE UPDATE ON t_sys_config       FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
CREATE TRIGGER trg_touch_sys_notice       BEFORE UPDATE ON t_sys_notice       FOR EACH ROW EXECUTE FUNCTION f_touch_update_time();
