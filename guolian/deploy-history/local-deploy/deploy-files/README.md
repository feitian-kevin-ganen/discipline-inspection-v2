# AIExamK3 本机发布包（纪检监察人员在线考试培训系统）

本包用于在**打包机本机**（`10.110.40.160`）部署运行，不用于目标机。目标机包见
`guolian/deploy-history/full-deploy/`。

## 1. 环境依赖（由本机 PaaS 提供，本包不携带）

| 组件 | 地址 | 说明 |
|------|------|------|
| JDK | 系统 JDK17 | 启动脚本用 PATH 中的 `java` |
| PostgreSQL | `127.0.0.1:5434`，库 `exam_db`，账号 `postgres/postgres` | 表结构与数据已就绪 |
| Redis | `127.0.0.1:6379`，无密码 | |
| MinIO | `http://127.0.0.1:9000`，bucket `exam-files`，`minioadmin/minioadmin` | 预签名对外地址 `http://10.110.40.160:9000` |
| ffmpeg | `middleware/ffmpeg` | 随包携带，视频抽帧用 |

## 2. 目录结构

```text
AIExamK3/
├── app/exam-server.jar      # 后端
├── config/application.yml   # 本机配置（PostgreSQL + 本地 MinIO）
├── web/admin|user|h5        # 三端前端（构建时 base 分别为 /mgmt/ /learning-web/ /learning-h5/）
├── middleware/ffmpeg
├── scripts/
│   ├── start_all.sh         # 启动后端 + 统一入口网关
│   ├── stop_all.sh          # 只停本目录记录的进程
│   ├── local_gateway.py     # 三端静态 + /api 反代（替代目标机 InforSuiteLB）
│   ├── verify_api.py        # 接口全流程校验
│   └── verify_pdf_pages.py  # PDF 页图预览校验（可选，需 playwright）
├── logs/  run/              # 运行时日志与 pid
└── README.md
```

## 3. 启动 / 停止

```bash
cd /home/cloud/AIExamK3
bash scripts/start_all.sh          # 启动并打印入口
bash scripts/start_all.sh status   # 只看状态
bash scripts/stop_all.sh           # 停止
```

启动顺序：后端 `8088` 就绪（探 `/v3/api-docs`）→ 统一入口网关 `6003`。

## 4. 访问入口

| 端 | 地址 |
|----|------|
| 管理端 | `http://10.110.40.160:6003/mgmt/` |
| 用户端 PC | `http://10.110.40.160:6003/learning-web/` |
| 用户端 H5 | `http://10.110.40.160:6003/learning-h5/` |
| 接口 | `http://10.110.40.160:6003/api/v1/**` → 反代 `127.0.0.1:8088` |

路径规则与目标机一致（目标机由 InforSuiteLB 在 `6003` 承载同样的前缀）。`/` 会 302 到
`/learning-h5/`。

## 5. 验证

```bash
bash scripts/verify_api.py --base http://127.0.0.1:8088/api/v1
curl -sI http://127.0.0.1:6003/mgmt/ | head -1
curl -sI http://127.0.0.1:6003/learning-web/ | head -1
curl -sI http://127.0.0.1:6003/learning-h5/ | head -1
```

## 6. 配置要点（config/application.yml）

- `server.port: 8088`
- `spring.datasource.url: jdbc:postgresql://127.0.0.1:5434/exam_db?stringtype=unspecified`
- `exam.minio.endpoint: http://127.0.0.1:9000`
- `exam.minio.public-endpoint: http://10.110.40.160:9000` ← 必须与本机实际 IP 一致
- `exam.ffmpeg-path: /home/cloud/AIExamK3/middleware/ffmpeg`

本机 IP 变更时，只需改 `public-endpoint` 后重启后端。

## 7. 注意

- 本机包与目标机包**严禁混用**：目标机是 PolarDB-X/MySQL + OSS + InforSuiteLB，本机是
  PostgreSQL + 本地 MinIO + 网关脚本。
- `local_gateway.py` 只监听 `6003`，不接管本机其他端口。
- 停服务只按 `run/*.pid` 停止，不影响本机其他项目进程。
