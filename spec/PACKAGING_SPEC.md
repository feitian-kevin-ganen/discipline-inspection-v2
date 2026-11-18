# 在线考试系统打包发布规约

> 适用范围：`/home/cloud/projects/AIExamK3`（纪检监察人员在线考试培训系统）

## 0. 目录总览

```text
/home/cloud/projects/AIExamK3/
├── code/                       # 源码工程（唯一源）
├── spec/                       # 开发 / 打包 / 本机运行规约
├── deploy/
│   ├── local-deploy/           # 本机运行目录：jar、构建后前端、本机 config、logs、run（不入库）
│   └── guolian-deploy/         # 测试机发布历史
│       ├── full-deploy/        # 全量包：source-code/ + deploy-files/
│       └── incremental-deploy/ # 增量包
└── guolian/
    ├── test/                   # 国联测试环境
    │   └── project-files/      # 测试机配置相关的源码与配置文件
    └── prd/                    # 国联生产环境（生产配置申请到之前保持为空）
```

- `guolian/test/project-files/` 只放**测试机配置相关的源码与配置文件**，不放编译产物、发布包。
- `guolian/test/` 代表国联测试环境；`guolian/prd/` 代表生产环境，生产配置申请到后再落内容。
- 本机运行目录与测试机发布目录严格分离：本机用 `deploy/local-deploy/`，测试机包只从 `deploy/guolian-deploy/` 产出。

### 0.1 机器与环境角色（术语定义）

本文档及后续任务中的“测试机”，统一指**国联测试环境的部署机**；**生产机**是国联生产环境的机器，尚未申请。

| 角色 | 机器 | 关键信息 | 对应目录 / 配置基线 |
|------|------|----------|---------------------|
| 本机（开发打包机） | `10.110.40.160` | 只做开发、构建、本机运行验证，不是发布对象 | 运行目录 `deploy/local-deploy/`，规约 `spec/LOCAL_RUN_SPEC.md` |
| 测试机（国联测试环境） | `10.64.16.29` | staging `/root/fully-deploy/AIExamK3`、live `/root/AIExamK3`、LB `/data/InforSuiteLB`、DB `10.64.16.14:4886`、OSS `10.10.250.100:8333` | 配置基线 `guolian/test/project-files/`，打包产物 `deploy/guolian-deploy/` |
| 生产机（国联生产环境） | 待申请 | 申请到之前 `guolian/prd/` 保持为空，禁止用测试机配置冒充生产配置 | `guolian/prd/` |

- 历史文档与历史发布包（`*.tar.gz`、`migration.md` 既有记录）里的“目标机”与本文“测试机”是同一台机器（`10.64.16.29`）；自本版起统一称“测试机”。
- “发布到测试机”的任务，配置一律取 `guolian/test/project-files/` 与本文第 5 节，**不得使用本机配置**。
- 生产环境发布流程待 `guolian/prd/` 落地生产配置后另行补充，当前不执行。

## 1. 全量包（full-deploy）

### 1.1 存放路径

```text
/home/cloud/projects/AIExamK3/deploy/guolian-deploy/full-deploy
```

### 1.2 目录结构

```text
full-deploy/
├── source-code/          # 测试机部署所需的源代码（即代码工程）
│   └── code/             # 后端、前端、脚本等源码
└── deploy-files/         # 测试机发布物：全量压缩包 + 部署文本资产
    ├── full-deploy-{yyyyMMdd}-v{x}.tar.gz
    ├── README.md         # 部署说明（打包时复制进压缩包根目录）
    ├── RELEASE.md        # 发布说明（打包时复制进压缩包根目录）
    └── scripts/          # 启停/备份/发布/校验脚本（打包时复制进压缩包根目录）
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
| `deploy-files/README.md` | `deploy-files` | 发布包内 `README.md` 的权威副本，打包时复制进压缩包根目录。 |
| `deploy-files/scripts/` | `deploy-files` | 发布包内 `scripts/` 的权威副本（`start_all.sh`、`stop_all.sh`、`backup.sh`、`deploy_release.sh`、`verify_*.py` 等）。 |
| `deploy-files/RELEASE.md` | `deploy-files` | 发布包内 `RELEASE.md` 的权威副本（staging → live 发布说明），打包时复制进压缩包根目录。 |
| `deploy-files/full-deploy-*.tar.gz` | `deploy-files` | 测试机全量发布压缩包，解压后顶层为 `AIExamK3/`。 |
| `AIExamK3/app/` | 压缩包内 | 后端可执行 jar（`exam-server.jar`）。 |
| `AIExamK3/config/` | 压缩包内 | 测试机环境外置配置（如 `application-polardb.yml`）。 |
| `AIExamK3/data/` | 压缩包内 | 运行数据，如 `minio/exam-files/`（主存储在远端 OSS，通常为空）。 |
| `AIExamK3/docs/` | 压缩包内 | 需求、设计、迁移说明等项目资料。 |
| `AIExamK3/logs/` | 压缩包内 | 预留运行日志目录。 |
| `AIExamK3/middleware/` | 压缩包内 | JDK、MinIO（仅本机兜底）、ffmpeg、mysql-tools 等运行依赖。 |
| `AIExamK3/run/` | 压缩包内 | 预留 PID 目录。 |
| `AIExamK3/scripts/` | 压缩包内 | 启动、停止、备份、发布、迁移、校验等脚本。 |
| `AIExamK3/web/` | 压缩包内 | 三端前端构建产物（`admin/`、`user/`、`h5/`）。 |
| `AIExamK3/README.md` | 压缩包内 | 测试机部署说明。 |
| `AIExamK3/RELEASE.md` | 压缩包内 | 发布说明（staging → live 流程、回滚、LB 处置）。 |

### 1.4 打包要求

- 全量包必须包含**最新编译结果**：后端 `mvn clean package`、前端 `npm run build:target`（三端必须带测试机 base，见 5.7）。
- 包内**不得出现 nginx 配置或 nginx 启动逻辑**：测试机反向代理是 InforSuiteLB。
- 源码以**展开目录**形式存放，不再保留 `code-src.tar.gz`。
- 运行内容必须打成单一压缩包：
  - 命名：`full-deploy-{yyyyMMDD}-v{x}.tar.gz`
  - 版本号规则：每天从 `v1` 开始，同一天每打一次增加 `0.1`。
  - 示例：`full-deploy-20261116-v1.tar.gz`、`full-deploy-20261116-v1.1.tar.gz`
  - 压缩包顶层目录为 `AIExamK3/`，解压到 `/root/fully-deploy` 后即为 staging `/root/fully-deploy/AIExamK3`（见 5.9）。
- `deploy-files/` 目录下只保留压缩包与部署文本资产（`README.md`、`RELEASE.md`、`scripts/`），不再保留未压缩的 `app/`、`config/`、`web/` 等展开目录。
- 包内必须带 `scripts/deploy_release.sh` 与 `RELEASE.md`，发布以脚本为准，不靠手工命令清单。

### 1.5 源码同步要求

**每次发布打包测试机全量包时，必须同步更新并推送远端 `deploy/guolian-deploy/full-deploy/source-code/` 目录。**

具体操作：

1. 将本次构建所使用的源码同步到 `deploy/guolian-deploy/full-deploy/source-code/code/`。
2. 确保 `source-code/` 下不包含 `node_modules/`、`target/`、`dist/` 等编译产物。
3. 将 `source-code/` 的变更与本次发布说明一起提交到 Git，并推送至远端仓库。
4. 远端仓库中的源码版本必须与本次发布的 `deploy-files/` 版本一一对应。

---

## 2. 增量包（incremental-deploy）

### 2.1 存放路径

```text
/home/cloud/projects/AIExamK3/deploy/guolian-deploy/incremental-deploy
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
- 包顶层统一为 `./`（解压到 staging 目录即对应各自相对路径），并包含 `UPDATE.md`；涉及发布流程变化时同时包含 `RELEASE.md`。
- 同一天打了多版时，后一版允许做成**累积增量**（覆盖前一版内容），但必须在 `UPDATE.md` 首行写明“累积基准”是哪个全量包版本。
- 覆盖到 staging（`/root/fully-deploy/AIExamK3`）后再执行 `scripts/deploy_release.sh`，见 5.9。

---

## 3. 源码与二进制管理

- **禁止**将编译产物、依赖目录、部署包压缩文件提交到 Git：
  - `node_modules/`
  - `target/`
  - `dist/`
  - `logs/`、`run/`（运行态目录）
  - `deploy/guolian-deploy/**/*.tar.gz`
  - `deploy/guolian-deploy/full-deploy/deploy-files/data/minio/exam-files/`
  - `deploy/local-deploy/`（本机运行产物，含本机明文配置）
  - `.venv*/`
- Git 仓库保存的内容包括：
  - 源码（根目录 `code/` 以及 `deploy/guolian-deploy/full-deploy/source-code/code/`）
  - 配置、设计/需求文档、打包规约
  - 脚本源码等。
- `deploy/guolian-deploy/full-deploy/source-code/` 必须随每次全量发布同步更新并推送。

---

## 4. 版本号记录

每次打包后，应在 `UPDATE.md` 或规约附录中记录：

- 全量包日期
- 增量包文件名及对应变更内容
- 测试机部署根目录（`/root/AIExamK3`）

---

## 5. 测试机环境配置

> 以下配置来自最新增量包 `deploy-increment-merged-20261013.tar.gz`，是打包与发布时必须对齐的测试机环境。

### 5.1 基础信息

| 项目 | 配置值 |
|------|--------|
| 测试机 IP | `10.64.16.29` |
| staging（全量包解压目录） | `/root/fully-deploy/AIExamK3` |
| live（在运项目目录） | `/root/AIExamK3` |
| 负载均衡根目录 | `/data/InforSuiteLB`（同级的 `/data/rds-install` 是数据库安装目录，**不得改动**） |
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

### 5.4 OSS（S3 兼容对象存储）

> 测试机的文件存储是 **OSS**，不是本机 MinIO。应用仍用 MinIO SDK（S3 协议）访问，配置前缀 `exam.minio.*` 为历史命名，**值指向 OSS 即可，不需要改键名**。

| 项目 | 配置值 |
|------|--------|
| endpoint / public-endpoint | `http://10.10.250.100:8333` |
| 协议 / 区域 | AWS S3 SigV4，`REGION=us-east-1` |
| access-key | `891fef1e206847e1a073d47fcfe8252b` |
| secret-key | `B7E41087CA32E871D71316A7BA44D4FAF283D3152073AC003C2B7C448A67D05E40D198F686555F236ED9FC8D86741564` |
| bucket | `exam-files` |

- 端点、AK/SK、region 已由历史迁移脚本（`import_to_oss.sh`，curl + openssl 手工签 SigV4）核实，与 `config/application-polardb.yml` 一致，**打包时不需要改这些值**。
- 迁移/补传对象时保持对象 key 不变，导入目录形如 `<export_dir>/exam-files/...`；脚本先 `PUT /bucket`（409 视为已存在）再逐个 `PUT` 对象。

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

> **测试机没有 nginx**。三端静态资源与 `/api/` 反代由 **InforSuiteLB** 承载，安装目录 `/data/InforSuiteLB`。

**统一入口 `6003` 单端口**：三端前端 + API 反代全部收敛到一个端口，按路径前缀路由（历史上曾是 6009/6002/6001 三个端口，已废弃）。

| 端口 | 用途 | 路径前缀 | 静态目录 / 上游 |
|------|------|----------|-----------------|
| `6003` | 业务统一入口 | `/mgmt/` | `web/admin`（管理端，原 6009） |
| | | `/learning-web/` | `web/user`（PC 用户端，原 6002） |
| | | `/learning-h5/` | `web/h5`（H5 端，原 6001） |
| | | `/api/` | `127.0.0.1:8088`（后端） |
| `6001` | 兼容入口（保留段） | `/` | `302 /learning-h5/` |
| | | `/guolian-jijian/login` | `alias web/sso/index.html`（门户单点登录，`text/html` + `no-store`） |
| `8088` | 后端 API | `/api/v1` | `app/exam-server.jar` |
| `80` | 中创 LB 管控平台 | `/api`、`/console` | **与业务无关**，不参与发布验证 |

- `6000` 是 Chrome 受限端口（`ERR_UNSAFE_PORT`），不可用作入口。
- 前端构建必须带 base：`npm run build:target`（admin `--base=/mgmt/`、user `--base=/learning-web/`、h5 `--base=/learning-h5/`），路由使用 `createWebHistory(import.meta.env.BASE_URL)`。用默认 `npm run build` 打出的 `/assets/...` 在测试机会白屏。
- 每个 `location` 下 `try_files $uri $uri/ /<base>/index.html` 做 SPA 回退；入口 `index.html` 加 `Cache-Control: no-store`，hash 静态资源长缓存。
- **`web/sso/index.html` 是 LB 直接 alias 的独立页面，不由三端构建产出**。它不属于 Vite 构建物，打全量包时若仓库内没有该目录，必须在 `UPDATE.md` 说明，并由 `deploy_release.sh apply` **保留** live 上已有的 `web/sso/`（`web` 的 `--delete` 同步必须排除 `sso/`），否则 SSO 入口 404。

- 前端构建必须带 base：`npm run build:target`（admin `--base=/mgmt/`、user `--base=/learning-web/`、h5 `--base=/learning-h5/`），路由使用 `createWebHistory(import.meta.env.BASE_URL)`。用默认 `npm run build` 打出的 `/assets/...` 在测试机会白屏。
- LB 主配置：`/data/InforSuiteLB/conf/InforSuiteLB.conf`；站点配置：`/data/InforSuiteLB/conf/discipline-inspection.conf`（历史文件名 `cra.conf`，已改名）。
- 站点配置里的静态目录必须指向 live：`/root/AIExamK3/web/{admin,user,h5}`，不是 staging。
- LB 可执行：`/data/InforSuiteLB/sbin/InforSuiteLB`；测试配置 `-p <LB_HOME> -c <conf> -t`，重载 `-s reload`。
- 增量包只有在**确实下发 LB 配置变更**时才包含 `lb/conf/`，并在 `UPDATE.md` 写明测试与重载命令。
- 对象存储：测试机为 OSS（S3 兼容，`http://10.10.250.100:8333`，bucket `exam-files`）；`middleware/minio`、`data/minio` 只是本机兜底资产，`start_all.sh` 不会在测试机启动本地 MinIO。
- 测试机数据库客户端：`/root/migration/tools/mysql/mysql.sh`，备份优先使用同目录 `bin/mysqldump`。

### 5.8 配置变更说明

- 测试机配置以 `deploy-files/config/application-polardb.yml` 为准。
- 每次发布前必须核对该文件与测试机实际环境是否一致；不一致时先更新配置再打包。
- `source-code/code/exam-server/src/main/resources/application.yml` 和 `source-code/code/config/application-polardb.yml` 必须与 `deploy-files/config/application-polardb.yml` 保持同步。
- **注意**：`source-code/code/exam-server/src/main/resources/application.yml` 是**测试机配置**（MySQL/PolarDB + 目标 Redis/OSS），不是本机开发配置。用 `rsync code/ → source-code/code/` 同步源码后，必须把该文件恢复为测试机版本，禁止把本机 PostgreSQL 配置带进 `source-code/`。

### 5.9 发布流程（staging → live）

测试机采用**双目录模型**：全量包与增量包一律先落到 staging，再由发布脚本同步到 live。

| 路径 | 角色 | 谁能写 |
|------|------|--------|
| `/root/fully-deploy/AIExamK3` | staging：全量包解压内容，增量包覆盖到这里 | 人工解压/覆盖 |
| `/root/AIExamK3` | live：三端与后端实际运行 | 仅 `deploy_release.sh apply` |
| `/data/InforSuiteLB` | 负载均衡 | 仅 `deploy_release.sh lb`（改站点配置名 + reload） |
| `/data/rds-install` | 数据库安装目录 | **不改动** |

标准命令（包内 `scripts/deploy_release.sh`，测试机 root 执行）：

```bash
# 全量包：先解压到 staging
tar -xzf full-deploy-{yyyyMMdd}-v{x}.tar.gz -C /root/fully-deploy
# 增量包：覆盖到 staging
tar -xzf deploy-increment-{yyyyMMdd}-v{x}.tar.gz -C /root/fully-deploy/AIExamK3

bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh check     # 只读检查
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh all --yes # backup→apply→lb→restart→verify
```

脚本约定（修改脚本时必须保持）：

- `apply` 同步 `app`、`docs`、`README.md` 为增量覆盖；`web`、`scripts` 全量同步（`--delete`，用于清掉旧 hash 静态文件与已废弃的 `nginx-*.conf`）。**`web` 的 `--delete` 必须排除 `sso/`**，`web/sso/index.html` 是 LB 直接 alias 的门户单点登录页，包内不产出。
- `apply` **不覆盖** live 的 `config/application-polardb.yml`，不一致时只写 `.new` 并打印 diff。
- `apply` 默认跳过 `middleware/`、`data/`，只有 `--with-runtime` 才同步。
- `lb` 将 `conf/cra.conf` 改名为 `conf/discipline-inspection.conf`；若主配置仍 `include` 旧名则报错退出且**不 reload**。
- `restart` 只停止/启动本项目进程（后端/Redis），不动 LB、数据库与远端 OSS。
- `verify` 按统一入口（默认 `6003`，探测失败回退 `6001`）校验三端 `200 + text/html`、首页引用带对应 base、`/guolian-jijian/login` 返回 `text/html`、`/api/v1/health/db`，通过后才跑 `verify_api.py`。
- 路径可用 `SRC_DIR`、`APP_DIR`、`LB_HOME` 环境变量覆盖；支持 `--dry-run`。

发布验证必须以脚本输出为准，`check` 未打印“检查通过”不得进入 `apply`。
