param(
    [string]$ArchivePath = "",
    [string]$Server = "",
    [string]$User = "",
    [string]$RemoteDir = "",
    [string]$InstallerPath = "",
    [string]$UnpackScriptPath = "",
    [string]$CommonScriptPath = "",
    [string]$VerifyScriptPath = "",
    [string]$ReleaseManifestPath = "",
    [string]$BootstrapManifestPath = ""
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    throw $Message
}

function Need-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Fail "Command not found: $Name"
    }
}

function Resolve-RequiredPath([string]$PathValue, [string]$Label) {
    if (-not $PathValue) {
        Fail "Missing path: $Label"
    }
    if (-not (Test-Path -LiteralPath $PathValue)) {
        Fail "$Label not found: $PathValue"
    }
    return (Resolve-Path -LiteralPath $PathValue).Path
}

function Prompt-Value([string]$Prompt, [string]$Current) {
    if ($Current) {
        return $Current
    }
    return (Read-Host $Prompt).Trim()
}

function Read-Sha256([string]$PathValue) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $PathValue).Hash.ToLowerInvariant()
}

function Read-Size([string]$PathValue) {
    return (Get-Item -LiteralPath $PathValue).Length
}

function Invoke-Ssh([string]$Target, [string]$CommandText) {
    # PowerShell here-strings use the Windows CRLF convention.  Bash on the
    # Ubuntu host treats the trailing carriage return as part of each token
    # (for example `/home/qwerty/copimine-upload\r`), so normalize every
    # remote command to LF before handing it to SSH.
    $normalized = $CommandText -replace "`r`n", "`n" -replace "`r", "`n"
    & ssh $Target $normalized
    if ($LASTEXITCODE -ne 0) {
        Fail "SSH command failed: $CommandText"
    }
}

function Invoke-Scp([string[]]$Arguments) {
    & scp @Arguments
    if ($LASTEXITCODE -ne 0) {
        Fail "SCP failed."
    }
}

Need-Command "ssh"
Need-Command "scp"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ReleaseScriptDir = $PSScriptRoot

function Resolve-DefaultHelperPath {
    param(
        [Parameter(Mandatory = $true)][string]$ReleaseRelative,
        [Parameter(Mandatory = $true)][string]$ProjectRelative
    )
    $releaseCandidate = Join-Path $ReleaseScriptDir $ReleaseRelative
    if (Test-Path -LiteralPath $releaseCandidate) {
        return $releaseCandidate
    }
    return (Join-Path $ProjectRoot $ProjectRelative)
}

if (-not $InstallerPath) {
    $InstallerPath = Resolve-DefaultHelperPath -ReleaseRelative "copimine_full_replace.sh" -ProjectRelative "deploy\ubuntu\copimine_full_replace.sh"
}
if (-not $UnpackScriptPath) {
    $UnpackScriptPath = Resolve-DefaultHelperPath -ReleaseRelative "copimine_unpack_and_verify.sh" -ProjectRelative "deploy\ubuntu\copimine_unpack_and_verify.sh"
}
if (-not $CommonScriptPath) {
    $CommonScriptPath = Resolve-DefaultHelperPath -ReleaseRelative "copimine_common.sh" -ProjectRelative "deploy\shared\common.sh"
}
if (-not $VerifyScriptPath) {
    $VerifyScriptPath = Resolve-DefaultHelperPath -ReleaseRelative "copimine_verify.sh" -ProjectRelative "deploy\ubuntu\verify.sh"
}
if (-not $ReleaseManifestPath) {
    $ReleaseManifestPath = Resolve-DefaultHelperPath -ReleaseRelative "release_manifest.json" -ProjectRelative "deploy\release_manifest.json"
}

$ArchivePath = Prompt-Value "Path to release archive" $ArchivePath
$Server = Prompt-Value "Server or IP" $Server
$User = Prompt-Value "SSH user" $User

if (-not $RemoteDir) {
    $RemoteDir = "/home/$User/copimine-upload"
}

$ArchivePath = Resolve-RequiredPath $ArchivePath "Release archive"
if (-not $BootstrapManifestPath) {
    $bootstrapCandidate = [System.IO.Path]::ChangeExtension($ArchivePath, ".bootstrap.json")
    if (Test-Path -LiteralPath $bootstrapCandidate) {
        $BootstrapManifestPath = $bootstrapCandidate
    }
}
$InstallerPath = Resolve-RequiredPath $InstallerPath "Ubuntu full replace script"
$UnpackScriptPath = Resolve-RequiredPath $UnpackScriptPath "Ubuntu unpack script"
$CommonScriptPath = Resolve-RequiredPath $CommonScriptPath "Shared deploy helper"
$VerifyScriptPath = Resolve-RequiredPath $VerifyScriptPath "Ubuntu verify script"
$ReleaseManifestPath = Resolve-RequiredPath $ReleaseManifestPath "Release manifest"
if ($BootstrapManifestPath) {
    $BootstrapManifestPath = Resolve-RequiredPath $BootstrapManifestPath "Bootstrap manifest"
}

$ArchiveName = [System.IO.Path]::GetFileName($ArchivePath)
$ArchiveSha256 = Read-Sha256 $ArchivePath
$ArchiveSize = Read-Size $ArchivePath
$ShaFileLocal = "$ArchivePath.sha256"
$ReadmeLocal = Join-Path ([System.IO.Path]::GetTempPath()) ("copimine-upload-readme-" + [guid]::NewGuid().ToString() + ".txt")

@(
    $ArchiveSha256 + "  " + $ArchiveName
) | Set-Content -LiteralPath $ShaFileLocal -Encoding ascii

$InstallerRemoteName = "copimine_full_replace.sh"
$UnpackRemoteName = "copimine_unpack_and_verify.sh"
$CommonRemoteName = "copimine_common.sh"
$VerifyRemoteName = "copimine_verify.sh"
$ManifestRemoteName = "release_manifest.json"
$BootstrapRemoteName = "release.bootstrap.json"
$ReadmeRemoteName = "UPLOAD_README.txt"

$ReadmeContent = @"
CopiMine release upload

Archive: $ArchiveName
SHA256: $ArchiveSha256
Size: $ArchiveSize bytes

Recommended deploy:
  cd $RemoteDir
  chmod +x $InstallerRemoteName $UnpackRemoteName $VerifyRemoteName
  sudo bash ./$UnpackRemoteName "$RemoteDir/$ArchiveName" "$ArchiveSha256"

Full replace:
  sudo bash ./$InstallerRemoteName "$RemoteDir/$ArchiveName"

With external DB dump:
  sudo bash ./$UnpackRemoteName "$RemoteDir/$ArchiveName" "$ArchiveSha256" "/path/to/copimine.dump"

Post-deploy verify:
  sudo bash ./$VerifyRemoteName
"@
Set-Content -LiteralPath $ReadmeLocal -Value $ReadmeContent -Encoding UTF8

$SshTarget = "${User}@${Server}"
$UploadItems = @(
    $ArchivePath,
    $ShaFileLocal,
    $InstallerPath,
    $UnpackScriptPath,
    $CommonScriptPath,
    $VerifyScriptPath,
    $ReleaseManifestPath
)
if ($BootstrapManifestPath) {
    $UploadItems += $BootstrapManifestPath
}
$UploadItems += @(
    $ReadmeLocal,
    "${SshTarget}:$RemoteDir/"
)

Write-Host "[1/6] Creating remote directory: $RemoteDir"
Invoke-Ssh $SshTarget "mkdir -p '$RemoteDir'"

Write-Host "[2/6] Uploading archive and helper files"
Invoke-Scp $UploadItems

Write-Host "[3/6] Normalizing remote filenames"
$RemoteArchive = "$RemoteDir/$ArchiveName"
$RemoteInstaller = "$RemoteDir/$InstallerRemoteName"
$RemoteUnpack = "$RemoteDir/$UnpackRemoteName"
$RemoteCommon = "$RemoteDir/$CommonRemoteName"
$RemoteVerify = "$RemoteDir/$VerifyRemoteName"
$RemoteManifest = "$RemoteDir/$ManifestRemoteName"
$RemoteBootstrap = "$RemoteDir/$BootstrapRemoteName"
$BootstrapLeaf = if ($BootstrapManifestPath) { Split-Path -Leaf $BootstrapManifestPath } else { "" }

$RenameScript = @"
set -e
cd '$RemoteDir'
if [ '$(Split-Path -Leaf $InstallerPath)' != '$InstallerRemoteName' ]; then mv -f '$(Split-Path -Leaf $InstallerPath)' '$InstallerRemoteName'; fi
if [ '$(Split-Path -Leaf $UnpackScriptPath)' != '$UnpackRemoteName' ]; then mv -f '$(Split-Path -Leaf $UnpackScriptPath)' '$UnpackRemoteName'; fi
if [ '$(Split-Path -Leaf $CommonScriptPath)' != '$CommonRemoteName' ]; then mv -f '$(Split-Path -Leaf $CommonScriptPath)' '$CommonRemoteName'; fi
if [ '$(Split-Path -Leaf $VerifyScriptPath)' != '$VerifyRemoteName' ]; then mv -f '$(Split-Path -Leaf $VerifyScriptPath)' '$VerifyRemoteName'; fi
if [ '$(Split-Path -Leaf $ReleaseManifestPath)' != '$ManifestRemoteName' ]; then mv -f '$(Split-Path -Leaf $ReleaseManifestPath)' '$ManifestRemoteName'; fi
if [ -n '$BootstrapLeaf' ] && [ -f '$BootstrapLeaf' ]; then mv -f '$BootstrapLeaf' '$BootstrapRemoteName'; fi
mv -f '$(Split-Path -Leaf $ReadmeLocal)' '$ReadmeRemoteName'
chmod 644 '$ArchiveName' '$ArchiveName.sha256' '$ManifestRemoteName' '$ReadmeRemoteName'
if [ -f '$BootstrapRemoteName' ]; then chmod 644 '$BootstrapRemoteName'; fi
chmod 755 '$InstallerRemoteName' '$UnpackRemoteName' '$CommonRemoteName' '$VerifyRemoteName'
"@
Invoke-Ssh $SshTarget $RenameScript

Write-Host "[4/6] Checking remote size and SHA256"
$RemoteSize = (& ssh $SshTarget "stat -c %s '$RemoteArchive'").Trim()
if (-not $RemoteSize) {
    Fail "Could not read remote archive size."
}
if ([int64]$RemoteSize -ne [int64]$ArchiveSize) {
    Fail "Remote archive size mismatch. Local: $ArchiveSize Remote: $RemoteSize"
}
$RemoteShaValue = (& ssh $SshTarget "sha256sum '$RemoteArchive' | awk '{print \$1}'").Trim().ToLowerInvariant()
if ($RemoteShaValue -ne $ArchiveSha256) {
    Fail "Remote archive SHA256 mismatch. Local: $ArchiveSha256 Remote: $RemoteShaValue"
}

Write-Host "[5/6] Checking remote deploy scripts"
Invoke-Ssh $SshTarget "test -f '$RemoteInstaller' && test -x '$RemoteInstaller' && test -f '$RemoteUnpack' && test -x '$RemoteUnpack' && test -f '$RemoteCommon' && test -x '$RemoteCommon' && test -f '$RemoteVerify' && test -x '$RemoteVerify'"

Write-Host "[6/6] Done"
Write-Host ""
Write-Host "Archive upload completed."
Write-Host "Remote dir: $RemoteDir"
Write-Host "SHA256: $ArchiveSha256"
Write-Host ""

$UnpackCommand = 'ssh ' + $SshTarget + ' "cd ' + $RemoteDir + ' && sudo bash ./' + $UnpackRemoteName + " '$RemoteArchive' '$ArchiveSha256'" + '"'
$ReplaceCommand = 'ssh ' + $SshTarget + ' "cd ' + $RemoteDir + ' && sudo bash ./' + $InstallerRemoteName + " '$RemoteArchive'" + '"'
$VerifyCommand = 'ssh ' + $SshTarget + ' "cd ' + $RemoteDir + ' && sudo bash ./' + $VerifyRemoteName + '"'

Write-Host "Recommended deploy command:"
Write-Host ("  " + $UnpackCommand)
Write-Host ""
Write-Host "Full replace command:"
Write-Host ("  " + $ReplaceCommand)
Write-Host ""
Write-Host "Post-deploy verify command:"
Write-Host ("  " + $VerifyCommand)

Remove-Item -LiteralPath $ReadmeLocal -Force -ErrorAction SilentlyContinue
