$ErrorActionPreference = 'Stop'

$server = (Resolve-Path (Join-Path $PSScriptRoot '..\minecraft\server')).Path
$releaseRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$latestLog = Join-Path $server 'logs\latest.log'
$propertiesPath = Join-Path $server 'server.properties'

function Get-CopiMineEnvPath {
  if (-not [string]::IsNullOrWhiteSpace($env:COPIMINE_ENV_FILE)) {
    return $env:COPIMINE_ENV_FILE
  }
  return (Join-Path $releaseRoot 'admin-web\.env')
}

function Test-PostgresPasswordConfigured {
  param(
    [Parameter(Mandatory = $true)][string]$EnvPath
  )

  if (-not (Test-Path -LiteralPath $EnvPath)) {
    return $false
  }

  $line = Get-Content -LiteralPath $EnvPath -ErrorAction Stop |
    Where-Object { $_ -match '^\s*POSTGRES_PASSWORD\s*=' } |
    Select-Object -First 1

  if ([string]::IsNullOrWhiteSpace($line)) {
    return $false
  }

  $value = ($line -split '=', 2)[1].Trim().Trim('"').Trim("'")
  return -not [string]::IsNullOrWhiteSpace($value) -and $value -ne 'CHANGE_ME'
}

function Get-ServerProperties {
  $props = @{}
  foreach ($line in Get-Content -LiteralPath $propertiesPath -ErrorAction Stop) {
    if ($line.TrimStart().StartsWith('#') -or $line -notmatch '=') {
      continue
    }

    $key, $value = $line -split '=', 2
    $props[$key.Trim()] = $value.Trim()
  }

  return $props
}

function Send-RconCommand {
  param(
    [Parameter(Mandatory = $true)][string]$Command
  )

  $props = Get-ServerProperties
  $password = $props['rcon.password']
  $portValue = $props['rcon.port']
  if ([string]::IsNullOrWhiteSpace($portValue)) {
    $portValue = '25575'
  }
  $port = [int]$portValue
  if ([string]::IsNullOrWhiteSpace($password)) {
    throw 'RCON password is not configured.'
  }

  $client = [System.Net.Sockets.TcpClient]::new()
  $client.Connect('127.0.0.1', $port)
  $stream = $client.GetStream()

  function Write-Packet {
    param([int]$Id, [int]$Type, [string]$Body)
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $payloadLength = 4 + 4 + $bodyBytes.Length + 2
    $bytes = [byte[]]::new(4 + $payloadLength)
    [BitConverter]::GetBytes($payloadLength).CopyTo($bytes, 0)
    [BitConverter]::GetBytes($Id).CopyTo($bytes, 4)
    [BitConverter]::GetBytes($Type).CopyTo($bytes, 8)
    $bodyBytes.CopyTo($bytes, 12)
    $stream.Write($bytes, 0, $bytes.Length)
  }

  function Read-Exact {
    param([int]$Length)
    $buffer = [byte[]]::new($Length)
    $offset = 0
    while ($offset -lt $Length) {
      $read = $stream.Read($buffer, $offset, $Length - $offset)
      if ($read -le 0) {
        throw 'RCON connection closed unexpectedly.'
      }
      $offset += $read
    }
    return $buffer
  }

  function Read-Packet {
    $sizeBytes = Read-Exact 4
    $size = [BitConverter]::ToInt32($sizeBytes, 0)
    $payload = Read-Exact $size
    return [pscustomobject]@{
      Id = [BitConverter]::ToInt32($payload, 0)
      Type = [BitConverter]::ToInt32($payload, 4)
    }
  }

  try {
    Write-Packet 7001 3 $password
    $auth = Read-Packet
    if ($auth.Id -eq -1) {
      throw 'RCON authentication failed.'
    }

    Write-Packet 7002 2 $Command
    [void](Read-Packet)
  } finally {
    $stream.Dispose()
    $client.Dispose()
  }
}

$existing = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -match 'purpur\.jar' -and $_.CommandLine -match [regex]::Escape($server) }
if ($existing) {
  throw "A Purpur server process is already running for $server."
}

$copiMineEnv = Get-CopiMineEnvPath
if (-not (Test-PostgresPasswordConfigured -EnvPath $copiMineEnv)) {
  throw "Server smoke requires a real POSTGRES_PASSWORD in $copiMineEnv. CopiMineUltimateAdminPlus owns PostgreSQL/PIN/bridge, so Artifacts and Narcotics intentionally stop when this secret is missing."
}
$env:COPIMINE_ENV_FILE = $copiMineEnv

if (Test-Path -LiteralPath $latestLog) {
  Remove-Item -LiteralPath $latestLog -Force
}

$process = Start-Process `
  -FilePath 'java' `
  -ArgumentList @('--add-modules=jdk.incubator.vector', '-Xms512M', '-Xmx1536M', '-jar', 'purpur.jar', '--nogui') `
  -WorkingDirectory $server `
  -PassThru `
  -WindowStyle Hidden

$loaded = $false
$stopRequested = $false
$forcedKill = $false

try {
  $deadline = (Get-Date).AddSeconds(150)
  while (-not $process.HasExited -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    if (-not (Test-Path -LiteralPath $latestLog)) {
      continue
    }

    $tail = Get-Content -LiteralPath $latestLog -Tail 160
    if ($tail -match 'Done \([0-9\.,]+s\)!') {
      $loaded = $true
      break
    }
  }

  if ($loaded -and -not $process.HasExited) {
    $settleDeadline = (Get-Date).AddSeconds(35)
    while (-not $process.HasExited -and (Get-Date) -lt $settleDeadline) {
      $tail = Get-Content -LiteralPath $latestLog -Tail 120
      if ($tail -match 'ImageFrame\] Data loading completed') {
        break
      }
      Start-Sleep -Seconds 2
    }

    Send-RconCommand -Command 'stop'
    $stopRequested = $true
  }

  if (-not $process.WaitForExit(180000)) {
    if (-not $stopRequested) {
      try {
        Send-RconCommand -Command 'stop'
        $stopRequested = $true
      } catch {}
    }
  }
} finally {
  if (-not $process.HasExited) {
    if (-not $stopRequested) {
      try { Send-RconCommand -Command 'stop' } catch {}
    }
    if (-not $process.WaitForExit(30000)) {
      Stop-Process -Id $process.Id -Force
      $process.WaitForExit()
      $forcedKill = $true
    }
  }
}

$lines = Get-Content -LiteralPath $latestLog
$interesting = $lines |
  Where-Object { $_ -match 'CopiMine|ERROR|WARN|Done \(|Exception|Duplicate|Could not load|Could not pass|Enabling|Disabling|All dimensions are saved' } |
  Select-Object -Last 300

$interesting

if (-not $loaded) {
  throw 'Server did not reach the Done state during smoke test.'
}

$fatalLines = $lines | Where-Object {
  ($_ -match 'Could not load|Could not pass event|Duplicate command') -or
  ($_ -match '\[.+/ERROR\]:')
}

# EssentialsX 2.21.x logs this on Purpur 1.21.1. Official EssentialsX stable
# currently targets newer Paper versions, so this is a version-policy warning,
# not a CopiMine load/runtime failure.
$allowedFatalLines = $fatalLines | Where-Object {
  $_ -match '\[Essentials\]' -and $_ -match 'unsupported server version|[^\x00-\x7F]+'
}
$unallowedFatalLines = $fatalLines | Where-Object {
  -not ($_ -match '\[Essentials\]' -and $_ -match 'unsupported server version|[^\x00-\x7F]+')
}

if ($unallowedFatalLines.Count -gt 0) {
  $unallowedFatalLines | Select-Object -First 40
  throw 'Server smoke test reached Done but emitted unallowed load/runtime errors.'
}

if ($allowedFatalLines.Count -gt 0) {
  Write-Host 'Known compatibility warning: EssentialsX reports unsupported server version on Purpur 1.21.1.'
}

if ($forcedKill) {
  throw 'Server smoke test had to force-kill the Java process during shutdown.'
}

if ($process.ExitCode -ne 0) {
  throw "Server smoke test stopped with non-zero ExitCode=$($process.ExitCode)."
}

Write-Host "Server smoke test passed. ExitCode=$($process.ExitCode)"
