---
name: game-asset-pipeline
description: 总控《宠物精灵》的 AI 美术资源生产流程，读取游戏数据，生成任务清单，调度美术 Skill，执行 QC，并将通过的资源稳定落盘。
version: 1.0.0
---

# Game Asset Pipeline

## 目标

把宠物立绘、Tileset 和战斗 VFX 从“临时提示词生图”变成可重复、可追踪、可增量执行的资源生产管线。

本 Skill 是总控，不自行决定美术风格，也不绕过 QC。

## 调用关系

```text
game-asset-pipeline
  -> game-art-director
  -> pet-character-artist
  -> rpg-tileset-designer
  -> battle-vfx-artist
  -> game-asset-qc
  -> export
```

## 推荐项目目录

```text
project-root/
├─ skills/
│  ├─ game-art-director/SKILL.md
│  ├─ pet-character-artist/SKILL.md
│  ├─ rpg-tileset-designer/SKILL.md
│  ├─ battle-vfx-artist/SKILL.md
│  ├─ game-asset-qc/SKILL.md
│  └─ game-asset-pipeline/SKILL.md
├─ art/
│  ├─ art-bible.yaml
│  ├─ manifests/
│  │  ├─ pets.yaml
│  │  ├─ tilesets.yaml
│  │  └─ vfx.yaml
│  └─ prompts/
├─ generated/
│  ├─ anchors/
│  ├─ pets/
│  ├─ tilesets/
│  ├─ vfx/
│  ├─ previews/
│  └─ qc/
└─ assets/
   ├─ pets/portrait/
   ├─ maps/tilesets/
   └─ vfx/
```

如果项目已有 `assets` 结构，沿用现有结构，不强制迁移。

## 总输入格式

推荐入口文件 `art-generation.yaml`：

```yaml
project: 宠物精灵
mode: incremental
style_version: art-v1
sources:
  pets: path/to/pet-data
  skills: path/to/skill-data
  maps: path/to/map-design

targets:
  pets:
    enabled: true
    expected_count: 27
  tilesets:
    enabled: true
  vfx:
    enabled: true

defaults:
  portrait_master_size: 1024
  portrait_export_size: 512
  tile_size: 32
  vfx_canvas_size: 256

policy:
  overwrite_accepted: false
  auto_regenerate_max: 3
  require_qc: true
```

## 状态模型

每项资源只允许以下状态：

```text
planned
generating
generated
qc_pending
needs_fix
needs_review
accepted
exported
```

禁止直接从 `generated` 跳到 `exported`。

## 幂等规则

默认 `mode: incremental`。

每次执行：

1. 扫描 manifest；
2. 跳过 `accepted/exported` 且源定义未变化的资源；
3. 只处理新增、修改、失败或明确要求重做的资源；
4. 不覆盖已通过资源；
5. 源数据发生变化时计算受影响范围，而不是全量重生。

例如只修改 P017 的元素纹样，仅重生 P017，不重生 27 只。

## 27 只宠物批量规则

### Step 1：完整性检查

读取全部宠物数据，验证：

```text
expected_count == 27
pet_id 唯一
name 非空
核心外形描述存在
元素字段合法
进化关系无断链（如有）
```

若数量不是 27，但项目文档已正式更新为其他数量，应采用最新项目定义并在报告中说明；不要为了满足旧数字伪造宠物。

### Step 2：确定 A0 锚点

根据 `game-art-director` 规则选择 3 只。

生成计划：

```yaml
batch: A0
mode: anchor
pets: [Pxxx, Pxxx, Pxxx]
candidates_per_pet: 2
```

A0 未通过 QC，不进入 B01。

### Step 3：分配 B01-B08

将剩余 24 只按：

```text
body_type -> element -> palette -> rarity -> pet_id
```

进行平衡分组，每批 3 只。

生成固定计划并写入 manifest。除非宠物定义本身发生变化，后续执行不重新随机分组。

### Step 4：逐批生产

```text
B01 -> QC
B02 -> QC
B03 -> QC -> M1
B04 -> QC
B05 -> QC
B06 -> QC -> M2
B07 -> QC
B08 -> QC -> M3 + 27只总览
```

任何一批存在 high/critical 问题，只修复本批，不阻塞已通过资源，也不继续扩大错误。

### Step 5：导出

只导出 QC pass 项。

最终要求：

```text
27 个正式 portrait
27 个对应 manifest 记录
27 个 QC 结果
1 个全体 contact sheet
1 份批量生成汇总报告
```

## Tileset 管线

流程：

```text
读取地图需求
-> 按 biome/地图主题去重
-> 生成 required_groups
-> 基础地面
-> transitions
-> water/cliff/path
-> structures/props
-> 拼接预览
-> QC
-> atlas/manifest 导出
```

不允许根据“看起来可能有用”无限扩充 Tileset。

## VFX 管线

流程：

```text
读取技能/状态
-> 建立 VFX需求矩阵
-> category+element+intensity+loop 去重
-> 映射可复用资源
-> 生成缺失 VFX
-> 切帧/拼 sheet
-> QC
-> 输出映射表
```

目标是尽可能让多个技能合理复用同一特效资源。

## Prompt 存档

所有最终接受资源必须保留实际生成 Prompt：

```text
art/prompts/pets/P001.md
art/prompts/tilesets/forest_01.md
art/prompts/vfx/vfx_fire_hit_01.md
```

若生成工具返回 seed、generation id、model id 或 reference id，也写入 manifest，方便重现。

## 输出报告

一次批处理结束后输出：

```yaml
run_id: art-run-2026xxxx
style_version: art-v1
summary:
  planned: 42
  generated: 12
  accepted: 10
  needs_fix: 2
  skipped_existing: 30
cost_control:
  anchor_candidates: 6
  production_candidates: 9
  regenerations: 2
next_actions:
  - fix P014 alpha edge
  - regenerate vfx_ice_hit_02
```

## 成本控制规则

1. 锚点阶段可生成 2 候选；普通宠物默认 1 候选。
2. QC 通过后不因“可能还有更好”自动继续抽卡。
3. 自动重生最多 3 次。
4. VFX 先复用再新增。
5. Tileset 只生成项目实际需要的 tile group。
6. 不允许因为模型输出偶然差异自动全量重生。

## 变更影响规则

### 修改单只宠物

仅影响该宠物及其可能的进化家族一致性检查。

### 修改 Art Bible

生成影响列表，不默认立刻全量重生：

```yaml
impact:
  portraits: 27
  tilesets: 3
  vfx: 18
action: review_before_regenerate
```

### 修改 tile_size

所有 Tileset 属于结构性变化，需要重新导出或重生，不能只缩放图片蒙混通过。

### 修改技能数值

若视觉语义不变，不重生 VFX。

## 完成条件

一次全量美术资源生产完成必须满足：

- Art Bible 已锁定；
- 27 只宠物已通过 QC；
- 项目需要的 Tileset 已通过拼接 QC；
- 需要的 VFX 已建立技能映射；
- 正式资源目录只包含通过 QC 的文件；
- 所有源 Prompt 和 manifest 可追溯；
- 后续可通过 incremental 模式只生成新增资源。
