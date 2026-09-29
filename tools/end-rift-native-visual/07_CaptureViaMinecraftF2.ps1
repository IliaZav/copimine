param(
    [string]$Config = '',
    [string]$Output,
    [string]$Label = 'minecraft-f2',
    [ValidateRange(1,15)][int]$TimeoutSeconds = 8
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($scriptDirectory)) { throw 'Could not locate 07_CaptureViaMinecraftF2.ps1.' }
if ([string]::IsNullOrWhiteSpace($Config)) { $Config = Join-Path $scriptDirectory 'config.json' }
Import-Module (Join-Path $scriptDirectory 'lib\EndRift.NativeCapture.psm1') -Force

$c = Read-EndRiftConfig -Path $Config
$screenDir = Join-Path ([string]$c.ClientGameDirectory) 'screenshots'
if (-not (Test-Path -LiteralPath $screenDir)) {
    New-Item -ItemType Directory -Force -Path $screenDir | Out-Null
}
$before = @{}
Get-ChildItem -LiteralPath $screenDir -Filter '*.png' -File -ErrorAction SilentlyContinue | ForEach-Object {
    $before[$_.FullName] = $_.LastWriteTimeUtc.Ticks
}

$window = Get-MinecraftWindow -TitleRegex ([string]$c.MinecraftTitleRegex)
Send-WindowKeyChord -Window $window -Chord 'F2'

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$newFile = $null
while ((Get-Date) -lt $deadline -and $null -eq $newFile) {
    Start-Sleep -Milliseconds 200
    $candidate = Get-ChildItem -LiteralPath $screenDir -Filter '*.png' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if ($null -ne $candidate -and (-not $before.ContainsKey($candidate.FullName) -or
            $candidate.LastWriteTimeUtc.Ticks -gt $before[$candidate.FullName])) {
        $newFile = $candidate
    }
}
if ($null -eq $newFile) {
    throw "Minecraft F2 did not create a new screenshot in $screenDir within $TimeoutSeconds seconds."
}

if ([string]::IsNullOrWhiteSpace($Output)) {
    $dir = New-EndRiftEvidenceDirectory -Config $c -Suffix 'f2'
    $Output = Join-Path $dir ($Label + '.png')
}
$targetDir = Split-Path -Parent $Output
if ($targetDir -and -not (Test-Path -LiteralPath $targetDir)) { New-Item -ItemType Directory -Force -Path $targetDir | Out-Null }
Copy-Item -LiteralPath $newFile.FullName -Destination $Output -Force
$target = (Resolve-Path -LiteralPath $Output).Path
Write-Output "MINECRAFT_F2_CAPTURE source=$($newFile.FullName) output=$target sha256=$(Get-FileSha256 -Path $target)"
