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
# Paper 26.3 is compiled for class file version 69 (Java 25). Calling plain
# `javac` picks up whatever is first on PATH, which on a machine with an older
# default JDK is Java 21 and rejects every API class with "wrong version 69.0,
# should be 65.0". Pin the compiler to the JDK that JAVA_HOME names.
$javacCommand = 'javac'
if ($env:JAVA_HOME) {
  $javacInHome = Join-Path $env:JAVA_HOME 'bin/javac.exe'
  if (Test-Path -LiteralPath $javacInHome -PathType Leaf) { $javacCommand = $javacInHome }
}
& $javacCommand ("@" + $compileArgsPath)
if ($LASTEXITCODE -ne 0) { throw "javac failed for CopiMineEndEvent." }
if ($CompileOnly) {
  Write-Host "Compiled End Event sources against the current Minecraft 26.3 plugin outputs: $classes"
  return
}
Copy-Item -LiteralPath (Join-Path $pluginDir 'plugin.yml') -Destination (Join-Path $classes 'plugin.yml') -Force
Copy-Item -LiteralPath (Join-Path $pluginDir 'config.yml') -Destination (Join-Path $classes 'config.yml') -Force
# tools/minecraft-26.3/build.gradle packages one extra shared class into this
# jar: the saved-generation policy the End Rift snapshots validate with before
# the optional Narcotics bridge is available. A plain repack of `classes` drops
# it, and the pinned candidate then differs from the artifact Gradle produced
# and the runtime expects. Copy it in when the Gradle output exists.
$bridgeClass = Join-Path $classes 'me\copimine\protocol\EncounterGenerationPolicy.class'
if (-not (Test-Path -LiteralPath $bridgeClass -PathType Leaf)) {
  $bridgeSource = Join-Path $releaseRoot 'build\minecraft-26.3\plugins\classes\java\narcotics\me\copimine\protocol\EncounterGenerationPolicy.class'
  $bridgeSourceAlt = Join-Path $releaseRoot 'tools\minecraft-26.3\build\classes\java\main\me\copimine\protocol\EncounterGenerationPolicy.class'
  foreach ($candidate in @($bridgeSource, $bridgeSourceAlt)) {
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
      New-Item -ItemType Directory -Path (Split-Path -Parent $bridgeClass) -Force | Out-Null
      Copy-Item -LiteralPath $candidate -Destination $bridgeClass -Force
      $bridgeSource = $candidate
      break
    }
  }
  if (-not (Test-Path -LiteralPath $bridgeClass -PathType Leaf)) {
    throw 'The shared EncounterGenerationPolicy class required by the End Rift bridge is missing from the Gradle plugin output.'
  }
}
Remove-Item -LiteralPath $jar -Force -ErrorAction SilentlyContinue
# `jar` stamps its own version into MANIFEST.MF as Created-By. Resolving it
# through PATH let a Java 21 install write "Created-By: 21.0.10" into a jar
# compiled by JDK 25, so the same sources produced a different digest on every
# machine with a different default JDK. Pin the packer to $env:JAVA_HOME too.
$jarCommand = 'jar'
if ($env:JAVA_HOME) {
  $jarInHome = Join-Path $env:JAVA_HOME 'bin/jar.exe'
  if (Test-Path -LiteralPath $jarInHome -PathType Leaf) { $jarCommand = $jarInHome }
}
& $jarCommand --create --file $jar --date=2020-01-01T00:00:00Z -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar packaging failed for CopiMineEndEvent." }
# Provenance of the toolchain that produced the bytes, so a digest mismatch can
# be traced to the packer instead of guessed at.
$jarVersion = & $jarCommand --version 2>&1
Write-Host ("Packaged with jar: {0} ({1})" -f $jarCommand, ($jarVersion -join ' '))
Write-Host ("Compiled with javac: {0}" -f $javacCommand)
# A digest that ignores packaging order and timestamps: it tells whether two
# builds disagree about the class bytes or only about how they were zipped.
$contentDigest = [Security.Cryptography.SHA256]::Create()
try {
  $classFiles = Get-ChildItem -Path $classes -Filter '*.class' -Recurse | Sort-Object FullName
  $buffer = New-Object byte[] 65536
  foreach ($classFile in $classFiles) {
    $relative = $classFile.FullName.Substring($classes.Length + 1).Replace('\', '/')
    $nameBytes = [Text.Encoding]::UTF8.GetBytes($relative)
    [void]$contentDigest.TransformBlock($nameBytes, 0, $nameBytes.Length, $null, 0)
    $stream = [IO.File]::OpenRead($classFile.FullName)
    try {
      while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
        [void]$contentDigest.TransformBlock($buffer, 0, $read, $null, 0)
      }
    } finally { $stream.Dispose() }
  }
  [void]$contentDigest.TransformFinalBlock([byte[]]::new(0), 0, 0)
  $digestHex = -join ($contentDigest.Hash | ForEach-Object { $_.ToString('x2') })
  Write-Host ("Class content digest: {0} ({1} classes)" -f $digestHex, $classFiles.Count)
} finally { $contentDigest.Dispose() }
Copy-Item -LiteralPath $jar -Destination $serverJar -Force
# The migration lock pins build/minecraft-26.3/plugins/jars, not the staging
# copy. Leaving that stale is how the published digest silently drifted away
# from the source tree on every change, so refresh it here in one step.
$candidateJar = Join-Path $releaseRoot 'build\minecraft-26.3\plugins\jars\CopiMineEndEvent-26.3.jar'
New-Item -ItemType Directory -Path (Split-Path -Parent $candidateJar) -Force | Out-Null
Copy-Item -LiteralPath $jar -Destination $candidateJar -Force
New-Item -ItemType Directory -Path $serverDataDir -Force | Out-Null
$serverConfig = Join-Path $serverDataDir 'config.yml'
if ($SyncServerConfig -or -not (Test-Path $serverConfig)) {
  Copy-Item -LiteralPath (Join-Path $pluginDir 'config.yml') -Destination (Join-Path $serverDataDir 'config.yml') -Force
}
Write-Host "Built $jar"
Write-Host "Copied $serverJar"
