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
# Compile against the pinned 26.3 candidates, not against the loose jars in each
# module directory. Those are stale copies from earlier local builds: on this
# checkout they no longer matched the lock (CopiMineWorldCore 040c9462 vs the
# pinned e6a2703e), so the plugin was compiled against an older sibling API than
# CI used, and identical sources produced different class bytes. The pinned
# candidate is the one artifact whose digest the lock actually vouches for.
$isLegacyPaper = $paperApiSha256 -eq $legacyPaperApiSha256
$pinnedCandidate = {
  param([object] $Lock, [string] $PluginName, [string[]] $Fallbacks)
  $plainFallbacks = @($Fallbacks | ForEach-Object { Join-Path $releaseRoot $_ })
  # The legacy 1.21.1 branch keeps its literal module paths: the dependency probe
  # builds a fixture checkout and asserts that exact classpath, so resolving it
  # through the lock would look for files the fixture never creates.
  if ($isLegacyPaper) { return $plainFallbacks[0] }
  # A migration module candidate replaces the same plugin as a baseline entry,
  # so modules are searched first; baseline rows carry the first-party plugins.
  $rows = @($Lock.modules) + @($Lock.unchangedBaseline)
  $row = @($rows | Where-Object { $_.pluginName -eq $PluginName }) | Select-Object -First 1
  $relative = if ($row -and $row.candidate -and $row.candidate.buildArtifact) {
    $row.candidate.buildArtifact -replace '/', [IO.Path]::DirectorySeparatorChar
  }
  # The Java plugin gate builds these modules one by one from source, so the
  # pinned jar may not exist yet when this plugin is compiled. Fall back to the
  # module directory jar the gate does produce; the pinned file wins whenever it
  # is present, which is the case the release pin checks.
  $candidates = @()
  if ($relative) { $candidates += (Join-Path $releaseRoot $relative) }
  $candidates += $plainFallbacks
  $chosen = $candidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
  return $(if ($chosen) { $chosen } else { $candidates[0] })
}
# $compileLock describes the third-party compile graph; the first-party sibling
# plugins are pinned in the server plugin lock. The dependency probe compiles a
# fixture checkout that contains neither the lock nor the pinned candidates, so
# a missing lock leaves this empty and every lookup falls through to the module
# directory jars the probe does create.
$serverPluginLockPath = Join-Path $releaseRoot 'tools/minecraft-26.3/server-plugins.lock.json'
$serverPluginLock = if (Test-Path -LiteralPath $serverPluginLockPath -PathType Leaf) {
  Get-Content -LiteralPath $serverPluginLockPath -Raw | ConvertFrom-Json
} else { $null }
$narcoticsDependency = if ($isLegacyPaper) {
  Join-Path $releaseRoot 'copimine-narcotics\build\classes'
} else {
  & $pinnedCandidate $serverPluginLock 'CopiMineNarcotics' @(
    'build\minecraft-26.3\plugins\jars\CopiMineNarcotics-26.3.jar',
    'copimine-narcotics\build\libs\CopiMineNarcotics-26.3.jar',
    'copimine-narcotics\build\classes')
}
foreach ($dependency in @(
  (& $pinnedCandidate $serverPluginLock 'CopiMineWorldCore' @('copimine-world-core\CopiMineWorldCore.jar')),
  (& $pinnedCandidate $serverPluginLock 'CopiMineArtifacts' @('copimine-artifacts\CopiMineArtifacts.jar')),
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
# Compile encounter-owned shared helpers in the same invocation. They are
# deliberately absent from Narcotics.jar and must not depend on build order.
$bridgeSourceRoot = Join-Path $releaseRoot 'tools\minecraft-26.3\protocol\src\main\java'
foreach ($helper in @('EncounterGenerationPolicy', 'KaguneRingFrame', 'BossAnimationPlayback')) {
  $helperSource = Join-Path $bridgeSourceRoot ("me\copimine\protocol\$helper.java")
  if (-not (Test-Path -LiteralPath $helperSource -PathType Leaf)) {
    throw "Required encounter protocol source missing: $helperSource"
  }
  $sources += $helperSource
}
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
# COPIMINE_DEPENDENCY_PROBE lets the compile-dependency validator shadow `javac`
# with a function to inspect the classpath; an absolute path would bypass that
# shadow and run a real compiler against its fixture jars.
$javacCommand = 'javac'
if ($env:JAVA_HOME -and -not $env:COPIMINE_DEPENDENCY_PROBE) {
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
# and the runtime expects.
#
# This class is compiled here from its source rather than copied out of a
# previous Gradle run. Copying made the jar depend on build history: a checkout
# where Gradle had already run got the class and a clean checkout did not, so
# the same sources produced two different digests and the pinned lock could only
# ever match one of them. Compiling always is what makes the candidate
# reproducible.
$bridgeClass = Join-Path $classes 'me\copimine\protocol\EncounterGenerationPolicy.class'
$bridgeSourceRoot = Join-Path $releaseRoot 'tools\minecraft-26.3\protocol\src\main\java'
$bridgeSourceFile = Join-Path $bridgeSourceRoot 'me\copimine\protocol\EncounterGenerationPolicy.java'
if (Test-Path -LiteralPath $bridgeSourceFile -PathType Leaf) {
  New-Item -ItemType Directory -Path (Split-Path -Parent $bridgeClass) -Force | Out-Null
  & $javacCommand ('-proc:none', '-encoding', 'UTF-8', '-cp',
    ($cp -join [IO.Path]::PathSeparator), '-sourcepath', $bridgeSourceRoot,
    '-d', $classes, $bridgeSourceFile)
  if ($LASTEXITCODE -ne 0) { throw 'Failed to compile the shared EncounterGenerationPolicy class.' }
}
if (-not (Test-Path -LiteralPath $bridgeClass -PathType Leaf)) {
  throw "Shared EncounterGenerationPolicy class could not be compiled from source: $bridgeSourceFile"
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
# Pass every entry in a fixed, ordinal-sorted order instead of letting `jar`
# walk the directory. Directory enumeration order is filesystem-dependent, so
# two runners produced identical class bytes but a different archive layout --
# and therefore a different digest for the same code.
$packagedEntries = @(Get-ChildItem -LiteralPath $classes -Recurse -File |
  ForEach-Object { $_.FullName.Substring($classes.Length + 1).Replace('\', '/') })
# Ordinal sort, not Sort-Object: PowerShell sorts with the current culture, so
# `bridge/...` landed before `CopiMineEndEvent$1.class` on one host and after it
# on another. 729 of 735 positions differed between culture-aware and ordinal
# order, which made the archive layout -- and therefore the digest -- depend on
# the runner's locale rather than on the code.
[Array]::Sort($packagedEntries, [StringComparer]::Ordinal)
# Enumerate every entry in a fixed ordinal order instead of letting `jar` walk
# the directory: directory order is filesystem-dependent, so two runners
# produced identical class bytes but a different archive layout and therefore a
# different digest. The list overflows the Windows command line, so it goes
# through an argument file as `-C <classes> <entry>` triples; that keeps each
# name relative, so nothing but the archive-relative path is stored.
$jarArgsPath = Join-Path $pluginDir 'build\jar.args'
$jarArgumentParts = @('--create', '--file', ('"' + ($jar -replace '\\', '/') + '"'),
  '--date=2020-01-01T00:00:00Z')
$classesArgument = '"' + ($classes -replace '\\', '/') + '"'
foreach ($entry in $packagedEntries) {
  $jarArgumentParts += @('-C', $classesArgument, ('"' + $entry + '"'))
}
[IO.File]::WriteAllLines($jarArgsPath, @($jarArgumentParts -join ' '), [Text.UTF8Encoding]::new($false))
& $jarCommand ("@" + ($jarArgsPath -replace '\\', '/'))
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
# The archive layout itself, so a digest mismatch can be pinpointed to an entry
# instead of re-guessing at ordering, naming, or timestamps.
$archiveDump = Join-Path $pluginDir 'build\archive-listing.txt'
$archiveDumpDigest = [Security.Cryptography.SHA256]::Create()
try {
  $listing = & $jarCommand --list --verbose --file $jar 2>&1
  [IO.File]::WriteAllLines($archiveDump, $listing, [Text.UTF8Encoding]::new($false))
  $listingBytes = [Text.Encoding]::UTF8.GetBytes(($listing -join "`n"))
  Write-Host ("Archive listing digest: {0} ({1} lines)" -f (-join ($archiveDumpDigest.ComputeHash($listingBytes) | ForEach-Object { $_.ToString('x2') })), $listing.Count)
  # A digest alone cannot say which entry differs, and CI keeps no workspace,
  # so the entries this script actually packed are printed for the log.
  Write-Host ("Archive entries: {0}" -f (($packagedEntries) -join ','))
  # Names and stored attributes only, no local-time formatting: this is the part
  # of the digest that must match everywhere, independent of the runner's zone.
  $layoutDigest = [Security.Cryptography.SHA256]::Create()
  try {
    $layoutParts = foreach ($classFile in (Get-ChildItem -LiteralPath $classes -Recurse -File | Sort-Object -Property @{ Expression = { $_.FullName }; Ascending = $true })) {
      $relative = $classFile.FullName.Substring($classes.Length + 1).Replace('\', '/')
      '{0}:{1}:{2}' -f $relative, $classFile.Length, $classFile.LastWriteTimeUtc.Ticks
    }
    $layoutBytes = [Text.Encoding]::UTF8.GetBytes(($layoutParts -join "`n"))
    Write-Host ("Payload layout digest: {0}" -f (-join ($layoutDigest.ComputeHash($layoutBytes) | ForEach-Object { $_.ToString('x2') })))
  } finally { $layoutDigest.Dispose() }
  # Per-entry CRC and size, which is what a differing archive actually differs in.
  $entryCrcLines = foreach ($entry in $packagedEntries) {
    $full = Join-Path $classes $entry.Replace('/', [IO.Path]::DirectorySeparatorChar)
    $item = Get-Item -LiteralPath $full
    '{0} {1}' -f $entry, $item.Length
  }
  Write-Host ("Entry sizes: {0}" -f (@($entryCrcLines) -join ';'))
} finally { $archiveDumpDigest.Dispose() }
Copy-Item -LiteralPath $jar -Destination $serverJar -Force
# The pinned 26.3 candidate belongs exclusively to migrationPlugins in the
# target Gradle build. This direct packager also runs with the legacy Java 21
# API in CI; copying its output there corrupts a verified Java 25 candidate.
New-Item -ItemType Directory -Path $serverDataDir -Force | Out-Null
$serverConfig = Join-Path $serverDataDir 'config.yml'
if ($SyncServerConfig -or -not (Test-Path $serverConfig)) {
  Copy-Item -LiteralPath (Join-Path $pluginDir 'config.yml') -Destination (Join-Path $serverDataDir 'config.yml') -Force
}
Write-Host "Built $jar"
Write-Host "Copied $serverJar"
