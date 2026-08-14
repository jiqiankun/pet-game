---
name: rpg-tileset-designer
description: 为《宠物精灵》设计和生成可无缝拼接、适合实际关卡制作的 2D RPG 地图 Tileset，并输出地图编辑器可消费的资源描述。
version: 1.0.0
---

# RPG Tileset Designer

## 目标

生成“能用于地图编辑”的 Tileset，而不是一张看起来像地图的插画。优先保证网格、拼接、边缘、透视、光照、碰撞语义和地形可读性。

## 前置依赖

必须读取：

- 项目地图设计；
- 当前使用的地图编辑器/引擎规范（若存在）；
- `game-art-director` 的 `style_lock`；
- 已有 tile 尺寸、atlas 排列和 autotile 规则。

## 输入格式

```yaml
tileset_id: forest_01
biome: forest
tile_size: 32
perspective: top-down
lighting: upper-left
required_groups:
  - base_ground
  - ground_transition
  - water
  - cliff
  - path
  - vegetation
  - props
collision_groups:
  blocked:
    - cliff
    - deep_water
  walkable:
    - grass
    - path
```

项目没有指定 `tile_size` 时默认 32x32；已有规格时不得覆盖。

## 输出格式

```yaml
tileset_id: forest_01
style_version: art-v1
tile_size: 32
atlas: assets/maps/tilesets/forest_01.png
manifest: assets/maps/tilesets/forest_01.yaml
preview: generated/previews/forest_01_preview.png
qc_report: generated/qc/tilesets/forest_01.yaml
```

manifest 至少描述：

```yaml
tiles:
  - id: grass_base_01
    x: 0
    y: 0
    w: 32
    h: 32
    tags: [ground, grass, walkable]
  - id: water_edge_n
    x: 32
    y: 0
    w: 32
    h: 32
    tags: [water, transition, north]
```

## 生产批次

每套 Tileset 按功能拆分，避免一次性让模型生成整张复杂 atlas：

```text
T01 基础地面：草、土、沙、石等
T02 地面过渡：边、角、内角、混合边
T03 水体：静水、岸边、浅水/深水（若游戏需要）
T04 高差：悬崖、台阶、墙体
T05 道路：直线、转角、T 型、十字、端点
T06 结构：桥、门、围栏等
T07 植被与自然装饰
T08 可交互/不可交互 Props
```

只有项目需要的组才生成，禁止为了“完整”制造游戏永远不会使用的大量 tile。

## 无缝规则

所有基础地表必须通过 3x3 重复拼接测试。

基础 tile：

- 四边颜色和纹理频率不得出现明显接缝；
- 不在 tile 边缘放置唯一高对比元素；
- 大型纹理特征应跨多个变体随机出现；
- 至少准备 3~5 个视觉变体，减少棋盘重复感。

过渡 tile：

- 必须包含直边、外角、内角；
- 若游戏采用 47-tile、16-tile 或其他 autotile 模板，严格按引擎模板生成；
- 未知模板时不要猜测，先输出通用切片并在 manifest 标记 `autotile_template: unresolved`。

## 透视与光照

- 同一 Tileset 只允许一种透视；
- 建筑、树木、悬崖的可见面方向必须一致；
- 统一使用 Art Bible 的光源方向；
- tile 内阴影不得跨出边界造成错误拼接，除非该阴影属于明确的多 tile 结构。

## Props 规则

Props 与基础地面分离输出，优先透明背景。大型物件使用多 tile 尺寸：

```yaml
prop:
  id: tree_01
  footprint: [1, 1]
  visual_size: [2, 3]
  anchor: bottom-center
```

视觉尺寸与碰撞占地必须分开描述，防止“大树图片覆盖 2x3 tile 就默认阻挡 2x3”。

## Prompt 结构

```text
[STYLE LOCK]
Use the locked Pet Spirit environment art style, top-down perspective,
fixed light direction and fixed detail density.

[ASSET TYPE]
Seamless RPG tileset asset, not a painted map and not a concept illustration.
Tile size: {tile_size}px.

[GROUP]
Generate {required_group} for biome {biome}.
Edges must align to the tile grid. Maintain consistent scale.

[FUNCTION]
Clearly distinguish walkable ground, blocked terrain and transitions.

[NEGATIVE]
No perspective drift, no text, no labels inside the artwork,
no UI, no characters, no random buildings unless requested,
no baked full-scene composition.
```

## 变体规则

同类基础地面建议：

- 1 个 canonical base；
- 2~4 个轻微纹理变体；
- 不通过变色生成“伪变体”；
- 变体不得改变地形语义。

## Tileset QC 门槛

至少检查：

- tile 尺寸整除；
- atlas 无半像素或越界；
- 3x3 拼接无明显缝；
- 直边/外角/内角齐全；
- 光照一致；
- 透视一致；
- walkable 与 blocked 视觉上能辨识；
- Props 的透明区干净；
- atlas 中没有文字、水印、角色残影。

## 完成条件

Tileset 必须同时具有：

1. atlas；
2. tile manifest；
3. 拼接预览图；
4. QC 报告；
5. 与当前 `style_version` 一致的记录。
