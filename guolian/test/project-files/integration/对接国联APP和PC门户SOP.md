# 对接国联APP和PC门户 SOP（单点登录标准对接手册）

> 适用对象：需要接入国联集团公共服务平台（以下简称"公服"）单点登录的新应用系统。
> 本文档由"纪检监察业务学习平台"（本项目）与国联APP/PC门户的实际对接经验整理，含全部坑点与规避方法。
> 版本：2026-09-08（对应源码 git 分支 migration-prep，提交 39fadfc 及之后）

---

## 1. 对接概述

### 1.1 对接目标

国联APP / PC门户 → 本系统：用户在国联APP或PC门户点击应用图标，门户在配置地址后自动附加一次性 `ticket` 参数跳转过来，本系统用 ticket 调公服接口换得员工ID，匹配本地账号后**免密登录**进入系统，并按角色/终端分流到对应前端。

### 1.2 名词解释

| 名词 | 说明 |
|---|---|
| 公服 / 能开平台 | 国联集团公共服务平台（东西向流量网关），单点登录接口的提供方 |
| ticket | 国联APP/门户下发的一次性票据，几分钟内有效，用过即失效 |
| 员工ID | 公服 HR 体系的员工唯一标识（纯数字，如 `30`、`10536`），**与本地工号是两套编码** |
| clientId | 系统在公服注册的来源标识，本项目为 `guolian-jijian` |
| x-gateway-host | 能开平台 SSO 接口必填请求头，标识目标网关路由 |
| AK/SK | 开发者门户创建应用后分配的访问密钥，用于网关鉴权 |

### 1.3 整体时序

```
国联APP/门户                中创LB(6001)              后端(8088)                 公服(10.20.8.8:32389)
   │ ① 跳转 /guolian-jijian/login?ticket=xxx            │                          │
   │────────────────────────>│                          │                          │
   │                         │ ② 返回门户分流页(静态HTML) │                          │
   │<────────────────────────│                          │                          │
   │ ③ fetch POST /api/v1/integration/ticket/login ─────>│                          │
   │                         │                          │ ④ getEmpIdByTicket ─────>│
   │                         │                          │<─── ⑤ {"data":"30",...} ──│
   │                         │ ⑥ 匹配本地账号(emp_id/工号)│ 签发JWT                  │
   │ ⑦ 按角色/终端分流跳转，令牌写入目标端localStorage     │                          │
   │    /mgmt/login | /learning-web/login | /learning-h5/login                     │
   │────────────────────────>│ ⑧ 目标端凭sso_redirect标记直接进首页(免密)           │
```

分流规则（**移动端优先判断**）：

| 条件 | 跳转目标 |
|---|---|
| 移动端 UA（Android/iPhone/iPad/HarmonyOS 等） | `/learning-h5/login`（H5端） |
| PC + 管理员角色（SUPER_ADMIN/HQ_ADMIN/L2_ADMIN/SUB_ADMIN） | `/mgmt/login`（管理端） |
| PC + 其他（考生/阅卷等） | `/learning-web/login`（用户端） |

### 1.4 本项目三个前端入口（单 IP 单端口路径路由）

| 端 | 地址 |
|---|---|
| 管理端 | `http://<IP>:6001/mgmt/login` |
| 用户端 | `http://<IP>:6001/learning-web/login` |
| H5端 | `http://<IP>:6001/learning-h5/login` |
| **门户统一单点入口（配置给国联APP/门户的地址A）** | `http://<IP>:6001/guolian-jijian/login` |

---

## 2. 参考资料（本目录 integration/ 下）

| 文档 | 内容 | 对接时用到什么 |
|---|---|---|
| `数字国联APP解密员工ticket接口文档(2).docx` | **核心接口文档**。2.1.1 根据ticket换取员工ID：`{域名}/open-api/center/anon/user/getEmpIdByTicket`，请求参数 clientId、ticket 放 Headers，响应 `{"data":"<员工ID>","meta":{"success":true,...}}` | 后端 ticket 换员工ID 的唯一接口，路径/参数/响应结构以它为准 |
| `2.开发依赖及说明-2.6.8/2.JAVA SDK使用-6.pdf` | 网关 JAVA SDK 使用说明：引入方式、DefaultApiClient 初始化、GET/POST 调用、GwSdkApiRequest 的 headerParams/bizParams 用法、错误码 | 后端调用公服的 SDK 用法 |
| `2.开发依赖及说明-2.6.8/1.东西向流量子系统快速对接手册 1.3.0.docx` | 公服对接总手册：环境信息（测试/生产地址）、接口上架（提供者/订阅者）流程、SSO 对接说明 | 环境地址、对接流程背景 |
| `2.开发依赖及说明-2.6.8/3.对接SDK及依赖/gateway-sdk-2.6.8.jar` | 网关 SDK 本体（com.alipay.sofa.gateway） | 安装到本地 maven 仓库后作为依赖引入 |
| `manual-1.txt` | 上文档的文本摘录（环境地址等） | 快速查阅 |
| `manual-2-sdk.txt` | SDK 文档的文本摘录 | 快速查阅 |

**环境地址（来自对接手册）**：

| 环境 | 能开平台地址 | x-gateway-host | 说明 |
|---|---|---|---|
| 测试 | `http://10.20.8.8:32389` | `dflw9c81mnwwnejj.apigateway.inc.alipay.net` | 本项目联调通过 |
| 生产 | `http://10.10.10.32:32389` | `yqhzlczrefrxswd8.apigateway.inc.alipay.net` | 换 IP 和 host 即可 |

---

## 3. 对接前置准备（向公服方/管理员申请）

| # | 事项 | 本项目取值（示例） | 备注 |
|---|---|---|---|
| 1 | clientId（系统来源标识） | `guolian-jijian` | 向公服方申请，约定不可变 |
| 2 | AK / SK（开发者门户创建应用生成） | `yv1ftGWERni3gTzN` / `tey40AwC64S1NJFS2K1FZ9yeG5dDKmK2` | 生产环境重新申请 |
| 3 | 测试环境 x-gateway-host | `dflw9c81mnwwnejj.apigateway.inc.alipay.net` | SSO 接口必填请求头 |
| 4 | 门户/APP 后台配置地址A | `http://10.64.16.29:6001/guolian-jijian/login` | 见第 6 节，先完成本系统侧再配置 |
| 5 | 网络放通 | 应用服务器 → 公服 10.20.8.8:32389（生产 10.10.10.32:32389） | 不通会 Connect timed out |
| 6 | 员工ID与本地账号对照 | 提供需要免密登录的员工ID清单 | 用于第 7 节账号绑定 |

---

## 4. 后端对接

### 4.1 安装网关 SDK 到本地 maven 仓库

SDK jar 内嵌的 pom 引用了不可公开获取的父 pom，**必须手写最小 pom 安装**：

```bash
cat > /tmp/gateway-sdk-pom.xml <<'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.alipay.sofa</groupId>
  <artifactId>gateway-sdk</artifactId>
  <version>2.6.8</version>
  <packaging>jar</packaging>
</project>
EOF
mvn install:install-file \
  -Dfile=gateway-sdk-2.6.8.jar \
  -DpomFile=/tmp/gateway-sdk-pom.xml
```

业务模块 pom 中引入：

```xml
<dependency>
    <groupId>com.alipay.sofa</groupId>
    <artifactId>gateway-sdk</artifactId>
    <version>2.6.8</version>
</dependency>
```

（安装 pom 已存于 `integration/gateway-sdk-pom.xml`）

### 4.2 配置项（application.yml）

```yaml
exam:
  integration:
    guolian:
      base-url: http://10.20.8.8:32389          # 公服地址（生产换 10.10.10.32:32389）
      client-id: guolian-jijian                 # 系统来源标识
      gateway-host: dflw9c81mnwwnejj.apigateway.inc.alipay.net   # SSO接口必填头（生产更换）
      access-key: <AK>                          # 为空则按"无需鉴权"模式调用
      secret-key: <SK>
```

### 4.3 代码（本项目 exam-integration 模块，可直接复用）

模块文件清单（`code-src/exam-integration/`）：

| 文件 | 职责 |
|---|---|
| `config/GuolianIntegrationProperties.java` | 上述配置项的 @ConfigurationProperties（前缀 `exam.integration.guolian`） |
| `client/GuolianOpenApiClient.java` | 封装 SDK 调用公服 2.1.1 接口 |
| `service/TicketLoginService.java` | ticket → 员工ID → 匹配本地账号 → 复用现有认证体系签发本系统 JWT |
| `controller/IntegrationController.java` | 对外暴露两个白名单接口 |

**客户端核心写法**（`GuolianOpenApiClient`）：

```java
// AK/SK 非空时用鉴权模式初始化
this.apiClient = new DefaultApiClient(properties.getBaseUrl(), secretKeys);
GwSdkApiRequest request = new GwSdkApiRequest();
request.setPath("open-api/center/anon/user/getEmpIdByTicket");
// 必填：能开平台 SSO 接口的 x-gateway-host
request.getHeaderParams().put("x-gateway-host", properties.getGatewayHost());
// 文档 2.1.2 要求 clientId/ticket 放 Headers；bizParams 同步放一份兼容不同实现
request.getHeaderParams().put("clientId", cid);
request.getHeaderParams().put("ticket", ticket);
request.getBizParams().put("clientId", cid);
request.getBizParams().put("ticket", ticket);
```

**对外接口**（已在 `SecurityConfig` 加白名单 `"/api/v1/integration/**"`）：

| 接口 | 说明 |
|---|---|
| `GET /api/v1/integration/ticket/getEmpIdByTicket?ticket=x` | 调公服原始报文直出（联调排障用；旧路径 `/ticket/getEmpId` 保留兼容） |
| `POST /api/v1/integration/ticket/login` | 免密登录。body：`{"ticket":"x","terminal":"PC_ADMIN"}`（terminal 选填，仅用于登录日志区分 H5/PC_ADMIN/PC_APP）。成功返回与普通登录一致：`accessToken/refreshToken/mustChangePassword/user`（user 内含 roles） |

curl 样例：

```bash
# 公服原始报文（ticket 一次性，每测一次换新的）
curl -s "http://127.0.0.1:8088/api/v1/integration/ticket/getEmpIdByTicket?ticket=<真实ticket>"
# 预期：{"data":"30","meta":{"success":true,"message":"接口调用成功","status":200}}

# 免密登录
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"ticket":"<真实ticket>","terminal":"PC_ADMIN"}' \
  http://127.0.0.1:8088/api/v1/integration/ticket/login
# 预期：{"code":0,"data":{"accessToken":"...","user":{...,"roles":["HQ_ADMIN"]}}}
```

**账号匹配规则**（`TicketLoginService`）：员工ID 先匹配 `t_org_user.emp_id`（国联员工ID），兼容 `job_no = 员工ID`；**目标库未加 emp_id 列时自动降级为仅工号匹配**（不抛 SQL 错误，warn 日志记录）。未匹配到返回 `code=10002 "本平台无该员工账号，请联系管理员在国联员工ID中绑定"`。

---

## 5. 前端对接

### 5.1 三个前端登录页支持 `?ticket=` 免密

每个端的登录页 `onMounted`：

1. 先查 `sessionStorage.getItem('sso_redirect')`（门户分流进入，令牌已由门户页写入 localStorage）→ 清除标记 → `router.replace(首页)`，**不渲染登录表单**；
2. 否则查 `route.query.ticket` → 调 `POST /api/v1/integration/ticket/login` → 按本端存储格式写 token → 跳首页；
3. 都没有 → 正常显示工号密码登录。

各端令牌存储格式（门户页写入时必须一致）：

| 端 | localStorage |
|---|---|
| 管理端/用户端 | `accessToken`、`refreshToken`、`userInfo`(JSON) |
| H5端 | `h5_auth`（JSON：`{accessToken, refreshToken, user, mustChangePassword}`） |

### 5.2 必踩的坑：10002 拦截器死循环

ticket 失效/未绑定时后端返回 `code=10002`，前端 axios 拦截器若把它当"token 过期"去刷新凭证、失败又跳登录页 → **死循环**。必须把登录类白名单接口排除在刷新/跳转逻辑之外：

```js
// 响应拦截器中：
if (body.code === 10002 && !config.url.includes('/auth/') && !config.url.includes('/integration/')) { ...刷新... }
```

管理端拦截器对 `/auth/login`、`/integration/ticket/login` 做同样的"仅 toast + reject"特殊处理。

### 5.3 门户分流页（免构建静态页）

源码：`code-src/sso-portal/index.html`，部署到 `web/sso/index.html`，LB 按精确路径挂载（见 6.2）。逻辑：取 ticket → 调 ticket/login → 判角色/终端 → 写目标端 localStorage → `sessionStorage.setItem('sso_redirect','1')` → `location.replace(目标端/login)`；首登改密直接送 `目标端/change-password`。失败显示原因 + 三端入口链接。

### 5.4 防闪烁

登录表单必须用就绪守卫（如 `v-if="ready"`，ready 首帧 false），否则分流跳转会先闪一下原登录页。H5 同理加 `ssoEntering` 首帧 loading。

---

## 6. 网关 / 负载均衡配置

### 6.1 单端口路径路由（中创 InforSuiteLB，nginx 语法）

```nginx
server {
    listen 6001;
    server_name _;
    client_max_body_size 500m;

    location /api/ {                      # 后端反代
        proxy_pass http://127.0.0.1:8088;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 300s;
    }
    location = / { return 302 /learning-h5/; }

    location = /guolian-jijian/login {    # 门户单点入口（见 6.2 的坑）
        default_type text/html;
        alias /root/AIExamK3/web/sso/index.html;
        add_header Cache-Control "no-store";
    }
    location = /mgmt { return 301 /mgmt/; }
    location /mgmt/ {
        alias /root/AIExamK3/web/admin/;
        index index.html;
        try_files $uri $uri/ /mgmt/index.html;   # SPA 回退（alias 下同样适用）
        if ($uri ~* \.html$) { add_header Cache-Control "no-store"; }
    }
    # /learning-web/、/learning-h5/ 同理
}
```

三个前端构建时 vite `base` 分别为 `/mgmt/`、`/learning-web/`、`/learning-h5/`，vue-router 用 `createWebHistory(import.meta.env.BASE_URL)`。

### 6.2 必踩的坑：`/login` 触发文件下载

LB 按**请求 URI 的扩展名**判断 Content-Type；`/guolian-jijian/login` 无扩展名 → `application/octet-stream` → 浏览器下载而不是渲染。**location 内必须显式 `default_type text/html;`**（本手册已含）。验证：

```bash
curl -sI http://127.0.0.1:6001/guolian-jijian/login | grep -i content-type   # 必须 text/html
```

热加载：`InforSuiteLB -p /data/InforSuiteLB -c conf/InforSuiteLB.conf -t` 校验后 `-s reload`。

---

## 7. 数据库变更

### 7.1 必做：`t_org_user` 增加 `emp_id` 列

```sql
ALTER TABLE t_org_user
    ADD COLUMN emp_id VARCHAR(64) NULL DEFAULT NULL COMMENT '国联APP员工ID（单点登录匹配用）' AFTER unionid;
ALTER TABLE t_org_user
    ADD UNIQUE INDEX uk_org_user_emp_id (emp_id);
```
（脚本：`migration-final-backup/db/schema-v1.3-upgrade-mysql.sql`。**漏执行会导致所有用户查询报 `Unknown column 'emp_id'`**；v4 之后的 jar 有降级保护但只是兜底。）

### 7.2 账号初始化 SQL 模板（以"工号=员工ID=30 的考生账号"为例）

```sql
START TRANSACTION;
SELECT id, job_no, emp_id FROM t_org_user WHERE job_no = '30' OR emp_id = '30';  -- 改前检查

INSERT INTO t_org_user
    (job_no, user_name, password_hash, phone, gender, org_id, org_path, position,
     account_status, source, user_group, emp_id, create_by, create_time, update_by, update_time, deleted)
VALUES
    ('30', '国联员工30', '$2a$10$3xhoRpfuAAoZ/Bob68/s9OcxUOEVbgGiJMNToW33zA62pHGyhjtvK',  -- 初始密码 Exam@123
     NULL, 1, 4, '/1/2/4/', '考生', 1, 1, 9, '30', 1, NOW(6), NULL, NOW(6), 0);
SET @uid = LAST_INSERT_ID();
INSERT INTO t_org_user_role (user_id, role_id, create_by, create_time, update_by, update_time)
VALUES (@uid, 6, 1, NOW(6), NULL, NOW(6));   -- role_id 6=EXAMINEE 考生
COMMIT;
```
（已存 `migration-final-backup/db/init-user-job30-mysql.sql`；总部管理员示例见 `init-user-job10536-mysql.sql`。`account_status`：1 正常 / 3 首登强制改密。）

### 7.3 角色权限速查（`t_sys_role`）

| role_id | role_code | permissions（JSON） |
|---|---|---|
| 1 | SUPER_ADMIN | `["*"]` |
| 2 | HQ_ADMIN 总部管理员 | org/user/learn/ques/paper/exam/mark/stat/notice 全量 `*` |
| 3 | L2_ADMIN 二级管理员 | org:view user:* learn:* exam:* stat:view |
| 4 | SUB_ADMIN 子企业管理员 | user:* learn:* exam:* stat:view |
| 5 | MARKER 阅卷教师 | mark:execute |
| 6 | EXAMINEE 考生 | learn:study exam:take score:view |

门户分流判定管理员的集合：`SUPER_ADMIN/HQ_ADMIN/L2_ADMIN/SUB_ADMIN`。

---

## 8. 部署与验证

### 8.1 部署顺序

1. 数据库：执行 7.1 加列 → 7.2 建号/绑定；
2. 后端：含 integration 模块的 jar（SecurityConfig 白名单已加）；
3. 前端：三端按 base 构建部署 + `web/sso/` 门户页；
4. LB：替换 cra.conf，`-t` 校验 → `-s reload`；
5. 门户/APP 后台配置地址A：`http://<IP>:6001/guolian-jijian/login`。

### 8.2 验证清单

```bash
# 1. 门户页响应头
curl -sI http://127.0.0.1:6001/guolian-jijian/login | grep -i content-type    # text/html
# 2. 三端入口
for p in mgmt learning-web learning-h5; do curl -s -o /dev/null -w "/$p %{http_code}\n" http://127.0.0.1:6001/$p/login; done
# 3. 后端（需真实 ticket）
curl -s -X POST -H "Content-Type: application/json" -d '{"ticket":"<t>","terminal":"PC_ADMIN"}' \
  http://127.0.0.1:8088/api/v1/integration/ticket/login
```

浏览器（真实 ticket）：PC 访问门户地址 → 管理员进管理端首页/考生进用户端首页；手机访问 → 进 H5。**全程无登录页闪烁、无文件下载**。ticket 一次性，每验一次从 APP 重新点。

---

## 9. 生产环境切换清单

| 项 | 测试 | 生产 |
|---|---|---|
| base-url | `http://10.20.8.8:32389` | `http://10.10.10.32:32389` |
| x-gateway-host | `dflw9c81mnwwnejj.apigateway.inc.alipay.net` | `yqhzlczrefrxswd8.apigateway.inc.alipay.net` |
| AK/SK | 测试应用密钥 | 生产应用密钥（重新申请） |
| clientId | `guolian-jijian` | 同（如公服要求可另行申请） |
| 地址A | 测试 LB 地址 | 生产 LB 地址 |
| 数据库 | 测试库执行 7.1/7.2 | 生产库执行 7.1/7.2（员工ID对照表） |
| 网络 | 放通 → 10.20.8.8:32389 | 放通 → 10.10.10.32:32389 |

---

## 10. 故障排查手册（本项目实际踩过的坑）

| 症状 | 根因 | 解决 |
|---|---|---|
| 网关返回 `6301 BizInternalServerError` (HTTP 500) | ticket 无效/过期/已使用（一次性），或公服侧问题 | 用 APP 重新进入拿新 ticket；持续出现拿 traceId 找公服方 |
| `Unknown column 'emp_id' in 'field list'` | 目标库未执行 7.1 加列脚本 | 执行 v1.3 升级脚本；jar 升级至 v4+ 有降级保护 |
| 访问 `/login` 变成下载文件 | LB 按 URI 扩展名给 Content-Type，无扩展名 → octet-stream | location 内加 `default_type text/html;`（6.2） |
| 单点登录失败且页面反复跳登录页 | 前端拦截器把 10002 当 token 过期刷新→失败→跳登录，死循环 | 拦截器排除 `/auth/`、`/integration/`（5.2） |
| 免密登录报"本平台无该员工账号" | 员工ID 与本地账号未绑定 | 7.2 设置 emp_id，或工号直接等于员工ID |
| 分流错误（管理员进了用户端） | 账号角色不在 ADMIN_ROLES 集合 | 调整角色或门户页 ADMIN_ROLES 常量 |
| PC 上想测 H5 端分流 | UA 判定为 PC | 浏览器开发者工具切换设备仿真，或临时改门户页 isMobile 逻辑 |
| 登录后菜单权限不对 | 角色 permissions 不足 | 见 7.3，给用户换角色或调整 t_sys_role.permissions |
| 改工号导致历史数据对不上 | 只改了主表 | job_no 还分布在 t_answer_sheet / t_exam_candidate / t_sys_login_log，需同步 UPDATE |

---

## 11. 代码与产物清单

**源码（git 分支 migration-prep）**：

- 后端模块：`code-src/exam-integration/`（4 个类 + pom）、`code-src/exam-common/.../SecurityConfig.java`（白名单行 46）
- 配置：`config/application-polardb.yml` 的 `exam.integration.guolian` 段
- 前端：`exam-h5-web/src/views/login/LoginPage.vue`、`exam-admin-web/src/views/login/Login.vue`、`exam-user-web/src/views/login/Login.vue`（各含 api/stores/request 拦截器配套修改）；`code-src/sso-portal/index.html`（门户分流页源码）
- 前端构建配置：三个端 `vite.config.js` 的 `base` + `router/index.js` 的 `createWebHistory(import.meta.env.BASE_URL)`

**部署产物（源机 /root/AIExamK3/）**：

- 数据库脚本：`migration-final-backup/db/schema-v1.3-upgrade-mysql.sql`、`init-user-job30-mysql.sql`、`init-user-job10536-mysql.sql`
- LB 配置：`migration-final-backup/lb/conf/cra.conf`（含旧三端口版 `cra.conf.old-3ports`）
- 增量包：`deploy-increment-20260908-v6.tar.gz`（门户 /login 版）/`deploy-increment-20260908-v7.tar.gz`（入口改为 /guolian-jijian/login 版，**当前最新**）；全量包 `deploy-target-20260908-integration.tar.gz`
- 链路文档：`国联APP单点登录链路说明.md`（项目根目录，含 Mermaid 时序图）

**关键配置值（测试环境，生产见第 9 节）**：clientId=`guolian-jijian`，AK=`yv1ftGWERni3gTzN`，SK=`tey40AwC64S1NJFS2K1FZ9yeG5dDKmK2`，x-gateway-host=`dflw9c81mnwwnejj.apigateway.inc.alipay.net`，base-url=`http://10.20.8.8:32389`。
