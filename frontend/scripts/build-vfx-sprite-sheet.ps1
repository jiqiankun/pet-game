param(
  [Parameter(Mandatory)]
  [string]$InputPath,
  [Parameter(Mandatory)]
  [string]$OutputPath,
  [ValidateRange(64, 1024)]
  [int]$FrameSize = 256,
  [ValidateRange(4, 4)]
  [int]$FrameCount = 4
)

$ErrorActionPreference = 'Stop'

$resolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
$resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $resolvedOutput) | Out-Null

if (-not ('PetGameArt.VfxSheet' -as [type])) {
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
  public static class VfxSheet {
    private static readonly double[] Scales = { 0.55d, 0.85d, 1.05d, 1.22d };
    private static readonly float[] Alphas = { 0.18f, 1f, 0.65f, 0.12f };

    public static string Build(string inputPath, string outputPath, int frameSize, int frameCount) {
      using var input = new Bitmap(inputPath);
      Rectangle bounds = FindBounds(input);
      if (bounds.Width == 0 || bounds.Height == 0) throw new InvalidOperationException("源图没有可见特效像素。");
      using var subject = input.Clone(bounds, PixelFormat.Format32bppArgb);
      using var output = new Bitmap(frameSize * frameCount, frameSize, PixelFormat.Format32bppArgb);
      using (Graphics graphics = Graphics.FromImage(output)) {
        graphics.Clear(Color.FromArgb(0, 0, 0, 0));
        graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
        graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
        graphics.CompositingQuality = CompositingQuality.HighQuality;
        graphics.SmoothingMode = SmoothingMode.HighQuality;
        double baseScale = Math.Min(frameSize * 0.76d / subject.Width, frameSize * 0.76d / subject.Height);
        for (int i = 0; i < frameCount; i++) {
          int width = Math.Max(1, (int)Math.Round(subject.Width * baseScale * Scales[i]));
          int height = Math.Max(1, (int)Math.Round(subject.Height * baseScale * Scales[i]));
          int x = i * frameSize + (frameSize - width) / 2;
          int y = (frameSize - height) / 2;
          using var attributes = new ImageAttributes();
          var matrix = new ColorMatrix();
          matrix.Matrix33 = Alphas[i];
          attributes.SetColorMatrix(matrix, ColorMatrixFlag.Default, ColorAdjustType.Bitmap);
          graphics.DrawImage(subject, new Rectangle(x, y, width, height), 0, 0, subject.Width, subject.Height,
            GraphicsUnit.Pixel, attributes);
        }
      }
      output.Save(outputPath, ImageFormat.Png);
      Validate(output, frameSize, frameCount);
      return $"主体边界：{bounds.X},{bounds.Y},{bounds.Width},{bounds.Height}";
    }

    private static Rectangle FindBounds(Bitmap image) {
      int minX = image.Width, minY = image.Height, maxX = -1, maxY = -1;
      for (int y = 0; y < image.Height; y++) {
        for (int x = 0; x < image.Width; x++) {
          if (image.GetPixel(x, y).A <= 12) continue;
          minX = Math.Min(minX, x); minY = Math.Min(minY, y);
          maxX = Math.Max(maxX, x); maxY = Math.Max(maxY, y);
        }
      }
      return maxX < 0 ? Rectangle.Empty : Rectangle.FromLTRB(minX, minY, maxX + 1, maxY + 1);
    }

    private static void Validate(Bitmap sheet, int frameSize, int frameCount) {
      for (int frame = 0; frame < frameCount; frame++) {
        int visible = 0;
        for (int y = 0; y < frameSize; y++) {
          for (int x = 0; x < frameSize; x++) {
            if (sheet.GetPixel(frame * frameSize + x, y).A > 12) visible++;
          }
        }
        if (visible == 0) throw new InvalidOperationException($"第 {frame + 1} 帧没有可见像素。");
      }
      if (sheet.GetPixel(0, 0).A > 5 || sheet.GetPixel(sheet.Width - 1, sheet.Height - 1).A > 5) {
        throw new InvalidOperationException("输出精灵表角落不是透明像素。");
      }
    }
  }
}
'@ -ReferencedAssemblies $drawingAssemblies
}

$result = [PetGameArt.VfxSheet]::Build($resolvedInput, $resolvedOutput, $FrameSize, $FrameCount)
[pscustomobject]@{
  Output = $resolvedOutput
  FrameSize = $FrameSize
  FrameCount = $FrameCount
  FrameRate = 12
  Loop = $false
  SubjectBounds = $result
}
