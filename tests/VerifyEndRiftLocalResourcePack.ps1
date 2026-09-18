[CmdletBinding()]
param(
  [string]$ServerDir = '',
  [int]$ResourcePackPort = 8092
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$worktreeRoot = (Resolve-Path (Join-Path $scriptRoot '..')).Path
$localRuntimeRoot = (Resolve-Path (Join-Path $worktreeRoot 'local-runtime')).Path
if ([string]::IsNullOrWhiteSpace($ServerDir)) {
  $ServerDir = Join-Path $localRuntimeRoot 'end-rift-server'
}
$ServerDir = (Resolve-Path $ServerDir).Path
$localPrefix = $localRuntimeRoot.TrimEnd('\') + '\'
if (-not $ServerDir.StartsWith($localPrefix, [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Refused to inspect a server outside this worktree local-runtime directory.'
}

$pack = Join-Path $worktreeRoot 'resourcepacks\build\CopiMineResourcePack.zip'
$propertiesPath = Join-Path $ServerDir 'server.properties'
if (-not (Test-Path -LiteralPath $pack -PathType Leaf)) { throw "Resource pack is missing: $pack" }
if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) { throw "Local server.properties is missing: $propertiesPath" }

$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
  if ($line -match '^([^#=]+)=(.*)$') { $properties[$matches[1].Trim()] = $matches[2].Trim() }
}
$expectedSha1 = (Get-FileHash -LiteralPath $pack -Algorithm SHA1).Hash.ToLowerInvariant()
if ($properties['require-resource-pack'] -ne 'true') { throw 'Local server must require the event resource pack.' }
$resourcePackUrl = $properties['resource-pack'] -replace '\\:', ':'
if ($resourcePackUrl -ne "http://127.0.0.1:$ResourcePackPort/CopiMineResourcePack.zip") {
  throw 'Local server resource-pack URL is not the isolated loopback URL.'
}
if ($properties['resource-pack-sha1'].ToLowerInvariant() -ne $expectedSha1) {
  throw "Local server resource-pack-sha1 does not match $expectedSha1."
}
Write-Output "Local resource-pack contract PASS sha1=$expectedSha1 url=$resourcePackUrl"
