# Сборка моста сайт ↔ игра.
#
# Paper API 26.3 собран под Java 25 (class file 69), поэтому компилировать нужно
# тем же тулчейном — иначе javac падает с «wrong version 69.0, should be 65.0».
# Зависимости берутся из локального maven, профиль — из lock-файла сервера.
$ErrorActionPreference = 'Stop'

$pluginDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$siteRoot = Split-Path -Parent $pluginDir
$m2 = if ($env:COPIMINE_MAVEN_REPOSITORY) { $env:COPIMINE_MAVEN_REPOSITORY } else { Join-Path $env:USERPROFILE '.m2\repository' }

$profilePath = if ($env:COPIMINE_PROFILE_LOCK) { $env:COPIMINE_PROFILE_LOCK } else {
  Join-Path $siteRoot '..\copimine-source-of-truth\.worktrees\integration-20261030\tools\minecraft-26.3\profile.lock.json'
}
$dependencyLockPath = Join-Path (Split-Path -Parent $profilePath) 'java-plugin-compile-dependencies.lock.json'
if (-not (Test-Path -LiteralPath $profilePath)) { throw "Paper API lock is missing: $profilePath" }

$profile = Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json
$api = Join-Path $m2 "io/papermc/paper/paper-api/$($profile.paper.api)/paper-api-$($profile.paper.api).jar"
if (-not (Test-Path -LiteralPath $api -PathType Leaf)) { throw "Paper API is missing: $api" }
if ((Get-Item -LiteralPath $api).Length -ne [long]$profile.paper.apiSize) { throw 'Paper API size mismatch.' }
if ((Get-FileHash -LiteralPath $api -Algorithm SHA256).Hash.ToLowerInvariant() -ne $profile.paper.apiSha256) {
  throw 'Paper API SHA256 mismatch.'
}

# Paper API ссылается на adventure и аннотации: без них javac не видит типы Bukkit.
$classpath = @($api, (Join-Path $m2 'com/google/code/gson/gson/2.14.0/gson-2.14.0.jar'))
if (Test-Path -LiteralPath $dependencyLockPath) {
  $dependencyLock = Get-Content -LiteralPath $dependencyLockPath -Raw | ConvertFrom-Json
  foreach ($dependency in @($dependencyLock.paper26_3Dependencies)) {
    $candidate = Join-Path $m2 ([string]$dependency.path -replace '/', '\')
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { $classpath += $candidate }
  }
}
$classpath = $classpath -join ';'

$javaHome = if ($env:COPIMINE_JAVA_HOME) { $env:COPIMINE_JAVA_HOME } else {
  Join-Path $siteRoot '..\copimine-source-of-truth\.worktrees\integration-20261030\local-runtime\toolchains\temurin-25.0.4.1\jdk-25.0.4.1+1'
}
$javac = Join-Path $javaHome 'bin\javac.exe'
$jarTool = Join-Path $javaHome 'bin\jar.exe'
if (-not (Test-Path -LiteralPath $javac)) { throw "javac is missing: $javac" }

$buildDir = Join-Path $pluginDir 'build'
$classes = Join-Path $buildDir 'classes'
$outJar = Join-Path $pluginDir 'CopiMineWebsiteBridge.jar'
if (Test-Path -LiteralPath $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$sources = @()
foreach ($file in (Get-ChildItem -LiteralPath (Join-Path $pluginDir 'src') -Recurse -Filter '*.java')) {
  $sources += $file.FullName
}

# javac пишет предупреждения в stderr — результат проверяем по наличию класса.
& $javac -encoding UTF-8 -Xlint:-deprecation -source 25 -target 25 -cp $classpath -d $classes @sources |
  Set-Content -LiteralPath (Join-Path $buildDir 'javac.log') -Encoding UTF8

$mainClass = Join-Path $classes 'me/copimine/website/CopiMineWebsiteBridge.class'
if (-not (Test-Path -LiteralPath $mainClass)) {
  Get-Content -LiteralPath (Join-Path $buildDir 'javac.log') | Write-Host
  throw 'Compilation produced no class files.'
}

Copy-Item -LiteralPath (Join-Path $pluginDir 'plugin.yml') -Destination $classes -Force
if (Test-Path -LiteralPath $outJar) { Remove-Item -LiteralPath $outJar -Force }
& $jarTool --create --file $outJar --date=2020-01-01T00:00:00Z -C $classes . 2>&1 |
  Set-Content -LiteralPath (Join-Path $buildDir 'jar.log') -Encoding UTF8
if (-not (Test-Path -LiteralPath $outJar)) { throw 'jar was not produced.' }

$bytes = (Get-Item -LiteralPath $outJar).Length
$sha = (Get-FileHash -LiteralPath $outJar -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "CopiMineWebsiteBridge.jar: $bytes bytes, sha256 $sha"
