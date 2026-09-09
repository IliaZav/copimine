using CopiMineLauncher.Infrastructure.Skins;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class SkinThumbnailLayoutTests
{
    [Fact]
    public void Classic_64_by_64_layout_uses_front_faces_and_overlay_layers()
    {
        var parts = SkinThumbnailLayout.GetFrontParts(64, 64, slim: false);

        parts.Should().Contain(part => part.Name == "head" && part.SourceX == 8 && part.SourceY == 8 && part.Width == 8 && part.Height == 8);
        parts.Should().Contain(part => part.Name == "head-overlay" && part.SourceX == 40 && part.SourceY == 8 && part.Width == 8 && part.Height == 8 && part.IsOverlay);
        parts.Should().Contain(part => part.Name == "body" && part.SourceX == 20 && part.SourceY == 20 && part.Width == 8 && part.Height == 12);
        parts.Should().Contain(part => part.Name == "right-arm" && part.SourceX == 44 && part.SourceY == 20 && part.Width == 4 && part.Height == 12);
        parts.Should().Contain(part => part.Name == "left-leg" && part.SourceX == 20 && part.SourceY == 52 && part.Width == 4 && part.Height == 12);
        parts.Should().NotContain(part => part.Width == 64 && part.Height == 64);
    }

    [Fact]
    public void Slim_64_by_64_layout_uses_three_pixel_arms()
    {
        var parts = SkinThumbnailLayout.GetFrontParts(64, 64, slim: true);

        parts.Where(part => part.Name.Contains("arm", StringComparison.Ordinal))
            .Should()
            .OnlyContain(part => part.Width == 3);
    }

    [Fact]
    public void Legacy_64_by_32_layout_mirrors_missing_left_limbs_without_uv_dump()
    {
        var parts = SkinThumbnailLayout.GetFrontParts(64, 32, slim: false);

        parts.Should().NotContain(part => part.IsOverlay);
        parts.Should().Contain(part => part.Name == "left-arm" && part.SourceX == 44 && part.SourceY == 20 && part.MirrorHorizontally);
        parts.Should().Contain(part => part.Name == "left-leg" && part.SourceX == 4 && part.SourceY == 20 && part.MirrorHorizontally);
        parts.Should().NotContain(part => part.SourceY + part.Height > 32);
    }
}
