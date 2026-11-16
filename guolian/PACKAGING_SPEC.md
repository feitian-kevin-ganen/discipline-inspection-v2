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
└── deploy-files/         # 目标机要发布的运行内容
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
| `deploy-files/app/` | `deploy-files` | 后端可执行 jar（`exam-server.jar`）。 |
| `deploy-files/config/` | `deploy-files` | 目标环境外置配置（如 `application-polardb.yml`）。 |
| `deploy-files/data/` | `deploy-files` | 运行数据，如 `minio/exam-files/`。 |
| `deploy-files/docs/` | `deploy-files` | 需求、设计、迁移说明等项目资料。 |
| `deploy-files/logs/` | `deploy-files` | 预留运行日志目录。 |
| `deploy-files/middleware/` | `deploy-files` | JDK、MinIO、ffmpeg、mysql-tools 等运行依赖。 |
| `deploy-files/run/` | `deploy-files` | 预留 PID 目录。 |
| `deploy-files/scripts/` | `deploy-files` | 启动、停止、备份、迁移、校验等脚本。 |
| `deploy-files/web/` | `deploy-files` | 三端前端构建产物（`admin/`、`user/`、`h5/`）。 |
| `deploy-files/README.md` | `deploy-files` | 目标机部署说明。 |

### 1.4 打包要求

- 全量包必须包含**最新编译结果**：后端 `mvn clean package`、前端 `npm run build`。
- 源码以**展开目录**形式存放，不再保留 `code-src.tar.gz`。
- 运行内容以**展开目录**形式存放，不再保留 `binary.tar.gz` 等旧命名。

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
- Git 仓库只保存：源码、配置、设计/需求文档、打包规约、脚本源码等。

---

## 4. 版本号记录

每次打包后，应在 `UPDATE.md` 或规约附录中记录：

- 全量包日期
- 增量包文件名及对应变更内容
- 目标机部署根目录（`/root/discipline-inspection`）
