# 国联环境目录

本目录存放国联（guolian）各环境的目标机配置源码与相关文件。**发布包不在这里**，发布包在
`../deploy/guolian-deploy/`。

## 目录结构

```text
guolian/
├── README.md                 # 本说明
├── test/                     # 国联测试环境
│   └── project-files/        # 目标机配置相关的源码与配置文件
└── prd/                      # 国联生产环境（生产配置申请到之前保持为空）
```

## 说明

- `test/project-files/`：只放**目标机配置相关的源码和配置文件**（`code-src/`、`config/`、`db/`、
  `docs/`、`integration/`、`scripts/` 等），不放编译产物、jar、压缩包。
- `prd/`：生产环境配置尚未申请，目录保持为空，申请到后按 `test/project-files/` 的结构补齐。
- 打包规约见 `spec/PACKAGING_SPEC.md`；目标机全量/增量包路径见 `deploy/guolian-deploy/`。
