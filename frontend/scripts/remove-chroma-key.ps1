param(
  [Parameter(Mandatory)]
  [string]$InputPath,
  [Parameter(Mandatory)]
  [string]$OutputPath,
  [ValidateRange(16, 4096)]
  [int]$Size = 1024,
  [string]$KeyColor = '#00FF00',
  [switch]$UseInputAlpha,
  [switch]$CropToSubject,
  [ValidateRange(0, 45)]
  [int]$CanvasPaddingPercent = 0
)

$ErrorActionPreference = 'Stop'

$resolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
$resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
$outputDirectory = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$hex = $KeyColor.TrimStart('#')
if ($hex -notmatch '^[0-9A-Fa-f]{6}$') {
  throw 'KeyColor 必须是 6 位十六进制颜色，例如 #00FF00。'
}

if (-not ('PetGameArt.ChromaKey' -as [type])) {
  $drawingAssemblies = @(
    'System.Drawing.Common.dll',
    'System.Drawing.Primitives.dll',
    'System.Private.Windows.Core.dll',
    'System.Private.Windows.GdiPlus.dll'
  ) | ForEach-Object { Join-Path $PSHOME $_ }
  Add-Type -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Drawing.Drawing2D;

namespace PetGameArt {
  public static class ChromaKey {
    private const double TransparentDistance = 64d;
    private const double OpaqueDistance = 192d;

    public static string Convert(string inputPath, string outputPath, int size, int kr, int kg, int kb,
      bool useInputAlpha, bool cropToSubject, int canvasPaddingPercent) {
      using var input = new Bitmap(inputPath);
      if (!useInputAlpha) ValidateBorder(input, kr, kg, kb);
      using var matte = new Bitmap(input.Width, input.Height, PixelFormat.Format32bppArgb);
      int minX = input.Width, minY = input.Height, maxX = -1, maxY = -1;

      for (int y = 0; y < input.Height; y++) {
        for (int x = 0; x < input.Width; x++) {
          Color source = input.GetPixel(x, y);
          double matteAlpha = useInputAlpha
            ? source.A / 255d
            : Math.Max(0d, Math.Min(1d,
                (Math.Sqrt((source.R - kr) * (source.R - kr)
                  + (source.G - kg) * (source.G - kg) + (source.B - kb) * (source.B - kb))
                  - TransparentDistance) / (OpaqueDistance - TransparentDistance)));
          int alpha = useInputAlpha ? source.A : (int)Math.Round(source.A * matteAlpha);
          if (alpha == 0) {
            matte.SetPixel(x, y, Color.FromArgb(0, 0, 0, 0));
            continue;
          }
          int r = source.R, g = source.G, b = source.B;
          if (!useInputAlpha) {
            // 反推前景颜色，避免绿幕颜色在抗锯齿边缘残留。
            r = Clamp((source.R - (1d - matteAlpha) * kr) / matteAlpha);
            g = Clamp((source.G - (1d - matteAlpha) * kg) / matteAlpha);
            b = Clamp((source.B - (1d - matteAlpha) * kb) / matteAlpha);
            if (kg > kr && kg > kb && g > Math.Max(r, b) + 8) g = Math.Max(r, b) + 8;
          }
          matte.SetPixel(x, y, Color.FromArgb(alpha, r, g, b));
          if (alpha >= 16) {
            minX = Math.Min(minX, x); minY = Math.Min(minY, y);
            maxX = Math.Max(maxX, x); maxY = Math.Max(maxY, y);
          }
        }
      }
      if (maxX < 0) throw new InvalidOperationException("抠图后没有检测到主体。");
      // 绿幕贴边通常表示主体不完整；原生 Alpha 图会保留完整画布并缩小留边，可安全处理。
      if (!useInputAlpha && (minX == 0 || minY == 0 || maxX == input.Width - 1 || maxY == input.Height - 1)) {
        throw new InvalidOperationException("主体贴边，无法保证完整裁切。");
      }

      Rectangle sourceRect = new Rectangle(0, 0, matte.Width, matte.Height);
      if (cropToSubject) {
        int padding = Math.Max(1, (int)Math.Ceiling(Math.Max(maxX - minX + 1, maxY - minY + 1) * 0.08d));
        int left = Math.Max(0, minX - padding);
        int top = Math.Max(0, minY - padding);
        int right = Math.Min(matte.Width, maxX + padding + 1);
        int bottom = Math.Min(matte.Height, maxY + padding + 1);
        sourceRect = Rectangle.FromLTRB(left, top, right, bottom);
      }

      using var output = new Bitmap(size, size, PixelFormat.Format32bppArgb);
      using (Graphics graphics = Graphics.FromImage(output)) {
        graphics.Clear(Color.FromArgb(0, 0, 0, 0));
        graphics.CompositingMode = CompositingMode.SourceCopy;
        graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
        graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
        graphics.CompositingQuality = CompositingQuality.HighQuality;
        double availableSize = size * (1d - canvasPaddingPercent / 50d);
        double scale = Math.Min(availableSize / sourceRect.Width, availableSize / sourceRect.Height);
        int width = (int)Math.Round(sourceRect.Width * scale);
        int height = (int)Math.Round(sourceRect.Height * scale);
        graphics.DrawImage(matte, new Rectangle((size - width) / 2, (size - height) / 2, width, height),
          sourceRect.X, sourceRect.Y, sourceRect.Width, sourceRect.Height, GraphicsUnit.Pixel);
      }
      for (int y = 0; y < size; y++) {
        for (int x = 0; x < size; x++) {
          Color pixel = output.GetPixel(x, y);
          if (pixel.A <= 12) output.SetPixel(x, y, Color.FromArgb(0, 0, 0, 0));
        }
      }
      output.Save(outputPath, ImageFormat.Png);
      return $"主体边界：{minX},{minY} - {maxX},{maxY}；裁切窗口：{sourceRect.X},{sourceRect.Y},{sourceRect.Width},{sourceRect.Height}";
    }

    private static void ValidateBorder(Bitmap image, int kr, int kg, int kb) {
      int samples = 0, matches = 0;
      for (int x = 0; x < image.Width; x++) {
        matches += IsKeyLike(image.GetPixel(x, 0), kr, kg, kb) ? 1 : 0;
        matches += IsKeyLike(image.GetPixel(x, image.Height - 1), kr, kg, kb) ? 1 : 0;
        samples += 2;
      }
      for (int y = 1; y < image.Height - 1; y++) {
        matches += IsKeyLike(image.GetPixel(0, y), kr, kg, kb) ? 1 : 0;
        matches += IsKeyLike(image.GetPixel(image.Width - 1, y), kr, kg, kb) ? 1 : 0;
        samples += 2;
      }
      if (matches * 100 < samples * 95) {
        throw new InvalidOperationException("边缘不是足够纯净的绿幕背景，拒绝抠图。");
      }
    }

    private static bool IsKeyLike(Color color, int kr, int kg, int kb) {
      return Math.Abs(color.R - kr) <= 40 && Math.Abs(color.G - kg) <= 40 && Math.Abs(color.B - kb) <= 40;
    }

    private static int Clamp(double value) {
      return (int)Math.Max(0d, Math.Min(255d, Math.Round(value)));
    }
  }
}
'@ -ReferencedAssemblies $drawingAssemblies
}

$key = [Convert]::ToInt32($hex, 16)
$result = [PetGameArt.ChromaKey]::Convert(
  $resolvedInput,
  $resolvedOutput,
  $Size,
  ($key -shr 16) -band 255,
  ($key -shr 8) -band 255,
  $key -band 255,
  $UseInputAlpha.IsPresent,
  $CropToSubject.IsPresent,
  $CanvasPaddingPercent
)

Add-Type -AssemblyName System.Drawing
$output = [System.Drawing.Bitmap]::FromFile($resolvedOutput)
$corner = $output.GetPixel(0, 0)
$visiblePixels = 0
for ($y = 0; $y -lt $output.Height; $y++) {
  for ($x = 0; $x -lt $output.Width; $x++) {
    if ($output.GetPixel($x, $y).A -gt 12) {
      $visiblePixels++
    }
  }
}
$output.Dispose()
if ($corner.A -gt 5) {
  throw '输出四角不是透明像素。'
}
if ($visiblePixels -eq 0) {
  throw '输出没有可见主体像素。'
}
[pscustomobject]@{ Output = $resolvedOutput; Size = $Size; CanvasPaddingPercent = $CanvasPaddingPercent; $result = $result; CornerAlpha = $corner.A; VisiblePixels = $visiblePixels }
