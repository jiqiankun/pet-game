# 桌面版世界/UI 重构阶段 0：需求裁决与基线

> 状态：阶段 0 已完成  
> 基线日期：2026-08-19  
> 适用范围：仅桌面端  
> 上游需求：`docs/requirements/宠物精灵_桌面版世界与UI重构_完整需求文档_V1.0.md`  
> 后续任务：`docs/planning/宠物精灵_需求变更与桌面版世界UI重构_详细任务规划.md`

## 1. 文档优先级

本轮桌面版世界/UI 重构采用以下优先级：

1. 用户当前明确要求。
2. `宠物精灵_桌面版世界与UI重构_完整需求文档_V1.0.md` 的 MUST/SHOULD/MAY。
3. 第一阶段需求设计与技术方案中未被本轮需求覆盖的玩法、数值和架构规则。
4. 新版详细任务规划。
5. 第一阶段历史规划、旧 UI 设计和旧 Overlay 方案。

新需求只覆盖与桌面世界、地图、导航、页面联动、战斗上下文、动态世界和桌面表现直接冲突的内容。宠物、技能、队伍、捕捉、Boss、自动战斗等未冲突规则继续有效。

## 2. 正式需求裁决

### D0-01：交付终端

- 裁决：本轮只开发和验收桌面端。
- 包含：常见 16:9 桌面/笔记本分辨率与超宽桌面窗口。
- 不包含：手机、平板、触控导航、虚拟摇杆、Bottom Sheet 和移动端专项回归。
- 处理现有小屏样式：保留现状，不因本轮主动修改或验收；后续如需移动端必须单独立项。
- 依据：R-001 和用户 2026-08-19 明确确认。

### D0-02：战斗道具

- 裁决：战斗内允许使用服务端明确标记可用的既有道具，覆盖手动和自动战斗；旧“战斗内无道具行动”裁决被本轮 R-043 替代。
- 数据源：探索背包和战斗背包共享玩家库存，以 `/api/inventory` 和服务端战斗资源快照为事实源，不创建第二份战斗库存。
- 当前允许范围：
  - `usableInBattle=true` 且 `itemType=HEAL_HP`：对存活且未满血己方宠物使用。
  - `usableInBattle=true` 且 `itemType=REVIVE`：对倒下己方宠物使用。
  - 捕捉球仍通过 CAPTURE 行动和既有捕捉规则处理，不混入恢复道具 ITEM 行动。
- 消耗时机：开战时读取库存快照，战斗过程中只记内存消耗，结算时统一扣减数据库库存。
- 默认行为：自动使用恢复/复苏道具仍默认关闭，必须由玩家明确开启。
- 边界：前端过滤只用于展示；服务端必须同时校验 `usableInBattle`、类型、快照数量和目标合法性。当前服务端主要按类型和快照校验，补齐 `usableInBattle` 强制校验归入阶段 5。

### D0-03：动态世界

- 裁决：实现轻量游戏内时间、有限昼夜阶段、每 Region 少量天气、低频短时事件和持久剧情变化。
- 时间来源：游戏内状态和明确的游戏行为推进，不绑定操作系统时间、现实日期或后台定时刷新。
- 第一轮玩法影响：优先表现、可见性和遭遇生态；不急于增加大量战斗数值修正。
- 明确不做：季节、复杂天气预测、真实时间刷新、NPC 全日程、复杂生态模拟和实时倒计时焦虑。
- 依据：R-171～R-177；覆盖第一阶段“完全不做昼夜/天气”的旧范围结论。

### D0-04：旧路由兼容期限

- 裁决：Hash Router 和现有 18 个路由暂时保留，但宠物、队伍、背包、任务、图鉴、商店、Boss、战斗等路由只作为直接访问、异常恢复和开发调试兼容入口。
- 阶段 1～6：新世界内主流程必须改走 Context/Overlay；同一业务组件兼容路由和 Overlay，不复制两套实现。
- 阶段 10：根据调用方搜索和浏览器 E2E 结果，移除主导航中的旧功能路由入口，并删除已经无调用方的重复战斗页面逻辑。
- 阶段 11：发布前确认正常玩家流程不依赖功能路由跳转；仍保留的兼容路由必须在文档中列明用途和退出条件。

### D0-05：Region → Map 迁移

- 裁决：采用兼容映射后渐进拆分，不一次性推翻当前 6 个 RegionConfig/Map ID。
- 阶段 2：现有 `MAP_START_VILLAGE`、`MAP_AREA_*` 先同时映射为 Region 下的兼容 Map，旧存档 `current_map_id` 可继续解析。
- 阶段 3：森林纵切开始使用明确 Region ID、Map ID、Gateway 和 Anchor；写入新位置模型时保留旧字段兼容读取。
- 阶段 8：逐区域迁移剩余内容；只有所有调用方、存档样本和图谱校验通过后，才能删除 Region=Map 的兼容假设。
- ID 原则：新 Map ID 稳定且只表示地图节点；Region ID 不再兼作坐标、Tiled 文件或地图会话 ID。

## 3. 当前代码基线

### 3.1 构建与测试

| 项目 | 阶段 0 前基线 | 阶段 0 完成值 | 结果 |
|---|---:|---:|---|
| 后端测试 | 511 | 512 | 0 失败/0 错误/0 跳过；新增 1 项存档样本兼容测试 |
| 前端 `vue-tsc -b` | 通过 | 通过 | 零类型错误 |
| 前端 Vite build | 通过 | 通过 | 存在 Explore 大块告警 |
| ExploreView JS 块 | 1,709,979 bytes | 1,709,979 bytes | 性能改造基线，不是阶段 0 阻断项 |

统一复现命令：

```powershell
pwsh -File scripts/phase0-baseline.ps1 -MavenHome C:\Users\17632\Tools\apache-maven-3.9.16
```

该脚本执行前端生产构建、后端全量测试，并校验 R-001～R-205 连续性、内容数量、资源数量和 Explore 构建块。

若 Node/Java/Maven 未加入当前终端 PATH，可分别使用 `-NodePath`、`-JavaHome`、`-MavenHome`；受限环境读取本机 Maven 缓存时可额外传 `-MavenRepository`。`-SkipBuild` 只用于读取最近一次构建/测试报告，不替代完整验收。

### 3.2 内容与资源

| 内容 | 当前数量 | 事实来源 |
|---|---:|---|
| 宠物种族 | 27 | `pets.yml` |
| 主动技能 | 85 | `skills.yml` |
| 被动技能 | 51 | `skills.yml` |
| 道具 | 45 | `items.yml` |
| Boss | 8 | `bosses.yml` |
| 任务 | 25 | `quests.yml` |
| 成就 | 33 | `achievements.yml` |
| Region/兼容 Map 配置 | 6 | `maps.yml` |
| Tiled 地图 JSON | 6 | `frontend/public/assets/maps` |
| 前端 PNG 资源 | 292（约 93.36 MB） | `frontend/public/assets` |
| 音频资源 | 0 | `frontend/public/assets` |
| Flyway 迁移 | V1～V13 | `backend/src/main/resources/db/migration` |
| 存档结构版本 | 1 | `game.save-version` |

### 3.3 当前桌面交互路径

| 场景 | 当前路径 | 基线结论 |
|---|---|---|
| 功能导航 | MainLayout 顶栏 RouterLink → 独立 View | 会卸载 Explore，是阶段 1 改造对象 |
| 地图遇敌 | Explore/MapScene → BattleOverlay | 已有可复用路径，但返回和实体状态不完整 |
| 直接战斗 | `/battle` → BattleView | 与 BattleOverlay 重复，只保留兼容/调试用途 |
| 世界地图进入区域 | WorldMapView → `router.push('/explore')` | 即使嵌入 Overlay 仍跳路由，是阶段 1/4 改造对象 |
| 普通出口 | 接触 → 确认 → enterRegion → BootScene 重启 | 不符合连续世界，是阶段 3 改造对象 |
| NPC/商店 | 对话 → 硬编码 NPC ID → Shop Overlay | Overlay 可复用，动作来源需在阶段 6 配置化 |

桌面视觉证据目录为 `docs/development/baseline-assets/`，只定义并采集桌面视口；手机、平板和触控不采集。本次验收机 MySQL84 服务无法由当前账户启动，且应用内浏览器连接组件拒绝载入，因此未伪造运行态截图；目录内记录了采集口径、失败原因和补采条件。该环境限制不影响需求裁决、代码/资源计数、存档兼容、前端构建及后端测试基线。

## 4. 存档迁移准备

### 4.1 基线样本

| 样本 | 目录 | 关键状态 | 当前期望 |
|---|---|---|---|
| 新游戏 | `backend/src/test/resources/save-fixtures/new-game` | 据点、初始宠、默认队伍、基础背包 | 导入后保留初始宠/队伍/库存和 `MAP_START_VILLAGE` |
| 主线中段 | `backend/src/test/resources/save-fixtures/mid-game` | 森林、主线 05、区域/营地/会话 | 导入后保留任务、地图、营地和库存 |
| 内容完成 | `backend/src/test/resources/save-fixtures/completed` | 遗迹、主线完成、Boss 计数、成就、地图变化 | 导入后保留完成标记和主要永久进度 |

样本采用解包后的 `manifest.json + save.json`，没有机器路径、口令或真实玩家数据。`SaveBackupServiceTest` 会将其打包成正式 `.pet-save.zip` 结构并通过当前导入服务验证。

### 4.2 导入基线修复

阶段 0 核对发现 `SaveBackupService.importSave` 的注释和设计均要求“备份 → 清理旧存档 → 导入”，实际实现却缺少清理调用。若直接导入会让旧、新 `player` 并存，后续单主存档查询不确定。阶段 0 已补回 `deleteAll(oldSaveId)`，并在既有测试中验证导入前确实备份和清理；该操作仍位于同一事务内，导入失败由事务回滚。

### 4.3 后续迁移检查项

阶段 2 新增世界字段/表时，三个样本必须继续验证：

- `saveVersion=1` 可被读取；新增字段从 `currentMapId` 和默认 Anchor 推导。
- 宠物、技能、队伍、背包、任务、Boss、成就和地图变化不丢失。
- 新位置无效时只回退位置，不重置其他玩家进度。
- 迁移后再次导出使用新 saveVersion；旧样本原文件保持不变。

## 5. 需求追踪矩阵

状态说明：本表只表示“已经分配实施阶段和验收前缀”，不表示需求已经实现。

| 需求编号 | 主题 | 实施阶段 | 验收前缀/处理 |
|---|---|---|---|
| R-001～R-003 | 桌面范围、文档一致性、资源复用 | 0 | `D0-DOC` |
| R-004～R-012 | World-Persistent UI 与总体原则 | 1～11 | `CTX`、`E2E-WORLD` |
| R-013～R-015 | 明确不做与禁止旧页面换皮 | 0、1 | `D0-SCOPE`、`CTX-ROUTE` |
| R-016～R-022 | World/HUD/Panel/Blocking/返回层级 | 1、10 | `CTX-STACK`、`DESKTOP-LAYER` |
| R-023～R-028 | HUD、小地图和反馈层级 | 4、10 | `MAP-HUD`、`DESKTOP-HUD` |
| R-029～R-037 | 键鼠、快捷键、Input Context、相机 | 1、3、10 | `CTX-INPUT`、`MAP-CAMERA` |
| R-038～R-040 | 快速队伍、宠物详情、战斗换宠 | 5、6 | `FEATURE-TEAM`、`BATTLE-SWITCH` |
| R-041～R-043 | 探索/战斗背包与目标选择 | 0、5、6 | `D0-ITEM`、`BATTLE-ITEM` |
| R-044～R-046 | 任务面板、地图联动与反馈 | 4、6、7 | `FEATURE-QUEST`、`MAP-CONTEXT` |
| R-047～R-050 | NPC、商店、图鉴等窗口 | 6 | `FEATURE-NPC`、`FEATURE-POKEDEX` |
| R-051～R-059 | World→Region→Map、Gateway 与图谱事实 | 2、3 | `GRAPH-CORE`、`MAP-GATEWAY` |
| R-060～R-066 | 世界规模、Region 章节与地图职责 | 3、8 | `MAP-CONTRACT`、`CONTENT-REGION`；SHOULD 不硬编码数量 |
| R-067～R-073 | Map Contract、Anchor、可达性 | 2、3、8 | `GRAPH-VALIDATE`、`MAP-ARRIVAL` |
| R-074～R-081 | Region 身份、城镇/野外/Boss 空间 | 3、8、9 | `CONTENT-IDENTITY` |
| R-082～R-085 | World Map | 4 | `MAP-WORLD` |
| R-086～R-088 | Region Map 与隐藏连接 | 4 | `MAP-REGION` |
| R-089～R-092 | Mini Map 与来源 Context | 4 | `MAP-MINI`、`MAP-CONTEXT` |
| R-093～R-102 | 地点/路线知识、发现事件 | 2、4、7 | `KNOWLEDGE`、`DISCOVERY` |
| R-103～R-110 | 软导航、强导航和地理语言 | 4 | `NAV-SOFT`、`NAV-ROUTE` |
| R-111～R-123 | Gateway、营地、安全点与地图状态 | 2、3、4、7 | `MAP-STATE`、`SAFEPOINT` |
| R-124～R-131 | 可视遭遇、警示、追击和保护 | 5 | `ENCOUNTER` |
| R-132～R-141 | Battle Context、返回和 Boss 世界后果 | 5、7 | `BATTLE-RETURN`、`WORLD-BOSS` |
| R-142～R-150 | 冒险耐力、营地与 Boss 重试成本 | 5、7、8 | `ADVENTURE` |
| R-151～R-161 | 任务、World Gate、NPC/世界变化 | 6、7、8 | `WORLD-QUEST`、`WORLD-CHANGE` |
| R-162～R-170 | 捷径、快速旅行、失败恢复、自动保存 | 2、4、7 | `TRAVEL`、`AUTOSAVE` |
| R-171～R-177 | 游戏内时间、天气、低频事件、生态 | 9 | `DYNAMIC-WORLD` |
| R-178～R-184 | 音乐、环境音和交互信息密度 | 9、10 | `AUDIO`、`DESKTOP-VISUAL` |
| R-185～R-191 | WorldGraph/WorldState/Knowledge 与校验 | 2、7、11 | `GRAPH-DATA`、`GRAPH-VALIDATE` |
| R-192～R-199 | 桌面分辨率、缓存、预载、碰撞、阻塞 | 1、3、10、11 | `DESKTOP`、`PERF`、`E2E-BLOCKING` |
| R-200～R-205 | 现状盘点、复用、纵切和阶段验收 | 0、3～11 | `D0-AUDIT`、各阶段 `DONE` |

MUST 共 199 项、SHOULD 5 项（R-060、R-061、R-072、R-073、R-108）、MAY 1 项（R-109）。所有 205 个编号均已分配实施阶段；没有移动端需求被纳入。

## 6. 世界内容阻断缺陷登记

| ID | 当前实际问题 | 阻断阶段 | 进入后续阶段前的处理 |
|---|---|---|---|
| W0-01 | 森林 YAML 声明通向水域/雷地区，Tiled 只有返回草甸出口 | 3、8 | WorldGraph/Tiled 校验先报错，纵切修正连接 |
| W0-02 | 遗迹没有 Tiled/YAML 返回出口 | 8 | 明确单向理由或补回合法 Gateway，禁止软锁 |
| W0-03 | Tiled `BOSS_MEADOW`/`BOSS_FOREST` 与 Boss 配置 ID 不一致 | 3、8 | 使用稳定 Boss ID，删除静默选首个 Boss 兜底 |
| W0-04 | 多个已配置 NPC 未放入 Tiled | 3、8 | 按 LocationRef/Anchor 放置并做引用校验 |
| W0-05 | `activatedMapChanges` 后端已返回但前端/Phaser 未消费 | 2、3、7 | 对齐 DTO，建立幂等场景状态应用入口 |
| W0-06 | 当前校验不解析 Tiled 对象层 | 2 | 增加对象 ID、Gateway、Boss/NPC/任务和可达性校验 |
| W0-07 | 胜利不移除实体、逃跑不解除 engaged、失败不应用复活点 | 5 | 统一 ReturnContext 和结算状态机 |
| W0-08 | `ExploreView` 构建块约 1.71 MB | 10 | 以当前数值为性能基线，按需加载和去重 |

## 7. 阶段 0 验收记录

| 验收项 | 状态 | 证据 |
|---|---|---|
| 战斗道具范围与数据源已裁决 | ✅ | D0-02；权威文档同步 |
| 动态世界范围已裁决 | ✅ | D0-03；权威文档同步 |
| 仅桌面端范围一致 | ✅ | D0-01；R-001；用户确认 |
| 旧路由兼容期限已确定 | ✅ | D0-04 |
| Region→Map 迁移策略已确定 | ✅ | D0-05 |
| R-001～R-205 追踪完整 | ✅ | §5；`scripts/phase0-baseline.ps1` 连续性校验 |
| 三类脱敏存档样本可导入 | ✅ | `SaveBackupServiceTest`；新游戏/中期/完成态均通过 |
| 前端 build / 后端测试可复现 | ✅ | `scripts/phase0-baseline.ps1`；前端通过、后端 512 项全绿 |
| 桌面视觉口径与操作路径已留档 | ⚠️ 环境限制 | §3.3、`baseline-assets/README.md`；运行态截图待具备 MySQL 服务控制权限后补采，不阻断阶段 1 |
| 世界内容阻断缺陷已登记 | ✅ | §6 |

阶段 0 的需求、迁移、构建、测试和文档闸门均已通过，可以进入阶段 1/2。运行态截图是已登记的环境补采项，不得被误写为功能验收通过，也不阻断后续架构开发。
