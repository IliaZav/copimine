param([Parameter(Mandatory)][string]$JavaHome)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$profileDirectory = Join-Path $root 'tools/minecraft-26.3'
$profile = Get-Content -LiteralPath (Join-Path $profileDirectory 'profile.lock.json') -Raw | ConvertFrom-Json
$java = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'JavaHome must contain bin/java.exe' }
$versionText = (& $java --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $versionText -notmatch '(?m)^(?:openjdk|java)(?: version)?[ "]+(?<major>\d+)') { throw 'Cannot read Java version' }
if ([int]$Matches.major -lt $profile.minimumJava) { throw "Java $($profile.minimumJava)+ is required" }

$toolchain = Join-Path $root 'build/minecraft-26.3/toolchain'
New-Item -ItemType Directory -Path $toolchain -Force | Out-Null
$archive = Join-Path $toolchain "gradle-$($profile.gradle.version)-bin.zip"
if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) {
    Invoke-WebRequest -Uri $profile.gradle.url -OutFile $archive -TimeoutSec 120
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $profile.gradle.sha256) {
    throw 'Gradle archive SHA-256 mismatch; no build has been started'
}
$gradle = Join-Path $toolchain "gradle-$($profile.gradle.version)/bin/gradle.bat"
if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    Expand-Archive -LiteralPath $archive -DestinationPath $toolchain -Force
}
$previousJava = $env:JAVA_HOME
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    & $gradle --no-daemon -p $profileDirectory migrationPlugins --continue
    if ($LASTEXITCODE -ne 0) { throw '26.3 plugin compilation failed; existing server plugins have not been replaced' }
} finally {
    $env:JAVA_HOME = $previousJava
}
