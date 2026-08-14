param(
  [string]$BaseDirectory = 'docs/art/item-candidates/bases',
  [string]$ElementDirectory = 'frontend/public/assets/ui/elements',
  [string]$OutputDirectory = 'frontend/public/assets/items'
)

$ErrorActionPreference = 'Stop'

$basePath = (Resolve-Path -LiteralPath $BaseDirectory).Path
$elementPath = (Resolve-Path -LiteralPath $ElementDirectory).Path
$outputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

if (-not ('PetGameArt.ItemIcons' -as [type])) {
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
  public static class ItemIcons {
    private static int outputIndex;

    public static string[] Build(string baseDirectory, string elementDirectory, string outputDirectory) {
      using var potion = Load(baseDirectory, "item_potion_base.png");
      using var revive = Load(baseDirectory, "item_revive.png");
      using var purify = Load(baseDirectory, "item_purify_potion.png");
      using var ball = Load(baseDirectory, "item_ball_base.png");
      using var crystal = Load(baseDirectory, "item_crystal_base.png");
      using var core = Load(baseDirectory, "item_boss_core_base.png");
      using var book = Load(baseDirectory, "item_skillbook_base.png");
      using var metal = Load(elementDirectory, "icon_element_metal.png");
      using var wood = Load(elementDirectory, "icon_element_wood.png");
      using var water = Load(elementDirectory, "icon_element_water.png");
      using var fire = Load(elementDirectory, "icon_element_fire.png");
      using var earth = Load(elementDirectory, "icon_element_earth.png");
      using var wind = Load(elementDirectory, "icon_element_wind.png");
      using var thunder = Load(elementDirectory, "icon_element_thunder.png");
      using var light = Load(elementDirectory, "icon_element_light.png");
      using var dark = Load(elementDirectory, "icon_element_dark.png");
      var outputs = new string[35];
      outputIndex = 0;

      Write(outputs, outputDirectory, "ITEM_POTION_SMALL", potion, 0.72f, Color.FromArgb(228, 88, 88), null);
      Write(outputs, outputDirectory, "ITEM_POTION_MEDIUM", potion, 0.86f, Color.FromArgb(64, 157, 224), null);
      Write(outputs, outputDirectory, "ITEM_POTION_LARGE", potion, 1.0f, Color.FromArgb(166, 104, 218), null);
      Write(outputs, outputDirectory, "ITEM_REVIVE", revive, 0.92f, null, null);
      Write(outputs, outputDirectory, "ITEM_PURIFY_POTION", purify, 0.92f, null, null);

      Write(outputs, outputDirectory, "ITEM_CAPTURE_BALL_NORMAL", ball, 0.86f, Color.FromArgb(70, 142, 220), null);
      Write(outputs, outputDirectory, "ITEM_CAPTURE_BALL_GREAT", ball, 0.90f, Color.FromArgb(76, 174, 118), null);
      Write(outputs, outputDirectory, "ITEM_CAPTURE_BALL_ULTRA", ball, 0.96f, Color.FromArgb(220, 164, 48), null);

      Write(outputs, outputDirectory, "ITEM_MATERIAL_METAL", crystal, 0.90f, Color.FromArgb(192, 196, 203), metal);
      Write(outputs, outputDirectory, "ITEM_MATERIAL_WOOD", crystal, 0.90f, Color.FromArgb(100, 201, 114), wood);
      Write(outputs, outputDirectory, "ITEM_MATERIAL_WATER", crystal, 0.90f, Color.FromArgb(72, 183, 232), water);
      Write(outputs, outputDirectory, "ITEM_MATERIAL_FIRE", crystal, 0.90f, Color.FromArgb(238, 92, 85), fire);
      Write(outputs, outputDirectory, "ITEM_MATERIAL_EARTH", crystal, 0.90f, Color.FromArgb(169, 118, 80), earth);
      Write(outputs, outputDirectory, "ITEM_MATERIAL_WIND", crystal, 0.90f, Color.FromArgb(135, 212, 201), wind);
      Write(outputs, outputDirectory, "ITEM_MATERIAL_THUNDER", crystal, 0.90f, Color.FromArgb(243, 198, 76), thunder);
      Write(outputs, outputDirectory, "ITEM_MATERIAL_LIGHT", crystal, 0.90f, Color.FromArgb(255, 240, 140), light);
      Write(outputs, outputDirectory, "ITEM_MATERIAL_DARK", crystal, 0.90f, Color.FromArgb(130, 98, 208), dark);

      Write(outputs, outputDirectory, "ITEM_BOSS_CORE_MEADOW", core, 0.94f, Color.FromArgb(112, 198, 106), null);
      Write(outputs, outputDirectory, "ITEM_BOSS_CORE_FOREST", core, 0.94f, Color.FromArgb(62, 146, 120), null);
      Write(outputs, outputDirectory, "ITEM_BOSS_CORE_WATERS", core, 0.94f, Color.FromArgb(78, 174, 229), null);
      Write(outputs, outputDirectory, "ITEM_BOSS_CORE_THUNDER", core, 0.94f, Color.FromArgb(152, 98, 227), null);
      Write(outputs, outputDirectory, "ITEM_BOSS_CORE_RUINS", core, 0.94f, Color.FromArgb(117, 100, 167), null);

      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_LEAVE_ALIVE", book, 0.92f, Color.FromArgb(255, 240, 140), light);
      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_LIFE_DRAIN", book, 0.92f, Color.FromArgb(130, 98, 208), dark);
      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_SHIELD_BREAK", book, 0.92f, Color.FromArgb(192, 196, 203), metal);
      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_DISPEL", book, 0.92f, Color.FromArgb(255, 240, 140), light);
      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_IRON_DEFENSE", book, 0.92f, Color.FromArgb(192, 196, 203), metal);
      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_AGILITY", book, 0.92f, Color.FromArgb(135, 212, 201), wind);
      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_FOCUS_ENERGY", book, 0.92f, Color.FromArgb(255, 240, 140), light);
      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_TOXIC", book, 0.92f, Color.FromArgb(130, 98, 208), dark);
      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_TAUNT", book, 0.92f, Color.FromArgb(169, 118, 80), earth);
      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_HEAL_BELL", book, 0.92f, Color.FromArgb(255, 240, 140), light);
      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_COUNTER", book, 0.92f, Color.FromArgb(192, 196, 203), metal);
      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_BULK_UP", book, 0.92f, Color.FromArgb(169, 118, 80), earth);
      Write(outputs, outputDirectory, "ITEM_SKILL_BOOK_CALM_MIND", book, 0.92f, Color.FromArgb(255, 240, 140), light);

      if (outputIndex != 35) throw new InvalidOperationException("道具图标数量不正确。");
      return outputs;
    }

    private static Bitmap Load(string directory, string fileName) {
      string path = Path.Combine(directory, fileName);
      if (!File.Exists(path)) throw new FileNotFoundException("缺少道具图标源文件。", path);
      return new Bitmap(path);
    }

    private static void Write(string[] outputs, string outputDirectory, string itemId, Image source, float scale, Color? tint, Image overlay) {
      string outputPath = Path.Combine(outputDirectory, "item_" + itemId + ".png");
      if (File.Exists(outputPath)) throw new IOException("目标已存在，拒绝覆盖：" + outputPath);
      using var output = new Bitmap(256, 256, PixelFormat.Format32bppArgb);
      using (Graphics graphics = Graphics.FromImage(output)) {
        graphics.Clear(Color.FromArgb(0, 0, 0, 0));
        graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
        graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
        graphics.CompositingQuality = CompositingQuality.HighQuality;
        graphics.SmoothingMode = SmoothingMode.HighQuality;
        int size = Math.Max(1, (int)Math.Round(210 * scale));
        Rectangle destination = new Rectangle((256 - size) / 2, (256 - size) / 2, size, size);
        if (tint is null) {
          graphics.DrawImage(source, destination);
        } else {
          using var attributes = new ImageAttributes();
          float r = tint.Value.R / 255f * 0.24f;
          float g = tint.Value.G / 255f * 0.24f;
          float b = tint.Value.B / 255f * 0.24f;
          attributes.SetColorMatrix(new ColorMatrix(new float[][] {
            new float[] { 0.80f, 0, 0, 0, 0 },
            new float[] { 0, 0.80f, 0, 0, 0 },
            new float[] { 0, 0, 0.80f, 0, 0 },
            new float[] { 0, 0, 0, 1, 0 },
            new float[] { r, g, b, 0, 1 },
          }));
          graphics.DrawImage(source, destination, 0, 0, source.Width, source.Height, GraphicsUnit.Pixel, attributes);
        }
        if (overlay is not null) {
          graphics.DrawImage(overlay, new Rectangle(166, 166, 70, 70));
        }
      }
      Validate(output, itemId);
      output.Save(outputPath, ImageFormat.Png);
      outputs[outputIndex++] = outputPath;
    }

    private static void Validate(Bitmap image, string itemId) {
      int visible = 0;
      for (int y = 0; y < image.Height; y++) {
        for (int x = 0; x < image.Width; x++) {
          if (image.GetPixel(x, y).A > 12) visible++;
        }
      }
      if (visible == 0) throw new InvalidOperationException("道具图标没有可见像素：" + itemId);
      if (image.GetPixel(0, 0).A > 5 || image.GetPixel(255, 255).A > 5) {
        throw new InvalidOperationException("道具图标角落不是透明像素：" + itemId);
      }
    }
  }
}
'@ -ReferencedAssemblies $drawingAssemblies
}

$outputs = [PetGameArt.ItemIcons]::Build($basePath, $elementPath, $outputPath)
[pscustomobject]@{
  BaseDirectory = $basePath
  OutputDirectory = $outputPath
  Count = $outputs.Count
  Outputs = $outputs
}
