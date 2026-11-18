# 系统迁移说明书：AIExamK3 → 10.110.10.163（麒麟 V10 + PolarDB-X）

## 0. 迁移概览

| 项目 | 源环境（10.110.10.160） | 目标环境（10.110.10.163） |
|------|------------------------|--------------------------|
| 操作系统 | Ubuntu | 麒麟 Kylin V10 (Tercel) x86_64 |
| 数据库 | PostgreSQL（本地 PaaS，端口 5434） | PolarDB-X 标准版 `8.0.32-X-Cluster-8.4.19-20250825`（MySQL 8.0.32 协议兼容，Docker 部署，端口 14397） |
| 缓存 | Redis（PaaS，6379） | Redis 4.0.11（系统自带二进制，6379） |
| 对象存储 | MinIO（PaaS，9000/9001） | MinIO RELEASE.2025-07-23（9000/9001） |
| JDK | OpenJDK 17 | Temurin JDK 21.0.12（随包部署） |
| 后端 | exam-server.jar（8088） | 同 jar，profile=polardb（8088） |
| 前端 | vite dev（6009/6002/6001） | Nginx 1.18 托管 dist（6009/6002/6001） |
| 部署根目录 | /home/cloud/projects/AIExamK3 | /root/CRA |

> **关于 PolarDB 版本**：需求指定 `Polardb for Mysql 8.0.32-X-Cluster-8.4.20`。经核实，`8.4.20` 仅为阿里云商业版（PolarDB-X 企业版云服务）的内核版本，开源渠道不提供下载；开源可获取的最新版本为 **PolarDB-X v2.4.2（DN 引擎 8.4.19）**，`SELECT version()` 返回 `8.0.32-X-Cluster-8.4.19-20250825`，与 8.4.20 同属 8.0.32 协议线，完全兼容。本次按开源最新版部署。

## 1. 测试机勘察结论

```
OS:      Kylin Linux Advanced Server V10 (Tercel), kernel 4.19.90, x86_64
资源:    8C / 15GB RAM / 430GB 可用磁盘
已有:    java 1.8（不可用，Spring Boot 3 需 17+）、redis-server 4.0.11、nginx 1.18、
         docker 18.09.0（overlay2，active）、python3.7 + pip18、mariadb 客户端 10.3.9
缺失:    mysqld、minio、node
网络:    可访问互联网（阿里云镜像/官方站可达，Docker Hub 不可达）
```

## 2. 后端双库兼容改造（代码侧）

目标数据库从 PostgreSQL 换成 PolarDB-X（MySQL 协议），共 4 处适配，改造后**同一套代码同时兼容 PG 与 PolarDB-X**：

1. **`code/exam-common/pom.xml`**：新增 `mysql-connector-j` 依赖（Spring Boot 3.3.13 托管版本 8.3.0），保留 postgresql 驱动，两驱动共存。
2. **`MybatisPlusConfig.java`**：分页插件由固定 `DbType.POSTGRE_SQL` 改为不指定 DbType（按连接自动识别方言）。
3. **`AbstractJsonbTypeHandler.java`**：JSON 字段写入由 `ps.setObject(json, Types.OTHER)`（PG 专有）改为 `ps.setString(json)`；PG 侧在连接串加 `stringtype=unspecified` 保持兼容，MySQL/PolarDB-X 的 JSON/LONGTEXT 列直接接受字符串。
4. **`HealthController.java`**：探针 SQL 去掉 PG 专有的 `inet_server_addr()::text`，改为按 `version()` 判断方言的通用写法。

PG 专有 SQL 扫描结论：全项目仅上述几处，无 ILIKE / jsonb 函数 / string_agg 等其它 PG 专属用法；`now()`、`COALESCE` 等均为两边通用。

新增目标环境配置 `config/application-polardb.yml`（随包外置，不进 jar，便于改端口/IP）：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:14397/exam_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&zeroDateTimeBehavior=convertToNull
    username: exam
    password: Exam@163db
exam:
  minio:
    endpoint: http://127.0.0.1:9000
    public-endpoint: http://10.110.10.163:9000
  ffmpeg-path: /root/CRA/middleware/ffmpeg
```

启动命令（外置配置 + profile）：

```bash
/root/CRA/middleware/jdk21/bin/java -jar /root/CRA/app/exam-server.jar \
  --spring.profiles.active=polardb \
  --spring.config.additional-location=file:/root/CRA/config/
```

## 3. PolarDB-X 安装（测试机）

官方途径为 PXD 工具（pip 包），经两处适配后在麒麟 V10 成功部署：

```bash
# 1. PXD 安装（系统 python3.7 + pip18 过旧，先建 venv 升级 pip）
python3 -m venv /root/CRA/venv-pxd
/root/CRA/venv-pxd/bin/pip install --upgrade "pip<24" -i https://mirrors.aliyun.com/pypi/simple/
/root/CRA/venv-pxd/bin/pip install pxd -i https://mirrors.aliyun.com/pypi/simple/
export LC_ALL=en_US.utf-8 LANG=en_US.utf-8   # 否则 pxd 报 locale 错误
pxd version    # 0.7.1

# 2. 适配点 A：PXD 默认从 Docker Hub 拉镜像（目标网络不可达），
#    改用阿里云开源镜像仓库；且仓库内 DN 镜像名为 polardbx-engine（非 PXD 硬编码的 polardbx-engine-2.0）
sed -i 's/DN_IMAGE_NAME = "polardbx-engine-2.0"/DN_IMAGE_NAME = "polardbx-engine"/' \
  /root/CRA/venv-pxd/lib64/python3.7/site-packages/deployer/config/config.py

# 3. 部署标准版单节点（DN only，即 MySQL 协议服务端）
/root/CRA/venv-pxd/bin/pxd tryout -t standard \
  -dn_version v2.4.2_8.4.19 \
  -repo "polardbx-opensource-registry.cn-beijing.cr.aliyuncs.com/polardbx/"
# 输出：mysql -h127.0.0.1 -P14397 -uadmin -p<随机密码>
```

**适配点 B**：PXD 生成的 admin 账号无建库/授权权限（已知社区 issue #230），root 账号使用 caching_sha2_password，麒麟自带的 MariaDB 10.3 客户端不支持该插件，需进容器用引擎自带客户端操作：

```bash
docker exec pxc-tryout-dn-0-Cand-14397 /u01/xcluster80/bin/mysql -h127.0.0.1 -P14397 -uroot -e "
CREATE DATABASE IF NOT EXISTS exam_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER USER \"exam\"@\"%\" IDENTIFIED WITH mysql_native_password BY \"Exam@163db\";
GRANT ALL PRIVILEGES ON exam_db.* TO \"exam\"@\"%\";
CREATE USER IF NOT EXISTS \"exam\"@\"localhost\" IDENTIFIED WITH mysql_native_password BY \"Exam@163db\";
GRANT ALL PRIVILEGES ON exam_db.* TO \"exam\"@\"localhost\";
FLUSH PRIVILEGES;"
```

容器已设自启：`docker update --restart unless-stopped pxc-tryout-dn-0-Cand-14397`。

> 应用账号统一使用 `mysql_native_password`，保证老客户端/驱动兼容。

## 4. 数据迁移（PostgreSQL → PolarDB-X）

迁移脚本：`code/scripts/migration/pg_to_mysql.py`（已随包放在 `/root/CRA/scripts/`）。

原理：通过 `information_schema` 内省 PG 全部表结构，自动映射生成 MySQL DDL 并逐表复制数据：

| PostgreSQL | PolarDB-X / MySQL |
|------------|-------------------|
| bigserial / serial | BIGINT AUTO_INCREMENT 主键（导入后重置 AUTO_INCREMENT = max+1） |
| jsonb / json | LONGTEXT（应用层 TypeHandler 按字符串读写，避免 MySQL JSON 规范化改写语义） |
| timestamptz / timestamp | DATETIME(6)（保留微秒，去时区） |
| boolean | TINYINT(1) |
| text | LONGTEXT（内容字段可能超 64KB） |
| numeric(p,s) | DECIMAL(p,s) |
| 唯一约束 | 保留；普通二级索引因数据量小不迁移 |

执行（从源机直接连目标库，无需在测试机装 Python 依赖）：

```bash
python3 pg_to_mysql.py \
  --pg-host 127.0.0.1 --pg-port 5434 --pg-db exam_db --pg-user postgres --pg-pass postgres \
  --my-host 10.110.10.163 --my-port 14397 --my-db exam_db --my-user exam --my-pass 'Exam@163db'
```

结果：**27 张表全部迁移，行数逐表校验一致**（t_org_user 22 行、t_ques_item 616 行、t_sys_notice 1116 行等）。

**默认值补迁**：首轮迁移不带 PG 侧列默认值，导致登录日志写入报 `Field 'login_time' doesn't have a default value`。补迁规则：`now()`/`CURRENT_TIMESTAMP` → `DEFAULT CURRENT_TIMESTAMP(6)`，数值/布尔字面量原样迁移（共处理 137 个）；`t_sys_config.config_group`、`t_sys_role.permissions` 两列为 text/jsonb 带默认值，MySQL 不允许 TEXT 类默认值，改为可空列。

## 5. Redis 迁移

测试机已有 redis-server 4.0.11，直接以独立配置运行：

```bash
nohup /usr/bin/redis-server --port 6379 --bind 127.0.0.1 \
  --dir /root/CRA/data/redis --appendonly yes --daemonize no > /root/CRA/logs/redis.log 2>&1 &
```

数据说明：源 Redis 中仅有刷新令牌（`exam:refresh:*`）与学习心跳幂等键（`learn:hb:idem:*`）等**瞬态数据**，无业务持久数据，不做迁移；迁移后用户重新登录即可。

## 6. MinIO 迁移

1. 二进制：源机 `/home/cloud/projects/PaaS/MinIO/minio`（RELEASE.2025-07-23，静态编译）随包拷贝至 `/root/CRA/middleware/minio`。
2. 数据：整个 bucket 目录 `data/exam-files`（855MB，含全部学习视频/封面/PDF/页图）文件级拷贝至 `/root/CRA/data/minio/exam-files`。MinIO 数据目录为自描述结构，文件级拷贝即可完整恢复。
3. 启动：

```bash
MINIO_ROOT_USER=minioadmin MINIO_ROOT_PASSWORD=minioadmin \
nohup /root/CRA/middleware/minio server /root/CRA/data/minio \
  --address :9000 --console-address :9001 > /root/CRA/logs/minio.log 2>&1 &
```

应用侧视频/封面/PDF 均走后端同源代理，前端不直接接触 MinIO 地址，故 MinIO 只监听回源即可。

## 7. 前端部署（Nginx）

三端在源机构建产物（`npm run build`），拷贝至 `/root/CRA/web/{admin,user,h5}`，由测试机自带 nginx 1.18 托管。配置 `/etc/nginx/conf.d/cra.conf`（随包 `/root/CRA/scripts/nginx-cra.conf`）：每个端口一个 server 块，`/api/` 反代 `127.0.0.1:8088`，`try_files ... /index.html` 支持 SPA 路由，`client_max_body_size 500m` 支持视频上传。

**适配点 C**：`/root` 目录默认 `dr-xr-x---`，nginx worker（nginx 用户）无权读取，报 `stat() failed (13: Permission denied)` 并引发重写循环（500）。处理：`chmod 755 /root /root/CRA && chmod -R a+rX /root/CRA/web`（麒麟 V10 SELinux 为 Disabled，无需额外上下文处理）。

> 管理端使用 6009 而非任务书最初写的 6000：6000 属 Chrome 浏览器受限端口（ERR_UNSAFE_PORT），主流浏览器无法访问。

## 8. 防火墙（适配点 D）

麒麟 V10 默认 firewalld 运行中，docker 发布端口走 iptables 不受影响，但 8088/600x/900x 需显式放行：

```bash
firewall-cmd --permanent --add-port=8088/tcp --add-port=6001/tcp --add-port=6002/tcp \
  --add-port=6009/tcp --add-port=9000/tcp --add-port=9001/tcp --add-port=14397/tcp
firewall-cmd --reload
```

## 9. 部署包清单与目录结构

部署包 `CRA-stage.tar.gz`（1.2GB，保留在 `/root/CRA/` 作为备份），解包后结构：

```
/root/CRA
├── app/exam-server.jar          # 后端（含 mysql-connector-j，双库兼容）
├── config/application-polardb.yml  # 目标环境外置配置
├── web/{admin,user,h5}/         # 三端前端构建产物
├── middleware/
│   ├── jdk21/                   # Temurin JDK 21.0.12（346MB）
│   ├── minio                    # MinIO 服务端二进制
│   └── ffmpeg                   # ffmpeg 7.0.2 静态版（视频封面抽帧）
├── data/minio/exam-files/       # 对象存储数据（855MB，含视频/封面/PDF）
├── scripts/
│   ├── start_all.sh / stop_all.sh   # 一键启停
│   ├── nginx-cra.conf               # nginx 站点配置（已复制到 /etc/nginx/conf.d/）
│   ├── pg_to_mysql.py               # PG→PolarDB 迁移脚本
│   └── verify_api.py                # 64 项 API 端到端校验
├── code-src.tar.gz              # 全量源代码（剔除 node_modules/target/dist）
├── docs/                        # 需求/设计/成本/PPT/题库等项目资料
└── logs/ run/                   # 运行日志与 pid
```

启停：

```bash
bash /root/CRA/scripts/start_all.sh   # 启动 PolarDB 容器、Redis、MinIO、后端、Nginx
bash /root/CRA/scripts/stop_all.sh    # 停止后端/MinIO/Redis（nginx 与 PolarDB 容器默认保持）
```

已设置开机自启：nginx（systemctl enable）、docker 容器（--restart unless-stopped）；后端/MinIO/Redis 如需开机自启，可将 start_all.sh 加入 rc.local 或注册 systemd unit。

## 10. 验证结果

- 数据库：`SELECT version()` → `8.0.32-X-Cluster-8.4.19-20250825`；27 表行数与源库一致
- 后端探针：`GET /api/v1/health/db` 返回 PolarDB 版本、22 个用户
- API 回归：`verify_api.py --base http://10.110.10.163:8088/api/v1` → **64/64 全部通过**
- 浏览器无头实测：
  - 管理端 `http://10.110.10.163:6009` admin 登录成功，工作台正常
  - 用户端 `http://10.110.10.163:6002` ks001 登录、学习中心 12/12 封面渲染、视频可播放（readyState=4，时长 527s）
  - H5 端 `http://10.110.10.163:6001` 登录、学习列表封面/进度正常
- 无浏览器页面报错

## 11. 目标环境访问入口与账号

| 端 | 地址 | 账号 |
|----|------|------|
| 管理端 | http://10.110.10.163:6009 | admin / Admin@12345 |
| 用户端 | http://10.110.10.163:6002 | ks001 / Exam@123 |
| H5 端 | http://10.110.10.163:6001 | ks001 / Exam@123 |
| 后端 API | http://10.110.10.163:8088 | — |
| MinIO 控制台 | http://10.110.10.163:9001 | minioadmin / minioadmin |
| PolarDB-X | 10.110.10.163:14397 | exam / Exam@163db（应用）；admin / yGFdqUUQ（PXD 管理账号） |

## 12. 遗留事项

1. PolarDB-X 为 PXD tryout 单节点（开发测试形态）；生产建议改用 polardbx-operator on K8s 或标准版三节点（`pxd tryout -t standard -leader_only false`）。
2. 开源版本 8.4.19 与需求指定的 8.4.20 差一个小版本（8.4.20 仅云上商业版提供）；如客户购买阿里云 PolarDB-X 服务，仅需修改 `config/application-polardb.yml` 的 url/账号即可切换，代码无需改动。
3. 小程序两端（exam-miniprogram / exam-h5miniprogram）源码已随 `code-src.tar.gz` 打包，需在微信开发者工具中另行发布，不在本次服务器部署范围。
4. 后端/MinIO/Redis 的开机自启建议注册为 systemd unit（当前由 start_all.sh 手工/值守启动）。
