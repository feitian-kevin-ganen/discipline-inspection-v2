-- ============================================================================
-- rollback-timu-del.sql — 回滚 init-timu-del.sql 的题库清理
-- 作用：把「保留集（纪检监察题库-正式、纪检监察题库汇总（外部导入）及其子孙）」之外
--       被逻辑删除的题库与题目恢复为 deleted=0。
-- 例外：已下线的「AI生成题库」子树保持删除状态（如需一并恢复，去掉 _ai_bank 排除条件）。
-- 注意：本回滚按保留集补集恢复，会把该补集中更早（本次清理之前）被逻辑删除的
--       题库/题目也一并恢复；如需精确回滚到某一时间点，请用 backup 的备份还原。
-- 兼容性：MySQL 8.0 / PolarDB for MySQL 8.0（支持递归CTE）。
-- ============================================================================

BEGIN;

-- 保留集：两个指定根题库及其全部子孙分类（与 init-timu-del.sql 口径一致）
CREATE TEMPORARY TABLE _keep_bank AS
WITH RECURSIVE k AS (
    SELECT id FROM t_ques_bank
     WHERE bank_name IN ('纪检监察题库-正式', '纪检监察题库汇总（外部导入）')
    UNION ALL
    SELECT b.id FROM t_ques_bank b JOIN k ON b.parent_id = k.id
) SELECT id FROM k;

-- AI生成题库子树（保持删除，不恢复）
CREATE TEMPORARY TABLE _ai_bank AS
WITH RECURSIVE k AS (
    SELECT id FROM t_ques_bank WHERE bank_name = 'AI生成题库' AND parent_id IS NULL
    UNION ALL
    SELECT b.id FROM t_ques_bank b JOIN k ON b.parent_id = k.id
) SELECT id FROM k;

-- 1. 恢复题目
UPDATE t_ques_item
   SET deleted = 0, update_time = NOW(6)
 WHERE deleted = 1
   AND bank_id NOT IN (SELECT id FROM _keep_bank)
   AND bank_id NOT IN (SELECT id FROM _ai_bank);

-- 2. 恢复题库分类
UPDATE t_ques_bank
   SET deleted = 0, update_time = NOW(6)
 WHERE deleted = 1
   AND id NOT IN (SELECT id FROM _keep_bank)
   AND id NOT IN (SELECT id FROM _ai_bank);

DROP TEMPORARY TABLE _keep_bank;
DROP TEMPORARY TABLE _ai_bank;

COMMIT;

-- 执行后校验（根题库应恢复到清理前的完整列表，AI生成题库 仍保持隐藏）：
-- SELECT id, bank_name, deleted FROM t_ques_bank WHERE parent_id IS NULL ORDER BY id;
