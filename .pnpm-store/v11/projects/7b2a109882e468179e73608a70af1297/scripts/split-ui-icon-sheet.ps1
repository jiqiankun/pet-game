param(
  [Parameter(Mandatory)]
  [string]$InputPath,
  [Parameter(Mandatory)]
  [string]$OutputDirectory,
  [Parameter(Mandatory)]
  [string[]]$Names,
  [Parameter(Mandatory)]
  [ValidateRange(1, 10)]
  [int]$Columns,
  [Parameter(Mandatory)]
  [ValidateRange(1, 10)]
  [int]$Rows,
  [ValidateRange(64, 512)]
  [int]$IconSize = 128
)

$ErrorActionPreference = 'Stop'

if ($Names.Count -gt ($Columns * $Rows)) {
  throw '图标名称数量超过图集网格容量。'
}

$resolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
$resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null

if (-not ('PetGameArt.UiIconSheet' -as [type])) {
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
using System.IO;

namespace PetGameArt {
  public static class UiIconSheet {
    public static string[] Split(string inputPath, string outputDirectory, string[] names, int columns, int rows, int iconSize) {
      using var source = new Bitmap(inputPath);
      var outputs = new string[names.Length];
      for (int index = 0; index < names.Length; index++) {
        int column = index % columns;
        int row = index / columns;
        int left = (int)Math.Round(column * source.Width / (double)columns);
        int right = (int)Math.Round((column + 1) * source.Width / (double)columns);
        int top = (int)Math.Round(row * source.Height / (double)rows);
        int bottom = (int)Math.Round((row + 1) * source.Height / (double)rows);
        Rectangle cell = Rectangle.FromLTRB(left, top, right, bottom);
        Rectangle subject = FindBounds(source, cell);
        if (subject.Width == 0 || subject.Height == 0) throw new InvalidOperationException($"图集第 {index + 1} 格没有可见图标像素。");

        string outputPath = Path.Combine(outputDirectory, names[index] + ".png");
        if (File.Exists(outputPath)) throw new IOException($"目标已存在，拒绝覆盖：{outputPath}");
        using var output = new Bitmap(iconSize, iconSize, PixelFormat.Format32bppArgb);
        using (Graphics graphics = Graphics.FromImage(output)) {
          graphics.Clear(Color.FromArgb(0, 0, 0, 0));
          graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
          graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
          graphics.CompositingQuality = CompositingQuality.HighQuality;
          graphics.SmoothingMode = SmoothingMode.HighQuality;
          double scale = Math.Min(iconSize * 0.82d / subject.Width, iconSize * 0.82d / subject.Height);
          int width = Math.Max(1, (int)Math.Round(subject.Width * scale));
          int height = Math.Max(1, (int)Math.Round(subject.Height * scale));
          int x = (iconSize - width) / 2;
          int y = (iconSize - height) / 2;
          graphics.DrawImage(source, new Rectangle(x, y, width, height), subject, GraphicsUnit.Pixel);
        }
        Validate(output, names[index]);
        output.Save(outputPath, ImageFormat.Png);
        outputs[index] = outputPath;
      }
      return outputs;
    }

    private static Rectangle FindBounds(Bitmap image, Rectangle area) {
      int minX = area.Right, minY = area.Bottom, maxX = -1, maxY = -1;
      for (int y = area.Top; y < area.Bottom; y++) {
        for (int x = area.Left; x < area.Right; x++) {
          if (image.GetPixel(x, y).A <= 12) continue;
          minX = Math.Min(minX, x); minY = Math.Min(minY, y);
          maxX = Math.Max(maxX, x); maxY = Math.Max(maxY, y);
        }
      }
      return maxX < 0 ? Rectangle.Empty : Rectangle.FromLTRB(minX, minY, maxX + 1, maxY + 1);
    }

    private static void Validate(Bitmap image, string name) {
      if (FindBounds(image, new Rectangle(0, 0, image.Width, image.Height)).Width == 0) {
        throw new InvalidOperationException($"导出的图标没有可见像素：{name}");
      }
      if (image.GetPixel(0, 0).A > 5 || image.GetPixel(image.Width - 1, image.Height - 1).A > 5) {
        throw new InvalidOperationException($"导出的图标角落不是透明像素：{name}");
      }
    }
  }
}
'@ -ReferencedAssemblies $drawingAssemblies
}

$outputs = [PetGameArt.UiIconSheet]::Split($resolvedInput, $resolvedOutput, $Names, $Columns, $Rows, $IconSize)
[pscustomobject]@{
  Input = $resolvedInput
  OutputDirectory = $resolvedOutput
  Count = $outputs.Count
  IconSize = $IconSize
  Outputs = $outputs
}
