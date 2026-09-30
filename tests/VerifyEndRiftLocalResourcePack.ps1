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
if ($properties['resource-pack-sha1'].ToLowerInvariant() -ne $expectedSha1) {
  throw "Local server resource-pack-sha1 does not match $expectedSha1."
}
$uri = $null
$validUri = [Uri]::TryCreate($resourcePackUrl, [UriKind]::Absolute, [ref]$uri)
if (-not $validUri -or $uri.Scheme -ne 'http' -or $uri.Port -ne $ResourcePackPort -or $uri.AbsolutePath -ne '/CopiMineResourcePack.zip') {
  throw 'Local server resource-pack URL must use the expected HTTP endpoint and port.'
}
$hostAddresses = @()
if ($uri.Host -eq 'localhost') {
  $hostAddresses = @('127.0.0.1', '::1')
} else {
  $parsedAddress = $null
  if ([Net.IPAddress]::TryParse($uri.Host, [ref]$parsedAddress)) {
    $hostAddresses = @($parsedAddress.IPAddressToString)
  } else {
    $hostAddresses = @([Net.Dns]::GetHostAddresses($uri.Host) | ForEach-Object {
      $_.IPAddressToString
    })
  }
}
$localAddresses = @('127.0.0.1', '::1') + @(
  Get-NetIPAddress -AddressState Preferred -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty IPAddress
)
if (-not (@($hostAddresses | Where-Object { $localAddresses -contains $_ }).Count)) {
  throw 'Resource-pack URL host is not a local address on this machine.'
}
Add-Type -AssemblyName System.Net.Http
$client = [Net.Http.HttpClient]::new()
try {
  $bytes = $client.GetByteArrayAsync($uri).GetAwaiter().GetResult()
} finally {
  $client.Dispose()
}
$sha1Algorithm = [Security.Cryptography.SHA1]::Create()
try {
  $downloadSha1 = [BitConverter]::ToString($sha1Algorithm.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant()
} finally {
  $sha1Algorithm.Dispose()
}
if ($downloadSha1 -ne $expectedSha1) {
  throw "Resource-pack endpoint content hash mismatch. Expected=$expectedSha1 Actual=$downloadSha1"
}
Write-Output "Local resource-pack contract PASS sha1=$expectedSha1 bytes=$($bytes.Length) url=$resourcePackUrl"
