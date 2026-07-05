param(
    [string]$ArchivePath = "",
    [string]$Server = "",
    [string]$User = "",
    [string]$RemoteDir = "",
    [string]$InstallerPath = "",
    [string]$VerifyScriptPath = "",
    [string]$ReleaseManifestPath = ""
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    throw $Message
}

function Need-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Fail "Не найдена команда: $Name"
    }
}

function Resolve-RequiredPath([string]$PathValue, [string]$Label) {
    if (-not $PathValue) {
        Fail "Не указан путь: $Label"
    }
    if (-not (Test-Path -LiteralPath $PathValue)) {
        Fail "$Label не найден: $PathValue"
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
    & ssh $Target $CommandText
    if ($LASTEXITCODE -ne 0) {
        Fail "SSH-команда завершилась ошибкой: $CommandText"
    }
}

function Invoke-Scp([string[]]$Arguments) {
    & scp @Arguments
    if ($LASTEXITCODE -ne 0) {
        Fail "SCP завершился ошибкой."
    }
}

Need-Command "ssh"
Need-Command "scp"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

if (-not $InstallerPath) {
    $InstallerPath = Join-Path $ProjectRoot "deploy\ubuntu\copimine_full_replace.sh"
}
if (-not $VerifyScriptPath) {
    $VerifyScriptPath = Join-Path $ProjectRoot "deploy\ubuntu\verify.sh"
}
if (-not $ReleaseManifestPath) {
    $ReleaseManifestPath = Join-Path $ProjectRoot "deploy\release_manifest.json"
}

$ArchivePath = Prompt-Value "Путь к архиву релиза" $ArchivePath
$Server = Prompt-Value "Сервер или IP" $Server
$User = Prompt-Value "Пользователь SSH" $User

if (-not $RemoteDir) {
    $RemoteDir = "/home/$User/copimine-upload"
}

$ArchivePath = Resolve-RequiredPath $ArchivePath "Архив релиза"
$InstallerPath = Resolve-RequiredPath $InstallerPath "Ubuntu installer"
$VerifyScriptPath = Resolve-RequiredPath $VerifyScriptPath "Verify script"
$ReleaseManifestPath = Resolve-RequiredPath $ReleaseManifestPath "Release manifest"

$ArchiveName = [System.IO.Path]::GetFileName($ArchivePath)
$ArchiveSha256 = Read-Sha256 $ArchivePath
$ArchiveSize = Read-Size $ArchivePath
$ShaFileLocal = "$ArchivePath.sha256"
$ReadmeLocal = Join-Path ([System.IO.Path]::GetTempPath()) ("copimine-upload-readme-" + [guid]::NewGuid().ToString() + ".txt")

@(
    $ArchiveSha256 + "  " + $ArchiveName
) | Set-Content -LiteralPath $ShaFileLocal -Encoding ascii

$InstallerRemoteName = "copimine_full_replace.sh"
$VerifyRemoteName = "copimine_verify.sh"
$ManifestRemoteName = "release_manifest.json"
$ReadmeRemoteName = "UPLOAD_README.txt"

$ReadmeContent = @"
CopiMine release upload

Archive: $ArchiveName
SHA256: $ArchiveSha256
Size: $ArchiveSize bytes

Запуск на сервере:
  cd $RemoteDir
  chmod +x $InstallerRemoteName $VerifyRemoteName
  sudo bash ./$InstallerRemoteName "$RemoteDir/$ArchiveName"

Если есть отдельный dump БД:
  sudo bash ./$InstallerRemoteName "$RemoteDir/$ArchiveName" "/path/to/copimine.dump"

Проверка после установки:
  sudo bash ./$VerifyRemoteName
"@
Set-Content -LiteralPath $ReadmeLocal -Value $ReadmeContent -Encoding UTF8

$SshTarget = "${User}@${Server}"

Write-Host "[1/6] Создаю каталог на сервере: $RemoteDir"
Invoke-Ssh $SshTarget "mkdir -p '$RemoteDir'"

Write-Host "[2/6] Передаю архив и служебные файлы"
Invoke-Scp @(
    $ArchivePath,
    $ShaFileLocal,
    $InstallerPath,
    $VerifyScriptPath,
    $ReleaseManifestPath,
    $ReadmeLocal,
    "${SshTarget}:$RemoteDir/"
)

Write-Host "[3/6] Нормализую имена файлов на сервере"
$RemoteArchive = "$RemoteDir/$ArchiveName"
$RemoteSha = "$RemoteArchive.sha256"
$RemoteInstaller = "$RemoteDir/$InstallerRemoteName"
$RemoteVerify = "$RemoteDir/$VerifyRemoteName"
$RemoteManifest = "$RemoteDir/$ManifestRemoteName"
$RemoteReadme = "$RemoteDir/$ReadmeRemoteName"

Invoke-Ssh $SshTarget @"
set -e
cd '$RemoteDir'
mv -f '$(Split-Path -Leaf $InstallerPath)' '$InstallerRemoteName'
mv -f '$(Split-Path -Leaf $VerifyScriptPath)' '$VerifyRemoteName'
mv -f '$(Split-Path -Leaf $ReleaseManifestPath)' '$ManifestRemoteName'
mv -f '$(Split-Path -Leaf $ReadmeLocal)' '$ReadmeRemoteName'
chmod 644 '$ArchiveName' '$ArchiveName.sha256' '$ManifestRemoteName' '$ReadmeRemoteName'
chmod 755 '$InstallerRemoteName' '$VerifyRemoteName'
"@

Write-Host "[4/6] Проверяю размер и SHA256 на сервере"
$RemoteSize = (& ssh $SshTarget "stat -c %s '$RemoteArchive'").Trim()
if (-not $RemoteSize) {
    Fail "Не удалось получить размер удалённого файла."
}
if ([int64]$RemoteSize -ne [int64]$ArchiveSize) {
    Fail "Размер архива после передачи не совпадает. Локально: $ArchiveSize, удалённо: $RemoteSize"
}
$RemoteShaValue = (& ssh $SshTarget "sha256sum '$RemoteArchive' | awk '{print \$1}'").Trim().ToLowerInvariant()
if ($RemoteShaValue -ne $ArchiveSha256) {
    Fail "SHA256 после передачи не совпадает. Локально: $ArchiveSha256, удалённо: $RemoteShaValue"
}

Write-Host "[5/6] Проверяю, что installer и verify доступны"
Invoke-Ssh $SshTarget "test -f '$RemoteInstaller' && test -x '$RemoteInstaller' && test -f '$RemoteVerify' && test -x '$RemoteVerify'"

Write-Host "[6/6] Готово"
Write-Host ""
Write-Host "Архив успешно передан."
Write-Host "Удалённый каталог: $RemoteDir"
Write-Host "SHA256: $ArchiveSha256"
Write-Host ""
Write-Host "Команда установки:"
Write-Host "  ssh $SshTarget `"cd $RemoteDir && sudo bash ./$InstallerRemoteName '$RemoteArchive'`""
Write-Host ""
Write-Host "Команда проверки после установки:"
Write-Host "  ssh $SshTarget `"cd $RemoteDir && sudo bash ./$VerifyRemoteName`""

Remove-Item -LiteralPath $ReadmeLocal -Force -ErrorAction SilentlyContinue
