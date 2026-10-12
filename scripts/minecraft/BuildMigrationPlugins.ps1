param([Parameter(Mandatory)][string]$JavaHome)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'DownloadPinnedArtifact.ps1')
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$profileDirectory = Join-Path $root 'tools/minecraft-26.3'
$profile = Get-Content -LiteralPath (Join-Path $profileDirectory 'profile.lock.json') -Raw | ConvertFrom-Json
$serverPluginLock = Get-Content -LiteralPath (Join-Path $profileDirectory 'server-plugins.lock.json') -Raw | ConvertFrom-Json
$java = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'JavaHome must contain bin/java.exe' }
$javaVersionOutputPath = [IO.Path]::Combine([IO.Path]::GetTempPath(), 'copimine-migration-build-java-version-' + [guid]::NewGuid().ToString('N') + '.stdout')
$javaVersionErrorPath = [IO.Path]::Combine([IO.Path]::GetTempPath(), 'copimine-migration-build-java-version-' + [guid]::NewGuid().ToString('N') + '.stderr')
try {
    $javaVersionProcess = Start-Process -FilePath $java -ArgumentList '--version' -NoNewWindow -Wait -PassThru -RedirectStandardOutput $javaVersionOutputPath -RedirectStandardError $javaVersionErrorPath
    $javaVersionExitCode = $javaVersionProcess.ExitCode
    $javaVersionText = [IO.File]::ReadAllText($javaVersionOutputPath) + [Environment]::NewLine +
        [IO.File]::ReadAllText($javaVersionErrorPath)
}
finally {
    if (Test-Path -LiteralPath $javaVersionOutputPath -PathType Leaf) { Remove-Item -LiteralPath $javaVersionOutputPath -Force }
    if (Test-Path -LiteralPath $javaVersionErrorPath -PathType Leaf) { Remove-Item -LiteralPath $javaVersionErrorPath -Force }
}
$javaVersion = ($javaVersionText -split '\r?\n' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    Select-Object -First 1).Trim()
$javaMatch = [regex]::Match($javaVersion, '^(?:openjdk|java)(?: version)?\s+\D*(?<major>\d+)')
if ($javaVersionExitCode -ne 0 -or -not $javaMatch.Success) { throw "Cannot read Java version: $javaVersion" }
$javaMajor = [int]$javaMatch.Groups['major'].Value
if ($javaMajor -lt [int]$profile.minimumJava -or $javaMajor -ne [int]$profile.buildJavaMajor) {
    throw "Java $($profile.buildJavaMajor) is required for reproducible 26.3 plugin artifacts"
}

$toolchain = Join-Path $root 'build/minecraft-26.3/toolchain'
Assert-ProfileDirectoryDestination -Path $toolchain -Description 'migration build toolchain' -StopAt $root
New-Item -ItemType Directory -Path $toolchain -Force | Out-Null
Assert-ProfileDirectoryDestination -Path $toolchain -Description 'migration build toolchain' -StopAt $root
$archive = Join-Path $toolchain "gradle-$($profile.gradle.version)-bin.zip"
Assert-AtomicFileDestination -Path $archive -Description 'Gradle distribution archive' -StopAt $root
$gradleCacheValid = $false
if (Test-Path -LiteralPath $archive -PathType Leaf) {
    if ((Get-Item -LiteralPath $archive).Length -eq [long]$profile.gradle.size) {
        $gradleCacheValid = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -eq $profile.gradle.sha256
    }
}
if (-not $gradleCacheValid) {
    Save-PinnedArtifact -Uri $profile.gradle.url -Destination $archive -Algorithm SHA256 `
        -ExpectedHash $profile.gradle.sha256 -ExpectedSize ([long]$profile.gradle.size) `
        -MaximumBytes ([long]$profile.gradle.size) -StopAt $root
}
$gradle = Join-Path $toolchain "gradle-$($profile.gradle.version)/bin/gradle.bat"
Install-PinnedToolArchive -Archive $archive -Algorithm SHA256 -ExpectedHash $profile.gradle.sha256 `
    -ExpectedSize ([long]$profile.gradle.size) -DestinationDirectory (Join-Path $toolchain "gradle-$($profile.gradle.version)") `
    -ExecutableRelativePath 'bin/gradle.bat' -StopAt $root
$voiceChatPlugin = @($serverPluginLock.modules | Where-Object { $_.pluginName -ceq 'voicechat' })
if ($voiceChatPlugin.Count -ne 1) { throw 'Exactly one pinned Paper Voice Chat plugin candidate is required for the admin API classpath' }
$voiceChatPlugin = $voiceChatPlugin[0]
if ($voiceChatPlugin.filename -cne 'voicechat-bukkit-2.6.24.jar' -or
    $voiceChatPlugin.sha512 -notmatch '^[0-9a-f]{128}$' -or
    [long]$voiceChatPlugin.size -le 0 -or
    $voiceChatPlugin.url -cnotmatch ('^https://cdn\.modrinth\.com/data/' + [regex]::Escape($voiceChatPlugin.projectId) + '/versions/' + [regex]::Escape($voiceChatPlugin.versionId) + '/')) {
    throw 'Pinned Paper Voice Chat API metadata is invalid'
}
$serverPluginCandidateDirectory = Join-Path $root 'build/minecraft-26.3/server-plugins'
Assert-ProfileDirectoryDestination -Path $serverPluginCandidateDirectory -Description 'server plugin candidate directory' -StopAt $root
New-Item -ItemType Directory -Path $serverPluginCandidateDirectory -Force | Out-Null
Assert-DirectoryTreeNoReparse -Path $serverPluginCandidateDirectory -Description 'server plugin candidate directory' -StopAt $root
$voiceChatApiCandidate = Join-Path $serverPluginCandidateDirectory $voiceChatPlugin.filename
Assert-AtomicFileDestination -Path $voiceChatApiCandidate -Description 'Paper Voice Chat API candidate' -StopAt $root
$voiceChatCandidateValid = $false
if (Test-Path -LiteralPath $voiceChatApiCandidate -PathType Leaf) {
    if ((Get-Item -LiteralPath $voiceChatApiCandidate).Length -eq [long]$voiceChatPlugin.size) {
        $voiceChatCandidateValid = (Get-FileHash -LiteralPath $voiceChatApiCandidate -Algorithm SHA512).Hash.ToLowerInvariant() -eq $voiceChatPlugin.sha512
    }
}
if (-not $voiceChatCandidateValid) {
    Save-PinnedArtifact -Uri $voiceChatPlugin.url -Destination $voiceChatApiCandidate -Algorithm SHA512 `
        -ExpectedHash $voiceChatPlugin.sha512 -ExpectedSize ([long]$voiceChatPlugin.size) `
        -MaximumBytes ([long]$voiceChatPlugin.size) -StopAt $root
}
$pluginOutputRoot = Join-Path $root 'build/minecraft-26.3/plugins'
Assert-DirectoryTreeNoReparse -Path $pluginOutputRoot -Description 'first-party plugin build output' -StopAt $root
$gradleProjectCache = Join-Path $root 'build/minecraft-26.3/gradle-project-cache/plugins'
$localProjectCache = Join-Path $profileDirectory '.gradle'
Reset-GeneratedDirectory -Path $pluginOutputRoot -Description 'first-party plugin build output' -StopAt $root
Reset-GeneratedDirectory -Path $gradleProjectCache -Description 'Gradle plugin project cache' -StopAt $root
Reset-GeneratedDirectory -Path $localProjectCache -Description 'Gradle plugin local cache' -StopAt $root
$previousJava = $env:JAVA_HOME
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    & $gradle --no-daemon --project-cache-dir $gradleProjectCache -p $profileDirectory migrationPlugins --continue
    if ($LASTEXITCODE -ne 0) { throw '26.3 plugin compilation failed; existing server plugins have not been replaced' }
    Assert-DirectoryTreeNoReparse -Path $pluginOutputRoot -Description 'first-party plugin build output' -StopAt $root
    Assert-DirectoryTreeNoReparse -Path $gradleProjectCache -Description 'Gradle plugin project cache' -StopAt $root
    Assert-DirectoryTreeNoReparse -Path $localProjectCache -Description 'Gradle plugin local cache' -StopAt $root
} finally {
    $env:JAVA_HOME = $previousJava
}
