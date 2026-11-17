# discipline-inspection 目标机全量部署包

纪检监察考试培训系统（discipline-inspection）。解压到目标机 `/root` 后得到 `/root/AIExamK3`，按下列步骤启动。

目标机信息（国联环境）：

| 项 | 值 |
|----|----|
| 目标机 IP | 10.64.16.29 |
| 部署根目录 | /root/AIExamK3 |
| 数据库 | 10.64.16.14:4886 / exam_db（PolarDB-X） |
| 数据库客户端 | /root/migration/tools/mysql/（mysql.sh、bin/mysqldump） |
| Redis | 10.64.16.29:6379（带密码，见 config/application-polardb.yml） |
| MinIO（对象存储） | http://10.10.250.100:8333，bucket `exam-files` |
| 反向代理 | InforSuiteLB，安装目录 /data/InforSuiteLB |

## 目录说明

```
/root/AIExamK3
├── app/exam-server.jar              # 后端 jar（含 PolarDB-X/MySQL 驱动）
├── config/application-polardb.yml   # 目标环境外置配置（DB/Redis/MinIO/AI 等）
├── web/admin/                       # PC 管理端静态产物（base=/mgmt/）
├── web/user/                        # PC 用户端静态产物（base=/learning-web/）
├── web/h5/                          # H5 移动端静态产物（base=/learning-h5/）
├── middleware/
│   ├── jdk21/                       # Temurin JDK 21.0.12
│   ├── minio                        # MinIO 服务端二进制（本机兜底时使用）
│   ├── ffmpeg                       # 视频抽帧工具
│   └── mysql-tools/                 # 随包 mysqldump/mysql（目标机无客户端时兜底）
├── data/minio/exam-files/           # MinIO 对象数据（本地副本/兜底，主存储为远端 10.10.250.100）
├── scripts/
│   ├── start_all.sh                 # 一键启动（Redis/MinIO/后端/LB）
│   ├── stop_all.sh                  # 一键停止（保留 LB 与数据库）
│   ├── backup.sh                    # 一键备份（数据库/应用包/MinIO 数据/配置）
│   ├── pg_to_mysql.py               # PostgreSQL → MySQL 数据迁移脚本
│   ├── verify_api.py                # API 端到端校验脚本
│   └── verify_pdf_pages.py          # PDF 页数校验脚本
└── docs/                            # 需求/设计/迁移等项目资料
```

本包不含源码；目标机源码单独维护在发布仓库 `full-deploy/source-code/` 下。

## 前置条件（目标机）

- 麒麟 V10 / CentOS / Ubuntu 等 Linux x86_64
- 后端依赖的外部 MySQL 已就绪：`10.64.16.14:4886` 上的 `exam_db`
- Redis 已就绪：`10.64.16.29:6379`（密码见 `config/application-polardb.yml`）
- MinIO 已就绪：`http://10.10.250.100:8333`，bucket `exam-files`
- **InforSuiteLB 已安装并配置**（不是 Nginx）：`/data/InforSuiteLB`，主配置 `conf/InforSuiteLB.conf`，站点配置 `conf/discipline-inspection.conf`（历史文件名 `cra.conf`，已改名）
- 放行端口：8088（后端）、6001（H5）、6002（PC 用户端）、6009（管理端）

## 快速启动

```bash
# 1. 解压到 /root（包内顶层目录为 AIExamK3/，解压后即 /root/AIExamK3）
sudo tar -xzf full-deploy-{yyyyMMdd}-v{x}.tar.gz -C /root
sudo chmod 755 /root /root/AIExamK3
sudo chmod -R a+rX /root/AIExamK3/web

# 2. 启动后端（Redis/MinIO 按其配置地址探测：远端已可用则跳过本机启动）
bash /root/AIExamK3/scripts/start_all.sh

# 3. 查看状态
bash /root/AIExamK3/scripts/start_all.sh status

# 4. 验证
bash /root/AIExamK3/scripts/verify_api.py --base http://127.0.0.1:8088/api/v1
curl -sI http://127.0.0.1:6009/mgmt/ | head -1
curl -sI http://127.0.0.1:6002/learning-web/ | head -1
curl -sI http://127.0.0.1:6001/learning-h5/ | head -1
```

## InforSuiteLB 说明（反向代理，非 Nginx）

目标机反向代理为 **InforSuiteLB**，不存在 nginx。三端静态产物必须由 LB 以对应上下文路径暴露，否则会白屏（产物内已固定 base 路径）：

| 端 | 访问路径 | 静态目录 | 监听端口 |
|----|----------|----------|----------|
| 管理端 | `/mgmt/` | web/admin | 6009 |
| PC 用户端 | `/learning-web/` | web/user | 6002 |
| H5 端 | `/learning-h5/` | web/h5 | 6001 |
| 后端 API | `/api/` → 127.0.0.1:8088 | app/exam-server.jar | 8088 |

本包**不包含 LB 配置变更**。若站点配置文件仍是历史名称 `cra.conf`，按下列步骤改名（配置内容已切换为本系统，无需改内容）：

```bash
cd /data/InforSuiteLB/conf
mv cra.conf discipline-inspection.conf
# 主配置若有 include cra.conf，同步改为 include discipline-inspection.conf
grep -rn "cra.conf" /data/InforSuiteLB/conf/ || true

# 先测试再重载
/data/InforSuiteLB/sbin/InforSuiteLB -p /data/InforSuiteLB -c /data/InforSuiteLB/conf/InforSuiteLB.conf -t
/data/InforSuiteLB/sbin/InforSuiteLB -p /data/InforSuiteLB -c /data/InforSuiteLB/conf/InforSuiteLB.conf -s reload
```

若需调整站点的上下文映射 / 静态根目录 / 反代规则：

```bash
# 站点的上下文映射 / 静态根目录 / 反代规则位于
vi /data/InforSuiteLB/conf/discipline-inspection.conf

# 先测试再重载（路径按目标机实际安装位置，默认 /data/InforSuiteLB）
/data/InforSuiteLB/sbin/InforSuiteLB -p /data/InforSuiteLB -c /data/InforSuiteLB/conf/InforSuiteLB.conf -t
/data/InforSuiteLB/sbin/InforSuiteLB -p /data/InforSuiteLB -c /data/InforSuiteLB/conf/InforSuiteLB.conf -s reload

# 或使用本包脚本
bash /root/AIExamK3/scripts/start_all.sh reload-lb
```

## 备份

```bash
# 默认备份到 /root/AIExamK3-backup/<时间戳>/，保留最近 5 份
bash /root/AIExamK3/scripts/backup.sh

# 自定义备份目录和保留份数
bash /root/AIExamK3/scripts/backup.sh /data/backups 7
```

备份工具发现顺序：`${MYSQL_TOOLS_DIR:-/root/migration/tools/mysql}/bin/mysqldump` → 系统 `mysqldump` → 随包 `middleware/mysql-tools/bin/mysqldump`；都找不到时报错退出。

每次备份生成：

- `exam_db.sql.gz`：数据库 `exam_db` 的 SQL 导出
- `app-package.tar.gz`：应用包（`app/`、`config/`、`web/`、`scripts/`、`middleware/`、`README.md`）
- `minio-data.tar.gz`：`data/minio/exam-files` 本地对象数据
- `manifest.txt`：备份清单
- `backup.log`：备份日志

## 访问入口

| 端 | 地址 |
|----|------|
| 管理端 | http://10.64.16.29:6009/mgmt/ |
| PC 用户端 | http://10.64.16.29:6002/learning-web/ |
| H5 端 | http://10.64.16.29:6001/learning-h5/ |
| 后端 API | http://127.0.0.1:8088/api/v1 |
| MinIO 控制台 | http://10.10.250.100:8333（远端对象存储） |

默认账号参见源码 `code/README.md`。
