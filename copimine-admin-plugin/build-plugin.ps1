$ErrorActionPreference = 'Stop'

$pluginDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$releaseRoot = Resolve-Path (Join-Path $pluginDir '..')
$serverDir = Join-Path $releaseRoot 'minecraft\server'
$src = Join-Path $pluginDir 'src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$classes = Join-Path $pluginDir 'build\classes'
$jar = Join-Path $pluginDir 'CopiMineUltimateAdminPlus.jar'
$serverJar = Join-Path $serverDir 'plugins\CopiMineUltimateAdminPlus.jar'

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
if (Test-Path $mavenRepo) {
  # Paper API is intentionally thin. Include its public signature
  # dependencies so a clean local build does not rely on a populated server.
  foreach ($group in @('net\kyori', 'net\md-5', 'org\joml')) {
    $groupPath = Join-Path $mavenRepo $group
    if (Test-Path $groupPath) {
      $cp += Get-ChildItem -Path $groupPath -Filter '*.jar' -Recurse | ForEach-Object FullName
    }
  }
}
if ($env:PAPER_COMPILE_DEPS) {
  $cp += $env:PAPER_COMPILE_DEPS -split [IO.Path]::PathSeparator
}
$placeholder = Get-ChildItem -Path (Join-Path $serverDir 'plugins') -Filter 'PlaceholderAPI-*.jar' -File -ErrorAction SilentlyContinue |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1 -ExpandProperty FullName
if (-not $placeholder -and (Test-Path (Join-Path $mavenRepo 'me\clip\placeholderapi'))) {
  $placeholder = Get-ChildItem -Path (Join-Path $mavenRepo 'me\clip\placeholderapi') -Filter '*.jar' -Recurse -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName
}
if ($placeholder) { $cp += $placeholder }
$voicechat = Get-ChildItem -Path (Join-Path $serverDir 'plugins') -Filter 'voicechat-*.jar' -File -ErrorAction SilentlyContinue |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1 -ExpandProperty FullName
if (-not $voicechat) {
  $voicechat = Get-ChildItem -Path (Join-Path $releaseRoot 'thirdparty\client-mods') -Filter 'voicechat-*.jar' -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName
}
if ($voicechat) { $cp += $voicechat }
if (Test-Path (Join-Path $serverDir 'libraries')) {
  $cp += Get-ChildItem -Path (Join-Path $serverDir 'libraries') -Filter '*.jar' -Recurse | ForEach-Object FullName
}
if (Test-Path (Join-Path $releaseRoot 'copimine-economy-core\CopiMineEconomyCore.jar')) {
  $cp += (Join-Path $releaseRoot 'copimine-economy-core\CopiMineEconomyCore.jar')
}

Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $classes | Out-Null
javac -encoding UTF-8 -cp ($cp -join [IO.Path]::PathSeparator) -d $classes $src
if ($LASTEXITCODE -ne 0) {
  throw "javac failed for CopiMineUltimateAdminPlus with exit code $LASTEXITCODE."
}
Copy-Item -LiteralPath (Join-Path $pluginDir 'plugin.yml') -Destination (Join-Path $classes 'plugin.yml') -Force
Copy-Item -LiteralPath (Join-Path $pluginDir 'messages_ru.yml') -Destination (Join-Path $classes 'messages_ru.yml') -Force
if (Test-Path (Join-Path $pluginDir 'config.yml')) {
  Copy-Item -LiteralPath (Join-Path $pluginDir 'config.yml') -Destination (Join-Path $classes 'config.yml') -Force
}
Remove-Item -LiteralPath $jar -Force -ErrorAction SilentlyContinue
jar --create --file $jar --date=2020-01-01T00:00:00Z -C $classes .
if ($LASTEXITCODE -ne 0) {
  throw "jar packaging failed for CopiMineUltimateAdminPlus with exit code $LASTEXITCODE."
}
Copy-Item -LiteralPath $jar -Destination $serverJar -Force
Write-Host "Built $jar"
Write-Host "Copied $serverJar"
