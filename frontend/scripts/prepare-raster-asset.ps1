param(
  [Parameter(Mandatory)]
  [string]$InputPath,
  [Parameter(Mandatory)]
  [string]$OutputPath,
  [Parameter(Mandatory)]
  [ValidateRange(64, 4096)]
  [int]$Width,
  [Parameter(Mandatory)]
  [ValidateRange(64, 4096)]
  [int]$Height
)

$ErrorActionPreference = 'Stop'

$resolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
$resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $resolvedOutput) | Out-Null
if (Test-Path -LiteralPath $resolvedOutput) { throw "目标已存在，拒绝覆盖：$resolvedOutput" }

if (-not ('PetGameArt.RasterAsset' -as [type])) {
  $drawingAssemblies = @(
    'System.Drawing.Common.dll',
    'System.Drawing.Primitives.dll',
    'System.Private.Windows.Core.dll',
    'System.Private.Windows.GdiPlus.dll'
  ) | ForEach-Object { Join-Path $PSHOME $_ }
  Add-Type -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;

namespace PetGameArt {
  public static class RasterAsset {
    public static void Prepare(string inputPath, string outputPath, int width, int height) {
      using var input = new Bitmap(inputPath);
      using var output = new Bitmap(width, height, PixelFormat.Format32bppArgb);
      using (Graphics graphics = Graphics.FromImage(output)) {
        graphics.Clear(Color.FromArgb(0, 0, 0, 0));
        graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
        graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
        graphics.CompositingQuality = CompositingQuality.HighQuality;
        graphics.SmoothingMode = SmoothingMode.HighQuality;
        double scale = Math.Max(width / (double)input.Width, height / (double)input.Height);
        int drawWidth = Math.Max(1, (int)Math.Ceiling(input.Width * scale));
        int drawHeight = Math.Max(1, (int)Math.Ceiling(input.Height * scale));
        graphics.DrawImage(input, new Rectangle((width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight));
      }
      output.Save(outputPath, ImageFormat.Png);
      if (output.Width != width || output.Height != height) throw new InvalidOperationException("输出尺寸不正确。");
    }
  }
}
'@ -ReferencedAssemblies $drawingAssemblies
}

[PetGameArt.RasterAsset]::Prepare($resolvedInput, $resolvedOutput, $Width, $Height)
[pscustomobject]@{ Input = $resolvedInput; Output = $resolvedOutput; Size = "$Width×$Height" }
