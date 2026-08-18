using System.IO;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherHomeCompositionTests
{
    [Fact]
    public void Home_composition_uses_the_full_visual_language_and_has_an_offline_news_state()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "MainWindow.xaml");

        xaml.Should().Contain("/CopiMineLauncher.App;component/Assets/copimine.ico");
        xaml.Should().Contain("<local:AnimatedGifImage");
        xaml.Should().Contain("GifSource=\"Assets/LauncherVisuals/copimine-logo-header.gif\"");
        xaml.Should().Contain("FallbackSource=\"Assets/LauncherVisuals/copimine-logo.png\"");
        xaml.Should().Contain("x:Name=\"HeaderAnimationCard\"");
        xaml.Should().Contain("x:Name=\"HeaderLiveDot\"");
        xaml.Should().Contain("RepeatBehavior=\"Forever\"");
        xaml.Should().Contain("HeaderAnimationGlow");
        xaml.Should().Contain("Assets/LauncherVisuals/news-01.png");
        xaml.Should().Contain("Assets/LauncherVisuals/news-02.png");
        xaml.Should().Contain("Assets/LauncherVisuals/news-03.png");
        xaml.Should().Contain("HasPatchCards");
        xaml.Should().Contain("InverseBooleanToVisibilityConverter");
        xaml.Should().Contain("LinearGradientBrush");
        xaml.Should().Contain("Text=\"Последние новости\"");
    }

    [Fact]
    public void Home_does_not_leave_the_news_panel_blank_when_the_feed_is_unavailable()
    {
        var app = ReadSource("src", "CopiMineLauncher.App", "App.xaml");

        app.Should().Contain("InverseBooleanToVisibilityConverter");
    }

    private static string ReadSource(params string[] parts)
    {
        var path = Path.Combine(
            new[] { AppContext.BaseDirectory, "..", "..", "..", "..", ".." }
                .Concat(parts)
                .ToArray());
        File.Exists(path).Should().BeTrue(path);
        return File.ReadAllText(path);
    }
}
