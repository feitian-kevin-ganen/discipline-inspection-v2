# 纪检监察人员在线考试系统 API 接口设计说明书

| 项目 | 内容 |
| --- | --- |
| 文档名称 | 纪检监察人员在线考试系统 API 接口设计说明书 |
| 文档版本 | V1.1 |
| 编写日期 | 2026-08-18 |
| 文档状态 | 评审稿 |
| 密级 | 内部资料 |
| 关联文档 | 《在线考试系统用户需求书》V1.0、《纪检人员考试培训微信小程序开发任务书》（初稿） |

---

## 版本变更记录

| 版本 | 日期 | 变更说明 | 变更依据 |
| --- | --- | --- | --- |
| V1.0 | 2026-08-14 | 首版，覆盖需求书全部功能模块，共 117 个接口 | 《在线考试系统用户需求书》V1.0 |
| V1.1 | 2026-08-18 | ① 新增 PRAC 日常练习（4 个：发起/作答即时反馈/记录/统计）；② 新增 NOTIFY-WX 微信订阅通知（4 个：openid 绑定/订阅状态查询与上报/推送记录查询）；③ 新增 GROUP 用户分组（4 个：分组清单/成员查询/批量设置标签/批量移出，分组为 `t_org_user.user_group` 标签字段口径，无独立分组表）；④ 考试规则更新：本期默认 20 题单选×5 分=100 分、双乱序、默认限时 30 分钟可配、交卷即时出分、考后回顾含解析；⑤ 题库增强：10 部制度法规分类、题目来源制度/章节/知识点、难度两级（BASIC/ADVANCED）、导入模板增强；⑥ 排名规则：分数降序、同分按用时升序（STAT-03/08）；⑦ 终端标识新增 `MP`（微信小程序），EXAM-07 名单下发支持按分组标签圈人、EXAM-11/SYS-07 通道新增 `WX_SUB`；接口总数 117 → 129 | 《纪检人员考试培训微信小程序开发任务书》（初稿） |

---

## 1. 引言

### 1.1 编写目的

本文档定义纪检监察人员在线考试系统前后端之间的全部 REST API 接口，包括 URL 规范、统一响应结构、错误码体系、鉴权与数据权限规则，以及认证、组织人员、学习资料、在线学习、学习统计、题库、试卷、考试场次与下发、在线考试答题、判分阅卷、统计分析、系统管理、日常练习、微信订阅通知、用户分组共 16 个模块的接口详细设计，作为前端（PC 管理端 Vue3 + Element Plus、微信小程序端原生 + H5 套壳为主要用户载体、手机 H5 答题端 Vue3 + Vant 为可选渠道）与后端（Spring Boot 3.3.x + Spring Security + JWT + MyBatis-Plus + PostgreSQL 16 + Redis）开发、联调与测试验收的依据。

### 1.2 范围

- 覆盖需求书第 3 章全部功能模块（FR-ORG ~ FR-SYS）对应的 HTTP 接口。
- V1.1 起，额外覆盖《纪检人员考试培训微信小程序开发任务书》（初稿）新增范围：日常练习（PRAC）、微信订阅通知（NOTIFY-WX）、用户分组（GROUP），以及考试规则、题库分类与难度、成绩排名规则的口径更新。
- 覆盖 PC 管理端与考生端（微信小程序、PC 浏览器、手机 H5 共用同一套考生端接口）。
- 不含系统内部服务间接口（本系统为单体应用）；SSO、短信/企业微信、HR 主数据同步等外部对接仅预留，不在本文档定义；微信开放平台服务端接口（code2session、subscribeMessage.send、access_token 获取）为后端出站调用，见 4.15 节说明。

### 1.3 读者对象

- 后端开发工程师、前端开发工程师（PC 管理端 / 微信小程序端 / H5 答题端）。
- 测试工程师（接口测试、压测、安全测试）。
- 架构评审与运维人员。

### 1.4 技术背景与核心模型

- 后端根包：`com.group.exam`；Spring Boot 3.3.x，Spring Security + JWT 鉴权，MyBatis-Plus 数据访问，PostgreSQL 16 持久化，Redis 承载会话状态、答题暂存、限流与幂等控制。
- 核心实体：`OrgUnit`（组织单位）、`OrgUser`（人员，V1.1 含 `userGroup` 分组标签与 `mp_openid`/`unionid` 微信身份字段）、`LearnMaterial`（学习资料）、`LearnRecord`（学习记录）、`LearnTask`（学习任务）、`QuesBank`（题库分类）、`QuesItem`（题目）、`Paper`（试卷）、`PaperQuestion`（试卷题目）、`ExamSession`（考试场次）、`ExamCandidate`（考生名单）、`AnswerSheet`（答卷）、`AnswerDetail`（答题明细）、`MarkTask`（阅卷任务）、`GradeRule`（等级规则）、`PracticeRecord`（练习记录，一题一行，V1.1）、`NotifyWxLog`（微信推送记录，V1.1）、`SysConfig`（系统参数）、`SysLog`（系统日志）。

---

## 2. 通用约定

### 2.1 协议与编码

- 全链路 HTTPS（Nginx 反向代理终止 TLS）；开发环境可用 HTTP。
- 请求/响应 Body 均为 `application/json; charset=UTF-8`；文件上传为 `multipart/form-data`；文件下载为 `application/octet-stream`。
- 查询字符串与路径参数做 URL 编码；中文按 UTF-8 编码。

### 2.2 URL 规范

- 统一前缀 `/api/v1`。
- 管理端接口：`/api/v1/admin/...`，仅管理员类角色（超级管理员、总部管理员、二级管理员、子企业管理员、阅卷教师）可访问。
- 考生端接口：`/api/v1/app/...`，手机 H5 与 PC 考生端共用，仅"考生"角色可访问。
- 公开/白名单接口：`/api/v1/auth/login`、`/api/v1/auth/refresh`、`/api/v1/health` 等，无需鉴权。
- 资源命名使用小写复数名词，层级用路径表达，动作用子资源或动词性尾段表达（如 `/papers/{id}/seal`、`/exams/{id}/force-submit`）。

### 2.3 请求方法语义

| 方法 | 语义 | 幂等性 |
| --- | --- | --- |
| GET | 查询，不产生副作用 | 幂等 |
| POST | 创建资源、触发动作（导入、审核、下发、交卷、评分提交等） | 非幂等（文中注明幂等设计的除外） |
| PUT | 全量/按字段更新资源、状态变更 | 幂等 |
| DELETE | 删除/停用资源 | 幂等 |

### 2.4 统一响应结构

所有接口（含错误响应）返回统一结构，HTTP 状态码约定：业务结果一律以 `200` 返回并由 `code` 区分；仅框架级异常使用 `401`（未认证）、`403`（无权限）、`404`（路由不存在）、`429`（限流）、`500`（未捕获异常）。

```json
{
  "code": 0,
  "message": "success",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | int | 业务错误码，`0` 表示成功，非 0 见 2.5 错误码总表 |
| message | string | 提示信息，前端可直接用于 toast；错误时为用户可读描述 |
| data | object/array/null | 业务数据；无数据时为 `null` |

### 2.5 错误码总表

错误码分段：`10xxx` 通用/鉴权，`20xxx` 组织人员，`30xxx` 学习，`40xxx` 题库试卷，`50xxx` 考试，`60xxx` 统计，`70xxx` 日常练习/微信通知（V1.1 新增段）。

| 错误码 | 说明 |
| --- | --- |
| 0 | 成功 |
| 10001 | 参数校验失败（message 含字段级提示） |
| 10002 | 未登录或 token 无效/过期 |
| 10003 | 无功能权限访问该接口 |
| 10004 | 数据权限越权（目标数据不在本级组织范围内） |
| 10005 | 资源不存在 |
| 10006 | 请求过于频繁（触发限流） |
| 10007 | 重复提交/幂等冲突 |
| 10008 | 文件类型不支持 |
| 10009 | 文件大小超限 |
| 10010 | token 已强制失效（异地登录被踢下线） |
| 10500 | 系统内部错误 |
| 20001 | 工号已存在 |
| 20002 | 单位编码已存在 |
| 20003 | 上级单位不存在 |
| 20004 | 单位下存在子级单位或在册人员，不可删除 |
| 20005 | 导入数据校验失败（data 含行级错误明细） |
| 20006 | 原密码错误 |
| 20007 | 账号已锁定 |
| 30001 | 资料不存在或未发布 |
| 30002 | 资料不在本人可见范围内 |
| 30003 | 资料当前审核状态不允许该操作 |
| 30004 | 学习任务不存在或已截止 |
| 40001 | 题目不存在或已停用 |
| 40002 | 疑似重复题目（data 含相似题目列表） |
| 40003 | 卷面总分校验失败（与设定总分不一致） |
| 40004 | 试卷已被考试引用（已封存），不可修改，仅可复制新版本 |
| 40005 | 等级区间不连续或未完整覆盖 0~满分 |
| 40006 | 抽题策略可用题量不足 |
| 40007 | 题目未审核启用，不可用于组卷 |
| 50001 | 不在考试时间窗口内 |
| 50002 | 当前用户不在本场考试考生名单内 |
| 50003 | 已交卷，不可重复提交（幂等返回时 data 带原交卷结果） |
| 50004 | 切屏/离开页面次数超限 |
| 50005 | 账号已在其他终端在线答题 |
| 50006 | 答卷不存在 |
| 50007 | 考试场次当前状态不允许该操作 |
| 50008 | 超过允许迟到进入时限 |
| 50009 | 成绩尚未发布 |
| 50010 | 阅卷任务已被领取或不在本人任务内 |
| 60001 | 统计维度/时间范围参数错误 |
| 60002 | 导出任务生成失败 |
| 70001 | 练习记录不存在（V1.1） |
| 70002 | 当前范围可用题量不足，无法发起练习（V1.1） |
| 70003 | 微信登录凭证（code）无效或已过期（V1.1） |
| 70004 | 当前用户未绑定微信（V1.1） |
| 70005 | 用户未订阅对应消息模板，微信通道已跳过（V1.1） |
| 70006 | 微信订阅消息推送失败（V1.1） |

### 2.6 分页、排序与时间格式

分页请求参数（查询字符串）：

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| pageNum | int | 否 | 1 | 页码，从 1 开始 |
| pageSize | int | 否 | 20 | 每页条数，最大 200 |
| sortBy | string | 否 | - | 排序字段（各接口注明允许值） |
| sortOrder | string | 否 | desc | `asc` / `desc` |

分页响应 `data` 结构统一为：

```json
{
  "total": 137,
  "list": [ ],
  "pageNum": 1,
  "pageSize": 20
}
```

时间格式：请求与响应中的日期时间统一为 `yyyy-MM-dd HH:mm:ss`（东八区 Asia/Shanghai）；日期为 `yyyy-MM-dd`。所有"以服务端时间为准"的场景（倒计时、截止判定）以响应内返回的服务端时间字段为准。

### 2.7 鉴权与 Token 刷新

- 除白名单接口外，所有请求须携带请求头：`Authorization: Bearer <accessToken>`。
- 登录成功签发双 token：`accessToken`（有效期 2 小时，JWT，载荷含 userId、角色、组织节点路径）、`refreshToken`（有效期 7 天， opaque token 存 Redis，支持服务端吊销）。
- `accessToken` 过期后前端调用 `POST /api/v1/auth/refresh` 换新；`refreshToken` 过期或被吊销须重新登录。
- 同一账号同一时间仅允许一个终端在线答题（FR-TAKE-05）：考生进入考试时服务端校验并记录设备指纹/会话标识，异地进入旧会话被踢，旧终端后续请求返回 `10010`。
- 管理端账号支持并发登录，但踢出策略可由系统参数配置。
- 终端标识 `terminal`：`PC_ADMIN` / `PC_APP` / `H5` / `MP`（微信小程序，V1.1 新增；原生小程序与 H5 套壳内 H5 均使用）。小程序端用户身份仍为本系统账号体系（工号+密码登录）；微信 openid 仅作为通知触达的绑定关系（NOTIFY-WX-01），不构成独立登录凭证。

### 2.8 数据权限说明

- 数据权限按组织层级隔离，由服务端在 SQL/查询层统一过滤，前端不传"数据范围"类参数来放大可见范围。
- 本级管理员仅可读写本节点及全部下级节点数据；上级可查看下级，下级不可见上级；超级管理员不受限。
- 考生仅可见本人数据及可见范围内的学习资料、本人待考/成绩。
- 凡涉及组织维度数据的接口（人员列表、学习统计、考试统计、导出等），响应数据均已按当前登录人数据权限过滤；越权访问他级单位数据返回 `10004`。
- 本文档各接口"权限与数据范围"一节注明角色要求与数据权限口径。

### 2.9 限流与幂等

- 限流：登录接口按 IP+账号限流（如 10 次/分钟）；答案保存、学习心跳等高频接口按用户限流（如 30 次/分钟）；练习发起（PRAC-01）与微信绑定（NOTIFY-WX-01）按用户限流（10 次/分钟，防刷）；触发限流返回 HTTP 429 / code `10006`。
- 幂等设计：
  - 交卷 `POST /api/v1/app/sheets/{sheetId}/submit`：以答卷状态机保证，重复提交返回首次交卷结果（code=0）或 `50003` 携带原结果，不产生副作用。
  - 答案保存 `PUT /api/v1/app/sheets/{sheetId}/answers`：按 `questionId` 维度 upsert，天然幂等；客户端可带 `clientBatchId` 供服务端去重（Redis 短期去重键）。
  - 学习心跳 `POST /api/v1/app/learn/heartbeat`：按用户+资料单调推进，重复/乱序上报不后退。
  - 写类管理接口（创建、导入、审核、下发）由前端防重复点击，服务端辅以唯一约束（工号、单位编码、场次+考生唯一键）兜底，冲突返回 `10007`。
- 文件上传为 `multipart/form-data`，约定见第 5 章。

---

## 3. 接口清单总表

角色缩写：超管=超级管理员，总管=总部管理员，二级=二级管理员，子企=子企业管理员，阅卷=阅卷教师，考生=纪检人员考生。"管理员*"表示超管/总管/二级/子企（按数据权限过滤）。

### 3.1 AUTH 认证（5 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| AUTH-01 | POST | /api/v1/auth/login | 账号密码登录，签发双 token | 公开 |
| AUTH-02 | POST | /api/v1/auth/logout | 登出，吊销 token | 全部 |
| AUTH-03 | POST | /api/v1/auth/refresh | 刷新 accessToken | 公开（持 refreshToken） |
| AUTH-04 | PUT | /api/v1/auth/password | 修改本人密码 | 全部 |
| AUTH-05 | GET | /api/v1/auth/me | 获取当前用户信息、角色与权限菜单 | 全部 |

### 3.2 ORG 组织管理（7 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| ORG-01 | GET | /api/v1/admin/org/tree | 组织树查询（按数据权限返回子树） | 管理员* |
| ORG-02 | POST | /api/v1/admin/org | 新增组织单位 | 管理员* |
| ORG-03 | PUT | /api/v1/admin/org/{orgId} | 编辑组织单位 | 管理员* |
| ORG-04 | DELETE | /api/v1/admin/org/{orgId} | 删除组织单位（无子级且无在册人员） | 管理员* |
| ORG-05 | PUT | /api/v1/admin/org/{orgId}/status | 启用/停用组织单位 | 管理员* |
| ORG-06 | PUT | /api/v1/admin/org/sort | 同级单位排序 | 管理员* |
| ORG-07 | PUT | /api/v1/admin/org/{orgId}/parent | 调整单位归属（合并/迁移） | 总管、超管 |

### 3.3 USER 人员管理（10 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| USER-01 | GET | /api/v1/admin/users | 人员分页查询 | 管理员* |
| USER-02 | POST | /api/v1/admin/users | 新增人员 | 管理员* |
| USER-03 | GET | /api/v1/admin/users/{userId} | 人员详情 | 管理员* |
| USER-04 | PUT | /api/v1/admin/users/{userId} | 编辑人员（含单位变更） | 管理员* |
| USER-05 | DELETE | /api/v1/admin/users/{userId} | 删除人员（保留历史学习/考试记录快照） | 管理员* |
| USER-06 | PUT | /api/v1/admin/users/{userId}/status | 账号启用/锁定/解锁 | 管理员* |
| USER-07 | POST | /api/v1/admin/users/import | Excel 批量导入人员 | 管理员* |
| USER-08 | GET | /api/v1/admin/users/import-template | 下载导入模板 | 管理员* |
| USER-09 | PUT | /api/v1/admin/users/{userId}/roles | 分配角色 | 总管、超管（子企仅可授考生/阅卷） |
| USER-10 | PUT | /api/v1/admin/users/{userId}/password/reset | 重置密码（生成初始密码） | 管理员* |

### 3.4 LIB 学习资料（12 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| LIB-01 | GET | /api/v1/admin/lib/categories/tree | 资料分类树 | 管理员* |
| LIB-02 | POST | /api/v1/admin/lib/categories | 新增分类 | 管理员* |
| LIB-03 | PUT | /api/v1/admin/lib/categories/{catId} | 编辑分类（含可见范围） | 管理员* |
| LIB-04 | DELETE | /api/v1/admin/lib/categories/{catId} | 删除分类（须为空） | 管理员* |
| LIB-05 | GET | /api/v1/admin/lib/materials | 资料分页查询（管理端） | 管理员* |
| LIB-06 | POST | /api/v1/admin/lib/materials | 上传/新建资料（multipart） | 管理员* |
| LIB-07 | GET | /api/v1/admin/lib/materials/{materialId} | 资料详情 | 管理员* |
| LIB-08 | PUT | /api/v1/admin/lib/materials/{materialId} | 编辑资料元信息/换版 | 管理员* |
| LIB-09 | DELETE | /api/v1/admin/lib/materials/{materialId} | 删除资料 | 管理员* |
| LIB-10 | POST | /api/v1/admin/lib/materials/{materialId}/audit | 资料审核（通过/驳回） | 上级管理员 |
| LIB-11 | PUT | /api/v1/admin/lib/materials/{materialId}/publish | 发布/下架 | 管理员* |
| LIB-12 | PUT | /api/v1/admin/lib/materials/{materialId}/scope | 设置可见范围 | 管理员* |

### 3.5 LEARN 在线学习（8 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| LEARN-01 | GET | /api/v1/app/learn/materials | 本人可见资料列表（检索/排序） | 考生 |
| LEARN-02 | GET | /api/v1/app/learn/materials/{materialId} | 资料详情 + 本人学习断点 | 考生 |
| LEARN-03 | GET | /api/v1/app/learn/materials/{materialId}/file | 获取资料文件地址（预览/播放） | 考生 |
| LEARN-04 | POST | /api/v1/app/learn/heartbeat | 学习进度/时长心跳上报 | 考生 |
| LEARN-05 | GET | /api/v1/app/learn/records/{materialId} | 获取断点续学进度 | 考生 |
| LEARN-06 | GET | /api/v1/app/learn/tasks | 本人学习任务待办列表 | 考生 |
| LEARN-07 | GET | /api/v1/admin/learn/tasks | 学习任务分页查询（管理端） | 管理员* |
| LEARN-08 | POST | /api/v1/admin/learn/tasks | 下达学习任务 | 管理员* |

### 3.6 LSTAT 学习统计（5 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| LSTAT-01 | GET | /api/v1/app/lstat/personal | 个人学习统计 | 考生 |
| LSTAT-02 | GET | /api/v1/admin/lstat/org-summary | 单位学习汇总（按层级） | 管理员* |
| LSTAT-03 | GET | /api/v1/admin/lstat/org-summary/{orgId} | 单位下钻明细与未达标名单 | 管理员* |
| LSTAT-04 | GET | /api/v1/admin/lstat/ranking | 学习排行（单位/个人） | 管理员* |
| LSTAT-05 | GET | /api/v1/admin/lstat/export | 学习统计导出 Excel | 管理员* |

### 3.7 QUES 题库（13 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| QUES-01 | GET | /api/v1/admin/ques/banks/tree | 题库分类树 | 管理员* |
| QUES-02 | POST | /api/v1/admin/ques/banks | 新增题库分类 | 管理员* |
| QUES-03 | PUT | /api/v1/admin/ques/banks/{bankId} | 编辑题库分类 | 管理员* |
| QUES-04 | DELETE | /api/v1/admin/ques/banks/{bankId} | 删除题库分类（须无题目） | 管理员* |
| QUES-05 | GET | /api/v1/admin/ques/items | 题目分页查询 | 管理员* |
| QUES-06 | POST | /api/v1/admin/ques/items | 新增题目 | 管理员* |
| QUES-07 | GET | /api/v1/admin/ques/items/{itemId} | 题目详情 | 管理员* |
| QUES-08 | PUT | /api/v1/admin/ques/items/{itemId} | 编辑题目 | 管理员* |
| QUES-09 | DELETE | /api/v1/admin/ques/items/{itemId} | 删除/停用题目 | 管理员* |
| QUES-10 | POST | /api/v1/admin/ques/items/import | Excel/Word 批量导入题目 | 管理员* |
| QUES-11 | GET | /api/v1/admin/ques/items/import-template | 下载题目导入模板 | 管理员* |
| QUES-12 | POST | /api/v1/admin/ques/items/dup-check | 题干相似度查重 | 管理员* |
| QUES-13 | POST | /api/v1/admin/ques/items/{itemId}/audit | 题目审核（启用/驳回/停用） | 管理员* |

### 3.8 PAPER 试卷（10 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| PAPER-01 | GET | /api/v1/admin/papers | 试卷分页查询 | 管理员* |
| PAPER-02 | POST | /api/v1/admin/papers | 创建试卷（固定卷/随机策略卷） | 管理员* |
| PAPER-03 | GET | /api/v1/admin/papers/{paperId} | 试卷详情（含题目/策略/分值/等级规则） | 管理员* |
| PAPER-04 | PUT | /api/v1/admin/papers/{paperId} | 编辑试卷基础信息与分数设计 | 管理员* |
| PAPER-05 | DELETE | /api/v1/admin/papers/{paperId} | 删除试卷（未被引用） | 管理员* |
| PAPER-06 | PUT | /api/v1/admin/papers/{paperId}/questions | 固定卷选题与逐题分值设置 | 管理员* |
| PAPER-07 | GET | /api/v1/admin/papers/{paperId}/preview | 试卷预览（管理端，含答案解析） | 管理员* |
| PAPER-08 | POST | /api/v1/admin/papers/{paperId}/copy | 复制试卷新版本 | 管理员* |
| PAPER-09 | PUT | /api/v1/admin/papers/{paperId}/seal | 封存试卷 | 管理员* |
| PAPER-10 | PUT | /api/v1/admin/papers/{paperId}/grade-rules | 配置成绩等级规则 | 管理员* |

### 3.9 EXAM 考试场次与下发（14 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| EXAM-01 | GET | /api/v1/admin/exams | 考试场次分页查询 | 管理员* |
| EXAM-02 | POST | /api/v1/admin/exams | 创建考试场次 | 管理员* |
| EXAM-03 | GET | /api/v1/admin/exams/{examId} | 场次详情（含规则、名单统计） | 管理员* |
| EXAM-04 | PUT | /api/v1/admin/exams/{examId} | 编辑场次（未开始） | 管理员* |
| EXAM-05 | DELETE | /api/v1/admin/exams/{examId} | 删除场次（未开始且未下发） | 管理员* |
| EXAM-06 | PUT | /api/v1/admin/exams/{examId}/status | 场次状态流转（开始/结束等） | 管理员* |
| EXAM-07 | POST | /api/v1/admin/exams/{examId}/candidates/scope | 按组织范围生成考生名单快照（考题下发） | 管理员* |
| EXAM-08 | GET | /api/v1/admin/exams/{examId}/candidates | 考生名单分页查询 | 管理员* |
| EXAM-09 | POST | /api/v1/admin/exams/{examId}/candidates | 追加考生 | 管理员* |
| EXAM-10 | DELETE | /api/v1/admin/exams/{examId}/candidates | 剔除考生 | 管理员* |
| EXAM-11 | POST | /api/v1/admin/exams/{examId}/notify | 推送考试通知 | 管理员* |
| EXAM-12 | PUT | /api/v1/admin/exams/{examId}/extend | 延长考试时间 | 管理员* |
| EXAM-13 | POST | /api/v1/admin/exams/{examId}/force-submit | 强制收卷 | 管理员* |
| EXAM-14 | PUT | /api/v1/admin/exams/{examId}/retake | 补考设置（窗口与人员） | 管理员* |

### 3.10 TAKE 在线考试考生端（9 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| TAKE-01 | GET | /api/v1/app/exams | 待考/历史考试列表 | 考生 |
| TAKE-02 | POST | /api/v1/app/exams/{examId}/enter | 阅读须知确认并进入考试 | 考生 |
| TAKE-03 | GET | /api/v1/app/time | 服务端校时 | 考生 |
| TAKE-04 | GET | /api/v1/app/exams/{examId}/paper | 拉取试卷（脱敏，无答案解析） | 考生 |
| TAKE-05 | PUT | /api/v1/app/sheets/{sheetId}/answers | 批量/增量保存答案（心跳） | 考生 |
| TAKE-06 | POST | /api/v1/app/sheets/{sheetId}/screen-events | 切屏/离开页面上报 | 考生 |
| TAKE-07 | POST | /api/v1/app/sheets/{sheetId}/submit | 交卷（幂等） | 考生 |
| TAKE-08 | GET | /api/v1/app/sheets/{sheetId} | 答卷状态查询（断线续考恢复） | 考生 |
| TAKE-09 | GET | /api/v1/app/exams/{examId}/result | 成绩查询（发布后） | 考生 |

### 3.11 MARK 判分与阅卷（7 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| MARK-01 | GET | /api/v1/admin/mark/tasks | 阅卷任务列表 | 阅卷、管理员* |
| MARK-02 | POST | /api/v1/admin/mark/tasks/{taskId}/claim | 领取阅卷任务 | 阅卷 |
| MARK-03 | GET | /api/v1/admin/mark/tasks/{taskId}/items | 待阅答卷题目列表 | 阅卷 |
| MARK-04 | POST | /api/v1/admin/mark/scores | 提交评分（批量，含评语） | 阅卷 |
| MARK-05 | GET | /api/v1/admin/mark/review-queue | 人工复核队列（填空疑似/双评仲裁） | 阅卷、管理员* |
| MARK-06 | POST | /api/v1/admin/mark/review | 提交复核结论 | 阅卷、管理员* |
| MARK-07 | POST | /api/v1/admin/mark/exams/{examId}/publish | 成绩合成并批量发布 | 总管、超管（或场次创建方管理员） |

### 3.12 STAT 考试统计分析（8 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| STAT-01 | GET | /api/v1/admin/stat/exams/{examId}/overview | 单场考试总览 | 管理员* |
| STAT-02 | GET | /api/v1/admin/stat/exams/{examId}/org-summary | 分层级单位汇总 | 管理员* |
| STAT-03 | GET | /api/v1/admin/stat/exams/{examId}/org-summary/{orgId} | 单位下钻（下级单位/人员明细） | 管理员* |
| STAT-04 | GET | /api/v1/admin/stat/exams/{examId}/score-segments | 分数段分布 | 管理员* |
| STAT-05 | GET | /api/v1/admin/stat/exams/{examId}/grade-distribution | 优良中差等级分布 | 管理员* |
| STAT-06 | GET | /api/v1/admin/stat/exams/{examId}/question-analysis | 题目正确率与知识点分析 | 管理员* |
| STAT-07 | GET | /api/v1/admin/stat/exams/{examId}/export | 统计报表导出 Excel | 管理员* |
| STAT-08 | GET | /api/v1/app/stat/my-transcripts | 个人历次成绩单 | 考生 |

### 3.13 SYS 系统管理（9 个）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| SYS-01 | GET | /api/v1/admin/sys/configs | 系统参数查询 | 超管、总管 |
| SYS-02 | PUT | /api/v1/admin/sys/configs | 系统参数修改（批量） | 超管 |
| SYS-03 | GET | /api/v1/admin/sys/logs/login | 登录日志查询 | 超管、总管 |
| SYS-04 | GET | /api/v1/admin/sys/logs/operation | 操作日志查询 | 超管、总管 |
| SYS-05 | GET | /api/v1/admin/sys/logs/exam-abnormal | 考试异常日志查询（强制交卷/切屏超限） | 管理员* |
| SYS-06 | GET | /api/v1/admin/sys/notices | 通知消息分页查询（管理端） | 管理员* |
| SYS-07 | POST | /api/v1/admin/sys/notices | 发布通知消息 | 管理员* |
| SYS-08 | GET | /api/v1/app/notices | 本人站内消息列表 | 考生 |
| SYS-09 | PUT | /api/v1/app/notices/{noticeId}/read | 标记消息已读 | 考生 |

### 3.14 PRAC 日常练习（4 个，V1.1 新增）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| PRAC-01 | POST | /api/v1/app/practice/start | 发起练习（按法规分类/全库随机抽题，题数可选） | 考生 |
| PRAC-02 | POST | /api/v1/app/practice/answer | 提交单题作答，即时反馈正误 + 解析 | 考生 |
| PRAC-03 | GET | /api/v1/app/practice/records | 本人练习记录分页查询（含错题回顾） | 考生 |
| PRAC-04 | GET | /api/v1/app/practice/stat | 本人练习统计（总量/分类正确率/趋势/薄弱知识点） | 考生 |

### 3.15 NOTIFY-WX 微信订阅通知（4 个，V1.1 新增）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| NOTIFY-WX-01 | POST | /api/v1/app/wechat/bind | 微信登录凭证（code）绑定当前用户（code2session） | 考生 |
| NOTIFY-WX-02 | GET | /api/v1/app/wechat/subscribe | 查询本人各场景订阅授权状态 | 考生 |
| NOTIFY-WX-03 | PUT | /api/v1/app/wechat/subscribe | 上报订阅授权结果（wx.requestSubscribeMessage 回调） | 考生 |
| NOTIFY-WX-04 | GET | /api/v1/admin/notify-wx/records | 微信推送记录分页查询（管理端） | 管理员* |

### 3.16 GROUP 用户分组（4 个，V1.1 新增）

| 编号 | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| GROUP-01 | GET | /api/v1/admin/groups | 分组清单（固定枚举：派驻组/子企业纪委/纪检委员/其他 + 各分组人数） | 管理员* |
| GROUP-02 | GET | /api/v1/admin/groups/{userGroup}/members | 分组成员分页查询 | 管理员* |
| GROUP-03 | PUT | /api/v1/admin/groups/{userGroup}/members | 批量设置用户分组标签（修改 user_group 字段） | 管理员* |
| GROUP-04 | DELETE | /api/v1/admin/groups/{userGroup}/members | 批量移出分组（user_group 重置为 9 其他） | 管理员* |

> 接口合计：**129 个**（V1.0 117 个 + V1.1 新增 12 个）。另有不计入清单的运维接口 `GET /api/v1/health`（健康检查，白名单，返回 `{"code":0,"data":{"status":"UP"}}`）。

---

## 4. 分模块接口详细设计

> 统一说明：除白名单外所有接口均需请求头 `Authorization: Bearer <accessToken>`；下文"请求头"一节仅在有额外要求时列出。响应统一包裹 `{code,message,data}`，下文"响应字段"表仅描述 `data` 内容。分页接口的请求/响应遵循 2.6 节，不再逐接口重复分页字段表。

### 4.1 AUTH 认证

#### AUTH-01 登录

- **方法/路径**：`POST /api/v1/auth/login`（白名单）
- **说明**：账号密码登录，校验账号状态（锁定/停用拒绝），签发 JWT accessToken 与 refreshToken；登录成功/失败写登录日志；连续失败按系统参数锁定账号。

请求 Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | string | 是 | 工号 |
| password | string | 是 | 密码（前端明文传输，依赖 HTTPS；不建议前端加密） |
| terminal | string | 否 | 终端标识：`PC_ADMIN` / `PC_APP` / `H5` / `MP`（微信小程序，V1.1），用于单终端答题控制 |
| deviceId | string | 否 | 设备指纹（H5 端生成并本地持久化） |

请求示例：

```json
{ "username": "JW10001", "password": "Xy@202608", "terminal": "H5", "deviceId": "h5-9f3c2a" }
```

响应字段（data）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| accessToken | string | JWT，有效期 2 小时 |
| refreshToken | string | 刷新令牌，有效期 7 天 |
| expiresIn | int | accessToken 有效期（秒） |
| mustChangePassword | boolean | 是否初始密码须强制修改 |
| user | object | `{userId, name, jobNo, orgId, orgName, roles[], avatarUrl}` |

响应示例：

```json
{
  "code": 0, "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "rft_7c9e2f1a",
    "expiresIn": 7200,
    "mustChangePassword": false,
    "user": { "userId": 10021, "name": "张三", "jobNo": "JW10001", "orgId": 305, "orgName": "华北子企业纪委", "roles": ["EXAMINEE"], "avatarUrl": null }
  }
}
```

错误码：10001、20007（账号锁定）、10002（用户名或密码错误，统一模糊提示）。限流：同 IP+账号 10 次/分钟。

#### AUTH-02 登出

- **方法/路径**：`POST /api/v1/auth/logout`
- **说明**：吊销当前 accessToken（加入 Redis 黑名单）与 refreshToken。无 Body，data 为 null。错误码：10002。

#### AUTH-03 刷新 Token

- **方法/路径**：`POST /api/v1/auth/refresh`（白名单，凭 refreshToken）

请求 Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| refreshToken | string | 是 | 登录时签发的刷新令牌 |

响应 data：同 AUTH-01 的 token 三元组（`accessToken/refreshToken/expiresIn`，refreshToken 轮换签发，旧 refreshToken 立即失效）。错误码：10002（refreshToken 无效/过期/已吊销，需重新登录）、10010。

#### AUTH-04 修改密码

- **方法/路径**：`PUT /api/v1/auth/password`

请求 Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| oldPassword | string | 是 | 原密码 |
| newPassword | string | 是 | 新密码，8-32 位，须含字母+数字 |

- 说明：修改成功后吊销本人全部 token（需重新登录）；清除"强制改密"标记。错误码：20006、10001。

#### AUTH-05 获取当前用户与权限菜单

- **方法/路径**：`GET /api/v1/auth/me`

响应字段（data）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| userId / name / jobNo | long/string | 基本信息 |
| orgId / orgName / orgPath | long/string/string | 所属单位及组织路径（如 `/1/12/305/`） |
| roles | string[] | 角色编码：`SUPER_ADMIN`/`HQ_ADMIN`/`L2_ADMIN`/`SUB_ADMIN`/`MARKER`/`EXAMINEE` |
| permissions | string[] | 功能权限点编码（如 `ques:item:edit`），前端控制按钮级显隐 |
| menus | object[] | 管理端菜单树 `{code,title,path,icon,children[]}`，按角色过滤 |
| dataScopeOrgId | long | 数据权限根节点（本人可管理的最高组织节点） |

错误码：10002。备注：H5 考生端仅需 `user` 与 `roles`，`menus` 可为空数组。

---

### 4.2 ORG 组织管理

数据权限：全部接口要求管理员角色，且只能管理本人数据权限根节点（含）以下的组织子树；越权返回 10004。组织调整、增删改均写操作日志（FR-ORG-07）。

#### ORG-01 组织树查询

- **方法/路径**：`GET /api/v1/admin/org/tree`

查询参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| rootOrgId | long | 否 | 指定根节点，默认当前管理员数据权限根节点 |
| depth | int | 否 | 下钻层数，默认全量；用于大树懒加载 |
| includeDisabled | boolean | 否 | 是否含停用节点，默认 false |

响应 data：`OrgNode[]` 树形结构，节点字段：`{orgId, parentId, orgName, orgCode, orgType(HQ/L2/SUB/DEPT), leaderName, phone, sortNo, status(1启用/0停用), userCount, children[]}`。

响应示例：

```json
{
  "code": 0, "message": "success",
  "data": [
    { "orgId": 1, "parentId": 0, "orgName": "集团总部纪委", "orgCode": "HQ", "orgType": "HQ",
      "leaderName": "李书记", "phone": "010-88888888", "sortNo": 1, "status": 1, "userCount": 35,
      "children": [
        { "orgId": 12, "parentId": 1, "orgName": "华北二级集团纪委", "orgCode": "HB", "orgType": "L2",
          "leaderName": "王组长", "phone": null, "sortNo": 1, "status": 1, "userCount": 128, "children": [] }
      ] }
  ]
}
```

错误码：10003、10004。

#### ORG-02 新增组织单位

- **方法/路径**：`POST /api/v1/admin/org`

请求 Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| parentId | long | 是 | 上级单位 ID，须在数据权限范围内 |
| orgName | string | 是 | 单位名称，≤50 字 |
| orgCode | string | 是 | 单位编码，全局唯一，字母数字 |
| orgType | string | 是 | `HQ`/`L2`/`SUB`/`DEPT` |
| leaderName | string | 否 | 负责人 |
| phone | string | 否 | 联系电话 |
| sortNo | int | 否 | 排序号，默认同级末位 |

响应 data：`{ "orgId": 305 }`。错误码：10001、20002（编码重复）、20003、10004。

#### ORG-03 编辑组织单位

- **方法/路径**：`PUT /api/v1/admin/org/{orgId}`
- 说明：Body 同 ORG-02（不含 parentId，改归属走 ORG-07）；仅传需修改字段。错误码：10005、20002、10004。

#### ORG-04 删除组织单位

- **方法/路径**：`DELETE /api/v1/admin/org/{orgId}`
- 说明：仅允许删除无子级且无在册人员的单位；物理删除前置校验失败返回 20004。错误码：10005、20004、10004。

#### ORG-05 启用/停用组织单位

- **方法/路径**：`PUT /api/v1/admin/org/{orgId}/status`

请求 Body：`{ "status": 0 }`（1 启用 / 0 停用）。说明：停用为单位维度标记，停用后单位下人员不可登录考生端，历史数据保留；不影响子级单位自身状态。错误码：10005、10004。

#### ORG-06 同级单位排序

- **方法/路径**：`PUT /api/v1/admin/org/sort`

请求 Body：`{ "parentId": 12, "orderedOrgIds": [305, 306, 307] }`。说明：按数组顺序重排同级 sortNo，幂等。错误码：10001、10004。

#### ORG-07 调整单位归属（合并/迁移）

- **方法/路径**：`PUT /api/v1/admin/org/{orgId}/parent`（仅总部管理员、超管）

请求 Body：`{ "newParentId": 15 }`。说明：整棵子树迁移；校验目标上级不是自身后代（防环）；迁移后该子树管理员数据权限随新路径自动生效；人员历史学习/考试记录保留原单位快照不受影响。错误码：10001（形成环路）、20003、10004。

---

### 4.3 USER 人员管理

数据权限：管理员仅可管理本级及以下单位人员。人员增删改、角色授权、密码重置均写操作日志。

#### USER-01 人员分页查询

- **方法/路径**：`GET /api/v1/admin/users`

查询参数（另含分页参数）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orgId | long | 否 | 按单位筛选 |
| includeSub | boolean | 否 | 是否含下级单位，默认 true |
| keyword | string | 否 | 姓名/工号模糊 |
| roleCode | string | 否 | 按角色筛选 |
| status | int | 否 | 1 正常 / 0 停用 / 2 锁定 |

响应 data（分页）：list 项 `{userId, name, jobNo, phone, gender, orgId, orgName, position, roles[], userGroup(V1.1：分组标签 1/2/3/9), userGroupName(V1.1：分组名称), status, lastLoginTime}`。

#### USER-02 新增人员

- **方法/路径**：`POST /api/v1/admin/users`

请求 Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 姓名 |
| jobNo | string | 是 | 工号，全局唯一 |
| phone | string | 是 | 手机号 |
| gender | string | 否 | `M`/`F` |
| orgId | long | 是 | 所属单位 |
| position | string | 否 | 职务 |
| roleCodes | string[] | 是 | 角色编码数组 |
| userGroup | int | 否 | 用户分组标签（V1.1：1 派驻组 / 2 子企业纪委 / 3 纪检委员 / 9 其他，默认 9；见 GROUP 模块） |

响应 data：`{ "userId": 10021, "initialPassword": "Xk#84213" }`。说明：服务端生成初始密码并标记强制改密；初始密码仅在创建响应中返回一次，不落库明文。错误码：20001（工号重复）、10001、10004。

#### USER-03 人员详情

- **方法/路径**：`GET /api/v1/admin/users/{userId}`
- 响应 data：USER-01 列表项字段 + `{idCardMasked, entryTime, remark, wxBound(V1.1：是否已绑定微信), createdTime, updatedTime}`。错误码：10005、10004。

#### USER-04 编辑人员

- **方法/路径**：`PUT /api/v1/admin/users/{userId}`
- 说明：Body 同 USER-02（不含 jobNo，工号不可改）；`orgId` 变更即人员调岗，其历史学习记录（LearnRecord）与考试记录（AnswerSheet/ExamCandidate）保留调岗时单位快照，新统计周期按新单位归集。错误码：10005、10004。

#### USER-05 删除人员

- **方法/路径**：`DELETE /api/v1/admin/users/{userId}`
- 说明：逻辑删除（账号停用且不可登录），历史学习与考试数据全部保留；若需彻底匿名化由超管走运维流程，不在本接口。错误码：10005、10004。

#### USER-06 账号启用/锁定/解锁

- **方法/路径**：`PUT /api/v1/admin/users/{userId}/status`

请求 Body：`{ "status": 1 }`（1 正常 / 0 停用 / 2 锁定）。说明：锁定/停用即刻吊销该用户全部 token。错误码：10005、10004。

#### USER-07 批量导入人员

- **方法/路径**：`POST /api/v1/admin/users/import`，`Content-Type: multipart/form-data`

表单字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| file | file | 是 | Excel 文件（.xlsx，≤10MB），模板见 USER-08 |
| mode | string | 否 | `INSERT_ONLY`（默认，重复工号即失败行）/ `UPSERT`（按工号更新） |

响应 data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| totalCount / successCount / failCount | int | 导入统计 |
| failures | object[] | 失败明细 `{rowNo, jobNo, name, reason}`，重复工号/单位不存在/格式错误逐行给出 |

响应示例：

```json
{ "code": 0, "message": "success",
  "data": { "totalCount": 120, "successCount": 117, "failCount": 3,
    "failures": [ { "rowNo": 5, "jobNo": "JW10005", "name": "赵某", "reason": "工号已存在" } ] } }
```

说明：服务端先整表校验再入库；存在失败行时默认成功行照常入库（事务按行），前端据 failures 修正后重传；同文件重复提交由工号唯一约束兜底。错误码：10008、10009、20005（整表校验失败时 failures 全量返回）。

#### USER-08 下载导入模板

- **方法/路径**：`GET /api/v1/admin/users/import-template`
- 说明：返回 `.xlsx` 文件流（`Content-Disposition: attachment`），含表头与填写说明行。

#### USER-09 角色分配

- **方法/路径**：`PUT /api/v1/admin/users/{userId}/roles`

请求 Body：`{ "roleCodes": ["EXAMINEE", "MARKER"] }`。说明：总部管理员/超管可授全部角色；二级/子企管理员仅可授 `EXAMINEE`、`MARKER`，授管理员角色返回 10003。角色变更即时生效（权限点缓存在 token 刷新后更新）。错误码：10005、10003、10004。

#### USER-10 重置密码

- **方法/路径**：`PUT /api/v1/admin/users/{userId}/password/reset`
- 响应 data：`{ "initialPassword": "Qm#55920" }`。说明：重置后吊销该用户全部 token 并标记强制改密；仅返回一次。错误码：10005、10004。

---

### 4.4 LIB 学习资料

资料状态机：`DRAFT（草稿）→ PENDING_AUDIT（待审核）→ APPROVED（审核通过）→ PUBLISHED（已发布）→ OFFLINE（已下架）`；审核驳回回到 `DRAFT`。"资料需审核"由系统参数 `lib.auditRequired` 控制，关闭时上传后直接 APPROVED。数据权限：管理员管理本组织范围内上传的资料；审核由上传单位的上级管理员执行。

#### LIB-01 资料分类树

- **方法/路径**：`GET /api/v1/admin/lib/categories/tree`
- 响应 data：分类树 `[{catId, parentId, catName, sortNo, scopeType, children[]}]`；`scopeType`：`ALL`（全集团）/`L2_SUB`（指定二级集团及下属）/`SPECIFIC`（指定单位）。错误码：10003。

#### LIB-02 新增分类

- **方法/路径**：`POST /api/v1/admin/lib/categories`

请求 Body：`{ "parentId": 0, "catName": "党纪法规", "sortNo": 1 }`。响应 data：`{ "catId": 8 }`。错误码：10001、10005。

#### LIB-03 编辑分类

- **方法/路径**：`PUT /api/v1/admin/lib/categories/{catId}`

请求 Body：`{ "catName": "党纪法规", "sortNo": 1, "scopeType": "L2_SUB", "scopeOrgIds": [12, 15] }`。说明：`scopeType=ALL` 时 `scopeOrgIds` 忽略；分类可见范围对下属资料起默认约束作用。错误码：10001、10005、10004。

#### LIB-04 删除分类

- **方法/路径**：`DELETE /api/v1/admin/lib/categories/{catId}`
- 说明：仅允许删除无子分类且无资料的分类，否则返回 20004。错误码：10005、20004。

#### LIB-05 资料分页查询（管理端）

- **方法/路径**：`GET /api/v1/admin/lib/materials`

查询参数：`catId`、`keyword`（标题/摘要/标签）、`status`（DRAFT/PENDING_AUDIT/APPROVED/PUBLISHED/OFFLINE）、`materialType`（DOC/VIDEO/RICHTEXT）、分页参数；`sortBy` 允许 `createdTime`、`studyCount`。

响应 data（分页）：list 项 `{materialId, title, materialType, catId, catName, coverUrl, tags[], status, fileSize, durationSec, uploaderOrgName, studyCount, createdTime}`。

#### LIB-06 上传/新建资料

- **方法/路径**：`POST /api/v1/admin/lib/materials`，`multipart/form-data`

表单字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| file | file | 条件 | 资料文件；`materialType=RICHTEXT` 时可为空 |
| title | string | 是 | 资料标题 |
| catId | long | 是 | 所属分类 |
| materialType | string | 是 | `DOC`（PDF/Word/PPT）/ `VIDEO`（MP4）/ `RICHTEXT`（图文） |
| summary | string | 否 | 摘要 |
| tags | string | 否 | 标签，逗号分隔 |
| cover | file | 否 | 封面图（jpg/png，≤2MB） |
| contentHtml | string | 否 | 图文内容（materialType=RICHTEXT 时必填） |
| durationSec | int | 条件 | 标称时长（秒），VIDEO 必填，DOC 可填建议阅读时长 |
| scopeType | string | 是 | 可见范围：`ALL`/`L2_SUB`/`SPECIFIC` |
| scopeOrgIds | string | 条件 | 可见单位 ID，逗号分隔，scopeType≠ALL 时必填 |

说明：

- 文件类型与大小限制由系统参数控制（默认视频 500MB、文档 50MB），超限返回 10009，类型不符返回 10008。
- 服务端计算文件 MD5 与页数/时长元数据；上传后状态为 `DRAFT` 或 `PENDING_AUDIT`（视审核开关）。
- 大文件分片断点续传见第 5 章，本接口承载 ≤100MB 的单次上传。

响应 data：`{ "materialId": 8801, "status": "PENDING_AUDIT" }`。错误码：10001、10008、10009、10004。

#### LIB-07 资料详情（管理端）

- **方法/路径**：`GET /api/v1/admin/lib/materials/{materialId}`
- 响应 data：LIB-05 列表项 + `{summary, contentHtml, fileUrl, fileMd5, pageCount, scopeType, scopeOrgIds[], auditBy, auditTime, auditComment, version}`。错误码：10005、10004。

#### LIB-08 编辑资料/版本更新

- **方法/路径**：`PUT /api/v1/admin/lib/materials/{materialId}`，`multipart/form-data`
- 说明：字段同 LIB-06 均可选；携带 `file` 视为版本更新（`version`+1），学员断点进度保留，完成状态按新版本重新判定（已完成的保留完成标记）；已发布资料修改后需重新审核（审核开关开启时）。错误码：30003、10004。

#### LIB-09 删除资料

- **方法/路径**：`DELETE /api/v1/admin/lib/materials/{materialId}`
- 说明：逻辑删除；学员学习记录保留用于历史统计。错误码：10005、10004。

#### LIB-10 资料审核

- **方法/路径**：`POST /api/v1/admin/lib/materials/{materialId}/audit`

请求 Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| action | string | 是 | `APPROVE` / `REJECT` |
| comment | string | 条件 | 驳回时必填审核意见 |

说明：审核人为上传单位的上级管理员（服务端校验其组织层级高于资料上传单位）；通过后状态 `APPROVED`，驳回回 `DRAFT` 并附意见。错误码：30003（状态非待审核）、10003、10004。

#### LIB-11 发布/下架

- **方法/路径**：`PUT /api/v1/admin/lib/materials/{materialId}/publish`

请求 Body：`{ "action": "PUBLISH" }`（`PUBLISH` / `OFFLINE`）。说明：仅 `APPROVED` 可发布，`PUBLISHED` 可下架；下架后学员端不可见，历史学习记录保留。幂等：重复发布返回当前状态不报错。错误码：30003、10004。

#### LIB-12 设置可见范围

- **方法/路径**：`PUT /api/v1/admin/lib/materials/{materialId}/scope`

请求 Body：`{ "scopeType": "L2_SUB", "scopeOrgIds": [12, 15] }`。说明：可见范围判定口径——`ALL` 全员可见；`L2_SUB` 指定二级集团整棵子树可见；`SPECIFIC` 仅指定单位（不含下级）可见；学员端列表与详情均由服务端按该规则过滤。错误码：10001、10005、10004。

---

### 4.5 LEARN 在线学习

考生端接口（`/app/`）数据范围恒为"当前登录考生本人"。完成判定（FR-LEARN-05）：进度 ≥ 阈值（默认 90%）且有效时长 ≥ 标称时长阈值（默认 80%，参数 `learn.durationThreshold`），由服务端在心跳时判定。

#### LEARN-01 可见资料列表

- **方法/路径**：`GET /api/v1/app/learn/materials`

查询参数：`catId`、`keyword`、`sortBy`（`createdTime`/`studyCount`）、分页参数。

响应 data（分页）：list 项 `{materialId, title, materialType, catName, coverUrl, tags[], durationSec, studyCount, myProgress: {progressPct, effectiveSec, status(NOT_STARTED/IN_PROGRESS/COMPLETED)}}`。说明：仅返回已发布且在本人可见范围内的资料（服务端过滤）。错误码：10002。

#### LEARN-02 资料详情 + 断点

- **方法/路径**：`GET /api/v1/app/learn/materials/{materialId}`

响应 data：`{materialId, title, materialType, summary, contentHtml, catName, tags[], durationSec, pageCount, myRecord: {progressPct, lastPosition, readPages, effectiveSec, status, completedTime}}`。说明：`lastPosition` 视频为播放秒数、文档为最后阅读页码；`readPages` 为已读页码数组。资料不存在/未发布/不可见分别返回 30001/30001/30002。

#### LEARN-03 获取资料文件地址

- **方法/路径**：`GET /api/v1/app/learn/materials/{materialId}/file`

响应 data：`{ "fileUrl": "https://oss.example.com/m/8801.pdf?sign=...", "expireSec": 3600, "fileType": "pdf" }`。说明：返回短时签名的对象存储地址（MinIO 预签名 URL 或本地存储临时令牌 URL），前端直接预览/播放；可见性校验同 LEARN-02。错误码：30001、30002。

#### LEARN-04 学习进度心跳上报

- **方法/路径**：`POST /api/v1/app/learn/heartbeat`

请求 Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| materialId | long | 是 | 资料 ID |
| eventId | string | 是 | 客户端生成 UUID，幂等去重键（Redis 去重，重复上报直接返回当前进度） |
| positionSec | int | 条件 | 视频当前播放位置（秒） |
| pageNo | int | 条件 | 文档当前页码 |
| readPages | int[] | 否 | 本次新增已读页码 |
| deltaSec | int | 是 | 本次心跳新增有效时长（秒），页面失焦/暂停期间前端不得计时；倍速播放按实际播放时长计 |

响应 data：`{progressPct, effectiveSec, status, serverTime}`。说明：

- 服务端按 `deltaSec` 累计有效时长（防挂机：单次 delta 超过心跳间隔上限 1.5 倍时按上限截断；心跳间隔由参数 `learn.heartbeatSec` 控制，默认 15s）。
- 进度单调推进，乱序/重复上报不后退（幂等）。
- 达到完成阈值时 `status` 变为 `COMPLETED` 并记录 `completedTime`，同时更新关联学习任务进度。

错误码：30001、30002、10007（eventId 重复时以 code=0 返回当前进度，不算错误）。

#### LEARN-05 获取断点续学进度

- **方法/路径**：`GET /api/v1/app/learn/records/{materialId}`

响应 data：`{progressPct, lastPosition, readPages[], effectiveSec, status}`。说明：H5/PC 切换终端时调用本接口恢复断点；无记录返回各字段零值。错误码：30001、30002。

#### LEARN-06 学习任务待办（考生端）

- **方法/路径**：`GET /api/v1/app/learn/tasks`

查询参数：`status`（`TODO`/`DONE`，默认全部）、分页参数。

响应 data（分页）：list 项 `{taskId, title, materialIds[], materials: [{materialId,title,materialType,durationSec,myProgressPct}], requiredSec, deadline, myStatus(TODO/DONE/EXPIRED), completedTime}`。

#### LEARN-07 学习任务分页查询（管理端）

- **方法/路径**：`GET /api/v1/admin/learn/tasks`

查询参数：`keyword`、`status`（进行中/已截止）、分页参数。响应 data（分页）：list 项 `{taskId, title, materialCount, requiredSec, deadline, targetOrgNames[], assigneeCount, doneCount, createdBy, createdTime}`。数据权限：本组织范围内下达的任务。

#### LEARN-08 下达学习任务

- **方法/路径**：`POST /api/v1/admin/learn/tasks`

请求 Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| title | string | 是 | 任务名称 |
| materialIds | long[] | 是 | 指定资料（须已发布） |
| requiredSec | int | 是 | 要求有效学习时长（秒） |
| deadline | string | 是 | 完成期限 `yyyy-MM-dd HH:mm:ss` |
| targetOrgIds | long[] | 是 | 目标单位（含下级，服务端展开为人员快照） |

响应 data：`{ "taskId": 6601, "assigneeCount": 342 }`。说明：任务人员为下达时刻快照；新调入人员不自动补入，可由管理员重新下达。任务完成判定沿用 LEARN-04 心跳逻辑（资料完成 + 累计有效时长 ≥ requiredSec）。错误码：30001（资料未发布）、10001、10004。

---

### 4.6 LSTAT 学习统计

时间范围参数（以下接口通用）：`periodType`（`MONTH`/`QUARTER`/`YEAR`/`CUSTOM`），CUSTOM 时传 `startDate`/`endDate`（`yyyy-MM-dd`）；默认当前月度。

#### LSTAT-01 个人学习统计

- **方法/路径**：`GET /api/v1/app/lstat/personal`

响应 data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| totalEffectiveSec | long | 累计有效学习时长（秒） |
| materialStats | object | `{completed, inProgress, notStarted}` 各状态资料数 |
| categoryDist | object[] | 各分类学习分布 `[{catId, catName, effectiveSec, completedCount}]` |
| taskStats | object | `{total, done, doing, expired, doneRate}` 学习任务完成率 |

错误码：10002。

#### LSTAT-02 单位学习汇总

- **方法/路径**：`GET /api/v1/admin/lstat/org-summary`

查询参数：`orgId`（默认数据权限根节点，返回其直属下级逐单位汇总）、时间范围参数。

响应 data：`{orgId, orgName, children: [{orgId, orgName, userCount, avgEffectiveSec, materialDoneRate, taskDoneRate, belowCount}], hasSubLevel}`。说明：汇总按组织路径递归聚合到各直属下级单位；`hasSubLevel` 指示可继续下钻。数据权限：越级查询返回 10004。错误码：60001。

#### LSTAT-03 单位下钻明细

- **方法/路径**：`GET /api/v1/admin/lstat/org-summary/{orgId}`

查询参数：时间范围参数、`detailType`（`SUB_ORG` 下级单位 / `USER` 人员明细）、分页参数（USER 时生效）。

响应 data：SUB_ORG 同 LSTAT-02 结构；USER 为分页 list `{userId, name, jobNo, effectiveSec, completedCount, inProgressCount, taskDoneRate, isBelowStandard}`。说明：`isBelowStandard` 依据参数 `learn.standardSec`（单位周期达标时长）判定，即"未达标人员名单"。错误码：10004、60001。

#### LSTAT-04 学习排行

- **方法/路径**：`GET /api/v1/admin/lstat/ranking`

查询参数：`rankType`（`ORG`/`USER`）、`orgId`（限定范围，默认数据权限根）、时间范围参数、`topN`（默认 20，最大 100）。

响应 data：`[{rank, orgId?, orgName?, userId?, name?, orgName?, effectiveSec, completedCount}]`。错误码：60001、10004。

#### LSTAT-05 学习统计导出

- **方法/路径**：`GET /api/v1/admin/lstat/export`

查询参数：`exportType`（`ORG_SUMMARY`/`USER_DETAIL`/`BELOW_STANDARD`）、`orgId`、时间范围参数。

响应 data：`{ "taskId": "exp_20260814_001", "fileUrl": "https://.../lstat.xlsx?sign=...", "expireSec": 3600 }`。说明：同步生成（≤5 万行），返回带签名下载地址；导出内容按当前管理员数据权限过滤；导出动作写操作日志。错误码：60001、60002、10004。

---

### 4.7 QUES 题库

题型编码：`SINGLE`（单选）、`MULTI`（多选）、`JUDGE`（判断）、`FILL`（填空）、`ESSAY`（简答）；**本期正式考试与日常练习仅启用 `SINGLE`**（V1.1 任务书），其余题型为架构预留。题目状态：`DRAFT`（草稿）→ `PENDING_AUDIT`（待审核）→ `ENABLED`（启用）→ `DISABLED`（停用）；免审由参数 `ques.auditRequired` 控制。难度编码（V1.1 收敛为两级）：`BASIC`（基础）、`ADVANCED`（进阶）；存量 `EASY/MEDIUM/HARD` 由服务端映射（EASY→BASIC，MEDIUM/HARD→ADVANCED）。数据权限：题库按全集团共享（题库不涉组织数据隔离），但创建/审核动作记录操作人单位；普通管理端均可读。

**制度法规分类**（V1.1）：题库一级分类固定为任务书界定的 10 部制度法规（初始化落库，管理员可调整显示名但不宜删改）：

1. 《纪检监察机关处理检举控告工作规则》；2. 《中国共产党纪律处分条例》；3. 《中华人民共和国公职人员政务处分法》；4. 《中华人民共和国监察法》；5. 《中华人民共和国监察法实施条例》；6. 《中国共产党问责条例》；7. 《中国共产党党内监督条例》；8. 《中国共产党纪律检查机关监督执纪工作规则》；9. 《中国共产党廉洁自律准则》；10. 《国有企业领导人员廉洁从业若干规定》。

#### QUES-01 题库分类树

- **方法/路径**：`GET /api/v1/admin/ques/banks/tree`
- 响应 data：`[{bankId, parentId, bankName, sortNo, itemCount, children[]}]`。

#### QUES-02 新增题库分类

- **方法/路径**：`POST /api/v1/admin/ques/banks`
- 请求 Body：`{ "parentId": 0, "bankName": "党纪处分条例", "sortNo": 1 }`。响应 data：`{ "bankId": 21 }`。错误码：10001。

#### QUES-03 编辑题库分类

- **方法/路径**：`PUT /api/v1/admin/ques/banks/{bankId}`
- 请求 Body：`{ "bankName": "...", "sortNo": 2 }`。错误码：10005。

#### QUES-04 删除题库分类

- **方法/路径**：`DELETE /api/v1/admin/ques/banks/{bankId}`
- 说明：存在子分类或题目时返回 20004。

#### QUES-05 题目分页查询

- **方法/路径**：`GET /api/v1/admin/ques/items`

查询参数：`bankId`、`quesType`、`difficulty`（`BASIC`/`ADVANCED`，V1.1 两级）、`status`、`keyword`（题干模糊）、`tag`、`sourceLaw`（来源制度名称，V1.1）、`knowledgePoint`（知识点，V1.1）、分页参数。

响应 data（分页）：list 项 `{itemId, bankId, bankName, quesType, stemText(纯文本摘要), difficulty, sourceLaw, sourceChapter, knowledgePoint, scoreSuggest, tags[], status, useCount, correctRate, createdTime}`。

#### QUES-06 新增题目

- **方法/路径**：`POST /api/v1/admin/ques/items`

请求 Body（按题型取不同字段）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| bankId | long | 是 | 所属题库分类 |
| quesType | string | 是 | SINGLE/MULTI/JUDGE/FILL/ESSAY |
| stem | string | 是 | 题干，富文本 HTML（图片为已上传的 URL，图片上传走第 5 章通用图片接口） |
| options | object[] | 条件 | 选择题型必填：`[{optKey:"A", content:"...", isAnswer:true}]`；判断题固定 `[{optKey:"T"},{optKey:"F"}]` 可省略 |
| answer | object | 条件 | 非选择题必填：判断题 `{"value":"T"}`；填空题 `{"blanks":[{"no":1,"answers":["答案1","等价答案"],"ordered":false}]}`；简答题 `{"reference":"参考答案","keyPoints":["要点1","要点2"]}` |
| analysis | string | 否 | 解析（富文本） |
| difficulty | string | 是 | `BASIC`（基础）/ `ADVANCED`（进阶）（V1.1 两级；传存量 EASY/MEDIUM/HARD 由服务端映射） |
| sourceLaw | string | 是 | 来源制度（V1.1；须为 10 部制度法规之一或与本表一致的全称，默认取所属一级分类名） |
| sourceChapter | string | 是 | 对应章节（V1.1，如"第三章 纪律处分运用规则"） |
| knowledgePoint | string | 否 | 规范知识点词（V1.1，如"检举控告受理"） |
| tags | string[] | 否 | 知识点/分类标签（自由标签，与 knowledgePoint 并存） |
| scoreSuggest | number | 否 | 分值建议 |
| source | string | 否 | 来源 |
| fillConfig | object | 否 | 填空判分配置 `{ignoreCase:true, ignoreWidth:true, blankOrdered:false}`（忽略大小写/全半角/多空乱序） |

响应 data：`{ "itemId": 90001, "status": "PENDING_AUDIT", "dupHints": [] }`。说明：保存前自动执行查重，命中相似题时 `dupHints` 返回疑似列表（仅提示，不阻断；如前端要求先确认，可先调 QUES-12）。错误码：10001、40002（服务端强制阻断模式时）。

#### QUES-07 题目详情

- **方法/路径**：`GET /api/v1/admin/ques/items/{itemId}`
- 响应 data：QUES-06 全部字段 + `{status, useCount, correctRate, auditBy, auditTime, createdBy, createdTime, updatedTime}`。错误码：10005。

#### QUES-08 编辑题目

- **方法/路径**：`PUT /api/v1/admin/ques/items/{itemId}`
- 说明：Body 同 QUES-06；已被封存试卷引用的题目修改后不影响历史试卷（PaperQuestion 快照保存题目内容），仅影响后续组卷；修改后需重新审核（审核开关开启时回到 PENDING_AUDIT）。错误码：10005、40001。

#### QUES-09 删除/停用题目

- **方法/路径**：`DELETE /api/v1/admin/ques/items/{itemId}`

查询参数：`hard=false` 默认停用（DISABLED），`hard=true` 物理删除（仅未被任何试卷引用且未被使用时允许）。错误码：10005、40004。

#### QUES-10 批量导入题目

- **方法/路径**：`POST /api/v1/admin/ques/items/import`，`multipart/form-data`

表单字段：`file`（.xlsx/.docx，≤20MB）、`bankId`（默认归属分类，模板内可逐行覆盖）。响应 data 同 USER-07 结构（totalCount/successCount/failCount/failures），failures 的 `reason` 含"题型无法识别/选项缺失/答案未配置/来源制度不匹配/章节缺失/难度非法/疑似重复"等（V1.1 模板列与校验增强）。错误码：10008、10009、20005。

#### QUES-11 下载题目导入模板

- **方法/路径**：`GET /api/v1/admin/ques/items/import-template`
- 说明：返回 `.xlsx` 模板（按题型分 sheet，含填写示例）。V1.1 模板列：题型、题干、选项 A~F、正确答案、解析、来源制度（下拉限定 10 部制度法规）、章节、知识点、难度（基础/进阶）、标签、所属分类编码（可覆盖表单 bankId）。

#### QUES-12 题干相似度查重

- **方法/路径**：`POST /api/v1/admin/ques/items/dup-check`

请求 Body：`{ "stem": "题干文本", "quesType": "SINGLE", "excludeItemId": 0 }`。

响应 data：`[{ "itemId": 90012, "similarity": 0.92, "stemText": "相似题干摘要", "status": "ENABLED" }]`。说明：服务端基于题干文本相似度（阈值默认 0.85，参数可调）返回 TOP 5 疑似重复；纯提示型接口，不阻断。

#### QUES-13 题目审核

- **方法/路径**：`POST /api/v1/admin/ques/items/{itemId}/audit`

请求 Body：`{ "action": "ENABLE", "comment": "" }`（`ENABLE` 启用 / `REJECT` 驳回至草稿 / `DISABLE` 停用）。说明：仅 `ENABLED` 题目可用于组卷（否则 PAPER-06 返回 40007）；审核动作写操作日志。错误码：40001、10005。

---

### 4.8 PAPER 试卷

试卷类型：`FIXED`（固定卷）、`RANDOM`（随机策略卷）。试卷状态：`EDITING`（编辑中）→ `READY`（待使用）→ `SEALED`（已封存，被考试引用后自动或手动封存，不可再改，仅可复制新版本）。等级规则（GradeRule）随试卷快照保存，规则变更仅影响之后发布的成绩（FR-STAT-04）。

#### PAPER-01 试卷分页查询

- **方法/路径**：`GET /api/v1/admin/papers`

查询参数：`keyword`、`paperType`、`status`、分页参数。响应 data（分页）：list 项 `{paperId, paperName, paperType, totalScore, passScore, quesCount, status, useCount, createdBy, createdTime}`。

#### PAPER-02 创建试卷

- **方法/路径**：`POST /api/v1/admin/papers`

请求 Body（固定卷）：

```json
{
  "paperName": "2026 年纪检监察法规统考卷 A",
  "paperType": "FIXED",
  "totalScore": 100,
  "passScore": 60,
  "multiScoreRule": "HALF_ON_MISS",
  "questions": [
    { "itemId": 90001, "score": 2, "seq": 1 },
    { "itemId": 90012, "score": 5, "seq": 21 }
  ],
  "gradeRules": [
    { "gradeName": "优", "minScore": 90, "maxScore": 100 },
    { "gradeName": "良", "minScore": 80, "maxScore": 90 },
    { "gradeName": "中", "minScore": 60, "maxScore": 80 },
    { "gradeName": "差", "minScore": 0,  "maxScore": 60 }
  ]
}
```

请求 Body（随机策略卷）：

```json
{
  "paperName": "2026 年随机抽题卷",
  "paperType": "RANDOM",
  "totalScore": 100,
  "passScore": 60,
  "multiScoreRule": "HALF_ON_MISS",
  "samePaperForAll": false,
  "randomStrategy": [
    { "quesType": "SINGLE", "bankId": 21, "difficulty": "EASY",  "count": 8, "score": 2 },
    { "quesType": "SINGLE", "bankId": 21, "difficulty": "MEDIUM","count": 8, "score": 2 },
    { "quesType": "SINGLE", "bankId": 21, "difficulty": "HARD",  "count": 4, "score": 2 },
    { "quesType": "ESSAY",  "bankId": 22, "difficulty": null,   "count": 2, "score": 30 }
  ],
  "gradeRules": [ "同上结构" ]
}
```

参数字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| paperName / paperType | string | 是 | 名称 / FIXED、RANDOM |
| totalScore / passScore | number | 是 | 卷面总分（默认 100）/ 及格线（默认 60） |
| multiScoreRule | string | 否 | 多选判分规则：`ALL_OR_NOTHING`（全对得分）/ `HALF_ON_MISS`（漏选得半分，错选不得分）/ `NONE_ON_MISS`（错选、漏选均不得分），默认 `HALF_ON_MISS`；对应数据库 `t_paper.multi_judge_rule` 三值规则 1/2/3 |
| questions | object[] | 条件 | FIXED 必填；服务端校验各题为 ENABLED、分值合计 = totalScore，否则 40003/40007 |
| randomStrategy | object[] | 条件 | RANDOM 必填；`bankId/difficulty` 可为 null 表示不限；服务端试抽校验题量，不足返回 40006 |
| samePaperForAll | boolean | 否 | RANDOM 专用：true=全考生同卷（首次抽题后缓存），false=一人一卷（进入考试时生成） |
| gradeRules | object[] | 否 | 不传则使用系统默认等级（优[90,100]/良[80,90)/中[60,80)/差[0,60)） |

响应 data：`{ "paperId": 501 }`。错误码：10001、40003、40005、40006、40007。

#### PAPER-03 试卷详情

- **方法/路径**：`GET /api/v1/admin/papers/{paperId}`
- 响应 data：创建参数全量 + 固定卷题目明细（含题干、选项、答案、解析——管理端不脱敏）+ `{status, version, sealedTime, usedByExamIds[]}`。错误码：10005、10004。

#### PAPER-04 编辑试卷基础信息

- **方法/路径**：`PUT /api/v1/admin/papers/{paperId}`
- 说明：可改名称、总分、及格线、多选判分规则；仅 `EDITING` 状态可改，已封存返回 40004。错误码：10005、40004。

#### PAPER-05 删除试卷

- **方法/路径**：`DELETE /api/v1/admin/papers/{paperId}`
- 说明：仅未被任何考试引用（useCount=0）可删，否则 40004。

#### PAPER-06 固定卷选题与分值设置

- **方法/路径**：`PUT /api/v1/admin/papers/{paperId}/questions`

请求 Body：`{ "questions": [{ "itemId": 90001, "score": 2, "seq": 1 }] }`（全量替换）。说明：校验总分一致（40003）、题目启用（40007）；幂等（全量替换语义）。错误码：40004、40003、40007。

#### PAPER-07 试卷预览

- **方法/路径**：`GET /api/v1/admin/papers/{paperId}/preview`
- 响应 data：按考试界面结构返回全卷（含答案与解析，仅管理端）：`{paperName, totalScore, passScore, sections: [{quesType, questions: [{seq, itemId, stem, options, answer, analysis, score}]}]}`；RANDOM 卷按策略试抽一份样卷返回并标注 `sample: true`。错误码：10005、40006。

#### PAPER-08 复制试卷

- **方法/路径**：`POST /api/v1/admin/papers/{paperId}/copy`

请求 Body：`{ "paperName": "xxx 卷 B" }`。响应 data：`{ "paperId": 502 }`。说明：深拷贝题目快照/策略/等级规则，新卷状态 EDITING、version 独立；封存卷的常规演进路径。错误码：10005。

#### PAPER-09 封存试卷

- **方法/路径**：`PUT /api/v1/admin/papers/{paperId}/seal`
- 说明：状态 → SEALED；考试场次关联试卷时服务端自动封存，本接口供手动提前封存；幂等。错误码：10005、40004（重复封存返回成功）。

#### PAPER-10 配置等级规则

- **方法/路径**：`PUT /api/v1/admin/papers/{paperId}/grade-rules`

请求 Body：`{ "gradeRules": [{ "gradeName": "优", "minScore": 90, "maxScore": 100 }] }`。说明：校验区间连续且完整覆盖 [0, totalScore]，左闭右开（最末段右闭），否则 40005；封存卷不可改（40004）。错误码：10001、40004、40005。

---

### 4.9 EXAM 考试场次与下发

场次状态机：`NOT_STARTED（未开始）→ IN_PROGRESS（进行中）→ ENDED（已结束）→ SCORED_PUBLISHED（已发布成绩）`；另有 `CANCELED`（已取消）。数据权限：管理员仅能管理本组织范围内创建的场次；总部可查全集团场次（FR-EXAM-05 上级可查下级组织的考试）。

#### EXAM-01 考试场次分页查询

- **方法/路径**：`GET /api/v1/admin/exams`

查询参数：`keyword`、`status`、`startDate/endDate`（按开考时间）、分页参数。响应 data（分页）：list 项 `{examId, examName, paperId, paperName, paperType, startTime, endTime, durationMin, status, candidateCount, attendedCount, createdOrgName, createdTime}`。

#### EXAM-02 创建考试场次

- **方法/路径**：`POST /api/v1/admin/exams`

请求 Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| examName | string | 是 | 考试名称 |
| paperId | long | 是 | 关联试卷（固定卷或随机策略卷；关联后试卷自动封存） |
| startTime | string | 是 | 时间窗口开始 `yyyy-MM-dd HH:mm:ss` |
| endTime | string | 是 | 时间窗口结束；截止后不可进入，未交卷强制收卷 |
| durationMin | int | 是 | 考试时长（分钟），从考生进入起算，且不超过窗口结束时间；V1.1 本期默认 30 分钟（系统参数 `exam.defaultDurationMin`），管理员可按场次调整 |
| lateEnterMin | int | 否 | 允许迟到进入时限（分钟，相对 startTime），0 表示不允许迟到 |
| switchScreenLimit | int | 否 | 切屏次数上限，默认取系统参数（如 3） |
| allowEarlySubmit | boolean | 否 | 是否允许提前交卷，默认 true |
| earlySubmitAfterMin | int | 否 | 开考多少分钟后允许提前交卷，默认 0 |
| shuffleQuestion | boolean | 否 | 题目顺序乱序，默认 true（V1.1 本期正式考试强制开启，仅演练场次可关闭） |
| shuffleOption | boolean | 否 | 选项顺序乱序（选择题型），默认 true（V1.1 本期正式考试强制开启；乱序后顺序固化于答卷快照，判分按原始选项 ID 映射还原） |
| notice | string | 否 | 考试须知富文本 |

响应 data：`{ "examId": 3001, "status": "NOT_STARTED" }`。错误码：10001（时间窗口非法）、10005（试卷不存在）、40004（试卷不可用）、10004。

> **V1.1 本期默认考试模板**：仅单选题、20 题 × 5 分 = 100 分；创建试卷（PAPER-02）时按 `randomStrategy: [{quesType:"SINGLE", bankId:null, difficulty:null, count:20, score:5}]` 预填即可，可限定单部或多部制度法规分类出题（bankId 取对应法规一级分类）；题序与选项顺序双乱序、交卷即时自动评分出分（纯客观卷）、考后可查看答题回顾（每题正误与解析，TAKE-09）。

#### EXAM-03 场次详情

- **方法/路径**：`GET /api/v1/admin/exams/{examId}`
- 响应 data：创建参数全量 + `{status, candidateCount, attendedCount, submittedCount, avgScore, notifySentTime, retakeConfig}`。错误码：10005、10004。

#### EXAM-04 编辑场次

- **方法/路径**：`PUT /api/v1/admin/exams/{examId}`
- 说明：仅 `NOT_STARTED` 且未下发名单时可全量编辑；已下发后仅可改考试须知与规则说明。错误码：50007、10004。

#### EXAM-05 删除场次

- **方法/路径**：`DELETE /api/v1/admin/exams/{examId}`
- 说明：仅 `NOT_STARTED` 且考生名单为空时可删除；已下发的场次只能取消（走 EXAM-06 `CANCEL`）。错误码：50007。

#### EXAM-06 场次状态流转

- **方法/路径**：`PUT /api/v1/admin/exams/{examId}/status`

请求 Body：`{ "action": "START" }`。动作与流转：`START`（手动提前开考，NOT_STARTED→IN_PROGRESS）/ `END`（提前结束，IN_PROGRESS→ENDED，触发未交卷强制收卷）/ `CANCEL`（取消，仅未开始）。说明：到达 startTime/endTime 由服务端定时任务自动流转，本接口供人工干预；每次流转写操作日志。错误码：50007、10004。

#### EXAM-07 按组织范围生成考生名单快照（考题下发）

- **方法/路径**：`POST /api/v1/admin/exams/{examId}/candidates/scope`

请求 Body：

```json
{
  "scopes": [
    { "orgId": 12, "includeSub": true },
    { "orgId": 305, "includeSub": false }
  ],
  "userGroups": [1, 3]
}
```

响应 data：`{ "examId": 3001, "candidateCount": 486, "snapshotTime": "2026-08-20 10:00:00" }`。说明：

- 服务端按所选组织范围（含/不含下级）展开为在册且状态正常的考生集合，写入 ExamCandidate 快照；`userGroups`（V1.1，分组标签：1 派驻组 / 2 子企业纪委 / 3 纪检委员 / 9 其他，见 GROUP 模块）与 `scopes` 并集去重展开；重复调用为**全量重建**（幂等），重建后名单以最后一次为准；已在考/已交卷的场次禁止重建（50007）。
- 快照记录考生当时单位（orgSnapshot），后续调岗不影响本场归集（FR-ORG-03）。
- 名单生成后可继续 EXAM-09/10 追加/剔除，再调 EXAM-11 推送通知。

错误码：50007、10004（所选单位超出数据权限）、10001。

#### EXAM-08 考生名单分页查询

- **方法/路径**：`GET /api/v1/admin/exams/{examId}/candidates`

查询参数：`keyword`、`sheetStatus`（`NOT_ENTERED`/`IN_EXAM`/`SUBMITTED`/`FORCED`/`ABSENT`）、分页参数。响应 data（分页）：list 项 `{candidateId, userId, name, jobNo, orgNameSnapshot, sheetId, sheetStatus, enterTime, submitTime, objectiveScore, totalScore, grade}`。

#### EXAM-09 追加考生

- **方法/路径**：`POST /api/v1/admin/exams/{examId}/candidates`

请求 Body：`{ "userIds": [10021, 10022] }`。响应 data：`{ "addedCount": 2, "skipped": [{ "userId": 10022, "reason": "已在名单内" }] }`。说明：服务端校验人员在数据权限范围内且状态正常；唯一约束（examId+userId）兜底防重。错误码：50007、10004。

#### EXAM-10 剔除考生

- **方法/路径**：`DELETE /api/v1/admin/exams/{examId}/candidates`

请求 Body：`{ "userIds": [10021] }`。说明：已进入考试（存在答卷）的考生不可剔除，只能对其单独强制收卷；响应同 EXAM-09 结构（removedCount/skipped）。错误码：50007。

#### EXAM-11 推送考试通知

- **方法/路径**：`POST /api/v1/admin/exams/{examId}/notify`

请求 Body：`{ "message": "请于 8 月 25 日 9:00-11:00 参加统考", "channels": ["INBOX", "WX_SUB"] }`。说明：`channels` 支持 `INBOX`（站内消息）与 `WX_SUB`（微信小程序订阅消息，V1.1；仅对"已绑定微信且已订阅"的考生下发，其余考生自动跳过并记入推送记录），`SMS`/`WECOM` 为预留枚举（未配置时忽略）；推送给当前名单全部考生；重复推送由前端确认，服务端不限制。响应 data：`{ "sentCount": 486, "wxPushedCount": 312, "wxSkippedCount": 174 }`（V1.1 增补微信通道统计）。错误码：50007（名单为空）。

#### EXAM-12 延长考试时间

- **方法/路径**：`PUT /api/v1/admin/exams/{examId}/extend`

请求 Body：`{ "extendMin": 30 }`。说明：进行中场次的窗口 endTime 顺延；在考考生的剩余时间同步延长（以服务端时间为准，考生端下次校时/心跳响应中的 `remainingSec` 自动反映）；写操作日志。错误码：50007、10001。

#### EXAM-13 强制收卷

- **方法/路径**：`POST /api/v1/admin/exams/{examId}/force-submit`

请求 Body：`{ "userIds": [10021], "reason": "异常处理" }`（`userIds` 为空表示对在考考生全部收卷）。响应 data：`{ "forcedCount": 1 }`。说明：被收卷答卷标记 `FORCED` 并进入判分流程；考生端下次心跳收到 `submitStatus=FORCED` 后跳转交卷完成页；写考试异常日志。错误码：50007。

#### EXAM-14 补考设置

- **方法/路径**：`PUT /api/v1/admin/exams/{examId}/retake`

请求 Body：

```json
{ "retakeStartTime": "2026-08-28 09:00:00", "retakeEndTime": "2026-08-28 11:00:00", "userIds": [10031, 10032] }
```

说明：针对缺考/不及格考生设置补考窗口；补考考生在窗口内通过考生端进入，生成新答卷（AnswerSheet 标记 `retakeSeq=1`）；仅已结束场次可设置。错误码：50007、10001。

---

### 4.10 TAKE 在线考试（考生端）

考试安全约定在本模块集中体现：试卷接口脱敏（不下发标准答案与解析）、答案批量/增量保存、交卷幂等、倒计时以服务端时间为准并配校时接口、单终端在线控制。

#### TAKE-01 待考/历史考试列表

- **方法/路径**：`GET /api/v1/app/exams`

查询参数：`type`（`TODO` 待考 / `HISTORY` 历史，默认 TODO）、分页参数。

响应 data（分页）：list 项 `{examId, examName, startTime, endTime, durationMin, status, mySheetStatus(NOT_ENTERED/IN_EXAM/SUBMITTED/FORCED/ABSENT), canEnter, cannotEnterReason, scorePublished}`。说明：仅返回本人在名单内的场次；`canEnter` 由服务端综合时间窗口、迟到时限、答卷状态判定。

#### TAKE-02 进入考试（须知确认）

- **方法/路径**：`POST /api/v1/app/exams/{examId}/enter`

请求 Body：`{ "noticeConfirmed": true, "deviceId": "h5-9f3c2a", "terminal": "H5" }`。

响应 data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| sheetId | long | 答卷 ID（断线续考凭据） |
| serverTime | string | 服务端当前时间 |
| deadlineTime | string | 本场答卷截止（进入时间+时长与窗口结束的较小者；补考按补考窗口） |
| remainingSec | int | 剩余秒数 |
| resumeFrom | string | `NEW` 新答卷 / `RESUME` 断线恢复（前端随后调 TAKE-08 拉已答内容） |
| examRules | object | `{durationMin, switchScreenLimit, allowEarlySubmit, earlySubmitAfterMin, notice}`（V1.1 本期 durationMin 默认 30） |

说明：

- 校验：在时间窗口内（50001）、在名单内（50002）、未超迟到时限（50008）、未交卷（50003）。
- 单终端控制：该账号已有进行中的其他终端会话时，按参数 `exam.multiTerminal`（默认 `KICK_OLD`）踢掉旧会话（旧终端收到 10010）或拒绝新终端（50005）。
- RANDOM 一人一卷在此刻按策略抽题生成并快照至 AnswerSheet；RANDOM 同卷复用预生成试卷。
- V1.1 本期正式考试：题序与选项顺序双乱序在快照生成时完成并固化（同一考生任何终端顺序一致），判分按原始选项 ID 映射还原比对。

错误码：50001、50002、50003、50005、50008。

#### TAKE-03 服务端校时

- **方法/路径**：`GET /api/v1/app/time`

响应 data：`{ "serverTime": "2026-08-25 09:30:15", "epochMillis": 1787655015000 }`。说明：前端在进入考试与每次答案保存响应中同步校时；倒计时本地渲染 = 进入时 `deadlineTime` − 本地已流逝时间（以 performance.now 计，防用户改系统时间），并周期性以本接口校准。

#### TAKE-04 拉取试卷（脱敏）

- **方法/路径**：`GET /api/v1/app/exams/{examId}/paper`

查询参数：`sheetId`（必填，用于还原本人乱序顺序与已抽题目）。

响应 data：

```json
{
  "code": 0, "message": "success",
  "data": {
    "examId": 3001, "sheetId": 77001,
    "paperName": "2026 年纪检监察法规统考卷 A",
    "totalScore": 100, "durationMin": 30,
    "remainingSec": 1620, "serverTime": "2026-08-25 09:36:00",
    "sections": [
      { "quesType": "SINGLE", "typeName": "单选题", "scorePerQues": 5,
        "questions": [
          { "seq": 1, "sheetQuesId": 555001, "stem": "《中国共产党纪律处分条例》规定……",
            "options": [ { "optKey": "A", "content": "警告" }, { "optKey": "B", "content": "严重警告" } ] }
        ] }
    ]
  }
}
```

安全说明（必须遵守）：

- 响应中**绝不含**标准答案（answer/isAnswer）、解析（analysis）、评分要点等字段；填空题不含 blanks 答案，简答题不含 reference/keyPoints。
- `sheetQuesId` 为答卷题目快照 ID（AnswerDetail 主键维度），不是题库 itemId，防止考生端反推题库。
- 乱序由服务端在生成答卷快照时完成，同一考生多次拉取顺序一致；选项乱序时 `optKey` 已按乱序后重排。
- 题目图片等富文本资源经签名 URL 下发。

错误码：50001、50002、50003、50006。

#### TAKE-05 批量/增量保存答案（心跳）

- **方法/路径**：`PUT /api/v1/app/sheets/{sheetId}/answers`

请求 Body：

```json
{
  "clientBatchId": "uuid-9f3c-0017",
  "answers": [
    { "sheetQuesId": 555001, "answer": { "selected": ["A"] } },
    { "sheetQuesId": 555021, "answer": { "blanks": ["答案一", "答案二"] } },
    { "sheetQuesId": 555033, "answer": { "text": "简答作答内容……" } }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| clientBatchId | string | 是 | 客户端批次 UUID，服务端 Redis 去重，弱网重发安全 |
| answers[].sheetQuesId | long | 是 | 答卷题目快照 ID |
| answers[].answer.selected | string[] | 条件 | 单选/多选/判断（判断用 ["T"]/["F"]） |
| answers[].answer.blanks | string[] | 条件 | 填空，按空序号 |
| answers[].answer.text | string | 条件 | 简答文本 |

响应 data：`{ "savedCount": 3, "serverTime": "...", "remainingSec": 6700, "submitStatus": "IN_EXAM" }`。说明：

- 幂等：按 sheetQuesId upsert；同一 `clientBatchId` 重复提交直接返回成功不重复写。
- 仅保存未交卷答卷；`submitStatus` 用于服务端反向通知（被强制收卷/时间到自动交卷时前端据以跳转）。
- 超过截止时间到达的请求：仍接收保存（宽松窗口 30s，网络延迟容忍）但不改变已到点自动交卷的结果。
- 性能要求：P95 ≤ 500ms（5000 人同场），实现上写 Redis 暂存 + 异步批量落库。

错误码：50003、50006、10007。

#### TAKE-06 切屏/离开页面上报

- **方法/路径**：`POST /api/v1/app/sheets/{sheetId}/screen-events`

请求 Body：`{ "eventType": "LEAVE", "occurredAt": "2026-08-25 09:40:02" }`（`LEAVE` 切出 / `RETURN` 返回）。

响应 data：`{ "leaveCount": 2, "limit": 3, "action": "WARN" }`。说明：服务端累计切屏次数；`action`：`WARN`（未超限，前端弹预警）/ `FORCE_SUBMIT`（超限，服务端已自动交卷，前端立即跳交卷页）/ `MARK_ABNORMAL`（按场次配置仅标记异常）；超限处理写考试异常日志。错误码：50003、50006。

#### TAKE-07 交卷

- **方法/路径**：`POST /api/v1/app/sheets/{sheetId}/submit`

请求 Body：`{ "confirmUnanswered": true, "clientSubmitId": "uuid-final-0001" }`。

响应 data：

```json
{
  "code": 0, "message": "success",
  "data": {
    "sheetId": 77001, "submitTime": "2026-08-25 11:00:12",
    "unansweredCount": 0, "objectiveAutoScored": true,
    "scoreVisible": false, "message": "含主观题，成绩待阅卷发布后可查询"
  }
}
```

说明：

- **幂等**：重复交卷（网络重试/双击/刷新重发）返回首次交卷结果（code=0），不产生二次副作用；`clientSubmitId` 用于去重日志关联。
- 交卷触发客观题自动判分（单选/判断按标准答案；多选按试卷 multiScoreRule；填空按 fillConfig 匹配，忽略大小写/全半角、多空乱序）；纯客观卷 `scoreVisible=true` 时 TAKE-09 可立即查分。**V1.1 本期正式考试为纯单选卷，交卷即时出分**，响应可直接携带成绩摘要 `{myScore, passed}`（scoreVisible=true 时下发）。
- 交卷后 TAKE-04/05/06 均返回 50003。

错误码：50003（重复提交但首提已被拒绝类场景）、50006。

#### TAKE-08 答卷状态查询（断线续考）

- **方法/路径**：`GET /api/v1/app/sheets/{sheetId}`

响应 data：`{sheetId, examId, submitStatus(IN_EXAM/SUBMITTED/FORCED), serverTime, remainingSec, deadlineTime, answers: [{sheetQuesId, answer}], leaveCount, switchScreenLimit}`。说明：断网/换端恢复时调用，一次性取回已保存答案与权威剩余时间；考试时长不因断线延长（FR-TAKE-04）。错误码：50006、10010。

#### TAKE-09 成绩查询

- **方法/路径**：`GET /api/v1/app/exams/{examId}/result`

响应 data：

```json
{
  "code": 0, "message": "success",
  "data": {
    "examId": 3001, "examName": "...", "scorePublished": true,
    "totalScore": 100, "myScore": 85, "grade": "良", "passed": true,
    "objectiveScore": 85, "subjectiveScore": 0,
    "durationSec": 1260,
    "showAnalysis": true,
    "details": [
      { "seq": 1, "quesType": "SINGLE", "stem": "...", "options": [], "myAnswer": ["A"],
        "correctAnswer": ["A"], "isCorrect": true, "gainedScore": 5, "analysis": "...",
        "sourceLaw": "《中国共产党纪律处分条例》", "sourceChapter": "第三章", "knowledgePoint": "纪律处分运用规则" }
    ]
  }
}
```

说明：

- 成绩未发布（含主观题待阅卷或管理员未确认发布）返回 `{"scorePublished": false}` 或 50009（按前端轮询习惯，建议用 scorePublished=false 的 200 响应）。V1.1 本期纯单选卷交卷即出分，发布后本接口即"考后答题回顾"：逐题返回正误、得分与解析。
- 发布前本接口不下发任何答案/解析（NFR-04）；发布后是否展示解析 `showAnalysis` 由场次参数控制（本期默认开启）。
- `details` 按本人答卷快照中乱序后的题序/选项序返回（与答题时所见一致）；`durationSec` 为本场答题用时（秒），用于同分排名口径。
- 含补考记录时返回最新一次有效成绩及 `retakeSeq`。

错误码：50002、50009。

---

### 4.11 MARK 判分与阅卷

客观题在交卷时自动判分（见 TAKE-07）。主观题以"阅卷任务（MarkTask）"为单元分派：任务 = 场次 × 题目（或题目组），含待阅答卷集合。阅卷教师角色走 `/admin/mark/` 前缀，但数据范围限定为分配给本人的任务。

#### MARK-01 阅卷任务列表

- **方法/路径**：`GET /api/v1/admin/mark/tasks`

查询参数：`examId`、`status`（`PENDING` 待领取 / `DOING` 阅卷中 / `DONE` 完成）、分页参数。响应 data（分页）：list 项 `{taskId, examId, examName, sheetQuesSeq, quesType, stemText, totalSheets, markedSheets, myRole(OWNER/UNCLAIMED), status, deadline}`。说明：阅卷教师仅见分派给本人或可领取的任务；管理员见全部（监控进度）。

#### MARK-02 领取阅卷任务

- **方法/路径**：`POST /api/v1/admin/mark/tasks/{taskId}/claim`
- 响应 data：`{ "taskId": 4401, "status": "DOING" }`。说明：乐观锁防并发重复领取，已被领取返回 50010；领取动作留痕。错误码：50010、10005。

#### MARK-03 待阅答卷题目列表

- **方法/路径**：`GET /api/v1/admin/mark/tasks/{taskId}/items`

查询参数：`marked`（true/false 过滤）、分页参数。响应 data（分页）：list 项：

```json
{
  "detailId": 990001, "sheetId": 77001, "anonymousNo": "卷-038",
  "stem": "简答题题干……", "reference": "参考答案……", "keyPoints": ["要点1", "要点2"],
  "fullScore": 30, "candidateAnswer": "考生作答文本……",
  "marked": false, "myScore": null, "myComment": null
}
```

说明：阅卷视图匿名化（`anonymousNo`，不展示考生姓名/单位，防人情分；双评模式下对一评教师分数也互不可见）。错误码：50010、10004。

#### MARK-04 提交评分

- **方法/路径**：`POST /api/v1/admin/mark/scores`

请求 Body：

```json
{ "taskId": 4401, "scores": [ { "detailId": 990001, "score": 24, "comment": "要点覆盖不全" } ] }
```

说明：批量提交；分值须在 [0, fullScore] 内；按 detailId upsert（重复提交覆盖本人上次评分，任务完成前允许改分）；任务全部题目评完后状态自动 → DONE。响应 data：`{ "savedCount": 1, "taskStatus": "DOING" }`。错误码：10001（分值越界）、50010。

#### MARK-05 人工复核队列

- **方法/路径**：`GET /api/v1/admin/mark/review-queue`

查询参数：`examId`、`reviewType`（`FILL_SIMILAR` 填空疑似命中 / `DOUBLE_MARK_GAP` 双评分差仲裁）、分页参数。响应 data（分页）：list 项 `{reviewId, reviewType, examName, sheetId, detailId, quesType, stemText, candidateAnswer, standardAnswer, autoScore, firstScore, secondScore, gap}`。说明：填空未命中但相似度高的答案（FR-MARK-03）、双评分差超阈值（FR-MARK-02 双评仲裁）进入本队列。

#### MARK-06 提交复核结论

- **方法/路径**：`POST /api/v1/admin/mark/review`

请求 Body：`{ "reviewId": 88001, "finalScore": 2, "comment": "表述等价，判对" }`。说明：复核结论覆盖原（自动/双评）得分并留痕；重复提交按 reviewId 幂等覆盖。错误码：10001、10005、50010。

#### MARK-07 成绩合成并批量发布

- **方法/路径**：`POST /api/v1/admin/mark/exams/{examId}/publish`

请求 Body：`{ "confirm": true }`。

响应 data：`{ "examId": 3001, "publishedCount": 480, "avgScore": 76.3, "passRate": 0.86 }`。说明：

- 前置校验：全部答卷已交卷（含强制）、全部主观题阅卷完成、复核队列清空，否则 50007 并在 message 中说明未完成项数量。
- 合成：客观分 + 主观分 = 总分，按试卷封存的 GradeRule 快照评定等级（优良中差），写入 AnswerSheet；历史成绩保留评定当时的等级快照（FR-STAT-04）。
- 发布后置场次状态 `SCORED_PUBLISHED`，向考生推送成绩发布站内通知（SYS 模块）；发布后考生端 TAKE-09 可查。
- 幂等：重复发布返回首次发布结果。发布不可撤回（如需更正，由超管走运维流程并全程留痕）。

错误码：50007、10004、10003。

---

### 4.12 STAT 考试统计分析

统计口径：以 ExamCandidate 快照与 AnswerSheet 为准；等级取成绩发布时快照。数据权限：按组织层级过滤，越权 10004；统计接口响应时间目标 ≤5s（NFR-01），由预聚合表/缓存支撑。

#### STAT-01 单场考试总览

- **方法/路径**：`GET /api/v1/admin/stat/exams/{examId}/overview`

响应 data：`{examId, examName, shouldCount, attendedCount, absentCount, attendRate, avgScore, maxScore, minScore, passRate, passCount, gradeDist: [{gradeName, count, ratio}], status}`。说明：`shouldCount` 为名单人数；缺考 = 名单内未进入且未交卷（含补考未完成）。错误码：10005、10004。

#### STAT-02 分层级单位汇总

- **方法/路径**：`GET /api/v1/admin/stat/exams/{examId}/org-summary`

查询参数：`orgId`（默认数据权限根节点，返回其直属下级逐单位汇总）。

响应 data：`{orgId, orgName, children: [{orgId, orgName, shouldCount, attendedCount, attendRate, avgScore, passRate, gradeDist: [{gradeName, count}], rank(同级排名)}], hasSubLevel}`。说明：按组织路径递归聚合；总部视角返回各二级集团，二级视角返回下属子企业（FR-STAT-02）。错误码：10004、60001。

#### STAT-03 单位下钻

- **方法/路径**：`GET /api/v1/admin/stat/exams/{examId}/org-summary/{orgId}`

查询参数：`detailType`（`SUB_ORG` / `USER`）、分页参数（USER 时）。

响应 data：SUB_ORG 同 STAT-02 结构；USER 为分页 list `{userId, name, jobNo, score, grade, passed, durationSec, rankInOrg, sheetStatus}`。说明：人员明细含单位内排名；**V1.1 排名规则：按分数降序，同分按答题用时（durationSec = 交卷时间 − 进入时间）升序**；是否向考生公开排名由参数 `stat.rankPublic` 控制，不影响管理端。错误码：10004、60001。

#### STAT-04 分数段分布

- **方法/路径**：`GET /api/v1/admin/stat/exams/{examId}/score-segments`

查询参数：`orgId`（可选，按单位筛选对比）、`segments`（可选，如 `0-59,60-69,70-79,80-89,90-100`，默认取系统参数）。

响应 data：`[{ "segment": "60-69", "minScore": 60, "maxScore": 70, "count": 85, "ratio": 0.177 }]`。说明：区间左闭右开、末段右闭，由服务端统一解析；非法分段返回 60001。

#### STAT-05 等级分布

- **方法/路径**：`GET /api/v1/admin/stat/exams/{examId}/grade-distribution`

查询参数：`orgId`（可选）。响应 data：`[{ "gradeName": "优", "count": 42, "ratio": 0.087 }, ...]`。说明：按成绩发布时等级快照统计；等级规则后续变更不回溯历史成绩。

#### STAT-06 题目维度分析

- **方法/路径**：`GET /api/v1/admin/stat/exams/{examId}/question-analysis`

查询参数：`orderBy`（`WRONG_DESC` 错误率最高 / `SEQ`）、`topN`（默认 20）。

响应 data：`[{seq, sheetQuesId, quesType, stemText, fullScore, avgScore, correctRate, wrongRate, tags[], optionDist: [{optKey, selectCount, ratio}]}]`。说明：知识点掌握度按 `tags` 聚合由前端分组展示；RANDOM 一人一卷按题目快照聚合。错误码：10005、10004。

#### STAT-07 统计报表导出

- **方法/路径**：`GET /api/v1/admin/stat/exams/{examId}/export`

查询参数：`exportType`（`SUMMARY` 单位汇总表 / `DETAIL` 人员成绩明细表 / `QUESTION` 题目分析表）、`orgId`。

响应 data：`{ "taskId": "exp_001", "fileUrl": "https://.../stat.xlsx?sign=...", "expireSec": 3600 }`。说明：导出内容按数据权限过滤并写操作日志（FR-STAT-07）。错误码：60002、10004。

#### STAT-08 个人历次成绩单

- **方法/路径**：`GET /api/v1/app/stat/my-transcripts`

查询参数：分页参数。响应 data（分页）：list 项 `{examId, examName, examTime, myScore, totalScore, grade, passed, durationSec, rankInOrg(null 当 stat.rankPublic=false), retakeSeq}`。说明：仅含已发布成绩的场次；`rankInOrg` 口径为分数降序、同分按用时升序（V1.1）。

---

### 4.13 SYS 系统管理

#### SYS-01 系统参数查询

- **方法/路径**：`GET /api/v1/admin/sys/configs`

查询参数：`group`（可选：`EXAM`/`LEARN`/`QUES`/`LIB`/`STAT`/`SECURITY`）。响应 data：`[{configKey, configName, configValue, valueType, group, defaultValue, updatedBy, updatedTime}]`。内置参数（示例）：

| configKey | 说明 | 默认 |
| --- | --- | --- |
| exam.passScoreDefault | 及格线默认值 | 60 |
| exam.gradeRuleDefault | 等级规则默认值 | 优[90,100]/良[80,90)/中[60,80)/差[0,60) |
| exam.switchScreenLimit | 切屏次数上限 | 3 |
| exam.multiTerminal | 多终端策略 | KICK_OLD |
| exam.scoreSegmentDefault | 分数段统计口径 | 0-59,60-69,70-79,80-89,90-100 |
| learn.completeThreshold | 学习完成进度阈值 | 0.9 |
| learn.durationThreshold | 有效时长占标称比例阈值 | 0.8 |
| learn.heartbeatSec | 学习心跳间隔（秒） | 15 |
| learn.standardSec | 周期达标学习时长（秒） | 7200 |
| lib.videoMaxMB / lib.docMaxMB | 上传大小限制 | 500 / 50 |
| lib.auditRequired / ques.auditRequired | 资料/题目审核开关 | true / true |
| stat.rankPublic | 单位内排名是否向考生公开 | false |
| security.loginFailLock | 连续登录失败锁定次数 | 5 |
| exam.defaultDurationMin | 考试默认时长（分钟，V1.1） | 30 |
| exam.defaultTemplate | 本期默认考试模板（V1.1） | SINGLE×20×5（100 分） |
| practice.defaultCount / practice.maxCount | 练习默认题数 / 单次上限（V1.1） | 10 / 50 |
| wx.notify.enabled | 微信订阅消息通道总开关（V1.1，关闭时降级为仅站内信） | true |
| wx.tpl.examPublish / wx.tpl.examRemindBefore / wx.tpl.examRemindDeadline / wx.tpl.scorePublished | 四类场景订阅消息模板 ID（V1.1） | 按小程序后台申请结果配置 |
| wx.remind.beforeMin / wx.remind.deadlineMin | 考前提醒 / 截止前提醒提前量（分钟，V1.1） | 60 / 30 |

#### SYS-02 系统参数修改

- **方法/路径**：`PUT /api/v1/admin/sys/configs`（仅超管）

请求 Body：`{ "configs": [{ "configKey": "exam.switchScreenLimit", "configValue": "5" }] }`。说明：按 key upsert，服务端按 valueType 校验；修改即时生效（Redis 缓存失效广播）并写操作日志。错误码：10001、10003。

#### SYS-03 登录日志查询

- **方法/路径**：`GET /api/v1/admin/sys/logs/login`

查询参数：`keyword`（工号/姓名）、`result`（SUCCESS/FAIL）、`startTime/endTime`、分页参数。响应 data（分页）：list 项 `{logId, jobNo, name, orgName, terminal, ip, result, failReason, loginTime}`。

#### SYS-04 操作日志查询

- **方法/路径**：`GET /api/v1/admin/sys/logs/operation`

查询参数：`keyword`（操作人）、`module`（ORG/USER/LIB/QUES/PAPER/EXAM/MARK/SYS）、`startTime/endTime`、分页参数。响应 data（分页）：list 项 `{logId, operatorName, orgName, module, action, targetDesc, detail, ip, operateTime}`。

#### SYS-05 考试异常日志查询

- **方法/路径**：`GET /api/v1/admin/sys/logs/exam-abnormal`

查询参数：`examId`、`abnormalType`（`FORCE_SUBMIT` 强制收卷 / `SWITCH_EXCEED` 切屏超限 / `KICK_OFFLINE` 异地踢出）、分页参数。响应 data（分页）：list 项 `{logId, examName, jobNo, name, abnormalType, detail, occurTime}`。数据权限：本组织范围内场次。

#### SYS-06 通知消息分页查询（管理端）

- **方法/路径**：`GET /api/v1/admin/sys/notices`

查询参数：`noticeType`（`EXAM` 考试通知 / `SCORE` 成绩发布 / `LEARN_TASK` 学习任务 / `GENERAL` 通用）、分页参数。响应 data（分页）：list 项 `{noticeId, noticeType, title, targetDesc, sentCount, readCount, createdBy, createdTime}`。

#### SYS-07 发布通知消息

- **方法/路径**：`POST /api/v1/admin/sys/notices`

请求 Body：`{ "noticeType": "GENERAL", "title": "...", "content": "富文本", "targetOrgIds": [12], "channels": ["INBOX", "WX_SUB"] }`。响应 data：`{ "noticeId": 7701, "sentCount": 486 }`。说明：`SMS`/`WECOM` 渠道为预留（对接后生效）；`WX_SUB`（V1.1）对绑定且已订阅的用户下发微信订阅消息；EXAM/SCORE/LEARN_TASK 类型一般由 EXAM-11、MARK-07、LEARN-08 自动触发，本接口用于手工通知。

#### SYS-08 本人站内消息列表

- **方法/路径**：`GET /api/v1/app/notices`

查询参数：`read`（true/false）、分页参数。响应 data（分页）：list 项 `{noticeId, noticeType, title, content, read, sendTime, bizLink: {examId?/taskId?}}` + 顶层 `{unreadCount}`。

#### SYS-09 标记消息已读

- **方法/路径**：`PUT /api/v1/app/notices/{noticeId}/read`
- 说明：幂等；`noticeId=0` 表示全部已读。错误码：10005。

---

### 4.14 PRAC 日常练习（V1.1 新增）

考生端接口（`/app/`），数据范围恒为"当前登录考生本人"。练习不受考试时间窗口与名单限制，凡考生角色可随时发起；练习结果独立于考试判分体系，不计入任何考试统计。练习无会话表：发起（PRAC-01）为无状态抽题不落库，作答（PRAC-02）每题即时判分并落一行练习记录（`t_practice_record`，一题一行、只增不改）。安全约束：PRAC-01/02 按用户限流（10 次/分钟，10006）；作答接口按 `clientEventId` 幂等（重复提交返回首次判分结果，不重复落行）；练习抽题与作答响应在作答前不含答案/解析（脱敏口径与考试一致）。

#### PRAC-01 发起练习（抽题）

- **方法/路径**：`POST /api/v1/app/practice/start`

请求 Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| mode | string | 否 | `CATEGORY`（按制度法规分类练习）/ `RANDOM`（全库随机抽题），默认 `RANDOM` |
| bankId | long | 条件 | mode=CATEGORY 时必填，制度法规分类 ID（含其子分类） |
| count | int | 否 | 抽题数量，可选 5/10/20，默认 10；上限取系统参数 `practice.maxCount`（默认 50） |
| difficulty | string | 否 | `BASIC`/`ADVANCED`，不传表示不限 |

请求示例：

```json
{ "mode": "CATEGORY", "bankId": 21, "count": 10, "difficulty": "BASIC" }
```

响应示例：

```json
{
  "code": 0, "message": "success",
  "data": {
    "mode": "CATEGORY", "count": 10,
    "bankName": "《中国共产党纪律处分条例》",
    "questions": [
      { "quesItemId": 90001, "seq": 1,
        "stem": "党员受到警告处分……内，不得在党内提拔职务。",
        "options": [ { "optKey": "A", "content": "六个月" }, { "optKey": "B", "content": "一年" } ],
        "sourceLaw": "《中国共产党纪律处分条例》", "sourceChapter": "第一章",
        "knowledgePoint": "纪律处分种类", "difficulty": "BASIC" }
    ]
  }
}
```

说明：

- 抽题范围为本期启用（ENABLED）的单选题；`questions` 不含标准答案与解析（作答后由 PRAC-02 下发）。
- 本接口为无状态抽题（不落库、无练习会话）；练习记录以"一题一行"在 PRAC-02 作答时产生。练习场景面向题库刷题，`quesItemId` 下发无反推风险（与考试答卷快照脱敏口径刻意区分）。
- 候选题量不足 `count` 时返回 70002，不降级少抽。
- 练习题目顺序随机；选项顺序不打乱（学习场景按题库原始选项展示）。

错误码：10001（参数非法）、10006（限流）、70002（题量不足）、10005（bankId 不存在）。

#### PRAC-02 提交单题作答（即时反馈）

- **方法/路径**：`POST /api/v1/app/practice/answer`

请求 Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| quesItemId | long | 是 | 题目 ID（PRAC-01 返回） |
| answer | object | 是 | 单选 `{ "selected": ["B"] }` |
| mode | string | 是 | 本次练习模式：`CATEGORY` / `RANDOM`（落练习记录） |
| bankId | long | 否 | 分类练习时所选分类（落练习记录快照） |
| durationSec | int | 否 | 本题作答用时（秒） |
| clientEventId | string | 是 | 客户端事件 UUID，幂等去重键（Redis 去重，重复提交直接返回首次判分结果） |

响应示例：

```json
{
  "code": 0, "message": "success",
  "data": {
    "recordId": 770001, "quesItemId": 90001, "isRight": true,
    "correctAnswer": ["B"],
    "analysis": "依据《中国共产党纪律处分条例》第十条，警告处分的处分期为一年……",
    "sourceLaw": "《中国共产党纪律处分条例》", "sourceChapter": "第一章", "knowledgePoint": "纪律处分种类"
  }
}
```

说明：

- **即时判分**：服务端读题目现值即时比对并返回正误、正确答案与解析，同时落一行 `t_practice_record`（含来源制度/章节快照与作答用时）。
- **幂等**：同一 `clientEventId` 重复提交（网络重试/双击）返回首次判分结果（code=0），不重复落行；同一题跨次练习可多次作答（每次一行流水）。

错误码：40001（题目不存在或已停用）、10001、10006、10007（clientEventId 重复时以 code=0 返回首次结果，不算错误）。

#### PRAC-03 练习记录

- **方法/路径**：`GET /api/v1/app/practice/records`

查询参数：`sourceLaw`（按制度法规筛选）、`onlyWrong`（true 时仅错题，即错题回顾）、分页参数。

响应 data（分页）：list 项 `{recordId, quesItemId, stemText, myAnswer, isRight, sourceLaw, sourceChapter, practiceMode(CATEGORY/RANDOM), durationSec, answerTime}`；`onlyWrong=true` 时 list 项额外展开 `{stem, options, correctAnswer, analysis, knowledgePoint}`（错题回顾含解析——仅本人历史练习）。

错误码：10002。

#### PRAC-04 练习统计

- **方法/路径**：`GET /api/v1/app/practice/stat`

查询参数：`periodType`（`MONTH`/`QUARTER`/`YEAR`/`CUSTOM`，CUSTOM 传 `startDate`/`endDate`），默认全部累计。

响应 data：

```json
{
  "code": 0, "message": "success",
  "data": {
    "totalQuestions": 150, "rightCount": 123, "accuracy": 0.82, "practiceDays": 12,
    "byLaw": [
      { "sourceLaw": "《中国共产党纪律处分条例》", "questions": 60, "accuracy": 0.85 }
    ],
    "trend": [ { "date": "2026-08-17", "questions": 20, "accuracy": 0.8 } ],
    "weakPoints": [ { "knowledgePoint": "检举控告受理", "wrongCount": 6, "wrongRate": 0.43 } ]
  }
}
```

说明：全部指标基于 `t_practice_record` 实时聚合；`byLaw` 按来源制度（快照）给出正确率分布，`weakPoints` 为薄弱知识点 TOP10（按题目现值 knowledge_point 聚合错误率降序）。错误码：10002、60001（时间范围非法）。

---

### 4.15 NOTIFY-WX 微信订阅通知（V1.1 新增）

微信小程序订阅消息通道（`subscribeMessage.send`），与站内信（INBOX）并存：四类场景（考试发布、考前提醒、截止前提醒、成绩公布，scene 取值 1~4）固定双通道下发——触发即落站内信（`t_sys_notice`），微信通道仅对"已绑定 openid 且有订阅额度"的用户下发，每次推送结果落一行 `t_notify_wx_log`（只增不改，失败重试追加 `retry_of` 关联新行）；公众号服务通知作为预留通道（`channels` 枚举 `WX_MP`，未配置时忽略）。模板 ID 由系统参数配置（见 SYS-01 `wx.tpl.*`）。

安全约束：绑定接口按用户限流（10 次/分钟）；openid 一账号一绑定，冲突时解绑旧账号并写操作日志；微信通道下发的消息内容仅含业务摘要（考试名称/时间/成绩分数），不含题目、答案等敏感数据；推送记录查询按数据权限过滤（仅本组织范围内场次）。

#### NOTIFY-WX-01 微信绑定（code 绑定）

- **方法/路径**：`POST /api/v1/app/wechat/bind`

请求 Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| code | string | 是 | 小程序 `wx.login()` 返回的临时登录凭证 |

请求示例：

```json
{ "code": "0c3XaZ0w3k9L2e1fGh4j5k6l7m8n9o0p" }
```

响应 data：`{ "openidMasked": "oXy***3kQ", "alreadyBound": false }`。

说明：服务端以 `code` 调用微信 `code2session` 换取 openid/unionid 并写入当前用户 `t_org_user.mp_openid`/`unionid`；同一用户重复绑定幂等返回成功；openid 已被其他账号绑定时先解绑旧账号（全库部分唯一索引兜底，写操作日志）。解绑：`POST /api/v1/app/wechat/bind` 携带 `{ "unbind": true }`（mp_openid/unionid 置空）。

错误码：10001（code 缺失）、70003（code 无效/过期）、10006（限流）、10002。

#### NOTIFY-WX-02 查询订阅授权状态

- **方法/路径**：`GET /api/v1/app/wechat/subscribe`

响应 data：

```json
{
  "code": 0, "message": "success",
  "data": {
    "wxBound": true,
    "scenes": [
      { "scene": "EXAM_PUBLISH",       "tplId": "tpl_aaa", "subscribed": true,  "remainCount": 1 },
      { "scene": "EXAM_REMIND_BEFORE", "tplId": "tpl_bbb", "subscribed": false, "remainCount": 0 },
      { "scene": "EXAM_REMIND_DEADLINE","tplId": "tpl_ccc", "subscribed": false, "remainCount": 0 },
      { "scene": "SCORE_PUBLISHED",    "tplId": "tpl_ddd", "subscribed": true,  "remainCount": 1 }
    ]
  }
}
```

说明：小程序据此对 `subscribed=false`（或 `remainCount=0`）的场景调用 `wx.requestSubscribeMessage` 拉起授权；`wxBound=false` 时先引导 NOTIFY-WX-01 绑定。订阅额度存于服务端 Redis（`wx:subscribe:{userId}:{scene}` 计数，不落库），每次成功下发扣减一次。错误码：10002、70004（未绑定，仍返回 200 + wxBound=false 亦可，二选一实现并在联调时固定）。

#### NOTIFY-WX-03 上报订阅授权结果

- **方法/路径**：`PUT /api/v1/app/wechat/subscribe`

请求 Body：

```json
{
  "results": [
    { "scene": "EXAM_PUBLISH", "tplId": "tpl_aaa", "action": "accept" },
    { "scene": "SCORE_PUBLISHED", "tplId": "tpl_ddd", "action": "reject" }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| results[].scene | string | 是 | 场景编码：`EXAM_PUBLISH`/`EXAM_REMIND_BEFORE`/`EXAM_REMIND_DEADLINE`/`SCORE_PUBLISHED` |
| results[].tplId | string | 是 | 模板 ID（须与系统参数配置一致，不一致记 10001） |
| results[].action | string | 是 | `accept`（订阅成功，额度+1）/ `reject`（拒绝）/ `ban`（拒绝且不再询问） |

响应 data：`{ "updated": 2 }`。说明：幂等（重复上报不累加）；额度记于 Redis `wx:subscribe:{userId}:{scene}` 计数（不落库）；`accept` 累加一次性订阅额度（微信一次性订阅约束：一次授权 = 一次下发）。错误码：10001、70004。

#### NOTIFY-WX-04 推送记录查询（管理端）

- **方法/路径**：`GET /api/v1/admin/notify-wx/records`

查询参数：`examId`、`scene`（`EXAM_PUBLISH`/`EXAM_REMIND_BEFORE`/`EXAM_REMIND_DEADLINE`/`SCORE_PUBLISHED`）、`status`（`PENDING` 待发送 / `SUCCESS` / `FAILED` / `GIVE_UP` 放弃）、`keyword`（姓名/工号）、`startTime/endTime`、分页参数。

响应 data（分页）：list 项 `{recordId, noticeId, scene, examId, examName, userId, name, jobNo, orgName, mpOpenidMasked, templateId, status, errcode, errmsg, retryOf, sendTime, createdTime}`。

说明：数据源自 `t_notify_wx_log`（每次推送一行、只增不改，失败重试为 `retryOf` 关联的追加新行）；数据权限按场次创建方组织路径过滤（越权 10004）；失败记录可由管理员在详情页触发人工重推（复用 NOTIFY-WX-04 记录定位，重推动作走服务端 Job 通道追加新行，写操作日志）。错误码：10004、60001。

---

### 4.16 GROUP 用户分组（V1.1 新增）

分组为人员标签字段口径：`t_org_user.user_group`（SMALLINT），**固定枚举、无独立分组表**——`1` 派驻组、`2` 子企业纪委、`3` 纪检委员、`9` 其他纪检相关人员（默认）。每名用户一个分组标签；分组与组织树正交（标签维度与单位维度相互独立）。组织覆盖国联集团、国发集团、机场集团三家一级单位建制（组织树初始化数据，不新增接口）。分组用于考试名单圈定（EXAM-07 `userGroups`）、学习任务下达（LEARN-08）等场景。数据权限：分组清单与人数按当前管理员数据权限过滤；分组标签的设置/移出仅允许操作人数据权限范围内的人员（越权成员计入 skipped）。

#### GROUP-01 分组清单

- **方法/路径**：`GET /api/v1/admin/groups`

响应 data：`[{userGroup: 1, groupName: "派驻组", memberCount: 86}, {userGroup: 2, groupName: "子企业纪委", memberCount: 152}, {userGroup: 3, groupName: "纪检委员", memberCount: 240}, {userGroup: 9, groupName: "其他纪检相关人员", memberCount: 8}]`。说明：固定枚举清单 + 各分组人数实时统计（按数据权限过滤）；无新增/删除分组接口（枚举变更走版本迭代）。错误码：10003。

#### GROUP-02 分组成员分页查询

- **方法/路径**：`GET /api/v1/admin/groups/{userGroup}/members`

查询参数：`keyword`（姓名/工号）、分页参数。路径参数 `userGroup` 取值 1/2/3/9。响应 data（分页）：list 项 `{userId, name, jobNo, orgName, position, userGroup, status}`（成员列表按数据权限过滤）。错误码：10001（分组标签非法）、10004。

#### GROUP-03 批量设置分组标签

- **方法/路径**：`PUT /api/v1/admin/groups/{userGroup}/members`

请求 Body：`{ "userIds": [10021, 10022, 10031] }`。

响应 data：`{ "updatedCount": 2, "skipped": [ { "userId": 10031, "reason": "超出数据权限范围" } ] }`。说明：即批量 `UPDATE t_org_user.user_group = {userGroup}`（条件更新，幂等）；逐个校验用户存在、状态正常且在操作人数据权限范围内；标签随人行（调岗不影响），设置动作写操作日志。错误码：10001、10004。

#### GROUP-04 批量移出分组

- **方法/路径**：`DELETE /api/v1/admin/groups/{userGroup}/members`

请求 Body：`{ "userIds": [10021] }`。响应 data 同 GROUP-03。说明：将指定用户的 `user_group` 重置为 9（其他）；仅处理当前确属该分组的用户，其余计入 skipped；写操作日志。错误码：10001、10004。

---

## 5. 文件上传/下载约定

### 5.1 通用图片上传（富文本内嵌图）

- **方法/路径**：`POST /api/v1/admin/files/image`，`multipart/form-data`，表单字段 `file`（jpg/png/gif/webp，≤5MB）。
- 响应 data：`{ "fileId": "f_2026_001", "url": "https://.../img/f_2026_001.png?sign=..." }`。
- 用途：题干/选项/解析/考试须知等富文本中的图片先经本接口上传，再将返回的 URL 嵌入 HTML 提交（如 QUES-06）。管理端鉴权接口，数据无组织隔离。

### 5.2 业务文件上传

| 场景 | 接口 | 限制 |
| --- | --- | --- |
| 人员导入 | USER-07 | .xlsx ≤10MB |
| 题目导入 | QUES-10 | .xlsx/.docx ≤20MB |
| 学习资料文档 | LIB-06/08 | pdf/doc/docx/ppt/pptx，≤`lib.docMaxMB`（默认 50MB） |
| 学习资料视频 | LIB-06/08 | mp4，≤`lib.videoMaxMB`（默认 500MB），>100MB 走 5.3 分片上传 |
| 资料封面 | LIB-06/08 | jpg/png ≤2MB |

统一约定：

- 请求头 `Content-Type: multipart/form-data`；响应仍为统一 `{code,message,data}` 结构。
- 服务端校验扩展名 + MIME + 魔数三重检查，不匹配返回 10008；超限返回 10009（message 含配置上限）。
- 文件落 MinIO/本地存储后以 MD5 + 随机串重命名，原始文件名仅存元数据，防路径穿越与覆盖。

### 5.3 大视频分片断点续传

超过 100MB 的视频采用分片上传（三步，均走 `/api/v1/admin/lib/upload/...`）：

1. `POST /api/v1/admin/lib/upload/init`：Body `{fileName, fileSize, fileMd5, chunkSize}`（chunkSize 默认 5MB）；响应 `{uploadId, uploadedChunks[]}`。若 fileMd5 已存在（秒传），直接返回 `{materialFileUrl, instant: true}`。
2. `PUT /api/v1/admin/lib/upload/{uploadId}/chunks/{chunkNo}`：Body 为分片二进制；幂等，已上传分片重复 PUT 直接成功；响应 `{uploadId, uploadedChunks[]}`，前端据以断点续传。
3. `POST /api/v1/admin/lib/upload/{uploadId}/complete`：合并分片并校验整体 MD5；响应 `{materialFileUrl}`，随后在 LIB-06 中以此 URL 代替 file 字段（表单字段 `fileUrl` 二选一）。

未完成的分片任务保留 24 小时，过期由定时任务清理。

### 5.4 下载

- 下载统一由业务接口返回带签名的短时 URL（默认 3600 秒），前端直链下载，不经应用中转（学习资料文件、统计导出、导入模板除外——模板为静态资源直接输出文件流）。
- 下载地址失效属正常行为，前端重新调用对应接口获取新签名 URL。

---

## 6. 前端对接要点

### 6.1 通用

- 请求拦截器统一注入 `Authorization`；收到 `code=10002` 时先用 refreshToken 静默刷新重放一次，仍失败则跳登录页；`10010` 提示"账号已在其他终端登录/被强制下线"。
- HTTP 401/403 与 `code=10003/10004` 区分处理：前者为会话问题，后者为权限问题，提示"无权限访问"。
- 所有列表页接 `pageNum/pageSize`，空数据以 `total=0, list=[]` 呈现。
- 时间一律展示后端下发的 `yyyy-MM-dd HH:mm:ss` 字符串，不做时区换算（服务端已按东八区输出）。

### 6.2 PC 管理端（Vue3 + Element Plus）

- 组织树/分类树组件采用 ORG-01、LIB-01、QUES-01 返回的树结构，懒加载场景传 `depth`。
- 批量导入三步交互：下载模板 → 上传 → 展示 failures 行级明细，失败行可导出修正后重传。
- 组卷页选题分值编辑在本地完成后一次性调 PAPER-06 全量替换，服务端校验总分（40003）。

### 6.3 H5 答题端（Vue3 + Vant）

- **倒计时**：进入考试（TAKE-02）拿 `deadlineTime` 与 `serverTime`，本地用 `performance.now()` 差值渲染，每次 TAKE-05 响应与每 60s 一次的 TAKE-03 校时进行漂移校准；禁止直接使用 `Date.now()` 绝对值判断剩余时间（防改系统时间）。剩余 5 分钟本地提醒；剩余 0 时前端自动触发交卷，最终以服务端判定为准。
- **答案保存**：每题作答变更后 3s 防抖批量提交 TAKE-05，另加 30s 定时心跳兜底；提交前将答案快照写入 `localStorage`（key 含 sheetId），弱网/断网时队列化暂存，网络恢复后按序重放（`clientBatchId` 保证重放幂等）；TAKE-08 用于恢复时与服务端对账，以服务端数据为准合并。
- **弱网重试**：TAKE-05/07 失败按 1s/3s/8s 指数退避重试，重试仍失败保留本地队列并提示"答案已本地保存，恢复网络后自动同步"；交卷必须在线完成，失败持续重试直至成功或到点由服务端自动收卷。
- **防作弊配合**：监听 `visibilitychange`/`blur` 调 TAKE-06；收到 `action=FORCE_SUBMIT` 或 TAKE-05 响应 `submitStatus≠IN_EXAM` 时立即清理本地缓存并跳交卷完成页。
- **单手持机**：答题卡、上一题/下一题、交卷按钮布局于拇指热区；题型分区锚点来自 TAKE-04 的 `sections`。
- **脱敏红线**：考生端任何接口均不含答案/解析字段，前端不得本地存储任何含答案的数据；成绩发布后 TAKE-09 的解析展示受 `showAnalysis` 控制。

### 6.4 状态轮询建议

- 阅卷进度监控（MARK-01）与考试监控大屏（EXAM-08/STAT-01）轮询间隔 ≥15s；考生端成绩等待页轮询 TAKE-09 间隔 ≥30s，避免集中轮询打满统计接口。

### 6.5 微信小程序端（V1.1 新增，主要用户载体）

- **双工程**：原生小程序 `exam-miniprogram` 承载练习/考试/成绩/消息主流程；H5 套壳小程序 `exam-h5miniprogram` 以 `web-view` 整页加载 H5 考生端（学习资料等次级场景）。两工程与 H5 考生端调用同一套 `/api/v1/app/**` 接口，登录 `terminal=MP`。
- **域名与证书（微信平台强制）**：API 域名须配置小程序后台「request 合法域名」；web-view 加载的 H5 域名须配置「业务域名」并放置微信校验文件；文件上传/下载域名须配置 uploadFile/downloadFile 合法域名；全部强制 HTTPS（TLS ≥ 1.2）且域名完成 ICP 备案。开发期可在开发者工具勾选"不校验合法域名/TLS"绕过，体验版/正式版必须配置齐全。
- **网络层**：`wx.request` 封装同 6.1 约定（Bearer 注入、10002 静默刷新重放、10010 被踢提示）；答题弱网队列用 `wx.setStorageSync` 持久化，`wx.onNetworkStatusChange` 恢复后按 `clientBatchId` 幂等重放。
- **web-view 与 H5 通讯**：H5 引入微信 JSSDK，经 `wx.miniProgram.postMessage` 向小程序回传结果（注意仅在小程序后退/组件销毁/分享时批量送达，非实时）；实时跳转用 `wx.miniProgram.navigateTo`；小程序 → H5 通过 web-view `src` 查询参数传递（如 `?from=mp`）。web-view 内不支持 `wx.login`/`wx.requestSubscribeMessage`，微信绑定与订阅授权只在原生小程序完成。
- **订阅消息**：进入待考列表/成绩页时先调 NOTIFY-WX-02，对未订阅场景 `wx.requestSubscribeMessage`（仅可由用户手势触发，禁止 onLoad 自动拉起），授权结果 PUT NOTIFY-WX-03 上报。

---

## 附录 A 角色编码与权限点（摘要）

| 角色编码 | 角色 | 关键权限点 |
| --- | --- | --- |
| SUPER_ADMIN | 超级管理员 | 全部 + `sys:config:edit` |
| HQ_ADMIN | 总部管理员 | 全集团 `org/user/lib/ques/paper/exam/mark/stat` 读写 |
| L2_ADMIN | 二级管理员 | 本集团及下属 `org/user/lib/learn/exam/stat` 读写，`ques` 读写 |
| SUB_ADMIN | 子企业管理员 | 本单位 `user/lib/learn/exam/stat` |
| MARKER | 阅卷教师 | `mark:task:claim`、`mark:score:submit` |
| EXAMINEE | 考生 | `/api/v1/app/**` 全部考生端接口 |

## 附录 B 版本变更记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-08-14 | 首版，覆盖需求书全部功能模块，共 117 个接口 |
| V1.1 | 2026-08-18 | 依据《纪检人员考试培训微信小程序开发任务书》（初稿）：新增 PRAC 日常练习（4）、NOTIFY-WX 微信订阅通知（4）、GROUP 用户分组（4，分组为 `t_org_user.user_group` 标签字段口径）三个模块共 12 个接口；更新考试规则（20 题单选×5 分/双乱序/默认 30 分钟/即时出分/考后回顾）、题库分类与难度两级、成绩排名规则（分数降序同分按用时升序）、终端标识 MP、EXAM-07 分组标签圈人、EXAM-11/SYS-07 通道 WX_SUB；接口总数 129 个；详见文首"版本变更记录" |
