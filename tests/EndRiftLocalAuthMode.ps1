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

  $serverIpAssignments = @(
    $ServerPropertiesLines | Where-Object { $_ -match '^server-ip=' }
  )
  if ($serverIpAssignments.Count -ne 1 -or $serverIpAssignments[0] -cne 'server-ip=127.0.0.1') {
    throw 'The local no-auth smoke requires exactly one explicit server-ip=127.0.0.1 setting.'
  }
  return $true
}
