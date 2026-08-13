# 《宠物精灵》AI 美术资源 Skills

本目录包含 6 个可直接放入项目的 `SKILL.md`：

1. `game-art-director`：统一美术规范与 3 个风格锚点；
2. `pet-character-artist`：27 只宠物立绘批量生成；
3. `rpg-tileset-designer`：地图 Tileset；
4. `battle-vfx-artist`：战斗 VFX；
5. `game-asset-qc`：资源技术/视觉/一致性 QC；
6. `game-asset-pipeline`：总控批处理、增量生成和落盘。

## 推荐放置位置

直接复制 `skills/` 到项目根目录：

```text
<project>/skills/
```

如果你的 AI 编程工具使用 `.agents/skills/`、`.claude/skills/` 或其他约定目录，可将这 6 个目录整体移动过去；Skill 内部路径均为逻辑建议，不依赖固定工具。

## 27 只宠物生产策略

采用：

```text
A0 风格锚点：3只，每只2候选
B01-B08：剩余24只，每批3只，每只默认1候选
```

每批立即 QC。这样既能控制跨批次风格漂移，也避免 27 只都生成多个候选导致成本快速膨胀。

## 使用顺序

```text
game-art-director
-> pet-character-artist / rpg-tileset-designer / battle-vfx-artist
-> game-asset-qc
-> game-asset-pipeline 管理状态与导出
```

示例总配置见 `examples/art-generation.yaml`。
