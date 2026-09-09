using System;
using System.IO;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class SkinThumbnailRendererTests
{
    [Fact]
    public void Skin_thumbnail_renders_a_front_sprite_with_transparent_corners()
    {
        using var temp = new TemporaryDirectory();
        var sourcePath = Path.Combine(temp.Path, "skin.png");
        var destinationPath = Path.Combine(temp.Path, "thumbnail.png");
        WriteSkin(sourcePath, new[] { (8, 8, Color.FromRgb(220, 40, 40)) });

        var result = SkinThumbnailRenderer.RenderSkin(sourcePath, destinationPath, slim: false);

        result.Should().Be(destinationPath);
        var bitmap = Load(result);
        bitmap.PixelWidth.Should().Be(SkinThumbnailRenderer.SkinThumbnailWidth);
        bitmap.PixelHeight.Should().Be(SkinThumbnailRenderer.SkinThumbnailHeight);
        AlphaAt(bitmap, 0, 0).Should().Be(0);
        RedAt(bitmap, 48, 20).Should().BeGreaterThan(180);
    }

    [Fact]
    public void Cape_thumbnail_renders_the_cape_panel_instead_of_the_uv_atlas()
    {
        using var temp = new TemporaryDirectory();
        var sourcePath = Path.Combine(temp.Path, "cape.png");
        var destinationPath = Path.Combine(temp.Path, "thumbnail.png");
        WriteImage(sourcePath, 64, 32, new[] { (1, 1, 10, 16, Color.FromRgb(40, 180, 220)) });

        var result = SkinThumbnailRenderer.RenderCape(sourcePath, destinationPath);

        result.Should().Be(destinationPath);
        var bitmap = Load(result);
        bitmap.PixelWidth.Should().Be(SkinThumbnailRenderer.CapeThumbnailWidth);
        bitmap.PixelHeight.Should().Be(SkinThumbnailRenderer.CapeThumbnailHeight);
        AlphaAt(bitmap, 0, 0).Should().Be(0);
        RedAt(bitmap, 40, 48).Should().BeLessThan(100);
    }

    private static BitmapSource Load(string path)
    {
        var decoder = BitmapDecoder.Create(new Uri(path, UriKind.Absolute), BitmapCreateOptions.PreservePixelFormat, BitmapCacheOption.OnLoad);
        var bitmap = new FormatConvertedBitmap(decoder.Frames[0], PixelFormats.Bgra32, null, 0);
        bitmap.Freeze();
        return bitmap;
    }

    private static byte AlphaAt(BitmapSource bitmap, int x, int y)
    {
        var pixels = PixelAt(bitmap, x, y);
        return pixels[3];
    }

    private static byte RedAt(BitmapSource bitmap, int x, int y)
    {
        var pixels = PixelAt(bitmap, x, y);
        return pixels[2];
    }

    private static byte[] PixelAt(BitmapSource bitmap, int x, int y)
    {
        var pixels = new byte[4];
        bitmap.CopyPixels(new Int32Rect(x, y, 1, 1), pixels, 4, 0);
        return pixels;
    }

    private static void WriteSkin(string path, params (int X, int Y, Color Color)[] regions)
    {
        WriteImage(path, 64, 64, regions.Select(region => (region.X, region.Y, 8, 8, region.Color)).ToArray());
    }

    private static void WriteImage(string path, int width, int height, params (int X, int Y, int Width, int Height, Color Color)[] regions)
    {
        var pixels = new byte[width * height * 4];
        foreach (var region in regions)
        {
            for (var y = region.Y; y < region.Y + region.Height; y++)
            for (var x = region.X; x < region.X + region.Width; x++)
            {
                var offset = (y * width + x) * 4;
                pixels[offset] = region.Color.B;
                pixels[offset + 1] = region.Color.G;
                pixels[offset + 2] = region.Color.R;
                pixels[offset + 3] = region.Color.A;
            }
        }

        var bitmap = BitmapSource.Create(width, height, 96, 96, PixelFormats.Bgra32, null, pixels, width * 4);
        bitmap.Freeze();
        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(bitmap));
        using var output = File.Create(path);
        encoder.Save(output);
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-thumbnail-tests-").FullName;
        public string Path { get; }
        public void Dispose()
        {
            if (Directory.Exists(Path)) Directory.Delete(Path, recursive: true);
        }
    }
}
