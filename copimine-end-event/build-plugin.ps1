$ErrorActionPreference = 'Stop'

$pluginDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$releaseRoot = Resolve-Path (Join-Path $pluginDir '..')
$serverDir = Join-Path $releaseRoot 'minecraft\server'
$srcRoot = Join-Path $pluginDir 'src'
$classes = Join-Path $pluginDir 'build\classes'
$jar = Join-Path $pluginDir 'CopiMineEndEvent.jar'
$serverJar = Join-Path $serverDir 'plugins\CopiMineEndEvent.jar'
$serverDataDir = Join-Path $serverDir 'plugins\CopiMineEndEvent'

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
$mavenRepo = Join-Path $env:USERPROFILE '.m2\repository'
foreach ($group in @('net\kyori', 'net\md-5', 'org\joml')) {
  $groupPath = Join-Path $mavenRepo $group
  if (Test-Path $groupPath) {
    $cp += Get-ChildItem -Path $groupPath -Filter '*.jar' -Recurse | ForEach-Object FullName
  }
}
foreach ($dependency in @(
  (Join-Path $releaseRoot 'copimine-world-core\CopiMineWorldCore.jar'),
  (Join-Path $releaseRoot 'copimine-artifacts\CopiMineArtifacts.jar')
)) {
  if (-not (Test-Path $dependency)) {
    throw "Required typed dependency jar is missing: $dependency"
  }
  $cp += $dependency
}
if ($env:PAPER_COMPILE_DEPS) {
  $cp += $env:PAPER_COMPILE_DEPS -split [IO.Path]::PathSeparator
}
if (Test-Path (Join-Path $serverDir 'libraries')) {
  $cp += Get-ChildItem -Path (Join-Path $serverDir 'libraries') -Filter '*.jar' -Recurse | ForEach-Object FullName
}

Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $classes | Out-Null
$sources = Get-ChildItem -Path $srcRoot -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName
if (-not $sources) { throw "No Java sources found under $srcRoot." }
javac -encoding UTF-8 -cp ($cp -join [IO.Path]::PathSeparator) -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed for CopiMineEndEvent." }
Copy-Item -LiteralPath (Join-Path $pluginDir 'plugin.yml') -Destination (Join-Path $classes 'plugin.yml') -Force
Copy-Item -LiteralPath (Join-Path $pluginDir 'config.yml') -Destination (Join-Path $classes 'config.yml') -Force
Remove-Item -LiteralPath $jar -Force -ErrorAction SilentlyContinue
jar --create --file $jar --date=2020-01-01T00:00:00Z -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar packaging failed for CopiMineEndEvent." }
Copy-Item -LiteralPath $jar -Destination $serverJar -Force
New-Item -ItemType Directory -Path $serverDataDir -Force | Out-Null
if (-not (Test-Path (Join-Path $serverDataDir 'config.yml'))) {
  Copy-Item -LiteralPath (Join-Path $pluginDir 'config.yml') -Destination (Join-Path $serverDataDir 'config.yml') -Force
}
Write-Host "Built $jar"
Write-Host "Copied $serverJar"
