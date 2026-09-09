$ErrorActionPreference = 'Stop'

$pluginDir = Split-Path -Parent $PSScriptRoot
$releaseRoot = Resolve-Path (Join-Path $pluginDir '..')
$serverDir = Join-Path $releaseRoot 'minecraft\server'
$classes = Join-Path $pluginDir 'build\focused-classes'
$jar = Join-Path $pluginDir 'build\CopiMineNarcotics-focused.jar'

$paperApi = $env:PAPER_API_JAR
if (-not $paperApi) {
    $paperApi = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.m2\repository') -Filter 'paper-api-*-R0.1-SNAPSHOT.jar' -Recurse -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $paperApi -or -not (Test-Path $paperApi)) {
    throw 'Paper API jar not found. Set PAPER_API_JAR to paper-api-1.21.1-R0.1-SNAPSHOT.jar.'
}

$cp = @($paperApi)
$mavenRepo = Join-Path $env:USERPROFILE '.m2\repository'
foreach ($group in @('net\kyori', 'net\md-5', 'org\joml')) {
    $groupPath = Join-Path $mavenRepo $group
    if (Test-Path $groupPath) {
        $cp += Get-ChildItem -Path $groupPath -Filter '*.jar' -Recurse | ForEach-Object FullName
    }
}
if ($env:PAPER_COMPILE_DEPS) {
    $cp += $env:PAPER_COMPILE_DEPS -split [IO.Path]::PathSeparator
}
$serverLibraries = Join-Path $serverDir 'libraries'
if (Test-Path $serverLibraries) {
    $cp += Get-ChildItem -Path $serverLibraries -Filter '*.jar' -Recurse | ForEach-Object FullName
}
foreach ($dependency in @(
    'copimine-economy-core\CopiMineEconomyCore.jar',
    'copimine-admin-plugin\CopiMineUltimateAdminPlus.jar',
    'copimine-election-core\CopiMineElectionCore.jar',
    'copimine-artifacts\CopiMineArtifacts.jar'
)) {
    $dependencyPath = Join-Path $releaseRoot $dependency
    if (Test-Path $dependencyPath) {
        $cp += $dependencyPath
    }
}

Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sources = Get-ChildItem -Path (Join-Path $pluginDir 'src') -Recurse -Filter '*.java' | ForEach-Object FullName
& javac -encoding UTF-8 -cp ($cp -join [IO.Path]::PathSeparator) -d $classes $sources
if ($LASTEXITCODE -ne 0) {
    throw 'javac failed for focused CopiMineNarcotics build.'
}
Copy-Item -LiteralPath (Join-Path $pluginDir 'plugin.yml') -Destination (Join-Path $classes 'plugin.yml') -Force
Copy-Item -LiteralPath (Join-Path $pluginDir 'config.yml') -Destination (Join-Path $classes 'config.yml') -Force
Remove-Item -LiteralPath $jar -Force -ErrorAction SilentlyContinue
& jar --create --file $jar --date=2020-01-01T00:00:00Z -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw 'jar packaging failed for focused CopiMineNarcotics build.'
}

Write-Host "FOCUSED_PLUGIN_BUILD=PASS"
Write-Host "FOCUSED_PLUGIN_JAR=$jar"
Write-Host "FOCUSED_PLUGIN_SHA256=$((Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash)"
