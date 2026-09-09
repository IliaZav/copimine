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

$teal = [System.Drawing.Color]::FromArgb(99, 223, 160)
$copyBackground = [System.Drawing.Color]::FromArgb(245, 247, 249)

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
        $artWidth = 493 - $copyAreaX
        $graphics.SetClip([System.Drawing.Rectangle]::new($copyAreaX, 0, $artWidth, 312))
        $graphics.TranslateTransform($copyAreaX, 0)
        Draw-Cover $graphics $banner $artWidth 312
        $graphics.ResetTransform()
        $graphics.ResetClip()
        $graphics.FillRectangle((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(220, $copyBackground.R, $copyBackground.G, $copyBackground.B))), $copyAreaX, 0, $artWidth, 312)
        $graphics.FillRectangle((New-Object System.Drawing.SolidBrush($teal)), 0, 0, 4, 312)
        # Velopack renders only a narrow, fixed strip of this background next
        # to its standard copy. Keep the mark in the banner instead of
        # stretching it into a distorted vertical shape here.
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
}
