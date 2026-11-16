# Agent Instructions

> 本项目（纪检监察人员在线考试培训系统）位于 `/home/cloud/projects/AIExamK3`。  
> 本文件为 Kimi Code 代理指令入口，详细规则请阅读下方三份规约。

## 必须遵守的规约

| 场景 | 规约文件 |
|------|----------|
| 开发活动（编码、修复、SQL、权限、冒烟、提交等） | `spec/DEVELOPMENT_SPEC.md` |
| 目标机打包发布活动（全量包、增量包、source-code/deploy-files 组织、目标机配置同步与推送） | `spec/PACKAGING_SPEC.md` |
| 本机打包发布运行活动 | `spec/LOCAL_PACKAGING_SPEC.md` |

## 执行要求

1. 每次接到代码修改、bug 修复、功能开发类任务时，**先读取 `spec/DEVELOPMENT_SPEC.md`**，并按其中 10 条规则执行。
2. 每次接到目标机打包、发布、部署类任务时，**先读取 `spec/PACKAGING_SPEC.md`**，并严格按目录结构、命名规则和目标机配置执行。
3. 每次接到“打包本机并发布运行”类任务时，**先读取 `spec/LOCAL_PACKAGING_SPEC.md`**，并按本机环境配置执行。
4. 若任务同时涉及开发、目标机发布、本机发布中的多项，对应规约均需遵守。
5. 规约未明确的事项，**先向用户确认**，不得擅自决定或编造。
