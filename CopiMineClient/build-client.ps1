$ErrorActionPreference = 'Stop'

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleDir = Join-Path $projectDir '.gradle-dist'
$gradleZip = Join-Path $gradleDir 'gradle-8.10.2-bin.zip'
$gradleHome = Join-Path $gradleDir 'gradle-8.10.2'
$gradleBat = Join-Path $gradleHome 'bin\gradle.bat'
$gradleSha256 = '31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26'

New-Item -ItemType Directory -Path $gradleDir -Force | Out-Null

if (-not (Test-Path $gradleZip)) {
  Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.10.2-bin.zip' -OutFile $gradleZip
}
$actualGradleSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $gradleZip).Hash.ToLowerInvariant()
if ($actualGradleSha256 -ne $gradleSha256) {
  throw "Gradle distribution SHA256 mismatch. Expected=$gradleSha256 Actual=$actualGradleSha256"
}
if (-not (Test-Path $gradleBat)) {
  Expand-Archive -LiteralPath $gradleZip -DestinationPath $gradleDir -Force
}

Push-Location $projectDir
try {
  & $gradleBat build
  if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }
} finally {
  Pop-Location
}
