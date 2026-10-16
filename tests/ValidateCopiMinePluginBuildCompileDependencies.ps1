$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$scripts = @(
  'copimine-economy-core\build-plugin.ps1',
  'copimine-election-core\build-plugin.ps1',
  'copimine-admin-plugin\build-plugin.ps1',
  'copimine-artifacts\build-plugin.ps1',
  'copimine-end-event\build-plugin.ps1',
  'copimine-narcotics\build-plugin.ps1',
  'copimine-world-core\build-plugin.ps1',
  'minecraft\server\plugins\AuthEffects\build-plugin.ps1'
)
$errors = [System.Collections.Generic.List[string]]::new()

$javaPluginCi = Get-Content -LiteralPath (Join-Path $root 'scripts/minecraft/RunJavaPluginCi.ps1') -Raw -Encoding UTF8
$legacyBuildStart = $javaPluginCi.IndexOf('foreach ($relativeDirectory in @(', [StringComparison]::Ordinal)
if ($legacyBuildStart -lt 0) {
  $errors.Add('RunJavaPluginCi.ps1 must declare the legacy plugin build order.')
} else {
  $legacyBuildEnd = $javaPluginCi.IndexOf(')) {', $legacyBuildStart, [StringComparison]::Ordinal)
  if ($legacyBuildEnd -lt 0) {
    $errors.Add('RunJavaPluginCi.ps1 must close the legacy plugin build list.')
  } else {
    $legacyBuildList = $javaPluginCi.Substring($legacyBuildStart, $legacyBuildEnd - $legacyBuildStart)
    $narcoticsBuildIndex = $legacyBuildList.IndexOf("'copimine-narcotics'", [StringComparison]::Ordinal)
    $endEventBuildIndex = $legacyBuildList.IndexOf("'copimine-end-event'", [StringComparison]::Ordinal)
    if ($narcoticsBuildIndex -lt 0 -or $endEventBuildIndex -lt 0 -or $narcoticsBuildIndex -ge $endEventBuildIndex) {
      $errors.Add('RunJavaPluginCi.ps1 must build legacy Narcotics before End Event so its typed compile dependency exists.')
    }
  }
}

foreach ($relativePath in $scripts) {
  $text = Get-Content -LiteralPath (Join-Path $root $relativePath) -Raw -Encoding UTF8
  $markers = @('$mavenRepo = if ($env:COPIMINE_MAVEN_REPOSITORY)', 'PAPER_COMPILE_DEPS')
  if ($relativePath -in @('copimine-economy-core\build-plugin.ps1', 'copimine-election-core\build-plugin.ps1', 'copimine-admin-plugin\build-plugin.ps1', 'copimine-artifacts\build-plugin.ps1', 'copimine-world-core\build-plugin.ps1', 'minecraft\server\plugins\AuthEffects\build-plugin.ps1')) {
    $markers += @('java-plugin-compile-dependencies.lock.json', 'Assert-LockedCompileFile', '$lockedDependencies')
  } elseif ($relativePath -notin @('copimine-end-event\build-plugin.ps1', 'copimine-narcotics\build-plugin.ps1')) {
    $markers += @('net\kyori', 'net\md-5', 'org\joml')
  }
  foreach ($marker in $markers) {
    if (-not $text.Contains($marker)) {
      $errors.Add("$relativePath must include Paper compile dependency marker: $marker")
    }
  }
  $lockedClasspathBranch = $text.IndexOf('if ($env:PAPER_COMPILE_DEPS)', [StringComparison]::Ordinal)
  $lockedClasspathUse = $text.IndexOf('$cp += $env:PAPER_COMPILE_DEPS', [StringComparison]::Ordinal)
  if ($lockedClasspathUse -lt 0) {
    $lockedClasspathUse = $text.IndexOf('$cp = @($paperApi) + $compileDependencies', [StringComparison]::Ordinal)
  }
  $fallbackClasspathBranch = if ($lockedClasspathBranch -ge 0) { $text.IndexOf('} else {', $lockedClasspathBranch, [StringComparison]::Ordinal) } else { -1 }
  if ($lockedClasspathBranch -lt 0 -or $lockedClasspathUse -lt $lockedClasspathBranch) {
    $errors.Add("$relativePath must consume the verified PAPER_COMPILE_DEPS classpath")
  }
  foreach ($ambientMarker in @("Get-ChildItem -Path `$groupPath", "Get-ChildItem -Path (Join-Path `$serverDir 'libraries')", 'Get-ChildItem -Path $serverLibraries')) {
    $ambientIndex = $text.IndexOf($ambientMarker, [StringComparison]::Ordinal)
    if ($ambientIndex -ge 0) {
      if ($fallbackClasspathBranch -lt 0 -or $ambientIndex -lt $fallbackClasspathBranch) {
        $errors.Add("$relativePath must keep ambient compile libraries out of the locked CI branch")
      }
    }
  }
}

$adminBuild = Get-Content -LiteralPath (Join-Path $root 'copimine-admin-plugin\build-plugin.ps1') -Raw -Encoding UTF8
foreach ($marker in @('me\clip\placeholderapi', 'thirdparty\client-mods', "'voicechat-*.jar'")) {
  if (-not $adminBuild.Contains($marker)) {
    $errors.Add("copimine-admin-plugin\\build-plugin.ps1 must provide a clean-checkout fallback for $marker")
  }
}

# Exercise the real build scripts up to javac, with private fixture files.
# The compiler is intercepted so the test cannot package or install a plugin.
function Invoke-DependencyProbe {
  param([string]$BuildScript, [string]$FixtureRoot)
  $probe = @{ Classpath = $null; Failure = $null }
  function javac {
    if ($args.Count -eq 1 -and $args[0].StartsWith('@')) {
      $compilerArgs = @(Get-Content -LiteralPath $args[0].Substring(1))
      $cpIndex = [Array]::IndexOf($compilerArgs, '-cp')
      $probe.Classpath = $compilerArgs[$cpIndex + 1].Trim('"') -replace '/', [IO.Path]::DirectorySeparatorChar
    } else {
      $cpIndex = [Array]::IndexOf($args, '-cp')
      $probe.Classpath = $args[$cpIndex + 1]
    }
    throw 'DEPENDENCY_PROBE_COMPILER_REACHED'
  }
  try { & (Join-Path $FixtureRoot $BuildScript) | Out-Null }
  catch { $probe.Failure = $_.Exception.Message }
  return $probe
}

function Write-FixtureArtifact {
  param([string]$Path, [string]$Content)
  New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
  [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
}

$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('copimine-compile-dependency-test-' + [guid]::NewGuid().ToString('N'))
$savedEnvironment = @{}
foreach ($name in @('COPIMINE_MAVEN_REPOSITORY', 'PAPER_API_JAR', 'PAPER_COMPILE_DEPS', 'PAPER_PLACEHOLDER_API_JAR', 'PAPER_VOICECHAT_API_JAR')) {
  $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
  $fixtureMaven = Join-Path $fixtureRoot 'maven'
  $lockDirectory = Join-Path $fixtureRoot 'tools/minecraft-26.3'
  New-Item -ItemType Directory -Path $lockDirectory -Force | Out-Null
  $paperPath = Join-Path $fixtureMaven 'io/papermc/paper/paper-api/26.3.build.169-beta/paper-api-26.3.build.169-beta.jar'
  $paperContent = 'locked-paper'.PadRight(2934206, '_')
  $tamperedPaperContent = 'tamper-paper'.PadRight(2934206, '_')
  Write-FixtureArtifact $paperPath $paperContent
  $paperHash = (Get-FileHash -LiteralPath $paperPath -Algorithm SHA256).Hash.ToLowerInvariant()
  @{ schemaVersion = 1; paper = @{ api = '26.3.build.169-beta'; apiSha256 = $paperHash } } |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $lockDirectory 'profile.lock.json') -Encoding UTF8
  $dependencyPaths = @(
    'net/kyori/adventure-api/5.2.0/adventure-api-5.2.0.jar',
    'org/joml/joml/1.10.9/joml-1.10.9.jar'
  )
  $lockedPaths = @()
  $dependencyEntries = @()
  foreach ($dependencyPath in $dependencyPaths) {
    $path = Join-Path $fixtureMaven $dependencyPath
    Write-FixtureArtifact $path $dependencyPath
    $lockedPaths += $path
    $dependencyEntries += @{ path = $dependencyPath; size = (Get-Item -LiteralPath $path).Length; sha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
  }
  $legacyPath = 'net/kyori/adventure-api/4.17.0/adventure-api-4.17.0.jar'
  $legacyFixture = Join-Path $fixtureMaven $legacyPath
  Write-FixtureArtifact $legacyFixture 'legacy-only'
  $legacyEntries = @(@{ path = $legacyPath; size = 11; sha256 = (Get-FileHash -LiteralPath $legacyFixture -Algorithm SHA256).Hash.ToLowerInvariant() })
  @{ schemaVersion = 1; dependencies = $legacyEntries; paper26_3Api = '26.3.build.169-beta'; paper26_3Dependencies = $dependencyEntries } | ConvertTo-Json -Depth 4 |
    Set-Content -LiteralPath (Join-Path $lockDirectory 'java-plugin-compile-dependencies.lock.json') -Encoding UTF8
  $decoyPaper = Join-Path $fixtureMaven 'io/papermc/paper/paper-api/99-R0.1-SNAPSHOT/paper-api-99-R0.1-SNAPSHOT.jar'
  Write-FixtureArtifact $decoyPaper 'wrong-paper!'
  (Get-Item -LiteralPath $decoyPaper).LastWriteTime = [DateTime]::Now.AddDays(2)
  Write-FixtureArtifact (Join-Path $fixtureMaven 'net/kyori/adventure-api/99/adventure-api-99.jar') 'wrong-adventure'
  Write-FixtureArtifact (Join-Path $fixtureRoot 'minecraft/server/libraries/ambient.jar') 'ambient'
  Write-FixtureArtifact (Join-Path $fixtureRoot 'minecraft/server/libraries/org/jetbrains/kotlin/kotlin-stdlib/1.9.21/kotlin-stdlib-1.9.21.jar') 'unrelated-kotlin'
  Write-FixtureArtifact (Join-Path $fixtureRoot 'minecraft/server/libraries/com/google/guava/guava/21.0/guava-21.0.jar') 'old-guava'
  $placeholderJar = Join-Path $fixtureRoot 'minecraft/server/plugins/PlaceholderAPI-2.12.3.jar'
  $voicechatJar = Join-Path $fixtureRoot 'minecraft/server/plugins/voicechat-test.jar'
  Write-FixtureArtifact $placeholderJar 'placeholder-api-fixture'
  Write-FixtureArtifact $voicechatJar 'voicechat-api-fixture'
  $migrationNarcoticsJar = Join-Path $fixtureRoot 'build/minecraft-26.3/plugins/jars/CopiMineNarcotics-26.3.jar'
  Write-FixtureArtifact $migrationNarcoticsJar 'typed-migration-dependency'
  foreach ($relative in @('copimine-world-core/CopiMineWorldCore.jar', 'copimine-artifacts/CopiMineArtifacts.jar')) {
    Write-FixtureArtifact (Join-Path $fixtureRoot $relative) 'typed-dependency'
  }
  Write-FixtureArtifact (Join-Path $fixtureRoot 'copimine-economy-core/CopiMineEconomyCore.jar') 'typed-project-dependency'
  Write-FixtureArtifact (Join-Path $fixtureRoot 'copimine-admin-plugin/CopiMineUltimateAdminPlus.jar') 'typed-project-dependency'
  $env:COPIMINE_MAVEN_REPOSITORY = $fixtureMaven
  foreach ($buildScript in @(
    'copimine-end-event/build-plugin.ps1',
    'copimine-narcotics/build-plugin.ps1',
    'copimine-economy-core/build-plugin.ps1',
    'copimine-election-core/build-plugin.ps1',
    'copimine-admin-plugin/build-plugin.ps1',
    'copimine-artifacts/build-plugin.ps1',
    'copimine-world-core/build-plugin.ps1',
    'minecraft/server/plugins/AuthEffects/build-plugin.ps1'
  )) {
    $fixtureScript = Join-Path $fixtureRoot $buildScript
    New-Item -ItemType Directory -Path (Split-Path -Parent $fixtureScript) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $root $buildScript) -Destination $fixtureScript
    $fixtureSource = if ($buildScript -like '*AuthEffects*') {
      Join-Path (Split-Path -Parent $fixtureScript) 'src/main/java/Probe.java'
    } elseif ($buildScript -like '*admin-plugin*') {
      Join-Path (Split-Path -Parent $fixtureScript) 'src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java'
    } else {
      Join-Path (Split-Path -Parent $fixtureScript) 'src/Probe.java'
    }
    Write-FixtureArtifact $fixtureSource 'class Probe {}'
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'tools/minecraft-26.3/protocol/src/main/java') -Force | Out-Null
    $expectedClasspath = @($paperPath)
    if ($buildScript -like '*end-event*') {
      $expectedClasspath += Join-Path $fixtureRoot 'copimine-world-core/CopiMineWorldCore.jar'
      $expectedClasspath += Join-Path $fixtureRoot 'copimine-artifacts/CopiMineArtifacts.jar'
      $expectedClasspath += $migrationNarcoticsJar
    }
    $expectedClasspath += $lockedPaths
    if ($buildScript -like '*election-core*' -or $buildScript -like '*narcotics*') {
      $expectedClasspath += Join-Path $fixtureRoot 'copimine-economy-core/CopiMineEconomyCore.jar'
    }
    if ($buildScript -like '*narcotics*') {
      $expectedClasspath += Join-Path $fixtureRoot 'copimine-admin-plugin/CopiMineUltimateAdminPlus.jar'
    }
    if ($buildScript -like '*admin-plugin*') {
      $expectedClasspath += $placeholderJar
      $expectedClasspath += $voicechatJar
      $expectedClasspath += Join-Path $fixtureRoot 'copimine-economy-core/CopiMineEconomyCore.jar'
    }
    if ($buildScript -like '*artifacts*') {
      $expectedClasspath += Join-Path $fixtureRoot 'copimine-economy-core/CopiMineEconomyCore.jar'
      $expectedClasspath += Join-Path $fixtureRoot 'copimine-admin-plugin/CopiMineUltimateAdminPlus.jar'
    }
    if ($buildScript -like '*narcotics*') {
      $expectedClasspath += Join-Path $fixtureRoot 'copimine-artifacts/CopiMineArtifacts.jar'
    }
    foreach ($scenario in @('default', 'explicit', 'missing-paper', 'corrupt-paper', 'wrong-paper-override', 'missing-dependency', 'corrupt-dependency', 'reordered-override', 'extra-override')) {
      $env:PAPER_API_JAR = $null
      $env:PAPER_COMPILE_DEPS = $null
      $env:PAPER_PLACEHOLDER_API_JAR = $null
      $env:PAPER_VOICECHAT_API_JAR = $null
      if ($scenario -eq 'explicit') {
        $env:PAPER_API_JAR = $paperPath
        $env:PAPER_COMPILE_DEPS = $lockedPaths -join [IO.Path]::PathSeparator
        $env:PAPER_PLACEHOLDER_API_JAR = $placeholderJar
        $env:PAPER_VOICECHAT_API_JAR = $voicechatJar
      }
      if ($scenario -eq 'missing-paper') { Move-Item -LiteralPath $paperPath -Destination ($paperPath + '.saved') }
      if ($scenario -eq 'corrupt-paper') { Write-FixtureArtifact $paperPath $tamperedPaperContent }
      if ($scenario -eq 'wrong-paper-override') { $env:PAPER_API_JAR = $decoyPaper }
      if ($scenario -eq 'missing-dependency') { Move-Item -LiteralPath $lockedPaths[0] -Destination ($lockedPaths[0] + '.saved') }
      if ($scenario -eq 'corrupt-dependency') { Write-FixtureArtifact $lockedPaths[0] ('X' * $dependencyPaths[0].Length) }
      if ($scenario -eq 'reordered-override') { $env:PAPER_COMPILE_DEPS = @($lockedPaths[1], $lockedPaths[0]) -join [IO.Path]::PathSeparator }
      if ($scenario -eq 'extra-override') { $env:PAPER_COMPILE_DEPS = @($lockedPaths + $decoyPaper) -join [IO.Path]::PathSeparator }
      try {
        $result = Invoke-DependencyProbe $buildScript $fixtureRoot
        if ($scenario -in @('default', 'explicit')) {
          $expected = $expectedClasspath -join [IO.Path]::PathSeparator
          if ($result.Failure -ne 'DEPENDENCY_PROBE_COMPILER_REACHED' -or $result.Classpath -ne $expected) {
            $errors.Add("$buildScript [$scenario] must compile with exactly the locked classpath in lock order; failure=$($result.Failure); classpath=$($result.Classpath)")
          }
        } elseif ($result.Classpath -or $result.Failure -notmatch 'Locked .*(missing|verification|count)') {
          $errors.Add("$buildScript [$scenario] must reject invalid dependencies before javac; failure=$($result.Failure)")
        }
      } finally {
        if ($scenario -eq 'missing-paper') { Move-Item -LiteralPath ($paperPath + '.saved') -Destination $paperPath }
        if ($scenario -eq 'corrupt-paper') { Write-FixtureArtifact $paperPath $paperContent }
        if ($scenario -eq 'missing-dependency') { Move-Item -LiteralPath ($lockedPaths[0] + '.saved') -Destination $lockedPaths[0] }
        if ($scenario -eq 'corrupt-dependency') { Write-FixtureArtifact $lockedPaths[0] $dependencyPaths[0] }
      }
    }
  }

  # Exercise End Event's legacy Paper branch with isolated artifacts. The real
  # CI pins Paper 1.21.1 by SHA; substitute fixture values only in the copied
  # script so this probe can verify dependency selection without network access.
  $legacyPaperFixture = Join-Path $fixtureRoot 'legacy-paper-api.jar'
  Write-FixtureArtifact $legacyPaperFixture 'legacy-paper-fixture'
  $legacyPaperFixtureHash = (Get-FileHash -LiteralPath $legacyPaperFixture -Algorithm SHA256).Hash.ToLowerInvariant()
  $legacyPaperFixtureSize = (Get-Item -LiteralPath $legacyPaperFixture).Length
  $legacyNarcoticsFixture = Join-Path $fixtureRoot 'copimine-narcotics/build/classes'
  Write-FixtureArtifact (Join-Path $legacyNarcoticsFixture 'me/copimine/clientbridge/Fixture.class') 'legacy-narcotics-compile-dependency'
  $endEventFixtureScript = Join-Path $fixtureRoot 'copimine-end-event/build-plugin.ps1'
  $endEventFixtureText = Get-Content -LiteralPath $endEventFixtureScript -Raw -Encoding UTF8
  $endEventFixtureText = $endEventFixtureText.Replace(
    "b8df3e7f2739e21072a5263e41b307bd30cfa8d8f72258ce27973167f8ad07c0", $legacyPaperFixtureHash)
  $endEventFixtureText = $endEventFixtureText.Replace('$paperApiSize = 2344886', ('$paperApiSize = ' + $legacyPaperFixtureSize))
  Set-Content -LiteralPath $endEventFixtureScript -Value $endEventFixtureText -Encoding UTF8
  $env:PAPER_API_JAR = $legacyPaperFixture
  $env:PAPER_COMPILE_DEPS = $null
  $legacyProbe = Invoke-DependencyProbe 'copimine-end-event/build-plugin.ps1' $fixtureRoot
  $legacyExpectedClasspath = @(
    $legacyPaperFixture,
    (Join-Path $fixtureRoot 'copimine-world-core/CopiMineWorldCore.jar'),
    (Join-Path $fixtureRoot 'copimine-artifacts/CopiMineArtifacts.jar'),
    $legacyNarcoticsFixture,
    (Join-Path $fixtureMaven $legacyPath)
  ) -join [IO.Path]::PathSeparator
  if ($legacyProbe.Failure -ne 'DEPENDENCY_PROBE_COMPILER_REACHED' -or $legacyProbe.Classpath -ne $legacyExpectedClasspath) {
    $errors.Add("copimine-end-event/build-plugin.ps1 [legacy-paper] must select the freshly compiled legacy Narcotics classes; failure=$($legacyProbe.Failure); classpath=$($legacyProbe.Classpath)")
  }
} finally {
  foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
  $resolvedFixture = [IO.Path]::GetFullPath($fixtureRoot)
  $tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
  if (-not $resolvedFixture.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Fixture cleanup path escaped the temporary directory.' }
  Remove-Item -LiteralPath $resolvedFixture -Recurse -Force -ErrorAction SilentlyContinue
}

if ($errors.Count -gt 0) {
  throw ("Plugin build dependency validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Plugin build dependency validation passed.'
