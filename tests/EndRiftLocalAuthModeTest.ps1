$ErrorActionPreference = 'Stop'

$helperPath = Join-Path $PSScriptRoot 'EndRiftLocalAuthMode.ps1'
if (Test-Path -LiteralPath $helperPath -PathType Leaf) {
  . $helperPath
}

$authModeFunction = Get-Command 'Test-EndRiftLocalAuthMeEnabled' -ErrorAction SilentlyContinue
if ($null -eq $authModeFunction) {
  throw 'Missing Test-EndRiftLocalAuthMeEnabled helper.'
}

$serverBindFunction = Get-Command 'Test-EndRiftLocalServerBind' -ErrorAction SilentlyContinue
if ($null -eq $serverBindFunction) {
  throw 'Missing Test-EndRiftLocalServerBind helper.'
}

$sectionSign = [string][char]0x00A7
$hexPrefix = $sectionSign + 'x' + (($sectionSign + '0') * 6)
$cases = @(
  @{
    Label = 'AuthMe loaded'
    PluginList = ("Paper Plugins: (0):`nBukkit Plugins: (2): " + [char]0x00A7 + 'aAuthMe' + [char]0x00A7 + 'r, LuckPerms')
    Expected = $true
  },
  @{
    Label = 'AuthMe in wrapped RGB plugin list'
    PluginList = ($hexPrefix + "Paper Plugins: (0):`nBukkit Plugins: (2):`n " + $sectionSign + '8 - ' + $sectionSign + 'aAuthMe, LuckPerms')
    Expected = $true
  },
  @{
    Label = 'AuthMe disabled'
    PluginList = "Paper Plugins: (0):`nBukkit Plugins: (2): AuthEffects, LuckPerms"
    Expected = $false
  },
  @{
    Label = 'similar plugin name is not AuthMe'
    PluginList = "Bukkit Plugins: (1): NotAuthMe"
    Expected = $false
  }
)

foreach ($case in $cases) {
  $actual = Test-EndRiftLocalAuthMeEnabled -PluginListOutput $case.PluginList
  if ($actual -ne $case.Expected) {
    throw "$($case.Label): expected AuthMeEnabled=$($case.Expected), got $actual"
  }
  Write-Output "PASS $($case.Label)"
}

$invalidCases = @(
  @{ Label = 'empty plugin list is unknown'; PluginList = '' },
  @{ Label = 'unrecognized plugin response is unknown'; PluginList = 'No plugins matched. AuthMe was disabled for the local server.' }
)

foreach ($case in $invalidCases) {
  $threw = $false
  try {
    $null = Test-EndRiftLocalAuthMeEnabled -PluginListOutput $case.PluginList
  } catch {
    $threw = $true
  }
  if (-not $threw) {
    throw "$($case.Label): expected an error for an unknown plugin list response."
  }
  Write-Output "PASS $($case.Label)"
}

$bindCases = @(
  @{
    Label = 'explicit IPv4 loopback bind accepted'
    Lines = @('online-mode=false', 'server-ip=127.0.0.1', 'rcon.ip=127.0.0.1', 'server-port=25566', 'rcon.port=25576')
    ShouldThrow = $false
  },
  @{
    Label = 'empty server-ip bind rejected'
    Lines = @('online-mode=false', 'server-ip=', 'server-port=25566')
    ShouldThrow = $true
  },
  @{
    Label = 'wildcard server-ip bind rejected'
    Lines = @('online-mode=false', 'server-ip=0.0.0.0', 'server-port=25566')
    ShouldThrow = $true
  },
  @{
    Label = 'missing server-ip bind rejected'
    Lines = @('online-mode=false', 'server-port=25566')
    ShouldThrow = $true
  },
  @{
    Label = 'duplicate server-ip entries rejected'
    Lines = @('server-ip=127.0.0.1', 'server-ip=', 'rcon.ip=127.0.0.1', 'server-port=25566', 'rcon.port=25576')
    ShouldThrow = $true
  },
  @{
    Label = 'missing RCON bind rejected'
    Lines = @('server-ip=127.0.0.1', 'server-port=25566', 'rcon.port=25576')
    ShouldThrow = $true
  },
  @{
    Label = 'wildcard RCON bind rejected'
    Lines = @('server-ip=127.0.0.1', 'rcon.ip=0.0.0.0', 'server-port=25566', 'rcon.port=25576')
    ShouldThrow = $true
  },
  @{
    Label = 'duplicate RCON bind entries rejected'
    Lines = @('server-ip=127.0.0.1', 'rcon.ip=127.0.0.1', 'rcon.ip=', 'server-port=25566', 'rcon.port=25576')
    ShouldThrow = $true
  }
)

foreach ($case in $bindCases) {
  $threw = $false
  try {
    $null = Test-EndRiftLocalServerBind -ServerPropertiesLines $case.Lines
  } catch {
    $threw = $true
  }
  if ($threw -ne $case.ShouldThrow) {
    throw "$($case.Label): expected ShouldThrow=$($case.ShouldThrow), got $threw"
  }
  Write-Output "PASS $($case.Label)"
}

$listenerCases = @(
  @{
    Label = 'Minecraft and RCON IPv4 loopback listeners accepted'
    Minecraft = @('127.0.0.1')
    Rcon = @('127.0.0.1')
    ShouldThrow = $false
  },
  @{
    Label = 'IPv6 loopback listeners accepted'
    Minecraft = @('::1')
    Rcon = @('::1')
    ShouldThrow = $false
  },
  @{
    Label = 'wildcard Minecraft listener rejected'
    Minecraft = @('0.0.0.0')
    Rcon = @('127.0.0.1')
    ShouldThrow = $true
  },
  @{
    Label = 'external RCON listener rejected'
    Minecraft = @('127.0.0.1')
    Rcon = @('192.0.2.10')
    ShouldThrow = $true
  },
  @{
    Label = 'missing active listener rejected'
    Minecraft = @()
    Rcon = @('127.0.0.1')
    ShouldThrow = $true
  }
)

foreach ($case in $listenerCases) {
  $threw = $false
  try {
    $null = Test-EndRiftLocalListenerAddresses `
      -MinecraftListenerAddresses $case.Minecraft `
      -RconListenerAddresses $case.Rcon
  } catch {
    $threw = $true
  }
  if ($threw -ne $case.ShouldThrow) {
    throw "$($case.Label): expected ShouldThrow=$($case.ShouldThrow), got $threw"
  }
  Write-Output "PASS $($case.Label)"
}
