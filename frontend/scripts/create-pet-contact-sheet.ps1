param(
  [string]$SourceDirectory = 'frontend/public/assets/pets/portraits',
  [string]$OutputPath = 'docs/art/pets-contact-sheet.png'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$files = @(Get-ChildItem -LiteralPath (Resolve-Path -LiteralPath $SourceDirectory) -Filter 'pet_*_portrait.png' -File | Sort-Object Name)
if ($files.Count -ne 27) { throw "宠物立绘数量应为 27，实际为 $($files.Count)" }

$columns = 9
$cell = 144
$sheet = [System.Drawing.Bitmap]::new($columns * $cell, [int][Math]::Ceiling($files.Count / $columns) * $cell, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($sheet)
try {
  $graphics.Clear([System.Drawing.Color]::FromArgb(255, 24, 32, 48))
  $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $font = [System.Drawing.Font]::new('Arial', 8)
  $brush = [System.Drawing.Brushes]::White
  try {
    for ($index = 0; $index -lt $files.Count; $index++) {
      $source = [System.Drawing.Image]::FromFile($files[$index].FullName)
      try {
        $x = ($index % $columns) * $cell
        $y = [int][Math]::Floor($index / $columns) * $cell
        $graphics.DrawImage($source, $x + 8, $y + 4, 128, 112)
        $label = $files[$index].BaseName -replace '^pet_', '' -replace '_portrait$', ''
        $graphics.DrawString($label, $font, $brush, $x + 4, $y + 122)
      } finally { $source.Dispose() }
    }
  } finally { $font.Dispose() }
  $output = [System.IO.Path]::GetFullPath($OutputPath)
  [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($output)) | Out-Null
  $sheet.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
  $graphics.Dispose()
  $sheet.Dispose()
}
