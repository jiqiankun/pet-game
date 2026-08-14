param(
  [string]$SourceDirectory = 'docs/art/map-candidates',
  [string]$OutputDirectory = 'frontend/public/assets/maps/tilesets',
  [string]$PreviewPath = 'docs/art/tileset-preview-map.png'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$sourcePath = (Resolve-Path -LiteralPath $SourceDirectory).Path
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
[System.IO.Directory]::CreateDirectory($outputPath) | Out-Null
$names = 'grassland_base', 'waters', 'thunder', 'ruins'
$tilesets = @()

foreach ($name in $names) {
  $source = [System.Drawing.Bitmap]::new((Join-Path $sourcePath "tileset_${name}_source.png"))
  try {
    if ($source.Width % 2 -ne 0 -or $source.Height % 2 -ne 0) { throw "图集不是 2×2 方格：$name" }
    $halfWidth = [int]($source.Width / 2)
    $halfHeight = [int]($source.Height / 2)
    $tileset = [System.Drawing.Bitmap]::new(128, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($tileset)
    try {
      $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
      $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
      $rectangles = @(
        [System.Drawing.Rectangle]::new(0, 0, $halfWidth, $halfHeight),
        [System.Drawing.Rectangle]::new($halfWidth, 0, $halfWidth, $halfHeight),
        [System.Drawing.Rectangle]::new(0, $halfHeight, $halfWidth, $halfHeight),
        [System.Drawing.Rectangle]::new($halfWidth, $halfHeight, $halfWidth, $halfHeight)
      )
      for ($index = 0; $index -lt 4; $index++) {
        $graphics.DrawImage($source, [System.Drawing.Rectangle]::new($index * 32, 0, 32, 32), $rectangles[$index], [System.Drawing.GraphicsUnit]::Pixel)
      }
      $target = Join-Path $outputPath "tileset_${name}.png"
      $tileset.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
      $check = [System.Drawing.Image]::FromFile($target)
      try { if ($check.Width -ne 128 -or $check.Height -ne 32) { throw "Tileset 尺寸校验失败：$target" } } finally { $check.Dispose() }
      $tilesets += $tileset.Clone()
    } finally {
      $graphics.Dispose()
      $tileset.Dispose()
    }
  } finally { $source.Dispose() }
}

Copy-Item -LiteralPath (Join-Path $outputPath 'tileset_grassland_base.png') -Destination 'frontend/public/assets/maps/tileset.png' -Force

$preview = [System.Drawing.Bitmap]::new(384, 384, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$previewGraphics = [System.Drawing.Graphics]::FromImage($preview)
try {
  for ($row = 0; $row -lt $tilesets.Count; $row++) {
    for ($column = 0; $column -lt 4; $column++) {
      for ($y = 0; $y -lt 3; $y++) {
        for ($x = 0; $x -lt 3; $x++) {
          $previewGraphics.DrawImage($tilesets[$row], [System.Drawing.Rectangle]::new($column * 96 + $x * 32, $row * 96 + $y * 32, 32, 32), [System.Drawing.Rectangle]::new($column * 32, 0, 32, 32), [System.Drawing.GraphicsUnit]::Pixel)
        }
      }
    }
  }
  $previewOutput = [System.IO.Path]::GetFullPath($PreviewPath)
  [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($previewOutput)) | Out-Null
  $preview.Save($previewOutput, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
  $previewGraphics.Dispose()
  $preview.Dispose()
  foreach ($tileset in $tilesets) { $tileset.Dispose() }
}
