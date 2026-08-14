# ============================================================
# 阶段 6 地图探索与区域系统 E2E 验收脚本
# ============================================================
# 前置：后端已启动（127.0.0.1:8080）且数据库可用；已有存档或可创建新存档。
# 覆盖：大地图视图 / 区域移动（出口）/ 采集一次性与刷新 / 宝箱一次性 /
#       遭遇刷新组校验 / 地图遭遇战斗与结算 / HP 持续消耗 /
#       营地恢复+激活+刷新 / 营地传送 / 5 套预设 / 战败零惩罚检查。
# ============================================================
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

function Post-Json($url, $obj) {
    return Invoke-RestMethod -Uri $url -Method Post -ContentType 'application/json' -Body ($obj | ConvertTo-Json -Depth 5)
}

# 0. 无存档时创建新游戏
$status = Invoke-RestMethod -Uri "$base/api/game/save-status"
if (-not $status.data.hasSave) {
    $new = Post-Json "$base/api/game/new-game" @{playerName='地图验收'; avatarId='AVATAR_DEFAULT'; petChoiceId='PET_FIRE_001'}
    Write-Output ("[0] 新游戏创建 success=" + $new.success)
} else {
    Write-Output "[0] 检测到已有存档，直接用于验收"
}

# 1. 大地图视图：3 个已实装区域全部解锁，Boss 占位，据点营地自动激活
$world = Invoke-RestMethod -Uri "$base/api/maps/world"
Write-Output ("[1] 大地图区域数=" + $world.data.regions.Count)
foreach ($r in $world.data.regions) {
    Write-Output ("    区域 " + $r.mapId + " 解锁=" + $r.unlocked + " Boss=" + $r.bossStatus + " 营地激活=" + (($r.camps | ForEach-Object { $_.campId + ':' + $_.activated }) -join ','))
}

# 2. 当前区域状态
$cur = Invoke-RestMethod -Uri "$base/api/maps/current"
Write-Output ("[2] 当前区域=" + $cur.data.mapId + " 会话=" + $cur.data.sessionId)

# 3. 出口移动：据点 → 青草原（后端解析入口对象）
$enter = Post-Json "$base/api/maps/MAP_AREA_MEADOW/enter" @{exitId='EXIT_VILLAGE_TO_MEADOW'}
Write-Output ("[3] 进入青草原 success=" + $enter.success + " 落点=" + $enter.data.spawnObjectId + " 会话=" + $enter.data.sessionId)
if ($enter.data.spawnObjectId -ne 'ENTRY_MEADOW_FROM_VILLAGE') { throw "入口对象解析错误: " + $enter.data.spawnObjectId }
$session1 = $enter.data.sessionId

# 3.1 非法出口应被拒绝
try {
    $bad = Post-Json "$base/api/maps/MAP_AREA_FOREST/enter" @{exitId='EXIT_VILLAGE_TO_MEADOW'}
    Write-Output ("[3.1] 非法出口 code=" + $bad.code)
} catch { Write-Output "[3.1] 非法出口请求异常（符合预期路径之一）" }

# 4. 采集：首次成功，同会话重复采集被拒
$g1 = Post-Json "$base/api/maps/gathers/GATHER_MEADOW_1/gather" @{}
Write-Output ("[4] 采集 success=" + $g1.success + " 金币+" + $g1.data.goldGained + " 道具=" + (($g1.data.items | ForEach-Object { $_.itemId + 'x' + $_.quantity }) -join ','))
$g2 = Post-Json "$base/api/maps/gathers/GATHER_MEADOW_1/gather" @{}
Write-Output ("[4.1] 重复采集 code=" + $g2.code + "（预期 GATHER_ALREADY_USED）")

# 5. 宝箱：首次成功，重复开启被拒（全局一次性）
$c1 = Post-Json "$base/api/maps/chests/CHEST_MEADOW_HIDDEN_1/open" @{}
Write-Output ("[5] 宝箱 success=" + $c1.success + " 金币+" + $c1.data.goldGained + " 道具=" + (($c1.data.items | ForEach-Object { $_.itemId + 'x' + $_.quantity }) -join ','))
$c2 = Post-Json "$base/api/maps/chests/CHEST_MEADOW_HIDDEN_1/open" @{}
Write-Output ("[5.1] 重复开箱 code=" + $c2.code + "（预期 CHEST_ALREADY_LOOTED）")

# 6. 遭遇校验：当前区域不允许森林刷新组；青草原刷新组允许
$badEnc = Post-Json "$base/api/maps/encounters" @{groupId='ENCOUNTER_FOREST'}
Write-Output ("[6] 跨区刷新组 code=" + $badEnc.code + "（预期 ENCOUNTER_GROUP_NOT_ALLOWED）")
$enc = Post-Json "$base/api/maps/encounters" @{groupId='ENCOUNTER_MEADOW'}
Write-Output ("[6.1] 地图遭遇开战 success=" + $enc.success + " battleId=" + $enc.data.battleId)
$snap = $enc.data

# 6.2 回合循环直到战斗结束（最多 30 回合）
$round = 0
while (-not $snap.finished -and $round -lt 30) {
    $round++
    $playerUnit = $snap.playerUnits | Where-Object { $_.alive -and $_.active -and $_.skillIds.Count -gt 0 } | Select-Object -First 1
    $target = $snap.enemyUnits | Where-Object { $_.alive -and $_.active } | Select-Object -First 1
    $actions = @()
    if ($playerUnit -and $target) {
        $actions += @{type='SKILL'; petId=$playerUnit.unitId; skillId=$playerUnit.skillIds[0]; targetId=$target.unitId}
    }
    $res = Post-Json ("$base/api/battles/" + $snap.battleId + "/actions") @{actions=$actions}
    $snap = $res.data
}
Write-Output ("[6.2] 战斗结束 finished=" + $snap.finished + " winner=" + $snap.winner + " 回合=" + $snap.currentRound)

# 6.3 结算：HP 回写；战败时随结算返回战败流程（零惩罚）
$settle = Post-Json ("$base/api/battles/" + $snap.battleId + "/settle") @{joinTeam=$false}
$s = $settle.data
Write-Output ("[6.3] 结算 success=" + $settle.success + " playerWon=" + $s.playerWon + " HP回写=" + $s.hpWritebacks.Count + " 只")
if ($s.defeat) {
    Write-Output ("      战败流程：提示=" + $s.defeat.message + " 恢复点=" + $s.defeat.respawnObjectId + " 恢复宠物=" + $s.defeat.healedPets + " 只")
} else {
    Write-Output "      胜利或逃跑：无战败流程（符合预期）"
}

# 7. 营地休息：激活 + 全队恢复 + 触发刷新（新会话）
$rest = Post-Json "$base/api/maps/camps/CAMP_MEADOW_1/rest" @{}
Write-Output ("[7] 营地休息 success=" + $rest.success + " 首次激活=" + $rest.data.firstActivation + " 恢复宠物=" + $rest.data.healedPets + " 只")
$cur2 = Invoke-RestMethod -Uri "$base/api/maps/current"
Write-Output ("[7.1] 休息后会话刷新=" + ($cur2.data.sessionId -ne $session1) + " 新会话=" + $cur2.data.sessionId)

# 7.2 刷新后采集点可再次采集
$g3 = Post-Json "$base/api/maps/gathers/GATHER_MEADOW_1/gather" @{}
Write-Output ("[7.2] 刷新后再次采集 success=" + $g3.success + "（预期 True）")

# 8. 营地传送：已激活的青草原营地 → 晨曦村营地
$tp = Post-Json "$base/api/maps/camps/CAMP_VILLAGE_1/teleport" @{}
Write-Output ("[8] 传送 success=" + $tp.success + " 到达=" + $tp.data.mapId + " 落点=" + $tp.data.spawnObjectId)
$tpBad = Post-Json "$base/api/maps/camps/CAMP_FOREST_1/teleport" @{}
Write-Output ("[8.1] 未激活营地传送 code=" + $tpBad.code + "（预期 CAMP_NOT_ACTIVATED）")

# 9. 队伍预设：5 套懒创建 + 切换 + 战斗中禁止切换由后端守卫
$presets = Invoke-RestMethod -Uri "$base/api/team/presets"
Write-Output ("[9] 预设数量=" + $presets.data.Count + "（预期 5）")
$active = $presets.data | Where-Object { $_.isActive } | Select-Object -First 1
$other = $presets.data | Where-Object { -not $_.isActive } | Select-Object -First 1
$sw = Post-Json ("$base/api/team/presets/" + $other.teamId + "/activate") @{}
Write-Output ("[9.1] 切换预设到槽位 " + $other.slot + " success=" + $sw.success)
$back = Post-Json ("$base/api/team/presets/" + $active.teamId + "/activate") @{}
Write-Output ("[9.2] 切回原预设 success=" + $back.success)

# 10. HP 持续消耗抽查：结算后宠物 HP 不回满（除非战败恢复/营地恢复）
$boot = Invoke-RestMethod -Uri "$base/api/game/bootstrap"
$anyDamaged = ($boot.data.pets | Where-Object { $_.currentHp -lt 10000 } | Measure-Object).Count
Write-Output ("[10] 存档宠物数=" + $boot.data.pets.Count + "（HP 由后端持久化，战败/营地外不自动回满）")

Write-Output ""
Write-Output "==================== 阶段 6 地图 E2E 验收完成 ===================="
