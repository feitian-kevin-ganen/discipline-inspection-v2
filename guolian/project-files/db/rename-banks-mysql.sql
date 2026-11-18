-- ============================================================================
-- rename-banks-mysql.sql — 「纪检监察题库-正式」下子分类缩写改为全称
-- 按 (父题库=纪检监察题库-正式, 原名称) 匹配更新，幂等可重复执行。
-- ============================================================================

UPDATE t_ques_bank SET bank_name = '无锡市市属国有企业贯彻落实中央八项规定精神的实施意见'
 WHERE bank_name = '中央八项规定精神实施意见' AND deleted = 0
   AND parent_id = (SELECT id FROM (SELECT id FROM t_ques_bank WHERE bank_name = '纪检监察题库-正式' AND deleted = 0) t);

UPDATE t_ques_bank SET bank_name = '中国共产党廉洁自律准则'
 WHERE bank_name = '廉洁自律准则' AND deleted = 0
   AND parent_id = (SELECT id FROM (SELECT id FROM t_ques_bank WHERE bank_name = '纪检监察题库-正式' AND deleted = 0) t);

UPDATE t_ques_bank SET bank_name = '中国共产党党内监督条例'
 WHERE bank_name = '党内监督条例' AND deleted = 0
   AND parent_id = (SELECT id FROM (SELECT id FROM t_ques_bank WHERE bank_name = '纪检监察题库-正式' AND deleted = 0) t);

UPDATE t_ques_bank SET bank_name = '中国共产党纪律检查机关监督执纪工作规则'
 WHERE bank_name = '监督执纪工作规则' AND deleted = 0
   AND parent_id = (SELECT id FROM (SELECT id FROM t_ques_bank WHERE bank_name = '纪检监察题库-正式' AND deleted = 0) t);

UPDATE t_ques_bank SET bank_name = '中华人民共和国公职人员政务处分法'
 WHERE bank_name = '政务处分法' AND deleted = 0
   AND parent_id = (SELECT id FROM (SELECT id FROM t_ques_bank WHERE bank_name = '纪检监察题库-正式' AND deleted = 0) t);

UPDATE t_ques_bank SET bank_name = '中华人民共和国监察法'
 WHERE bank_name = '监察法' AND deleted = 0
   AND parent_id = (SELECT id FROM (SELECT id FROM t_ques_bank WHERE bank_name = '纪检监察题库-正式' AND deleted = 0) t);

UPDATE t_ques_bank SET bank_name = '中华人民共和国监察法实施条例'
 WHERE bank_name = '监察法实施条例' AND deleted = 0
   AND parent_id = (SELECT id FROM (SELECT id FROM t_ques_bank WHERE bank_name = '纪检监察题库-正式' AND deleted = 0) t);

UPDATE t_ques_bank SET bank_name = '国有企业领导人员廉洁从业若干规定'
 WHERE bank_name = '国企领导廉洁从业规定' AND deleted = 0
   AND parent_id = (SELECT id FROM (SELECT id FROM t_ques_bank WHERE bank_name = '纪检监察题库-正式' AND deleted = 0) t);

UPDATE t_ques_bank SET bank_name = '纪检监察机关处理检举控告工作规则'
 WHERE bank_name = '检举控告工作规则' AND deleted = 0
   AND parent_id = (SELECT id FROM (SELECT id FROM t_ques_bank WHERE bank_name = '纪检监察题库-正式' AND deleted = 0) t);

UPDATE t_ques_bank SET bank_name = '中国共产党处分违纪党员批准权限和程序规定'
 WHERE bank_name = '处分批准权限和程序规定' AND deleted = 0
   AND parent_id = (SELECT id FROM (SELECT id FROM t_ques_bank WHERE bank_name = '纪检监察题库-正式' AND deleted = 0) t);

UPDATE t_ques_bank SET bank_name = '中国共产党问责条例'
 WHERE bank_name = '问责条例' AND deleted = 0
   AND parent_id = (SELECT id FROM (SELECT id FROM t_ques_bank WHERE bank_name = '纪检监察题库-正式' AND deleted = 0) t);

-- 校验：「纪检监察题库-正式」下的分类名称
-- SELECT bank_name FROM t_ques_bank WHERE deleted = 0
--  AND parent_id = (SELECT id FROM (SELECT id FROM t_ques_bank WHERE bank_name = '纪检监察题库-正式' AND deleted = 0) t) ORDER BY id;
