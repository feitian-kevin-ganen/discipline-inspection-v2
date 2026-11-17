# 在线考试系统打包发布规约

> 适用范围：`/home/cloud/projects/AIExamK3`（纪检监察人员在线考试培训系统）

## 1. 全量包（full-deploy）

### 1.1 存放路径

```text
/home/cloud/projects/AIExamK3/guolian/deploy-history/full-deploy
```

### 1.2 目录结构

```text
full-deploy/
├── source-code/          # 目标机部署所需的源代码（即代码工程）
│   └── code/             # 后端、前端、脚本等源码
├── deploy-assets/        # 发布包内可变文本资产的权威副本（README.md、scripts/）
└── deploy-files/         # 目标机要发布的运行内容（仅保留压缩包）
    └── full-deploy-{yyyyMMDD}-v{x}.tar.gz
```

解压后结构：

```text
AIExamK3/
├── app/
├── config/
├── data/
├── docs/
├── logs/
├── middleware/
├── README.md
├── run/
├── scripts/
└── web/
```

### 1.3 内容说明

| 目录/文件 | 归属 | 说明 |
|-----------|------|------|
| `source-code/code/` | `source-code` | 项目源码工程，包括 Maven 后端、Vue 前端、脚本源码等。 |
| `deploy-assets/README.md` | `deploy-assets` | 发布包内 `README.md` 的权威副本，打包时复制进压缩包根目录。 |
| `deploy-assets/scripts/` | `deploy-assets` | 发布包内 `scripts/` 的权威副本（`start_all.sh`、`stop_all.sh`、`backup.sh`、`verify_*.py` 等）。 |
| `deploy-files/full-deploy-*.tar.gz` | `deploy-files` | 目标机全量发布压缩包，解压后顶层为 `AIExamK3/`。 |
| `AIExamK3/app/` | 压缩包内 | 后端可执行 jar（`exam-server.jar`）。 |
| `AIExamK3/config/` | 压缩包内 | 目标环境外置配置（如 `application-polardb.yml`）。 |
| `AIExamK3/data/` | 压缩包内 | 运行数据，如 `minio/exam-files/`。 |
| `AIExamK3/docs/` | 压缩包内 | 需求、设计、迁移说明等项目资料。 |
| `AIExamK3/logs/` | 压缩包内 | 预留运行日志目录。 |
| `AIExamK3/middleware/` | 压缩包内 | JDK、MinIO、ffmpeg、mysql-tools 等运行依赖。 |
| `AIExamK3/run/` | 压缩包内 | 预留 PID 目录。 |
| `AIExamK3/scripts/` | 压缩包内 | 启动、停止、备份、迁移、校验等脚本。 |
| `AIExamK3/web/` | 压缩包内 | 三端前端构建产物（`admin/`、`user/`、`h5/`）。 |
| `AIExamK3/README.md` | 压缩包内 | 目标机部署说明。 |

### 1.4 打包要求

- 全量包必须包含**最新编译结果**：后端 `mvn clean package`、前端 `npm run build:target`（三端必须带目标机 base，见 5.7）。
- 包内**不得出现 nginx 配置或 nginx 启动逻辑**：目标机反向代理是 InforSuiteLB。
- 源码以**展开目录**形式存放，不再保留 `code-src.tar.gz`。
- 运行内容必须打成单一压缩包：
  - 命名：`full-deploy-{yyyyMMDD}-v{x}.tar.gz`
  - 版本号规则：每天从 `v1` 开始，同一天每打一次增加 `0.1`。
  - 示例：`full-deploy-20261116-v1.tar.gz`、`full-deploy-20261116-v1.1.tar.gz`
  - 压缩包顶层目录为 `AIExamK3/`，解压到 `/root` 后即为 `/root/AIExamK3`。
- `deploy-files/` 目录下**只保留压缩包**，不再保留未压缩的 `app/`、`config/` 等原始文件夹。

### 1.5 源码同步要求

**每次发布打包目标机全量包时，必须同步更新并推送远端 `guolian/deploy-history/full-deploy/source-code/` 目录。**

具体操作：

1. 将本次构建所使用的源码同步到 `guolian/deploy-history/full-deploy/source-code/code/`。
2. 确保 `source-code/` 下不包含 `node_modules/`、`target/`、`dist/` 等编译产物。
3. 将 `source-code/` 的变更与本次发布说明一起提交到 Git，并推送至远端仓库。
4. 远端仓库中的源码版本必须与本次发布的 `deploy-files/` 版本一一对应。

---

## 2. 增量包（incremental-deploy）

### 2.1 存放路径

```text
/home/cloud/projects/AIExamK3/guolian/deploy-history/incremental-deploy
```

### 2.2 命名规则

```text
deploy-increment-{yyyyMMDD}-v{x}.tar.gz
```

- `yyyyMMDD`：打包当天日期，例如 `20261116`。
- `x`：当天版本号，**每天从 1 开始**。
- 同一天每打一次增量包，`x` 增加 `0.1`。

#### 示例

| 时间 | 文件名 |
|------|--------|
| 2026-11-16 第一次 | `deploy-increment-20261116-v1.tar.gz` |
| 2026-11-16 第二次 | `deploy-increment-20261116-v1.1.tar.gz` |
| 2026-11-16 第三次 | `deploy-increment-20261116-v1.2.tar.gz` |
| 2026-11-17 第一次 | `deploy-increment-20261117-v1.tar.gz` |

### 2.3 内容要求

增量包只应包含**自上一版本以来发生变更**的文件，常见内容参考：

```text
deploy-increment-YYYYMMDD-vX.tar.gz
├── UPDATE.md                  # 本次更新说明（必含）
├── app/
│   └── exam-server.jar        # 变更后的后端 jar
├── config/
│   └── application-polardb.yml # 变更后的配置
├── web/
│   ├── admin/                 # 变更后的前端产物
│   ├── user/
│   └── h5/
└── db/
    ├── patch-YYYYMMDD-xxx.sql # 数据库补丁脚本
    └── ...
```

- 不应重复包含未变更的 `middleware/`、`data/` 等大体积目录。
- 若数据库有变更，必须附带 `UPDATE.md` 和 `db/` 补丁脚本。

---

## 3. 源码与二进制管理

- **禁止**将编译产物、依赖目录、部署包压缩文件提交到 Git：
  - `node_modules/`
  - `target/`
  - `dist/`
  - `logs/`、`run/`（运行态目录）
  - `guolian/deploy-history/**/*.tar.gz`
  - `guolian/deploy-history/full-deploy/deploy-files/data/minio/exam-files/`
  - `.venv*/`
- Git 仓库保存的内容包括：
  - 源码（根目录 `code/` 以及 `guolian/deploy-history/full-deploy/source-code/code/`）
  - 配置、设计/需求文档、打包规约
  - 脚本源码等。
- `guolian/deploy-history/full-deploy/source-code/` 必须随每次全量发布同步更新并推送。

---

## 4. 版本号记录

每次打包后，应在 `UPDATE.md` 或规约附录中记录：

- 全量包日期
- 增量包文件名及对应变更内容
- 目标机部署根目录（`/root/AIExamK3`）

---

## 5. 目标机环境配置

> 以下配置来自最新增量包 `deploy-increment-merged-20261013.tar.gz`，是打包与发布时必须对齐的目标环境。

### 5.1 基础信息

| 项目 | 配置值 |
|------|--------|
| 目标机器 IP | `10.64.16.29` |
| 部署根目录 | `/root/AIExamK3` |
| 后端端口 | `8088` |

### 5.2 数据库

| 项目 | 配置值 |
|------|--------|
| 类型 | PolarDB for MySQL / MySQL 8.0 兼容 |
| IP / 端口 | `10.64.16.14:4886` |
| 库名 | `exam_db` |
| 用户名 | `exam_admin` |
| 密码 | `z>PD?8Wr_W7h` |

### 5.3 Redis

| 项目 | 配置值 |
|------|--------|
| IP / 端口 | `10.64.16.29:6379` |
| 密码 | `Glgc@2026` |

### 5.4 MinIO（S3 兼容对象存储）

| 项目 | 配置值 |
|------|--------|
| endpoint / public-endpoint | `http://10.10.250.100:8333` |
| access-key | `891fef1e206847e1a073d47fcfe8252b` |
| secret-key | `B7E41087CA32E871D71316A7BA44D4FAF283D3152073AC003C2B7C448A67D05E40D198F686555F236ED9FC8D86741564` |
| bucket | `exam-files` |

### 5.5 AI 生题

| 项目 | 配置值 |
|------|--------|
| base-url | `http://10.64.251.169:4000` |
| model | `deepseek-v4-flash` |

### 5.6 数字国联 APP 集成

| 项目 | 配置值 |
|------|--------|
| base-url | `http://10.20.8.8:32389` |
| client-id | `guolian-jijian` |

### 5.7 反向代理与端口对照（InforSuiteLB，不是 Nginx）

> **目标机没有 nginx**。三端静态资源与 `/api/` 反代由 **InforSuiteLB** 承载，安装目录 `/data/InforSuiteLB`。

| 端口 | 前端 | 静态目录 | 访问路径（base） |
|------|------|----------|------------------|
| `6009` | PC 管理端 | `/root/AIExamK3/web/admin` | `/mgmt/` |
| `6002` | PC 用户端 | `/root/AIExamK3/web/user` | `/learning-web/` |
| `6001` | H5 移动端 | `/root/AIExamK3/web/h5` | `/learning-h5/` |
| `8088` | 后端 API | `app/exam-server.jar` | `/api/v1` |

- 前端构建必须带 base：`npm run build:target`（admin `--base=/mgmt/`、user `--base=/learning-web/`、h5 `--base=/learning-h5/`），路由使用 `createWebHistory(import.meta.env.BASE_URL)`。用默认 `npm run build` 打出的 `/assets/...` 在目标机会白屏。
- LB 主配置：`/data/InforSuiteLB/conf/InforSuiteLB.conf`；站点配置：`/data/InforSuiteLB/conf/discipline-inspection.conf`（历史文件名 `cra.conf`，已改名）。
- LB 可执行：`/data/InforSuiteLB/sbin/InforSuiteLB`；测试配置 `-p <LB_HOME> -c <conf> -t`，重载 `-s reload`。
- 增量包只有在**确实下发 LB 配置变更**时才包含 `lb/conf/`，并在 `UPDATE.md` 写明测试与重载命令。
- 目标机数据库客户端：`/root/migration/tools/mysql/mysql.sh`，备份优先使用同目录 `bin/mysqldump`。

### 5.8 配置变更说明

- 目标机配置以 `deploy-files/config/application-polardb.yml` 为准。
- 每次发布前必须核对该文件与目标机实际环境是否一致；不一致时先更新配置再打包。
- `source-code/code/exam-server/src/main/resources/application.yml` 和 `source-code/code/config/application-polardb.yml` 必须与 `deploy-files/config/application-polardb.yml` 保持同步。
- **注意**：`source-code/code/exam-server/src/main/resources/application.yml` 是**目标机配置**（MySQL/PolarDB + 目标 Redis/MinIO），不是本机开发配置。用 `rsync code/ → source-code/code/` 同步源码后，必须把该文件恢复为目标机版本，禁止把本机 PostgreSQL 配置带进 `source-code/`。
