---
name: pet-character-artist
description: 根据《宠物精灵》的宠物定义和统一 Art Bible，批量生成 27 只宠物的标准化立绘资源，并控制跨批次一致性。
version: 1.0.0
---

# Pet Character Artist

## 目标

为《宠物精灵》生成可直接进入游戏资源流程的宠物立绘。重点不是单张图片最大化华丽程度，而是 27 只宠物整体统一、外形可辨识、属性特征明确、适合游戏 UI 展示。

## 前置依赖

必须先读取：

1. 项目中的宠物定义；
2. `game-art-director` 输出的 `style_lock`；
3. 已存在的宠物资源与命名规范；
4. 若存在，宠物进化关系、属性、稀有度、技能倾向。

如果 3 个风格锚点尚未通过 QC，只允许执行锚点阶段。

## 输入格式

推荐每只宠物规范化为：

```yaml
pet_id: P001
name: 示例宠物
family_id: F001
evolution_stage: 1
element:
  - fire
rarity: common
combat_role: attacker
body_type: quadruped
personality: energetic
silhouette_keywords:
  - large ears
  - short tail
palette:
  primary: warm-red
  secondary: cream
signature_features:
  - flame-shaped ear tips
  - glowing tail mark
forbidden_features:
  - armor
  - weapon
notes: null
```

最低必需字段：`pet_id`、`name`、`element`、外形描述。缺失字段可根据项目资料合理归纳，但不得凭空新增影响玩法设定的内容。

## 输出格式

每只宠物输出：

```yaml
pet_id: P001
status: accepted
style_version: art-v1
source_prompt: generated/prompts/pets/P001.md
master_asset: generated/pets/master/pet_p001.png
export_asset: assets/pets/portrait/pet_p001.png
qc_report: generated/qc/pets/P001.yaml
seed_or_generation_id: optional
```

图片默认：

- master：1024x1024；
- export：512x512；
- PNG；
- RGBA 透明背景；
- 单只宠物、完整身体；
- 不带文字和边框。

项目已有规格时覆盖以上默认值。

## 27 只宠物批量生成总规则

### 第一阶段：建立 3 个风格锚点

由 `game-art-director` 选择 3 只代表宠物。

批次：

```text
A0: 3 个风格锚点
```

每只锚点生成 2 个候选，人工或 QC 选择 1 个。A0 全部通过后锁定 `art-v1`。

### 第二阶段：剩余 24 只

拆为 8 个生产批次，每批 3 只：

```text
B01: 3
B02: 3
B03: 3
B04: 3
B05: 3
B06: 3
B07: 3
B08: 3
```

总计：`3 + 8 × 3 = 27`。

### 批次分配算法

不要简单按元素全部聚在一起。对剩余宠物按以下优先级平衡分配：

1. 不同 `body_type`；
2. 不同 `element`；
3. 不同主色；
4. 不同稀有度；
5. 最后按 `pet_id` 保证确定性。

同一进化家族可以相邻生成，以维持家族特征，但一个批次不建议全部来自同一家族。

### 每批生成限制

- 每批最多同时生产 3 只；
- 生产宠物默认每只只生成 1 个正式候选；
- 只有 QC 失败才重生；
- 同一批次中每只都必须携带 3 个已批准锚点作为风格参考；
- 每完成一批立即 QC，不允许 24 只全部生成后才统一检查；
- 连续 2 只出现相同风格偏移时，暂停下一批并回到 `game-art-director` 检查 Art Bible。

该规则优先控制生成成本和返工成本。

## Prompt 构建顺序

Prompt 必须按固定顺序构造：

1. 游戏与 Art Bible 固定段；
2. 宠物身份；
3. 身体结构；
4. 元素表现；
5. 主色与辅助色；
6. 标志性特征；
7. 性格和姿态；
8. 游戏资产构图规则；
9. 禁止项。

示例结构：

```text
[STYLE LOCK]
Follow the approved Pet Spirit art bible and the three approved reference pets.

[SUBJECT]
Full-body creature character: {name}, id {pet_id}.
{body_type}, {personality}.

[DESIGN]
Primary element: {element}.
Primary palette: {palette}.
Signature features: {signature_features}.
Keep a unique and instantly readable silhouette.

[COMPOSITION]
Single creature centered on canvas, complete body visible,
neutral or lightly dynamic 3/4 presentation pose,
consistent ground baseline, suitable for RPG portrait UI.
Transparent background.

[NEGATIVE]
No text, no logo, no frame, no scenery, no extra creature,
no cropped limbs, no unrelated props, no photorealism,
no style drift from approved references.
```

## 进化家族规则

若宠物存在进化关系：

- 必须保留至少 2 个家族识别特征；
- 进化后轮廓应增强，而不是完全换物种；
- 主色可以变化，但应保留至少一种关联色或纹样；
- 进化阶段越高，可增加局部复杂度，但不能突然改变 Art Bible；
- 不允许为了“更强”默认加入武器、盔甲、人形化，除非设计文档明确要求。

## 元素表现规则

元素通过局部视觉语言表达，不要让特效覆盖宠物本体：

- 火：暖色、火焰形轮廓、局部发光；
- 水：流线、鳍状或水滴纹样；
- 雷：锐角、闪电纹样、高亮点；
- 冰：晶体边缘、冷色高光；
- 草/自然：叶片、藤蔓、自然纹样；
- 毒：警示色、雾状或斑点视觉语言；
- 光/暗等其他属性以项目定义为准。

不要擅自新增不存在的元素体系。

## 构图一致性

默认目标：

- 宠物主体高度占画布约 68%~82%；
- 主体视觉中心稳定；
- 脚底/最低点基线尽量一致；
- 不裁切耳朵、尾巴、翅膀；
- 最大外扩装饰不得触碰画布边缘；
- 姿态允许有个性，但不能因动作造成 UI 内大小差异过大。

## 重生规则

优先局部修正，不无脑整图重生。

按顺序处理：

1. 背景/透明度错误 -> 去背景或修复 alpha；
2. 尺寸/位置错误 -> 重新裁切和缩放；
3. 小范围特征错误 -> 局部编辑；
4. 轮廓、物种、核心设计错误 -> 整体重生；
5. 画风漂移 -> 使用锚点重新生成。

同一宠物最多连续自动重生 3 次。仍失败时标记 `needs_review`，不得无限消耗生成次数。

## 文件命名

推荐：

```text
pet_p001.png
pet_p002.png
...
pet_p027.png
```

若需要区分：

```text
pet_p001_master.png
pet_p001_portrait.png
pet_p001_preview.png
```

禁止使用中文名直接作为唯一文件名，避免跨平台路径问题。中文名保存在 manifest。

## 完成条件

27 只宠物全部满足：

- 有唯一 `pet_id`；
- 使用同一有效 `style_version`；
- 通过 `game-asset-qc`；
- 无透明背景错误；
- 无明显跨批次风格漂移；
- 名称、属性、进化关系与游戏数据一致；
- 最终资源与生成源文件分开存放。
