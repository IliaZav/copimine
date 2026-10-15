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

foreach ($relativePath in $scripts) {
  $text = Get-Content -LiteralPath (Join-Path $root $relativePath) -Raw -Encoding UTF8
  $markers = @('$mavenRepo = if ($env:COPIMINE_MAVEN_REPOSITORY)', 'PAPER_COMPILE_DEPS')
  if ($relativePath -notin @('copimine-end-event\build-plugin.ps1', 'copimine-narcotics\build-plugin.ps1')) {
    $markers += @('net\kyori', 'net\md-5', 'org\joml')
  }
  foreach ($marker in $markers) {
    if (-not $text.Contains($marker)) {
      $errors.Add("$relativePath must include Paper compile dependency marker: $marker")
    }
  }
  $lockedClasspathBranch = $text.IndexOf('if ($env:PAPER_COMPILE_DEPS)', [StringComparison]::Ordinal)
  $lockedClasspathUse = $text.IndexOf('$cp += $env:PAPER_COMPILE_DEPS', [StringComparison]::Ordinal)
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
foreach ($name in @('COPIMINE_MAVEN_REPOSITORY', 'PAPER_API_JAR', 'PAPER_COMPILE_DEPS')) {
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
  foreach ($relative in @('copimine-world-core/CopiMineWorldCore.jar', 'copimine-artifacts/CopiMineArtifacts.jar')) {
    Write-FixtureArtifact (Join-Path $fixtureRoot $relative) 'typed-dependency'
  }
  $env:COPIMINE_MAVEN_REPOSITORY = $fixtureMaven
  foreach ($buildScript in @('copimine-end-event/build-plugin.ps1', 'copimine-narcotics/build-plugin.ps1')) {
    $fixtureScript = Join-Path $fixtureRoot $buildScript
    New-Item -ItemType Directory -Path (Split-Path -Parent $fixtureScript) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $root $buildScript) -Destination $fixtureScript
    Write-FixtureArtifact (Join-Path (Split-Path -Parent $fixtureScript) 'src/Probe.java') 'class Probe {}'
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'tools/minecraft-26.3/protocol/src/main/java') -Force | Out-Null
    $expectedClasspath = @($paperPath)
    if ($buildScript -like '*end-event*') {
      $expectedClasspath += Join-Path $fixtureRoot 'copimine-world-core/CopiMineWorldCore.jar'
      $expectedClasspath += Join-Path $fixtureRoot 'copimine-artifacts/CopiMineArtifacts.jar'
    }
    $expectedClasspath += $lockedPaths
    if ($buildScript -like '*narcotics*') {
      $expectedClasspath += Join-Path $fixtureRoot 'copimine-artifacts/CopiMineArtifacts.jar'
    }
    foreach ($scenario in @('default', 'explicit', 'missing-paper', 'corrupt-paper', 'wrong-paper-override', 'missing-dependency', 'corrupt-dependency', 'reordered-override', 'extra-override')) {
      $env:PAPER_API_JAR = $null
      $env:PAPER_COMPILE_DEPS = $null
      if ($scenario -eq 'explicit') {
        $env:PAPER_API_JAR = $paperPath
        $env:PAPER_COMPILE_DEPS = $lockedPaths -join [IO.Path]::PathSeparator
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
