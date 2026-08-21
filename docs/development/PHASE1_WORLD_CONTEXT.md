# 桌面版世界/UI 重构阶段 1：常驻世界根、Context Stack 与输入基础

> 状态：**实现完成，运行态验收进行中（独立测试存档补验已部分完成）**  
> 日期：2026-08-21  
> 范围：仅桌面端；不包含手机、平板、触控与移动端专项适配。

## 1. 本阶段已实现内容

- `/explore` 改由 `WorldRoot.vue` 承载，并在 `App.vue` 中仅缓存该世界根；正常游戏内功能通过 Context/Overlay 打开，探索 Phaser 实例不会因功能窗口而卸载。
- `useOverlayStore` 形成最小 Context Stack。每个条目包含实例 `id/key`、类型、阻塞语义、父 Context、来源、关闭策略、可选返回数据和打开前焦点；关闭可按实例精确命中，按类型关闭时命中最靠上的同类实例。
- 定义 WORLD、PANEL、DIALOG、BATTLE、TEXT_INPUT 输入上下文。PANEL/DIALOG 冻结探索和野怪更新，BATTLE 使用最高锁定；奖励保持非阻塞世界输入。
- 地图遭遇确认、营地、出口、随机事件、事件结果、奖励、NPC 对话、功能窗口和战斗均接入同一 Context Stack；MapScene 重启后通过 `map:ready` 重新同步栈顶暂停语义。
- 全局快捷键收敛为 Q（快捷队伍）、B（背包）、J（任务）、M（地图）与 Esc；W/A/S/D、E 仍仅由 Phaser 接收。输入框、重复按键、窗口失焦与页面隐藏不会透传持续移动。
- 通用窗口、NPC 对话、探索确认框和战斗层已接入初始焦点、Tab 边界、ARIA dialog 标注；关闭最外层 Context 时恢复原触发元素或地图焦点根。
- 世界地图嵌入 Context 后，关闭地图不再重启当前 Phaser 场景；只有实际传送/进入区域才发出地图重启命令。旧直达路由仍保留兼容。
- 世界图新增最小 `REGION_MAP` 子 Context，复用既有兼容地图数据，不引入阶段 2 的 WorldGraph、存档或接口变更；可形成 `WORLD → WORLD_MAP → REGION_MAP` 的严格返回链。
- 快捷队伍进入完整队伍改为原子替换栈顶，避免关闭与打开之间的焦点回退；触发元素已卸载时回退到世界焦点根。
- 世界页只建立一个稳定历史保护位。浏览器返回有 Context 时关闭栈顶并恢复保护位，空栈后才继续真实路由返回，不随浮层反复写入历史记录。

## 2. 关键实现位置

- `frontend/src/views/Explore/WorldRoot.vue`
- `frontend/src/views/Explore/ExploreView.vue`
- `frontend/src/stores/overlay.ts`
- `frontend/src/layouts/MainLayout.vue`
- `frontend/src/composables/useKeyboardShortcuts.ts`
- `frontend/src/components/overlay/OverlayLayer.vue`
- `frontend/src/components/overlay/OverlayShell.vue`
- `frontend/src/game/bridge/GameBridge.ts`
- `frontend/src/game/scenes/MapScene.ts`
- `frontend/src/views/WorldMap/WorldMapView.vue`

## 3. 已完成验证

| 项目 | 结果 | 证据 |
|---|---|---|
| 前端类型检查 | 通过 | `vue-tsc -b` 零错误 |
| 前端生产构建 | 通过 | Vite build 成功；世界根构建块 1,713,760 bytes |
| 后端全量回归 | 通过 | Maven：512 项，失败 0、错误 0、跳过 0 |
| 基线复核 | 通过 | `scripts/phase0-baseline.ps1 -SkipBuild`：R-001～R-205、内容/资源数量与世界根构建块均通过；世界根构建块 1,713,760 bytes |
| MySQL84 启动与应用烟测 | 通过 | MySQL84 为 `Running`；Spring Boot 已连接 `pet_game`、Flyway 校验 13 个迁移且无待执行迁移；`GET /api/game/save-status` 返回 `hasSave=true`，Vite 首页返回 200 |
| 浏览器 E2E（无写入路径） | 部分通过 | 官方浏览器组件已恢复。M/Q/B/J 打开不改 Hash；世界实例与单 Canvas 保持；`WORLD → WORLD_MAP → REGION_MAP` 连续 Esc 严格 LIFO；随机事件冻结画面；焦点、文本输入、浏览器返回与旧路由兼容均已实测 |
| 浏览器 E2E（独立测试存档） | 部分通过 | 已创建并覆盖测试存档，接受 `QUEST_MAIN_01`；任务详情的“地图查看”形成 `QUEST → WORLD_MAP → REGION_MAP`，连续 Esc 后对话数为 2→1→0；NPC、出口确认、奖励与地图遭遇均接入实际 UI 处理链；战斗 Esc/浏览器返回均不能退出 Battle Context |

## 4. 运行态补验清单

### 4.1 已通过的浏览器用例（2026-08-21）

1. 进入已有存档 `/explore` 后，Q/B/J/M 打开与关闭均保持 `#/explore`、同一 `data-world-instance-id` 和单个 Canvas。
2. M → 查看区域图得到 `WORLD → WORLD_MAP → REGION_MAP`；两次 Esc 依次回到世界图、探索世界，Hash 与世界实例不变。
3. 随机事件打开期间，W 与 Esc 不会关闭不可取消对话；间隔一秒的地图画面像素一致，证明世界更新已冻结。
4. 阻塞窗口初始焦点位于窗口内，Shift+Tab 不离开窗口；文本输入聚焦后 W 与 Esc 不会误触快捷键或关闭窗口。快捷队伍替换为完整队伍后，焦点不会被中间的世界恢复抢走；最外层关闭后回到地图焦点根。
5. 浏览器返回在区域图、世界图依次打开时逐层关闭 Context，第二次返回后仍保持 `#/explore` 与同一世界实例；不再出现返回空白页的问题。
6. 旧 `/pets` 兼容路由可直接加载，回到 `/explore` 后缓存的世界实例仍存在。

### 4.2 独立测试存档补验（2026-08-21）

1. **通过**：直接覆盖原测试数据后创建新游戏并接受 `QUEST_MAIN_01`。在任务详情点击“地图查看”，再点击青草原“查看区域图”，实际得到 `QUEST → WORLD_MAP → REGION_MAP`；连续 Esc 后可见对话数严格从 2→1→0。
2. **通过**：以同一条 Vue↔Phaser `gameBridge` 事件桥驱动本地浏览器验收（浏览器控制面无法稳定保持 WASD 长按）：`npc:touch` 打开村长对话，Esc 返回世界；`exit:touch` 打开出口确认，取消和“出发”均正常，出发后进入青草原；`gather:request` 显示草药与金币奖励，收下后关闭。
3. **通过（战斗进入与锁定）**：`encounter:touch` 先进入遭遇确认，再进入 `BATTLE`；Esc 和浏览器返回都保持 `#/explore`、同一世界实例和战斗层，不会错误退出战斗。
4. **通过（结算恢复）**：重启到修复版本后，以野外遭遇 → 战术逃跑 → 结束回合复验。`/settle` 在 1.2 秒内返回，显示“战斗结算 / 逃跑成功，无奖励 / 宠物 HP 回写”，点击“返回”后战斗 Context 关闭，仍为 `#/explore` 且 `data-world-instance-id` 保持 `world-mt2fkqhb`。根因修复为 `PetHistoryService` 加入外层结算事务，避免 `REQUIRES_NEW` 重复锁定同一 `player_pet` 行；`BattleServiceSettlementTest` 20 项通过。
5. **待补验**：当前浏览器控制面不支持持续按键，且无法可靠模拟真实页面隐藏；尚需可持续按住 WASD 的真实浏览器手工验证窗口失焦/标签切换后没有持续移动。

## 5. 阶段结论

代码、类型检查、生产构建、后端回归、无写入浏览器验收和独立测试存档的任务/NPC/出口/奖励/战斗进入、结算恢复补验均已完成。真实页面隐藏后的持续移动仍未验证。**在 4.2 的待解决项全部通过前，阶段 1 不宣告正式验收完成，也不得启动阶段 2。**

## 6. 运行环境复验记录（2026-08-21）

- MySQL84、Spring Boot 与 Vite 正常；官方浏览器组件已重新加载，可执行真实键盘、浮层、焦点、冻结和返回用例。
- 本轮获用户授权直接覆盖测试数据：已重置并创建“阶段一验收”新游戏，接受主线、进入青草原、解析随机事件、采集奖励并发起/结算测试战斗；不恢复原测试数据。
- 复验中发现并修复三项问题：快捷队伍的关闭/打开焦点竞态、嵌套 Context 连续浏览器返回会离开应用，以及 Overlay 战斗结束后未自动发起结算。
