# 在线考试系统本机打包发布规约

> 适用范围：`/home/cloud/projects/AIExamK3`（纪检监察人员在线考试培训系统）  
> 使用场景：用户明确要求“打包本机并发布运行”时。

## 1. 本机环境配置

> 以下配置基于当前打包机器实际环境采集，打包前必须核对。

### 1.1 机器信息

| 项目 | 配置值 |
|------|--------|
| 操作系统 | Ubuntu 24.04.3 LTS (noble) |
| 本机 IP | `10.110.40.160` |
| 部署根目录 | `/home/cloud/AIExamK3`（推荐）或 `/home/cloud/projects/AIExamK3/local-run/AIExamK3` |

### 1.2 中间件与工具

| 组件 | 版本/路径 | 说明 |
|------|-----------|------|
| JDK | OpenJDK 17.0.21 | 系统已安装，本机包不重复打包 |
| Maven | 3.9.9 | 仅用于构建，不进入发布包 |
| Node.js | v22.22.1 | 仅用于构建，不进入发布包 |
| npm | 10.9.4 | 仅用于构建，不进入发布包 |
| Python | 3.12.3 | 用于校验脚本 |
| PostgreSQL | 16.15 | `127.0.0.1:5434`，库 `exam_db` |
| Redis | 7.0.15 | `127.0.0.1:6379`，无密码 |
| MinIO | RELEASE.2025-07-23 | `http://127.0.0.1:9000`，bucket `exam-files` |
| ffmpeg | `/home/cloud/projects/AIExamK3/code/tools/ffmpeg` | 视频封面抽帧，须随包携带 |

### 1.3 应用配置规约

配置文件来源：`code/exam-server/src/main/resources/application.yml`

打包本机发布包时，必须保证 `deploy-files/config/application.yml` 与下表完全一致：

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
| ffmpeg 路径 | `/home/cloud/AIExamK3/middleware/ffmpeg` | 发布后路径 |
| JWT secret | `9f4c2a7b8d1e3f5061728394a5b6c7d8e9f0a1b2c3d4e5f60718293a4b5c6d7e8` | 与目标机共用同一签名密钥 |
| JWT access-expire | `43200` 秒 | 与目标机一致 |
| JWT refresh-expire | `604800` 秒 | 与目标机一致 |

> **强制检查项**：打包前必须执行 `hostname -I` 核对本机 IP。若 IP 不是 `10.110.40.160`，必须同步修改 `application.yml` 中的 `minio.public-endpoint`，否则外部访问 MinIO 预签名 URL 会失败。

---

## 2. 本机包结构

存放路径：

```text
/home/cloud/projects/AIExamK3/guolian/deploy-history/local-deploy
```

目录结构：

```text
local-deploy/
├── source-code/          # 本机运行所需的源码（不含 node_modules/target/dist，不含 ffmpeg 二进制）
│   └── code/
├── deploy-files/         # 本机要发布的运行内容（归档来源，保留目录形态便于增量对比）
│   ├── app/
│   ├── config/
│   ├── docs/
│   ├── middleware/
│   │   └── ffmpeg
│   ├── README.md
│   ├── scripts/
│   └── web/
└── local-deploy-{yyyyMMdd}-v{x}.tar.gz   # 归档包，内容为 deploy-files，顶层目录 discipline-inspection-local
```

归档包只含发布内容（`deploy-files`），不含 `source-code`；`source-code` 以目录形态保存在仓库中。

### 2.1 与目标机全量包的区别

| 内容 | 本机包 | 目标机全量包 |
|------|--------|--------------|
| 数据库 | PostgreSQL（本地 PaaS） | PolarDB-X/MySQL（目标机） |
| JDK | 使用系统 JDK，不打包 | 随包携带 JDK21 |
| MinIO | 使用本地 PaaS，不打包二进制和数据 | 随包携带 MinIO 二进制和数据 |
| Redis | 使用本地 PaaS，不打包 | 目标机系统自带 |
| 中间件目录 | 只含 `ffmpeg` | 含 `jdk21/`、`minio`、`ffmpeg`、`mysql-tools` |
| 部署根目录 | `/home/cloud/AIExamK3` | `/root/AIExamK3` |

---

## 3. 打包流程

### 3.1 构建

```bash
cd /home/cloud/projects/AIExamK3/code
mvn -q -DskipTests clean package
npm run build --prefix exam-admin-web
npm run build --prefix exam-user-web
npm run build --prefix exam-h5-web
```

### 3.2 组织本地包

1. 清空或创建 `guolian/deploy-history/local-deploy/`。
2. `source-code/`：同步 `code/` 源码（剔除 `node_modules`、`target`、`dist`、`logs`、`run`、`.venv`、`tools/ffmpeg`）。
3. `deploy-files/`：
   - `app/exam-server.jar`（`code/exam-server/target/exam-server.jar`）
   - `config/application.yml`（本机 PostgreSQL 配置，`exam.ffmpeg-path` 指向发布后路径 `middleware/ffmpeg`）
   - `web/admin/`、`web/user/`、`web/h5/`（三端 dist，构建时 base 分别为 `/mgmt/`、`/learning-web/`、`/learning-h5/`）
   - `middleware/ffmpeg`
   - `scripts/`：`start_all.sh`、`stop_all.sh`、`local_gateway.py`、`verify_api.py`、`verify_pdf_pages.py`
     （本机专用，**不含**目标机的 `deploy_release.sh`、`backup.sh`、`pg_to_mysql.py`）
   - `docs/`（项目资料，可选）
   - `logs/`、`run/`（空目录占位）
   - `README.md`
4. 归档：`tar -czf local-deploy-{yyyyMMdd}-v{x}.tar.gz --transform 's,^deploy-files,discipline-inspection-local,' deploy-files`

### 3.3 命名

本机包以目录形式为主，如需压缩归档：

```text
local-deploy-{yyyyMMDD}-v{x}.tar.gz
```

版本号规则同目标机增量包：每天从 `v1` 开始，同日每打一次增加 `0.1`。

---

## 4. 本地发布与运行

### 4.1 解压

```bash
mkdir -p /home/cloud/AIExamK3
tar -xzf local-deploy-YYYYMMDD-vX.tar.gz -C /home/cloud/AIExamK3 --strip-components=1
chmod +x /home/cloud/AIExamK3/scripts/*.sh /home/cloud/AIExamK3/scripts/*.py /home/cloud/AIExamK3/middleware/ffmpeg
```

归档包顶层目录为 `discipline-inspection-local/`，`--strip-components=1` 后 `app/`、`config/` 等直接落在
`/home/cloud/AIExamK3` 下。

### 4.2 启动 / 停止

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

### 4.3 验证

```bash
# 接口全流程（64 项，经网关）
python3 scripts/verify_api.py --base http://127.0.0.1:6003/api/v1

# 三端入口
for p in /mgmt/ /learning-web/ /learning-h5/; do curl -s -o /dev/null -w "$p %{http_code}\n" http://127.0.0.1:6003$p; done

# 可选：PDF 页图预览（需 playwright；默认走 6003 的两个用户端前缀）
python3 scripts/verify_pdf_pages.py
```

---

## 5. 注意事项

- 本机包**不携带数据库、Redis、MinIO、JDK**，依赖本地 PaaS 环境。
- 发布前确认 PostgreSQL `exam_db`、Redis `6379`、MinIO `9000` 均已可用。
- 若本机 IP 变化，须同步修改 `config/application.yml` 中的 `minio.public-endpoint`。
- 本机包与目标机包**严禁混用配置**：目标机是 PolarDB-X/MySQL + OSS + InforSuiteLB，本机是
  PostgreSQL + 本地 MinIO + `local_gateway.py`。
- 端口模型：后端 `8088` 只被网关反代，对外只需开放 `6003`；本机不要再沿用历史上
  `6009/6002/6001` 分端口静态服务的做法，三端 dist 已按 `/mgmt/`、`/learning-web/`、
  `/learning-h5/` 前缀构建，分端口静态服务会导致资源 404。
- 停服务只按 `run/*.pid` 停止，不得 `pkill java` 或按端口杀进程（会波及其他项目）。
- 每个版本的发布记录（`local-deploy-{yyyyMMdd}-v{x}.tar.gz`、md5、验证结果）需与
  `source-code` 一并在仓库中留存；打包后同步更新本规约中的端口/配置口径（如有变化）。
