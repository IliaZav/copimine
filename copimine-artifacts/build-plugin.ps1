$ErrorActionPreference = 'Stop'

$pluginDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$releaseRoot = Resolve-Path (Join-Path $pluginDir '..')
$serverDir = Join-Path $releaseRoot 'minecraft\server'
$src = Join-Path $pluginDir 'src\me\copimine\artifacts\CopiMineArtifacts.java'
$classes = Join-Path $pluginDir 'build\classes'
$jar = Join-Path $pluginDir 'CopiMineArtifacts.jar'
$serverJar = Join-Path $serverDir 'plugins\CopiMineArtifacts.jar'
$serverDataDir = Join-Path $serverDir 'plugins\CopiMineArtifacts'

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
if (Test-Path (Join-Path $releaseRoot 'copimine-admin-plugin\CopiMineUltimateAdminPlus.jar')) {
  $cp += (Join-Path $releaseRoot 'copimine-admin-plugin\CopiMineUltimateAdminPlus.jar')
}

Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $classes | Out-Null
javac -encoding UTF-8 -cp ($cp -join [IO.Path]::PathSeparator) -d $classes $src
Copy-Item -LiteralPath (Join-Path $pluginDir 'plugin.yml') -Destination (Join-Path $classes 'plugin.yml') -Force
Copy-Item -LiteralPath (Join-Path $pluginDir 'config.yml') -Destination (Join-Path $classes 'config.yml') -Force
Copy-Item -LiteralPath (Join-Path $pluginDir 'items.yml') -Destination (Join-Path $classes 'items.yml') -Force
Remove-Item -LiteralPath $jar -Force -ErrorAction SilentlyContinue
jar --create --file $jar -C $classes .
Write-Host "Built $jar"
Copy-Item -LiteralPath $jar -Destination $serverJar -Force
New-Item -ItemType Directory -Path $serverDataDir -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $pluginDir 'config.yml') -Destination (Join-Path $serverDataDir 'config.yml') -Force
Copy-Item -LiteralPath (Join-Path $pluginDir 'items.yml') -Destination (Join-Path $serverDataDir 'items.yml') -Force
Write-Host "Copied $serverJar"
Write-Host "Copied default config and items to $serverDataDir"
