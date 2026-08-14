param(
  [string]$ProjectRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $ProjectRoot) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) {
    throw $Message
  }
}

function Assert-Contains([string]$Text, [string]$Needle, [string]$Message) {
  Assert-True $Text.Contains($Needle) $Message
}

$skillsPath = Join-Path $ProjectRoot 'backend/src/main/resources/game-config/skills/skills.yml'
$battleViewPath = Join-Path $ProjectRoot 'frontend/src/views/Battle/BattleView.vue'
$assetHelperPath = Join-Path $ProjectRoot 'frontend/src/game-assets.ts'
$skillsText = Get-Content -Raw $skillsPath
$battleViewText = Get-Content -Raw $battleViewPath
$assetHelperText = Get-Content -Raw $assetHelperPath

$activeSection = [regex]::Match($skillsText, '(?ms)^skills:\s*\r?\n(?<content>.*?)(?=^passives:\s*$)')
$passiveSection = [regex]::Match($skillsText, '(?ms)^passives:\s*\r?\n(?<content>.*)\z')
Assert-True $activeSection.Success '未找到主动技能配置段。'
Assert-True $passiveSection.Success '未找到被动技能配置段。'

$idPattern = '(?m)^\s*-\s+id:\s*(\S+)\s*$'
$activeIds = @([regex]::Matches($activeSection.Groups['content'].Value, $idPattern) | ForEach-Object { $_.Groups[1].Value })
$passiveIds = @([regex]::Matches($passiveSection.Groups['content'].Value, $idPattern) | ForEach-Object { $_.Groups[1].Value })
Assert-True ($activeIds.Count -eq 85) "主动技能数量应为 85，实际为 $($activeIds.Count)。"
Assert-True ($passiveIds.Count -eq 24) "被动技能数量应为 24（14 个固有/升级被动 + 10 个技能书被动），实际为 $($passiveIds.Count)。"

$newActiveIds = @(
  'SKILL_MAGMA_CLASH', 'SKILL_INFERNO', 'SKILL_WATER_WHIP', 'SKILL_TORRENT',
  'SKILL_BRANCH_STRIKE', 'SKILL_BLOOM', 'SKILL_IRON_EDGE', 'SKILL_REFORGE',
  'SKILL_STONE_BARRIER', 'SKILL_ROCK_BLAST', 'SKILL_AIR_CUTTER', 'SKILL_WIND_GRACE',
  'SKILL_CHAIN_LIGHTNING', 'SKILL_LIGHTNING_ROD', 'SKILL_PRISM', 'SKILL_BLESSING',
  'SKILL_NIGHT_SHROUD', 'SKILL_SHADOW_CLONE', 'SKILL_GROUP_GUARD', 'SKILL_GROUP_HEAL',
  'SKILL_MARK_TARGET', 'SKILL_ANTI_HEAL', 'SKILL_ENERGIZE', 'SKILL_ELIMINATE'
)
$newInnatePassiveIds = @(
  'PASSIVE_RAGE', 'PASSIVE_IRON_BODY', 'PASSIVE_QUICK_FEET', 'PASSIVE_MENTAL_FOCUS',
  'PASSIVE_BERSERKER', 'PASSIVE_RECOVER_STRONG', 'PASSIVE_KILL_RECOVER',
  'PASSIVE_THORN_RETURN', 'PASSIVE_TEAM_HUSTLE'
)
$bookPassiveIds = @(
  'PASSIVE_BOOK_VANGUARD', 'PASSIVE_BOOK_FORTIFY', 'PASSIVE_BOOK_ENTRY_BOOST',
  'PASSIVE_BOOK_STURDY', 'PASSIVE_BOOK_RECUPERATE', 'PASSIVE_BOOK_ON_KILL_ATK',
  'PASSIVE_BOOK_DEATH_FIRE', 'PASSIVE_BOOK_THORN_AURA', 'PASSIVE_BOOK_LAST_STAND',
  'PASSIVE_BOOK_AVENGE'
)
foreach ($id in $newActiveIds) {
  Assert-True ($activeIds -contains $id) "缺少新增主动技能：$id"
}
foreach ($id in $newInnatePassiveIds + $bookPassiveIds) {
  Assert-True ($passiveIds -contains $id) "缺少新增被动技能：$id"
}

$vfxBySkill = [ordered]@{
  SKILL_MAGMA_CLASH = 'fx/elemental/vfx_fire_medium_sheet.png'
  SKILL_INFERNO = 'fx/elemental/vfx_fire_large_sheet.png'
  SKILL_WATER_WHIP = 'fx/elemental/vfx_water_small_sheet.png'
  SKILL_TORRENT = 'fx/elemental/vfx_water_medium_sheet.png'
  SKILL_BRANCH_STRIKE = 'fx/elemental/vfx_wood_small_sheet.png'
  SKILL_BLOOM = 'fx/combat/vfx_heal_aoe_sheet.png'
  SKILL_IRON_EDGE = 'fx/elemental/vfx_metal_small_sheet.png'
  SKILL_REFORGE = 'fx/combat/vfx_buff_up_sheet.png'
  SKILL_STONE_BARRIER = 'fx/combat/vfx_shield_sheet.png'
  SKILL_ROCK_BLAST = 'fx/elemental/vfx_earth_large_sheet.png'
  SKILL_AIR_CUTTER = 'fx/elemental/vfx_wind_medium_sheet.png'
  SKILL_WIND_GRACE = 'fx/combat/vfx_buff_up_sheet.png'
  SKILL_CHAIN_LIGHTNING = 'fx/elemental/vfx_thunder_large_sheet.png'
  SKILL_LIGHTNING_ROD = 'fx/combat/vfx_buff_up_sheet.png'
  SKILL_PRISM = 'fx/elemental/vfx_light_medium_sheet.png'
  SKILL_BLESSING = 'fx/combat/vfx_heal_small_sheet.png'
  SKILL_NIGHT_SHROUD = 'fx/combat/vfx_debuff_down_sheet.png'
  SKILL_SHADOW_CLONE = 'fx/elemental/vfx_dark_small_sheet.png'
  SKILL_GROUP_GUARD = 'fx/combat/vfx_buff_up_sheet.png'
  SKILL_GROUP_HEAL = 'fx/combat/vfx_heal_aoe_sheet.png'
  SKILL_MARK_TARGET = 'fx/combat/vfx_debuff_down_sheet.png'
  SKILL_ANTI_HEAL = 'fx/combat/vfx_debuff_down_sheet.png'
  SKILL_ENERGIZE = 'fx/combat/vfx_buff_up_sheet.png'
  SKILL_ELIMINATE = 'fx/combat/vfx_dispel_sheet.png'
}
foreach ($entry in $vfxBySkill.GetEnumerator()) {
  $assetPath = Join-Path $ProjectRoot ('frontend/public/assets/' + $entry.Value)
  Assert-True (Test-Path -LiteralPath $assetPath) "缺少 $($entry.Key) 的 VFX：$($entry.Value)"
}

foreach ($icon in @('physical_strike', 'projectile', 'claw_slash', 'wave', 'explosion', 'heal', 'shield', 'buff_up', 'debuff_down', 'mist', 'dispel', 'passive_badge')) {
  $assetPath = Join-Path $ProjectRoot "frontend/public/assets/ui/skill-types/icon_skilltype_$icon.png"
  Assert-True (Test-Path -LiteralPath $assetPath) "缺少技能 UI 图标：$icon"
}

foreach ($entry in @(
  "SKILL_MAGMA_CLASH: 'medium'", "SKILL_INFERNO: 'large'", "SKILL_TORRENT: 'medium'",
  "SKILL_ROCK_BLAST: 'large'", "SKILL_AIR_CUTTER: 'medium'", "SKILL_CHAIN_LIGHTNING: 'large'",
  "SKILL_PRISM: 'medium'", "SKILL_REFORGE: 'buff_up'", "SKILL_WIND_GRACE: 'buff_up'",
  "SKILL_LIGHTNING_ROD: 'buff_up'", "SKILL_GROUP_GUARD: 'buff_up'", "SKILL_ENERGIZE: 'buff_up'",
  "SKILL_NIGHT_SHROUD: 'debuff_down'", "SKILL_MARK_TARGET: 'debuff_down'",
  "SKILL_ANTI_HEAL: 'debuff_down'", "SKILL_ELIMINATE: 'dispel'"
)) {
  Assert-Contains $battleViewText $entry "BattleView 缺少 VFX 映射：$entry"
}
foreach ($entry in @(
  "if (event.statusId === 'REGEN') return combatVfx('buff_up', targetId)",
  "if (event.statusId === 'STEALTH') return { category: 'elemental', template: 'dark_small', targetId }",
  'event.data?.passive === true'
)) {
  Assert-Contains $battleViewText $entry "BattleView 缺少状态或被动 VFX 适配：$entry"
}
foreach ($entry in @(
  "[/^(PASSIVE_)/, 'passive_badge']", "if (!type && skill.effectType === 'HEAL') type = 'heal'",
  "if (!type && skill.effectType === 'SHIELD') type = 'shield'",
  "if (!type && skill.damageType === 'PHYSICAL') type = 'physical_strike'",
  "if (!type && skill.damageType === 'MAGICAL') type = 'projectile'"
)) {
  Assert-Contains $assetHelperText $entry "技能 UI 回退映射缺失：$entry"
}

Write-Output "技能视觉资源检查通过：$($activeIds.Count) 个主动技能、$($passiveIds.Count) 个被动技能（14 个固有/升级 + 10 个技能书）；新增 $($newActiveIds.Count) 个主动、$($newInnatePassiveIds.Count) 个固有被动与 $($bookPassiveIds.Count) 个技能书被动均已覆盖。"
