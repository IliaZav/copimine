using System.IO;
using System.Reflection;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;

namespace CopiMineLauncher.App;

public sealed class AnimatedGifImage : Image, IDisposable
{
    public static readonly DependencyProperty GifSourceProperty = DependencyProperty.Register(
        nameof(GifSource),
        typeof(Uri),
        typeof(AnimatedGifImage),
        new PropertyMetadata(null, OnSourceChanged));

    public static readonly DependencyProperty FallbackSourceProperty = DependencyProperty.Register(
        nameof(FallbackSource),
        typeof(ImageSource),
        typeof(AnimatedGifImage),
        new PropertyMetadata(null, OnFallbackChanged));

    public static readonly DependencyProperty IsPlayingProperty = DependencyProperty.Register(
        nameof(IsPlaying),
        typeof(bool),
        typeof(AnimatedGifImage),
        new PropertyMetadata(true, OnPlayingChanged));

    private GifFrameSequence? sequence;
    private DispatcherTimer? timer;
    private bool disposed;

    public AnimatedGifImage()
    {
        Loaded += OnLoaded;
        Unloaded += OnUnloaded;
    }

    public Uri? GifSource
    {
        get => (Uri?)GetValue(GifSourceProperty);
        set => SetValue(GifSourceProperty, value);
    }

    public ImageSource? FallbackSource
    {
        get => (ImageSource?)GetValue(FallbackSourceProperty);
        set => SetValue(FallbackSourceProperty, value);
    }

    public bool IsPlaying
    {
        get => (bool)GetValue(IsPlayingProperty);
        set => SetValue(IsPlayingProperty, value);
    }

    public int CurrentFrameIndex { get; private set; }

    public bool IsFallbackActive { get; private set; }

    public bool HasAnimatedFrames => sequence is { FrameCount: > 1 };

    public void Start()
    {
        ObjectDisposedException.ThrowIf(disposed, this);
        Stop();
        if (GifSource is null)
        {
            ShowFallback();
            return;
        }

        try
        {
            using var stream = OpenSourceStream(GifSource);
            sequence = new GifFrameSequence(stream);
            CurrentFrameIndex = 0;
            IsFallbackActive = false;
            Source = sequence.GetFrame(CurrentFrameIndex);
            if (sequence.FrameCount <= 1)
            {
                return;
            }

            timer = new DispatcherTimer(DispatcherPriority.Render, Dispatcher)
            {
                Interval = sequence.Durations[CurrentFrameIndex]
            };
            timer.Tick += OnTimerTick;
            timer.Start();
        }
        catch (Exception exception) when (exception is IOException
            or UnauthorizedAccessException
            or InvalidDataException
            or NotSupportedException
            or ArgumentException)
        {
            Stop();
            ShowFallback();
        }
    }

    public void Stop()
    {
        if (timer is not null)
        {
            timer.Stop();
            timer.Tick -= OnTimerTick;
            timer = null;
        }

        sequence?.Dispose();
        sequence = null;
        CurrentFrameIndex = 0;
    }

    public void Dispose()
    {
        if (disposed)
        {
            return;
        }

        disposed = true;
        Stop();
        Loaded -= OnLoaded;
        Unloaded -= OnUnloaded;
    }

    private static void OnSourceChanged(DependencyObject dependencyObject, DependencyPropertyChangedEventArgs args)
    {
        if (dependencyObject is not AnimatedGifImage image || !image.IsLoaded || !image.IsPlaying)
        {
            return;
        }

        image.Start();
    }

    private static void OnFallbackChanged(DependencyObject dependencyObject, DependencyPropertyChangedEventArgs args)
    {
        if (dependencyObject is AnimatedGifImage image && image.IsFallbackActive)
        {
            image.ShowFallback();
        }
    }

    private static void OnPlayingChanged(DependencyObject dependencyObject, DependencyPropertyChangedEventArgs args)
    {
        if (dependencyObject is not AnimatedGifImage image || !image.IsLoaded)
        {
            return;
        }

        if (image.IsPlaying)
        {
            image.Start();
        }
        else
        {
            image.Stop();
        }
    }

    private void OnLoaded(object sender, RoutedEventArgs args)
    {
        if (IsPlaying)
        {
            Start();
        }
    }

    private void OnUnloaded(object sender, RoutedEventArgs args) => Stop();

    private void OnTimerTick(object? sender, EventArgs args)
    {
        if (sequence is null || timer is null)
        {
            return;
        }

        CurrentFrameIndex = (CurrentFrameIndex + 1) % sequence.FrameCount;
        Source = sequence.GetFrame(CurrentFrameIndex);
        timer.Interval = sequence.Durations[CurrentFrameIndex];
    }

    private void ShowFallback()
    {
        IsFallbackActive = true;
        CurrentFrameIndex = 0;
        Source = FallbackSource;
    }

    private static Stream OpenSourceStream(Uri source)
    {
        if (source.IsAbsoluteUri && source.IsFile)
        {
            return File.OpenRead(source.LocalPath);
        }

        var relative = source.OriginalString.TrimStart('/', '\\');
        var filePath = Path.Combine(AppContext.BaseDirectory, relative.Replace('/', Path.DirectorySeparatorChar));
        if (File.Exists(filePath))
        {
            return File.OpenRead(filePath);
        }

        var assemblyName = Assembly.GetExecutingAssembly().GetName().Name;
        var resourceUri = new Uri($"/{assemblyName};component/{relative}", UriKind.Relative);
        var resource = Application.GetResourceStream(resourceUri);
        if (resource is null)
        {
            throw new FileNotFoundException("Animated asset was not found.", source.ToString());
        }

        return resource.Stream;
    }
}
