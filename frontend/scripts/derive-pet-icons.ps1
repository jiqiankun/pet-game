param(
  [string]$SourceDirectory = 'frontend/public/assets/pets/portraits',
  [string]$OutputDirectory = 'frontend/public/assets/pets/icons',
  [string]$MapSpriteDirectory = 'frontend/public/assets/sprites'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$sourcePath = (Resolve-Path -LiteralPath $SourceDirectory).Path
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
$mapSpritePath = [System.IO.Path]::GetFullPath($MapSpriteDirectory)
[System.IO.Directory]::CreateDirectory($outputPath) | Out-Null
[System.IO.Directory]::CreateDirectory($mapSpritePath) | Out-Null

foreach ($sourceFile in Get-ChildItem -LiteralPath $sourcePath -Filter 'pet_*_portrait.png' -File) {
  $image = [System.Drawing.Bitmap]::new($sourceFile.FullName)
  try {
    foreach ($size in 256, 128, 64) {
      $canvas = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
      $graphics = [System.Drawing.Graphics]::FromImage($canvas)
      try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $scale = [Math]::Min(($size - 8) / $image.Width, ($size - 8) / $image.Height)
        $width = [int][Math]::Round($image.Width * $scale)
        $height = [int][Math]::Round($image.Height * $scale)
        $x = [int](($size - $width) / 2)
        $y = [int](($size - $height) / 2)
        $graphics.DrawImage($image, $x, $y, $width, $height)
        $name = $sourceFile.BaseName -replace '_portrait$', "_icon_$size"
        $target = Join-Path $outputPath "$name.png"
        $canvas.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
        $check = [System.Drawing.Image]::FromFile($target)
        try {
          if ($check.Width -ne $size -or $check.Height -ne $size) { throw "派生尺寸校验失败：$target" }
        } finally { $check.Dispose() }
      } finally {
        $graphics.Dispose()
        $canvas.Dispose()
      }
    }
  } finally {
    $image.Dispose()
  }
}

# 地图行为精灵复用已验收的宠物图标，避免维护额外的泛化野怪源图。
$wildSprites = [ordered]@{
  wild_wander = 'PET_WOOD_002'
  wild_timid = 'PET_WATER_001'
  wild_aggressive = 'PET_FIRE_001'
  wild_rare = 'PET_LIGHT_003'
}
foreach ($entry in $wildSprites.GetEnumerator()) {
  $source = Join-Path $outputPath "pet_$($entry.Value)_icon_64.png"
  $image = [System.Drawing.Bitmap]::new($source)
  try {
    $canvas = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($canvas)
    try {
      $graphics.Clear([System.Drawing.Color]::Transparent)
      $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
      $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
      $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
      $graphics.DrawImage($image, 0, 0, 32, 32)
      $target = Join-Path $mapSpritePath "$($entry.Key).png"
      $canvas.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
      $check = [System.Drawing.Image]::FromFile($target)
      try {
        if ($check.Width -ne 32 -or $check.Height -ne 32) { throw "地图野怪派生尺寸校验失败：$target" }
      } finally { $check.Dispose() }
    } finally {
      $graphics.Dispose()
      $canvas.Dispose()
    }
  } finally {
    $image.Dispose()
  }
}

Get-ChildItem -LiteralPath $outputPath -Filter 'pet_*_icon_*.png' -File | Measure-Object | Select-Object -ExpandProperty Count
