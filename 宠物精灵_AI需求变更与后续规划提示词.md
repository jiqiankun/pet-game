# 《宠物精灵》桌面版需求变更与后续开发规划提示词

你现在作为《宠物精灵》项目的**高级游戏产品架构师、技术负责人和开发规划负责人**工作。你的任务不是立即重写项目，而是基于最新桌面版需求，对现有项目做完整的需求变更、现状核对、影响分析、技术方案同步和后续开发规划，并在后续阶段按规划落地。

## 一、最高优先级输入

首先完整读取并理解以下内容：

1. 最新需求文档：`宠物精灵_桌面版世界与UI重构_完整需求文档_V1.0`（或项目中对应的最新版本）。
2. 项目现有需求文档、技术方案文档、开发规划/阶段规划文档。
3. `README`、`AGENTS.md`、部署说明、目录说明以及项目中其他会约束开发行为的文档。
4. 当前前端页面、路由、状态管理、游戏循环、地图、战斗、任务、NPC、背包、队伍、宠物、图鉴、商店、存档等相关代码。
5. 当前地图配置、世界连接配置、宠物/技能/道具配置、地图 Tileset、战斗背景、音频、UI、美术资源与资源清单。
6. 已存在的测试、校验工具、脚本、开发工具以及已有的 AI 美术资源生产规范。

**不要只阅读文档而忽略实际代码，也不要只看代码而忽略现有规划。需要把“文档描述”和“实际实现”进行交叉核对。**

---

## 二、本次变更的总原则

本次只针对**桌面版本**进行变更。

不得在本轮中开发：

- 手机版 UI；
- 虚拟摇杆；
- 移动端触控交互；
- 手机 Bottom Sheet；
- 移动端页面适配专项；
- 队伍宠物跟随玩家进入探索世界。

最新需求是本轮桌面体验重构的**上游基线**。

如果以下内容和新需求冲突：

- 旧需求；
- 旧技术方案；
- 旧规划；
- 当前页面行为；
- 当前路由结构；
- 当前地图结构；
- 当前资源组织方式；

必须以**完整满足最新需求为第一前提**处理冲突。

### 资源复用原则

可以并且应该尽量复用现有：

- 宠物资源；
- 技能；
- 道具；
- 战斗逻辑；
- 自动战斗；
- 难度系统；
- 地图 Tileset；
- UI 元素；
- 音频；
- 战斗特效；
- 页面组件；
- 数据配置；
- 通用工具；
- 已成熟的业务模块。

但是必须遵守：

> **复用资源是降低开发成本的手段，不是降低需求标准的理由。**

如果已有资源不能满足新需求，应选择：

1. 直接复用；
2. 适配后复用；
3. 重组后复用；
4. 替换；
5. 新增。

不得为了保护旧实现而把新的 World-Persistent UI、Connected World、WorldGraph、地图联动等需求降级成“旧页面换皮”。

同时也不得为了“彻底重构”而无理由推翻已经满足需求的成熟模块。遵循**最小必要改造 + 需求完整达成**。

---

## 三、必须理解的核心体验方向

后续所有方案必须围绕以下原则：

### 1. World-Persistent UI

世界/探索地图是游戏根节点。

队伍、宠物、背包、任务、地图、商店、对话、战斗等行为，应表现为世界上的 Panel、Overlay、Dialog、Battle Context，而不是频繁跳转到与世界割裂的页面。

关闭功能后，必须立即回到原来的世界状态继续探索。

### 2. 地图就是桌面

探索地图长期作为桌面主舞台。

中央视觉区域应尽量保持干净；HUD 只承担当前决策信息，详细数据按需展开。

### 3. World → Region → Map

世界采用三级空间结构。

所有 Map 通过 Gateway / Connection 构成可信的 WorldGraph。

技术上可以分地图，但体验上必须连续。

### 4. WorldGraph 是空间单一真相源

以下系统不得分别维护互相冲突的地图连接关系：

- 实际 Map；
- 世界地图；
- 区域地图；
- 导航；
- 任务地点；
- 快速旅行；
- 捷径；
- Discovery。

### 5. World Truth / Player Knowledge / World State 分离

至少从语义上区分：

- 世界客观有什么；
- 玩家知道什么；
- 世界现在处于什么状态。

### 6. 战斗打断探索，但不能切断世界

探索：

`Explore → Encounter → Battle → Result → Explore`

而不是：

`MapPage → BattlePage → Reload MapPage`

胜利、捕捉、逃跑应合理恢复原地图位置；战败返回安全点。

### 7. 系统告诉方向，玩家决定怎么走

默认使用软导航：

- 地标；
- 路牌；
- 区域目标；
- 下一出口；
- Region/World Map。

不得默认使用自动跑图和持续 GPS 线路。

### 8. 地点必须值得被记住

主要 Map 必须拥有明确：

- 身份；
- 主地标；
- 相邻关系；
- 视觉差异；
- 路线；
- 首次体验；
- 回访用途。

不要用大量“森林01、森林02”式地图制造规模。

---

## 四、第一阶段工作：现状盘点，不要立即开发

先扫描项目，输出一份正式文档，例如：

`docs/planning/desktop-world-ui-rework-impact-analysis.md`

文档至少包括：

### 1. 当前页面架构

列出：

- 探索页；
- 战斗页；
- 队伍；
- 宠物；
- 背包；
- 任务；
- 地图；
- 图鉴；
- NPC/对话；
- 商店；
- 系统菜单；
- 其他相关页面。

记录：

- 当前是否路由跳转；
- 是否会销毁地图；
- 是否保留玩家位置；
- 是否符合 World-Persistent UI。

### 2. 当前地图体系

盘点：

- Map 数量；
- Region 是否存在；
- Map 是否完全独立；
- 出入口；
- Spawn Point；
- 地图连接；
- 世界地图；
- 小地图；
- 快速旅行；
- 营地；
- Boss Map；
- 地图状态；
- 隐藏路线；
- 捷径。

### 3. 当前游戏循环

核对：

`探索 → 遭遇 → 战斗 → 捕捉/胜利 → 结算 → 返回探索`

当前哪些步骤会：

- 跳页；
- 重载；
- 丢失 Context；
- 重置坐标；
- 产生明显体验割裂。

### 4. 当前资源盘点

按照以下分类输出：

- 可直接复用；
- 需要适配；
- 需要重组；
- 需要替换；
- 缺失，需要新增。

尤其检查：

- 地图 Tileset；
- 地标；
- Gateway；
- 营地；
- 城镇；
- 战斗环境；
- HUD；
- Panel；
- 地图图标；
- 音乐；
- 环境音。

### 5. 差距矩阵

形成表格：

| 新需求 | 当前实现 | 差距 | 是否复用 | 改造方案 | 风险 | 优先级 |
|---|---|---|---|---|---|---|

不得只写笼统的“需要优化”。

---

## 五、第二阶段工作：同步修改项目文档

在开始大规模代码改造前，先根据分析修改或补充：

1. 需求文档；
2. 技术方案；
3. 分阶段开发规划；
4. 必要时的架构说明；
5. 地图设计规范；
6. UI/交互规范；
7. 测试/验收规范；
8. README 中的相关目录和文档索引。

保证文档之间没有互相矛盾的规则。

### 技术方案至少需要覆盖

- World-Persistent UI；
- UI Layer / Context Stack；
- Input Context；
- Explore / Dialog / Shop / Map / Battle Context；
- WorldGraph；
- Region / Map / Gateway / Connection；
- Map Design Contract；
- Anchor / Zone；
- SafePoint；
- Fast Travel；
- Discovery；
- Player Knowledge；
- World State；
- Encounter Session；
- Battle returnContext；
- AutoSave；
- Map Validator；
- 地图/导航 Single Source of Truth。

不要只修改需求文档而让技术和规划继续使用旧架构。

---

## 六、第三阶段工作：制定新的开发规划

不要一次同时重构整个世界。

优先规划一个完整的 **Vertical Slice Region**。

可优先选择现有最适合作为样板的城镇 + 野外 Region；如果项目已有森林相关地图，优先考虑复用并改造成类似：

```text
城镇
 ↓
森林入口
 ↓
森林南部
 ↓
猎人营地
 ↓
森林深处
 ↓
Boss区域
 ↓
古树核心
```

需要在这个垂直切片中跑通：

1. World 常驻；
2. HUD；
3. 队伍 Panel；
4. 背包 Panel；
5. 任务联动；
6. 世界地图；
7. Region Map；
8. Mini Map；
9. Gateway；
10. 双向切图；
11. 玩家位置与朝向恢复；
12. 营地；
13. SafePoint；
14. Encounter；
15. Battle Overlay；
16. Battle Result；
17. 逃跑；
18. 战败返回；
19. Discovery；
20. Landmark；
21. Shortcut；
22. Boss；
23. Boss 后 World State；
24. Fast Travel；
25. AutoSave；
26. Validator；
27. 回归测试。

Vertical Slice 验收通过以后，再逐个 Region 扩展。

---

## 七、规划必须包含的阶段建议

请根据项目当前状态重新判断阶段编号，不要盲目套用旧阶段。

新的规划至少应包含以下类型的工作包：

### A. 基础架构层

- World 根节点；
- Context Stack；
- Input 管理；
- Panel/Overlay 管理；
- World Simulation Pause；
- WorldGraph。

### B. 地图基础设施

- Region；
- Map；
- Gateway；
- Connection；
- Anchor；
- Zone；
- World Position / Display Position；
- Map Transition；
- Preload/Cache。

### C. 地图认知与导航

- UNKNOWN / KNOWN / VISITED / MAPPED；
- Connection Knowledge；
- Connection Traversal；
- Landmark Discovery；
- Region Map；
- World Map；
- Mini Map；
- Route Planning；
- Navigation Context。

### D. 冒险节点

- Town；
- Camp；
- SafePoint；
- Rest；
- Fast Travel；
- Defeat Return。

### E. 遭遇与战斗衔接

- Visible Encounter；
- Aggro；
- Chase；
- Encounter Transition；
- Encounter Session；
- Battle Overlay；
- Result；
- Return Context；
- Post-battle Protection。

### F. 任务与世界状态

- Quest + World Event；
- World Flags；
- Boss world changes；
- Shortcut；
- Bridge/Gate/Camp state；
- NPC state variations。

### G. 内容生产

- Region Contract；
- Map Contract；
- Landmark；
- Spawn Zone；
- Encounter Pool；
- BGM/Mood；
- Environment Tags；
- Battle Environment；
- Existing asset reuse。

### H. 工具与质量保证

- WorldGraph Debug View；
- Gateway Checker；
- Unreachable Map Checker；
- Direction Mismatch Warning；
- Missing Landmark Warning；
- SafeZone Spawn validation；
- Shortcut unlock test；
- Discovery/Navigation regression tests。

---

## 八、地图设计必须执行 Map Design Contract

每个主要 Map 在实现前至少要有：

```text
Map ID
Map Name
Region
Gameplay Identity
Player Emotion
Primary Landmark
Secondary Landmark
Main Flow
Optional Flow
Decision Points
Gateways
Connections
Anchors
Safe Zones
Spawn Zones
No Spawn Zones
Quest/Event Zones
World State Conditions
Dynamic System Support
Environment Tags
Battle Environment
Mini Map behavior
Region Map node/display position
First Visit purpose
Return Visit purpose
Estimated first exploration time
Estimated pass-through time
Expected revisit count
```

如果缺少核心内容，不要直接进入地图实现。

尤其以下情况需要警告：

- EXPLORE Map 没有主地标；
- Gateway 无目标；
- 非单向 Gateway 不成对；
- 普通空间连接方向明显不一致；
- Map 从世界起点不可达；
- 主线目标只有 UNKNOWN 隐藏路线才能到达；
- Safe Zone 中存在主动 Encounter Spawn；
- Boss Map 没有 Boss/世界状态用途；
- 普通连接 Map 没有实际体验价值；
- 一张普通地图遭遇密度明显过高。

---

## 九、页面/UI 改造要求

重点检查现有前端路由。

对于以下功能，优先改造成 World 上层 UI：

- 队伍；
- 宠物；
- 背包；
- 任务；
- 地图；
- NPC 对话；
- 商店；
- 战斗；
- 战斗结果。

探索界面推荐层次：

```text
World Layer
HUD Layer
Interaction Layer
Panel Layer
Dialog Layer
Battle Layer
Effect / Notification Layer
```

必须实现：

- Esc Context Stack；
- Blocking / Non-blocking Context；
- 输入焦点切换；
- 打开 Blocking UI 时世界暂停；
- UI 关闭后恢复 World；
- HUD 根据 Explore/Dialog/Shop/Map/Battle Context 变化；
- 页面不因打开功能而重建地图。

不要只把旧页面改成 modal 外观，而内部仍然触发地图卸载、位置丢失或路由重建。

---

## 十、地图与任务必须联动

任务地点不得单独维护一套地图坐标或连接关系。

任务应尽量使用：

- Region；
- Map；
- Landmark；
- Anchor；
- Zone；
- World Event；

而不是裸像素坐标。

例如：

```text
目标：翡翠森林 · 森林深处 · 巨石桥西侧
```

而不是：

```text
targetX=123,targetY=456
```

后者可以作为底层实现细节，但不得成为跨系统唯一语义。

---

## 十一、战斗改造重点

现有战斗算法、技能、宠物、AI、难度系统能复用则优先复用。

重点变更战斗的**进入和退出方式**。

必须保证：

```text
Explore
→ Encounter
→ Battle Context
→ Result
→ Restore World / SafePoint
```

战斗开始记录：

- sourceMap；
- sourcePosition；
- sourceFacing；
- sourceCamera；
- enemyEntity；
- encounterType；
- battleEnvironment；
- returnContext；
- safeReturnPoint。

胜利：

- 敌方实体正确消失；
- 原位置恢复；
- Result 可快速关闭。

捕捉：

- 世界实体消失；
- 图鉴/捕捉数据更新；
- 返回原地。

逃跑：

- 敌方不永久消失；
- 拉开安全距离；
- 短暂禁止再次追击。

战败：

- 执行战败结果/嘲讽；
- 返回最近已激活 SafePoint；
- 不回滚已完成的重要探索/世界状态。

Boss：

- 胜利后返回世界现场；
- 展示道路/NPC/环境变化；
- 不直接传送到下一章。

---

## 十二、现有游戏内容的处理

不要因为 UI/地图架构重构而随意删除：

- 现有宠物；
- 现有主动技能；
- 被动技能；
- 技能书；
- 自由属性点；
- 捕捉规则；
- 留生一击；
- 自动战斗策略；
- Boss AI；
- 难度系统；
- 战败嘲讽；
- 现有道具；
- 现有图鉴；
- 已完成并合理的业务逻辑。

这些系统原则上应继续存在并被新的 World/Context/UI 架构承载。

如有冲突，明确写出：

```text
旧行为
→ 与新需求冲突原因
→ 调整后的行为
→ 数据迁移/兼容策略
```

---

## 十三、测试和验收不能缺失

每个阶段必须包含：

1. 单元测试；
2. 集成测试；
3. UI 行为测试；
4. WorldGraph/Map Validator；
5. 回归测试；
6. 手工体验验收项。

重点场景：

### Gateway

```text
A.north → B.south
B.south → A.north
```

验证：

- Map 正确；
- Spawn 正确；
- Facing 正确；
- 返回正确。

### Hidden Route

未发现：

- Map 不显示；
- Navigation 不使用。

发现：

- Map 更新；
- Navigation 可用。

### Shortcut

Boss/事件前：BLOCKED。

完成事件后：OPEN。

同时验证：

- World；
- Region Map；
- Navigation；
- Quest；
- Save。

### Context

背包/地图/对话打开：

- 玩家不移动；
- World Pause；
- Esc 正确返回。

关闭：

- 不自动继续旧按键移动。

### Battle

验证：

- 胜利；
- 捕捉；
- 逃跑；
- 战败；
- Boss；
- 剧情战斗 returnContext。

---

## 十四、最终输出要求

完成本轮“需求变更与规划”时，先不要直接声称开发完成。

至少输出/更新以下成果：

### 文档

1. 桌面版需求更新；
2. 技术方案更新；
3. 需求影响分析；
4. 世界/地图架构说明；
5. Map Design Contract；
6. UI Context/交互规范；
7. 新的分阶段开发规划；
8. 验收/测试说明；
9. README/文档索引更新。

### 规划摘要

需要明确：

- 哪些旧功能无需改；
- 哪些需要轻量适配；
- 哪些需要重构；
- 哪些资源直接复用；
- 哪些资源需要补充；
- 最大技术风险；
- 推荐实施顺序；
- Vertical Slice 范围；
- 后续 Region 扩展策略。

### 需求覆盖矩阵

最后形成：

| Requirement | Design | Implementation Stage | Test | Status |
|---|---|---|---|---|

确保需求没有因为阶段拆分而遗漏。

---

## 十五、工作原则

执行过程中遵守：

> **先理解现有项目，再修改文档；先统一需求和架构，再改代码；先跑通一个完整 Region，再扩全世界。**

> **可以复用现有资源，但必须以完成需求为第一前提。**

> **不要为了减少开发工作而保留破坏体验的旧架构，也不要为了追求“新架构”无意义重写成熟业务逻辑。**

> **最终目标不是“完成一次 UI 改版”，而是让《宠物精灵》的探索、地图、任务、剧情、战斗、营地和世界状态真正成为一个连续、可维护、可扩展的桌面游戏系统。**
