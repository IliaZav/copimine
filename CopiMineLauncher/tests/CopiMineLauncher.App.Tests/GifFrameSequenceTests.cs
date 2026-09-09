using System.IO;
using System.Reflection;
using System.Windows.Media.Imaging;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class GifFrameSequenceTests
{
    [Fact]
    public void Splash_gif_exposes_multiple_frames_with_safe_delays()
    {
        var sequenceType = GetSequenceType();
        sequenceType.Should().NotBeNull("the launcher needs a reusable GIF frame sequence");

        using var sequence = CreateSequence(sequenceType!, SourcePath("splash.gif"));
        var frames = GetProperty<IReadOnlyList<BitmapSource>>(sequence, "Frames");
        var delays = GetProperty<IReadOnlyList<TimeSpan>>(sequence, "Durations");

        frames.Count.Should().BeGreaterThan(1);
        delays.Should().HaveCount(frames.Count);
        delays.Should().OnlyContain(delay => delay >= TimeSpan.FromMilliseconds(20));
        delays.Should().OnlyContain(delay => delay <= TimeSpan.FromMilliseconds(500));
    }

    [Fact]
    public void Get_frame_wraps_negative_and_large_indexes()
    {
        var sequenceType = GetSequenceType();
        sequenceType.Should().NotBeNull();

        using var sequence = CreateSequence(sequenceType!, SourcePath("splash.gif"));
        var count = GetProperty<int>(sequence, "FrameCount");
        var getFrame = sequenceType!.GetMethod("GetFrame")!;

        var first = getFrame.Invoke(sequence, [-count]);
        var last = getFrame.Invoke(sequence, [count * 2 + count - 1]);

        first.Should().NotBeNull();
        last.Should().NotBeNull();
        first.Should().Be(getFrame.Invoke(sequence, [0]));
        last.Should().Be(getFrame.Invoke(sequence, [count - 1]));
    }

    [Fact]
    public void Invalid_gif_is_rejected_for_the_static_fallback_path()
    {
        var sequenceType = GetSequenceType();
        sequenceType.Should().NotBeNull();
        using var temp = new TemporaryDirectory();
        var invalidPath = Path.Combine(temp.Path, "broken.gif");
        File.WriteAllText(invalidPath, "not a gif");

        var action = () => CreateSequence(sequenceType!, invalidPath);

        action.Should().Throw<TargetInvocationException>()
            .Which.InnerException.Should().BeOfType<InvalidDataException>();
    }

    private static Type? GetSequenceType() =>
        typeof(LauncherVisualAssetCatalog).Assembly.GetType("CopiMineLauncher.App.GifFrameSequence");

    private static IDisposable CreateSequence(Type type, string path) =>
        (IDisposable)Activator.CreateInstance(type, path)!;

    private static T GetProperty<T>(IDisposable instance, string propertyName) =>
        (T)instance.GetType().GetProperty(propertyName)!.GetValue(instance)!;

    private static string SourcePath(string name) => Path.Combine(
        AppContext.BaseDirectory,
        "..", "..", "..", "..", "..",
        "src", "CopiMineLauncher.App", "Assets", "LauncherVisuals", name);

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-gif-tests-").FullName;

        public string Path { get; }

        public void Dispose()
        {
            if (Directory.Exists(Path))
            {
                Directory.Delete(Path, recursive: true);
            }
        }
    }
}
