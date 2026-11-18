-- =====================================================================
-- clear-testdata.sql  测试数据清理（重新导入数据前执行）
-- 生成日期: 2026-09-22
-- 适用库: exam_db（本机 10.110.40.125:3306 / 测试机 10.64.16.14:4886，
--         均为 PolarDB for MySQL 8.0.32-X-Cluster，已实测语法兼容）
--
-- 【保留范围】
--   1) 资料分类：纪检法规学习资料（11部）、纪检法律法规及制度规定（含其下资料）
--   2) 题库分类：纪检监察题库-正式（含全部下级子库及其题目，递归任意层级）
--   3) 组织机构/人员/角色/系统参数/等级规则等基础数据不动
--
-- 【清理范围】（物理删除，不可恢复，执行前请先备份！）
--   - 其余资料分类及其下全部资料
--   - 其余题库分类（含 纪检监察题库汇总（外部导入）、AI生成题库 等）及其全部题目
--   - 全部试卷（t_paper + t_paper_question）
--   - 全部测试场次及链路数据（场次/考生/答卷/答题明细/阅卷任务/单位统计）
--   - 全部学习任务、任务人员、学习记录
--   - 全部日常练习记录
--   - 全部站内通知、登录日志、操作日志、微信通知日志
--   - 附带清零：保留资料的 study_count；保留题目的
--     use_count/answer_count/right_count/right_rate（作答数据已清，统计一并归零）
--
-- 【备份命令（强烈建议先执行）】
--   mysqldump -h<库IP> -P<端口> -uexam_admin -p'<密码>' exam_db > backup-all-$(date +%Y%m%d).sql
--
-- 【执行方式】
--   本机:   mysql -h10.110.40.125 -P3306 -uexam_admin -p'Oh]fT>*PA&,6' exam_db < clear-testdata.sql
--   测试机: /root/migration/tools/mysql/mysql.sh -h10.64.16.14 -P4886 -uexam_admin -p'z>PD?8Wr_W7h' exam_db < clear-testdata.sql
-- =====================================================================

-- ---------- 0. 执行前预览（仅查看，不影响后续执行） ----------
SELECT 'before:待删资料(不在保留分类下)' AS item, COUNT(*) AS cnt FROM t_learn_material
 WHERE category_id NOT IN (SELECT id FROM (SELECT id FROM t_learn_category
   WHERE category_name IN ('纪检法规学习资料（11部）','纪检法律法规及制度规定')) x);
SELECT 'before:待删题目(不在保留题库下)' AS item, COUNT(*) AS cnt FROM t_ques_item
 WHERE bank_id NOT IN (
   WITH RECURSIVE k AS (
     SELECT id FROM t_ques_bank WHERE bank_name='纪检监察题库-正式' AND deleted=0
     UNION ALL SELECT b.id FROM t_ques_bank b JOIN k ON b.parent_id=k.id)
   SELECT id FROM k);

-- ---------- 1. 日志与通知（全清） ----------
DELETE FROM t_sys_login_log;
DELETE FROM t_sys_log;
DELETE FROM t_sys_notice;
DELETE FROM t_notify_wx_log;

-- ---------- 2. 测试链路（先子后父，全清） ----------
DELETE FROM t_mark_task;          -- 阅卷任务
DELETE FROM t_answer_detail;      -- 答题明细
DELETE FROM t_answer_sheet;       -- 答卷（我的成绩数据来源）
DELETE FROM t_exam_candidate;     -- 场次考生
DELETE FROM t_stat_exam_unit;     -- 场次单位统计
DELETE FROM t_exam_session;       -- 测试场次

-- ---------- 3. 试卷（全清） ----------
DELETE FROM t_paper_question;
DELETE FROM t_paper;

-- ---------- 4. 学习任务与学习记录（全清） ----------
DELETE FROM t_learn_task_user;
DELETE FROM t_learn_task;
DELETE FROM t_learn_record;       -- 学习中心/工作台学习数据来源

-- ---------- 5. 日常练习记录（全清） ----------
DELETE FROM t_practice_record;

-- ---------- 6. 资料分类与资料：仅保留两个指定分类 ----------
DROP TEMPORARY TABLE IF EXISTS tmp_keep_cat;
CREATE TEMPORARY TABLE tmp_keep_cat AS
  SELECT id FROM t_learn_category
  WHERE category_name IN ('纪检法规学习资料（11部）','纪检法律法规及制度规定');
DELETE FROM t_learn_material WHERE category_id NOT IN (SELECT id FROM tmp_keep_cat);
DELETE FROM t_learn_category WHERE id NOT IN (SELECT id FROM tmp_keep_cat);
UPDATE t_learn_material SET study_count = 0;
DROP TEMPORARY TABLE tmp_keep_cat;

-- ---------- 7. 题库分类与题目：仅保留「纪检监察题库-正式」子树（递归任意层级） ----------
DROP TEMPORARY TABLE IF EXISTS tmp_keep_bank;
CREATE TEMPORARY TABLE tmp_keep_bank AS
  WITH RECURSIVE k AS (
    SELECT id FROM t_ques_bank WHERE bank_name='纪检监察题库-正式' AND deleted=0
    UNION ALL
    SELECT b.id FROM t_ques_bank b JOIN k ON b.parent_id = k.id
  ) SELECT id FROM k;
DELETE FROM t_ques_item WHERE bank_id NOT IN (SELECT id FROM tmp_keep_bank);
DELETE FROM t_ques_bank WHERE id NOT IN (SELECT id FROM tmp_keep_bank);
DROP TEMPORARY TABLE tmp_keep_bank;

-- ---------- 8. 保留题目的作答统计清零 ----------
UPDATE t_ques_item SET use_count = 0, answer_count = 0, right_count = 0, right_rate = NULL;

-- ---------- 9. AI 生题任务历史（默认保留；如需一并清空，取消以下两行注释） ----------
-- DELETE FROM t_ai_gen_question;
-- DELETE FROM t_ai_gen_task;

-- ---------- 10. 执行后校验（期望输出见下行注释） ----------
SELECT 'after:资料分类' AS item, COUNT(*) AS cnt FROM t_learn_category;               -- 期望 2
SELECT 'after:资料' AS item, COUNT(*) AS cnt FROM t_learn_material;                  -- 期望 51（本机基线）
SELECT 'after:题库分类' AS item, COUNT(*) AS cnt FROM t_ques_bank;                   -- 期望 15（本机基线）
SELECT 'after:题目' AS item, COUNT(*) AS cnt FROM t_ques_item;                       -- 期望 283（本机基线）
SELECT 'after:试卷' AS item, COUNT(*) AS cnt FROM t_paper;                           -- 期望 0
SELECT 'after:测试场次' AS item, COUNT(*) AS cnt FROM t_exam_session;                -- 期望 0
SELECT 'after:答卷' AS item, COUNT(*) AS cnt FROM t_answer_sheet;                    -- 期望 0
SELECT 'after:阅卷任务' AS item, COUNT(*) AS cnt FROM t_mark_task;                   -- 期望 0
SELECT 'after:学习任务' AS item, COUNT(*) AS cnt FROM t_learn_task;                  -- 期望 0
SELECT 'after:学习记录' AS item, COUNT(*) AS cnt FROM t_learn_record;                -- 期望 0
SELECT 'after:练习记录' AS item, COUNT(*) AS cnt FROM t_practice_record;             -- 期望 0
SELECT 'after:站内通知' AS item, COUNT(*) AS cnt FROM t_sys_notice;                  -- 期望 0
SELECT 'after:登录日志' AS item, COUNT(*) AS cnt FROM t_sys_login_log;               -- 期望 0
SELECT 'after:操作日志' AS item, COUNT(*) AS cnt FROM t_sys_log;                     -- 期望 0

-- ---------- 11. 说明：学习统计"未达标"记录无需（也无法）用 SQL 清理 ----------
-- 学习管理-学习统计-单位汇总穿透到最后一层的"未达标"行，是实时计算自 t_org_user
-- 人员花名册（LearnStatService.orgUserDetail）：清理后每人有效学习时长为 0，
-- 小于达标线（参数 learn.standardSec，默认 3600 秒），故全员显示未达标。
-- 这不是残留测试数据，没有对应的可删除记录；重新下达学习任务、人员产生学习
-- 数据后即恢复真实统计。切勿为清空该页面删除 t_org_user 人员账号。

-- ---------- 12. 清空「纪检监察题库-正式」下全部题目（2026-09-22 追加） ----------
-- 仅删除题目，保留题库分类结构（纪检监察题库-正式 及其下级子库）。
-- 本机已执行：删除 283 道，题目表清零，题库分类 15 个保留不动。
DROP TEMPORARY TABLE IF EXISTS tmp_del_bank;
CREATE TEMPORARY TABLE tmp_del_bank AS
  WITH RECURSIVE k AS (
    SELECT id FROM t_ques_bank WHERE bank_name='纪检监察题库-正式' AND deleted=0
    UNION ALL
    SELECT b.id FROM t_ques_bank b JOIN k ON b.parent_id = k.id
  ) SELECT id FROM k;
DELETE FROM t_ques_item WHERE bank_id IN (SELECT id FROM tmp_del_bank);
DROP TEMPORARY TABLE tmp_del_bank;
SELECT 'after:题目(纪检监察题库-正式已清空)' AS item, COUNT(*) AS cnt FROM t_ques_item;  -- 期望 0
