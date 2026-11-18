# Agent Instructions

> 本项目（纪检监察人员在线考试培训系统）位于 `/home/cloud/projects/AIExamK3`。  
> 本文件为 Kimi Code 代理指令入口，详细规则请阅读下方三份规约。

## 必须遵守的规约

| 场景 | 规约文件 |
|------|----------|
| 开发活动（编码、修复、SQL、权限、冒烟、提交等） | `spec/DEVELOPMENT_SPEC.md` |
| 测试机打包发布活动（全量包、增量包、source-code/deploy-files 组织、测试机配置同步与推送） | `spec/PACKAGING_SPEC.md` |
| 本机启动/重启/验证活动 | `spec/LOCAL_RUN_SPEC.md` |

## 术语约定（机器与环境）

- **本机**：开发打包机 `10.110.40.160`，只做开发、构建、本机运行验证，运行目录 `deploy/local-deploy/`。
- **测试机**：国联**测试环境**部署机 `10.64.16.29`（旧文档中的“目标机”即此机），配置基线 `guolian/test/project-files/`。
- **生产机**：国联**生产环境**机器，尚未申请；申请前 `guolian/prd/` 保持为空，不得用测试机配置代替。
- `guolian/test/project-files/` 是**差异基线**而非源码副本：`code/` 下只放需覆盖主工程的文件；测试机源码由 `code/scripts/sync-test-source.sh` 从主工程 `code/` 生成。生产环境沿用同一结构。
- 完整定义见 `spec/PACKAGING_SPEC.md` 第 0.1 节。

## 执行要求

1. 每次接到代码修改、bug 修复、功能开发类任务时，**先读取 `spec/DEVELOPMENT_SPEC.md`**，并按其中 10 条规则执行。
2. 每次接到测试机打包、发布、部署类任务时，**先读取 `spec/PACKAGING_SPEC.md`**，并严格按目录结构、命名规则和测试机配置执行。
3. 每次接到“启动/重启本机服务、本机运行验证”类任务时，**先读取 `spec/LOCAL_RUN_SPEC.md`**，并按本机环境配置执行。
4. 若任务同时涉及开发、测试机发布、本机运行中的多项，对应规约均需遵守。
5. 规约未明确的事项，**先向用户确认**，不得擅自决定或编造。
