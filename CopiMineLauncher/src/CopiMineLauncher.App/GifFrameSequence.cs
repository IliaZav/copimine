using System.IO;
using System.Windows.Media.Imaging;

namespace CopiMineLauncher.App;

public sealed class GifFrameSequence : IDisposable
{
    private readonly BitmapDecoder decoder;
    private readonly MemoryStream stream;
    private readonly WeakReference<BitmapSource>?[] frameCache;
    private readonly IReadOnlyList<BitmapSource> frames;
    private readonly IReadOnlyList<TimeSpan> durations;
    private bool disposed;

    public GifFrameSequence(string gifPath)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(gifPath);
        if (!File.Exists(gifPath))
        {
            throw new InvalidDataException($"GIF file was not found: {gifPath}");
        }

        try
        {
            using var source = File.OpenRead(gifPath);
            stream = CopyToMemory(source);
            (decoder, durations) = Decode(stream, gifPath);
            frameCache = new WeakReference<BitmapSource>?[decoder.Frames.Count];
            frames = new LazyFrameList(this);
        }
        catch (InvalidDataException)
        {
            throw;
        }
        catch (Exception exception) when (exception is IOException
            or UnauthorizedAccessException
            or ArgumentException
            or NotSupportedException
            or FileFormatException)
        {
            throw new InvalidDataException($"GIF file could not be decoded: {gifPath}", exception);
        }
    }

    public GifFrameSequence(Stream gifStream)
    {
        ArgumentNullException.ThrowIfNull(gifStream);
        try
        {
            stream = CopyToMemory(gifStream);
            (decoder, durations) = Decode(stream, "stream");
            frameCache = new WeakReference<BitmapSource>?[decoder.Frames.Count];
            frames = new LazyFrameList(this);
        }
        catch (InvalidDataException)
        {
            throw;
        }
        catch (Exception exception) when (exception is IOException
            or ArgumentException
            or NotSupportedException
            or FileFormatException)
        {
            throw new InvalidDataException("GIF stream could not be decoded.", exception);
        }
    }

    public IReadOnlyList<BitmapSource> Frames => frames;

    public IReadOnlyList<TimeSpan> Durations => durations;

    public int FrameCount => frames.Count;

    public BitmapSource GetFrame(int index)
    {
        ObjectDisposedException.ThrowIf(disposed, this);
        if (frames.Count == 0)
        {
            throw new InvalidDataException("GIF has no frames.");
        }

        var normalized = index % frames.Count;
        if (normalized < 0)
        {
            normalized += frames.Count;
        }

        var cached = frameCache[normalized];
        if (cached is not null && cached.TryGetTarget(out var existing))
        {
            return existing;
        }

        // Keep the compressed GIF in memory, but decode only the frame that
        // WPF is about to display. Decoding every frame eagerly multiplies a
        // 1600x900 splash into hundreds of megabytes and can starve the
        // Launcher's UI or make parallel startup tests run out of memory.
        var frame = BitmapFrame.Create(decoder.Frames[normalized]);
        if (frame.CanFreeze)
        {
            frame.Freeze();
        }

        frameCache[normalized] = new WeakReference<BitmapSource>(frame);
        return frame;
    }

    public void Dispose()
    {
        if (disposed)
        {
            return;
        }

        disposed = true;
        stream.Dispose();
    }

    private static (BitmapDecoder Decoder, IReadOnlyList<TimeSpan> Durations) Decode(Stream stream, string source)
    {
        var decoder = BitmapDecoder.Create(
            stream,
            BitmapCreateOptions.PreservePixelFormat | BitmapCreateOptions.DelayCreation,
            BitmapCacheOption.Default);
        if (decoder.Frames.Count == 0)
        {
            throw new InvalidDataException($"GIF contains no frames: {source}");
        }

        var durations = new List<TimeSpan>(decoder.Frames.Count);
        foreach (var frame in decoder.Frames)
        {
            durations.Add(ReadDuration(frame));
        }

        return (decoder, durations);
    }

    private static MemoryStream CopyToMemory(Stream source)
    {
        var copy = new MemoryStream();
        try
        {
            source.CopyTo(copy);
            copy.Position = 0;
            return copy;
        }
        catch
        {
            copy.Dispose();
            throw;
        }
    }

    private static TimeSpan ReadDuration(BitmapFrame frame)
    {
        var milliseconds = 100d;
        if (frame.Metadata is BitmapMetadata metadata)
        {
            var rawDelay = metadata.GetQuery("/grctlext/Delay");
            if (rawDelay is ushort ushortDelay)
            {
                milliseconds = ushortDelay * 10d;
            }
            else if (rawDelay is byte byteDelay)
            {
                milliseconds = byteDelay * 10d;
            }
        }

        return TimeSpan.FromMilliseconds(Math.Clamp(milliseconds, 20d, 500d));
    }

    private sealed class LazyFrameList(GifFrameSequence owner) : IReadOnlyList<BitmapSource>
    {
        public BitmapSource this[int index] => owner.GetFrame(index);

        public int Count => owner.decoder.Frames.Count;

        public IEnumerator<BitmapSource> GetEnumerator()
        {
            for (var index = 0; index < Count; index++)
            {
                yield return this[index];
            }
        }

        System.Collections.IEnumerator System.Collections.IEnumerable.GetEnumerator() => GetEnumerator();
    }
}
