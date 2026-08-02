param([string]$ProjectRoot = "", [string]$ReleaseDir = "")
$ErrorActionPreference = "Stop"
if (-not $ProjectRoot) { $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..") }
if (-not $ReleaseDir) { $ReleaseDir = Join-Path (Split-Path -Parent (Split-Path -Parent $ProjectRoot)) "release\backups" }
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null
$stamp = Get-Date -Format "yyyy-MM-dd-HHmmss"
$zip = Join-Path $ReleaseDir "copimine-backup-$stamp.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
$staging = Join-Path ([IO.Path]::GetTempPath()) ("copimine-backup-" + [guid]::NewGuid().ToString())

function Test-ExcludedBackupPath {
    param([string]$Name, [bool]$Directory)
    if ($Name -in @('.git', '.venv', 'node_modules', '__pycache__', 'logs', 'backups', 'data', '.cache')) { return $true }
    if ($Directory) { return $false }
    if ($Name -in @('.env', '.env.local', '.env.production', 'id_rsa', 'id_ed25519')) { return $true }
    if ($Name -match '(^|\.)(pem|key|p12|pfx|secret|secrets)$') { return $true }
    if ($Name -match '(password|passwd|credential|token)') { return $true }
    if ($Name -match '\.((db|sqlite)(-wal|-shm)?)$' -or $Name -match '\.dump$') { return $true }
    return $false
}

function Copy-RedactedBackupTree {
    param([string]$Source, [string]$Destination, [string]$Relative = '')
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    foreach ($item in (Get-ChildItem -LiteralPath $Source -Force)) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { continue }
        $relativeName = if ($Relative) { Join-Path $Relative $item.Name } else { $item.Name }
        if (Test-ExcludedBackupPath -Name $item.Name -Directory $item.PSIsContainer) { continue }
        $destination = Join-Path $Destination $item.Name
        if ($item.PSIsContainer) {
            Copy-RedactedBackupTree -Source $item.FullName -Destination $destination -Relative $relativeName
        } else {
            Copy-Item -LiteralPath $item.FullName -Destination $destination -Force
        }
    }
}

try {
    $payload = Join-Path $staging "copimine"
    New-Item -ItemType Directory -Force -Path $payload | Out-Null
    Copy-RedactedBackupTree -Source $ProjectRoot -Destination $payload
    $metadata = [ordered]@{
        createdAtUtc = (Get-Date).ToUniversalTime().ToString('o')
        redacted = @('.env', 'runtime data/backups/logs', 'private keys and certificates', 'database dumps and SQLite files', 'credential-named files')
        note = 'Secrets and mutable runtime state are intentionally excluded. Restore database and protected runtime configuration from their dedicated recovery procedures.'
    }
    $metadata | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $payload 'deploy\backup-redaction.json') -Encoding UTF8
    Compress-Archive -Path $payload -DestinationPath $zip -CompressionLevel Optimal
    (Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash.ToLowerInvariant() | Set-Content -LiteralPath "$zip.sha256" -Encoding ascii
}
finally {
    if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue }
}
Write-Host "Backup: $zip"
Write-Host "SHA256: $zip.sha256"
