$ErrorActionPreference = 'Stop'

$pluginDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$releaseRoot = Resolve-Path (Join-Path $pluginDir '..')
$serverDir = Join-Path $releaseRoot 'minecraft\server'
$src = Join-Path $pluginDir 'src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$classes = Join-Path $pluginDir 'build\classes'
$jar = Join-Path $pluginDir 'CopiMineUltimateAdminPlus.jar'
$serverJar = Join-Path $serverDir 'plugins\CopiMineUltimateAdminPlus.jar'

$mavenRepo = if ($env:COPIMINE_MAVEN_REPOSITORY) {
  $env:COPIMINE_MAVEN_REPOSITORY
} else {
  Join-Path $env:USERPROFILE '.m2\repository'
}
$profile = Get-Content -LiteralPath (Join-Path $releaseRoot 'tools/minecraft-26.3/profile.lock.json') -Raw | ConvertFrom-Json
$compileLock = Get-Content -LiteralPath (Join-Path $releaseRoot 'tools/minecraft-26.3/java-plugin-compile-dependencies.lock.json') -Raw | ConvertFrom-Json
if ($profile.schemaVersion -ne 1 -or $profile.paper.api -notmatch '^[A-Za-z0-9._+-]+$' -or
    $profile.paper.apiSha256 -notmatch '^[0-9a-f]{64}$' -or $compileLock.schemaVersion -ne 1 -or -not $compileLock.dependencies) {
  throw 'Invalid Paper API or Java compile dependency lock.'
}

function Assert-LockedCompileFile {
  param([string]$Path, [string]$Sha256, [long]$Size = -1)
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "Locked compile dependency is missing: $Path"
  }
  if (($Size -ge 0 -and (Get-Item -LiteralPath $Path).Length -ne $Size) -or
      (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Sha256) {
    throw "Locked compile dependency failed size/SHA256 verification: $Path"
  }
}

$paperApi = $env:PAPER_API_JAR
if (-not $paperApi) {
  $paperApi = Join-Path $mavenRepo "io/papermc/paper/paper-api/$($profile.paper.api)/paper-api-$($profile.paper.api).jar"
}
$paperApiSha256 = $profile.paper.apiSha256
# Paper build 169's API JAR is pinned by the migration profile at 2,934,206 bytes.
$paperApiSize = 2934206
# RunJavaPluginCi may explicitly request this exact legacy Paper API for compatibility checks.
$legacyPaperApiSha256 = 'b8df3e7f2739e21072a5263e41b307bd30cfa8d8f72258ce27973167f8ad07c0'
if ($env:PAPER_API_JAR -and (Test-Path -LiteralPath $paperApi -PathType Leaf) -and
    (Get-FileHash -LiteralPath $paperApi -Algorithm SHA256).Hash.ToLowerInvariant() -eq $legacyPaperApiSha256) {
  $paperApiSha256 = $legacyPaperApiSha256
  $paperApiSize = 2344886
}
Assert-LockedCompileFile -Path $paperApi -Sha256 $paperApiSha256 -Size $paperApiSize

$lockedDependencies = if ($paperApiSha256 -eq $legacyPaperApiSha256) {
  @($compileLock.dependencies)
} else {
  if ($compileLock.paper26_3Api -cne $profile.paper.api -or -not $compileLock.paper26_3Dependencies) {
    throw 'Locked Paper 26.3 compile dependency graph is missing or targets a different API.'
  }
  @($compileLock.paper26_3Dependencies)
}
$compileDependencies = @()
if ($env:PAPER_COMPILE_DEPS) {
  $compileDependencies = @($env:PAPER_COMPILE_DEPS -split [IO.Path]::PathSeparator)
  if ($compileDependencies.Count -ne $lockedDependencies.Count) {
    throw 'Locked compile dependency count differs from PAPER_COMPILE_DEPS.'
  }
} else {
  $compileDependencies = @($lockedDependencies | ForEach-Object { Join-Path $mavenRepo $_.path })
}
for ($index = 0; $index -lt $lockedDependencies.Count; $index++) {
  $locked = $lockedDependencies[$index]
  if ($locked.path -notmatch '^[A-Za-z0-9._+/-]+\.jar$' -or $locked.path.Split('/') -contains '..' -or
      $locked.size -le 0 -or $locked.sha256 -notmatch '^[0-9a-f]{64}$') {
    throw "Invalid Java compile dependency lock entry: $($locked.path)"
  }
  Assert-LockedCompileFile -Path $compileDependencies[$index] -Sha256 $locked.sha256 -Size $locked.size
}

$cp = @($paperApi) + $compileDependencies
$placeholder = $env:PAPER_PLACEHOLDER_API_JAR
if (-not $placeholder) {
  $placeholder = Get-ChildItem -Path (Join-Path $serverDir 'plugins') -Filter 'PlaceholderAPI-*.jar' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName
}
if (-not $env:PAPER_COMPILE_DEPS -and -not $placeholder -and (Test-Path (Join-Path $mavenRepo 'me\clip\placeholderapi'))) {
  $placeholder = Get-ChildItem -Path (Join-Path $mavenRepo 'me\clip\placeholderapi') -Filter '*.jar' -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName
}
if ($placeholder) { $cp += $placeholder }
$voicechat = $env:PAPER_VOICECHAT_API_JAR
if ($env:PAPER_COMPILE_DEPS) {
  if (-not $voicechat -or -not (Test-Path -LiteralPath $voicechat -PathType Leaf)) {
    throw 'The locked PAPER_VOICECHAT_API_JAR artifact is required for the shared Java CI build.'
  }
} else {
  if (-not $voicechat) {
    $voicechat = Get-ChildItem -Path (Join-Path $serverDir 'plugins') -Filter 'voicechat-*.jar' -File -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
      Sort-Object LastWriteTime -Descending |
      Select-Object -First 1 -ExpandProperty FullName
  }
  if (-not $voicechat) {
    $voicechat = Get-ChildItem -Path (Join-Path $releaseRoot 'thirdparty\client-mods') -Filter 'voicechat-*.jar' -File -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
      Sort-Object LastWriteTime -Descending |
      Select-Object -First 1 -ExpandProperty FullName
  }
}
foreach ($optionalDependency in @($placeholder, $voicechat)) {
  if ($optionalDependency -and [IO.Path]::GetFileName($optionalDependency) -match '-(sources|javadoc)\.jar$') {
    throw 'Source and javadoc archives cannot be used as plugin compile dependencies.'
  }
}
if ($voicechat) { $cp += $voicechat }
if (Test-Path (Join-Path $releaseRoot 'copimine-economy-core\CopiMineEconomyCore.jar')) {
  $cp += (Join-Path $releaseRoot 'copimine-economy-core\CopiMineEconomyCore.jar')
}

Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $classes | Out-Null
javac -proc:none -encoding UTF-8 -cp ($cp -join [IO.Path]::PathSeparator) -d $classes $src
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
