#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
答题题库 xlsx → t_ques_item 导入脚本（2026-09-22）
规则：
  1. xlsx 第 1 ~ 倒数第 2 个 sheet，按顺序映射「纪检监察题库-正式」现有子库
     （按 sort_no,id 从上到下），子库名称保持不变；
  2. 最后一个 sheet 作为新子库插入「纪检监察题库-正式」下，子库名=sheet 名；
  3. 判断题：xlsx 选项A=正确/B=错误，答案 A→T、B→F（与系统 T/F 存储口径一致）；
  4. 题目初始状态 status=2(启用)，audit_by=1，gen_type='MANUAL'（人工录入），
     与历史外部导入题目口径一致；
  5. 文件内按规范化题干 MD5 去重，重复题跳过并打印。
用法：venv-pxd/bin/python3 scripts/import_dati_ques.py [--dry-run]
"""
import html
import hashlib
import re
import sys

import openpyxl
import pymysql

XLSX = '/root/AIExamK3/biz-data/答题题库（已新增中国共产党纪律处分条例题目）.xlsx'
ROOT_BANK_NAME = '纪检监察题库-正式'
DB = dict(host='10.110.40.125', port=3306, user='exam_admin',
          password='Oh]fT>*PA&,6', database='exam_db', charset='utf8mb4')

QTYPE = {'单选': 1, '多选': 2, '判断': 3}
DIFF = {'简单': 1, '中等': 2, '困难': 3, '易': 1, '中': 2, '难': 3}
SCORE = {1: 3.00, 2: 4.00, 3: 2.00}

TAG_RE = re.compile(r'<[^>]+>', re.S)
WS_RE = re.compile(r'\s+')


def normalize(stem):
    """与 StemTextUtils.normalize 一致：去标签→实体还原→全角转半角→去空白→小写"""
    text = TAG_RE.sub('', stem or '')
    text = html.unescape(text)
    out = []
    for ch in text:
        o = ord(ch)
        if ch == '　':
            out.append(' ')
        elif 0xFF01 <= o <= 0xFF5E:
            out.append(chr(o - 0xFEE0))
        else:
            out.append(ch)
    return WS_RE.sub('', ''.join(out)).lower()


def stem_md5(stem):
    return hashlib.md5(normalize(stem).encode('utf-8')).hexdigest()


def cut(s, n):
    s = (s or '').strip()
    return s[:n] if s else None


def main():
    dry = '--dry-run' in sys.argv
    wb = openpyxl.load_workbook(XLSX, read_only=True)
    sheets = wb.sheetnames
    map_sheets, new_sheet = sheets[:-1], sheets[-1]
    print(f'sheet 总数 {len(sheets)}：映射 {len(map_sheets)} 个，新建子库 1 个（{new_sheet}）')

    conn = pymysql.connect(**DB)
    cur = conn.cursor()

    cur.execute("SELECT id FROM t_ques_bank WHERE bank_name=%s AND deleted=0", (ROOT_BANK_NAME,))
    root_id = cur.fetchone()[0]
    cur.execute("SELECT id, bank_name, sort_no FROM t_ques_bank "
                "WHERE parent_id=%s AND deleted=0 ORDER BY sort_no, id", (root_id,))
    children = cur.fetchall()
    print(f'现有子库 {len(children)} 个')
    if len(children) != len(map_sheets):
        raise SystemExit(f'子库数({len(children)})与待映射 sheet 数({len(map_sheets)})不一致，中止')

    sheet_bank = {}
    for (bid, bname, _), sname in zip(children, map_sheets):
        sheet_bank[sname] = bid
        print(f'  sheet[{sname}] -> 子库[{bname}](id={bid})')

    # 新子库
    cur.execute("SELECT id FROM t_ques_bank WHERE parent_id=%s AND bank_name=%s AND deleted=0",
                (root_id, new_sheet))
    row = cur.fetchone()
    if row:
        new_bank_id = row[0]
        print(f'新子库已存在 id={new_bank_id}，直接导入')
    else:
        if dry:
            new_bank_id = -1
            print(f'[dry] 将新建子库[{new_sheet}] sort_no={max(c[2] for c in children) + 1}')
        else:
            cur.execute(
                "INSERT INTO t_ques_bank (parent_id, bank_name, path, level, sort_no, status,"
                " create_by, create_time, update_time, deleted)"
                " VALUES (%s,%s,'',2,%s,1,1,NOW(6),NOW(6),0)",
                (root_id, new_sheet, max(c[2] for c in children) + 1))
            new_bank_id = cur.lastrowid
            cur.execute("UPDATE t_ques_bank SET path=%s WHERE id=%s",
                        (f'/{root_id}/{new_bank_id}/', new_bank_id))
            print(f'新建子库[{new_sheet}] id={new_bank_id}')
    sheet_bank[new_sheet] = new_bank_id

    # 解析 + 去重 + 组装
    items = []
    seen = set()
    skip_dup = skip_bad = 0
    for sname in sheets:
        ws = wb[sname]
        doc_title = None
        for r_i, row in enumerate(ws.iter_rows(values_only=True), start=1):
            if r_i == 1:
                doc_title = cut(re.sub(r'^文档：', '', str(row[0] or '').strip()), 128)
                continue
            if r_i == 2:
                continue
            if row is None or (row[0] is None and row[3] is None):
                continue
            qtype_cn = str(row[1] or '').strip()
            diff_cn = str(row[2] or '').strip()
            stem = str(row[3] or '').strip()
            ans_raw = str(row[8] or '').strip().upper()
            if not stem or qtype_cn not in QTYPE:
                skip_bad += 1
                print(f'  [跳过-坏行] sheet[{sname}] 行{r_i}: 题型={qtype_cn!r} 题干={stem[:20]!r}')
                continue
            qt = QTYPE[qtype_cn]
            md5 = stem_md5(stem)
            if md5 in seen:
                skip_dup += 1
                print(f'  [跳过-重复] sheet[{sname}] 行{r_i}: {stem[:30]}')
                continue
            seen.add(md5)
            if qt == 3:  # 判断：A=正确→T, B=错误→F
                options = [{'key': 'T', 'content': '正确'}, {'key': 'F', 'content': '错误'}]
                amap = {'A': 'T', 'B': 'F', 'T': 'T', 'F': 'F'}
                keys = [amap[a] for a in ans_raw if a in amap]
                if len(keys) != 1:
                    skip_bad += 1
                    print(f'  [跳过-答案异常] sheet[{sname}] 行{r_i}: 答案={ans_raw!r}')
                    continue
            else:
                opts = []
                for i, key in enumerate('ABCD'):
                    v = row[4 + i]
                    if v is not None and str(v).strip():
                        opts.append({'key': key, 'content': str(v).strip()})
                keys = sorted({c for c in ans_raw if c in 'ABCD'})
                if len(opts) < 2 or not keys or not set(keys) <= {o['key'] for o in opts}:
                    skip_bad += 1
                    print(f'  [跳过-选项/答案异常] sheet[{sname}] 行{r_i}: 答案={ans_raw!r}')
                    continue
                if qt == 1 and len(keys) != 1:
                    skip_bad += 1
                    print(f'  [跳过-单选多答案] sheet[{sname}] 行{r_i}: 答案={ans_raw!r}')
                    continue
                options = opts
            import json
            items.append((
                sheet_bank[sname], qt, stem, md5,
                json.dumps(options, ensure_ascii=False),
                json.dumps({'keys': keys}, ensure_ascii=False),
                cut(str(row[9] or ''), None),  # analysis ← 资料原文出处
                DIFF.get(diff_cn, 2), SCORE[qt],
                doc_title,                       # source_law ← 文档名
                cut(str(row[10] or ''), 128),    # source_chapter ← 题目来源位置
            ))
    print(f'解析完成：有效 {len(items)} 题，重复跳过 {skip_dup}，异常跳过 {skip_bad}')

    if dry:
        print('[dry] 不写库，结束')
        conn.close()
        return

    sql = ("INSERT INTO t_ques_item (bank_id, ques_type, stem, stem_md5, options, answer,"
           " analysis, difficulty, score_suggest, knowledge_tags, source, status, audit_by,"
           " audit_time, use_count, answer_count, right_count, right_rate, create_by,"
           " create_time, update_time, deleted, source_law, source_chapter, gen_type)"
           " VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,NULL,NULL,2,1,NOW(6),0,0,0,NULL,1,"
           "NOW(6),NOW(6),0,%s,%s,'MANUAL')")
    cur.executemany(sql, [(i[0], i[1], i[2], i[3], i[4], i[5], i[6], i[7], i[8],
                           i[9], i[10]) for i in items])
    conn.commit()
    print(f'入库完成：{cur.rowcount} 题')

    # 验证
    cur.execute("SELECT b.bank_name, COUNT(i.id) FROM t_ques_bank b "
                "LEFT JOIN t_ques_item i ON i.bank_id=b.id AND i.deleted=0 "
                "WHERE b.parent_id=%s AND b.deleted=0 GROUP BY b.id ORDER BY b.sort_no, b.id",
                (root_id,))
    for name, cnt in cur.fetchall():
        print(f'  {name}: {cnt} 题')
    conn.close()


if __name__ == '__main__':
    main()
