param(
  [switch]$SyncServerConfig,
  [switch]$CompileOnly
)

$ErrorActionPreference = 'Stop'

$pluginDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$releaseRoot = Resolve-Path (Join-Path $pluginDir '..')
$serverDir = Join-Path $releaseRoot 'minecraft\server'
$srcRoot = Join-Path $pluginDir 'src'
$classes = Join-Path $pluginDir 'build\classes'
$jar = Join-Path $pluginDir 'CopiMineEndEvent.jar'
$serverJar = Join-Path $serverDir 'plugins\CopiMineEndEvent.jar'
$serverDataDir = Join-Path $serverDir 'plugins\CopiMineEndEvent'

$mavenRepo = if ($env:COPIMINE_MAVEN_REPOSITORY) {
  $env:COPIMINE_MAVEN_REPOSITORY
} else {
  Join-Path $env:USERPROFILE '.m2\repository'
}
$profile = Get-Content -LiteralPath (Join-Path $releaseRoot 'tools/minecraft-26.3/profile.lock.json') -Raw | ConvertFrom-Json
$compileLock = Get-Content -LiteralPath (Join-Path $releaseRoot 'tools/minecraft-26.3/java-plugin-compile-dependencies.lock.json') -Raw | ConvertFrom-Json
if ($profile.schemaVersion -ne 1 -or $profile.paper.api -notmatch '^[A-Za-z0-9._+-]+$' -or [long]$profile.paper.apiSize -le 0 -or
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
# Keep the API artifact size in sync with the same lock that pins its URL and digest.
$paperApiSize = [long]$profile.paper.apiSize
# RunJavaPluginCi passes this exact legacy API explicitly. Do not discover it
# from the cache, or let a legacy override bypass the migration Gradle gate.
$legacyPaperApiSha256 = 'b8df3e7f2739e21072a5263e41b307bd30cfa8d8f72258ce27973167f8ad07c0'
if ($env:PAPER_API_JAR -and (Test-Path -LiteralPath $paperApi -PathType Leaf) -and
    (Get-FileHash -LiteralPath $paperApi -Algorithm SHA256).Hash.ToLowerInvariant() -eq $legacyPaperApiSha256) {
  $paperApiSha256 = $legacyPaperApiSha256
  $paperApiSize = 2344886
}
Assert-LockedCompileFile -Path $paperApi -Sha256 $paperApiSha256 -Size $paperApiSize

$cp = @($paperApi)
$narcoticsDependency = if ($paperApiSha256 -eq $legacyPaperApiSha256) {
  Join-Path $releaseRoot 'copimine-narcotics\build\classes'
} else {
  Join-Path $releaseRoot 'build\minecraft-26.3\plugins\jars\CopiMineNarcotics-26.3.jar'
}
foreach ($dependency in @(
  (Join-Path $releaseRoot 'copimine-world-core\CopiMineWorldCore.jar'),
  (Join-Path $releaseRoot 'copimine-artifacts\CopiMineArtifacts.jar'),
  $narcoticsDependency
)) {
  if (-not (Test-Path $dependency)) {
    throw "Required typed dependency jar is missing: $dependency"
  }
  $cp += $dependency
}
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
if ($env:PAPER_COMPILE_DEPS) {
  $cp += $env:PAPER_COMPILE_DEPS -split [IO.Path]::PathSeparator
} else {
  $cp += $compileDependencies
}

Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $classes | Out-Null
$sources = Get-ChildItem -Path $srcRoot -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName
if (-not $sources) { throw "No Java sources found under $srcRoot." }
# Keep javac below Windows' native command-line limit in nested worktrees.
# The dependency list and all source paths are still passed unchanged.
$compileArgsPath = Join-Path $pluginDir 'build\javac.args'
$compileArgs = @('-proc:none', '-encoding', 'UTF-8', '-cp',
  ('"' + (($cp -join [IO.Path]::PathSeparator) -replace '\\', '/') + '"'),
  '-d', ('"' + ($classes -replace '\\', '/') + '"'))
$compileArgs += @($sources | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' })
[IO.File]::WriteAllLines($compileArgsPath, $compileArgs, [Text.UTF8Encoding]::new($false))
& javac ("@" + $compileArgsPath)
if ($LASTEXITCODE -ne 0) { throw "javac failed for CopiMineEndEvent." }
if ($CompileOnly) {
  Write-Host "Compiled End Event sources against the current Minecraft 26.3 plugin outputs: $classes"
  return
}
Copy-Item -LiteralPath (Join-Path $pluginDir 'plugin.yml') -Destination (Join-Path $classes 'plugin.yml') -Force
Copy-Item -LiteralPath (Join-Path $pluginDir 'config.yml') -Destination (Join-Path $classes 'config.yml') -Force
Remove-Item -LiteralPath $jar -Force -ErrorAction SilentlyContinue
jar --create --file $jar --date=2020-01-01T00:00:00Z -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar packaging failed for CopiMineEndEvent." }
Copy-Item -LiteralPath $jar -Destination $serverJar -Force
New-Item -ItemType Directory -Path $serverDataDir -Force | Out-Null
$serverConfig = Join-Path $serverDataDir 'config.yml'
if ($SyncServerConfig -or -not (Test-Path $serverConfig)) {
  Copy-Item -LiteralPath (Join-Path $pluginDir 'config.yml') -Destination (Join-Path $serverDataDir 'config.yml') -Force
}
Write-Host "Built $jar"
Write-Host "Copied $serverJar"
