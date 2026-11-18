# 在线考试系统本机运行规约

> 适用范围：`/home/cloud/projects/AIExamK3`（纪检监察人员在线考试培训系统）
> 使用场景：在本机启动、重启、验证服务。本机只运行，不单独打包归档。

## 1. 本机环境配置

> 以下配置基于当前机器实际环境采集，启动前必须核对。

### 1.1 机器信息

| 项目 | 配置值 |
|------|--------|
| 操作系统 | Ubuntu 24.04.3 LTS (noble) |
| 本机 IP | `10.110.40.160` |
| 部署运行目录 | `/home/cloud/AIExamK3` |

### 1.2 中间件与工具

| 组件 | 版本/路径 | 说明 |
|------|-----------|------|
| JDK | OpenJDK 17.0.21 | 系统已安装 |
| Maven | 3.9.9 | 仅用于构建 |
| Node.js | v22.22.1 | 仅用于构建 |
| npm | 10.9.4 | 仅用于构建 |
| Python | 3.12.3 | 用于网关与校验脚本 |
| PostgreSQL | 16.15 | `127.0.0.1:5434`，库 `exam_db` |
| Redis | 7.0.15 | `127.0.0.1:6379`，无密码 |
| MinIO | RELEASE.2025-07-23 | `http://127.0.0.1:9000`，bucket `exam-files` |
| ffmpeg | `/home/cloud/AIExamK3/middleware/ffmpeg` | 视频封面抽帧 |

### 1.3 应用配置

配置来源：`code/exam-server/src/main/resources/application.yml`；运行副本 `/home/cloud/AIExamK3/config/application.yml` 必须与下表一致。

| 配置项 | 规约值 | 说明 |
|--------|--------|------|
| 后端端口 | `8088` | 不可随意变更，网关 `/api/` 反代指向它 |
| 统一入口端口 | `6003` | 三端静态 + `/api` 反代，与目标机 6003 单入口口径一致 |
| 数据库驱动 | `org.postgresql.Driver` | 本机使用 PostgreSQL |
| 数据库 URL | `jdbc:postgresql://127.0.0.1:5434/exam_db?stringtype=unspecified` | 本地 PaaS PostgreSQL |
| 数据库账号 | `postgres / postgres` | 与本地 PostgreSQL 实例一致 |
| Redis host | `127.0.0.1` | 本地 PaaS Redis |
| Redis port | `6379` | 本地 PaaS Redis |
| Redis password | （空） | 本地 Redis 无密码 |
| MinIO endpoint | `http://127.0.0.1:9000` | 本地 PaaS MinIO |
| MinIO public-endpoint | `http://10.110.40.160:9000` | **必须与本机实际 IP 一致** |
| MinIO access-key | `minioadmin` | 本地 MinIO 默认账号 |
| MinIO secret-key | `minioadmin` | 本地 MinIO 默认密码 |
| MinIO bucket | `exam-files` | 本地 MinIO bucket 名 |
| ffmpeg 路径 | `/home/cloud/AIExamK3/middleware/ffmpeg` | 运行路径 |
| JWT secret | `9f4c2a7b8d1e3f5061728394a5b6c7d8e9f0a1b2c3d4e5f60718293a4b5c6d7e8` | 与目标机共用同一签名密钥 |
| JWT access-expire | `43200` 秒 | 与目标机一致 |
| JWT refresh-expire | `604800` 秒 | 与目标机一致 |

> **强制检查项**：启动前执行 `hostname -I` 核对本机 IP。若 IP 不是 `10.110.40.160`，必须同步修改 `application.yml` 中的 `minio.public-endpoint`，否则外部访问 MinIO 预签名 URL 会失败。

## 2. 运行目录

本机不生成打包归档，直接运行部署目录：

```text
/home/cloud/AIExamK3/
├── app/exam-server.jar
├── config/application.yml
├── web/{admin,user,h5}/
├── middleware/ffmpeg
├── scripts/{start_all.sh,stop_all.sh,local_gateway.py,verify_api.py,verify_pdf_pages.py}
├── logs/
└── run/
```

- 启停与校验脚本以部署目录 `/home/cloud/AIExamK3/scripts/` 为准。
- 三端 dist 构建时 base 分别为 `/mgmt/`、`/learning-web/`、`/learning-h5/`。

## 3. 启动 / 停止

```bash
cd /home/cloud/AIExamK3
bash scripts/start_all.sh          # 启动后端 + 网关，并打印入口
bash scripts/start_all.sh status   # 只看状态
bash scripts/stop_all.sh           # 只停本目录 run/*.pid 记录的进程
```

`start_all.sh` 完成：

1. 依赖探测：`127.0.0.1:5434`、`6379`、`9000`，缺失只告警不阻断。
2. 启动后端 jar（端口 8088）：
   `java -jar app/exam-server.jar --spring.config.additional-location=file:$APP_DIR/config/`
3. 等待 `/v3/api-docs` 可用（最长 120 秒）。
4. 启动统一入口网关（端口 6003）：`python3 scripts/local_gateway.py --port 6003 --root $APP_DIR`。

`local_gateway.py` 等价实现目标机 InforSuiteLB 的路由模型（本机无 InforSuiteLB）：

| 前缀 | 去向 |
|------|------|
| `/mgmt/` | `web/admin`（SPA 回退 index.html） |
| `/learning-web/` | `web/user` |
| `/learning-h5/` | `web/h5` |
| `/api/` | 反代 `127.0.0.1:8088`，响应加 `Cache-Control: no-store` |
| `/` | 302 到 `/learning-h5/` |
| `/guolian-jijian/login` | `web/sso/index.html`（不存在时返回 404 说明页） |

## 4. 验证

```bash
# 接口全流程（64 项，经网关）
python3 scripts/verify_api.py --base http://127.0.0.1:6003/api/v1

# 三端入口
for p in /mgmt/ /learning-web/ /learning-h5/; do curl -s -o /dev/null -w "$p %{http_code}\n" http://127.0.0.1:6003$p; done

# 可选：PDF 页图预览（需 playwright；默认走 6003 的两个用户端前缀）
python3 scripts/verify_pdf_pages.py
```

## 5. 注意事项

- 本机**不生成 `local-deploy` 归档**，启动所需文件直接位于 `/home/cloud/AIExamK3`。
- 依赖本地 PaaS：PostgreSQL `exam_db`、Redis `6379`、MinIO `9000` 必须可用。
- 若本机 IP 变化，须同步修改 `config/application.yml` 中的 `minio.public-endpoint`。
- 本机配置与目标机配置**严禁混用**：目标机是 PolarDB-X/MySQL + OSS + InforSuiteLB，本机是
  PostgreSQL + 本地 MinIO + `local_gateway.py`。
- 端口模型：后端 `8088` 只被网关反代，对外只需开放 `6003`；不要沿用历史上
  `6009/6002/6001` 分端口静态服务的做法，三端 dist 已按 `/mgmt/`、`/learning-web/`、
  `/learning-h5/` 前缀构建，分端口静态服务会导致资源 404。
- 停服务只按 `run/*.pid` 停止，不得 `pkill java` 或按端口杀进程（会波及其他项目）。
