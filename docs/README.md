# 文档总索引

本目录是《宠物精灵》项目全部文档的统一索引。根目录 [README.md](../README.md) 负责项目入口与快速导航，本文件负责**完整文档目录**与文档维护约定。

> 文档优先级（来源 [AGENTS.md](../AGENTS.md)）：用户当前明确要求 > 需求/技术文档 > 分阶段开发规划 > 其余内容 > 历史结论。冲突时必须上报确认，禁止自行选择一方并隐式修改需求。

---

## 目录分类说明

| 子目录 | 职责 | 谁是维护者 |
|---|---|---|
| `requirements/` | 需求设计（玩法 / 数值 / 内容规模的**唯一依据**） | 需求变更时 |
| `technical/` | 技术方案与子系统设计 | 技术方案变更时 |
| `architecture/` | 当前系统实际结构（代码如何工作） | 实现与现状同步 |
| `planning/` | 开发阶段与规划（阶段范围 / 验收标准） | 阶段规划变更时 |
| `development/` | 开发规范与开发状态 | 规范与进度变更时 |
| `guide/` | 安装运行与玩家玩法指南 | 流程 / 玩法变更时 |
| `prompts/` | AI 开发提示词 / 任务模板 | 提示词迭代时 |
| `art/` | 美术生产规范、资源清单、批次 QC 与候选图 | 美术资源变更时 |

---

## 需求设计（权威来源）

- [宠物精灵游戏第一阶段需求设计文档 V1.0](requirements/宠物精灵游戏第一阶段需求设计文档 V1.0.md) — 玩法规则、数值规则、内容规模的唯一依据
- [宠物精灵游戏第一阶段 UI 设计文档 V1.0](requirements/宠物精灵游戏第一阶段UI设计文档 V1.0.md)

## 技术方案

- [宠物精灵游戏第一阶段技术方案说明 V1.0](technical/宠物精灵游戏第一阶段技术方案说明 V1.0.md) — 技术栈、架构边界、部署方式、数据方案
- [自动战斗系统设计](technical/AUTO_BATTLE_DESIGN.md)
- [Boss AI 重构方案](technical/BOSS_AI_REWORK.md)
- [动态难度、野外缩放、Boss 自适应与等级压制系统方案](technical/动态难度、野外缩放、Boss自适应与等级压制系统方案.md)

## 架构与代码结构

- [系统架构（当前实现）](architecture/ARCHITECTURE.md)
- [项目结构（代码目录与模块职责）](architecture/PROJECT_STRUCTURE.md)

## 开发规划

- [宠物精灵游戏分阶段开发规划 V1.0](planning/宠物精灵游戏分阶段开发规划 V1.0.md) — 开发顺序、阶段范围、验收标准
- [前五阶段开发修订计划](planning/前五阶段开发修订计划.md)
- [被动技能体系扩展计划](planning/passive-skill-expansion-plan.md)
- [项目文档体系重构方案](planning/项目文档体系重构方案.md)（历史规划记录）

## 开发规范与状态

- [开发状态与历史阶段记录](development/DEVELOPMENT_STATUS.md)
- [前端开发规范](development/FRONTEND_STANDARDS.md)
- [后端开发规范](development/BACKEND_STANDARDS.md)
- [测试规约](development/TESTING_STANDARDS.md)

## 安装运行与玩法

- [快速开始（安装与运行）](guide/QUICK_START.md)
- [玩家玩法说明](guide/GAMEPLAY.md)

## AI 开发提示词

- [第十阶段：智能自动战斗策略开发提示词](prompts/第十阶段：智能自动战斗策略开发提示词.md)

## 美术生产体系

> 美术规范统一见 [art/README.md](art/README.md)，以下为其中核心文档。

- [美术圣经（画风与规则）](art/art-bible.md)
- [资源管理与接入点](art/art-resource-management.md)
- [生成提示词模板](art/art-prompt-templates.md)
- [生成到验收 SOP](art/art-generation-workflow.md)
- [质量标准与 QC](art/art-quality-standard.md)
- [资源清单与批次记录](art/asset-manifest.md)

---

## 文档维护约定

1. **新增文档**：先判断所属分类，放入对应子目录；若无对应分类，先评估是否确需新增，再在 `requirements/` / `technical/` / `planning/` 中合理归属，避免在根目录堆积。
2. **移动 / 重命名文档**：必须同步检查全部引用（README、AGENTS.md、docs 内相对链接、脚本、CI），禁止只移动不修链。
3. **废弃文档**：不直接删除，统一移入 `docs/archive/`（如确需建立）并保留可追溯说明；无法确认是否有效时优先保留。
4. **内容权威性**：需求、技术方案、开发规划中的设计结论不得因整理而改动；本文档只负责索引与存放位置。
