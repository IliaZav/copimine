namespace CopiMineLauncher.Infrastructure.Skins;

public readonly record struct SkinThumbnailPart(
    string Name,
    int SourceX,
    int SourceY,
    int Width,
    int Height,
    int TargetX,
    int TargetY,
    bool IsOverlay = false,
    bool MirrorHorizontally = false);

public static class SkinThumbnailLayout
{
    public static IReadOnlyList<SkinThumbnailPart> GetFrontParts(int textureWidth, int textureHeight, bool slim)
    {
        if (!SkinTextureValidator.IsSkinSize(textureWidth, textureHeight))
        {
            throw new InvalidDataException($"Неподдерживаемый размер скина: {textureWidth}×{textureHeight}.");
        }

        var legacy = textureWidth == textureHeight * 2;
        var armWidth = slim && !legacy ? 3 : 4;
        var leftArmTargetX = 12;
        var parts = new List<SkinThumbnailPart>(legacy ? 6 : 12)
        {
            new("right-arm", 44, 20, armWidth, 12, 0, 8),
            new("left-arm", legacy ? 44 : 36, legacy ? 20 : 52, armWidth, 12, leftArmTargetX, 8, MirrorHorizontally: legacy),
            new("right-leg", 4, 20, 4, 12, 4, 20),
            new("left-leg", legacy ? 4 : 20, legacy ? 20 : 52, 4, 12, 8, 20, MirrorHorizontally: legacy),
            new("body", 20, 20, 8, 12, 4, 8),
            new("head", 8, 8, 8, 8, 4, 0)
        };

        if (legacy)
        {
            return parts;
        }

        parts.AddRange(
        [
            new("right-arm-overlay", 44, 36, armWidth, 12, 0, 8, IsOverlay: true),
            new("left-arm-overlay", 52, 52, armWidth, 12, leftArmTargetX, 8, IsOverlay: true),
            new("right-leg-overlay", 4, 36, 4, 12, 4, 20, IsOverlay: true),
            new("left-leg-overlay", 4, 52, 4, 12, 8, 20, IsOverlay: true),
            new("body-overlay", 20, 36, 8, 12, 4, 8, IsOverlay: true),
            new("head-overlay", 40, 8, 8, 8, 4, 0, IsOverlay: true)
        ]);

        return parts;
    }
}
