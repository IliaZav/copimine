param(
    [string]$Server = "qwerty@176.209.210.252",
    [int]$Port = 2222,
    [string]$RemoteDir = "/home/qwerty/copimine-upload",
    [string]$ReleaseDir = "",
    [string]$Archive = ""
)

$ErrorActionPreference = "Stop"

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Command not found: $Name"
    }
}

Require-Command ssh
Require-Command scp

function Invoke-NativeChecked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
    }
}

if (-not $Archive) {
    if (-not $ReleaseDir) {
        $repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
        $workspaceRoot = Split-Path -Parent (Split-Path -Parent $repoRoot)
        $ReleaseDir = Join-Path $workspaceRoot "release"
    }
    $latest = Get-ChildItem -LiteralPath $ReleaseDir -Filter "copimine-opt-full-*.tar.gz" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $latest) {
        throw "Release archive not found in $ReleaseDir"
    }
    $Archive = $latest.FullName
}

$archivePath = Resolve-Path -LiteralPath $Archive
$archiveName = Split-Path -Leaf $archivePath
$shaPath = "$archivePath.sha256"
$bootstrapName = $archiveName -replace "\.gz$", ".bootstrap.json"
$bootstrapPath = Join-Path (Split-Path -Parent $archivePath) $bootstrapName

if (-not (Test-Path -LiteralPath $shaPath)) {
    throw "SHA256 sidecar not found: $shaPath"
}
if (-not (Test-Path -LiteralPath $bootstrapPath)) {
    throw "Bootstrap manifest not found: $bootstrapPath"
}

$expected = (Get-Content -LiteralPath $shaPath -Raw).Trim().Split()[0].ToLowerInvariant()
$actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash.ToLowerInvariant()
if ($expected -ne $actual) {
    throw "Local SHA256 mismatch. Expected $expected, got $actual"
}

Write-Host "Archive: $archivePath"
Write-Host "SHA256:  $actual"
Write-Host "Server:  $Server"
Write-Host "Remote:  $RemoteDir"

Invoke-NativeChecked -FilePath ssh -Arguments @("-p", "$Port", $Server, "mkdir -p '$RemoteDir'")
Invoke-NativeChecked -FilePath scp -Arguments @("-P", "$Port", $archivePath, "${Server}:$RemoteDir/")
Invoke-NativeChecked -FilePath scp -Arguments @("-P", "$Port", $shaPath, "${Server}:$RemoteDir/")
Invoke-NativeChecked -FilePath scp -Arguments @("-P", "$Port", $bootstrapPath, "${Server}:$RemoteDir/")

$remoteCheck = "cd '$RemoteDir' && sha256sum '$archiveName' && cat '$archiveName.sha256' && test -f '$bootstrapName'"
Invoke-NativeChecked -FilePath ssh -Arguments @("-p", "$Port", $Server, $remoteCheck)

Write-Host ""
Write-Host "Upload complete. On Ubuntu run:"
Write-Host "  cd $RemoteDir"
Write-Host "  sudo bash /opt/copimine/deploy/ubuntu/copimine_full_replace.sh $RemoteDir/$archiveName"
