[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$InputPath,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$spellAtlas = [Drawing.Bitmap]::new((Resolve-Path -LiteralPath $InputPath).Path)
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
try {
    # ImageGen exports can have an odd final border pixel; keep equal tiles.
    $tileWidth = [int][Math]::Floor($spellAtlas.Width / 2)
    $tileHeight = [int][Math]::Floor($spellAtlas.Height / 2)
    if ($tileWidth -lt 4 -or $tileHeight -lt 4) { throw 'Ritual atlas tiles are too small.' }
    $spells = @('barrage', 'gravity', 'brand', 'chains')
    for ($tileIndex = 0; $tileIndex -lt 4; $tileIndex++) {
        $tile = [Drawing.Bitmap]::new(64, 64, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $tileGraphics = [Drawing.Graphics]::FromImage($tile)
        try {
            $tileGraphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
            $tileGraphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $tileGraphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
            $tileGraphics.Clear([Drawing.Color]::Transparent)
            $sourceTile = [Drawing.Rectangle]::new(($tileIndex % 2) * $tileWidth,
                [Math]::Floor($tileIndex / 2) * $tileHeight, $tileWidth, $tileHeight)
            $tileGraphics.DrawImage($spellAtlas, [Drawing.Rectangle]::new(2, 2, 60, 60),
                $sourceTile, [Drawing.GraphicsUnit]::Pixel)
            for ($pixelY = 0; $pixelY -lt 64; $pixelY++) {
                for ($pixelX = 0; $pixelX -lt 64; $pixelX++) {
                    $pixel = $tile.GetPixel($pixelX, $pixelY)
                    $alpha = if ($pixel.A -lt 64) { 0 } else { [Math]::Min(224, $pixel.A) }
                    $tile.SetPixel($pixelX, $pixelY,
                        [Drawing.Color]::FromArgb($alpha, $pixel.R, $pixel.G, $pixel.B))
                }
            }
            $tile.Save([IO.Path]::GetFullPath((Join-Path $OutputDirectory "ritual_spell_$($spells[$tileIndex]).png")),
                [Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $tileGraphics.Dispose()
            $tile.Dispose()
        }
    }
} finally {
    $spellAtlas.Dispose()
}
