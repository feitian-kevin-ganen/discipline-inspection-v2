# 纪检监察人员在线考试系统 — 代码工程

依据 `../requirement/01-在线考试系统用户需求书.md` 与 `../design/` 下四份设计文档实现。

## 工程结构

| 目录 | 说明 | 端口 |
| --- | --- | --- |
| `exam-parent (pom.xml)` | Maven 父工程 | — |
| `exam-common` | 通用响应/异常/工具/常量 | — |
| `exam-system` | 组织、人员、角色权限、系统参数、日志、通知 | — |
| `exam-learn` | 学习资料、在线学习心跳、学习任务、学习统计 | — |
| `exam-question` | 题库、题目、试卷、抽题策略、等级规则 | — |
| `exam-exam` | 考试场次、考生下发、答卷、判分、阅卷、防作弊 | — |
| `exam-stat` | 考试统计（总览/分层级/分数段/等级/导出） | — |
| `exam-server` | 聚合启动模块（Spring Boot 可执行 Jar） | 8088 |
| `exam-admin-web` | PC 管理端（Vue3 + Element Plus） | 6000 |
| `exam-h5-web` | 手机 H5 考生端（Vue3 + Vant） | 6001 |
| `exam-user-web` | PC 用户端（Vue3 + Element Plus） | 6002 |
| `exam-miniprogram` | 原生微信小程序（WXML/WXSS/JS，功能对齐 H5 端；`preview/` 为浏览器说明预览页） | 6003（预览页） |
| `exam-h5miniprogram` | H5 套壳小程序（`miniprogram/` 为 web-view 壳源码；`preview/` 为模拟壳预览页） | 6004（预览页） |
| `scripts/start_all.sh` | 一键启动/停止/查看全部服务 | — |
| `scripts/verify_api.py` | 端到端 API 自动校验脚本 | — |

## 依赖中间件（PaaS 目录已安装实例）

| 组件 | 地址 | 凭据 |
| --- | --- | --- |
| PostgreSQL 16 | `127.0.0.1:5434` | `postgres / postgres`，库名 `exam_db` |
| Redis 7 | `127.0.0.1:6379` | 无密码 |
| MinIO | `http://127.0.0.1:9000` | `minioadmin / minioadmin`，bucket `exam-files` |

数据库脚本：`exam-server/src/main/resources/db/schema.sql`（建表）、`data-init.sql`（角色/参数/默认等级/admin）、`data-seed.sql`（示例组织人员/学习资料/题库试题）。

## 启动与校验

```bash
# 编译后端
cd code && mvn -q -DskipTests package

# 一键启动（后端8088 + 三前端6000/6001/6002）
code/scripts/start_all.sh start     # stop / status 可用

# 端到端 API 校验（登录→组卷→下发→答卷→阅卷→发布→统计→学习 全流程）
python3 code/scripts/verify_api.py
```

日志：`code/logs/`；PID：`code/run/`。

## 初始账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `Admin@12345`（首次部署初始为 `Admin@123`，首登已按强制改密流程修改） | 超级管理员 |
| 种子数据账号（工号见 `data-seed.sql`，如 `hqadmin01`/`marker01`/`ks001`~`ks006`） | `Exam@123` | 各级管理员/阅卷教师/考生 |
