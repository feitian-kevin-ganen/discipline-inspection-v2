-- ============================================================================
-- init-timu-del.sql — 题目管理题库清理
-- 目标：仅保留「纪检监察题库-正式」与「纪检监察题库汇总（外部导入）」两个根题库
--       及其全部下级分类，其余题库与题目全部清理（逻辑删除，可回滚）。
-- 说明：
--   1. 逻辑删除（deleted=1），非物理删除；如需回滚，将对应行 deleted 改回 0 即可。
--   2. 被试卷引用的题目一并逻辑删除；试卷中保存的是组卷时的题目快照
--      （t_paper_question.ques_snapshot），历史试卷展示/阅卷不受影响。
--   3. 保留集按根题库名称动态计算（递归CTE），不依赖固定id，源库/目标库通用。
-- 兼容性：MySQL 8.0 / PolarDB for MySQL 8.0（支持递归CTE）。
-- ============================================================================

-- 先预览将被清理的题库与题目数量（只读，不写数据）：
-- CREATE TEMPORARY TABLE _keep_bank AS
--   WITH RECURSIVE k AS (
--     SELECT id FROM t_ques_bank
--      WHERE bank_name IN ('纪检监察题库-正式', '纪检监察题库汇总（外部导入）')
--     UNION ALL
--     SELECT b.id FROM t_ques_bank b JOIN k ON b.parent_id = k.id
--   ) SELECT id FROM k;
-- SELECT COUNT(*) AS 待清理题库数 FROM t_ques_bank WHERE deleted = 0 AND id NOT IN (SELECT id FROM _keep_bank);
-- SELECT COUNT(*) AS 待清理题目数 FROM t_ques_item WHERE deleted = 0 AND bank_id NOT IN (SELECT id FROM _keep_bank);
-- DROP TEMPORARY TABLE _keep_bank;

BEGIN;

-- 保留集：两个指定根题库及其全部子孙分类
CREATE TEMPORARY TABLE _keep_bank AS
WITH RECURSIVE k AS (
    SELECT id FROM t_ques_bank
     WHERE bank_name IN ('纪检监察题库-正式', '纪检监察题库汇总（外部导入）')
    UNION ALL
    SELECT b.id FROM t_ques_bank b JOIN k ON b.parent_id = k.id
) SELECT id FROM k;

-- 1. 清理题目（逻辑删除，含被试卷引用的题目，快照不受影响）
UPDATE t_ques_item
   SET deleted = 1, update_time = NOW(6)
 WHERE deleted = 0
   AND bank_id NOT IN (SELECT id FROM _keep_bank);

-- 2. 清理题库分类（逻辑删除）
UPDATE t_ques_bank
   SET deleted = 1, update_time = NOW(6)
 WHERE deleted = 0
   AND id NOT IN (SELECT id FROM _keep_bank);

DROP TEMPORARY TABLE _keep_bank;

COMMIT;

-- 执行后校验（应返回 0 / 0，且只剩两个根题库及其下级）：
-- SELECT COUNT(*) AS 剩余应清理题库 FROM t_ques_bank WHERE deleted = 0
--   AND id NOT IN (WITH RECURSIVE k AS (
--     SELECT id FROM t_ques_bank WHERE bank_name IN ('纪检监察题库-正式','纪检监察题库汇总（外部导入）')
--     UNION ALL SELECT b.id FROM t_ques_bank b JOIN k ON b.parent_id = k.id) SELECT id FROM k);
-- SELECT id, bank_name, parent_id FROM t_ques_bank WHERE deleted = 0 AND parent_id IS NULL;
