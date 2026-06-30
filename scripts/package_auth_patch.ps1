$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$releaseDir = Join-Path $repoRoot 'release'
$stageRoot = Join-Path $env:TEMP ("copimine-auth-patch-" + [guid]::NewGuid().ToString('N'))
$timestamp = Get-Date -Format 'yyyy-MM-dd-HHmmss'
$archiveName = "copimine-authme-patch-$timestamp.tar.gz"
$archivePath = Join-Path $releaseDir $archiveName
$scriptSource = Join-Path $repoRoot 'deploy\ubuntu\copimine_auth_patch.sh'
$scriptOut = Join-Path $releaseDir 'copimine_auth_patch.sh'
$infoOut = Join-Path $stageRoot 'PATCH_INFO.txt'
$payloadRoot = Join-Path $stageRoot 'copimine-auth-patch'
$pluginRoot = Join-Path $payloadRoot 'minecraft\server\plugins'

$requiredFiles = @(
  (Join-Path $repoRoot 'minecraft\server\plugins\AuthMe-5.6.0.jar'),
  (Join-Path $repoRoot 'minecraft\server\plugins\AuthEffects.jar'),
  $scriptSource
)

foreach ($file in $requiredFiles) {
  if (-not (Test-Path -LiteralPath $file)) {
    throw "Required file not found: $file"
  }
}

New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
if (Test-Path -LiteralPath $stageRoot) {
  Remove-Item -LiteralPath $stageRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $pluginRoot | Out-Null

try {
  Copy-Item -LiteralPath (Join-Path $repoRoot 'minecraft\server\plugins\AuthMe-5.6.0.jar') -Destination (Join-Path $pluginRoot 'AuthMe-5.6.0.jar') -Force
  Copy-Item -LiteralPath (Join-Path $repoRoot 'minecraft\server\plugins\AuthEffects.jar') -Destination (Join-Path $pluginRoot 'AuthEffects.jar') -Force
  Copy-Item -LiteralPath $scriptSource -Destination $scriptOut -Force

  $commit = (git -C $repoRoot rev-parse --short HEAD).Trim()
  $authMeHash = (Get-FileHash -Algorithm SHA256 (Join-Path $pluginRoot 'AuthMe-5.6.0.jar')).Hash.ToLowerInvariant()
  $authEffectsHash = (Get-FileHash -Algorithm SHA256 (Join-Path $pluginRoot 'AuthEffects.jar')).Hash.ToLowerInvariant()
  @(
    "CopiMine auth patch"
    "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')"
    "Commit: $commit"
    ""
    "Files:"
    "  minecraft/server/plugins/AuthMe-5.6.0.jar"
    "    sha256=$authMeHash"
    "  minecraft/server/plugins/AuthEffects.jar"
    "    sha256=$authEffectsHash"
    ""
    "Server apply:"
    "  sudo bash copimine_auth_patch.sh /home/<user>/$archiveName"
  ) | Set-Content -LiteralPath $infoOut -Encoding UTF8
  Copy-Item -LiteralPath $infoOut -Destination (Join-Path $payloadRoot 'PATCH_INFO.txt') -Force

  if (Test-Path -LiteralPath $archivePath) {
    Remove-Item -LiteralPath $archivePath -Force
  }
  tar -czf $archivePath -C $stageRoot 'copimine-auth-patch'
  if ($LASTEXITCODE -ne 0) {
    throw "tar packaging failed with exit code $LASTEXITCODE."
  }

  $archiveHash = (Get-FileHash -Algorithm SHA256 $archivePath).Hash.ToLowerInvariant()
  Set-Content -LiteralPath ($archivePath + '.sha256') -Encoding ascii -Value "$archiveHash *$archiveName"

  Write-Host "Created archive: $archivePath"
  Write-Host "Created hash: $($archivePath + '.sha256')"
  Write-Host "Copied script: $scriptOut"
}
finally {
  if (Test-Path -LiteralPath $stageRoot) {
    Remove-Item -LiteralPath $stageRoot -Recurse -Force
  }
}
