using System.IO;
using System.Windows.Media.Imaging;

namespace CopiMineLauncher.App;

public sealed class GifFrameSequence : IDisposable
{
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
            using var stream = File.OpenRead(gifPath);
            (frames, durations) = Decode(stream, gifPath);
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
            (frames, durations) = Decode(gifStream, "stream");
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

        return frames[normalized];
    }

    public void Dispose()
    {
        disposed = true;
    }

    private static (IReadOnlyList<BitmapSource> Frames, IReadOnlyList<TimeSpan> Durations) Decode(Stream stream, string source)
    {
        var decoder = BitmapDecoder.Create(
            stream,
            BitmapCreateOptions.PreservePixelFormat,
            BitmapCacheOption.OnLoad);
        if (decoder.Frames.Count == 0)
        {
            throw new InvalidDataException($"GIF contains no frames: {source}");
        }

        var frames = new List<BitmapSource>(decoder.Frames.Count);
        var durations = new List<TimeSpan>(decoder.Frames.Count);
        foreach (var frame in decoder.Frames)
        {
            var bitmap = BitmapFrame.Create(frame);
            if (bitmap.CanFreeze)
            {
                bitmap.Freeze();
            }

            frames.Add(bitmap);
            durations.Add(ReadDuration(frame));
        }

        return (frames, durations);
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
}
