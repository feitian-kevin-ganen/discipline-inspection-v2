# 发布说明 full-deploy-20261118-v1

**包类型**：测试机全量包（后端 + 三端前端 + 配置 + 中间件 + 数据副本 + 文档 + 脚本）
**打包日期**：2026-11-18
**包大小**：约 1.2 GB
**包顶层**：`AIExamK3/`（解压到 `/root/fully-deploy` 得到 `/root/fully-deploy/AIExamK3`）
**目标机**：10.64.16.29（国联测试环境）

> 本包替代 `full-deploy-20261117-v1.tar.gz`。旧包的后端 jar 只打进了 `exam-common` 一个模块，启动即 `NoClassDefFoundError`，已改名为 `full-deploy-20261117-v1.broken-do-not-use.tar.gz`，**不要再用**。

---

## 一、本包内容与校验点

| 项 | 值 / 校验方法 |
|----|---------------|
| 后端 | `app/exam-server.jar`，112,651,939 字节 |
| jar 内模块 | 9 个：exam-common/exam/exam-integration/exam-learn/exam-practice/exam-question/exam-stat/exam-system/exam-workflow（`unzip -l app/exam-server.jar \| grep BOOT-INF/lib/exam-`） |
| 工作流引擎 | Flowable（jar 内 `BOOT-INF/lib/flowable-*`），表结构 `flowable.database-schema-update=true` 自动建 |
| 管理端 | `web/admin/`，构建 base `/mgmt/`，含 `审批流程`（待办 / 我发起的 / 流程定义）三个页面 |
| 用户端 PC | `web/user/`，base `/learning-web/` |
| H5 端 | `web/h5/`，base `/learning-h5/` |
| 单点登录页 | `web/sso/index.html`（门户带 `?ticket=` 进入，按角色/终端分流） |
| 外置配置 | `config/application-polardb.yml`：DB `10.64.16.14:4886/exam_db`、Redis `10.64.16.29:6379`、OSS `10.10.250.100:8333`（bucket `exam-files`）、AI `10.64.251.169:4000`、公服 `10.20.8.8:32389`（client-id `guolian-jijian`） |
| 文档 | `docs/`，新增 `docs/flow-config.docx`（审批工作流配置与操作手册） |
| 中间件 | `middleware/`：jdk21、ffmpeg、mysql-tools、minio（测试机已用 OSS，minio 仅兜底） |
| 对象存储副本 | `data/minio/exam-files/`（主存储是 OSS，本地副本通常不参与运行） |

自查命令（解压后执行）：

```bash
cd /root/fully-deploy/AIExamK3
ls -l app/exam-server.jar
unzip -l app/exam-server.jar | grep -c 'BOOT-INF/lib/exam-'
unzip -p app/exam-server.jar BOOT-INF/classes/application.yml | head -40
grep -oE '(src|href)="/[^"]+"' web/admin/index.html | head -3
grep -oE '(src|href)="/[^"]+"' web/user/index.html  | head -3
grep -oE '(src|href)="/[^"]+"' web/h5/index.html    | head -3
ls -l docs/flow-config.docx web/sso/index.html
```

---

## 二、三个路径角色（先认清）

| 路径 | 角色 | 发布脚本是否改动 |
|------|------|------------------|
| `/root/fully-deploy/AIExamK3` | **staging**：本包解压出来的待发布内容 | 只读（只从它取文件） |
| `/root/AIExamK3` | **live**：在运目录，实际运行的地方 | 读写（同步 + 重启） |
| `/data/InforSuiteLB` | 负载均衡 InforSuiteLB 安装目录（**不是 Nginx**） | 只校正站点配置文件名并 reload |
| `/data/rds-install` | 数据库安装目录 | **不动** |

---

## 三、发布步骤（测试机 root 下执行）

### 1. 上传并解压到 staging

```bash
cd /root
tar -xzf full-deploy-20261118-v1.tar.gz -C /root/fully-deploy
# 若 /root/fully-deploy 不存在：mkdir -p /root/fully-deploy
ls /root/fully-deploy/AIExamK3
```

解压后顶层应为：`app config data docs logs middleware run scripts web README.md RELEASE.md`。

### 2. 发布前检查（不落盘、不重启）

```bash
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh check
```

必须看到 `[info] 检查通过` 再继续。

### 3. 一键发布

```bash
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh all --yes
```

首次安装（live 目录为空或中间件有升级）时加 `--with-runtime`，一并同步 `middleware/` 与 `data/`：

```bash
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh all --yes --with-runtime
```

`all` = check → backup → apply → lb → restart → verify。

### 4. 分步执行（可选，便于定位问题）

```bash
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh check
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh backup
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh apply
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh lb
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh restart
bash /root/fully-deploy/AIExamK3/scripts/deploy_release.sh verify
```

`--dry-run` 可在任何动作前预演；`backup` 备份数据库、应用包、对象存储本地副本与配置，产物在 `/root/backup/` 下。

### 5. 数据库脚本（按需，本包不含自动执行）

超管账号（手机号 18706197825 授 SUPER_ADMIN）脚本在发布仓库 `guolian/test/project-files/db/grant-super-admin-18706197825.sql`，用测试机自带客户端执行：

```bash
/root/migration/tools/mysql/mysql.sh -h10.64.16.14 -P4886 -uexam_admin -p'<密码>' exam_db \
  -e "source /root/grant-super-admin-18706197825.sql;"
```

执行后脚本会打印该账号的 `id/job_no/phone/emp_id/role_ids`。若查询为空，说明该手机号在 `t_org_user` 中不存在或未绑定 `emp_id`——门户单点登录按 `emp_id` 匹配账号，需先建号或补绑。

---

## 四、发布后验证

统一入口（InforSuiteLB，按路径前缀路由）：

| 入口 | 地址 |
|------|------|
| 管理端 | `http://10.64.16.29:6003/mgmt/` |
| 用户端 PC | `http://10.64.16.29:6003/learning-web/` |
| H5 端 | `http://10.64.16.29:6003/learning-h5/` |
| 门户单点登录 | `http://10.64.16.29:6003/guolian-jijian/login?ticket=<票据>` |
| 后端直连 | `http://10.64.16.29:8088/api/v1/...` |

快速验证：

```bash
curl -sI http://127.0.0.1:6003/mgmt/ | head -3
curl -sI http://127.0.0.1:6003/learning-web/ | head -3
curl -sI http://127.0.0.1:6003/learning-h5/ | head -3
curl -s  http://127.0.0.1:8088/api/v1/health/db
```

脚本自带 `verify` 会覆盖上述检查。前端发版后若页面仍是旧界面，是浏览器缓存旧 JS：入口 html 已加 `Cache-Control: no-store`，强制刷新（Ctrl+F5）即可。

---

## 五、回滚

`backup` 步骤的产物（`/root/backup/<时间戳>/`）包含上一版应用包、配置与数据库导出：

```bash
bash /root/AIExamK3/scripts/stop_all.sh
# 用备份中的应用包与配置覆盖 /root/AIExamK3 后重启
bash /root/AIExamK3/scripts/start_all.sh
```

数据库回滚用备份的 dump 导入，导入前确认业务已停止写入。

---

## 六、已知事项

- 反向代理是 **InforSuiteLB**，不是 Nginx；测试机上没有 nginx 配置。站点配置在 `/data/InforSuiteLB/conf/discipline-inspection.conf`（历史文件名 `cra.conf` 已由 `lb` 步骤自动改名并校验）。
- 6000 是 Chrome 受限端口，不可用作入口；80 属于中创 LB 管控平台，与业务无关。
- 对象存储是 OSS（S3 兼容），配置项前缀仍沿用 `exam.minio.*`，只改端点/密钥，不改代码。
- `data/minio/exam-files/` 是本地副本，OSS 已迁移完成，正常运行不依赖它。
