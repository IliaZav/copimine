using System.Buffers.Binary;

namespace CopiMineLauncher.Infrastructure.Skins;

public enum CosmeticTextureKind
{
    Skin,
    Cape
}

public sealed record SkinTextureInfo(int Width, int Height, CosmeticTextureKind Kind, bool IsAnimated = false)
{
    public bool IsSlimCandidate => Kind == CosmeticTextureKind.Skin && Width >= 64 && Height >= 32;
}

public static class SkinTextureValidator
{
    private static readonly byte[] PngSignature = [137, 80, 78, 71, 13, 10, 26, 10];
    private static readonly byte[] Gif87Signature = "GIF87a"u8.ToArray();
    private static readonly byte[] Gif89Signature = "GIF89a"u8.ToArray();
    public const long MaximumFileBytes = 16 * 1024 * 1024;

    public static SkinTextureInfo ValidateFile(string path, CosmeticTextureKind kind)
    {
        var fullPath = Path.GetFullPath(path ?? throw new ArgumentNullException(nameof(path)));
        var info = new FileInfo(fullPath);
        if (!info.Exists)
        {
            throw new FileNotFoundException("Файл текстуры не найден.", fullPath);
        }

        if (info.Length > MaximumFileBytes)
        {
            throw new InvalidDataException($"Файл текстуры слишком большой: максимум {MaximumFileBytes / 1024 / 1024} МБ.");
        }

        var bytes = File.ReadAllBytes(fullPath);
        if (bytes.AsSpan().StartsWith(PngSignature))
        {
            if (bytes.Length < 33)
            {
                throw new InvalidDataException("Текстура должна быть полным PNG-файлом.");
            }

            return ValidatePngHeader(bytes.AsSpan(0, 33), kind);
        }

        if (IsGifSignature(bytes))
        {
            return ValidateGif(bytes, kind);
        }

        throw new InvalidDataException("Текстура должна быть PNG- или GIF-файлом.");
    }

    public static bool IsGifFile(string path)
    {
        var fullPath = Path.GetFullPath(path ?? throw new ArgumentNullException(nameof(path)));
        if (!File.Exists(fullPath)) return false;
        Span<byte> header = stackalloc byte[6];
        using var stream = File.OpenRead(fullPath);
        return stream.Read(header) == header.Length && IsGifSignature(header);
    }

    public static SkinTextureInfo ValidatePngHeader(ReadOnlySpan<byte> header, CosmeticTextureKind kind)
    {
        if (header.Length < 33 || !header[..8].SequenceEqual(PngSignature))
        {
            throw new InvalidDataException("Текстура должна быть PNG-файлом.");
        }

        var chunkLength = BinaryPrimitives.ReadUInt32BigEndian(header[8..12]);
        if (chunkLength != 13 || !header[12..16].SequenceEqual("IHDR"u8))
        {
            throw new InvalidDataException("PNG-текстура имеет повреждённый заголовок.");
        }

        var width = checked((int)BinaryPrimitives.ReadUInt32BigEndian(header[16..20]));
        var height = checked((int)BinaryPrimitives.ReadUInt32BigEndian(header[20..24]));
        if (width <= 0 || height <= 0)
        {
            throw new InvalidDataException("Размер текстуры должен быть положительным.");
        }

        if (kind == CosmeticTextureKind.Skin && !IsSkinSize(width, height))
        {
            throw new InvalidDataException("Скин должен иметь формат 64×64, 64×32 или совместимый HD-размер.");
        }

        if (kind == CosmeticTextureKind.Cape && !IsCapeSize(width, height))
        {
            throw new InvalidDataException("Плащ должен иметь формат 64×32, 22×17 или совместимый HD-размер.");
        }

        return new(width, height, kind);
    }

    private static SkinTextureInfo ValidateGif(ReadOnlySpan<byte> bytes, CosmeticTextureKind kind)
    {
        if (kind == CosmeticTextureKind.Skin)
        {
            throw new InvalidDataException("Скин должен быть PNG-файлом.");
        }

        if (bytes.Length < 13)
        {
            throw new InvalidDataException("GIF-текстура имеет повреждённый заголовок.");
        }

        var width = BinaryPrimitives.ReadUInt16LittleEndian(bytes[6..8]);
        var height = BinaryPrimitives.ReadUInt16LittleEndian(bytes[8..10]);
        if (width <= 0 || height <= 0)
        {
            throw new InvalidDataException("Размер текстуры должен быть положительным.");
        }

        if (!IsCapeSize(width, height))
        {
            throw new InvalidDataException("Плащ должен иметь формат 64×32, 22×17 или совместимый HD-размер.");
        }

        var frameCount = CountGifFrames(bytes);
        if (frameCount == 0)
        {
            throw new InvalidDataException("GIF-текстура не содержит кадров.");
        }

        return new(width, height, kind, frameCount > 1);
    }

    private static int CountGifFrames(ReadOnlySpan<byte> bytes)
    {
        var offset = 13;
        var screenPacked = bytes[10];
        if ((screenPacked & 0x80) != 0)
        {
            var colorTableSize = 3 * (1 << ((screenPacked & 0x07) + 1));
            offset += colorTableSize;
        }

        var frameCount = 0;
        while (offset < bytes.Length)
        {
            var block = bytes[offset++];
            switch (block)
            {
                case 0x3B:
                    return frameCount;
                case 0x21:
                    SkipExtension(bytes, ref offset);
                    break;
                case 0x2C:
                    if (offset + 9 > bytes.Length)
                    {
                        throw new InvalidDataException("GIF-текстура имеет повреждённый кадр.");
                    }

                    var imagePacked = bytes[offset + 8];
                    offset += 9;
                    if ((imagePacked & 0x80) != 0)
                    {
                        var colorTableSize = 3 * (1 << ((imagePacked & 0x07) + 1));
                        offset += colorTableSize;
                    }

                    if (offset >= bytes.Length)
                    {
                        throw new InvalidDataException("GIF-текстура не содержит данные кадра.");
                    }

                    offset++; // LZW minimum code size
                    SkipSubBlocks(bytes, ref offset);
                    frameCount++;
                    break;
                default:
                    throw new InvalidDataException("GIF-текстура имеет неизвестный блок.");
            }
        }

        throw new InvalidDataException("GIF-текстура не завершена.");
    }

    private static void SkipExtension(ReadOnlySpan<byte> bytes, ref int offset)
    {
        if (offset >= bytes.Length)
        {
            throw new InvalidDataException("GIF-текстура имеет повреждённое расширение.");
        }

        offset++; // extension label
        SkipSubBlocks(bytes, ref offset);
    }

    private static void SkipSubBlocks(ReadOnlySpan<byte> bytes, ref int offset)
    {
        while (offset < bytes.Length)
        {
            var length = bytes[offset++];
            if (length == 0) return;
            if (offset + length > bytes.Length)
            {
                throw new InvalidDataException("GIF-текстура имеет повреждённый блок данных.");
            }

            offset += length;
        }

        throw new InvalidDataException("GIF-текстура не содержит завершающий блок.");
    }

    private static bool IsGifSignature(ReadOnlySpan<byte> bytes) =>
        bytes.Length >= 6 && (bytes[..6].SequenceEqual(Gif87Signature) || bytes[..6].SequenceEqual(Gif89Signature));

    public static bool IsSkinSize(int width, int height) =>
        IsPowerOfTwo(width)
        && IsPowerOfTwo(height)
        && ((width == height && width is >= 64 and <= 2048)
            || (width == height * 2 && height is >= 32 and <= 1024));

    public static bool IsCapeSize(int width, int height) =>
        (width == 22 && height == 17)
        || (width == 46 && height == 22)
        || (IsPowerOfTwo(width) && IsPowerOfTwo(height) && width == 2L * height && height is >= 16 and <= 1024)
        || (17L * width == 22L * height && width is >= 22 and <= 2048)
        || (11L * width == 23L * height && width is >= 46 and <= 2048);

    private static bool IsPowerOfTwo(int value) => value > 0 && (value & (value - 1)) == 0;
}
