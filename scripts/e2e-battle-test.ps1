$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

# 1. 新游戏（烬牙兽 FIRE）
$new = Invoke-RestMethod -Uri "$base/api/game/new-game" -Method Post -ContentType 'application/json' -Body (@{playerName='测试玩家'; avatarId='AVATAR_DEFAULT'; petChoiceId='PET_FIRE_001'} | ConvertTo-Json)
Write-Output ("新游戏 success=" + $new.success)

# 2. 开始测试战斗（固定种子 20260813）
$battle = Invoke-RestMethod -Uri "$base/api/battles" -Method Post -ContentType 'application/json' -Body '{"type":"TEST_BATTLE","seed":20260813}'
$snap = $battle.data
Write-Output ("开战 success=" + $battle.success + " battleId=" + $snap.battleId + " seed=" + $snap.seed)
Write-Output ("玩家单位: " + (($snap.playerUnits | ForEach-Object { $_.unitId + '/' + $_.element + '/HP' + $_.maxHp + '/技能:' + ($_.skillIds -join ',') }) -join ' | '))
Write-Output ("敌方单位: " + (($snap.enemyUnits | ForEach-Object { $_.unitId + '/' + $_.element + '/HP' + $_.maxHp }) -join ' | '))
Write-Output ("开战事件数: " + $snap.events.Count)

$playerUnit = $snap.playerUnits[0]
$skill = $playerUnit.skillIds[0]
$round = 0
$firstRoundSeq = ''
while (-not $snap.finished -and $round -lt 20) {
    $round++
    $target = $snap.enemyUnits | Where-Object { $_.alive -and $_.active } | Select-Object -First 1
    $body = @{actions = @(@{type='SKILL'; petId=$playerUnit.unitId; skillId=$skill; targetId=$target.unitId})} | ConvertTo-Json -Depth 5
    $res = Invoke-RestMethod -Uri ("$base/api/battles/" + $snap.battleId + "/actions") -Method Post -ContentType 'application/json' -Body $body
    if (-not $res.success) { Write-Output ("回合失败: " + $res.code + " " + $res.message); break }
    $snap = $res.data
    if ($round -eq 1) { $firstRoundSeq = (($snap.events | ForEach-Object { $_.type }) -join ',') }
    $dmg = $snap.events | Where-Object { $_.type -eq 'DAMAGE' -and $_.sourceId -eq $playerUnit.unitId } | Select-Object -First 1
    $hp = ($snap.playerUnits | Where-Object { $_.unitId -eq $playerUnit.unitId }).currentHp
    Write-Output ("回合" + $snap.currentRound + " 目标=" + $target.unitId + " 输出伤害=" + $dmg.value + " 克制=" + $dmg.elementRelation + " 暴击=" + $dmg.critical + " 我方HP=" + $hp + " 事件数=" + $snap.events.Count)
}
Write-Output ("战斗结束 finished=" + $snap.finished + " winner=" + $snap.winner + " 总回合=" + $snap.currentRound)

# 3. 战斗结束后再行动应报 BATTLE_FINISHED
$after = Invoke-RestMethod -Uri ("$base/api/battles/" + $snap.battleId + "/actions") -Method Post -ContentType 'application/json' -Body '{"actions":[]}'
Write-Output ("结束后行动 code=" + $after.code)

# 4. 查询战斗快照接口
$get = Invoke-RestMethod -Uri ("$base/api/battles/" + $snap.battleId)
Write-Output ("查询快照 success=" + $get.success + " finished=" + $get.data.finished)

# 5. 种子复现：同种子重开一场，比较前 3 回合事件类型序列
$b2 = Invoke-RestMethod -Uri "$base/api/battles" -Method Post -ContentType 'application/json' -Body '{"type":"TEST_BATTLE","seed":20260813}'
$snap2 = $b2.data
$p2 = $snap2.playerUnits[0]
$r2 = Invoke-RestMethod -Uri ("$base/api/battles/" + $snap2.battleId + "/actions") -Method Post -ContentType 'application/json' -Body (@{actions = @(@{type='SKILL'; petId=$p2.unitId; skillId=$p2.skillIds[0]; targetId=($snap2.enemyUnits | Where-Object { $_.alive } | Select-Object -First 1).unitId})} | ConvertTo-Json -Depth 5)
Write-Output ("复现回合1事件序列一致=" + $firstRoundSeq.Equals(($r2.data.events | ForEach-Object { $_.type }) -join ','))
