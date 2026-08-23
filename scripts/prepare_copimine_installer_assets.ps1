[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $SourceLogo,
    [Parameter(Mandatory = $true)]
    [string] $SourceBanner,
    [Parameter(Mandatory = $true)]
    [string] $DestinationRoot
)

$ErrorActionPreference = 'Stop'

$source = (Resolve-Path -LiteralPath $SourceLogo -ErrorAction Stop).Path
$bannerSource = (Resolve-Path -LiteralPath $SourceBanner -ErrorAction Stop).Path
$destination = [System.IO.Path]::GetFullPath($DestinationRoot)
New-Item -ItemType Directory -Path $destination -Force | Out-Null

Add-Type -AssemblyName System.Drawing

$navy = [System.Drawing.Color]::FromArgb(9, 20, 30)
$teal = [System.Drawing.Color]::FromArgb(99, 223, 160)
$copyBackground = [System.Drawing.Color]::FromArgb(245, 247, 249)
$copyInk = [System.Drawing.Color]::FromArgb(27, 40, 49)
$copyMuted = [System.Drawing.Color]::FromArgb(79, 96, 106)
$fontFamily = New-Object System.Drawing.FontFamily('Segoe UI')

function Draw-Logo([System.Drawing.Graphics] $graphics, [System.Drawing.Image] $logo, [int] $x, [int] $y, [int] $size) {
    $sourceWidth = [Math]::Min($logo.Width, $logo.Height)
    $sourceX = [int](($logo.Width - $sourceWidth) / 2)
    $sourceY = [int](($logo.Height - $sourceWidth) / 2)
    $destination = [System.Drawing.Rectangle]::new($x, $y, $size, $size)
    $sourceRectangle = [System.Drawing.Rectangle]::new($sourceX, $sourceY, $sourceWidth, $sourceWidth)
    $graphics.DrawImage($logo, $destination, $sourceRectangle.X, $sourceRectangle.Y, $sourceRectangle.Width, $sourceRectangle.Height, [System.Drawing.GraphicsUnit]::Pixel)
}

function Draw-Cover([System.Drawing.Graphics] $graphics, [System.Drawing.Image] $image, [int] $width, [int] $height) {
    $scale = [Math]::Max($width / [double]$image.Width, $height / [double]$image.Height)
    $drawWidth = [int][Math]::Ceiling($image.Width * $scale)
    $drawHeight = [int][Math]::Ceiling($image.Height * $scale)
    $x = [int](($width - $drawWidth) / 2)
    $y = [int](($height - $drawHeight) / 2)
    $graphics.DrawImage($image, [System.Drawing.Rectangle]::new($x, $y, $drawWidth, $drawHeight))
}

function New-Banner([string] $path) {
    $bitmap = New-Object System.Drawing.Bitmap(493, 58, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        # Velopack draws its standard black installer copy on top of this
        # bitmap. Keep the whole surface light; a dark banner makes every
        # welcome/readme step unreadable.
        $graphics.Clear($copyBackground)
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.DrawImage($banner, [System.Drawing.Rectangle]::new(362, 0, 131, 58))
        $graphics.FillRectangle((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(215, $copyBackground.R, $copyBackground.G, $copyBackground.B))), 362, 0, 131, 58)
        $graphics.FillRectangle((New-Object System.Drawing.SolidBrush($teal)), 0, 0, 5, 58)
        Draw-Logo $graphics $logo 18 8 42
        $graphics.DrawString('COPIMINE LAUNCHER', (New-Object System.Drawing.Font($fontFamily, 13, [System.Drawing.FontStyle]::Bold)), (New-Object System.Drawing.SolidBrush($copyInk)), 76, 10)
        $graphics.DrawString('Minecraft 1.21.1 · Fabric', (New-Object System.Drawing.Font($fontFamily, 9, [System.Drawing.FontStyle]::Regular)), (New-Object System.Drawing.SolidBrush($copyMuted)), 77, 32)
        $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Bmp)
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function New-LogoPanel([string] $path) {
    $bitmap = New-Object System.Drawing.Bitmap(493, 312, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        # The MSI renderer places its own text over this image. Do not draw a
        # second text block here and do not put a dark background under the
        # renderer's black copy.
        $copyAreaX = 176
        $graphics.Clear($copyBackground)
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.SetClip([System.Drawing.Rectangle]::new(0, 0, $copyAreaX, 312))
        Draw-Cover $graphics $banner $copyAreaX 312
        $graphics.ResetClip()
        $graphics.FillRectangle((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(190, $navy.R, $navy.G, $navy.B))), 0, 0, $copyAreaX, 312)
        $graphics.FillRectangle((New-Object System.Drawing.SolidBrush($teal)), ($copyAreaX - 4), 0, 4, 312)
        Draw-Logo $graphics $logo 28 82 118
        $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Bmp)
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$logo = [System.Drawing.Image]::FromFile($source)
$banner = [System.Drawing.Image]::FromFile($bannerSource)
try {
    $bannerPath = Join-Path $destination 'installer-banner.bmp'
    $logoPath = Join-Path $destination 'installer-logo.bmp'
    New-Banner $bannerPath
    New-LogoPanel $logoPath

    foreach ($output in @($bannerPath, $logoPath)) {
        if (-not (Test-Path -LiteralPath $output -PathType Leaf)) {
            throw "Installer asset was not produced: $output"
        }
    }

    Write-Output "BANNER_OUTPUT=$bannerPath"
    Write-Output "LOGO_OUTPUT=$logoPath"
}
finally {
    $banner.Dispose()
    $logo.Dispose()
    $fontFamily.Dispose()
}
