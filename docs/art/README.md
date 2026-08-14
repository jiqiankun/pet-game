# 《宠物精灵》美术生产体系

状态：可执行；基于 2026-08-14 Batch 0～8 的实际产出、脚本和接入代码整理。  
范围：**新增或替换既有类型的游戏美术资源**。本目录不等同于临时灵感库，也不要求引入资产管理平台。

## 先读什么

| 顺序 | 文档 | 职责 |
|---:|---|---|
| 1 | [art-bible.md](art-bible.md) | 项目固定画风、属性色、构图与透明规则（即风格指南） |
| 2 | [art-resource-management.md](art-resource-management.md) | 资源类型、正式目录、命名、状态、清单与接入点 |
| 3 | [art-prompt-templates.md](art-prompt-templates.md) | 可直接填变量的生成提示词模板 |
| 4 | [art-generation-workflow.md](art-generation-workflow.md) | 从需求到游戏内验收的 SOP |
| 5 | [art-quality-standard.md](art-quality-standard.md) | 自动检查与人工视觉检查的通过条件 |
| 6 | [asset-manifest.md](asset-manifest.md) | 当前已导出资源、批次、来源与 QC 记录 |

权威顺序仍遵循项目总规范：用户当前要求、需求/技术/UI 设计文档优先于本目录。本目录只规定如何把已经确认的需求做成可用资源，不新增玩法或内容规模。

## 最短执行路径

1. 在 [art-resource-management.md](art-resource-management.md#新增资源登记) 填写五项新增需求；以配置 ID 和当前 `asset-manifest.md` 查重。
2. 选择 [art-prompt-templates.md](art-prompt-templates.md) 中对应类型，仅填写可变字段；将候选源图保存在现有 `docs/art/*-candidates/` 对应目录。
3. 按 [art-generation-workflow.md](art-generation-workflow.md) 的“生成、后处理、接入”执行已有轻量脚本；不要改正式目录结构。
4. 完成 [art-quality-standard.md](art-quality-standard.md) 的自动项和人工项，接入页面/Phaser 后做实机截图检查。
5. 在 `asset-manifest.md` 追加资源记录和 QC 链接，将状态更新到 `VERIFIED`。

## 历史资料与当前关系

以下文件保留为本轮生成的可追溯证据，**不是新增资源时的当前规范**：

- `docs/art/art-resource-inventory.md`：生成前基线与缺口盘点；
- `docs/art/art-resource-generation-execution-plan.md`：Batch 0～8 的历史执行矩阵；
- `docs/art/《宠物精灵》AI 美术资源批量生成提示词.md`：本轮的一次性任务提示词；
- `pet-spirit-art-skills/`：生成前的通用 Skill 草案，已与实际规格出现差异，见其 README 的归档说明。

不删除这些记录，以便复盘候选图、脚本处理和批次决策。今后只维护本目录中的当前规范和 `asset-manifest.md`。
