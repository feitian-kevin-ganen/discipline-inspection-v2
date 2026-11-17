# 发布说明 deploy-increment-20261117-v1.1

**适用场景**：目标机上已有全量包解压目录，用增量包覆盖后，再发布到在运目录。
**打包日期**：2026-11-17
**包顶层**：`./`（即包内文件名直接对应 `<staging>/` 下的相对路径）
**累积性**：本包是自 `full-deploy-20261117-v1.tar.gz` 起的**累积增量**，无论是否已应用过 `deploy-increment-20261117-v1.tar.gz`，直接覆盖即可。

---

## 一、目标机目录角色（先认清三个路径）

| 路径 | 角色 | 本脚本会不会动它 |
|------|------|------------------|
| `/root/fully-deploy/AIExamK3` | **staging**：全量包解压出来的待发布内容，增量包覆盖到这里 | 只读（脚本从它取文件） |
| `/root/AIExamK3` | **live**：在运目录，三端与后端实际运行的地方 | 读写（同步 + 重启） |
| `/data/InforSuiteLB` | **负载均衡** InforSuiteLB 安装目录 | 只改名站点配置并 reload |
| `/data/rds-install` | 数据库安装目录 | **不动，绝对不要碰** |

> 反向代理是 **InforSuiteLB**，不是 Nginx。目标机上不存在 nginx 配置文件，`scripts/` 里历史遗留的 `nginx-discipline-inspection.conf` 会在同步时被自动清除。

---

## 二、发布步骤（在目标机 root 下执行）

### 1. 把增量包覆盖到 staging

```bash
tar -xzf deploy-increment-20261117-v1.1.tar.gz -C /root/fully-deploy/AIExamK3
```

覆盖后 staging 应包含新增的 `scripts/deploy_release.sh` 和 `RELEASE.md`，可自查：

```bash
ls -l /root/fully-deploy/AIExamK3/scripts/deploy_release.sh
ls -l /root/fully-deploy/AIExamK3/RELEASE.md
```

### 2. 发布前检查（不改动任何文件）

```bash
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh check
```

检查项：staging/live 是否同一目录、jar 是否存在、三端 `index.html` 的 base 是否为 `/mgmt/`、`/learning-web/`、`/learning-h5/`、staging 与 live 的配置差异、进程状态、磁盘余量、LB 站点配置文件名。
**必须看到 `[info] 检查通过` 才能继续**；出现 `[warn]` 先按下文第五节处置。

### 3. 一键发布

```bash
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh all --yes
```

等价于依次执行：

| 子命令 | 做什么 |
|--------|--------|
| `check` | 上面的发布前检查 |
| `backup` | 调 `/root/AIExamK3/scripts/backup.sh`：数据库 `exam_db.sql.gz`、应用包 `app-package.tar.gz`、对象存储本地副本 `minio-data.tar.gz`（如存在）、配置，落到 `/root/AIExamK3-backup/<时间戳>/` |
| `apply` | `app`、`docs`、`README.md` 增量覆盖；`web`、`scripts` 用 `--delete` 全量同步（清掉旧 hash 静态文件与废弃 nginx 配置）；**不覆盖** live 的 `config/application-polardb.yml` |
| `lb` | 站点配置 `cra.conf` → `discipline-inspection.conf` 改名，`-t` 测试通过后 `-s reload` |
| `restart` | `stop_all.sh` 后 `start_all.sh`，只重启本项目后端/Redis（目标机对象存储为远端 OSS，不在本机），LB 与数据库不动 |
| `verify` | 三端入口 200 + `text/html`、首页引用带对应 base、后端 `/api/v1/health/db`，通过后再跑 `verify_api.py` |

也可以分步执行（每一步都会交互确认，加 `--yes` 免确认）：

```bash
S=/root/fully-deploy/AIExamK3/scripts/deploy_release.sh
bash $S backup
bash $S apply
bash $S lb
bash $S restart
bash $S verify
```

只想看会做什么、不落盘：

```bash
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh all --dry-run
```

### 4. 人工复核（脚本无法替代）

```bash
bash /root/AIExamK3/scripts/start_all.sh status
curl -sI http://127.0.0.1:6009/mgmt/ | head -1
curl -sI http://127.0.0.1:6002/learning-web/ | head -1
curl -sI http://127.0.0.1:6001/learning-h5/ | head -1
curl -s  http://127.0.0.1:6009/mgmt/ | grep -oE 'src="[^"]*"' | head -1
```

浏览器从**外部**访问一次（走 LB，不是直连端口）：`http://10.64.16.29:6009/mgmt/`、`http://10.64.16.29:6002/learning-web/`、`http://10.64.16.29:6001/learning-h5/`。

---

## 三、本次发布包里有什么

| 内容 | 说明 |
|------|------|
| `web/admin/`、`web/user/`、`web/h5/` | 三端产物，base 分别为 `/mgmt/`、`/learning-web/`、`/learning-h5/` |
| `scripts/` | `start_all.sh`、`stop_all.sh`、`backup.sh`、`verify_api.py`、`verify_pdf_pages.py`、`pg_to_mysql.py`，以及**新增** `deploy_release.sh` |
| `README.md`、`RELEASE.md` | 全量包说明与本发布说明 |
| `docs/migration.md` | 迁移文档（反向代理表述已改为 InforSuiteLB） |
| `logs/.gitkeep`、`run/.gitkeep` | 目录占位 |
| `UPDATE.md` | 变更清单 |

**不在包内**：`app/exam-server.jar`（本次不变）、`config/application-polardb.yml`（本次不变，且 `apply` 不会覆盖 live 配置）、`middleware/`、`data/`、`db/`（本次无数据库变更）。

---

## 四、两个必须知道的行为

### 1. 配置不覆盖，只生成 `.new`

`apply` 发现 staging 与 live 的 `config/application-polardb.yml` 不一致时：

- 不会覆盖 live 配置，只写 `config/application-polardb.yml.new`
- 并在 `check` 阶段打印 diff

确认要采用新配置时手工执行：

```bash
cp /root/AIExamK3/config/application-polardb.yml.new /root/AIExamK3/config/application-polardb.yml
bash /root/AIExamK3/scripts/stop_all.sh && bash /root/AIExamK3/scripts/start_all.sh
```

### 2. nginx 残留配置自动清除

`scripts/` 走 `--delete` 同步，历史文件 `/root/AIExamK3/scripts/nginx-discipline-inspection.conf` 会被自动删除，无需再手工 `rm -f`。

---

## 五、LB 改名失败怎么办

`lb` 子命令先做改名，再检查主配置里是否仍引用旧文件名：

```bash
cd /data/InforSuiteLB/conf
[ -f cra.conf ] && mv cra.conf discipline-inspection.conf
grep -rn "cra.conf" /data/InforSuiteLB/conf/ || true
```

若 `grep` 仍有命中（通常是 `InforSuiteLB.conf` 里写着 `include conf/cra.conf;`），脚本会**报错退出且不重载**。处置：

```bash
vi /data/InforSuiteLB/conf/InforSuiteLB.conf      # 把 include 改成 discipline-inspection.conf
/data/InforSuiteLB/sbin/InforSuiteLB -p /data/InforSuiteLB -c /data/InforSuiteLB/conf/InforSuiteLB.conf -t
/data/InforSuiteLB/sbin/InforSuiteLB -p /data/InforSuiteLB -c /data/InforSuiteLB/conf/InforSuiteLB.conf -s reload
```

改完再跑一次 `bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh lb` 即可。

站点配置内容（`discipline-inspection.conf`）不在本包内；若三端 404/白屏而进程正常，按下列映射现场核对：

| 端 | 上下文 | 静态根 | 监听端口 |
|----|--------|--------|----------|
| 管理端 | `/mgmt/` | `/root/AIExamK3/web/admin` | 6009 |
| PC 用户端 | `/learning-web/` | `/root/AIExamK3/web/user` | 6002 |
| H5 | `/learning-h5/` | `/root/AIExamK3/web/h5` | 6001 |
| 后端 API | `/api/` → `127.0.0.1:8088` | `app/exam-server.jar` | 8088 |

---

## 六、回滚

```bash
bash /root/AIExamK3/scripts/stop_all.sh

# 备份目录形如 /root/AIExamK3-backup/20261117_153000/
ls -lt /root/AIExamK3-backup/ | head

# 恢复应用包与配置
tar -xzf /root/AIExamK3-backup/<时间戳>/app-package.tar.gz -C /root/AIExamK3
chmod +x /root/AIExamK3/scripts/*.sh
chmod -R a+rX /root/AIExamK3/web

bash /root/AIExamK3/scripts/start_all.sh
```

需要回库时才用 `exam_db.sql.gz`：

```bash
/root/migration/tools/mysql/mysql.sh -h10.64.16.14 -P4886 -uexam_admin -p'z>PD?8Wr_W7h' exam_db < <(zcat /root/AIExamK3-backup/<时间戳>/exam_db.sql.gz)
```

---

## 七、已知事项

- 目标机文件存储是**远端 OSS**（S3 兼容；当前配置端点 `http://10.10.250.100:8333`，bucket `exam-files`），不是本机 MinIO。应用配置前缀仍为 `exam.minio.*`，无需改名；`start_all.sh` 只做连通性探测，不会启动本地 MinIO，`backup.sh` 的 `minio-data.tar.gz` 仅在本地副本存在时生成。
- 本次**无数据库结构变更**，包内无 `db/` 补丁。
- `apply` 会以 staging 的 `scripts/` 全量替换 live 的 `scripts/`。若现场曾手工改过 live 的脚本，先跑 `backup`（`all` 已包含），差异可从备份里取回。
- 目标机若装了 `rsync`，同步走 `rsync -a --delete`；没装则退化为 `cp -a`（`--delete` 场景先清空目录再拷贝），行为一致只是更慢。
- `verify` 里的 `verify_api.py` 依赖后端健康检查通过；后端未就绪时会打印 warn 但不阻断前面三端的结论。
