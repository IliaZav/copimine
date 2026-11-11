<#
  Real full-tree type-check of the end-event plugin.

  Exists because the python probes compile EXTRACTED method bodies against
  hand-written stubs: they can pass while the plugin does not compile at all,
  which is exactly how an unimplemented Host method reached the branch. This
  script compiles every source the plugin needs with a real JDK 25 against the
  real pinned Paper API, so "it compiles" is a fact rather than an assumption.

  Requires JDK 25 (Paper 26.3 targets class file v69; plain Java 21 cannot read
  it) and the pinned Paper 1.21.1 API jar in the local maven repository.
#>
[CmdletBinding()]
param(
  [string]$RepoRoot,
  [string]$JdkHome = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.2.10-hotspot'
)

$ErrorActionPreference = 'Stop'

# Resolve inside the body: $PSScriptRoot is empty in a param default block.
if (-not $RepoRoot) {
  $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}

$javac = Join-Path $JdkHome 'bin/javac.exe'
if (-not (Test-Path -LiteralPath $javac -PathType Leaf)) {
  throw "JDK 25 javac not found at $javac"
}

$pinnedPaperApi = Join-Path $env:USERPROFILE '.m2/repository/io/papermc/paper/paper-api/1.21.1-R0.1-SNAPSHOT/paper-api-1.21.1-R0.1-20250328.161643-128.jar'
if (-not (Test-Path -LiteralPath $pinnedPaperApi -PathType Leaf)) {
  throw "Pinned Paper API jar not found at $pinnedPaperApi"
}

# Third-party jars the plugin imports directly.
$jars = @($pinnedPaperApi)
$jars += Join-Path $RepoRoot 'build/minecraft-26.3/toolchain/gradle-9.8.1/lib/guava-33.4.6-jre.jar'
$jars += Join-Path $RepoRoot 'build/minecraft-26.3/toolchain/gradle-9.8.1/lib/plugins/snakeyaml-2.6.jar'
$m2 = Join-Path $env:USERPROFILE '.m2/repository'
foreach ($group in @('org/joml', 'net/kyori', 'com/google/code/gson', 'net/md-5/bungeecord-chat')) {
  $path = Join-Path $m2 $group
  if (Test-Path -LiteralPath $path) {
    $jars += Get-ChildItem -LiteralPath $path -Recurse -Filter '*.jar' -File |
      Where-Object { $_.Name -notmatch 'sources|javadoc' } |
      ForEach-Object { $_.FullName }
  }
}
$jars = $jars | Where-Object { Test-Path -LiteralPath $_ } | Sort-Object -Unique
$classpath = $jars -join ';'

# Every source the plugin needs: its own tree, the protocol module, and the
# sibling modules it calls into.
$sources = @()
$sources += Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'copimine-end-event/src') -Recurse -Filter '*.java' -File | ForEach-Object { $_.FullName }
$protocolSrc = Join-Path $RepoRoot 'tools/minecraft-26.3/protocol/src/main/java'
if (Test-Path -LiteralPath $protocolSrc) {
  $sources += Get-ChildItem -LiteralPath $protocolSrc -Recurse -Filter '*.java' -File | ForEach-Object { $_.FullName }
}
foreach ($module in @('copimine-artifacts', 'copimine-world-core', 'copimine-economy-core', 'copimine-election-core')) {
  $moduleSrc = Join-Path $RepoRoot "$module/src"
  if (Test-Path -LiteralPath $moduleSrc) {
    $sources += Get-ChildItem -LiteralPath $moduleSrc -Recurse -Filter '*.java' -File | ForEach-Object { $_.FullName }
  }
}
if ($sources.Count -eq 0) { throw 'No java sources found.' }

$out = Join-Path ([IO.Path]::GetTempPath()) ("cm-fullcompile-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force -Path $out | Out-Null
$argfile = Join-Path ([IO.Path]::GetTempPath()) ("cm-fullcompile-" + [Guid]::NewGuid().ToString('N').Substring(0, 8) + '.txt')
$lines = @('-proc:none', '-encoding', 'UTF-8', '-nowarn', '-cp', $classpath)
$lines += $sources
Set-Content -LiteralPath $argfile -Value $lines -Encoding ascii

try {
  $log = Join-Path $out 'javac.log'
  # javac writes deprecation notes to stderr; under ErrorActionPreference Stop a
  # redirect would turn a warning into a terminating error. Judge the run by the
  # exit status and the error lines alone.
  $previous = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    & $javac "@$argfile" -d $out 2>&1 | Set-Content -LiteralPath $log -Encoding utf8
    $code = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previous
  }
  $errors = @(Get-Content -LiteralPath $log | Where-Object { $_ -match 'error:' })
  if ($code -ne 0) {
    $errors | Select-Object -First 40 | ForEach-Object { Write-Host $_ }
    throw "Full-tree compile FAILED: $($errors.Count) error line(s), exit $code. Sources: $($sources.Count)."
  }
  $classes = @(Get-ChildItem -LiteralPath $out -Recurse -Filter '*.class' -File)
  Write-Host "Full-tree compile PASSED: $($sources.Count) sources, $($classes.Count) classes, exit 0."
} finally {
  Remove-Item -LiteralPath $argfile -Force -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $out -Recurse -Force -ErrorAction SilentlyContinue
}
