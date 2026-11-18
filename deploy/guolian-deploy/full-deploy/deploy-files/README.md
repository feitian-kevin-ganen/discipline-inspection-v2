# discipline-inspection 测试机全量部署包

纪检监察考试培训系统（discipline-inspection）。解压到测试机 `/root` 后得到 `/root/AIExamK3`，按下列步骤启动。

测试机信息（国联环境）：

| 项 | 值 |
|----|----|
| 测试机 IP | 10.64.16.29 |
| 部署根目录 | /root/AIExamK3 |
| 数据库 | 10.64.16.14:4886 / exam_db（PolarDB-X） |
| 数据库客户端 | /root/migration/tools/mysql/（mysql.sh、bin/mysqldump） |
| Redis | 10.64.16.29:6379（带密码，见 config/application-polardb.yml） |
| OSS（S3 兼容对象存储） | 当前配置端点 `http://10.10.250.100:8333`，bucket `exam-files`；应用配置前缀沿用 `exam.minio` |
| 反向代理 | InforSuiteLB，安装目录 /data/InforSuiteLB |

## 目录说明

```
/root/AIExamK3
├── app/exam-server.jar              # 后端 jar（含 PolarDB-X/MySQL 驱动）
├── config/application-polardb.yml   # 测试机环境外置配置（DB/Redis/对象存储/AI 等）
├── web/admin/                       # PC 管理端静态产物（base=/mgmt/）
├── web/user/                        # PC 用户端静态产物（base=/learning-web/）
├── web/h5/                          # H5 移动端静态产物（base=/learning-h5/）
├── middleware/
│   ├── jdk21/                       # Temurin JDK 21.0.12
│   ├── minio                        # MinIO 服务端二进制（测试机已改用 OSS，仅作本机兜底）
│   ├── ffmpeg                       # 视频抽帧工具
│   └── mysql-tools/                 # 随包 mysqldump/mysql（测试机无客户端时兜底）
├── data/minio/exam-files/           # 对象存储本地副本（主存储为 OSS 10.10.250.100:8333，通常为空）
├── scripts/
│   ├── start_all.sh                 # 一键启动（Redis/对象存储探测/后端）
│   ├── stop_all.sh                  # 一键停止（保留 LB 与数据库）
│   ├── backup.sh                    # 一键备份（数据库/应用包/对象存储副本/配置）
│   ├── deploy_release.sh            # staging → live 发布脚本（check/backup/apply/lb/restart/verify/all）
│   ├── pg_to_mysql.py               # PostgreSQL → MySQL 数据迁移脚本
│   ├── verify_api.py                # API 端到端校验脚本
│   └── verify_pdf_pages.py          # PDF 页数校验脚本
└── docs/                            # 需求/设计/迁移等项目资料
```

本包不含源码；测试机源码单独维护在发布仓库 `full-deploy/source-code/` 下。

## 前置条件（测试机）

- 麒麟 V10 / CentOS / Ubuntu 等 Linux x86_64
- 后端依赖的外部 MySQL 已就绪：`10.64.16.14:4886` 上的 `exam_db`
- Redis 已就绪：`10.64.16.29:6379`（密码见 `config/application-polardb.yml`）
- OSS 已就绪（当前配置端点 `http://10.10.250.100:8333`，bucket `exam-files`；端点不同时改配置，不改代码）
- **InforSuiteLB 已安装并配置**（不是 Nginx）：`/data/InforSuiteLB`，主配置 `conf/InforSuiteLB.conf`，站点配置 `conf/discipline-inspection.conf`（历史文件名 `cra.conf`，已改名）
- 放行端口：8088（后端直连/内网）、6003（业务统一入口：三端 + `/api/` 按路径前缀路由）、6001（兼容段：`/` 跳 H5、`/guolian-jijian/login` 单点登录）
- `80` 是中创 LB 管控平台（`/console`、`/api` 管控接口），与业务发布无关；`6000` 是 Chrome 受限端口，不可用作入口

测试机的 `/data` 下有且只有两个与发布相关（无关）的目录：

| 路径 | 角色 | 发布时是否改动 |
|------|------|----------------|
| `/data/InforSuiteLB` | 负载均衡安装目录（`sbin/InforSuiteLB`、`conf/InforSuiteLB.conf`、`conf/discipline-inspection.conf`） | 只在站点配置改名后测试 + reload |
| `/data/rds-install` | 数据库安装目录 | **不动** |

## 快速启动

全量包**先解压到 staging，再发布到在运目录**，两个路径不要混用：

| 路径 | 角色 |
|------|------|
| `/root/fully-deploy/AIExamK3` | staging：全量包解压出来的待发布内容（增量包也覆盖到这里） |
| `/root/AIExamK3` | live：三端与后端实际运行的目录 |

```bash
# 1. 解压到 staging（包内顶层目录为 AIExamK3/）
sudo mkdir -p /root/fully-deploy
sudo tar -xzf full-deploy-{yyyyMMdd}-v{x}.tar.gz -C /root/fully-deploy
sudo chmod -R a+rX /root/fully-deploy/AIExamK3/web

# 2. 发布前检查（不改动任何文件）
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh check

# 3. 一键发布：backup → apply → lb → restart → verify
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh all --yes

# 4. 查看状态
bash /root/AIExamK3/scripts/start_all.sh status

# 5. 验证（deploy_release.sh verify 已包含大部分）
bash /root/AIExamK3/scripts/verify_api.py --base http://127.0.0.1:8088/api/v1
curl -sI http://127.0.0.1:6003/mgmt/ | head -1
curl -sI http://127.0.0.1:6003/learning-web/ | head -1
curl -sI http://127.0.0.1:6003/learning-h5/ | head -1
curl -sI http://127.0.0.1:6001/guolian-jijian/login | grep -i content-type   # text/html
```

`deploy_release.sh` 的完整能力见文末“发布流程”一节。

> 首次安装（live 目录还不存在或缺少中间件）时，先手动把 staging 的内容整体拷到 `/root/AIExamK3`：
> `mkdir -p /root/AIExamK3 && cp -a /root/fully-deploy/AIExamK3/. /root/AIExamK3/`
> 之后再走 `deploy_release.sh`；需要连 `middleware/`、`data/` 一起同步时加 `--with-runtime`。

## 对象存储说明（OSS，非本机 MinIO）

测试机的文件存储是 **OSS**（S3 兼容对象存储）。当前目标配置的端点是 `http://10.10.250.100:8333`，bucket `exam-files`；若 OSS 实际端点不同，只改下面两个地址即可。
应用侧仍使用 MinIO SDK（S3 协议）与历史配置前缀 `exam.minio.*`，因此**配置项名不需要改**，只改值即可：

```yaml
exam:
  minio:
    endpoint: http://10.10.250.100:8333
    public-endpoint: http://10.10.250.100:8333   # 预签名 URL 对外地址
    access-key: <OSS AK>
    secret-key: <OSS SK>
    bucket: exam-files
```

`start_all.sh` 只对该 endpoint 做连通性探测，**不会在测试机启动本地 MinIO**；`middleware/minio` 与 `data/minio` 仅作本机兜底，备份时缺失会自动跳过。

## InforSuiteLB 说明（反向代理，非 Nginx）

测试机反向代理为 **InforSuiteLB**，不存在 nginx。三端静态产物必须由 LB 以对应上下文路径暴露，否则会白屏（产物内已固定 base 路径）：

三端与 `/api/` 全部收敛到**统一入口 `6003`** 单端口，按路径前缀路由（历史上 6009/6002/6001 三端口已废弃）：

| 端 | 访问路径 | 静态目录 / 上游 | 监听端口 |
|----|----------|-----------------|----------|
| 管理端 | `/mgmt/` | web/admin | 6003 |
| PC 用户端 | `/learning-web/` | web/user | 6003 |
| H5 端 | `/learning-h5/` | web/h5 | 6003 |
| 后端 API | `/api/` → 127.0.0.1:8088 | app/exam-server.jar | 6003 |
| 门户单点登录 | `/guolian-jijian/login` | `web/sso/index.html` | 6001（兼容段） |
| 兼容跳转 | `/` → 302 `/learning-h5/` | — | 6001（兼容段） |

- 每个 location 需 `try_files $uri $uri/ /<base>/index.html` 做 SPA 回退；入口 html 加 `Cache-Control: no-store`，hash 静态资源长缓存。
- `web/sso/index.html` 由 LB 直接 `alias`，不是三端构建产物，本包内不含；`apply` 会保留在运目录已有的 `web/sso/`（`web` 的 `--delete` 已排除 `sso/`）。
- `80` 端口是中创 LB 管控平台（`/console`、`/api` 管控接口），与业务无关。

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

# 先测试再重载（路径按测试机实际安装位置，默认 /data/InforSuiteLB）
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
- `minio-data.tar.gz`：`data/minio/exam-files` 本地副本（主存储在 OSS，该目录通常不存在，缺失时自动跳过）
- `manifest.txt`：备份清单
- `backup.log`：备份日志

## 访问入口

| 端 | 地址 |
|----|------|
| 管理端 | http://10.64.16.29:6003/mgmt/ |
| PC 用户端 | http://10.64.16.29:6003/learning-web/ |
| H5 端 | http://10.64.16.29:6003/learning-h5/ |
| 门户单点登录 | http://10.64.16.29:6001/guolian-jijian/login |
| 后端 API | http://127.0.0.1:8088/api/v1 |
| 对象存储 OSS | 当前配置端点 http://10.10.250.100:8333（S3 兼容） |

默认账号参见源码 `code/README.md`。

## 发布流程（staging → live）

`scripts/deploy_release.sh` 负责把 staging 的内容发布到 live，并处理 LB 站点配置改名、进程重启与验证。

```bash
S=/root/fully-deploy/AIExamK3/scripts/deploy_release.sh

bash $S check      # 发布前检查：jar/三端 base/配置差异/进程/磁盘/LB 配置，不改动文件
bash $S backup     # 备份：exam_db.sql.gz + app-package.tar.gz + 对象存储本地副本（如存在）+ 配置
bash $S apply      # 同步 app/docs/README 增量，web/scripts 全量（--delete）
bash $S lb         # cra.conf → discipline-inspection.conf，-t 通过后 -s reload
bash $S restart    # stop_all.sh + start_all.sh（只动本项目进程）
bash $S verify     # 三端 200+text/html、首页 base、/api/v1/health/db、verify_api.py
bash $S all --yes  # 以上全部，按序执行
```

选项：

- `--yes` 不交互确认；`--dry-run` 只打印动作不落盘；`--with-runtime` 额外同步 `middleware/`、`data/`
- 路径可用环境变量覆盖：`SRC_DIR`（默认 `/root/fully-deploy/AIExamK3`）、`APP_DIR`（默认 `/root/AIExamK3`）、`LB_HOME`（默认 `/data/InforSuiteLB`）

两条与现场有关的行为：

- **live 配置不被覆盖**：staging 与 live 的 `config/application-polardb.yml` 不一致时只写 `.new` 并打印 diff，确认后手工 `cp` 并重启。
- **废弃 nginx 配置自动清理**：`scripts/` 走 `--delete`，`nginx-discipline-inspection.conf` 会被删掉。

LB 改名后若主配置仍 `include conf/cra.conf;`，`lb` 会报错退出**不重载**；先改 `InforSuiteLB.conf` 的 include，再跑一次 `lb`。

增量包的使用方式相同：先覆盖到 staging，再跑 `deploy_release.sh`。

```bash
tar -xzf deploy-increment-{yyyyMMdd}-v{x}.tar.gz -C /root/fully-deploy/AIExamK3
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh all --yes
```
