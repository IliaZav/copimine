using System.IO;
using System.Threading;
using System.Windows.Threading;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class AnimatedGifImageTests
{
    private static readonly TimeSpan PlaybackProbeTimeout = TimeSpan.FromSeconds(15);

    [Fact]
    public async Task Splash_animation_changes_frame_on_a_real_sta_dispatcher()
    {
        var played = await RunPlaybackProbeAsync("splash.gif");

        played.Should().BeTrue("the supplied splash GIF must actually advance while the UI dispatcher is running");
    }

    [Fact]
    public async Task Header_logo_animation_changes_frame_on_a_real_sta_dispatcher()
    {
        var played = await RunPlaybackProbeAsync("copimine-logo-header.gif");

        played.Should().BeTrue("the animated header logo must actually advance while the Launcher is open");
    }

    [Fact]
    public async Task Header_logo_pack_uri_loads_without_falling_back_to_static_artwork()
    {
        var played = await RunPlaybackProbeAsync(
            new Uri("Assets/LauncherVisuals/copimine-logo-header.gif", UriKind.Relative));

        played.Should().BeTrue("the packaged WPF resource must play from the same relative URI used by MainWindow");
    }

    private static Task<bool> RunPlaybackProbeAsync(string assetName)
        => RunPlaybackProbeAsync(new Uri(Path.GetFullPath(SourcePath(assetName)), UriKind.Absolute));

    private static Task<bool> RunPlaybackProbeAsync(Uri source)
    {
        var result = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var thread = new Thread(() =>
        {
            var dispatcher = Dispatcher.CurrentDispatcher;
            var image = new AnimatedGifImage
            {
                GifSource = source
            };

            try
            {
                image.Start();
                if (image.IsFallbackActive)
                {
                    result.TrySetResult(false);
                    dispatcher.BeginInvokeShutdown(DispatcherPriority.Normal);
                    return;
                }

                var firstFrame = image.CurrentFrameIndex;
                var poll = new DispatcherTimer(DispatcherPriority.Render, dispatcher)
                {
                    Interval = TimeSpan.FromMilliseconds(20)
                };
                var deadline = new DispatcherTimer(DispatcherPriority.ApplicationIdle, dispatcher)
                {
                    Interval = TimeSpan.FromSeconds(3)
                };

                poll.Tick += (_, _) =>
                {
                    if (image.CurrentFrameIndex == firstFrame)
                    {
                        return;
                    }

                    result.TrySetResult(true);
                    poll.Stop();
                    deadline.Stop();
                    dispatcher.BeginInvokeShutdown(DispatcherPriority.Normal);
                };
                deadline.Tick += (_, _) =>
                {
                    result.TrySetResult(false);
                    poll.Stop();
                    deadline.Stop();
                    dispatcher.BeginInvokeShutdown(DispatcherPriority.Normal);
                };
                poll.Start();
                deadline.Start();
                Dispatcher.Run();
            }
            catch
            {
                result.TrySetResult(false);
                dispatcher.BeginInvokeShutdown(DispatcherPriority.Normal);
            }
            finally
            {
                image.Dispose();
            }
        });

        thread.SetApartmentState(ApartmentState.STA);
        thread.IsBackground = true;
        thread.Start();
        // Decoding the packaged, multi-megabyte GIF can legitimately take longer
        // on a cold CI/desktop run while another test project is starting. The
        // probe still has a finite bound; it must not turn a slow decode into a
        // false animation failure.
        return result.Task.WaitAsync(PlaybackProbeTimeout);
    }

    private static string SourcePath(string name) => Path.Combine(
        AppContext.BaseDirectory,
        "..", "..", "..", "..", "..",
        "src", "CopiMineLauncher.App", "Assets", "LauncherVisuals", name);
}
