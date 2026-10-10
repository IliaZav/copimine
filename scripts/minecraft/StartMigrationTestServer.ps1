[CmdletBinding()]
param(
    [string]$JavaHome,
    [switch]$ValidateOnly,
    [switch]$FullPluginSet
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
Import-Module -Name (Join-Path $PSScriptRoot 'MigrationDatabaseEnvironment.psm1') -Force
$expectedRuntime = Join-Path $root 'local-runtime/end-rift-server-26.3'
if (-not (Test-Path -LiteralPath $expectedRuntime -PathType Container)) {
    throw "Prepared migration runtime is missing: $expectedRuntime"
}
$runtime = (Resolve-Path -LiteralPath $expectedRuntime).Path
if (-not [string]::Equals($runtime, $expectedRuntime, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Migration server path resolved outside its owned local runtime.'
}

$mutex = [System.Threading.Mutex]::new($false, 'Local\CopiMineMinecraft263RuntimeLifecycleLock')
$mutexHeld = $false
$previousDatabaseEnvironment = [ordered]@{}
$previousCopimineEnvFile = [Environment]::GetEnvironmentVariable('COPIMINE_ENV_FILE', [EnvironmentVariableTarget]::Process)
try {
    $databaseEnvironmentFile = Join-Path $runtime 'migration-test.env'
    if (-not (Test-Path -LiteralPath $databaseEnvironmentFile -PathType Leaf)) {
        throw 'The isolated migration-test.env database configuration is missing.'
    }
    $databaseEnvironmentItem = Get-Item -LiteralPath $databaseEnvironmentFile -Force
    if ($databaseEnvironmentItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw 'The isolated database environment file cannot be a symlink or reparse point.'
    }
    $env:COPIMINE_ENV_FILE = $databaseEnvironmentFile
    $previousDatabaseEnvironment = Clear-MigrationDatabaseOverrides

    try {
        $mutexHeld = $mutex.WaitOne(0)
    }
    catch [System.Threading.AbandonedMutexException] {
        $mutexHeld = $true
    }
    if (-not $mutexHeld) {
        throw 'Migration runtime lifecycle lock is held by plugin installation or another server start.'
    }

    $propertiesPath = Join-Path $runtime 'server.properties'
    if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
        throw 'Migration server.properties is missing.'
    }
    $pythonLauncher = Get-Command 'py.exe' -ErrorAction SilentlyContinue
    if ($null -eq $pythonLauncher) { throw 'Python 3.13 is required to validate effective Java server.properties settings.' }
    $pluginInstaller = Join-Path $root 'scripts/minecraft/install_migration_server_plugins.py'
    $authMeJars = @(Get-ChildItem -LiteralPath (Join-Path $runtime 'plugins') -Filter '*.jar' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '(?i)^AuthMe.*\.jar$' })
    if ($FullPluginSet -and $authMeJars.Count -ne 1) {
        throw 'AuthMe is required for -FullPluginSet. Install the full locked candidate runtime without -unauthenticated-test-runtime first.'
    }
    if (-not $FullPluginSet -and $authMeJars.Count -gt 0) {
        throw 'AuthMe is still active. Run the unauthenticated local-test plugin installer first.'
    }
    $startupSettingsArguments = @('--startup-settings-json')
    if (-not $FullPluginSet) { $startupSettingsArguments += '--unauthenticated-test-runtime' }
    $settingsOutputPath = [IO.Path]::Combine([IO.Path]::GetTempPath(), 'copimine-migration-settings-' + [guid]::NewGuid().ToString('N') + '.stdout')
    $settingsErrorPath = [IO.Path]::Combine([IO.Path]::GetTempPath(), 'copimine-migration-settings-' + [guid]::NewGuid().ToString('N') + '.stderr')
    $settingsArguments = @('-3.13', ('"' + $pluginInstaller + '"')) + $startupSettingsArguments
    try {
        $settingsProcess = Start-Process -FilePath $pythonLauncher.Source -ArgumentList $settingsArguments `
            -NoNewWindow -Wait -PassThru -RedirectStandardOutput $settingsOutputPath -RedirectStandardError $settingsErrorPath
        $settingsExitCode = $settingsProcess.ExitCode
        $settingsOutput = [IO.File]::ReadAllText($settingsOutputPath)
        $settingsError = [IO.File]::ReadAllText($settingsErrorPath)
    }
    finally {
        if (Test-Path -LiteralPath $settingsOutputPath -PathType Leaf) { Remove-Item -LiteralPath $settingsOutputPath -Force }
        if (Test-Path -LiteralPath $settingsErrorPath -PathType Leaf) { Remove-Item -LiteralPath $settingsErrorPath -Force }
    }
    if ($settingsExitCode -ne 0) {
        $failureDetails = @("validator exit code $settingsExitCode")
        if (-not [string]::IsNullOrWhiteSpace($settingsError)) { $failureDetails += "stderr: $settingsError" }
        if (-not [string]::IsNullOrWhiteSpace($settingsOutput)) { $failureDetails += "stdout: $settingsOutput" }
        throw "Migration server properties are unsafe or invalid: $($failureDetails -join ' ')"
    }
    if (-not [string]::IsNullOrWhiteSpace($settingsError)) { Write-Verbose "Startup settings validator stderr: $settingsError" }
    try {
        $runtimeSettings = $settingsOutput | ConvertFrom-Json -ErrorAction Stop
    }
    catch {
        throw 'Migration server properties validator returned invalid JSON.'
    }
    if ($runtimeSettings.serverIp -cne '127.0.0.1' -or
        $runtimeSettings.onlineMode -cne 'false' -or
        $runtimeSettings.enableRcon -cne 'false') {
        throw 'The local migration server must stay loopback-only, offline-compatible, and keep RCON disabled.'
    }
    if ($runtimeSettings.voiceChatBindAddress -cne '127.0.0.1') {
        throw 'The local migration Voice Chat UDP listener must stay bound to 127.0.0.1.'
    }
    $serverPort = [int]$runtimeSettings.serverPort
    $eula = [System.IO.File]::ReadAllText((Join-Path $runtime 'eula.txt'))
    if ($eula -notmatch '(?m)^eula=true\r?$') { throw 'The isolated test server does not have the accepted EULA setting.' }

    $profile = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/profile.lock.json') -Raw |
        ConvertFrom-Json
    $paperFilename = 'paper-' + $profile.minecraftVersion + '-' + $profile.paper.build + '.jar'
    $paperPath = Join-Path $root ('build/minecraft-26.3/server/' + $paperFilename)
    if (-not (Test-Path -LiteralPath $paperPath -PathType Leaf) -or
        (Get-Item -LiteralPath $paperPath).Length -ne [long]$profile.paper.size -or
        (Get-FileHash -LiteralPath $paperPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $profile.paper.sha256) {
        throw 'Prepared Paper server JAR does not match the 26.3 profile lock.'
    }

    if (-not $JavaHome) {
        $adoptiumRoot = Join-Path $env:ProgramFiles 'Eclipse Adoptium'
        $javaCandidate = Get-ChildItem -LiteralPath $adoptiumRoot -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($null -eq $javaCandidate) { throw 'Java 25 is required; pass -JavaHome with a Java 25 installation.' }
        $JavaHome = $javaCandidate.FullName
    }
    $java = Join-Path $JavaHome 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw "Java executable is missing: $java" }
    $javaVersionOutputPath = [IO.Path]::Combine([IO.Path]::GetTempPath(), 'copimine-migration-java-version-' + [guid]::NewGuid().ToString('N') + '.stdout')
    $javaVersionErrorPath = [IO.Path]::Combine([IO.Path]::GetTempPath(), 'copimine-migration-java-version-' + [guid]::NewGuid().ToString('N') + '.stderr')
    try {
        $javaVersionProcess = Start-Process -FilePath $java -ArgumentList '--version' -NoNewWindow -Wait -PassThru `
            -RedirectStandardOutput $javaVersionOutputPath -RedirectStandardError $javaVersionErrorPath
        $javaVersionExitCode = $javaVersionProcess.ExitCode
        $javaVersionText = [IO.File]::ReadAllText($javaVersionOutputPath) + [Environment]::NewLine +
            [IO.File]::ReadAllText($javaVersionErrorPath)
    }
    finally {
        if (Test-Path -LiteralPath $javaVersionOutputPath -PathType Leaf) { Remove-Item -LiteralPath $javaVersionOutputPath -Force }
        if (Test-Path -LiteralPath $javaVersionErrorPath -PathType Leaf) { Remove-Item -LiteralPath $javaVersionErrorPath -Force }
    }
    $javaVersion = ($javaVersionText -split '\r?\n' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -First 1).Trim()
    $javaMatch = [regex]::Match($javaVersion, '^(?:openjdk|java)(?: version)?\s+\D*(?<major>\d+)')
    if ($javaVersionExitCode -ne 0 -or -not $javaMatch.Success -or
        [int]$javaMatch.Groups['major'].Value -lt [int]$profile.minimumJava) {
        throw "Java $($profile.minimumJava) or newer is required to start Paper 26.3; detected: $javaVersion"
    }

    $probe = [System.Net.Sockets.TcpClient]::new()
    try {
        $pending = $probe.BeginConnect('127.0.0.1', $serverPort, $null, $null)
        if ($pending.AsyncWaitHandle.WaitOne(300) -and $probe.Connected) {
            throw "Port $serverPort is already accepting connections. Stop that server before starting this runtime."
        }
    }
    finally {
        $probe.Dispose()
    }

    if ($ValidateOnly) {
        if ($FullPluginSet) {
            Write-Output "Validated full locked-plugin Minecraft 26.3 candidate server on 127.0.0.1:$serverPort with AuthMe enabled; loopback-only."
        }
        else {
            Write-Output "Validated loopback-only Minecraft 26.3 test server on 127.0.0.1:$serverPort with AuthMe disabled."
        }
        return
    }

    if ($FullPluginSet) {
        Write-Output "Starting the full locked-plugin Minecraft 26.3 candidate server on 127.0.0.1:$serverPort with AuthMe enabled. This server remains loopback-only."
    }
    else {
        Write-Output "Starting Minecraft 26.3 test server on 127.0.0.1:$serverPort. Licensed and offline clients can join from this PC."
    }
    Write-Output 'Keep this console open; press Ctrl+C to stop the server.'
    $javaArguments = @(
        '-Xms256M'
        '-Xmx2G'
        '-XX:+UseG1GC'
        '-Dfile.encoding=UTF-8'
        '-jar'
        $paperPath
        'nogui'
    )
    Push-Location -LiteralPath $runtime
    try {
        & $java @javaArguments
        if ($LASTEXITCODE -ne 0) { throw "Paper exited with code $LASTEXITCODE." }
    }
    finally {
        Pop-Location
    }
}
finally {
    Restore-MigrationDatabaseOverrides -Values $previousDatabaseEnvironment
    if ($null -eq $previousCopimineEnvFile) {
        Remove-Item -LiteralPath 'Env:COPIMINE_ENV_FILE' -ErrorAction SilentlyContinue
    }
    else {
        [Environment]::SetEnvironmentVariable(
            'COPIMINE_ENV_FILE',
            $previousCopimineEnvFile,
            [EnvironmentVariableTarget]::Process
        )
    }
    if ($mutexHeld) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
