$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

Write-Output "=============================="
Write-Output "阶段 7 Boss 系统 E2E 验收脚本"
Write-Output "=============================="

# 1. 新游戏
$new = Invoke-RestMethod -Uri "$base/api/game/new-game" -Method Post -ContentType 'application/json' -Body (@{playerName='Boss测试'; avatarId='AVATAR_DEFAULT'; petChoiceId='PET_FIRE_001'} | ConvertTo-Json)
Write-Output ("1. 新游戏 success=" + $new.success)

# 2. 查询 Boss 列表
$bosses = Invoke-RestMethod -Uri "$base/api/bosses"
Write-Output ("2. Boss 列表 success=" + $bosses.success + " count=" + $bosses.data.Count)
if ($bosses.data.Count -gt 0) {
    foreach ($b in $bosses.data) {
        Write-Output ("   Boss: " + $b.bossId + " name=" + $b.name + " element=" + $b.element + " luckValue=" + $b.luckValue)
    }
}

# 3. 查询单个 Boss 详情
$bossId = $bosses.data[0].bossId
$detail = Invoke-RestMethod -Uri "$base/api/bosses/$bossId"
Write-Output ("3. Boss 详情 success=" + $detail.success + " name=" + $detail.data.name)
Write-Output ("   难度数: " + $detail.data.difficulties.Count)
foreach ($d in $detail.data.difficulties) {
    Write-Output ("   " + $d.difficulty + " unlocked=" + $d.unlocked + " defeatCount=" + $d.defeatCount)
}

# 4. 开始 Boss 战斗（普通难度）
$battle = Invoke-RestMethod -Uri "$base/api/bosses/$bossId/battle" -Method Post -ContentType 'application/json' -Body '{"difficulty":"NORMAL","seed":20260813}'
Write-Output ("4. Boss 开战 success=" + $battle.success + " battleId=" + $battle.data.battleId)

# 5. 验证战斗中 uncapturable
$snapRes = Invoke-RestMethod -Uri ("$base/api/battles/" + $battle.data.battleId)
$snap = $snapRes.data
Write-Output ("5. 快照 battleType=" + $snap.battleType + " uncapturable=" + $snap.uncapturable)
Write-Output ("   敌方单位: " + (($snap.enemyUnits | ForEach-Object { $_.unitId + '/HP' + $_.maxHp }) -join ' | '))

# 6. 进行战斗（手动打 10 回合）
$round = 0
while (-not $snap.finished -and $round -lt 30) {
    $round++
    $playerUnits = $snap.playerUnits | Where-Object { $_.alive -and $_.active }
    $enemyTarget = $snap.enemyUnits | Where-Object { $_.alive -and $_.active } | Select-Object -First 1
    if (-not $enemyTarget) { break }
    $actions = @()
    foreach ($pu in $playerUnits) {
        $skillId = $pu.skillIds[0]
        $actions += @{type='SKILL'; petId=$pu.unitId; skillId=$skillId; targetId=$enemyTarget.unitId}
    }
    $body = @{actions = $actions} | ConvertTo-Json -Depth 5
    $res = Invoke-RestMethod -Uri ("$base/api/battles/" + $snap.battleId + "/actions") -Method Post -ContentType 'application/json' -Body $body
    if (-not $res.success) { Write-Output ("回合失败: " + $res.code + " " + $res.message); break }
    $snap = $res.data
    # 检查阶段转换事件
    $phaseEvents = $snap.events | Where-Object { $_.type -eq 'PHASE_TRANSITION' }
    if ($phaseEvents.Count -gt 0) {
        Write-Output ("   回合" + $snap.currentRound + " *** 阶段转换! ***")
    }
}
Write-Output ("6. 战斗结束 finished=" + $snap.finished + " winner=" + $snap.winner + " 总回合=" + $snap.currentRound)

# 7. 结算战斗
$settle = Invoke-RestMethod -Uri ("$base/api/battles/" + $snap.battleId + "/settle") -Method Post -ContentType 'application/json' -Body '{}'
Write-Output ("7. 结算 success=" + $settle.success)
if ($settle.success) {
    Write-Output ("   playerWon=" + $settle.data.playerWon + " exp=" + $settle.data.expGained + " gold=" + $settle.data.goldGained)
}

# 8. 再次查询 Boss 列表（验证击败次数和幸运值）
$after = Invoke-RestMethod -Uri "$base/api/bosses"
Write-Output ("8. 战后 Boss 列表:")
foreach ($b in $after.data) {
    Write-Output ("   " + $b.bossId + " luckValue=" + $b.luckValue + " 总击败=" + (($b.difficulties | ForEach-Object { $_.defeatCount } | Measure-Object -Sum).Sum))
}

# 9. 自动挑战（需已手动击败过普通难度）
$autoBody = @{difficulty='NORMAL'; mode='ONCE'} | ConvertTo-Json
$auto = Invoke-RestMethod -Uri "$base/api/bosses/$bossId/auto" -Method Post -ContentType 'application/json' -Body $autoBody
Write-Output ("9. 自动挑战 success=" + $auto.success)
if ($auto.success) {
    $ar = $auto.data
    Write-Output ("   总场次=" + $ar.totalBattles + " 胜=" + $ar.wins + " 负=" + $ar.losses + " exp=" + $ar.totalExp + " gold=" + $ar.totalGold + " luck=" + $ar.finalLuck)
}

# 10. 幸运值不足时兑换应失败
$exch = Invoke-RestMethod -Uri "$base/api/bosses/$bossId/exchange" -Method Post -ContentType 'application/json' -Body '{"dropItemId":"ITEM_EARTH_MATERIAL"}'
Write-Output ("10. 幸运兑换（应失败 luck<100）code=" + $exch.code)

Write-Output ""
Write-Output "=============================="
Write-Output "E2E Boss 验收完成"
Write-Output "=============================="
