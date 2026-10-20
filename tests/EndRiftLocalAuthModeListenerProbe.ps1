$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'EndRiftLocalAuthMode.ps1')

$global:listenerProbeCallCount = 0
$global:listenerProbeMode = 'transient-empty'

function global:Get-NetTCPConnection {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)][int]$LocalPort,
    [Parameter(Mandatory = $true)][string]$State
  )

  $global:listenerProbeCallCount++
  if ($global:listenerProbeMode -eq 'always-empty' -or
      ($global:listenerProbeMode -eq 'transient-empty' -and $global:listenerProbeCallCount -le 2)) {
    return
  }

  $localAddress = if ($global:listenerProbeMode -eq 'wrong-address') { '0.0.0.0' } else { '127.0.0.1' }
  $owner = if ($global:listenerProbeMode -eq 'wrong-owner') { 99 } else { 42 }
  [pscustomobject]@{ LocalAddress = $localAddress; OwningProcess = $owner }
}

$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/')
$testRoot = Join-Path $tempRoot ('end-rift-listener-probe-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
try {
  $propertiesPath = Join-Path $testRoot 'server.properties'
  [IO.File]::WriteAllLines($propertiesPath, @('server-ip=127.0.0.1', 'rcon.ip=127.0.0.1'))

  $global:listenerProbeCallCount = 0
  $global:listenerProbeMode = 'transient-empty'
  $recovered = Assert-EndRiftLocalServerBind `
    -ServerDir $testRoot -ServerPort 25566 -RconPort 25576 -ExpectedProcessId 42
  if ($recovered.MinecraftListenerAddresses -notcontains '127.0.0.1' -or
      $recovered.RconListenerAddresses -notcontains '127.0.0.1' -or
      $global:listenerProbeCallCount -ne 4) {
    throw 'A single empty socket snapshot was not retried exactly once before accepting the expected loopback listeners.'
  }

  $global:listenerProbeCallCount = 0
  $global:listenerProbeMode = 'always-empty'
  $missingListenerError = ''
  try {
    Assert-EndRiftLocalServerBind `
      -ServerDir $testRoot -ServerPort 25566 -RconPort 25576 -ExpectedProcessId 42 | Out-Null
  } catch {
    $missingListenerError = $_.Exception.Message
  }
  if ($missingListenerError -notmatch '^No active local (?:Minecraft|RCON) listener was found\.$' -or
      $global:listenerProbeCallCount -ne 6) {
    throw 'A permanently missing listener did not fail closed after exactly three bounded snapshots.'
  }

  $global:listenerProbeCallCount = 0
  $global:listenerProbeMode = 'wrong-address'
  $wrongAddressError = ''
  try {
    Assert-EndRiftLocalServerBind `
      -ServerDir $testRoot -ServerPort 25566 -RconPort 25576 -ExpectedProcessId 42 | Out-Null
  } catch {
    $wrongAddressError = $_.Exception.Message
  }
  if ($wrongAddressError -notmatch 'listener is not bound to loopback' -or
      $global:listenerProbeCallCount -ne 2) {
    throw 'A non-loopback listener was not rejected immediately.'
  }

  $global:listenerProbeCallCount = 0
  $global:listenerProbeMode = 'wrong-owner'
  $wrongOwnerError = ''
  try {
    Assert-EndRiftLocalServerBind `
      -ServerDir $testRoot -ServerPort 25566 -RconPort 25576 -ExpectedProcessId 42 | Out-Null
  } catch {
    $wrongOwnerError = $_.Exception.Message
  }
  if ($wrongOwnerError -notmatch 'belongs to another process' -or
      $global:listenerProbeCallCount -ne 2) {
    throw 'A listener owned by a different process was not rejected immediately.'
  }

  Write-Output 'END_RIFT_LOCAL_LISTENER_PROBE_PASS'
} finally {
  $resolvedTestRoot = [IO.Path]::GetFullPath($testRoot)
  if (-not $resolvedTestRoot.StartsWith($tempRoot + [IO.Path]::DirectorySeparatorChar,
      [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refused to remove a listener probe folder outside the system temporary directory.'
  }
  if (Test-Path -LiteralPath $resolvedTestRoot) {
    Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
  }
}
