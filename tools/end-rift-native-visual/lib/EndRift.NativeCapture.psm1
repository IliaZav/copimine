Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms

if (-not ('EndRift.NativeMethods' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;

namespace EndRift {
    public static class NativeMethods {
        public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

        [StructLayout(LayoutKind.Sequential)]
        public struct RECT {
            public int Left;
            public int Top;
            public int Right;
            public int Bottom;
        }

        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);

        [DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
        public static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

        [DllImport("user32.dll", SetLastError = true)]
        public static extern int GetWindowTextLength(IntPtr hWnd);

        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool IsWindowVisible(IntPtr hWnd);

        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool IsIconic(IntPtr hWnd);

        [DllImport("user32.dll", SetLastError = true)]
        public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);

        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);

        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool SetForegroundWindow(IntPtr hWnd);

        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool BringWindowToTop(IntPtr hWnd);

        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool PrintWindow(IntPtr hwnd, IntPtr hdcBlt, uint nFlags);

        [DllImport("user32.dll")]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool SetProcessDPIAware();

        [DllImport("user32.dll")]
        public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);
    }
}
'@
}

try { [void][EndRift.NativeMethods]::SetProcessDPIAware() } catch { }

function Read-EndRiftConfig {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string]$Path)

    $resolved = (Resolve-Path -LiteralPath $Path).Path
    $config = Get-Content -LiteralPath $resolved -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace([string]$config.Worktree)) {
        throw "Config.Worktree is required: $resolved"
    }
    return $config
}

function Get-EndRiftGitIdentity {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string]$Worktree)

    if (-not (Test-Path -LiteralPath $Worktree)) {
        throw "Worktree does not exist: $Worktree"
    }
    $branch = (& git -C $Worktree branch --show-current 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw "git branch failed in $Worktree" }
    $head = (& git -C $Worktree rev-parse HEAD 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw "git rev-parse failed in $Worktree" }
    $status = @(& git -C $Worktree status --short 2>$null)
    [pscustomobject]@{
        Branch = $branch
        Head = $head
        Dirty = ($status.Count -gt 0)
        Status = $status
    }
}

function Get-TopLevelWindows {
    [CmdletBinding()]
    param()

    $items = New-Object System.Collections.Generic.List[object]
    $callback = [EndRift.NativeMethods+EnumWindowsProc]{
        param([IntPtr]$hWnd, [IntPtr]$lParam)
        if (-not [EndRift.NativeMethods]::IsWindowVisible($hWnd)) { return $true }
        $length = [EndRift.NativeMethods]::GetWindowTextLength($hWnd)
        if ($length -le 0) { return $true }
        $builder = New-Object System.Text.StringBuilder ($length + 1)
        [void][EndRift.NativeMethods]::GetWindowText($hWnd, $builder, $builder.Capacity)
        $title = $builder.ToString()
        if ([string]::IsNullOrWhiteSpace($title)) { return $true }
        [uint32]$pid = 0
        [void][EndRift.NativeMethods]::GetWindowThreadProcessId($hWnd, [ref]$pid)
        $rect = New-Object EndRift.NativeMethods+RECT
        if (-not [EndRift.NativeMethods]::GetWindowRect($hWnd, [ref]$rect)) { return $true }
        $width = [Math]::Max(0, $rect.Right - $rect.Left)
        $height = [Math]::Max(0, $rect.Bottom - $rect.Top)
        $processName = ''
        try { $processName = (Get-Process -Id $pid -ErrorAction Stop).ProcessName } catch { }
        $items.Add([pscustomobject]@{
            Handle = $hWnd
            HandleInt64 = $hWnd.ToInt64()
            ProcessId = [int]$pid
            ProcessName = $processName
            Title = $title
            Left = $rect.Left
            Top = $rect.Top
            Width = $width
            Height = $height
            Minimized = [EndRift.NativeMethods]::IsIconic($hWnd)
            Area = [int64]$width * [int64]$height
        })
        return $true
    }
    [void][EndRift.NativeMethods]::EnumWindows($callback, [IntPtr]::Zero)
    return $items.ToArray()
}

function Get-MinecraftWindow {
    [CmdletBinding()]
    param(
        [string]$TitleRegex = '(?i)(minecraft|1\.21\.1)',
        [switch]$AllowJavaWithoutTitle
    )

    $candidates = @(Get-TopLevelWindows | Where-Object {
        $javaProcess = $_.ProcessName -match '^(?i:javaw|java)$'
        $titleMatch = $_.Title -match $TitleRegex
        $_.Width -ge 640 -and $_.Height -ge 360 -and
        (($javaProcess -and $titleMatch) -or ($AllowJavaWithoutTitle -and $javaProcess))
    } | Sort-Object Area -Descending)

    if ($candidates.Count -eq 0) {
        $visible = @(Get-TopLevelWindows | Sort-Object Area -Descending | Select-Object -First 20 |
            ForEach-Object { "PID=$($_.ProcessId) process=$($_.ProcessName) title=$($_.Title) size=$($_.Width)x$($_.Height)" })
        throw "Minecraft window was not found. Visible windows:`n$($visible -join [Environment]::NewLine)"
    }

    if ($candidates.Count -gt 1) {
        Write-Warning ("Multiple Minecraft-like windows found; choosing largest: " +
            (($candidates | ForEach-Object { "[$($_.ProcessId)] $($_.Title) $($_.Width)x$($_.Height)" }) -join '; '))
    }
    return $candidates[0]
}

function Set-WindowForeground {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)]$Window)

    $h = [IntPtr]::new([int64]$Window.HandleInt64)
    if ($Window.Minimized) {
        [void][EndRift.NativeMethods]::ShowWindowAsync($h, 9) # SW_RESTORE
        Start-Sleep -Milliseconds 300
    }
    [void][EndRift.NativeMethods]::BringWindowToTop($h)
    [void][EndRift.NativeMethods]::SetForegroundWindow($h)
    Start-Sleep -Milliseconds 250
}

function Get-CaptureQuality {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Bitmap)

    $samples = New-Object System.Collections.Generic.List[double]
    $colors = New-Object System.Collections.Generic.HashSet[string]
    $gridX = 24
    $gridY = 16
    for ($gy = 0; $gy -lt $gridY; $gy++) {
        $y = [Math]::Min($Bitmap.Height - 1, [int](($gy + 0.5) * $Bitmap.Height / $gridY))
        for ($gx = 0; $gx -lt $gridX; $gx++) {
            $x = [Math]::Min($Bitmap.Width - 1, [int](($gx + 0.5) * $Bitmap.Width / $gridX))
            $c = $Bitmap.GetPixel($x, $y)
            $brightness = (0.2126 * $c.R) + (0.7152 * $c.G) + (0.0722 * $c.B)
            $samples.Add($brightness)
            $key = ('{0:X1}{1:X1}{2:X1}' -f ([int]($c.R / 16)), ([int]($c.G / 16)), ([int]($c.B / 16)))
            [void]$colors.Add($key)
        }
    }
    $mean = ($samples | Measure-Object -Average).Average
    $min = ($samples | Measure-Object -Minimum).Minimum
    $max = ($samples | Measure-Object -Maximum).Maximum
    $sumSq = 0.0
    foreach ($v in $samples) { $sumSq += [Math]::Pow($v - $mean, 2) }
    $variance = if ($samples.Count -gt 0) { $sumSq / $samples.Count } else { 0.0 }
    $range = $max - $min
    $looksBlank = (($mean -lt 4.0 -and $range -lt 8.0) -or ($colors.Count -lt 4 -and $variance -lt 3.0))
    [pscustomobject]@{
        MeanBrightness = [Math]::Round($mean, 2)
        BrightnessRange = [Math]::Round($range, 2)
        Variance = [Math]::Round($variance, 2)
        ApproxUniqueColors = $colors.Count
        LooksBlank = [bool]$looksBlank
    }
}

function Save-WindowScreenshot {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]$Window,
        [Parameter(Mandatory = $true)][string]$OutputPath,
        [ValidateSet('Auto','PrintWindow','Screen')][string]$Mode = 'Auto'
    )

    $outputDir = Split-Path -Parent $OutputPath
    if ($outputDir -and -not (Test-Path -LiteralPath $outputDir)) {
        New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    }

    $width = [int]$Window.Width
    $height = [int]$Window.Height
    if ($width -lt 1 -or $height -lt 1) { throw "Invalid window bounds: ${width}x${height}" }
    $h = [IntPtr]::new([int64]$Window.HandleInt64)

    $captureMode = $Mode
    $quality = $null
    $bitmap = $null

    if ($Mode -in @('Auto','PrintWindow')) {
        $bitmap = [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $hdc = $graphics.GetHdc()
        try {
            $ok = [EndRift.NativeMethods]::PrintWindow($h, $hdc, 2)
        } finally {
            $graphics.ReleaseHdc($hdc)
            $graphics.Dispose()
        }
        if ($ok) { $quality = Get-CaptureQuality -Bitmap $bitmap }
        if ($ok -and -not $quality.LooksBlank) {
            $captureMode = 'PrintWindow'
        } elseif ($Mode -eq 'PrintWindow') {
            if ($bitmap) { $bitmap.Dispose() }
            throw "PrintWindow returned a blank/invalid OpenGL capture. Use -Mode Screen or Auto."
        } else {
            if ($bitmap) { $bitmap.Dispose(); $bitmap = $null }
        }
    }

    if ($null -eq $bitmap) {
        Set-WindowForeground -Window $Window
        $fresh = Get-MinecraftWindow -TitleRegex ([regex]::Escape([string]$Window.Title))
        $width = [int]$fresh.Width
        $height = [int]$fresh.Height
        $bitmap = [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.CopyFromScreen($fresh.Left, $fresh.Top, 0, 0,
                ([System.Drawing.Size]::new($width, $height)),
                [System.Drawing.CopyPixelOperation]::SourceCopy)
        } finally {
            $graphics.Dispose()
        }
        $quality = Get-CaptureQuality -Bitmap $bitmap
        if ($quality.LooksBlank) {
            $bitmap.Dispose()
            throw "Screen capture also looks blank. Ensure Minecraft is visible and not covered/minimized."
        }
        $captureMode = 'Screen'
    }

    try {
        $bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }

    [pscustomobject]@{
        Path = (Resolve-Path -LiteralPath $OutputPath).Path
        Mode = $captureMode
        WindowTitle = $Window.Title
        ProcessId = $Window.ProcessId
        Width = $width
        Height = $height
        CapturedAt = (Get-Date).ToString('o')
        Quality = $quality
    }
}

function Send-WindowKeyChord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]$Window,
        [Parameter(Mandatory = $true)][string]$Chord
    )

    $map = @{
        'F1' = [byte]0x70
        'F2' = [byte]0x71
        'F3' = [byte]0x72
        'F5' = [byte]0x74
        'B'  = [byte]0x42
        'ESC' = [byte]0x1B
    }
    $parts = @($Chord.ToUpperInvariant().Split('+') | Where-Object { $_ })
    if ($parts.Count -eq 0) { throw "Empty key chord" }
    foreach ($part in $parts) {
        if (-not $map.ContainsKey($part)) { throw "Unsupported key in chord '$Chord': $part" }
    }

    Set-WindowForeground -Window $Window

    # keybd_event with a zero scan code is ignored by some Java/OpenGL
    # windows.  That made the old F2 path report success without creating a
    # Minecraft framebuffer screenshot.  SendKeys goes through the active
    # desktop input queue and is reliable for the small key vocabulary used
    # by this harness.  Keep the Win32 path as a fallback for environments
    # where Windows Forms cannot attach to the interactive desktop.
    $sendKeys = foreach ($part in $parts) {
        switch ($part) {
            'F1'  { '{F1}' }
            'F2'  { '{F2}' }
            'F3'  { '{F3}' }
            'F5'  { '{F5}' }
            'ESC' { '{ESC}' }
            'B'   { 'b' }
        }
    }
    try {
        [System.Windows.Forms.SendKeys]::SendWait(($sendKeys -join ''))
    } catch {
        foreach ($part in $parts) { [EndRift.NativeMethods]::keybd_event($map[$part], 0, 0, [UIntPtr]::Zero) }
        Start-Sleep -Milliseconds 120
        for ($i = $parts.Count - 1; $i -ge 0; $i--) {
            [EndRift.NativeMethods]::keybd_event($map[$parts[$i]], 0, 2, [UIntPtr]::Zero)
        }
    }
    Start-Sleep -Milliseconds 200
}

function New-ContactSheet {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string[]]$Images,
        [Parameter(Mandatory = $true)][string]$OutputPath,
        [int]$Columns = 3,
        [int]$CellWidth = 480,
        [int]$LabelHeight = 34
    )

    $valid = @($Images | Where-Object { Test-Path -LiteralPath $_ })
    if ($valid.Count -eq 0) { throw "No images for contact sheet." }
    $columns = [Math]::Max(1, $Columns)
    $rows = [int][Math]::Ceiling($valid.Count / [double]$columns)
    $cellHeight = [int]($CellWidth * 9 / 16) + $LabelHeight
    $sheet = [System.Drawing.Bitmap]::new($columns * $CellWidth, $rows * $cellHeight)
    $gfx = [System.Drawing.Graphics]::FromImage($sheet)
    $gfx.Clear([System.Drawing.Color]::FromArgb(24,24,24))
    $font = [System.Drawing.Font]::new('Segoe UI', 12)
    $brush = [System.Drawing.Brushes]::White
    try {
        for ($i = 0; $i -lt $valid.Count; $i++) {
            $img = [System.Drawing.Image]::FromFile($valid[$i])
            try {
                $col = $i % $columns
                $row = [int][Math]::Floor($i / $columns)
                $x = $col * $CellWidth
                $y = $row * $cellHeight
                $targetHeight = $cellHeight - $LabelHeight
                $scale = [Math]::Min($CellWidth / [double]$img.Width, $targetHeight / [double]$img.Height)
                $drawW = [int]($img.Width * $scale)
                $drawH = [int]($img.Height * $scale)
                $dx = $x + [int](($CellWidth - $drawW) / 2)
                $dy = $y + [int](($targetHeight - $drawH) / 2)
                $gfx.DrawImage($img, $dx, $dy, $drawW, $drawH)
                $label = Split-Path -Leaf $valid[$i]
                $gfx.DrawString($label, $font, $brush, $x + 6, $y + $targetHeight + 6)
            } finally { $img.Dispose() }
        }
        $dir = Split-Path -Parent $OutputPath
        if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
        $sheet.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $font.Dispose()
        $gfx.Dispose()
        $sheet.Dispose()
    }
    return (Resolve-Path -LiteralPath $OutputPath).Path
}

function Invoke-EndRiftRcon {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]$Config,
        [Parameter(Mandatory = $true)][string]$CommandText
    )

    $script = [string]$Config.RconScript
    $serverDir = [string]$Config.ServerDir
    $port = [int]$Config.RconPort
    if (-not (Test-Path -LiteralPath $script)) { throw "RCON helper not found: $script" }
    if (-not (Test-Path -LiteralPath $serverDir)) { throw "Server directory not found: $serverDir" }
    $output = & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $script `
        -ServerDir $serverDir -RconPort $port -CommandText $CommandText 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "RCON failed [$CommandText]: $($output | Out-String)"
    }
    return ($output | Out-String).Trim()
}

function New-EndRiftEvidenceDirectory {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]$Config,
        [string]$Suffix = 'native-auto'
    )
    $git = Get-EndRiftGitIdentity -Worktree ([string]$Config.Worktree)
    $root = if ([System.IO.Path]::IsPathRooted([string]$Config.EvidenceRoot)) {
        [string]$Config.EvidenceRoot
    } else {
        Join-Path ([string]$Config.Worktree) ([string]$Config.EvidenceRoot)
    }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $short = $git.Head.Substring(0, [Math]::Min(12, $git.Head.Length))
    $dir = Join-Path $root ("$stamp-$short-$Suffix")
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    return (Resolve-Path -LiteralPath $dir).Path
}

function Get-FileSha256 {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Expand-EndRiftTokens {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [hashtable]$Variables = @{}
    )
    $value = $Text
    foreach ($key in $Variables.Keys) {
        $value = $value.Replace('{' + [string]$key + '}', [string]$Variables[$key])
    }
    return $value
}

Export-ModuleMember -Function Read-EndRiftConfig,Get-EndRiftGitIdentity,Get-TopLevelWindows,Get-MinecraftWindow,Set-WindowForeground,Save-WindowScreenshot,Send-WindowKeyChord,New-ContactSheet,Invoke-EndRiftRcon,New-EndRiftEvidenceDirectory,Get-FileSha256,Expand-EndRiftTokens
