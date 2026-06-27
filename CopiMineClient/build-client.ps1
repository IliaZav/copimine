$ErrorActionPreference = 'Stop'

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleDir = Join-Path $projectDir '.gradle-dist'
$gradleZip = Join-Path $gradleDir 'gradle-8.10.2-bin.zip'
$gradleHome = Join-Path $gradleDir 'gradle-8.10.2'
$gradleBat = Join-Path $gradleHome 'bin\gradle.bat'

New-Item -ItemType Directory -Path $gradleDir -Force | Out-Null

if (-not (Test-Path $gradleBat)) {
  if (-not (Test-Path $gradleZip)) {
    Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.10.2-bin.zip' -OutFile $gradleZip
  }
  Expand-Archive -LiteralPath $gradleZip -DestinationPath $gradleDir -Force
}

Push-Location $projectDir
try {
  & $gradleBat build
  if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }
} finally {
  Pop-Location
}
