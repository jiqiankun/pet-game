你现在需要为《宠物精灵》项目生成正式游戏美术资源。

本项目已经配置以下 6 个美术资源 Skill：

- `game-art-director`
- `pet-character-artist`
- `rpg-tileset-designer`
- `battle-vfx-artist`
- `game-asset-qc`
- `game-asset-pipeline`

请严格按照这些 Skill 的职责、输入输出规范、批量生成规则和 QC 规则执行，不要绕过 Skill 直接自由生成图片。

# 一、总体目标

读取项目当前已有的需求文档、设计文档、技术方案、开发规划、宠物配置、技能配置、地图配置以及现有美术资源，为游戏生成以下正式资源：

1. 27 只宠物的完整立绘资源
2. 游戏地图 Tileset
3. 地图场景装饰资源
4. 战斗技能特效资源
5. 状态效果相关视觉资源
6. 必要的资源预览图、Contact Sheet 和 QC 报告

最终生成的资源需要能够直接用于项目开发，而不是仅作为概念设计图。

# 二、执行前必须完成的工作

首先扫描项目，重点查找：

- 宠物设计文档
- 宠物属性和种族配置
- 27 只宠物的名称、元素、定位、稀有度、技能等信息
- 技能系统设计
- 状态效果设计
- 地图和场景设计
- 当前已有美术规范
- 游戏实际使用的图片尺寸
- 地图 Tile 尺寸
- 战斗场景尺寸
- 当前资源目录结构
- 当前资源命名规则

如果项目文档已经明确某项规范，以项目规范为最高优先级。

Skill 中的默认值只能作为项目未定义相关规则时的备用值。

不要未经分析直接开始生成全部图片。

# 三、先建立统一美术规范

首先使用：

`game-art-director`

建立本项目统一的 Art Bible。

至少确定：

- 游戏整体画风
- 宠物身体比例
- 宠物头身比例
- 线条风格
- 描边粗细
- 光影方式
- 光源方向
- 色彩饱和度
- 色彩数量控制
- 元素属性的视觉语言
- 普通、稀有、特殊宠物之间的视觉区别
- 宠物立绘统一观察角度
- 宠物立绘统一构图
- 背景透明规则
- 阴影规则
- 地图整体风格
- Tileset 透视方式
- 地图色彩体系
- 战斗特效风格
- 粒子表现方式
- VFX 发光强度
- UI 与美术资源的视觉兼容规则

生成：

`art-bible.md`

如果项目已有 Art Bible，则读取并补充，不要重新建立冲突规范。

# 四、27 只宠物批量生成

使用：

`pet-character-artist`

并由：

`game-asset-pipeline`

负责批次控制。

不要一次生成 27 只宠物。

采用以下批量策略。

## 第一阶段：A0 风格锚点

从 27 只宠物中选择 3 只最有代表性的宠物。

3 只宠物应尽量覆盖不同：

- 元素属性
- 身体结构
- 主色调
- 性格
- 战斗定位
- 稀有度

例如尽量避免全部选择四足兽或全部选择火系宠物。

A0 批次：

```text
A0
├─ Anchor-01
├─ Anchor-02
└─ Anchor-03
```

每只锚点宠物允许生成最多 2 个候选版本。

对候选进行比较，选择最符合 Art Bible 的版本。

使用：

`game-asset-qc`

检查：

- 画风
- 比例
- 构图
- 线条
- 光影
- 色彩
- 轮廓辨识度
- 与宠物设定匹配程度

只有 A0 全部通过 QC 后，才允许继续生成剩余宠物。

A0 的正式版本作为后续 24 只宠物的风格参考。

# 五、剩余 24 只宠物生产批次

剩余 24 只宠物拆分为：

```text
B01
B02
B03
B04
B05
B06
B07
B08
```

每批 3 只宠物。

批次划分时不要简单按照宠物 ID 顺序。

应综合：

- body_type
- element
- palette
- rarity
- combat_role
- silhouette

进行平衡。

尽量保证同一个批次中的 3 只宠物差异明显。

例如避免：

```text
火系四足兽
火系四足兽
火系四足兽
```

应优先形成类似：

```text
火系四足兽
水系飞行宠物
草系小型植物宠物
```

的组合。

# 六、单只宠物生成规则

每只宠物生成前必须读取其真实配置。

禁止根据名字自行猜测设定。

为每只宠物建立内部生成规格：

```yaml
pet_id:
name:
element:
secondary_element:
rarity:
body_type:
combat_role:
personality:
primary_color:
secondary_color:
signature_feature:
silhouette_feature:
evolution_relation:
special_notes:
```

生成时必须满足：

## 角色完整

必须：

- 完整展示整个身体
- 不裁切耳朵
- 不裁切尾巴
- 不裁切翅膀
- 不裁切脚
- 不超出画布

## 角色识别

每只宠物至少具有：

- 一个独特轮廓特征
- 一个独特身体结构特征
- 一个独特颜色组合
- 一个视觉记忆点

不要仅通过换颜色制造不同宠物。

## 元素表达

元素特征应该融入：

- 身体结构
- 毛发
- 角
- 翅膀
- 尾巴
- 花纹
- 器官
- 少量能量效果

避免所有元素宠物都简单添加大量粒子。

## 设计复杂度

宠物设计应符合本项目轻量游戏定位。

避免：

- 极度复杂铠甲
- 大量机械零件
- 大量细碎挂件
- 过多纹理
- MMO 风格装备堆叠

优先保证：

- 可爱
- 易识别
- 有个性
- 轮廓清晰
- 游戏内缩小时仍然容易辨认

# 七、宠物候选生成规则

A0 阶段：

每只允许：

```text
最多 2 个候选
```

正式生产阶段：

每只默认：

```text
1 个正式候选
```

不要为每只宠物大量抽卡生成 5～10 个候选。

只有 QC 不通过时才重新生成。

重新生成时必须针对失败原因进行修正。

例如：

```text
FAIL: silhouette too similar to pet_012
```

重新生成时重点修改轮廓，而不是完全随机重画。

# 八、宠物阶段 QC

每完成若干批次后执行阶段性检查。

执行：

### M1

B01～B02 完成后。

### M2

B03～B05 完成后。

### M3

B06～B08 完成后。

重点检查：

- 风格漂移
- 身体比例漂移
- 颜色重复
- 轮廓重复
- 元素视觉语言是否统一
- 是否出现某些宠物明显精细度过高
- 是否出现某些宠物明显过于简单
- 是否与 A0 锚点保持一致

# 九、27 只宠物最终总检

27 只全部完成以后生成：

`pets-contact-sheet.png`

要求一次展示全部 27 只宠物。

按照统一缩放比例展示。

不要单独调整某只宠物尺寸来让它“看起来一样大”。

通过 Contact Sheet 检查：

- 整体风格一致性
- 色彩分布
- 体型差异
- 轮廓差异
- 元素分布
- 稀有度视觉差异
- 是否存在明显重复设计
- 是否存在异常角色

发现问题只重新生成问题宠物。

不要重新生成全部宠物。

# 十、地图 Tileset 生成

完成宠物风格以后，使用：

`rpg-tileset-designer`

生成正式地图资源。

首先读取项目地图需求。

确定：

- Tile Size
- 地图透视
- 地图尺寸
- 场景类型
- 地图主题
- 是否使用 autotile
- 碰撞规则
- 动画 Tile
- 水体动画
- 建筑方式

不要直接生成一整张随机地图作为 Tileset。

Tileset 应拆分生成。

建议顺序：

```text
T01 基础地面

T02 地形过渡

T03 水体

T04 悬崖 / 高低差

T05 道路

T06 建筑基础

T07 树木与植被

T08 Rocks / Props

T09 特殊场景装饰

T10 动画 Tile
```

具体资源种类以项目实际地图设计为准。

# 十一、Tileset 无缝规则

所有重复地形必须进行无缝测试。

至少进行：

```text
3×3 tile 拼接
```

检查：

- 左右接缝
- 上下接缝
- 四角接缝
- 重复纹理
- 明显周期图案
- 颜色断层
- 光影方向冲突

地面 Tile 必须可以连续铺设。

道路需要考虑：

- 直线
- 横向
- 纵向
- 十字
- T 型
- 四个转角
- 起点
- 终点
- 与不同地形连接

水体需要考虑：

- 中心水面
- 岸边
- 外角
- 内角
- 河道
- 水陆过渡

# 十二、地图 Props

地图 Props 必须独立生成。

例如：

- 树
- 草
- 花
- 石头
- 木箱
- 木桶
- 路牌
- 栅栏
- 灯
- 桥
- 门
- 小型建筑物
- 自然装饰

Props 应：

- 使用透明背景
- 与地图透视一致
- 与 Tile Size 匹配
- 不携带独立背景
- 光源方向一致

# 十三、地图 QC

使用：

`game-asset-qc`

进行：

- tile seam test
- perspective test
- palette test
- lighting test
- scale test
- prop compatibility test

生成至少一个测试地图：

`tileset-preview-map`

使用所有主要类型 Tile 和 Props 拼出实际地图场景。

不要只检查单独 Tile。

# 十四、战斗技能特效

使用：

`battle-vfx-artist`

生成战斗特效。

首先读取：

- 技能列表
- 主动技能
- 被动效果
- 状态效果
- 元素系统

不要按照：

```text
一个技能 = 一个完全独立 VFX
```

的方式生成资源。

首先建立：

`VFX Matrix`

根据以下维度分类：

```text
element
category
intensity
target_type
duration
loop
```

例如：

```yaml
element: fire
category: projectile
intensity: small
loop: false
```

不同技能优先复用基础特效。

# 十五、建议 VFX 分类

至少考虑：

## 攻击

- slash
- impact
- projectile
- explosion
- beam
- burst
- ground-hit

## 元素

- fire
- water
- grass
- electric
- ice
- poison
- wind
- earth

具体元素以项目当前设计为准。

## Buff

- attack-up
- defense-up
- speed-up
- heal
- shield

## Debuff

- attack-down
- defense-down
- slow
- stun
- blind

## 状态

根据项目实际状态系统生成。

例如：

- burn
- poison
- freeze
- paralysis
- sleep
- confusion

不要因为示例中存在某个状态就擅自加入项目中不存在的状态。

# 十六、VFX 动画规则

每个动画特效需要统一：

- Canvas Size
- Pivot
- Origin
- FPS
- Frame Count
- Alpha
- Glow
- Scale

如果是 Sprite Sheet：

保证：

- 每帧尺寸完全相同
- 帧间角色位置一致
- 中心点不漂移
- 第一帧和最后一帧逻辑合理
- Loop VFX 首尾可以自然连接
- Non-loop VFX 最后一帧自然消散

禁止生成：

- 白色背景
- 黑色背景
- 摄影背景
- UI 边框
- 多余文字

背景必须透明。

# 十七、VFX 强度分级

相同元素不要为每个技能重新设计完全不同的视觉语言。

建议建立：

```text
small
medium
large
ultimate
```

四档。

例如火系：

```text
fire_small
fire_medium
fire_large
fire_ultimate
```

技能只引用合适的特效组合。

这样能够显著减少项目美术资源数量。

# 十八、资源命名

所有资源使用项目已有命名规范。

如果项目没有统一规范，则使用：

宠物：

```text
pet_<id>_<name>.png
```

地图：

```text
tile_<category>_<name>.png
```

Props：

```text
prop_<category>_<name>.png
```

VFX：

```text
vfx_<element>_<category>_<level>.png
```

Sprite Sheet：

```text
vfx_<element>_<category>_<level>_sheet.png
```

预览：

```text
preview_<category>.png
```

禁止：

```text
image1.png
final.png
final2.png
new.png
test.png
```

# 十九、资源目录

优先读取项目当前资源目录。

如果尚未建立，则建议：

```text
assets/
├─ pets/
│  ├─ portraits/
│  ├─ previews/
│  └─ contact-sheets/
│
├─ maps/
│  ├─ tilesets/
│  ├─ autotiles/
│  ├─ props/
│  └─ previews/
│
├─ battle/
│  ├─ vfx/
│  ├─ status/
│  └─ previews/
│
└─ art/
   ├─ references/
   ├─ art-bible/
   └─ qc/
```

# 二十、资源生命周期

所有资源通过：

`game-asset-pipeline`

维护以下状态：

```text
planned
↓
generating
↓
generated
↓
qc_pending
↓
accepted
```

QC 不通过：

```text
qc_pending
↓
needs_fix
↓
generating
```

通过后：

```text
accepted
↓
exported
```

只有：

```text
accepted
```

或者：

```text
exported
```

状态的资源才能进入正式游戏资源目录。

# 二十一、禁止事项

整个生产过程中禁止：

1. 一次性生成全部 27 只宠物而不做风格验证。
2. 每只宠物生成大量随机候选。
3. 未读取宠物配置就自行设计宠物。
4. 只靠换颜色制造不同宠物。
5. 让不同宠物使用几乎相同的身体模板。
6. 每个技能单独生成一套完全独立 VFX。
7. 生成不能无缝拼接的地面 Tiles。
8. 把概念地图图片当作正式 Tileset。
9. 无视项目实际 Tile Size。
10. 修改项目已经确认的宠物设定。
11. 修改项目已经确定的元素体系。
12. 添加设计文档不存在的宠物。
13. 添加设计文档不存在的状态。
14. 因为某个资源 QC 失败就重新生成所有资源。
15. 未通过 QC 就直接覆盖正式资源。
16. 在正式图片中加入文字、水印、签名或 Logo。

# 二十二、增量生成

必须支持增量生成。

例如以后增加：

```text
pet_028
```

只需要：

1. 读取现有 Art Bible。
2. 读取 A0 风格锚点。
3. 读取现有宠物 Contact Sheet。
4. 生成 pet_028。
5. 与已有宠物进行 QC。
6. 更新 Contact Sheet。
7. 导出新资源。

禁止重新生成前 27 只宠物。

新增技能、状态、地图 Tile 同理。

# 二十三、生成过程输出

在真正调用图像生成工具以前，先输出：

## 1. 项目美术扫描结果

说明找到了哪些相关配置和设计。

## 2. 当前采用的 Art Bible

列出关键美术规范。

## 3. 27 只宠物清单

列出：

```text
ID
名称
元素
体型
定位
主要视觉特征
```

## 4. 宠物批次规划

输出：

```text
A0
B01
B02
...
B08
```

以及每批具体宠物。

## 5. Tileset 资源计划

说明准备生成哪些 Tile 类型。

## 6. VFX Matrix

列出项目实际需要的 VFX 类型。

## 7. 预计资源清单

明确哪些资源：

- 新生成
- 已存在
- 可复用
- 不需要生成

完成以上分析后，再开始正式生成美术资源。

# 二十四、正式执行

完成规划后，不要停留在“给出建议”。

按照：

`game-asset-pipeline`

实际执行资源生产。

执行顺序：

```text
扫描项目
↓
读取 6 个 Skill
↓
建立 / 校验 Art Bible
↓
建立资源 Manifest
↓
生成 A0
↓
A0 QC
↓
生成 B01～B08
↓
阶段 QC
↓
27 宠物最终 QC
↓
生成 Tileset
↓
Tileset 拼接 QC
↓
生成地图 Props
↓
生成 VFX
↓
VFX QC
↓
生成资源总览
↓
更新 Manifest
↓
导出 accepted 资源
```

如果图像生成工具支持参考图功能：

后续宠物生成必须使用 A0 正式宠物作为风格参考。

如果支持 Seed：

同类型资源尽量采用受控 Seed。

如果支持 Style Reference：

始终保持 Art Bible 对应 Style Reference。

如果支持透明背景：

宠物、Props、VFX 优先直接生成透明背景。

如果某种生成能力当前工具不支持，不要伪造已经完成。

明确记录：

```text
BLOCKED
```

以及原因，然后继续处理其他可完成资源。

最终目标不是获得一些“漂亮的 AI 图片”，而是建立一套：

**风格统一、结构规范、可以复用、可以增量扩展、可以直接接入《宠物精灵》游戏项目的正式美术资源库。**