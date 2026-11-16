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

### 1.3 应用配置

配置文件来源：`code/exam-server/src/main/resources/application.yml`

| 项目 | 配置值 |
|------|--------|
| 后端端口 | `8088` |
| 数据库驱动 | `org.postgresql.Driver` |
| 数据库 URL | `jdbc:postgresql://127.0.0.1:5434/exam_db?stringtype=unspecified` |
| 数据库账号 | `postgres / postgres` |
| Redis | `127.0.0.1:6379` |
| MinIO endpoint | `http://127.0.0.1:9000` |
| MinIO public-endpoint | `http://10.110.40.160:9000` |
| MinIO 账号 | `minioadmin / minioadmin` |
| ffmpeg 路径 | `/home/cloud/AIExamK3/middleware/ffmpeg`（发布后路径） |

> 若本机 IP 发生变化，打包前必须同步更新 `application.yml` 中的 `public-endpoint`。

---

## 2. 本机包结构

存放路径：

```text
/home/cloud/projects/AIExamK3/guolian/deploy-history/local-deploy
```

目录结构：

```text
local-deploy/
├── source-code/          # 本机运行所需的源码
│   └── code/
└── deploy-files/         # 本机要发布的运行内容
    ├── app/
    ├── config/
    ├── docs/
    ├── middleware/
    │   └── ffmpeg
    ├── README.md
    ├── scripts/
    └── web/
```

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
2. `source-code/`：同步 `code/` 源码（剔除 `node_modules`、`target`、`dist`、`logs`、`run`、`.venv`）。
3. `deploy-files/`：
   - `app/exam-server.jar`
   - `config/application.yml`（本机 PostgreSQL 配置）
   - `web/admin/`、`web/user/`、`web/h5/`
   - `middleware/ffmpeg`
   - `scripts/`（本地启停脚本，不含目标机专用脚本）
   - `docs/`（项目资料，可选）
   - `README.md`

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
sudo mkdir -p /home/cloud/AIExamK3
sudo tar -xzf local-deploy-YYYYMMDD-vX.tar.gz -C /home/cloud/AIExamK3 --strip-components=1
sudo chmod -R a+rX /home/cloud/AIExamK3/web
```

### 4.2 启动

```bash
cd /home/cloud/AIExamK3
bash scripts/start_all.sh
```

`start_all.sh` 至少完成：

1. 启动后端 jar（端口 8088）。
2. 以静态方式启动三端前端（端口 6009/6002/6001），可用 `python3 -m http.server` 或 Node 静态服务。

### 4.3 验证

```bash
bash scripts/verify_api.py --base http://127.0.0.1:8088/api/v1
```

---

## 5. 注意事项

- 本机包**不携带数据库、Redis、MinIO、JDK**，依赖本地 PaaS 环境。
- 发布前确认 PostgreSQL `exam_db`、Redis `6379`、MinIO `9000` 均已可用。
- 若本机 IP 变化，须同步修改 `config/application.yml` 中的 `minio.public-endpoint`。
- 本机包与目标机包**严禁混用配置**，打包前须按本规约核对。
