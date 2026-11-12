<# Full source compilation using the verified Paper 26.3 Gradle classpath. #>
[CmdletBinding()]
param([string]$RepoRoot, [string]$JdkHome, [string]$GradleExecutable,
    [string]$TemporaryDirectory = [IO.Path]::GetTempPath(), [switch]$KeepOutputs)
$ErrorActionPreference = 'Stop'

function Write-FullTreeJavaArgumentFile {
    param([string]$Path, [string]$ClassPath, [string[]]$Sources)
    $lines = @('-proc:none', '-encoding', 'UTF-8', '-nowarn', '-cp',
        ('"' + $ClassPath.Replace('\', '/').Replace('"', '\"') + '"'))
    foreach ($sourcePath in $Sources) {
        $lines += '"' + $sourcePath.Replace('\', '/').Replace('"', '\"') + '"'
    }
    [IO.File]::WriteAllLines($Path, [string[]]$lines, [Text.UTF8Encoding]::new($false))
}
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path }
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$profileDirectory = Join-Path $RepoRoot 'tools/minecraft-26.3'
$profile = Get-Content -LiteralPath (Join-Path $profileDirectory 'profile.lock.json') -Raw | ConvertFrom-Json
if (-not $JdkHome) {
    $JdkHome = Join-Path $RepoRoot ("local-runtime/toolchains/temurin-{0}/jdk-{0}+1" -f $profile.buildJavaVersion)
}
$javac = Join-Path $JdkHome 'bin/javac.exe'
if (-not (Test-Path -LiteralPath $javac -PathType Leaf)) { throw "Pinned javac unavailable: $javac" }
if (-not $GradleExecutable) {
    $GradleExecutable = @(
        (Join-Path $RepoRoot ("build/minecraft-26.3/toolchain/gradle-{0}/bin/gradle.bat" -f $profile.gradle.version)),
        (Join-Path $RepoRoot ("local-runtime/toolchains/gradle-{0}/gradle-{0}/bin/gradle.bat" -f $profile.gradle.version))
    ) | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
}
if (-not $GradleExecutable) { throw 'Prepare the pinned Gradle toolchain first.' }
$tempRoot = [IO.Path]::GetFullPath($TemporaryDirectory).TrimEnd('\', '/')
if (-not (Test-Path -LiteralPath $tempRoot -PathType Container)) { throw 'Temporary directory missing.' }
$out = [IO.Path]::GetFullPath((Join-Path $tempRoot ('cm-fullcompile-' + [Guid]::NewGuid().ToString('N'))))
New-Item -ItemType Directory -Path $out | Out-Null
$argfile = Join-Path $out 'javac-arguments.txt'
$classpathFile = Join-Path $out 'classpath.txt'
$init = Join-Path $out 'classpath.gradle'
$log = Join-Path $out 'javac.log'
$classesDirectory = Join-Path $out 'classes'
New-Item -ItemType Directory -Path $classesDirectory | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
# Resolve real dependencies and validate API size/hash from profile.lock.json.
$initScript = @'
gradle.projectsEvaluated {
    def target = gradle.rootProject
    target.tasks.register('exportEndRiftFullTreeClasspath') {
        dependsOn target.tasks.named('endEventClasses'), target.tasks.named('verifyPaperApi')
        doLast {
            new File(target.property('copimineFullTreeClasspath').toString())
                .setText(target.sourceSets.endEvent.compileClasspath.asPath, 'UTF-8')
        }
    }
}
'@
[IO.File]::WriteAllText($init, $initScript, $utf8)
$priorJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JdkHome
    & $GradleExecutable -p $profileDirectory --init-script $init --console=plain ("-PcopimineFullTreeClasspath=" + $classpathFile) exportEndRiftFullTreeClasspath
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $classpathFile)) { throw 'Verified Paper classpath unavailable.' }
    $classpath = [IO.File]::ReadAllText($classpathFile, $utf8)
    $sources = @()
    foreach ($relative in @('copimine-end-event/src', 'tools/minecraft-26.3/protocol/src/main/java',
            'copimine-artifacts/src', 'copimine-world-core/src', 'copimine-economy-core/src', 'copimine-election-core/src')) {
        $sourceRoot = Join-Path $RepoRoot $relative
        if (Test-Path -LiteralPath $sourceRoot -PathType Container) {
            $sources += Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' -File | ForEach-Object { $_.FullName }
        }
    }
    $sources = @($sources | Sort-Object -Unique)
    if ($sources.Count -eq 0) { throw 'No Java sources found.' }
    Write-FullTreeJavaArgumentFile -Path $argfile -ClassPath $classpath -Sources $sources
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $javac "@$argfile" -d $classesDirectory 2>&1 | Set-Content -LiteralPath $log -Encoding utf8
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($code -ne 0) {
        Get-Content -LiteralPath $log | Select-Object -First 45 | Write-Host
        throw "Full-tree compile FAILED: exit $code. Log: $log"
    }
    $classes = @(Get-ChildItem -LiteralPath $classesDirectory -Recurse -Filter '*.class' -File)
    Write-Host "Full-tree compile PASSED: $($sources.Count) sources, $($classes.Count) classes, verified Paper $($profile.paper.api), exit 0."
    if ($KeepOutputs) { Write-Host "Full-tree evidence: $out" }
} finally {
    $env:JAVA_HOME = $priorJavaHome
    if (-not $KeepOutputs -and (Test-Path -LiteralPath $out)) {
        $resolvedOut = (Resolve-Path -LiteralPath $out).Path
        $prefix = $tempRoot + [IO.Path]::DirectorySeparatorChar + 'cm-fullcompile-'
        $item = Get-Item -LiteralPath $resolvedOut -Force
        if (-not $resolvedOut.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or
                ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Unsafe full-tree cleanup path.' }
        Remove-Item -LiteralPath $resolvedOut -Recurse -Force
    }
}
