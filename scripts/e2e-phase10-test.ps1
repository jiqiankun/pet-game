$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

Write-Output "=============================="
Write-Output "阶段 10 效率/经济/随机系统 E2E 验收脚本"
Write-Output "=============================="

# 1. 新游戏（已有存档时复用现有存档）
$status = Invoke-RestMethod -Uri "$base/api/game/save-status"
if ($status.data.hasSave) {
    Write-Output "1. 已存在存档，复用现有存档继续验收"
} else {
    $new = Invoke-RestMethod -Uri "$base/api/game/new-game" -Method Post -ContentType 'application/json' -Body (@{playerName='阶段10测试'; avatarId='AVATAR_DEFAULT'; petChoiceId='PET_FIRE_001'} | ConvertTo-Json)
    Write-Output ("1. 新游戏 success=" + $new.success)
}

# 2. 商店列表
$shop = Invoke-RestMethod -Uri "$base/api/shop"
Write-Output ("2. 商店列表 success=" + $shop.success + " 商品数=" + $shop.data.items.Count + " 金币=" + $shop.data.gold)
foreach ($item in $shop.data.items | Select-Object -First 5) {
    Write-Output ("   " + $item.itemId + " " + $item.name + " 价格=" + $item.price + " 解锁=" + $item.unlocked)
}

# 3. 购买商品（选择第一个已解锁商品）
$buyTarget = $shop.data.items | Where-Object { $_.unlocked } | Select-Object -First 1
if ($buyTarget) {
    $buy = Invoke-RestMethod -Uri "$base/api/shop/buy" -Method Post -ContentType 'application/json' -Body (@{itemId=$buyTarget.itemId; quantity=1} | ConvertTo-Json)
    Write-Output ("3. 购买 " + $buyTarget.itemId + " success=" + $buy.success + " 花费=" + $buy.data.totalCost + " 剩余金币=" + $buy.data.remainingGold)
} else {
    Write-Output "3. 无已解锁商品可购买"
}

# 4. 宠物详情（含技能书信息）
$boot = Invoke-RestMethod -Uri "$base/api/game/bootstrap"
$petId = $boot.data.pets[0].id
$detail = Invoke-RestMethod -Uri "$base/api/pets/$petId"
Write-Output ("4. 宠物详情 success=" + $detail.success + " 技能书学习数=" + $detail.data.bookSkillLearnCount + "/10")

# 5. 技能书学习（背包中如有技能书道具）
$inv = Invoke-RestMethod -Uri "$base/api/inventory"
$bookItem = $inv.data.items | Where-Object { $_.itemType -eq 'SKILL_BOOK' -and $_.quantity -gt 0 } | Select-Object -First 1
if ($bookItem) {
    $learn = Invoke-RestMethod -Uri "$base/api/pets/$petId/learn-skill-book" -Method Post -ContentType 'application/json' -Body (@{itemId=$bookItem.itemId} | ConvertTo-Json)
    Write-Output ("5. 学习技能书 " + $bookItem.itemId + " success=" + $learn.success)
    $detail2 = Invoke-RestMethod -Uri "$base/api/pets/$petId"
    foreach ($bs in $detail2.data.learnedBookSkills) {
        Write-Output ("   已学: " + $bs.name)
        # 6. 装备到书槽 5
        $equip = Invoke-RestMethod -Uri "$base/api/pets/$petId/equip-book-skill" -Method Post -ContentType 'application/json' -Body (@{skillId=$bs.skillId; bookSlot=5} | ConvertTo-Json)
        Write-Output ("6. 装备书槽5 success=" + $equip.success)
        # 7. 卸下书槽 5
        $unequip = Invoke-RestMethod -Uri "$base/api/pets/$petId/unequip-book-skill" -Method Post -ContentType 'application/json' -Body (@{bookSlot=5} | ConvertTo-Json)
        Write-Output ("7. 卸下书槽5 success=" + $unequip.success)
        # 8. 遗忘
        $forget = Invoke-RestMethod -Uri "$base/api/pets/$petId/forget-book-skill" -Method Post -ContentType 'application/json' -Body (@{skillId=$bs.skillId} | ConvertTo-Json)
        Write-Output ("8. 遗忘技能 success=" + $forget.success)
        break
    }
} else {
    Write-Output "5. 背包无技能书道具，跳过学习/装备/遗忘流程"
}

# 9. 推荐 Build
$builds = Invoke-RestMethod -Uri "$base/api/pets/$petId/build-recommendations"
Write-Output ("9. 推荐Build success=" + $builds.success + " 方案数=" + $builds.data.Count)
foreach ($b in $builds.data) {
    Write-Output ("   " + $b.name + "：" + $b.description)
}

# 10. 进入草原区域
$enter = Invoke-RestMethod -Uri "$base/api/maps/MAP_AREA_MEADOW/enter" -Method Post -ContentType 'application/json' -Body '{}'
Write-Output ("10. 进入青草原 success=" + $enter.success)

# 11. 随机事件尝试（多次 roll，15% 概率）
$eventTriggered = $false
for ($i = 0; $i -lt 20; $i++) {
    $roll = Invoke-RestMethod -Uri "$base/api/maps/events/roll"
    if ($roll.success -and $roll.data) {
        $eventTriggered = $true
        Write-Output ("11. 随机事件触发: " + $roll.data.name)
        $opt = $roll.data.options[0]
        $resolve = Invoke-RestMethod -Uri "$base/api/maps/events/resolve" -Method Post -ContentType 'application/json' -Body (@{eventId=$roll.data.eventId; optionId=$opt.optionId} | ConvertTo-Json)
        Write-Output ("    选项[" + $opt.text + "] 结果: " + $resolve.data.type + " - " + $resolve.data.description)
        break
    }
}
if (-not $eventTriggered) {
    Write-Output "11. 20 次尝试未触发随机事件（15% 概率，属正常情况）"
}

# 12. 野生遭遇（含精英个体判定，精英由后端按 5% 概率生成）
$wild = Invoke-RestMethod -Uri "$base/api/maps/encounters" -Method Post -ContentType 'application/json' -Body (@{groupId='ENCOUNTER_MEADOW'} | ConvertTo-Json)
Write-Output ("12. 野生遭遇 success=" + $wild.success)
if ($wild.data.enemyUnits) {
    foreach ($u in $wild.data.enemyUnits) {
        Write-Output ("    敌方: " + $u.name + " Lv." + $u.level + " 精英=" + $u.elite)
    }
}

Write-Output ""
Write-Output "=============================="
Write-Output "E2E 测试完成"
Write-Output "=============================="
