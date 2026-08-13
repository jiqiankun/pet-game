---
name: game-art-director
description: 为《宠物精灵》建立、冻结并维护统一的游戏美术规范，供宠物立绘、地图 Tileset、战斗特效等生成 Skill 共同遵循。
version: 1.0.0
---

# Game Art Director

## 目标

建立《宠物精灵》的唯一美术基准（Art Bible），防止不同批次、不同模型或不同生成工具产生画风漂移。任何宠物立绘、地图 Tileset、战斗特效生成任务开始前，都应先读取本 Skill 的输出。

## 使用时机

在以下情况调用：

- 首次批量生成游戏美术资源；
- 27 只宠物开始生产前；
- 新增地图主题、Tileset 或战斗特效体系前；
- 更换图像生成模型或供应商后；
- QC 发现多批次画风明显漂移时；
- 用户明确要求调整整体美术风格时。

## 输入

优先读取项目现有需求、设计、技术、规划、资源规范。已有项目约束始终高于本 Skill 的默认值。

推荐输入对象：

```yaml
project:
  name: 宠物精灵
  art_direction: 可爱幻想、轻量单机 RPG
  target_platform: PC/Web
  camera: top-down

style:
  rendering: 2D illustration
  outline: clean
  saturation: medium
  lighting: soft
  complexity: medium
  background: transparent

asset_defaults:
  portrait_master_size: 1024
  portrait_export_size: 512
  tile_size: 32
  vfx_canvas_size: 256
```

若项目已有同名字段，以项目值覆盖默认值。

## 输出

输出一个稳定的 `art-bible.yaml` 或等价结构，并生成供其他 Skill 使用的 `style_lock`。

```yaml
style_lock:
  version: art-v1
  status: locked
  character:
    proportions: unified
    outline_weight: fixed
    light_direction: upper-left
    shadow_softness: medium
    saturation: medium
    detail_density: medium
    portrait_background: transparent
  map:
    perspective: top-down
    light_direction: upper-left
    edge_contrast: medium
    tile_grid_visible: false
  vfx:
    readability_first: true
    silhouette_clear: true
    background: transparent
    max_palette_complexity: medium
  anchors:
    - pet_id: P001
      asset: reference/path/pet_001.png
    - pet_id: P002
      asset: reference/path/pet_002.png
    - pet_id: P003
      asset: reference/path/pet_003.png
```

## 硬性规则

1. 不允许每个资源单独决定画风。
2. 宠物、Tileset、VFX 必须共享同一光照方向和整体饱和度等级。
3. 立绘默认透明背景，不生成场景背景、地面、装饰框、文字、水印。
4. 不因为稀有度提高而改变整个游戏的渲染风格；稀有度差异应通过配色、装饰、轮廓复杂度和局部特征表达。
5. 不允许某一批宠物突然进入写实、3D、厚涂、像素或动漫赛璐璐等不同风格，除非 Art Bible 明确修改。
6. Tileset 不追求单张图片观赏性，优先保证可拼接性、可读性和游戏使用性。
7. VFX 不追求电影级复杂度，优先保证战斗中快速辨识。
8. Art Bible 一旦 `status: locked`，普通生成任务不得擅自修改。
9. 修改 Art Bible 必须提升版本，如 `art-v1 -> art-v2`，并列出受影响资源。

## 27 只宠物的风格锚点规则

27 只宠物不要一次性无参考批量生成。必须先建立 3 个风格锚点。

### 锚点选择

从完整宠物定义中选择 3 只，满足尽可能多的差异：

- 不属于同一进化家族；
- 身体结构不同，例如四足 / 双足 / 飞行或漂浮；
- 主色不同；
- 元素属性不同；
- 至少一只结构简单、一只中等、一只相对复杂。

若项目中没有对应分类，按外形差异最大原则选择。

### 锚点生成

每只锚点允许生成 2 个候选版本，仅此阶段允许主动扩大候选数。

最终锁定时确认：

- 头身比例；
- 眼睛与面部语言；
- 描边；
- 材质表达；
- 阴影方式；
- 光照方向；
- 细节密度；
- 主体占画布比例；
- 透明背景处理。

3 个锚点全部通过 QC 后，才允许生成剩余 24 只。

## Prompt 规范

所有下游 Prompt 至少包含以下稳定段：

```text
Follow the locked art bible of the game Pet Spirit.
Keep the same character proportion, outline weight, rendering method,
lighting direction, saturation level and detail density as the approved style references.
Game asset only. Clean silhouette. Transparent background where applicable.
Do not add text, frame, logo, scenery or unrelated objects.
```

具体宠物、地图和特效描述由下游 Skill 补充，不允许覆盖 `style_lock`。

## 风格变更流程

发现某个资源“不够好看”时，不应直接修改全局风格。先判断：

- 单资源构图问题 -> 交给对应生成 Skill 重生；
- 单资源画风偏移 -> 使用现有 Art Bible 重生；
- 多批资源均存在同一问题 -> 才考虑修改 Art Bible。

修改后输出：

```yaml
style_change:
  from: art-v1
  to: art-v2
  reason: 描边过重影响小尺寸显示
  changed_fields:
    character.outline_weight: medium -> light-medium
  regeneration_scope:
    - portraits
```

## 完成条件

只有满足以下条件才可声明美术规范已锁定：

- Art Bible 已产生版本号；
- 3 个宠物风格锚点通过 QC；
- 宠物、Tileset、VFX 的共同光照与色彩原则明确；
- 下游 Skill 能直接读取 `style_lock`；
- 不存在未决的整体画风分歧。
