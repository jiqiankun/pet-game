---
name: battle-vfx-artist
description: 为《宠物精灵》的主动技能、状态效果和战斗反馈生成轻量、清晰、可切帧使用的 2D 战斗特效资源。
version: 1.0.0
---

# Battle VFX Artist

## 目标

生成适合实际战斗播放的 2D VFX：清晰、短促、透明背景、易识别、不过度遮挡宠物。重点服务技能反馈，不制作电影级长动画。

## 前置依赖

读取：

- 技能与状态定义；
- 技能类型（主动/被动/技能书等）；
- 元素属性；
- `game-art-director` 的 `style_lock`；
- 游戏战斗区域尺寸和动画播放规范（若存在）。

被动技能若设计为“战斗中自动生效且无需播放主动施法动画”，默认只在真正需要反馈时生成短提示特效，不强制每个被动技能制作完整 VFX。

## 输入格式

```yaml
vfx_id: vfx_fire_hit_01
source_type: skill
source_id: SKILL_FIRE_001
name: 火焰冲击
category: impact
element: fire
target: enemy
intensity: medium
loop: false
suggested_frames: 8
duration_ms: 480
visual_keywords:
  - compact flame burst
  - sparks
forbidden:
  - screen-filling explosion
```

## 输出格式

```yaml
vfx_id: vfx_fire_hit_01
style_version: art-v1
frames: 8
fps: 16
loop: false
frame_size: [256, 256]
sheet: assets/vfx/vfx_fire_hit_01.png
metadata: assets/vfx/vfx_fire_hit_01.yaml
preview: generated/previews/vfx_fire_hit_01.gif
qc_report: generated/qc/vfx/vfx_fire_hit_01.yaml
anchor: center
```

默认参数可被项目现有规范覆盖。

## 特效分类

优先复用“基础 VFX + 参数变化”，不要每个技能都制作完全独立特效。

建议基础类型：

```text
cast       施法/蓄力
projectile 飞行物
impact     命中爆发
area       范围效果
buff       增益
heal       治疗
shield     护盾
debuff     减益
status     状态持续反馈
critical   暴击
miss       闪避/落空
```

元素再作为第二维：火、水、雷、冰、草/自然、毒以及项目中实际存在的其他属性。

## 复用规则

创建 VFX 前先查现有 manifest。

如果已有：

```text
impact + fire + medium
```

新技能只有伤害数值不同，不应创建新美术资源。

只有以下差异才考虑新增：

- 技能机制明显不同；
- 视觉形状承担玩法信息；
- Boss/特殊技能需要独特辨识；
- 状态效果必须长期显示且语义不同。

## 帧数规则

默认：

- 极短反馈：4~6 帧；
- 普通技能：8 帧；
- 较复杂爆发：10~12 帧；
- 循环状态：4~8 帧循环。

不默认生成 20+ 帧动画。轻量单机项目优先控制资源量。

## 动画阶段

非循环特效统一按三段：

```text
anticipation -> peak -> dissipate
```

例如 8 帧：

```text
F01-F02: 出现/聚集
F03-F05: 峰值
F06-F08: 消散
```

每帧主体中心与 anchor 基本稳定，除非设计本身是 projectile。

## Sprite Sheet 规则

- 每帧画布尺寸相同；
- 背景完全透明；
- 所有帧按固定顺序排列；
- 不允许帧间缩放导致 anchor 跳动；
- 不允许生成文字、伤害数字或 UI；
- 伤害数字应由游戏代码绘制；
- 若生成工具一次输出整张 sheet 难以稳定，允许先输出独立帧，再由管线拼 sheet。

## 状态效果规则

状态类优先使用小型、循环、低遮挡效果：

- 中毒：微弱毒泡/雾点，不覆盖全身；
- 灼烧：局部火苗；
- 冰冻：边缘冰晶或短冻结闪光；
- 眩晕：头顶小型旋转符号/电星；
- 护盾：清晰外圈或半透明罩；
- 治疗：上升粒子和柔和闪光。

如果项目新增有趣状态，先复用视觉语义，再决定是否新增资源。

## Prompt 结构

```text
[STYLE LOCK]
Use the approved Pet Spirit battle VFX style.
Readable 2D game effect, transparent background, clean silhouette.

[FUNCTION]
{category} effect for {element}.
Intensity: {intensity}.
Gameplay readability is more important than cinematic complexity.

[ANIMATION]
{frames} frames, consistent canvas and anchor.
Clear anticipation, peak and dissipate phases.

[COMPOSITION]
Centered unless projectile. Keep safe margins around the effect.
Do not obscure the whole creature at peak frame.

[NEGATIVE]
No character, no environment, no UI, no text, no damage numbers,
no watermark, no opaque background, no unrelated particles.
```

## 批量生成规则

1. 先从技能数据建立 `VFX需求矩阵`；
2. 按 `category + element + intensity + loop` 去重；
3. 先生成通用命中、治疗、护盾、常见状态；
4. 再生成机制独特的技能；
5. 每批不超过 5 个 VFX；
6. 每批生成后立即 QC；
7. 同语义资源优先参数化复用，不重复造图。

## 完成条件

- 所有需要视觉反馈的技能或状态都有映射；
- 不要求每个技能拥有独立 VFX；
- Sprite Sheet 帧尺寸统一；
- loop 特效首尾连续；
- 非 loop 特效末帧能自然消失；
- alpha 干净；
- 与当前 Art Bible 一致；
- 战斗中不会长期大面积遮挡宠物主体。
