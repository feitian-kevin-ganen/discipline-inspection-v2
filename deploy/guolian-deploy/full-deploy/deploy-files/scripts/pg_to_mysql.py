#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PostgreSQL -> PolarDB-X(MySQL 8.0 兼容) 结构与数据迁移脚本
用法:
  python3 pg_to_mysql.py --pg-host 127.0.0.1 --pg-port 5434 --pg-db exam_db \
      --pg-user postgres --pg-pass postgres \
      --my-host 10.110.10.163 --my-port 3306 --my-db exam_db \
      --my-user root --my-pass 'xxx'
说明:
  - 通过 information_schema 内省 PG 表结构，自动生成 MySQL DDL 并建表
  - jsonb -> LONGTEXT(应用层 TypeHandler 按字符串读写，避免 JSON 列规范化改写)
  - timestamptz/timestamp -> DATETIME(6)(保留微秒，去掉时区)
  - bool -> TINYINT(1)；bigserial/serial -> 自增主键
  - 唯一约束保留；普通二级索引数据量小不迁移
"""
import argparse
import json
import sys
from datetime import datetime
from decimal import Decimal

import psycopg2
import pymysql

TYPE_MAP = {
    'int8': 'BIGINT',
    'int4': 'INT',
    'int2': 'SMALLINT',
    'bool': 'TINYINT(1)',
    'text': 'LONGTEXT',
    'jsonb': 'LONGTEXT',
    'json': 'LONGTEXT',
    'uuid': 'VARCHAR(64)',
    'date': 'DATE',
    'float8': 'DOUBLE',
    'float4': 'FLOAT',
    'bytea': 'LONGBLOB',
}


def mysql_type(col):
    udt = col['udt_name']
    dt = col['data_type']
    if udt in TYPE_MAP:
        return TYPE_MAP[udt]
    if udt == 'varchar':
        n = col['character_maximum_length'] or 255
        return f'VARCHAR({min(n, 1024)})'
    if udt in ('numeric',):
        p = col['numeric_precision'] or 18
        s = col['numeric_scale'] or 0
        return f'DECIMAL({p},{s})'
    if udt in ('timestamp', 'timestamptz'):
        return 'DATETIME(6)'
    if udt == 'time':
        return 'TIME'
    raise ValueError(f"未映射类型: {udt} ({dt}) 列 {col['table_name']}.{col['column_name']}")


def conv_value(v):
    """PG 值 -> MySQL 可插入值"""
    if isinstance(v, bool):
        return 1 if v else 0
    if isinstance(v, (dict, list)):
        return json.dumps(v, ensure_ascii=False)
    if isinstance(v, datetime):
        return v.replace(tzinfo=None)
    if isinstance(v, Decimal):
        return str(v)
    if isinstance(v, (bytes, bytearray, memoryview)):
        return bytes(v)
    return v


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--pg-host', default='127.0.0.1')
    ap.add_argument('--pg-port', type=int, default=5434)
    ap.add_argument('--pg-db', default='exam_db')
    ap.add_argument('--pg-user', default='postgres')
    ap.add_argument('--pg-pass', default='postgres')
    ap.add_argument('--my-host', required=True)
    ap.add_argument('--my-port', type=int, required=True)
    ap.add_argument('--my-db', default='exam_db')
    ap.add_argument('--my-user', required=True)
    ap.add_argument('--my-pass', required=True)
    args = ap.parse_args()

    pg = psycopg2.connect(host=args.pg_host, port=args.pg_port, dbname=args.pg_db,
                          user=args.pg_user, password=args.pg_pass)
    my = pymysql.connect(host=args.my_host, port=args.my_port, user=args.my_user,
                         password=args.my_pass, database=args.my_db, charset='utf8mb4',
                         autocommit=False)
    pgc = pg.cursor()
    myc = my.cursor()
    myc.execute('SET FOREIGN_KEY_CHECKS=0')
    myc.execute("SET NAMES utf8mb4")

    # 1. 枚举表
    pgc.execute("""
        SELECT table_name FROM information_schema.tables
        WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name""")
    tables = [r[0] for r in pgc.fetchall()]
    print(f'共 {len(tables)} 张表待迁移')

    for t in tables:
        # 2. 列定义
        pgc.execute("""
            SELECT column_name, data_type, udt_name, character_maximum_length,
                   numeric_precision, numeric_scale, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema='public' AND table_name=%s ORDER BY ordinal_position""", (t,))
        cols = []
        auto_inc_col = None
        for r in pgc.fetchall():
            col = dict(zip(['column_name', 'data_type', 'udt_name', 'character_maximum_length',
                            'numeric_precision', 'numeric_scale', 'is_nullable', 'column_default'], r))
            mtype = mysql_type(col)
            default = col['column_default'] or ''
            is_auto = 'nextval(' in default
            if is_auto:
                auto_inc_col = col['column_name']
            ddl = f"`{col['column_name']}` {mtype}"
            if is_auto:
                ddl += ' NOT NULL AUTO_INCREMENT'
            else:
                ddl += ' NULL' if col['is_nullable'] == 'YES' else ' NOT NULL'
                # 不迁移序列/now() 等 PG 侧默认值，时间默认值由应用层写入
            cols.append(ddl)

        # 3. 主键
        pgc.execute("""
            SELECT a.attname FROM pg_index i
            JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
            WHERE i.indrelid = %s::regclass AND i.indisprimary""", (t,))
        pk = [r[0] for r in pgc.fetchall()]
        if pk:
            cols.append(f"PRIMARY KEY ({','.join('`' + c + '`' for c in pk)})")

        # 4. 唯一约束（排除主键）
        pgc.execute("""
            SELECT con.conname, array_agg(a.attname ORDER BY u.ord)
            FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            JOIN unnest(con.conkey) WITH ORDINALITY u(attnum, ord) ON true
            JOIN pg_attribute a ON a.attrelid = rel.oid AND a.attnum = u.attnum
            WHERE rel.relname = %s AND con.contype = 'u'
            GROUP BY con.conname""", (t,))
        for name, ucols in pgc.fetchall():
            cols.append(f"UNIQUE KEY `{name}` ({','.join('`' + c + '`' for c in ucols)})")

        ddl = (f"CREATE TABLE `{t}` (\n  " + ",\n  ".join(cols) +
               "\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci")
        myc.execute(f'DROP TABLE IF EXISTS `{t}`')
        try:
            myc.execute(ddl)
        except Exception as e:
            print(f'[建表失败] {t}: {e}\nDDL:\n{ddl}')
            raise

        # 5. 数据
        pgc.execute(f'SELECT * FROM "{t}"')
        colnames = [d.name for d in pgc.description]
        rows = pgc.fetchall()
        if rows:
            ph = ','.join(['%s'] * len(colnames))
            sql = f"INSERT INTO `{t}` ({','.join('`' + c + '`' for c in colnames)}) VALUES ({ph})"
            batch = [tuple(conv_value(v) for v in row) for row in rows]
            try:
                myc.executemany(sql, batch)
            except Exception as e:
                print(f'[数据失败] {t}: {e}')
                raise
        # 6. 自增起点
        if auto_inc_col and rows:
            pgc.execute(f'SELECT MAX("{auto_inc_col}") FROM "{t}"')
            mx = pgc.fetchone()[0] or 0
            myc.execute(f'ALTER TABLE `{t}` AUTO_INCREMENT = {mx + 1}')
        my.commit()
        print(f'[OK] {t}: {len(rows)} 行')

    # 7. 校验行数一致
    bad = []
    for t in tables:
        pgc.execute(f'SELECT COUNT(*) FROM "{t}"')
        pgn = pgc.fetchone()[0]
        myc.execute(f'SELECT COUNT(*) FROM `{t}`')
        myn = myc.fetchone()[0]
        if pgn != myn:
            bad.append((t, pgn, myn))
    myc.execute('SET FOREIGN_KEY_CHECKS=1')
    if bad:
        print('行数不一致:', bad)
        sys.exit(1)
    print(f'全部完成，{len(tables)} 张表行数一致。')
    pgc.close(); pg.close(); myc.close(); my.close()


if __name__ == '__main__':
    main()
