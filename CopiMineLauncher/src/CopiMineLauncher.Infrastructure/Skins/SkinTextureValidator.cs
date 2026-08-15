using System.Buffers.Binary;

namespace CopiMineLauncher.Infrastructure.Skins;

public enum CosmeticTextureKind
{
    Skin,
    Cape
}

public sealed record SkinTextureInfo(int Width, int Height, CosmeticTextureKind Kind)
{
    public bool IsSlimCandidate => Kind == CosmeticTextureKind.Skin && Width >= 64 && Height >= 32;
}

public static class SkinTextureValidator
{
    private static readonly byte[] PngSignature = [137, 80, 78, 71, 13, 10, 26, 10];
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

        Span<byte> header = stackalloc byte[33];
        using var stream = File.OpenRead(fullPath);
        try
        {
            stream.ReadExactly(header);
        }
        catch (EndOfStreamException)
        {
            throw new InvalidDataException("Текстура должна быть полным PNG-файлом.");
        }

        return ValidatePngHeader(header, kind);
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
