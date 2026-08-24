[CmdletBinding()]
param(
    [string] $BasePaperRoot = "",
    [string] $PluginSourceRoot = "",
    [string] $ClientGatePluginPath = "",
    [string] $AdminPluginOverridePath = "",
    [string] $TestPluginPath = "",
    [string] $OutputRoot = "",
    [int] $Port = 25568,
    [int] $DatabasePort = 55434,
    [int] $VoiceChatPort = 24455,
    [string] $DatabasePassword = 'copimine_test',
    [switch] $Start
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$validationRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'artifacts\local-validation'))

if ([string]::IsNullOrWhiteSpace($BasePaperRoot)) {
    $BasePaperRoot = Join-Path $validationRoot 'paper'
}
if ([string]::IsNullOrWhiteSpace($PluginSourceRoot)) {
    $PluginSourceRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'minecraft\server\plugins'))
}
if ([string]::IsNullOrWhiteSpace($ClientGatePluginPath)) {
    $ClientGatePluginPath = Join-Path $validationRoot 'paper\plugins\CopiMineNarcotics.jar'
}
if ([string]::IsNullOrWhiteSpace($AdminPluginOverridePath)) {
    $AdminPluginOverridePath = Join-Path $repoRoot 'copimine-admin-plugin\CopiMineUltimateAdminPlus.jar'
}
if ([string]::IsNullOrWhiteSpace($TestPluginPath)) {
    $TestPluginPath = Join-Path $validationRoot 'bed-probe\CopiMineRuntimeProbe.jar'
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $validationRoot 'paper-full-plugins'
}

function Resolve-FullPath([string] $Path) {
    return [IO.Path]::GetFullPath($Path)
}

function Assert-UnderRoot([string] $Path, [string] $Root, [string] $Label) {
    $candidate = (Resolve-FullPath $Path).TrimEnd('\') + '\'
    $allowed = (Resolve-FullPath $Root).TrimEnd('\') + '\'
    if (-not $candidate.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label must stay below $Root. Actual path: $Path"
    }
}

function Set-ServerProperty([string] $Path, [string] $Key, [string] $Value) {
    $lines = if (Test-Path -LiteralPath $Path -PathType Leaf) { @(Get-Content -LiteralPath $Path) } else { @() }
    $pattern = '^' + [regex]::Escape($Key) + '='
    $found = $false
    $updated = foreach ($line in $lines) {
        if ($line -match $pattern) {
            $found = $true
            "$Key=$Value"
        } else {
            $line
        }
    }
    if (-not $found) {
        $updated += "$Key=$Value"
    }
    Set-Content -LiteralPath $Path -Value $updated -Encoding utf8
}

$resolvedBasePaperRoot = Resolve-FullPath $BasePaperRoot
$resolvedPluginSourceRoot = Resolve-FullPath $PluginSourceRoot
$resolvedGatePluginPath = Resolve-FullPath $ClientGatePluginPath
$resolvedAdminPluginOverridePath = Resolve-FullPath $AdminPluginOverridePath
$resolvedTestPluginPath = Resolve-FullPath $TestPluginPath
$resolvedOutputRoot = Resolve-FullPath $OutputRoot

Assert-UnderRoot $resolvedBasePaperRoot $validationRoot 'Base Paper root'
Assert-UnderRoot $resolvedOutputRoot $validationRoot 'The full-plugin server may only be created below the validation root'
Assert-UnderRoot $resolvedGatePluginPath $validationRoot 'Client gate plugin'
Assert-UnderRoot $resolvedAdminPluginOverridePath $repoRoot 'Admin plugin override'
Assert-UnderRoot $resolvedTestPluginPath $validationRoot 'Test-only runtime probe'
if ([string]::Equals($resolvedBasePaperRoot.TrimEnd('\'), $resolvedOutputRoot.TrimEnd('\'), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The full-plugin server output must be separate from the gate server.'
}
if ($resolvedPluginSourceRoot -match '(?i)(world|playerdata|database|\.env)') {
    throw 'Plugin source must be a local plugin directory, not worlds, playerdata, databases, or environment files.'
}
if ($Port -lt 1024 -or $Port -gt 65535) {
    throw 'The disposable server port must be between 1024 and 65535.'
}
if ($DatabasePort -lt 1024 -or $DatabasePort -gt 65535) {
    throw 'The disposable database port must be between 1024 and 65535.'
}
if ($VoiceChatPort -lt 1024 -or $VoiceChatPort -gt 65535) {
    throw 'The disposable voice-chat port must be between 1024 and 65535.'
}
if ([string]::IsNullOrWhiteSpace($DatabasePassword) -or $DatabasePassword -ne 'copimine_test') {
    throw 'The full-plugin server accepts only the synthetic copimine_test staging database password.'
}

foreach ($requiredPath in @(
    $resolvedBasePaperRoot,
    (Join-Path $resolvedBasePaperRoot 'purpur.jar'),
    $resolvedPluginSourceRoot,
    $resolvedGatePluginPath,
    $resolvedAdminPluginOverridePath
)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required local validation path is missing: $requiredPath"
    }
}

$testPlugin = $null
if (Test-Path -LiteralPath $resolvedTestPluginPath -PathType Leaf) {
    $testPlugin = Get-Item -LiteralPath $resolvedTestPluginPath
}

$sourceJars = @(Get-ChildItem -LiteralPath $resolvedPluginSourceRoot -Filter '*.jar' -File | Sort-Object Name)
if ($sourceJars.Count -lt 20) {
    throw "The local main-server plugin inventory is unexpectedly small: $($sourceJars.Count) JAR files."
}

$selectedJars = [System.Collections.Generic.List[object]]::new()
$excludedEndRiftEvent = [System.Collections.Generic.List[string]]::new()
foreach ($jar in $sourceJars) {
    if ([string]::Equals($jar.Name, 'CopiMineEndEvent.jar', [StringComparison]::OrdinalIgnoreCase)) {
        $excludedEndRiftEvent.Add($jar.Name)
        continue
    }
    if ([string]::Equals($jar.Name, 'CopiMineNarcotics.jar', [StringComparison]::OrdinalIgnoreCase)) {
        continue
    }
    if ([string]::Equals($jar.Name, 'CopiMineUltimateAdminPlus.jar', [StringComparison]::OrdinalIgnoreCase)) {
        continue
    }
    $selectedJars.Add($jar)
}
$selectedJars.Add((Get-Item -LiteralPath $resolvedGatePluginPath))
$selectedJars.Add((Get-Item -LiteralPath $resolvedAdminPluginOverridePath))

if ($selectedJars.Count -lt 20) {
    throw "The selected local plugin inventory is unexpectedly small: $($selectedJars.Count) JAR files."
}

$existingProcess = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -match 'purpur\.jar' -and $_.CommandLine -match [regex]::Escape($resolvedOutputRoot)
})
if ($existingProcess.Count -gt 0) {
    throw "The disposable full-plugin server is already running. PIDs: $($existingProcess.ProcessId -join ', ')"
}

$listener = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
if ($listener.Count -gt 0) {
    throw "Port $Port is already listening; the full-plugin server will not reuse it."
}

if (Test-Path -LiteralPath $resolvedOutputRoot) {
    Remove-Item -LiteralPath $resolvedOutputRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $resolvedOutputRoot, (Join-Path $resolvedOutputRoot 'plugins'), (Join-Path $resolvedOutputRoot 'logs') -Force | Out-Null

# Copy only the disposable Paper runtime and server defaults. Do not copy worlds, playerdata, databases, or plugin data.
foreach ($directoryName in @('libraries', 'versions', 'cache', 'config')) {
    $sourceDirectory = Join-Path $resolvedBasePaperRoot $directoryName
    if (Test-Path -LiteralPath $sourceDirectory -PathType Container) {
        Copy-Item -LiteralPath $sourceDirectory -Destination (Join-Path $resolvedOutputRoot $directoryName) -Recurse -Force
    }
}
foreach ($fileName in @('purpur.jar', 'bukkit.yml', 'spigot.yml', 'pufferfish.yml', 'purpur.yml', 'commands.yml', 'permissions.yml', 'help.yml')) {
    $sourceFile = Join-Path $resolvedBasePaperRoot $fileName
    if (Test-Path -LiteralPath $sourceFile -PathType Leaf) {
        Copy-Item -LiteralPath $sourceFile -Destination (Join-Path $resolvedOutputRoot $fileName) -Force
    }
}

$propertiesPath = Join-Path $resolvedOutputRoot 'server.properties'
$basePropertiesPath = Join-Path $resolvedBasePaperRoot 'server.properties'
if (Test-Path -LiteralPath $basePropertiesPath -PathType Leaf) {
    Copy-Item -LiteralPath $basePropertiesPath -Destination $propertiesPath -Force
}
Set-ServerProperty $propertiesPath 'server-port' ([string]$Port)
Set-ServerProperty $propertiesPath 'query.port' ([string]$Port)
Set-ServerProperty $propertiesPath 'online-mode' 'false'
Set-ServerProperty $propertiesPath 'enforce-secure-profile' 'false'
Set-ServerProperty $propertiesPath 'enable-query' 'false'
Set-ServerProperty $propertiesPath 'enable-rcon' 'false'
Set-ServerProperty $propertiesPath 'level-name' 'DisposableCopiMineFullPlugins'
Set-ServerProperty $propertiesPath 'resource-pack' ''
Set-ServerProperty $propertiesPath 'resource-pack-sha1' ''
Set-ServerProperty $propertiesPath 'motd' 'CopiMine disposable full-plugin validation'

Set-Content -LiteralPath (Join-Path $resolvedOutputRoot 'eula.txt') -Value 'eula=true' -Encoding ascii

$manifestPlugins = [System.Collections.Generic.List[object]]::new()
foreach ($jar in ($selectedJars | Sort-Object Name)) {
    $destination = Join-Path $resolvedOutputRoot ('plugins\' + $jar.Name)
    Copy-Item -LiteralPath $jar.FullName -Destination $destination -Force
    $manifestPlugins.Add([ordered]@{
            file = $jar.Name
            source = $jar.FullName
            size = $jar.Length
            sha256 = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    })
}

$manifestTestPlugins = [System.Collections.Generic.List[object]]::new()
if ($null -ne $testPlugin) {
    $testDestination = Join-Path $resolvedOutputRoot ('plugins\' + $testPlugin.Name)
    Copy-Item -LiteralPath $testPlugin.FullName -Destination $testDestination -Force
    $manifestTestPlugins.Add([ordered]@{
            file = $testPlugin.Name
            source = $testPlugin.FullName
            size = $testPlugin.Length
            sha256 = (Get-FileHash -LiteralPath $testPlugin.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            testOnly = $true
        })
}

$voiceChatConfigDirectory = Join-Path $resolvedOutputRoot 'plugins\voicechat'
New-Item -ItemType Directory -Path $voiceChatConfigDirectory -Force | Out-Null
@(
    '# Disposable local validation voice-chat endpoint'
    "port=$VoiceChatPort"
    'bind_address=127.0.0.1'
) | Set-Content -LiteralPath (Join-Path $voiceChatConfigDirectory 'voicechat-server.properties') -Encoding ascii

$stagingEnvPath = Join-Path $resolvedOutputRoot 'staging.env'
@(
    'environment=staging'
    'COPIMINE_STAGING=1'
    'POSTGRES_HOST=127.0.0.1'
    "POSTGRES_PORT=$DatabasePort"
    'POSTGRES_DB=copimine_test'
    'POSTGRES_USER=copimine_test'
    "POSTGRES_PASSWORD=$DatabasePassword"
    'POSTGRES_SCHEMA=copimine_test'
) | Set-Content -LiteralPath $stagingEnvPath -Encoding ascii

$manifest = [ordered]@{
    environment = 'staging'
    serverRoot = $resolvedOutputRoot
    serverPort = $Port
    sourcePluginRoot = $resolvedPluginSourceRoot
    excluded_end_rift_event = @($excludedEndRiftEvent)
    do_not_copy = @('worlds', 'playerdata', 'databases', 'AuthMe data', 'plugin data')
    plugins = @($manifestPlugins)
    test_plugins = @($manifestTestPlugins)
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $resolvedOutputRoot 'full-plugin-manifest.json') -Encoding utf8

Write-Output "FULL_PLUGIN_SERVER_ROOT=$resolvedOutputRoot"
Write-Output "FULL_PLUGIN_SERVER_PORT=$Port"
Write-Output "FULL_PLUGIN_COUNT=$($selectedJars.Count)"
Write-Output "FULL_PLUGIN_TEST_COUNT=$($manifestTestPlugins.Count)"
Write-Output "EXCLUDED_END_RIFT_EVENT=$($excludedEndRiftEvent -join ',')"
Write-Output 'PRODUCTION_DATA_COPIED=NO'
Write-Output 'STAGING_DATABASE_HOST=127.0.0.1'
Write-Output 'STAGING_DATABASE_NAME=copimine_test'
Write-Output "STAGING_DATABASE_PORT=$DatabasePort"

if (-not $Start) {
    Write-Output 'FULL_PLUGIN_SERVER_PREPARED=YES'
    exit 0
}

$java = (Get-Command java.exe -ErrorAction SilentlyContinue).Source
if ([string]::IsNullOrWhiteSpace($java)) {
    throw 'Java 21 is required to start the disposable full-plugin server.'
}

$latestLog = Join-Path $resolvedOutputRoot 'logs\latest.log'
$stdoutLog = Join-Path $resolvedOutputRoot 'logs\full-plugin.stdout.log'
$stderrLog = Join-Path $resolvedOutputRoot 'logs\full-plugin.stderr.log'
$env:COPIMINE_STAGING = '1'
$env:COPIMINE_ENV_FILE = $stagingEnvPath
$env:POSTGRES_HOST = '127.0.0.1'
$env:POSTGRES_PORT = [string]$DatabasePort
$env:POSTGRES_DB = 'copimine_test'
$env:POSTGRES_DATABASE = 'copimine_test'
$env:POSTGRES_USER = 'copimine_test'
$env:POSTGRES_PASSWORD = $DatabasePassword
$env:POSTGRES_SCHEMA = 'copimine_test'
$env:PGHOST = '127.0.0.1'
$env:PGPORT = [string]$DatabasePort
$env:PGDATABASE = 'copimine_test'
$env:PGUSER = 'copimine_test'
$env:PGPASSWORD = $DatabasePassword
Remove-Item Env:DATABASE_URL -ErrorAction SilentlyContinue

$process = Start-Process -FilePath $java `
    -ArgumentList @('--add-modules=jdk.incubator.vector', '-Xms512M', '-Xmx2048M', '-jar', 'purpur.jar', '--nogui') `
    -WorkingDirectory $resolvedOutputRoot `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -WindowStyle Hidden `
    -PassThru

$deadline = [DateTimeOffset]::UtcNow.AddSeconds(240)
$ready = $false
try {
    while ([DateTimeOffset]::UtcNow -lt $deadline -and -not $process.HasExited) {
        if (Test-Path -LiteralPath $latestLog -PathType Leaf) {
            $tail = Get-Content -LiteralPath $latestLog -Tail 240 -ErrorAction SilentlyContinue
            if ($tail -match 'Done \([0-9\.,]+s\)!') {
                $ready = $true
                break
            }
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        $tail = if (Test-Path -LiteralPath $latestLog) { Get-Content -LiteralPath $latestLog -Tail 160 } else { @() }
        $tail | Select-Object -Last 160
        throw "Disposable full-plugin Paper did not reach Done. ExitCode=$($process.ExitCode)"
    }
    $readyText = (Get-Content -LiteralPath $latestLog -Tail 400) -join "`n"
    if ($readyText -notmatch 'CopiMineNarcotics') {
        throw 'Paper reached Done but CopiMineNarcotics was not observed in the startup log.'
    }
    Write-Output "FULL_PLUGIN_SERVER_PID=$($process.Id)"
    Write-Output 'FULL_PLUGIN_SERVER_READY=YES'
    Write-Output 'CLIENT_GATE_SERVER_READY=YES'
    Write-Output "FULL_PLUGIN_SERVER_LOG=$latestLog"
} catch {
    if ($null -ne $process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
    throw
}
