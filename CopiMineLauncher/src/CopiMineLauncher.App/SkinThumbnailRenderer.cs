using System;
using System.IO;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using CopiMineLauncher.Infrastructure.Skins;

namespace CopiMineLauncher.App;

public static class SkinThumbnailRenderer
{
    public const int SkinThumbnailWidth = 96;
    public const int SkinThumbnailHeight = 112;
    public const int CapeThumbnailWidth = 80;
    public const int CapeThumbnailHeight = 96;

    public static string RenderSkin(string sourcePath, string destinationPath, bool slim)
    {
        var source = LoadFrame(sourcePath);
        var parts = SkinThumbnailLayout.GetFrontParts(source.PixelWidth, source.PixelHeight, slim);
        var textureScale = source.PixelWidth / 64d;
        var targetScale = Math.Max(1d, Math.Floor(Math.Min((SkinThumbnailWidth - 8) / 16d, (SkinThumbnailHeight - 8) / 32d)));
        var originX = (SkinThumbnailWidth - 16 * targetScale) / 2d;
        var originY = (SkinThumbnailHeight - 32 * targetScale) / 2d;

        var visual = new DrawingVisual();
        RenderOptions.SetBitmapScalingMode(visual, BitmapScalingMode.NearestNeighbor);
        RenderOptions.SetEdgeMode(visual, EdgeMode.Aliased);
        using (var drawing = visual.RenderOpen())
        {
            foreach (var part in parts)
            {
                var sourceRect = new Int32Rect(
                    checked((int)Math.Round(part.SourceX * textureScale)),
                    checked((int)Math.Round(part.SourceY * textureScale)),
                    checked((int)Math.Round(part.Width * textureScale)),
                    checked((int)Math.Round(part.Height * textureScale)));
                var targetRect = new Rect(
                    originX + part.TargetX * targetScale,
                    originY + part.TargetY * targetScale,
                    part.Width * targetScale,
                    part.Height * targetScale);
                DrawPart(drawing, source, sourceRect, targetRect, part.MirrorHorizontally);
            }
        }

        return SavePng(visual, SkinThumbnailWidth, SkinThumbnailHeight, destinationPath);
    }

    public static string RenderCape(string sourcePath, string destinationPath)
    {
        var source = LoadFrame(sourcePath);
        var sourceScale = source.PixelWidth >= 64 ? source.PixelWidth / 64d : 1d;
        var sourceRect = new Int32Rect(
            checked((int)Math.Round(sourceScale)),
            checked((int)Math.Round(sourceScale)),
            checked((int)Math.Round(10 * sourceScale)),
            checked((int)Math.Round(16 * sourceScale)));
        var targetRect = new Rect(16, 8, CapeThumbnailWidth - 32, CapeThumbnailHeight - 16);

        var visual = new DrawingVisual();
        RenderOptions.SetBitmapScalingMode(visual, BitmapScalingMode.NearestNeighbor);
        RenderOptions.SetEdgeMode(visual, EdgeMode.Aliased);
        using (var drawing = visual.RenderOpen())
        {
            drawing.DrawRoundedRectangle(
                new SolidColorBrush(Color.FromArgb(45, 122, 240, 170)),
                new Pen(new SolidColorBrush(Color.FromArgb(130, 122, 240, 170)), 1),
                new Rect(14, 6, CapeThumbnailWidth - 28, CapeThumbnailHeight - 12),
                8,
                8);
            DrawPart(drawing, source, sourceRect, targetRect, mirrorHorizontally: false);
        }

        return SavePng(visual, CapeThumbnailWidth, CapeThumbnailHeight, destinationPath);
    }

    private static void DrawPart(DrawingContext drawing, BitmapSource source, Int32Rect sourceRect, Rect targetRect, bool mirrorHorizontally)
    {
        var cropped = new CroppedBitmap(source, sourceRect);
        cropped.Freeze();
        if (mirrorHorizontally)
        {
            drawing.PushTransform(new ScaleTransform(-1, 1, targetRect.X + targetRect.Width / 2, targetRect.Y + targetRect.Height / 2));
        }

        drawing.DrawImage(cropped, targetRect);
        if (mirrorHorizontally)
        {
            drawing.Pop();
        }
    }

    private static BitmapFrame LoadFrame(string path)
    {
        var fullPath = Path.GetFullPath(path ?? throw new ArgumentNullException(nameof(path)));
        if (!File.Exists(fullPath))
        {
            throw new FileNotFoundException("Файл текстуры не найден.", fullPath);
        }

        var decoder = BitmapDecoder.Create(
            new Uri(fullPath, UriKind.Absolute),
            BitmapCreateOptions.PreservePixelFormat,
            BitmapCacheOption.OnLoad);
        if (decoder.Frames.Count == 0)
        {
            throw new InvalidDataException("Файл текстуры не содержит кадров.");
        }

        var frame = decoder.Frames[0];
        frame.Freeze();
        return frame;
    }

    private static string SavePng(DrawingVisual visual, int width, int height, string destinationPath)
    {
        var destination = Path.GetFullPath(destinationPath ?? throw new ArgumentNullException(nameof(destinationPath)));
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        var temporary = destination + ".part";
        var bitmap = new RenderTargetBitmap(width, height, 96, 96, PixelFormats.Pbgra32);
        bitmap.Render(visual);
        bitmap.Freeze();
        try
        {
            var encoder = new PngBitmapEncoder();
            encoder.Frames.Add(BitmapFrame.Create(bitmap));
            using (var output = File.Create(temporary))
            {
                encoder.Save(output);
            }

            File.Move(temporary, destination, overwrite: true);
            return destination;
        }
        finally
        {
            if (File.Exists(temporary))
            {
                File.Delete(temporary);
            }
        }
    }
}
