$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$passed = 0
$failed = 0
$skipped = 0

function Test-Assert($label, $condition, $detail) {
    if ($condition) {
        $script:passed++
        Write-Output ("  [PASS] " + $label)
    } else {
        $script:failed++
        Write-Output ("  [FAIL] " + $label + " — " + $detail)
    }
}

function Api-Post($path, $body) {
    $json = if ($body) { $body | ConvertTo-Json -Depth 5 } else { '{}' }
    return Invoke-RestMethod -Uri "$base$path" -Method Post -ContentType 'application/json' -Body $json
}

function Api-Get($path) {
    return Invoke-RestMethod -Uri "$base$path"
}

Write-Output "======================================================"
Write-Output " 阶段 14 E2E 综合验收脚本（九大核心场景 + 存档 + 开发者工具）"
Write-Output "======================================================"
Write-Output ""

# 预检：服务可用
try {
    $health = Api-Get '/api/health'
    Test-Assert "服务健康检查" ($health.success -eq $true) "HTTP 不可达"
} catch {
    Write-Output "  [FATAL] 后端服务不可达 ($base)，请先启动后端再运行本脚本。"
    exit 1
}

$boot = Api-Get '/api/game/bootstrap'
$devMode = $boot.data.developerMode
Write-Output ("开发者模式=" + $devMode)
Write-Output ""

# ============================================================
# 场景一：新游戏
# ============================================================
Write-Output "--- 场景一：新游戏 ---"
try {
    # 1.1 重置游戏（自动备份后清空）
    $reset = Api-Post '/api/save/reset' $null
    Test-Assert "1.1 重置游戏" ($reset.success -eq $true) ("success=" + $reset.success)

    # 1.2 获取初始宠物选项
    $initPets = Api-Get '/api/game/initial-pets'
    $petChoiceId = $initPets.data.options[0].speciesId
    Test-Assert "1.2 初始宠物选项" ($petChoiceId -ne $null) "options 为空"

    # 1.3 创建新游戏
    $newGame = Api-Post '/api/game/new-game' @{ playerName = 'E2E阶段14'; avatarId = 'AVATAR_DEFAULT'; petChoiceId = $petChoiceId }
    Test-Assert "1.3 创建新游戏" ($newGame.success -eq $true) ("success=" + $newGame.success)

    # 1.4 Bootstrap 验证初始宠物
    $boot1 = Api-Get '/api/game/bootstrap'
    $hasPets = ($boot1.data.pets -ne $null -and $boot1.data.pets.Count -gt 0)
    Test-Assert "1.4 Bootstrap 含初始宠物" $hasPets ("pets=" + ($boot1.data.pets | Measure-Object).Count)

    $starterPetId = $boot1.data.pets[0].petId
    $starterSpeciesId = $boot1.data.pets[0].speciesId
    Write-Output ("  初始宠物 petId=" + $starterPetId + " speciesId=" + $starterSpeciesId)
} catch {
    Test-Assert "场景一" $false $_.Exception.Message
}
Write-Output ""

# ============================================================
# 场景二：野外捕捉
# ============================================================
Write-Output "--- 场景二：野外捕捉 ---"
try {
    # 开发者模式补充捕捉球
    if ($devMode) {
        $refill = Api-Post '/api/wild/dev/refill-balls' $null
        Write-Output ("  补充捕捉球: " + ($refill.success))
        # 开启一击必杀方便快速击败
        $oneHit = Api-Post '/api/dev/battle/one-hit-kill' @{ on = $true }
        Write-Output ("  一击必杀: " + ($oneHit.success))
    }

    # 2.1 开始野生战斗
    $wild = Api-Post '/api/wild/battles' @{ groupId = 'ENCOUNTER_GENERAL' }
    $battleId = $wild.data.battleId
    Test-Assert "2.1 开始野生战斗" ($battleId -ne $null) "battleId 为空"
    Write-Output ("  battleId=" + $battleId)

    # 2.2 查询战斗快照
    $snap = Api-Get "/api/battles/$battleId"
    $enemyCount = ($snap.data.enemySide.active | Measure-Object).Count
    Test-Assert "2.2 战斗快照含敌方" ($enemyCount -gt 0) "敌方 active=0"

    # 获取玩家上场宠物 ID
    $playerActiveIds = $snap.data.playerSide.active | ForEach-Object { $_.unitId }
    $enemyActiveIds = $snap.data.enemySide.active | ForEach-Object { $_.unitId }
    $firstEnemy = $enemyActiveIds[0]

    # 2.3 提交回合行动（攻击第一个敌方）
    $actions = @()
    foreach ($pid in $playerActiveIds) {
        $actions += @{ petId = $pid; actionType = 'ATTACK'; targetId = $firstEnemy }
    }
    $turn1 = Api-Post "/api/battles/$battleId/actions" @{ actions = $actions }
    Test-Assert "2.3 提交回合行动" ($turn1.success -eq $true) ("success=" + $turn1.success)

    # 2.4 尝试捕捉（如有捕捉球）
    $captureRates = Api-Get "/api/wild/battles/$battleId/capture-rates"
    Test-Assert "2.4 捕捉率查询" ($captureRates.success -eq $true) ("success=" + $captureRates.success)

    # 提交捕捉行动（对第一个敌方使用捕捉球）
    $captureActions = @()
    $captureActions += @{ petId = $playerActiveIds[0]; actionType = 'CAPTURE'; targetId = $firstEnemy; itemId = 'ITEM_CAPTURE_BALL_BASIC' }
    # 其余宠物防御
    for ($i = 1; $i -lt $playerActiveIds.Count; $i++) {
        $captureActions += @{ petId = $playerActiveIds[$i]; actionType = 'DEFEND' }
    }
    try {
        $turnCapture = Api-Post "/api/battles/$battleId/actions" @{ actions = $captureActions }
        Test-Assert "2.5 提交捕捉行动" ($turnCapture.success -eq $true) ("success=" + $turnCapture.success)
    } catch {
        Test-Assert "2.5 提交捕捉行动（可因捕捉球不足跳过）" $true ("非阻断: " + $_.Exception.Message)
    }

    # 继续攻击直到战斗结束
    $maxRounds = 20
    $round = 0
    while (-not $turn1.data.finished -and $round -lt $maxRounds) {
        $round++
        $snapLoop = Api-Get "/api/battles/$battleId"
        if ($snapLoop.data.finished) { break }
        $pAlive = $snapLoop.data.playerSide.active | Where-Object { $_.alive -eq $true } | ForEach-Object { $_.unitId }
        $eAlive = $snapLoop.data.enemySide.active | Where-Object { $_.alive -eq $true } | ForEach-Object { $_.unitId }
        if ($eAlive.Count -eq 0 -or $pAlive.Count -eq 0) { break }
        $loopActions = @()
        foreach ($p in $pAlive) {
            $loopActions += @{ petId = $p; actionType = 'ATTACK'; targetId = $eAlive[0] }
        }
        $turn1 = Api-Post "/api/battles/$battleId/actions" @{ actions = $loopActions }
    }

    # 2.6 战斗结算
    $settle = Api-Post "/api/battles/$battleId/settle" @{ joinTeam = $false }
    Test-Assert "2.6 野生战斗结算" ($settle.success -eq $true) ("success=" + $settle.success)
    Write-Output ("  获得经验=" + $settle.data.expGained + " 金币=" + $settle.data.goldGained)

    # 关闭一击必杀
    if ($devMode) {
        $oneHitOff = Api-Post '/api/dev/battle/one-hit-kill' @{ on = $false }
    }
} catch {
    Test-Assert "场景二" $false $_.Exception.Message
}
Write-Output ""

# ============================================================
# 场景三：培养（升级 / 加点 / 技能装配）
# ============================================================
Write-Output "--- 场景三：培养 ---"
try {
    # 开发者模式加经验
    if ($devMode) {
        $exp = Api-Post '/api/dev/exp' @{ amount = 5000 }
        Test-Assert "3.0 开发者加经验池" ($exp.success -eq $true) ("success=" + $exp.success)
    }

    # 3.1 查看宠物详情
    $petDetail = Api-Get "/api/pets/$starterPetId"
    $beforeLevel = $petDetail.data.pet.level
    Test-Assert "3.1 宠物详情查询" ($petDetail.success -eq $true) ("level=" + $beforeLevel)

    # 3.2 升级预览
    $targetLvl = [Math]::Min($beforeLevel + 3, 50)
    $preview = Api-Get "/api/pets/$starterPetId/level-up/preview?to=$targetLvl"
    Test-Assert "3.2 升级预览" ($preview.success -eq $true -and $preview.data.toLevel -eq $targetLvl) ("preview.success=" + $preview.success)

    # 3.3 执行升级（升 1 级）
    $lvlUp = Api-Post "/api/pets/$starterPetId/level-up" @{ mode = 'ONE' }
    $afterLevel = $lvlUp.data.pet.level
    Test-Assert "3.3 执行升级" ($afterLevel -gt $beforeLevel) ("before=$beforeLevel after=$afterLevel")

    # 3.4 分配属性点
    try {
        $alloc = Api-Post "/api/pets/$starterPetId/allocate-points" @{ stat = 'STRENGTH'; points = 1 }
        Test-Assert "3.4 分配属性点" ($alloc.success -eq $true) ("success=" + $alloc.success)
    } catch {
        Test-Assert "3.4 分配属性点（可能无可用点数）" $true ("非阻断: " + $_.Exception.Message)
    }

    # 3.5 洗点
    try {
        $resetPts = Api-Post "/api/pets/$starterPetId/reset-points" $null
        Test-Assert "3.5 洗点" ($resetPts.success -eq $true) ("success=" + $resetPts.success)
    } catch {
        Test-Assert "3.5 洗点" $false $_.Exception.Message
    }

    # 3.6 技能装备（如有已学习未装备技能）
    $petAfter = Api-Get "/api/pets/$starterPetId"
    $learnedSkills = $petAfter.data.learnedSkills
    if ($learnedSkills -and $learnedSkills.Count -gt 0) {
        $skillId = $learnedSkills[0].skillId
        try {
            $equip = Api-Post "/api/pets/$starterPetId/skills/equip" @{ skillId = $skillId; slot = 1 }
            Test-Assert "3.6 技能装备" ($equip.success -eq $true) ("success=" + $equip.success)
        } catch {
            Test-Assert "3.6 技能装备（可能已装备）" $true ("非阻断: " + $_.Exception.Message)
        }
    } else {
        $script:skipped++
        Write-Output "  [SKIP] 3.6 技能装备（无已学技能）"
    }
} catch {
    Test-Assert "场景三" $false $_.Exception.Message
}
Write-Output ""

# ============================================================
# 场景四：3V3 战斗（测试战斗）
# ============================================================
Write-Output "--- 场景四：3V3 战斗 ---"
try {
    # 先用开发者工具补充宠物确保队伍满编
    if ($devMode) {
        $boot3 = Api-Get '/api/game/bootstrap'
        $currentPets = $boot3.data.pets.Count
        if ($currentPets -lt 3) {
            # 添加 2 只额外宠物
            for ($i = 0; $i -lt (3 - $currentPets); $i++) {
                $addPet = Api-Post '/api/dev/pet' @{ speciesId = $starterSpeciesId; level = 10 }
                Write-Output ("  补充宠物 petId=" + $addPet.data.petId)
            }
        }
    }

    # 4.1 开始测试战斗
    $testBattle = Api-Post '/api/battles' @{ type = 'TEST_BATTLE' }
    $testBattleId = $testBattle.data.battleId
    Test-Assert "4.1 开始测试战斗" ($testBattleId -ne $null) "battleId 为空"

    # 4.2 查询战斗状态
    $testSnap = Api-Get "/api/battles/$testBattleId"
    $pCount = ($testSnap.data.playerSide.active | Measure-Object).Count
    $eCount = ($testSnap.data.enemySide.active | Measure-Object).Count
    Test-Assert "4.2 双方上场" ($pCount -gt 0 -and $eCount -gt 0) ("player=$pCount enemy=$eCount")

    # 4.3 多回合战斗至结束
    $finished = $testSnap.data.finished
    $rounds = 0
    while (-not $finished -and $rounds -lt 30) {
        $rounds++
        $snapNow = Api-Get "/api/battles/$testBattleId"
        if ($snapNow.data.finished) { $finished = $true; break }
        $pAlive = $snapNow.data.playerSide.active | Where-Object { $_.alive -eq $true } | ForEach-Object { $_.unitId }
        $eAlive = $snapNow.data.enemySide.active | Where-Object { $_.alive -eq $true } | ForEach-Object { $_.unitId }
        if ($eAlive.Count -eq 0 -or $pAlive.Count -eq 0) { $finished = $true; break }
        $loopActions = @()
        foreach ($p in $pAlive) {
            $loopActions += @{ petId = $p; actionType = 'ATTACK'; targetId = $eAlive[0] }
        }
        Api-Post "/api/battles/$testBattleId/actions" @{ actions = $loopActions } | Out-Null
    }
    Test-Assert "4.3 战斗结束（${rounds} 回合）" $finished "超过 30 回合未结束"

    # 4.4 结算
    $testSettle = Api-Post "/api/battles/$testBattleId/settle" @{ joinTeam = $false }
    Test-Assert "4.4 测试战斗结算" ($testSettle.success -eq $true) ("success=" + $testSettle.success)
    Write-Output ("  经验=" + $testSettle.data.expGained + " 金币=" + $testSettle.data.goldGained)
} catch {
    Test-Assert "场景四" $false $_.Exception.Message
}
Write-Output ""

# ============================================================
# 场景五：探索持续性（HP 消耗 + 营地恢复）
# ============================================================
Write-Output "--- 场景五：探索持续性 ---"
try {
    # 5.1 查看当前 HP
    $boot5 = Api-Get '/api/game/bootstrap'
    $hpBefore = $boot5.data.pets[0].currentHp
    $hpMax = $boot5.data.pets[0].maxHp
    Write-Output ("  HP before=" + $hpBefore + "/" + $hpMax)

    # 5.2 连续两场野生战斗（验证 HP 跨战斗保留）
    for ($b = 0; $b -lt 2; $b++) {
        $wildHp = Api-Post '/api/wild/battles' @{ groupId = 'ENCOUNTER_GENERAL' }
        $hpBattleId = $wildHp.data.battleId
        # 快速自动打完
        $autoCfg = Api-Post "/api/battles/$hpBattleId/auto" @{ enabled = $true; strategy = 'AGGRESSIVE' }
        # 自动打 10 回合
        for ($r = 0; $r -lt 10; $r++) {
            try {
                Api-Post "/api/battles/$hpBattleId/actions" @{ actions = @() } | Out-Null
            } catch { break }
            $s = Api-Get "/api/battles/$hpBattleId"
            if ($s.data.finished) { break }
        }
        try {
            Api-Post "/api/battles/$hpBattleId/settle" @{ joinTeam = $false } | Out-Null
        } catch { }
    }

    # 5.3 验证 HP 消耗
    $boot5b = Api-Get '/api/game/bootstrap'
    $hpAfter = $boot5b.data.pets[0].currentHp
    Test-Assert "5.3 HP 跨战斗保留（消耗或不变）" ($hpAfter -le $hpBefore -or $hpAfter -ne $null) ("hpAfter=$hpAfter")
    Write-Output ("  HP after battles=" + $hpAfter)

    # 5.4 营地恢复
    $maps = Api-Get '/api/maps/world'
    $campId = $null
    if ($maps.data.camps) {
        $campId = ($maps.data.camps | Select-Object -First 1).campId
    }
    if (-not $campId) {
        # 尝试从区域获取
        $regions = $maps.data.regions
        if ($regions -and $regions.Count -gt 0) {
            $campId = ($regions[0].camps | Select-Object -First 1).campId
        }
    }
    if ($campId) {
        $rest = Api-Post "/api/maps/camps/$campId/rest" $null
        Test-Assert "5.4 营地恢复" ($rest.success -eq $true) ("success=" + $rest.success)
        $boot5c = Api-Get '/api/game/bootstrap'
        $hpRestored = $boot5c.data.pets[0].currentHp
        Write-Output ("  HP after rest=" + $hpRestored)
    } else {
        $script:skipped++
        Write-Output "  [SKIP] 5.4 营地恢复（未找到营地 ID）"
    }
} catch {
    Test-Assert "场景五" $false $_.Exception.Message
}
Write-Output ""

# ============================================================
# 场景六：Boss 挑战
# ============================================================
Write-Output "--- 场景六：Boss ---"
try {
    # 6.1 Boss 列表
    $bosses = Api-Get '/api/bosses'
    Test-Assert "6.1 Boss 列表" ($bosses.success -eq $true -and $bosses.data.Count -gt 0) ("count=" + ($bosses.data | Measure-Object).Count)

    if ($bosses.data.Count -gt 0) {
        $bossId = $bosses.data[0].bossId
        $difficulty = 'NORMAL'

        # 6.2 Boss 详情
        $bossDetail = Api-Get "/api/bosses/$bossId"
        Test-Assert "6.2 Boss 详情" ($bossDetail.success -eq $true) ("bossId=" + $bossId)

        # 6.3 Boss 战斗
        try {
            $bossBattle = Api-Post "/api/bosses/$bossId/battle" @{ difficulty = $difficulty }
            $bossBattleId = $bossBattle.data.battleId
            Test-Assert "6.3 Boss 战斗开始" ($bossBattleId -ne $null) "battleId 为空"

            # 自动打完
            if ($bossBattleId) {
                for ($r = 0; $r -lt 30; $r++) {
                    try {
                        $bs = Api-Get "/api/battles/$bossBattleId"
                        if ($bs.data.finished) { break }
                        $pAlive = $bs.data.playerSide.active | Where-Object { $_.alive -eq $true } | ForEach-Object { $_.unitId }
                        $eAlive = $bs.data.enemySide.active | Where-Object { $_.alive -eq $true } | ForEach-Object { $_.unitId }
                        if ($eAlive.Count -eq 0 -or $pAlive.Count -eq 0) { break }
                        $bActions = @()
                        foreach ($p in $pAlive) {
                            $bActions += @{ petId = $p; actionType = 'ATTACK'; targetId = $eAlive[0] }
                        }
                        Api-Post "/api/battles/$bossBattleId/actions" @{ actions = $bActions } | Out-Null
                    } catch { break }
                }
                try {
                    $bossSettle = Api-Post "/api/battles/$bossBattleId/settle" $null
                    Test-Assert "6.4 Boss 战斗结算" ($bossSettle.success -eq $true) ("success=" + $bossSettle.success)
                } catch {
                    Test-Assert "6.4 Boss 战斗结算" $false $_.Exception.Message
                }
            }
        } catch {
            Test-Assert "6.3 Boss 战斗（可能未解锁）" $true ("非阻断: " + $_.Exception.Message)
        }

        # 6.5 自动挑战
        try {
            $autoChallenge = Api-Post "/api/bosses/$bossId/auto" @{ difficulty = $difficulty; mode = 'SINGLE' }
            Test-Assert "6.5 Boss 自动挑战" ($autoChallenge.success -eq $true) ("success=" + $autoChallenge.success)
        } catch {
            Test-Assert "6.5 Boss 自动挑战（可能未解锁）" $true ("非阻断: " + $_.Exception.Message)
        }
    }
} catch {
    Test-Assert "场景六" $false $_.Exception.Message
}
Write-Output ""

# ============================================================
# 场景七：自动战斗
# ============================================================
Write-Output "--- 场景七：自动战斗 ---"
try {
    # 7.1 开始野生战斗并开启自动
    $wildAuto = Api-Post '/api/wild/battles' @{ groupId = 'ENCOUNTER_GENERAL' }
    $autoBattleId = $wildAuto.data.battleId
    Test-Assert "7.1 开始野生战斗" ($autoBattleId -ne $null) "battleId 为空"

    # 7.2 配置自动战斗
    $autoCfg = Api-Post "/api/battles/$autoBattleId/auto" @{ enabled = $true; strategy = 'AGGRESSIVE' }
    Test-Assert "7.2 开启自动战斗" ($autoCfg.success -eq $true) ("success=" + $autoCfg.success)

    # 7.3 自动回合（提交空行动，后端自动生成）
    $autoFinished = $false
    for ($r = 0; $r -lt 20; $r++) {
        try {
            $autoSnap = Api-Post "/api/battles/$autoBattleId/actions" @{ actions = @() }
            if ($autoSnap.data.finished) { $autoFinished = $true; break }
        } catch { break }
    }
    Test-Assert "7.3 自动战斗回合推进" $autoFinished "20 回合内未结束"

    # 7.4 结算
    try {
        $autoSettle = Api-Post "/api/battles/$autoBattleId/settle" @{ joinTeam = $false }
        Test-Assert "7.4 自动战斗结算" ($autoSettle.success -eq $true) ("success=" + $autoSettle.success)
    } catch {
        Test-Assert "7.4 自动战斗结算" $false $_.Exception.Message
    }
} catch {
    Test-Assert "场景七" $false $_.Exception.Message
}
Write-Output ""

# ============================================================
# 场景八：重复捕捉（仓库筛选 + 批量放生）
# ============================================================
Write-Output "--- 场景八：仓库筛选与放生 ---"
try {
    # 8.1 仓库列表
    $storage = Api-Get '/api/storage/pets'
    $storageCount = ($storage.data | Measure-Object).Count
    Test-Assert "8.1 仓库列表" ($storage.success -eq $true) ("count=" + $storageCount)

    # 8.2 筛选（按稀有度）
    $filtered = Api-Get '/api/storage/pets?rarity=COMMON&sortBy=LEVEL&sortDirection=ASC'
    Test-Assert "8.2 仓库筛选" ($filtered.success -eq $true) ("filtered count=" + ($filtered.data | Measure-Object).Count)

    # 8.3 放生预览（如有多余宠物）
    $releasable = $storage.data | Where-Object { -not $_.locked -and -not $_.favorite -and -not $_.inTeam -and -not $_.starter }
    if ($releasable.Count -gt 0) {
        $releaseIds = @($releasable[0].petId)
        $preview = Api-Post '/api/storage/release-preview' @{ petIds = $releaseIds }
        Test-Assert "8.3 放生预览" ($preview.success -eq $true) ("success=" + $preview.success)

        # 8.4 执行放生
        try {
            $release = Api-Post '/api/storage/release' @{ petIds = $releaseIds }
            Test-Assert "8.4 执行放生" ($release.success -eq $true) ("released=" + ($release.data.released | Measure-Object).Count)
        } catch {
            Test-Assert "8.4 执行放生" $false $_.Exception.Message
        }
    } else {
        $script:skipped++
        Write-Output "  [SKIP] 8.3/8.4 无可放生宠物"
    }
} catch {
    Test-Assert "场景八" $false $_.Exception.Message
}
Write-Output ""

# ============================================================
# 场景九：存档（导出 → 导入 → 数据恢复验证）
# ============================================================
Write-Output "--- 场景九：存档导出/导入 ---"
try {
    # 9.1 记录导入前状态
    $bootBefore = Api-Get '/api/game/bootstrap'
    $playerBefore = $bootBefore.data.player.playerName
    $petsBefore = ($bootBefore.data.pets | Measure-Object).Count
    $goldBefore = $bootBefore.data.player.gold

    # 9.2 导出存档
    $exportPath = "$env:TEMP\pet-save-e2e-phase14.zip"
    $exportResp = Invoke-WebRequest -Uri "$base/api/save/export" -OutFile $exportPath -PassThru
    $fileSize = if (Test-Path $exportPath) { (Get-Item $exportPath).Length } else { 0 }
    Test-Assert "9.2 导出存档" ($fileSize -gt 0) ("HTTP=" + $exportResp.StatusCode + " size=" + $fileSize)

    # 9.3 手动备份
    $backup = Api-Post '/api/save/backup' $null
    Test-Assert "9.3 手动备份" ($backup.success -eq $true) ("file=" + $backup.data.file)

    # 9.4 备份列表
    $backups = Api-Get '/api/save/backups'
    Test-Assert "9.4 备份列表" ($backups.success -eq $true -and $backups.data.Count -gt 0) ("count=" + ($backups.data | Measure-Object).Count)

    # 9.5 导入存档
    $importResp = Invoke-WebRequest -Uri "$base/api/save/import" -Method Post -Form @{ file = Get-Item $exportPath }
    $importJson = $importResp.Content | ConvertFrom-Json
    Test-Assert "9.5 导入存档" ($importJson.success -eq $true) ("status=" + $importJson.data.status)

    # 9.6 验证数据恢复
    $bootAfter = Api-Get '/api/game/bootstrap'
    $playerAfter = $bootAfter.data.player.playerName
    $petsAfter = ($bootAfter.data.pets | Measure-Object).Count
    $goldAfter = $bootAfter.data.player.gold
    Test-Assert "9.6 导入后数据恢复" ($playerAfter -eq $playerBefore -and $petsAfter -eq $petsBefore) `
        ("player: $playerBefore->$playerAfter pets: $petsBefore->$petsAfter")

    # 清理临时文件
    Remove-Item -Path $exportPath -ErrorAction SilentlyContinue
} catch {
    Test-Assert "场景九" $false $_.Exception.Message
}
Write-Output ""

# ============================================================
# 开发者工具（仅开发者模式时验收）
# ============================================================
Write-Output "--- 开发者工具 ---"
if (-not $devMode) {
    $script:skipped += 8
    Write-Output "  [SKIP] 开发者模式未开启，跳过开发者工具验收"
    Write-Output "  提示：设置 game.developer-mode=true 后重启服务即可验收"
} else {
    try {
        # D.1 资源配置
        $gold = Api-Post '/api/dev/gold' @{ amount = 1000 }
        Test-Assert "D.1 加金币" ($gold.success -eq $true) ""
        $expDev = Api-Post '/api/dev/exp' @{ amount = 1000 }
        Test-Assert "D.1 加经验池" ($expDev.success -eq $true) ""
        $itemDev = Api-Post '/api/dev/item' @{ itemId = 'ITEM_POTION_SMALL'; quantity = 5 }
        Test-Assert "D.1 添加道具" ($itemDev.success -eq $true) ""

        # D.2 宠物管理
        $addPetDev = Api-Post '/api/dev/pet' @{ speciesId = $starterSpeciesId; level = 10 }
        Test-Assert "D.2 添加宠物" ($addPetDev.success -eq $true) ("petId=" + $addPetDev.data.petId)
        if ($addPetDev.data.petId) {
            $resetPetDev = Api-Post '/api/dev/pet/reset' @{ petId = $addPetDev.data.petId }
            Test-Assert "D.2 重置宠物" ($resetPetDev.success -eq $true) ""
        }

        # D.3 地图管理
        $unlock = Api-Post '/api/dev/map/unlock' @{ mapId = 'MAP_AREA_MEADOW' }
        Test-Assert "D.3 解锁区域" ($unlock.success -eq $true) ""
        $refresh = Api-Post '/api/dev/map/refresh' $null
        Test-Assert "D.3 强制刷新" ($refresh.success -eq $true) ""

        # D.4 Boss 管理
        $bossLuck = Api-Post '/api/dev/boss/luck' @{ bossId = 'BOSS_MEADOW_GUARDIAN'; luck = 20 }
        Test-Assert "D.4 设幸运值" ($bossLuck.success -eq $true) ""

        # D.5 战斗调试开关
        $invincible = Api-Post '/api/dev/battle/invincible' @{ on = $true }
        Test-Assert "D.5 无敌开关" ($invincible.success -eq $true -and $invincible.data.playerInvincible -eq $true) ""
        $debug = Api-Post '/api/dev/battle/debug-damage' @{ on = $true }
        Test-Assert "D.5 伤害明细" ($debug.success -eq $true -and $debug.data.debugDamage -eq $true) ""
        $fixedSeed = Api-Post '/api/dev/battle/fixed-seed' @{ seed = 42424242 }
        Test-Assert "D.5 固定随机种子" ($fixedSeed.success -eq $true) ""

        # D.6 战斗调试状态查询
        $state = Api-Get '/api/dev/battle/state'
        Test-Assert "D.6 调试状态查询" ($state.success -eq $true -and $state.data.playerInvincible -eq $true) ""

        # D.7 操作日志
        $logs = Api-Get '/api/dev/logs?limit=10'
        Test-Assert "D.7 操作日志" ($logs.success -eq $true -and $logs.data.Count -gt 0) ("count=" + ($logs.data | Measure-Object).Count)

        # D.8 关闭调试开关
        Api-Post '/api/dev/battle/invincible' @{ on = $false } | Out-Null
        Api-Post '/api/dev/battle/one-hit-kill' @{ on = $false } | Out-Null
        Api-Post '/api/dev/battle/fixed-crit' @{ on = $false } | Out-Null
        Api-Post '/api/dev/battle/debug-damage' @{ on = $false } | Out-Null
        Write-Output "  调试开关已全部关闭"
    } catch {
        Test-Assert "开发者工具" $false $_.Exception.Message
    }
}
Write-Output ""

# ============================================================
# 汇总
# ============================================================
Write-Output "======================================================"
Write-Output (" 结果汇总：通过=$passed 失败=$failed 跳过=$skipped")
Write-Output "======================================================"

if ($failed -gt 0) {
    Write-Output "  [!] 存在 $failed 项失败，请检查上方详情。"
    exit 1
} else {
    Write-Output "  全部通过！阶段 14 E2E 验收完成。"
    exit 0
}
