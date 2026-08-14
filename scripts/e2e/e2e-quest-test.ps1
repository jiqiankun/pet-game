$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

Write-Output "=============================="
Write-Output "阶段 9 任务系统 E2E 验收脚本"
Write-Output "=============================="

# 1. 新游戏
$new = Invoke-RestMethod -Uri "$base/api/game/new-game" -Method Post -ContentType 'application/json' -Body (@{playerName='任务测试'; avatarId='AVATAR_DEFAULT'; petChoiceId='PET_FIRE_001'} | ConvertTo-Json)
Write-Output ("1. 新游戏 success=" + $new.success)

# 2. 查询任务列表
$quests = Invoke-RestMethod -Uri "$base/api/quests"
Write-Output ("2. 任务列表 success=" + $quests.success)
Write-Output ("   主线: " + $quests.data.mainQuests.Count + " 支线: " + $quests.data.sideQuests.Count + " 隐藏: " + $quests.data.hiddenQuests.Count)
foreach ($q in $quests.data.mainQuests) {
    Write-Output ("   [主线] " + $q.questId + " - " + $q.name + " (" + $q.status + ")")
}

# 3. 查询主线摘要
$summary = Invoke-RestMethod -Uri "$base/api/quests/active-summary"
Write-Output ("3. 主线摘要 success=" + $summary.success)
if ($summary.data) {
    Write-Output ("   当前: " + $summary.data.name + " - " + $summary.data.description)
}

# 4. 接受第一个可用主线任务
$firstAvailable = $quests.data.mainQuests | Where-Object { $_.status -eq 'AVAILABLE' } | Select-Object -First 1
if ($firstAvailable) {
    $accept = Invoke-RestMethod -Uri "$base/api/quests/$($firstAvailable.questId)/accept" -Method Post
    Write-Output ("4. 接受任务 " + $firstAvailable.questId + " success=" + $accept.success)
} else {
    Write-Output "4. 没有可接受的主线任务"
}

# 5. 查询任务详情
if ($firstAvailable) {
    $detail = Invoke-RestMethod -Uri "$base/api/quests/$($firstAvailable.questId)"
    Write-Output ("5. 任务详情 success=" + $detail.success + " name=" + $detail.data.name)
    Write-Output ("   目标数: " + $detail.data.objectives.Count)
    foreach ($obj in $detail.data.objectives) {
        Write-Output ("   - " + $obj.description + " [" + $obj.progress + "/" + $obj.targetCount + "] " + $(if($obj.completed){"已完成"}else{"进行中"}))
    }
}

# 6. NPC 对话
$npcId = 'NPC_VILLAGE_1'
$talk = Invoke-RestMethod -Uri "$base/api/npcs/$npcId/talk" -Method Post
Write-Output ("6. NPC 对话 success=" + $talk.success)
if ($talk.data) {
    Write-Output ("   NPC: " + $talk.data.npcName + " 文本: " + $talk.data.text + " hasMore=" + $talk.data.hasMore)
    if ($talk.data.hasMore) {
        $talk2 = Invoke-RestMethod -Uri "$base/api/npcs/$npcId/talk" -Method Post
        Write-Output ("   继续对话: " + $talk2.data.text + " hasMore=" + $talk2.data.hasMore)
    }
}

# 7. 教学状态
$tut = Invoke-RestMethod -Uri "$base/api/tutorial"
Write-Output ("7. 教学状态 success=" + $tut.success)
if ($tut.data) {
    Write-Output ("   进度: " + $tut.data.completedCount + "/" + $tut.data.totalCount + " allCompleted=" + $tut.data.allCompleted)
    foreach ($step in $tut.data.steps) {
        Write-Output ("   [" + $step.order + "] " + $step.name + " completed=" + $step.completed + " skippable=" + $step.skippable)
    }
}

# 8. 完成教学步骤
$tutStep = $tut.data.steps | Where-Object { -not $_.completed -and -not $_.skipped } | Select-Object -First 1
if ($tutStep) {
    $completeTut = Invoke-RestMethod -Uri "$base/api/tutorial/$($tutStep.stepId)/complete" -Method Post
    Write-Output ("8. 完成教学 " + $tutStep.stepId + " success=" + $completeTut.success)
}

# 9. 地图变更列表
$changes = Invoke-RestMethod -Uri "$base/api/map-changes"
Write-Output ("9. 地图变更 success=" + $changes.success + " count=" + $(if($changes.data){$changes.data.Count}else{0}))

# 10. 跳过教学
$skipTut = Invoke-RestMethod -Uri "$base/api/tutorial/skip" -Method Post
Write-Output ("10. 跳过教学 success=" + $skipTut.success)

Write-Output ""
Write-Output "=============================="
Write-Output "E2E 测试完成"
Write-Output "=============================="
