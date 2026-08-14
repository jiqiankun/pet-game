# ============================================================
# 阶段 5 捕捉与仓库 E2E 验收脚本
# ============================================================
# 前置条件：后端已启动（127.0.0.1:8080）且 pet_game 数据库可用（建议空库）。
# 流程：新游戏（赠送捕捉球）→ 捕捉率查询 → 第一次遭遇捕捉（结算入队，需求 §48）→
#       第二次遭遇捕捉（结算留仓库）→ 捕捉球消耗校验 →
#       仓库筛选/排序/昵称/锁定/收藏/放生预览/放生礼物汇总。
# 说明：初始宠物 Lv.5（用户裁决），可直接与野生宠物战斗；脚本仍尝试多个遭遇种子、
#       每回合直接投球；普通球满血 COMMON 捕捉率约 27%+，多次尝试整体成功率极高。
# 注意：脚本会创建新存档，验收后如需干净环境请自行清理数据库。
# ============================================================

$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$script:attempt = 0

function Api([string]$method, [string]$path, $body = $null) {
    $uri = "$base$path"
    if ($method -eq 'GET') {
        return Invoke-RestMethod -Uri $uri -Method Get
    }
    $json = if ($null -ne $body) { $body | ConvertTo-Json -Depth 8 -Compress } else { '{}' }
    return Invoke-RestMethod -Uri $uri -Method $method -Body $json -ContentType 'application/json'
}

function Assert([bool]$cond, [string]$name) {
    if ($cond) { Write-Host "  [PASS] $name" -ForegroundColor Green }
    else { Write-Host "  [FAIL] $name" -ForegroundColor Red; $script:failed = $true }
}

function NextBall {
    $script:attempt++
    if ($script:attempt -le 10) { return 'ITEM_CAPTURE_BALL_NORMAL' }
    if ($script:attempt -le 15) { return 'ITEM_CAPTURE_BALL_GREAT' }
    return 'ITEM_CAPTURE_BALL_ULTRA'
}

# HP 跨战斗保留：用复苏药 + 恢复药把队伍宠物回满（无道具时自然跳过）
function HealTeam {
    $b = Api GET '/api/game/bootstrap'
    foreach ($p in @($b.data.pets)) {
        $summary = $b.data.petSummaries | Where-Object { $_.pet.id -eq $p.id }
        $maxHp = if ($summary) { $summary.panelStats.maxHp } else { $p.currentHp }
        $hp = $p.currentHp
        # 倒下宠物先复苏
        if ($hp -le 0) {
            $inv = Api GET '/api/inventory'
            $revive = @($inv.data.items | Where-Object { $_.itemId -eq 'ITEM_REVIVE' -and $_.quantity -gt 0 })
            if ($revive.Count -eq 0) { continue }
            $use = Api POST '/api/inventory/use' @{ itemId = 'ITEM_REVIVE'; petId = $p.id }
            if (-not $use.success) { continue }
            $hp = $use.data.afterHp
        }
        # 恢复药补满
        $guard = 0
        while ($hp -lt $maxHp -and $guard -lt 20) {
            $guard++
            $inv = Api GET '/api/inventory'
            $potion = @($inv.data.items | Where-Object { $_.itemId -eq 'ITEM_POTION_SMALL' -and $_.quantity -gt 0 })
            if ($potion.Count -eq 0) { break }
            $use = Api POST '/api/inventory/use' @{ itemId = 'ITEM_POTION_SMALL'; petId = $p.id }
            if (-not $use.success) { break }
            $hp = $use.data.afterHp
        }
    }
}

# 发起野生战斗并尝试捕捉，返回捕捉成功后的最终快照（失败返回 $null）
function TryCapture([int[]]$seeds, [bool]$checkRates) {
    foreach ($seed in $seeds) {
        # 每场遭遇前先恢复队伍（HP 跨战斗保留；无道具时自然跳过）
        HealTeam
        # 全队倒下且无复苏药：提前终止
        $check = Api GET '/api/game/bootstrap'
        $anyAlive = @($check.data.pets | Where-Object { $_.currentHp -gt 0 }).Count -gt 0
        $hasRevive = @((Api GET '/api/inventory').data.items | Where-Object { $_.itemId -eq 'ITEM_REVIVE' -and $_.quantity -gt 0 }).Count -gt 0
        if (-not $anyAlive -and -not $hasRevive) {
            Write-Host '  全队倒下且无复苏道具，终止尝试' -ForegroundColor Yellow
            return $null
        }
        $battle = Api POST '/api/wild/battles' @{ seed = $seed }
        if (-not $battle.success) { continue }
        $battleId = $battle.data.battleId

        if ($checkRates) {
            $rates = Api GET "/api/wild/battles/$battleId/capture-rates"
            $rateList = @($rates.data)
            Assert ($rateList.Count -gt 0) "捕捉率列表非空（$($rateList.Count) 条）"
            $sample = $rateList | Select-Object -First 1
            Write-Host "  示例：$($sample.unitName) x $($sample.ballName) = $([math]::Round($sample.rate * 100, 1))%"
            $outOfRange = @($rateList | Where-Object { $_.rate -lt 0 -or $_.rate -gt 1 })
            Assert ($outOfRange.Count -eq 0) '捕捉率均在 [0,1] 区间'
        }

        $round = 0
        while (-not $battle.data.finished -and $round -lt 8) {
            $round++
            $playerActive = @($battle.data.playerUnits | Where-Object { $_.alive -and $_.active })
            $enemyActive = @($battle.data.enemyUnits | Where-Object { $_.alive -and $_.active -and -not $_.captured })
            if ($playerActive.Count -eq 0 -or $enemyActive.Count -eq 0) { break }
            $targetUnit = $enemyActive[0]
            $actions = @()
            if ($targetUnit.currentHp / $targetUnit.maxHp -gt 0.6) {
                # 目标 HP 较高：仅第一只宠物攻击压血（避免误杀），其余防御
                foreach ($unit in $playerActive) {
                    if ($unit.unitId -eq $playerActive[0].unitId) {
                        $ready = @($unit.skillIds | Where-Object { ($unit.cooldowns.$_) -le 0 })
                        if ($ready.Count -gt 0) {
                            $actions += @{ type = 'SKILL'; petId = $unit.unitId; skillId = $ready[0]; targetId = $targetUnit.unitId }
                        } else {
                            $actions += @{ type = 'DEFEND'; petId = $unit.unitId }
                        }
                    } else {
                        $actions += @{ type = 'DEFEND'; petId = $unit.unitId }
                    }
                }
            } else {
                # 目标 HP 较低：第一只投球，其余防御避免误杀
                $ballItemId = NextBall
                $first = $true
                foreach ($unit in $playerActive) {
                    if ($first) {
                        $actions += @{ type = 'CAPTURE'; petId = $unit.unitId; itemId = $ballItemId; targetId = $targetUnit.unitId }
                        $first = $false
                    } else {
                        $actions += @{ type = 'DEFEND'; petId = $unit.unitId }
                    }
                }
            }
            $battle = Api POST "/api/battles/$battleId/actions" @{ actions = $actions }
            if (-not $battle.success) { break }
            if (@($battle.data.events | Where-Object { $_.type -eq 'CAPTURE_SUCCESS' }).Count -gt 0) {
                Write-Host "  seed=$seed 回合 $round：捕捉成功！" -ForegroundColor Green
                return $battle
            }
        }
        # 本场未捕捉成功且战斗已结束（战败）：同样执行结算（HP 回写 + 已用捕捉球扣除，无奖励）
        if ($battle.data.finished) {
            $null = Api POST "/api/battles/$battleId/settle" @{ joinTeam = $false }
        }
    }
    return $null
}

# 捕捉成功后把战斗推进到结束（其余敌方打完或战斗自然结束）
function FinishBattle($battle) {
    $guard = 0
    while (-not $battle.data.finished -and $guard -lt 20) {
        $guard++
        $battleId = $battle.data.battleId
        $playerActive = @($battle.data.playerUnits | Where-Object { $_.alive -and $_.active })
        $enemyActive = @($battle.data.enemyUnits | Where-Object { $_.alive -and $_.active -and -not $_.captured })
        if ($playerActive.Count -eq 0 -or $enemyActive.Count -eq 0) { break }
        $actions = @()
        foreach ($unit in $playerActive) {
            $ready = @($unit.skillIds | Where-Object { ($unit.cooldowns.$_) -le 0 })
            if ($ready.Count -gt 0) {
                $actions += @{ type = 'SKILL'; petId = $unit.unitId; skillId = $ready[0]; targetId = $enemyActive[0].unitId }
            } else {
                $actions += @{ type = 'DEFEND'; petId = $unit.unitId }
            }
        }
        $battle = Api POST "/api/battles/$battleId/actions" @{ actions = $actions }
        if (-not $battle.success) { break }
    }
    return $battle
}

$script:failed = $false

Write-Host "`n== 1. 新游戏 ==" -ForegroundColor Cyan
$ng = Api POST '/api/game/new-game' @{ playerName = 'E2E玩家'; avatarId = 'AVATAR_DEFAULT'; petChoiceId = 'PET_FIRE_001' }
Assert $ng.success '新游戏创建成功'

$boot = Api GET '/api/game/bootstrap'
$balls = @($boot.data.inventory | Where-Object { $_.itemType -eq 'CAPTURE_BALL' })
Assert ($balls.Count -eq 3) '新游戏赠送三档捕捉球'
$totalBalls = ($balls | Measure-Object -Property quantity -Sum).Sum
Assert ($totalBalls -eq 17) "捕捉球总数 17（10/5/2），实际 $totalBalls"
Assert (@($boot.data.inventory | Where-Object { $_.itemId -eq 'ITEM_POTION_SMALL' }).Count -eq 1) '新游戏赠送小型恢复药'

Write-Host "`n== 2. 第一次遭遇：捕捉并直接入队 ==" -ForegroundColor Cyan
$battle1 = TryCapture (101..140) $true
Assert ($null -ne $battle1) '第一次遭遇捕捉成功'
if ($null -eq $battle1) { Write-Host '捕捉未成功，终止' -ForegroundColor Red; exit 1 }
$battle1 = FinishBattle $battle1
$settle1 = Api POST "/api/battles/$($battle1.data.battleId)/settle" @{ joinTeam = $true }
Assert $settle1.success '第一次结算成功'
Assert (@($settle1.data.hpWritebacks).Count -gt 0) 'HP 回写明细非空'
$cap1 = @($settle1.data.capturedPets)
Assert ($cap1.Count -gt 0) '捕捉宠物已落库'
Write-Host "  捕捉：$($cap1[0].name) Lv.$($cap1[0].level)（$($cap1[0].rarity)）入队位置=$($cap1[0].teamPosition)"
Assert ($null -ne $cap1[0].teamPosition) '队伍未满时捕捉宠物直接入队（需求 §48）'

Write-Host "`n== 3. 第二次遭遇：捕捉并留在仓库 ==" -ForegroundColor Cyan
HealTeam
$battle2 = TryCapture (201..280) $false
Assert ($null -ne $battle2) '第二次遭遇捕捉成功'
if ($null -eq $battle2) { Write-Host '捕捉未成功，终止' -ForegroundColor Red; exit 1 }
$battle2 = FinishBattle $battle2
$settle2 = Api POST "/api/battles/$($battle2.data.battleId)/settle" @{ joinTeam = $false }
Assert $settle2.success '第二次结算成功'
$cap2 = @($settle2.data.capturedPets)
Assert ($cap2.Count -gt 0) '第二只捕捉宠物已落库'
Assert ($null -eq $cap2[0].teamPosition) 'joinTeam=false 时留在仓库'

$boot2 = Api GET '/api/game/bootstrap'
$ballsAfter = ($boot2.data.inventory | Where-Object { $_.itemType -eq 'CAPTURE_BALL' } | Measure-Object -Property quantity -Sum).Sum
Assert ($ballsAfter -eq ($totalBalls - $script:attempt)) "捕捉球消耗 = 尝试次数（$totalBalls → $ballsAfter，尝试 $($script:attempt) 次，失败也消耗）"

Write-Host "`n== 4. 仓库：筛选/排序/昵称/锁定/收藏/放生 ==" -ForegroundColor Cyan
$storage = Api GET '/api/storage/pets'
$allPets = @($storage.data)
Assert ($allPets.Count -ge 3) "仓库宠物数 $($allPets.Count)（初始宠 + 2 捕捉宠）"

# 筛选与排序
$filtered = Api GET '/api/storage/pets?rarity=COMMON&sortBy=LEVEL&sortDirection=ASC'
$filteredPets = @($filtered.data)
Assert ($filteredPets.Count -ge 1) "稀有度筛选生效（COMMON $($filteredPets.Count) 只）"
if ($filteredPets.Count -ge 2) {
    Assert ($filteredPets[0].level -le $filteredPets[1].level) '等级升序排序生效'
}

# 目标：第二次捕捉的宠物（在仓库、未锁定未收藏）
$pet = $allPets | Where-Object { $_.petId -eq $cap2[0].petId }
$petId = $pet.petId

# 昵称（种族名称保留）
$nick = Api PUT "/api/storage/pets/$petId/nickname" @{ nickname = '小测' }
Assert ($nick.success -and $nick.data.nickname -eq '小测') '昵称设置成功'
Assert (-not [string]::IsNullOrEmpty($nick.data.speciesName)) "种族名称保留（$($nick.data.speciesName)）"

# 收藏标记
$null = Api PUT "/api/storage/pets/$petId/favorite" @{ value = $true }
$previewFav = Api POST '/api/storage/release-preview' @{ petIds = @($petId) }
Assert (-not $previewFav.data.pets[0].releasable) '收藏宠物不可放生'
Assert (@($previewFav.data.pets[0].blockReasons) -contains 'FAVORITE') '保护原因 FAVORITE'
$null = Api PUT "/api/storage/pets/$petId/favorite" @{ value = $false }

# 锁定保护
$null = Api PUT "/api/storage/pets/$petId/locked" @{ value = $true }
$preview = Api POST '/api/storage/release-preview' @{ petIds = @($petId) }
Assert (-not $preview.data.pets[0].releasable) '锁定宠物不可放生'
Assert (@($preview.data.pets[0].blockReasons) -contains 'LOCKED') '保护原因 LOCKED'
$relLocked = Api POST '/api/storage/release' @{ petIds = @($petId) }
Assert ((-not $relLocked.success) -and ($relLocked.code -eq 'PET_PROTECTED')) '单只放生锁定宠物返回 PET_PROTECTED'
$null = Api PUT "/api/storage/pets/$petId/locked" @{ value = $false }

# 批量预览：初始宠（锁定+收藏+在队）与在队捕捉宠均应受保护
$starter = $allPets | Where-Object { $_.starter }
$inTeamPet = $allPets | Where-Object { $_.petId -eq $cap1[0].petId }
$batchPreview = Api POST '/api/storage/release-preview' @{ petIds = @($starter.petId, $inTeamPet.petId, $petId) }
$starterInfo = $batchPreview.data.pets | Where-Object { $_.petId -eq $starter.petId }
$inTeamInfo = $batchPreview.data.pets | Where-Object { $_.petId -eq $inTeamPet.petId }
Assert (-not $starterInfo.releasable) '初始宠（锁定/收藏/在队）不可放生'
Assert (-not $inTeamInfo.releasable) '在队宠物不可放生'
Assert (@($inTeamInfo.blockReasons) -contains 'IN_TEAM') '保护原因 IN_TEAM'

# 批量放生：自动排除受保护宠物
$relBatch = Api POST '/api/storage/release' @{ petIds = @($starter.petId, $inTeamPet.petId, $petId) }
Assert $relBatch.success '批量放生请求成功'
Assert (@($relBatch.data.released).Count -eq 1) '批量放生仅执行 1 只（其余自动排除）'
Assert (@($relBatch.data.skipped).Count -eq 2) "排除 2 只受保护宠物（实际 $(@($relBatch.data.skipped).Count)）"

# 放生预览：礼物点数底线（重新预览一只新目标已不存在，用上一步结果校验点数）
# 此处用批量结果中的点数与礼物校验底线规则
Assert ($relBatch.data.totalGiftPoints -ge 20) "礼物点数 >= 稀有度基础值（实际 $($relBatch.data.totalGiftPoints)）"
$gifts = @($relBatch.data.gifts)
Assert ($gifts.Count -gt 0) "临别礼物非空（$($gifts.Count) 项）"
$giftValue = ($gifts | Measure-Object -Property value -Sum).Sum
Assert ($giftValue -ge $relBatch.data.totalGiftPoints) "礼物总价值 $giftValue >= 应得点数 $($relBatch.data.totalGiftPoints)（底线规则）"
Write-Host "  礼物：$(($gifts | ForEach-Object { "$($_.type)x$($_.quantity)" }) -join '、')（总点数 $($relBatch.data.totalGiftPoints)）"

# 礼物实际发放
$boot3 = Api GET '/api/game/bootstrap'
if (@($gifts | Where-Object { $_.type -eq 'GOLD' }).Count -gt 0) {
    Assert ($boot3.data.player.gold -gt $boot2.data.player.gold) '金币礼物已发放'
}
if (@($gifts | Where-Object { $_.type -eq 'EXP' }).Count -gt 0) {
    Assert ($boot3.data.player.expPool -gt $boot2.data.player.expPool) '经验礼物已发放'
}

# 仓库状态：放生宠已删除、受保护宠仍在
$storage2 = Api GET '/api/storage/pets'
$afterPets = @($storage2.data)
Assert (@($afterPets | Where-Object { $_.petId -eq $petId }).Count -eq 0) '放生后宠物从仓库移除'
Assert (@($afterPets | Where-Object { $_.petId -eq $starter.petId }).Count -eq 1) '受保护初始宠仍在仓库'
Assert (@($afterPets | Where-Object { $_.petId -eq $inTeamPet.petId }).Count -eq 1) '受保护在队宠仍在仓库'

Write-Host "`n== 结果 ==" -ForegroundColor Cyan
if ($script:failed) { Write-Host '存在失败项，请检查！' -ForegroundColor Red; exit 1 }
Write-Host '全部验收项通过' -ForegroundColor Green
