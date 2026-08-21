param(
    [string]$MavenHome = $env:MAVEN_HOME,
    [string]$MavenRepository,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$PackageManager,
    [string]$NodePath,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$frontendDir = Join-Path $projectRoot 'frontend'
$backendDir = Join-Path $projectRoot 'backend'
$requirementFile = Join-Path $projectRoot 'docs\requirements\宠物精灵_桌面版世界与UI重构_完整需求文档_V1.0.md'

function Get-PatternCount {
    param(
        [Parameter(Mandatory)] [string]$Path,
        [Parameter(Mandatory)] [string]$Pattern
    )

    return @(Select-String -LiteralPath $Path -Pattern $Pattern).Count
}

if (-not $SkipBuild) {
    $packageManagerCommand = $PackageManager
    if (-not $packageManagerCommand) {
        $resolvedPackageManager = Get-Command npm.cmd -ErrorAction SilentlyContinue
        if (-not $resolvedPackageManager) {
            $resolvedPackageManager = Get-Command pnpm.cmd -ErrorAction SilentlyContinue
        }
        if ($resolvedPackageManager) {
            $packageManagerCommand = $resolvedPackageManager.Source
        }
    }
    if (-not $packageManagerCommand) {
        $resolvedNode = Get-Command node.exe -ErrorAction SilentlyContinue
        if ($resolvedNode) {
            $NodePath = $resolvedNode.Source
        }
    }
    if ($packageManagerCommand -and -not (Test-Path -LiteralPath $packageManagerCommand)) {
        throw '指定的包管理器不存在。'
    }
    if (-not $packageManagerCommand -and (-not $NodePath -or -not (Test-Path -LiteralPath $NodePath))) {
        throw '未找到 npm.cmd 或 node.exe，请通过 -PackageManager 或 -NodePath 指定完整路径。'
    }

    Push-Location $frontendDir
    try {
        if ($packageManagerCommand) {
            & $packageManagerCommand run build
            if ($LASTEXITCODE -ne 0) {
                throw "前端构建失败，退出码：$LASTEXITCODE"
            }
        } else {
            & $NodePath 'node_modules/vue-tsc/bin/vue-tsc.js' -b
            if ($LASTEXITCODE -ne 0) {
                throw "前端类型检查失败，退出码：$LASTEXITCODE"
            }
            & $NodePath 'node_modules/vite/bin/vite.js' build
            if ($LASTEXITCODE -ne 0) {
                throw "前端构建失败，退出码：$LASTEXITCODE"
            }
        }
    } finally {
        Pop-Location
    }

    $mavenCommand = $null
    if ($MavenHome) {
        $candidate = Join-Path $MavenHome 'bin\mvn.cmd'
        if (Test-Path -LiteralPath $candidate) {
            $mavenCommand = $candidate
        }
    }
    if (-not $mavenCommand) {
        $resolvedMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        if ($resolvedMaven) {
            $mavenCommand = $resolvedMaven.Source
        }
    }
    if (-not $mavenCommand) {
        throw '未找到 mvn.cmd，请设置 MAVEN_HOME 或通过 -MavenHome 指定 Maven 目录。'
    }
    if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
        throw '未找到 Java，请设置 JAVA_HOME 或通过 -JavaHome 指定 Java 21 目录。'
    }
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path

    Push-Location $backendDir
    try {
        $mavenArguments = @('test')
        if ($MavenRepository) {
            $resolvedRepository = (Resolve-Path -LiteralPath $MavenRepository).Path
            $mavenArguments = @("-Dmaven.repo.local=$resolvedRepository") + $mavenArguments
        }
        & $mavenCommand $mavenArguments
        if ($LASTEXITCODE -ne 0) {
            throw "后端测试失败，退出码：$LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

$reports = @(Get-ChildItem -File -LiteralPath (Join-Path $backendDir 'target\surefire-reports') -Filter 'TEST-*.xml')
if ($reports.Count -eq 0) {
    throw '未找到 Maven Surefire 测试报告，请先执行完整基线构建。'
}
$testTotal = 0
$failureTotal = 0
$errorTotal = 0
$skippedTotal = 0
foreach ($report in $reports) {
    [xml]$suite = Get-Content -Raw -LiteralPath $report.FullName
    $testTotal += [int]$suite.testsuite.tests
    $failureTotal += [int]$suite.testsuite.failures
    $errorTotal += [int]$suite.testsuite.errors
    $skippedTotal += [int]$suite.testsuite.skipped
}

$requirementText = Get-Content -Raw -LiteralPath $requirementFile
$requirementIds = [regex]::Matches($requirementText, '\*\*R-(\d{3})') |
    ForEach-Object { [int]$_.Groups[1].Value } |
    Sort-Object -Unique
$expectedIds = 1..205
$missingIds = @($expectedIds | Where-Object { $_ -notin $requirementIds })
$unexpectedIds = @($requirementIds | Where-Object { $_ -notin $expectedIds })
if ($missingIds.Count -gt 0 -or $unexpectedIds.Count -gt 0) {
    throw "需求编号不连续，缺失：$($missingIds -join ', ')；超出范围：$($unexpectedIds -join ', ')"
}

$configRoot = Join-Path $backendDir 'src\main\resources\game-config'
$petsFile = Join-Path $configRoot 'pets\pets.yml'
$skillsFile = Join-Path $configRoot 'skills\skills.yml'
$itemsFile = Join-Path $configRoot 'items\items.yml'
$bossesFile = Join-Path $configRoot 'bosses\bosses.yml'
$questsFile = Join-Path $configRoot 'quests\quests.yml'
$achievementsFile = Join-Path $configRoot 'achievements\achievements.yml'
$mapsDir = Join-Path $frontendDir 'public\assets\maps'
$assetsDir = Join-Path $frontendDir 'public\assets'
$worldRootChunk = Get-ChildItem -File -LiteralPath (Join-Path $frontendDir 'dist\assets') -Filter 'WorldRoot-*.js' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $worldRootChunk) {
    $worldRootChunk = Get-ChildItem -File -LiteralPath (Join-Path $frontendDir 'dist\assets') -Filter 'ExploreView-*.js' |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

Write-Output '======================================================'
Write-Output ' 宠物精灵桌面版世界/UI 重构阶段 0 基线'
Write-Output '======================================================'
Write-Output ("需求编号：R-001～R-205，连续性校验通过（{0} 项）" -f $requirementIds.Count)
Write-Output ("后端测试：{0}，失败={1}，错误={2}，跳过={3}" -f $testTotal, $failureTotal, $errorTotal, $skippedTotal)
Write-Output ("宠物：{0}" -f (Get-PatternCount -Path $petsFile -Pattern '^  - id: PET_'))
Write-Output ("主动技能：{0}" -f (Get-PatternCount -Path $skillsFile -Pattern '^  - id: SKILL_'))
Write-Output ("被动技能：{0}" -f (Get-PatternCount -Path $skillsFile -Pattern '^  - id: PASSIVE_'))
Write-Output ("道具：{0}" -f (Get-PatternCount -Path $itemsFile -Pattern '^  - id: ITEM_'))
Write-Output ("Boss：{0}" -f (Get-PatternCount -Path $bossesFile -Pattern '^  - id: BOSS_'))
Write-Output ("任务：{0}" -f (Get-PatternCount -Path $questsFile -Pattern '^  - id: QUEST_'))
Write-Output ("成就：{0}" -f (Get-PatternCount -Path $achievementsFile -Pattern '^  - id: ACH_'))
Write-Output ("Tiled 地图 JSON：{0}" -f @(Get-ChildItem -File -LiteralPath $mapsDir -Filter '*.json').Count)
Write-Output ("前端 PNG 资源：{0}" -f @(Get-ChildItem -Recurse -File -LiteralPath $assetsDir -Filter '*.png').Count)
if ($worldRootChunk) {
    Write-Output ("世界根构建块：{0} bytes（{1}）" -f $worldRootChunk.Length, $worldRootChunk.Name)
}
Write-Output '======================================================'

if ($failureTotal -gt 0 -or $errorTotal -gt 0 -or $skippedTotal -gt 0) {
    throw "后端测试报告未达到零失败/零错误/零跳过：失败=$failureTotal，错误=$errorTotal，跳过=$skippedTotal"
}
