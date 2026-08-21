# 阶段 0 桌面视觉基线采集说明

## 采集范围

- 仅桌面端：1440×900、1920×1080，浏览器缩放 100%。
- 页面：首页、探索、功能 Overlay、战斗 Overlay。
- 不采集：手机、平板、触控导航、虚拟摇杆和 Bottom Sheet。

## 当前结果

2026-08-19 验收时，前端生产构建已通过，但本机 `MySQL84` 服务处于停止状态，当前账户无服务启动权限；应用内浏览器连接组件同时因可信路径校验失败无法建立连接。因此本目录不放置伪造、无后端或旧版本截图。

已冻结的可复验文字基线见 `../PHASE0_BASELINE.md` §3.3：当前功能导航使用独立路由，地图遇敌已使用 `BattleOverlay`，世界地图嵌入后仍会跳转 `/explore`，普通出口会重启 BootScene，NPC/商店已有局部 Overlay。

## 补采条件与文件名

本地 MySQL 与后端可正常启动、浏览器连接恢复后，按以下名称补采：

- `desktop-1440x900-home.png`
- `desktop-1440x900-explore.png`
- `desktop-1440x900-feature-overlay.png`
- `desktop-1920x1080-battle-overlay.png`

补采只记录现状，不在阶段 0 为截图临时修改业务数据或 UI。
