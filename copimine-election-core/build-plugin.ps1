$ErrorActionPreference = 'Stop'

$pluginDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$releaseRoot = Resolve-Path (Join-Path $pluginDir '..')
$serverDir = Join-Path $releaseRoot 'minecraft\server'
$srcRoot = Join-Path $pluginDir 'src'
$classes = Join-Path $pluginDir 'build\classes'
$jar = Join-Path $pluginDir 'CopiMineElectionCore.jar'
$serverJar = Join-Path $serverDir 'plugins\CopiMineElectionCore.jar'

$paperApi = $env:PAPER_API_JAR
if (-not $paperApi) {
  $paperApi = Get-ChildItem -Path "$env:USERPROFILE\.m2\repository" -Filter 'paper-api-*-R0.1-SNAPSHOT.jar' -Recurse -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName
}
if (-not $paperApi -or -not (Test-Path $paperApi)) {
  throw 'Paper API jar not found. Set PAPER_API_JAR to paper-api-1.21.1-R0.1-SNAPSHOT.jar.'
}

$cp = @($paperApi)
if (Test-Path (Join-Path $serverDir 'libraries')) {
  $cp += Get-ChildItem -Path (Join-Path $serverDir 'libraries') -Filter '*.jar' -Recurse | ForEach-Object FullName
}
if (Test-Path (Join-Path $releaseRoot 'copimine-economy-core\CopiMineEconomyCore.jar')) {
  $cp += (Join-Path $releaseRoot 'copimine-economy-core\CopiMineEconomyCore.jar')
}

$sources = Get-ChildItem -Path $srcRoot -Recurse -Filter '*.java' | ForEach-Object FullName
if (-not $sources) {
  throw 'No Java sources found for CopiMineElectionCore.'
}

Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $classes | Out-Null
javac -encoding UTF-8 -cp ($cp -join [IO.Path]::PathSeparator) -d $classes $sources
if ($LASTEXITCODE -ne 0) {
  throw "javac failed for CopiMineElectionCore with exit code $LASTEXITCODE."
}
Copy-Item -LiteralPath (Join-Path $pluginDir 'plugin.yml') -Destination (Join-Path $classes 'plugin.yml') -Force
if (Test-Path (Join-Path $pluginDir 'config.yml')) {
  Copy-Item -LiteralPath (Join-Path $pluginDir 'config.yml') -Destination (Join-Path $classes 'config.yml') -Force
}
Remove-Item -LiteralPath $jar -Force -ErrorAction SilentlyContinue
jar --create --file $jar -C $classes .
if ($LASTEXITCODE -ne 0) {
  throw "jar packaging failed for CopiMineElectionCore with exit code $LASTEXITCODE."
}
Copy-Item -LiteralPath $jar -Destination $serverJar -Force
Write-Host "Built $jar"
Write-Host "Copied $serverJar"
