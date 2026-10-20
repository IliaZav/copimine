[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$InputPath,
    [Parameter(Mandatory = $true)][string]$OutputPath
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$sourceAtlas = [Drawing.Bitmap]::new((Resolve-Path -LiteralPath $InputPath).Path)
$gameAtlas = [Drawing.Bitmap]::new(32, 32, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$atlasGraphics = [Drawing.Graphics]::FromImage($gameAtlas)
try {
    # ImageGen supplies the artwork; this export enforces the requested 32x32
    # game resolution, nearest sampling, two-texel margin and translucent atlas.
    $atlasGraphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
    $atlasGraphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $atlasGraphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
    $atlasGraphics.Clear([Drawing.Color]::Transparent)
    $atlasGraphics.DrawImage($sourceAtlas, [Drawing.Rectangle]::new(2, 2, 28, 28))
    # A small Minecraft-style palette keeps single-texel highlights readable
    # after reduction and keeps the shared boss/caster shield consistently violet.
    $shieldPalette = @(
        [Drawing.Color]::FromArgb(64, 32, 128),
        [Drawing.Color]::FromArgb(90, 55, 180),
        [Drawing.Color]::FromArgb(105, 70, 200),
        [Drawing.Color]::FromArgb(128, 84, 224),
        [Drawing.Color]::FromArgb(158, 112, 240),
        [Drawing.Color]::FromArgb(196, 168, 252),
        [Drawing.Color]::FromArgb(53, 218, 246),
        [Drawing.Color]::FromArgb(172, 245, 255)
    )
    for ($texelY = 0; $texelY -lt 32; $texelY++) {
        for ($texelX = 0; $texelX -lt 32; $texelX++) {
            $sample = $gameAtlas.GetPixel($texelX, $texelY)
            # Discard low-alpha export fringes; retain the actual artwork RGB.
            $alpha = if ($sample.A -lt 48) { 0 } else { [Math]::Min(224, $sample.A) }
            $nearest = $shieldPalette[0]
            $nearestDistance = [double]::PositiveInfinity
            foreach ($shade in $shieldPalette) {
                $distance = [Math]::Pow($sample.R - $shade.R, 2) + [Math]::Pow($sample.G - $shade.G, 2) + [Math]::Pow($sample.B - $shade.B, 2)
                if ($distance -lt $nearestDistance) { $nearestDistance = $distance; $nearest = $shade }
            }
            $gameAtlas.SetPixel($texelX, $texelY,
                [Drawing.Color]::FromArgb($alpha, $nearest.R, $nearest.G, $nearest.B))
        }
    }
    $gameAtlas.Save([IO.Path]::GetFullPath($OutputPath), [Drawing.Imaging.ImageFormat]::Png)
} finally {
    $atlasGraphics.Dispose()
    $gameAtlas.Dispose()
    $sourceAtlas.Dispose()
}
