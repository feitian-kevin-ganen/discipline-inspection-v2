# 打包发布目录

本目录用于存放在线考试系统的打包产物和发布资料。

## 目录结构

```text
guolian/
├── spec/PACKAGING_SPEC.md         # 打包发布规约（位于项目根目录 spec/ 下）
├── README.md                      # 本说明
└── deploy-history/
    ├── full-deploy/
    │   ├── source-code/           # 目标机部署所需的源码
    │   └── deploy-files/          # 目标机发布所需的运行文件
    └── incremental-deploy/        # 增量包存放目录
```

## 全量包

路径：`deploy-history/full-deploy/`

- `source-code/`：展开的项目源码（对应工程根目录的 `code/`）。
- `deploy-files/`：目标机发布内容，包括 `app/`、`config/`、`data/`、`docs/`、`middleware/`、`scripts/`、`web/` 等。

## 增量包

路径：`deploy-history/incremental-deploy/`

命名规则：

```text
deploy-increment-{yyyyMMDD}-v{x}.tar.gz
```

- `yyyyMMDD`：打包日期，例如 `20261116`。
- `x`：当天版本号，每天从 `1` 开始，同一日内每打包一次增加 `0.1`。

示例：

```text
deploy-increment-20261116-v1.tar.gz
deploy-increment-20261116-v1.1.tar.gz
deploy-increment-20261117-v1.tar.gz
```

## 注意事项

- `deploy-history/` 下的实际包文件（`.tar.gz`）和大体积运行时数据（如 `data/minio/exam-files/`）**不提交到 Git**。
- 本目录只保留规约说明和目录结构，发布包通过 `scp` 等方式分发到目标机。
