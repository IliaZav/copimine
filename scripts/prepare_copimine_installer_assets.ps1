[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $SourceLogo,
    [Parameter(Mandatory = $true)]
    [string] $DestinationRoot
)

$ErrorActionPreference = 'Stop'

$source = (Resolve-Path -LiteralPath $SourceLogo -ErrorAction Stop).Path
$destination = [System.IO.Path]::GetFullPath($DestinationRoot)
New-Item -ItemType Directory -Path $destination -Force | Out-Null

Add-Type -AssemblyName System.Drawing

$navy = [System.Drawing.Color]::FromArgb(9, 20, 30)
$panel = [System.Drawing.Color]::FromArgb(18, 35, 50)
$teal = [System.Drawing.Color]::FromArgb(99, 223, 160)
$muted = [System.Drawing.Color]::FromArgb(177, 199, 206)
$fontFamily = New-Object System.Drawing.FontFamily('Segoe UI')

function Draw-Logo([System.Drawing.Graphics] $graphics, [System.Drawing.Image] $logo, [int] $x, [int] $y, [int] $size) {
    $graphics.DrawImage($logo, [System.Drawing.Rectangle]::new($x, $y, $size, $size))
}

function New-Banner([string] $path) {
    $bitmap = New-Object System.Drawing.Bitmap(493, 58, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear($navy)
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.FillRectangle((New-Object System.Drawing.SolidBrush($panel)), 0, 0, 493, 58)
        $graphics.FillRectangle((New-Object System.Drawing.SolidBrush($teal)), 0, 0, 5, 58)
        Draw-Logo $graphics $logo 18 8 42
        $graphics.DrawString('COPIMINE LAUNCHER', (New-Object System.Drawing.Font($fontFamily, 13, [System.Drawing.FontStyle]::Bold)), (New-Object System.Drawing.SolidBrush($teal)), 76, 10)
        $graphics.DrawString('Minecraft 1.21.1 · Fabric', (New-Object System.Drawing.Font($fontFamily, 9, [System.Drawing.FontStyle]::Regular)), (New-Object System.Drawing.SolidBrush($muted)), 77, 32)
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
        $graphics.Clear($navy)
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.FillRectangle((New-Object System.Drawing.SolidBrush($panel)), 0, 0, 493, 312)
        Draw-Logo $graphics $logo 34 40 152
        $graphics.DrawString('COPIMINE', (New-Object System.Drawing.Font($fontFamily, 22, [System.Drawing.FontStyle]::Bold)), (New-Object System.Drawing.SolidBrush($teal)), 214, 86)
        $graphics.DrawString('Launcher', (New-Object System.Drawing.Font($fontFamily, 16, [System.Drawing.FontStyle]::Regular)), (New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)), 216, 123)
        $graphics.DrawString('Игра и моды установятся в выбранную папку.', (New-Object System.Drawing.Font($fontFamily, 9, [System.Drawing.FontStyle]::Regular)), (New-Object System.Drawing.SolidBrush($muted)), 214, 164)
        $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Bmp)
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$logo = [System.Drawing.Image]::FromFile($source)
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
    $logo.Dispose()
    $fontFamily.Dispose()
}
