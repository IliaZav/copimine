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

function Assert-EndRiftLocalAuthMeDisabled {
  [CmdletBinding()]
  param(
    [AllowNull()]
    [AllowEmptyString()]
    [string]$PluginListOutput
  )

  if (Test-EndRiftLocalAuthMeEnabled -PluginListOutput $PluginListOutput) {
    throw 'Refused: End Rift local probes require AuthMe to be disabled; no probe accounts or credentials will be created.'
  }
  return $true
}

function Test-EndRiftLocalPlayerNamesOnline {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)]
    [AllowEmptyString()]
    [string]$PlayerListOutput,
    [Parameter(Mandatory = $true)]
    [ValidateCount(1, 128)]
    [string[]]$RequiredNames
  )

  if ([string]::IsNullOrWhiteSpace($PlayerListOutput)) { return $false }
  foreach ($name in $RequiredNames) {
    $pattern = '(?<![A-Za-z0-9_])' + [Regex]::Escape($name) + '(?![A-Za-z0-9_])'
    if ($PlayerListOutput -notmatch $pattern) { return $false }
  }
  return $true
}

function Assert-EndRiftLocalConfiguredPorts {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)]
    [AllowNull()]
    [AllowEmptyCollection()]
    [string[]]$ServerPropertiesLines,
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$ServerPort,
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$RconPort
  )

  if ($ServerPort -eq $RconPort) {
    throw 'Minecraft and RCON must use distinct local ports.'
  }
  foreach ($entry in @(
      @{ Property = 'server-port'; Expected = $ServerPort },
      @{ Property = 'rcon.port'; Expected = $RconPort }
    )) {
    $assignments = @(
      $ServerPropertiesLines | Where-Object { $_ -match ('^' + [Regex]::Escape($entry.Property) + '=') }
    )
    if ($assignments.Count -ne 1) {
      throw "Local server.properties must contain a single configured value for $($entry.Property)."
    }
    $configured = 0
    if (-not [int]::TryParse($assignments[0].Substring($entry.Property.Length + 1), [ref]$configured) -or
        $configured -ne $entry.Expected) {
      throw "Local server.properties $($entry.Property) must equal $($entry.Expected)."
    }
  }
  return $true
}

function Assert-EndRiftLocalPortsAvailable {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)]
    [ValidateCount(1, 16)]
    [int[]]$Ports
  )

  foreach ($port in $Ports) {
    if ($port -lt 1 -or $port -gt 65535) {
      throw "Invalid local probe port: $port"
    }
    $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count -gt 0) {
      throw "Refused to start because local port $port is already listening."
    }
  }
  return $true
}

function Get-EndRiftLocalBotSafeEnvironmentVariableNames {
  return @(
    'PATH', 'SYSTEMROOT', 'WINDIR', 'COMSPEC', 'PATHEXT', 'TEMP', 'TMP',
    'USERPROFILE', 'LOCALAPPDATA', 'APPDATA', 'HOMEDRIVE', 'HOMEPATH', 'OS',
    'PROCESSOR_ARCHITECTURE', 'PROCESSOR_IDENTIFIER', 'NUMBER_OF_PROCESSORS',
    'END_RIFT_ATTACK_INTERVAL_MS', 'END_RIFT_BOSS_UUID', 'END_RIFT_BOT_CONTROL_DIRECTORY',
    'END_RIFT_BOT_HOST', 'END_RIFT_BOT_NODE_MODULES', 'END_RIFT_BOT_PORT',
    'END_RIFT_BOT_PROTOCOL_VERSION', 'END_RIFT_BOT_SKIP_AUTH',
    'END_RIFT_BOT_SKIP_REGISTER', 'END_RIFT_BOT_WAVE7_AUTOPILOT',
    'END_RIFT_BOT_WAVE7_HOLD_POSITION', 'END_RIFT_GUARDIAN_PROBE_NAMES',
    'END_RIFT_OBELISK_TARGETS', 'END_RIFT_REFLECT_DIAGNOSTICS',
    'END_RIFT_REFLECT_ENABLED', 'END_RIFT_REFLECT_START_MS',
    'END_RIFT_TEST_CLIENT_MOD_HANDSHAKE', 'END_RIFT_TEST_CLIENT_MOD_VERSION',
    'END_RIFT_TENTACLE_PROBE_MAX_ATTACKS', 'END_RIFT_TENTACLE_PROBE_NAMES',
    'END_RIFT_TENTACLE_PROBE_TARGETS_FILE', 'END_RIFT_TENTACLE_TRACE',
    'END_RIFT_WAVE7_CHAMBERS'
  )
}

function Test-EndRiftLocalBotEnvironmentVariableToRemove {
  [CmdletBinding()]
  param([Parameter(Mandatory = $true)][string]$Name)

  $safeNames = @(Get-EndRiftLocalBotSafeEnvironmentVariableNames)
  if ($Name -match '(?i)(PASSWORD|SECRET|TOKEN|PRIVATE|CREDENTIAL|API[_-]?KEY)') {
    return $true
  }
  return $Name -notin $safeNames
}

function Get-EndRiftLocalBotNodeModulesPath {
  $repositoryRoot = Split-Path -Parent $PSScriptRoot
  $localRuntimeRoot = (Resolve-Path -LiteralPath (Join-Path $repositoryRoot 'local-runtime') -ErrorAction Stop).Path
  $botNodeModules = Assert-EndRiftLocalRuntimePath `
    -Path (Join-Path $localRuntimeRoot 'mc-bot-26.3-complete-probe\node_modules') `
    -LocalRuntimeRoot $localRuntimeRoot -MustExist
  foreach ($requiredModule in @(
      'mineflayer\package.json',
      'minecraft-protocol\package.json',
      'prismarine-chunk\package.json'
    )) {
    if (-not (Test-Path -LiteralPath (Join-Path $botNodeModules $requiredModule) -PathType Leaf)) {
      throw "The pinned ViaBackwards test-bot dependency is missing: $requiredModule"
    }
  }
  return $botNodeModules
}

function Assert-EndRiftLocalBotProtocolGateway {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(25566)]
    [int]$BotPort
  )

  if ($BotPort -ne 25566) {
    throw 'Refused a bot protocol route outside isolated port 25566.'
  }
  $repositoryRoot = Split-Path -Parent $PSScriptRoot
  $localRuntimeRoot = (Resolve-Path -LiteralPath (Join-Path $repositoryRoot 'local-runtime') -ErrorAction Stop).Path
  $serverDir = Assert-EndRiftLocalRuntimePath `
    -Path (Join-Path $localRuntimeRoot 'end-rift-server') `
    -LocalRuntimeRoot $localRuntimeRoot -MustExist
  $propertiesPath = Assert-EndRiftLocalRuntimePath `
    -Path (Join-Path $serverDir 'server.properties') `
    -LocalRuntimeRoot $localRuntimeRoot -MustExist
  $properties = [IO.File]::ReadAllLines($propertiesPath)
  Test-EndRiftLocalServerBind -ServerPropertiesLines $properties | Out-Null
  foreach ($requiredSetting in @(
      'server-port=25566', 'rcon.ip=127.0.0.1', 'rcon.port=25576',
      'white-list=true', 'online-mode=false'
    )) {
    if ($properties -cnotcontains $requiredSetting) {
      throw "Refused the bot gateway: isolated server setting is not $requiredSetting."
    }
  }

  $pinnedArtifacts = @(
    @{ Name = 'ViaVersion-5.12.1.jar'; Sha256 = 'b8033807e7182c35003a45962352dd642c1e91880ca281da3a43cb6f481a95a2'; Size = 6503218 },
    @{ Name = 'ViaBackwards-5.12.1.jar'; Sha256 = '29d94d3a26c85c8a327574b2322db4078992ae457f0173325431a12aac384911'; Size = 1436080 }
  )
  $pluginDir = Assert-EndRiftLocalRuntimePath `
    -Path (Join-Path $serverDir 'plugins') -LocalRuntimeRoot $localRuntimeRoot -MustExist
  foreach ($artifact in $pinnedArtifacts) {
    $artifactPath = Assert-EndRiftLocalRuntimePath `
      -Path (Join-Path $pluginDir $artifact.Name) `
      -LocalRuntimeRoot $localRuntimeRoot -MustExist
    $actualSize = (Get-Item -LiteralPath $artifactPath).Length
    $actualSha256 = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSize -ne $artifact.Size -or $actualSha256 -ne $artifact.Sha256) {
      throw "Refused the bot gateway because $($artifact.Name) does not match its pinned official release."
    }
  }

  $rconScript = Join-Path $PSScriptRoot 'InvokeEndRiftLocalRcon.ps1'
  try {
    $pluginList = & $rconScript -ServerDir $serverDir -ServerPort 25566 `
      -RconPort 25576 -CommandText 'plugins' | Out-String
  } catch {
    throw "Refused the bot gateway because isolated local RCON did not verify it: $($_.Exception.Message)"
  }
  $plainPluginList = $pluginList -replace '(?i)\u00A7x(?:\u00A7[0-9a-f]){6}', ''
  $plainPluginList = $plainPluginList -replace '[\u00A7&][0-9A-FK-ORa-fk-or]', ''
  if ($plainPluginList -notmatch '(?i)ViaVersion' -or
      $plainPluginList -notmatch '(?i)ViaBackwards') {
    throw 'Refused the bot gateway because the pinned ViaVersion plugins are not enabled on Paper.'
  }
  return $true
}

function Invoke-EndRiftLocalBotEnvironment {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('127.0.0.1')]
    [string]$BotHost,
    [Parameter(Mandatory = $true)]
    [ValidateSet(25566)]
    [int]$BotPort,
    [Parameter(Mandatory = $true)]
    [scriptblock]$Action
  )

  $null = Assert-EndRiftLocalBotProtocolGateway -BotPort $BotPort
  $botNodeModules = Get-EndRiftLocalBotNodeModulesPath
  $previousValues = @{}
  foreach ($environmentVariableName in [Environment]::GetEnvironmentVariables('Process').Keys) {
    $previousValues[[string]$environmentVariableName] = [Environment]::GetEnvironmentVariable(
      [string]$environmentVariableName, 'Process')
  }
  try {
    foreach ($environmentVariableName in @([Environment]::GetEnvironmentVariables('Process').Keys)) {
      if (Test-EndRiftLocalBotEnvironmentVariableToRemove -Name ([string]$environmentVariableName)) {
        [Environment]::SetEnvironmentVariable([string]$environmentVariableName, $null, 'Process')
      }
    }
    [Environment]::SetEnvironmentVariable('END_RIFT_BOT_HOST', $BotHost, 'Process')
    [Environment]::SetEnvironmentVariable('END_RIFT_BOT_PORT', [string]$BotPort, 'Process')
    [Environment]::SetEnvironmentVariable('END_RIFT_BOT_NODE_MODULES', $botNodeModules, 'Process')
    [Environment]::SetEnvironmentVariable('END_RIFT_BOT_PROTOCOL_VERSION', '26.1', 'Process')
    & $Action
  } finally {
    foreach ($environmentVariableName in @([Environment]::GetEnvironmentVariables('Process').Keys)) {
      if (-not $previousValues.ContainsKey([string]$environmentVariableName)) {
        [Environment]::SetEnvironmentVariable([string]$environmentVariableName, $null, 'Process')
      }
    }
    foreach ($environmentVariableName in $previousValues.Keys) {
      [Environment]::SetEnvironmentVariable($environmentVariableName, $previousValues[$environmentVariableName], 'Process')
    }
  }
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
    [int]$RconPort,
    [int]$ExpectedProcessId = 0
  )

  $propertiesPath = Join-Path $ServerDir 'server.properties'
  if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
    throw "Local server.properties is missing: $propertiesPath"
  }
  $propertiesLines = Get-Content -LiteralPath $propertiesPath
  Test-EndRiftLocalServerBind -ServerPropertiesLines $propertiesLines | Out-Null

  $minecraftListenerRows = @()
  $rconListenerRows = @()
  for ($snapshotAttempt = 1; $snapshotAttempt -le 3; $snapshotAttempt++) {
    $minecraftListenerRows = @(
      Get-NetTCPConnection -LocalPort $ServerPort -State Listen -ErrorAction SilentlyContinue |
        Select-Object LocalAddress, OwningProcess
    )
    $rconListenerRows = @(
      Get-NetTCPConnection -LocalPort $RconPort -State Listen -ErrorAction SilentlyContinue |
        Select-Object LocalAddress, OwningProcess
    )
    if ($minecraftListenerRows.Count -gt 0 -and $rconListenerRows.Count -gt 0) {
      break
    }
    if ($snapshotAttempt -lt 3) {
      Start-Sleep -Milliseconds 100
    }
  }
  $minecraftListeners = @($minecraftListenerRows | Select-Object -ExpandProperty LocalAddress -Unique)
  $rconListeners = @($rconListenerRows | Select-Object -ExpandProperty LocalAddress -Unique)
  Test-EndRiftLocalListenerAddresses `
    -MinecraftListenerAddresses $minecraftListeners `
    -RconListenerAddresses $rconListeners | Out-Null
  if ($ExpectedProcessId -gt 0) {
    foreach ($listenerRow in @($minecraftListenerRows) + @($rconListenerRows)) {
      if ([int]$listenerRow.OwningProcess -ne $ExpectedProcessId) {
        throw "Local listener on port $ServerPort or $RconPort belongs to another process."
      }
    }
  }
  return [pscustomobject]@{
    MinecraftListenerAddresses = $minecraftListeners
    RconListenerAddresses = $rconListeners
  }
}

function GetHardLinkCount {
  [CmdletBinding()]
  param([Parameter(Mandatory = $true)][string]$Path)

  if (-not ('EndRiftLocalFileIdentityNative' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;
public static class EndRiftLocalFileIdentityNative {
  [StructLayout(LayoutKind.Sequential)]
  public struct BY_HANDLE_FILE_INFORMATION {
    public uint FileAttributes;
    public System.Runtime.InteropServices.ComTypes.FILETIME CreationTime;
    public System.Runtime.InteropServices.ComTypes.FILETIME LastAccessTime;
    public System.Runtime.InteropServices.ComTypes.FILETIME LastWriteTime;
    public uint VolumeSerialNumber;
    public uint FileSizeHigh;
    public uint FileSizeLow;
    public uint NumberOfLinks;
    public uint FileIndexHigh;
    public uint FileIndexLow;
  }
  [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
  public static extern SafeFileHandle CreateFileW(string name, uint access, uint share,
    IntPtr security, uint creation, uint flags, IntPtr template);
  [DllImport("kernel32.dll", SetLastError = true)]
  [return: MarshalAs(UnmanagedType.Bool)]
  public static extern bool GetFileInformationByHandle(SafeFileHandle handle,
    out BY_HANDLE_FILE_INFORMATION information);
}
'@ -ErrorAction Stop | Out-Null
  }

  $fullPath = [IO.Path]::GetFullPath($Path)
  $nativePath = if ($fullPath.StartsWith('\\?\', [StringComparison]::OrdinalIgnoreCase)) {
    $fullPath
  } elseif ($fullPath.StartsWith('\\', [StringComparison]::OrdinalIgnoreCase)) {
    '\\?\UNC\' + $fullPath.Substring(2)
  } else {
    '\\?\' + $fullPath
  }
  $item = Get-Item -LiteralPath $fullPath -Force -ErrorAction Stop
  if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
    throw "Refused a reparse-point runtime path: $fullPath"
  }
  $flags = if ($item.PSIsContainer) { [uint32]0x02000000 } else { [uint32]0 }
  $handle = [EndRiftLocalFileIdentityNative]::CreateFileW(
    $nativePath, [uint32]0, [uint32]7, [IntPtr]::Zero, [uint32]3, $flags, [IntPtr]::Zero)
  if ($handle.IsInvalid) {
    throw "Unable to inspect runtime file identity: $fullPath"
  }
  try {
    $information = New-Object EndRiftLocalFileIdentityNative+BY_HANDLE_FILE_INFORMATION
    if (-not [EndRiftLocalFileIdentityNative]::GetFileInformationByHandle($handle, [ref]$information)) {
      throw "Unable to inspect runtime hard links: $fullPath"
    }
    return [int]$information.NumberOfLinks
  } finally {
    $handle.Dispose()
  }
}

function Assert-EndRiftLocalRuntimePath {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$LocalRuntimeRoot,
    [switch]$MustExist
  )

  $fullRuntimeRoot = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $LocalRuntimeRoot -ErrorAction Stop).ProviderPath)
  $rootItem = Get-Item -LiteralPath $fullRuntimeRoot -Force -ErrorAction Stop
  if (-not $rootItem.PSIsContainer -or ($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
    throw "Refused an unsafe local runtime root: $fullRuntimeRoot"
  }
  $fullPath = [IO.Path]::GetFullPath($Path)
  $fullRuntimeRootName = $fullRuntimeRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
  if (-not [string]::Equals($fullPath, $fullRuntimeRootName, [StringComparison]::OrdinalIgnoreCase) -and
      -not $fullPath.StartsWith($fullRuntimeRootName + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refused a path outside the isolated local runtime: $fullPath"
  }

  $relativePath = $fullPath.Substring($fullRuntimeRootName.Length).TrimStart([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
  if ($relativePath -match '(^|[\\/])[^\\/]*:') {
    throw "Refused an alternate data stream in the local runtime path: $fullPath"
  }
  $currentPath = $fullRuntimeRootName
  $parts = @($relativePath -split '[\\/]+' | Where-Object { $_ -ne '' })
  if ($parts.Count -eq 0 -and -not $MustExist) {
    if ($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
      throw "Refused a reparse-point runtime root: $fullRuntimeRootName"
    }
    return $fullRuntimeRootName
  }
  foreach ($part in $parts) {
    $currentPath = Join-Path $currentPath $part
    if (-not (Test-Path -LiteralPath $currentPath)) {
      if ($MustExist) { throw "Required local runtime path is missing: $currentPath" }
      continue
    }
    $entry = Get-Item -LiteralPath $currentPath -Force -ErrorAction Stop
    if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) {
      throw "Refused a reparse point in the local runtime path: $currentPath"
    }
    if (-not $entry.PSIsContainer -and (GetHardLinkCount -Path $currentPath) -ne 1) {
      throw "Refused a hard-linked local runtime file: $currentPath"
    }
  }
  return $fullPath
}

function Test-EndRiftLocalRuntimePath {
  [CmdletBinding()]
  param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][string]$LocalRuntimeRoot, [switch]$MustExist)
  return Assert-EndRiftLocalRuntimePath -Path $Path -LocalRuntimeRoot $LocalRuntimeRoot -MustExist:$MustExist
}

function Resolve-EndRiftLocalRuntimePath {
  [CmdletBinding()]
  param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][string]$LocalRuntimeRoot)

  $fullPath = [IO.Path]::GetFullPath($Path)
  $fullRuntimeRootName = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $LocalRuntimeRoot -ErrorAction Stop).ProviderPath).TrimEnd('\')
  if ([string]::Equals($fullPath.TrimEnd('\'), $fullRuntimeRootName, [StringComparison]::OrdinalIgnoreCase)) {
    $rootItem = Get-Item -LiteralPath $fullRuntimeRootName -Force -ErrorAction Stop
    if ($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
      throw "Refused a reparse-point local runtime root: $fullRuntimeRootName"
    }
    return $fullRuntimeRootName
  }
  return Assert-EndRiftLocalRuntimePath -Path $fullPath -LocalRuntimeRoot $fullRuntimeRootName
}

function Assert-EndRiftLocalOutputPath {
  [CmdletBinding()]
  param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][string]$LocalRuntimeRoot)
  return Assert-EndRiftLocalRuntimePath -Path $Path -LocalRuntimeRoot $LocalRuntimeRoot
}

function Assert-EndRiftLocalServerTreeIsolated {
  [CmdletBinding()]
  param([Parameter(Mandatory = $true)][string]$ServerDir, [Parameter(Mandatory = $true)][string]$LocalRuntimeRoot)

  $root = Assert-EndRiftLocalRuntimePath -Path $ServerDir -LocalRuntimeRoot $LocalRuntimeRoot -MustExist
  $rootItem = Get-Item -LiteralPath $root -Force
  if (-not $rootItem.PSIsContainer) { throw "Local Paper server path is not a directory: $root" }
  $pending = [System.Collections.Generic.Stack[string]]::new()
  $pending.Push($root)
  while ($pending.Count -gt 0) {
    $currentPath = $pending.Pop()
    foreach ($entryPath in [IO.Directory]::EnumerateFileSystemEntries($currentPath)) {
      $entry = Get-Item -LiteralPath $entryPath -Force -ErrorAction Stop
      if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Refused an isolated Paper server tree containing a reparse point: $entryPath"
      }
      if ($entry.PSIsContainer) {
        $pending.Push($entryPath)
      } elseif ((GetHardLinkCount -Path $entryPath) -ne 1) {
        throw "Refused an isolated Paper server tree containing a hard-linked file: $entryPath"
      }
    }
  }
  return $root
}

function Import-EndRiftLocalEnvironment {
  [CmdletBinding()]
  param([Parameter(Mandatory = $true)][string]$Path)

  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
  $seenEnvironmentNames = @{}
  foreach ($line in Get-Content -LiteralPath $Path) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
      $name = $matches[1]
      $value = $matches[2].Trim().Trim('"').Trim("'")
      if ($name -match '^(?i:PATH|SYSTEMROOT|WINDIR|COMSPEC|PATHEXT|TEMP|TMP|USERPROFILE|HOMEDRIVE|HOMEPATH|JAVA_HOME|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|CLASSPATH|PSMODULEPATH|NODE_OPTIONS|PYTHONHOME|PYTHONPATH)$') {
        throw "Refused a process-launch environment override in the local secret file: $name"
      }
      if ($seenEnvironmentNames.ContainsKey($name)) {
        throw "Refused duplicate environment entry in the local secret file: $name"
      }
      $seenEnvironmentNames[$name] = $true
      Set-Item -Path ("Env:" + $name) -Value $value
    }
  }
}

function Write-EndRiftLocalServerReceipt {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)][int]$ProcessId,
    [Parameter(Mandatory = $true)][string]$ServerDir,
    [Parameter(Mandatory = $true)][string]$PaperJarPath,
    [string]$ReceiptPath = '',
    [Parameter(Mandatory = $true)][string]$LocalRuntimeRoot
  )

  $safeServerDir = Assert-EndRiftLocalRuntimePath -Path $ServerDir -LocalRuntimeRoot $LocalRuntimeRoot -MustExist
  $safePaperJarPath = Assert-EndRiftLocalRuntimePath -Path $PaperJarPath -LocalRuntimeRoot $LocalRuntimeRoot -MustExist
  if ([string]::IsNullOrWhiteSpace($ReceiptPath)) {
    $ReceiptPath = Join-Path $safeServerDir '.end-rift-process-receipt.json'
  }
  $safeReceiptPath = Assert-EndRiftLocalRuntimePath -Path $ReceiptPath -LocalRuntimeRoot $LocalRuntimeRoot
  $process = Get-Process -Id $ProcessId -ErrorAction Stop
  $processStart = $process.StartTime.ToUniversalTime().Ticks
  $receipt = [ordered]@{
    ProcessId = $ProcessId
    ProcessStartTimeUtcTicks = $processStart
    WorkingDirectory = $safeServerDir
    ServerDir = $safeServerDir
    PaperJarPath = $safePaperJarPath
    CreatedAtUtc = [DateTime]::UtcNow.ToString('o')
  }
  [IO.File]::WriteAllText($safeReceiptPath, ($receipt | ConvertTo-Json -Depth 4), [Text.UTF8Encoding]::new($false))
  return [pscustomobject]$receipt
}

function Resolve-EndRiftLocalDriveSubstitutions {
  [CmdletBinding()]
  param([Parameter(Mandatory = $true)][string]$Path)

  $fullPath = [IO.Path]::GetFullPath($Path)
  $commandProcessor = [Environment]::GetEnvironmentVariable('ComSpec')
  if ([string]::IsNullOrWhiteSpace($commandProcessor) -or -not (Test-Path -LiteralPath $commandProcessor -PathType Leaf)) {
    return $fullPath
  }
  $mappingLines = @(& $commandProcessor /d /c subst 2>$null)
  if ($LASTEXITCODE -ne 0) { return $fullPath }
  for ($depth = 0; $depth -lt 8; $depth++) {
    $mapped = $false
    foreach ($line in $mappingLines) {
      if ([string]$line -notmatch '^(?<drive>[A-Za-z]:)\\: => (?<target>.+)$') { continue }
      $driveRoot = $Matches.drive + '\'
      if (-not $fullPath.StartsWith($driveRoot, [StringComparison]::OrdinalIgnoreCase)) { continue }
      $targetRoot = [IO.Path]::GetFullPath($Matches.target).TrimEnd('\')
      $suffix = $fullPath.Substring($driveRoot.Length)
      $fullPath = if ([string]::IsNullOrWhiteSpace($suffix)) {
        $targetRoot
      } else {
        [IO.Path]::GetFullPath((Join-Path $targetRoot $suffix))
      }
      $mapped = $true
      break
    }
    if (-not $mapped) { break }
  }
  return $fullPath
}

function Get-EndRiftLocalServerReceipt {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)][string]$ServerDir,
    [Parameter(Mandatory = $true)][string]$LocalRuntimeRoot,
    [string]$ReceiptPath = ''
  )

  $safeServerDir = Assert-EndRiftLocalRuntimePath -Path $ServerDir -LocalRuntimeRoot $LocalRuntimeRoot -MustExist
  if ([string]::IsNullOrWhiteSpace($ReceiptPath)) {
    $ReceiptPath = Join-Path $safeServerDir '.end-rift-process-receipt.json'
  }
  $safeReceiptPath = Assert-EndRiftLocalRuntimePath -Path $ReceiptPath -LocalRuntimeRoot $LocalRuntimeRoot -MustExist
  $receipt = Get-Content -LiteralPath $safeReceiptPath -Raw | ConvertFrom-Json
  $receiptWorkingDirectory = Resolve-EndRiftLocalDriveSubstitutions -Path ([string]$receipt.WorkingDirectory)
  $canonicalServerDir = Resolve-EndRiftLocalDriveSubstitutions -Path $safeServerDir
  if ([int]$receipt.ProcessId -le 0 -or [int64]$receipt.ProcessStartTimeUtcTicks -le 0 -or
      -not [IO.Path]::IsPathRooted([string]$receipt.PaperJarPath) -or
      -not [string]::Equals($receiptWorkingDirectory, $canonicalServerDir, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Local Paper process receipt is malformed or targets another working directory.'
  }
  return $receipt
}

function Test-EndRiftLocalServerProcessIdentity {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)][int]$ExpectedProcessId,
    [Parameter(Mandatory = $true)][int64]$ExpectedProcessStartTimeUtcTicks,
    [Parameter(Mandatory = $true)][string]$ServerDir,
    [Parameter(Mandatory = $true)][string]$PaperJarPath,
    [Parameter(Mandatory = $true)][string]$LocalRuntimeRoot,
    [Parameter(Mandatory = $true)][string]$ExpectedWorkingDirectory
  )

  try {
    $safeServerDir = Assert-EndRiftLocalRuntimePath -Path $ServerDir -LocalRuntimeRoot $LocalRuntimeRoot -MustExist
    $canonicalPaperJarPath = Resolve-EndRiftLocalDriveSubstitutions -Path $PaperJarPath
    $safeJarPath = Assert-EndRiftLocalRuntimePath -Path $canonicalPaperJarPath -LocalRuntimeRoot $LocalRuntimeRoot -MustExist
    $expectedWorkingDirectory = Resolve-EndRiftLocalDriveSubstitutions -Path $ExpectedWorkingDirectory
    $canonicalServerDir = Resolve-EndRiftLocalDriveSubstitutions -Path $safeServerDir
    if (-not [string]::Equals($expectedWorkingDirectory, $canonicalServerDir, [StringComparison]::OrdinalIgnoreCase)) { return $false }
    $process = Get-Process -Id $ExpectedProcessId -ErrorAction Stop
    if ($process.ProcessName -notin @('java', 'javaw')) { return $false }
    if ($process.StartTime.ToUniversalTime().Ticks -ne $ExpectedProcessStartTimeUtcTicks) { return $false }
    $nativeProcesses = @(Get-CimInstance Win32_Process -Filter "ProcessId = $ExpectedProcessId" -ErrorAction Stop)
    if ($nativeProcesses.Count -ne 1 -or [string]::IsNullOrWhiteSpace($nativeProcesses[0].CommandLine)) { return $false }
    if (-not [string]::IsNullOrWhiteSpace([string]$nativeProcesses[0].ExecutablePath) -and
        -not [string]::Equals([IO.Path]::GetFullPath([string]$nativeProcesses[0].ExecutablePath), [IO.Path]::GetFullPath($process.Path), [StringComparison]::OrdinalIgnoreCase)) {
      return $false
    }
    if (-not ('EndRiftCommandLineNative' -as [type])) {
      Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class EndRiftCommandLineNative {
  [DllImport("shell32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
  public static extern IntPtr CommandLineToArgvW(string commandLine, out int argumentCount);
  [DllImport("kernel32.dll", SetLastError = true)]
  public static extern IntPtr LocalFree(IntPtr memory);
}
'@ -ErrorAction Stop | Out-Null
    }
    $argumentCount = 0
    $argumentVector = [EndRiftCommandLineNative]::CommandLineToArgvW([string]$nativeProcesses[0].CommandLine, [ref]$argumentCount)
    if ($argumentVector -eq [IntPtr]::Zero) { return $false }
    try {
      $arguments = [System.Collections.Generic.List[string]]::new()
      for ($index = 0; $index -lt $argumentCount; $index++) {
        $argumentPointer = [Runtime.InteropServices.Marshal]::ReadIntPtr($argumentVector, $index * [IntPtr]::Size)
        $arguments.Add([Runtime.InteropServices.Marshal]::PtrToStringUni($argumentPointer))
      }
    } finally { [void][EndRiftCommandLineNative]::LocalFree($argumentVector) }
    $jarIndices = @()
    for ($index = 0; $index -lt ($arguments.Count - 1); $index++) {
      if ($arguments[$index] -ieq '-jar') { $jarIndices += $index }
    }
    if ($jarIndices.Count -ne 1) { return $false }
    $jarArgument = $arguments[$jarIndices[0] + 1]
    $resolvedPaperJarPath = if ([IO.Path]::IsPathRooted($jarArgument)) {
      [IO.Path]::GetFullPath($jarArgument)
    } else {
      [IO.Path]::GetFullPath((Join-Path $expectedWorkingDirectory $jarArgument))
    }
    $resolvedPaperJarPath = Resolve-EndRiftLocalDriveSubstitutions -Path $resolvedPaperJarPath
    $paperJarArgumentMatches = [string]::Equals($resolvedPaperJarPath, $safeJarPath, [StringComparison]::OrdinalIgnoreCase)
    if (-not $paperJarArgumentMatches) { return $false }
    return $true
  } catch { return $false }
}

function Set-EndRiftLocalBotChildEnvironment {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)][System.Diagnostics.ProcessStartInfo]$StartInfo,
    [ValidateSet('127.0.0.1')][string]$BotHost = '127.0.0.1',
    [ValidateSet(25566)][int]$BotPort = 25566
  )
  foreach ($name in @($StartInfo.EnvironmentVariables.Keys)) {
    if (Test-EndRiftLocalBotEnvironmentVariableToRemove -Name ([string]$name)) {
      [void]$StartInfo.EnvironmentVariables.Remove([string]$name)
    }
  }
  $null = Assert-EndRiftLocalBotProtocolGateway -BotPort $BotPort
  $botNodeModules = Get-EndRiftLocalBotNodeModulesPath
  $StartInfo.EnvironmentVariables['END_RIFT_BOT_HOST'] = $BotHost
  $StartInfo.EnvironmentVariables['END_RIFT_BOT_PORT'] = [string]$BotPort
  $StartInfo.EnvironmentVariables['END_RIFT_BOT_NODE_MODULES'] = $botNodeModules
  $StartInfo.EnvironmentVariables['END_RIFT_BOT_PROTOCOL_VERSION'] = '26.1'
}

function Set-EndRiftLocalBotChildEnvironmentOverrides {
  [CmdletBinding()]
  param([Parameter(Mandatory = $true)][System.Diagnostics.ProcessStartInfo]$StartInfo, [Parameter(Mandatory = $true)][hashtable]$Overrides)
  foreach ($key in $Overrides.Keys) {
    if ((Test-EndRiftLocalBotEnvironmentVariableToRemove -Name ([string]$key)) -or [string]$key -notlike 'END_RIFT_*') {
      throw "Refused an unsafe local End Rift bot environment override: $key"
    }
    $StartInfo.EnvironmentVariables[[string]$key] = [string]$Overrides[$key]
  }
}
