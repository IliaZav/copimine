function Test-EndRiftLocalAuthMeEnabled {
  [CmdletBinding()]
  param(
    [AllowNull()]
    [AllowEmptyString()]
    [string]$PluginListOutput
  )

  if ([string]::IsNullOrWhiteSpace($PluginListOutput)) {
    throw 'The server returned an empty plugin list; AuthMe state cannot be determined safely.'
  }

  $plainOutput = $PluginListOutput -replace "\x1B\[[0-?]*[ -/]*[@-~]", ''
  $plainOutput = $plainOutput -replace '(?i)\u00A7x(?:\u00A7[0-9a-f]){6}', ''
  $plainOutput = $plainOutput -replace '[\u00A7&][0-9A-FK-ORa-fk-or]', ''
  $pluginLists = [Regex]::Matches(
    $plainOutput,
    '(?im)^[ \t]*(?:(?:Paper|Bukkit)[ \t]+)?Plugins[ \t]*(?:\([ \t]*\d+[ \t]*\))?[ \t]*:[ \t]*(?:\([ \t]*\d+[ \t]*\)[ \t]*:[ \t]*)?(?<names>[^\r\n]*(?:\r?\n(?![ \t]*(?:(?:Paper|Bukkit)[ \t]+)?Plugins(?:[ \t]*\([ \t]*\d+[ \t]*\))?[ \t]*:)[^\r\n]*)*)'
  )

  if ($pluginLists.Count -eq 0) {
    throw 'The server plugin list response was not recognized; AuthMe state cannot be determined safely.'
  }

  foreach ($pluginList in $pluginLists) {
    foreach ($pluginName in $pluginList.Groups['names'].Value.Split(',')) {
      $normalizedName = $pluginName.Trim() -replace '^(?:-|\u2022)[ \t]*', ''
      if ($normalizedName -ieq 'AuthMe') {
        return $true
      }
    }
  }

  return $false
}

function Test-EndRiftLocalServerBind {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)]
    [AllowNull()]
    [AllowEmptyCollection()]
    [string[]]$ServerPropertiesLines
  )

  foreach ($property in @('server-ip', 'rcon.ip')) {
    $assignments = @(
      $ServerPropertiesLines | Where-Object { $_ -match ('^' + [Regex]::Escape($property) + '=') }
    )
    if ($assignments.Count -ne 1 -or $assignments[0] -cne ($property + '=127.0.0.1')) {
      throw "The local probes require exactly one explicit $property=127.0.0.1 setting."
    }
  }
  return $true
}

function Test-EndRiftLocalListenerAddresses {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)]
    [AllowEmptyCollection()]
    [string[]]$MinecraftListenerAddresses,
    [Parameter(Mandatory = $true)]
    [AllowEmptyCollection()]
    [string[]]$RconListenerAddresses
  )

  foreach ($listener in @(
      @{ Name = 'Minecraft'; Addresses = $MinecraftListenerAddresses },
      @{ Name = 'RCON'; Addresses = $RconListenerAddresses }
    )) {
    if ($listener.Addresses.Count -eq 0) {
      throw "No active local $($listener.Name) listener was found."
    }
    foreach ($address in $listener.Addresses) {
      $parsedAddress = $null
      if (-not [System.Net.IPAddress]::TryParse($address, [ref]$parsedAddress) -or
          -not [System.Net.IPAddress]::IsLoopback($parsedAddress)) {
        throw "$($listener.Name) listener is not bound to loopback: $address"
      }
    }
  }
  return $true
}

function Assert-EndRiftLocalServerBind {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)]
    [string]$ServerDir,
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$ServerPort,
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$RconPort
  )

  $propertiesPath = Join-Path $ServerDir 'server.properties'
  if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
    throw "Local server.properties is missing: $propertiesPath"
  }
  $propertiesLines = Get-Content -LiteralPath $propertiesPath
  Test-EndRiftLocalServerBind -ServerPropertiesLines $propertiesLines | Out-Null

  $minecraftListeners = @(
    Get-NetTCPConnection -LocalPort $ServerPort -State Listen -ErrorAction SilentlyContinue |
      Select-Object -ExpandProperty LocalAddress -Unique
  )
  $rconListeners = @(
    Get-NetTCPConnection -LocalPort $RconPort -State Listen -ErrorAction SilentlyContinue |
      Select-Object -ExpandProperty LocalAddress -Unique
  )
  Test-EndRiftLocalListenerAddresses `
    -MinecraftListenerAddresses $minecraftListeners `
    -RconListenerAddresses $rconListeners | Out-Null
  return [pscustomobject]@{
    MinecraftListenerAddresses = $minecraftListeners
    RconListenerAddresses = $rconListeners
  }
}
