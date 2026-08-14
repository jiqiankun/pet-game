---
name: game-asset-qc
description: 对《宠物精灵》的 AI 生成宠物立绘、Tileset 和战斗 VFX 执行统一的技术检查、视觉检查和跨批次一致性验收。
version: 1.0.0
---

# Game Asset QC

## 目标

确保“生成成功”不等于“可进入游戏”。所有 AI 美术资源必须经过技术 QC、内容 QC 和一致性 QC 后才允许进入正式 `assets/` 目录。

## 输入格式

```yaml
asset_id: P001
asset_type: pet_portrait
source: generated/pets/master/pet_p001.png
expected:
  style_version: art-v1
  size: [1024, 1024]
  alpha: true
  single_subject: true
references:
  - generated/anchors/pet_p001.png
  - generated/anchors/pet_p002.png
  - generated/anchors/pet_p003.png
```

支持类型：

```text
pet_portrait
tileset
map_prop
vfx_sheet
vfx_frame
```

## 输出格式

```yaml
asset_id: P001
result: pass
score: 96
checks:
  dimensions: pass
  alpha: pass
  composition: pass
  style_consistency: pass
  semantic_accuracy: pass
issues: []
action: accept
```

失败示例：

```yaml
result: fail
score: 72
issues:
  - code: PET_CROP_001
    severity: high
    message: 左耳接触画布边缘
  - code: STYLE_DRIFT_002
    severity: medium
    message: 阴影明显重于 art-v1
regeneration_instruction:
  mode: edit
  preserve:
    - silhouette
    - palette
  fix:
    - increase top margin
    - reduce shadow contrast
```

## QC 分级

- `critical`：文件无法使用，例如尺寸错误、透明通道丢失、atlas 切片错位；
- `high`：明显影响游戏，例如主体裁切、错误宠物、tile 接缝、VFX 帧错乱；
- `medium`：一致性问题，例如饱和度、比例、阴影偏移；
- `low`：轻微观感问题，不影响实际使用。

存在 critical/high 时不得通过。

## 宠物立绘检查

### 技术

- PNG/RGBA；
- 尺寸符合项目要求；
- 透明背景；
- 无隐藏白底/黑底；
- 无文字、水印、边框；
- 无明显压缩块。

### 构图

- 单只宠物；
- 身体完整；
- 耳朵、角、尾巴、翅膀不裁切；
- 主体高度默认约 68%~82%；
- 基线与同批资源一致；
- 四周留安全边距。

### 设计

- `pet_id` 对应正确宠物；
- 属性特征正确；
- 不出现设计中禁止的武器/盔甲/器官；
- 进化家族保留关联特征；
- 不与其他宠物形成过高视觉重复。

### 一致性

与 3 个风格锚点比较：

- 描边；
- 光源；
- 阴影；
- 眼睛/面部语言；
- 细节密度；
- 饱和度；
- 主体比例。

## 27 只宠物跨批次检查

每个 B 批次通过后执行一次 batch QC。

检查：

1. 本批 3 只内部大小是否一致；
2. 与 A0 锚点是否同一画风；
3. 与上一批是否发生渐进式风格漂移；
4. 是否出现重复轮廓；
5. 是否有异常主色趋同。

每完成 3 个生产批次再做一次 milestone QC：

```text
M1: A0 + B01 + B02 + B03
M2: B04 + B05 + B06
M3: B07 + B08 + 全 27 只总览
```

最终必须生成一张 27 只宠物的 contact sheet 供整体比较。contact sheet 仅用于 QC，不作为游戏资源。

## Tileset 检查

- atlas 宽高能被 tile_size 整除；
- 每个 tile 坐标合法；
- 3x3 重复测试无缝；
- 过渡边、内角、外角完整；
- 透视一致；
- 光源一致；
- Props alpha 干净；
- walkable / blocked 的视觉语义清晰；
- 不存在整幅场景被误切成 tile 的情况。

建议为每个基础 tile 自动生成 3x3 拼接预览。

## VFX 检查

- sheet 每帧尺寸一致；
- 帧数与 metadata 一致；
- alpha 正常；
- anchor 跳动在可接受范围；
- 非 projectile 特效中心稳定；
- loop 首尾无明显跳变；
- 非 loop 末尾自然消散；
- 峰值帧不过度遮挡战斗主体；
- 无文字和伤害数值。

## 自动检查优先级

如果执行环境允许读取图像，优先自动完成：

- 文件存在；
- 图片格式；
- 宽高；
- alpha 通道；
- atlas 整除；
- VFX sheet 帧数量；
- 文件命名；
- 重复文件 hash。

视觉一致性和语义准确性再由视觉模型检查。

## 评分建议

```text
技术完整性      25
内容正确性      25
风格一致性      25
构图/可用性     15
独特性/辨识度   10
总分           100
```

建议：

- >= 90：pass；
- 80~89：可自动修正后复检；
- < 80：fail，重生或人工检查。

任何 critical/high 问题无论总分多少都 fail。

## 修复策略

必须输出“最小修改指令”。避免把一个 95 分资源因为小问题整体重生。

优先级：

```text
metadata fix
-> crop/resize
-> alpha cleanup
-> localized edit
-> full regeneration
```

## 完成条件

只有 `result: pass` 的资源允许由 `game-asset-pipeline` 移入正式资源目录。
