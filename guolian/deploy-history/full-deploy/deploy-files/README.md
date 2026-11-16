# AIExamK3 目标机部署包

本包参考 `migration.md` 中的 `/root/AIExamK3` 全量发布包结构制作，解压到目标机 `/root/AIExamK3` 后即可按下列步骤启动。

## 目录说明

```
/root/AIExamK3
├── app/exam-server.jar              # 后端可执行 jar（已包含 PolarDB-X/MySQL 驱动）
├── config/application-polardb.yml   # 目标环境外置配置
├── web/admin/                       # PC 管理端静态产物（Nginx 6009）
├── web/user/                        # PC 用户端静态产物（Nginx 6002）
├── web/h5/                          # H5 移动端静态产物（Nginx 6001）
├── middleware/
│   ├── jdk21/                       # Temurin JDK 21.0.12
│   ├── minio                        # MinIO 服务端二进制
│   ├── ffmpeg                       # 视频抽帧工具
│   └── mysql-tools/                 # 随包 mysqldump/mysql（目标机无 MySQL 客户端时使用）
├── data/minio/exam-files/           # MinIO 对象存储数据
├── scripts/
│   ├── start_all.sh                 # 一键启动
│   ├── stop_all.sh                  # 一键停止（保留 nginx/polardb）
│   ├── backup.sh                    # 一键备份（数据库/应用包/MinIO 数据）
│   ├── nginx-discipline-inspection.conf               # Nginx 站点配置
│   ├── pg_to_mysql.py               # PostgreSQL → PolarDB-X 迁移脚本
│   ├── verify_api.py                # API 端到端校验脚本
│   └── verify_pdf_pages.py          # PDF 页数校验脚本
├── code-src.tar.gz                  # 全量源码（不含 node_modules/target/dist）
└── docs/                            # 需求/设计等项目资料
```

## 前置条件（目标机）

- 麒麟 V10 / CentOS / Ubuntu 等 Linux x86_64
- 已安装 Docker，并已用 PXD 部署 PolarDB-X 单节点（端口 14397，账号 exam / Exam@163db）
- 已安装 Nginx 1.18+，并已将 `scripts/nginx-discipline-inspection.conf` 复制到 `/etc/nginx/conf.d/`
- 已放行防火墙端口：8088、6001、6002、6009、9000、9001、14397

## 快速启动

```bash
# 1. 解压到 /root/AIExamK3（如已解压可跳过）
sudo mkdir -p /root/AIExamK3
sudo tar -xzf discipline-inspection-guolian.tar.gz -C /root/AIExamK3 --strip-components=1
sudo chmod 755 /root /root/AIExamK3
sudo chmod -R a+rX /root/AIExamK3/web

# 2. 复制 nginx 配置并启动 nginx
sudo cp /root/AIExamK3/scripts/nginx-discipline-inspection.conf /etc/nginx/conf.d/discipline-inspection.conf
sudo nginx -s reload   # 或 sudo systemctl start nginx

# 3. 启动后端、Redis、MinIO
bash /root/AIExamK3/scripts/start_all.sh

# 4. 验证
bash /root/AIExamK3/scripts/verify_api.py --base http://127.0.0.1:8088/api/v1
```

## 备份

包内已随包携带 `mysqldump` / `mysql` 工具（`middleware/mysql-tools/`），目标机没有 MySQL 客户端也能直接备份。

```bash
# 默认备份到 /root/AIExamK3-backup/<时间戳>/，保留最近 5 份
bash /root/AIExamK3/scripts/backup.sh

# 自定义备份目录和保留份数
bash /root/AIExamK3/scripts/backup.sh /data/backups 7
```

每次备份会生成：

- `exam_db.sql.gz`：数据库 `exam_db` 的 SQL 导出
- `app-package.tar.gz`：应用包（`app/`、`config/`、`web/`、`scripts/`、`middleware/`、`README.md`）
- `minio-data.tar.gz`：`data/minio/exam-files` 对象存储数据
- `manifest.txt`：备份清单
- `backup.log`：备份日志

## 访问入口

| 端 | 地址 |
|----|------|
| 管理端 | http://<目标机IP>:6009 |
| 用户端 | http://<目标机IP>:6002 |
| H5 端 | http://<目标机IP>:6001 |
| 后端 API | http://<目标机IP>:8088 |
| MinIO 控制台 | http://<目标机IP>:9001 |

默认账号参见源码 `code/README.md`。
