$ErrorActionPreference = 'Stop'

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleDir = Join-Path $projectDir '.gradle-dist'
$gradleZip = Join-Path $gradleDir 'gradle-8.10.2-bin.zip'
$gradleHome = Join-Path $gradleDir 'gradle-8.10.2'
$gradleBat = Join-Path $gradleHome 'bin\gradle.bat'
$gradleSha256 = '31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26'

New-Item -ItemType Directory -Path $gradleDir -Force | Out-Null

# Bounded retry: the Gradle CDN can answer 502/503/504 for a transient window.
# A single transient failure must not fail the whole build/verification job.
function Get-GradleDistribution {
  param([string]$Uri, [string]$Destination, [int]$Attempts = 5)
  for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    try {
      Invoke-WebRequest -Uri $Uri -OutFile $Destination -UseBasicParsing -TimeoutSec 300
      return
    } catch {
      if (Test-Path -LiteralPath $Destination) { Remove-Item -LiteralPath $Destination -Force -ErrorAction SilentlyContinue }
      if ($attempt -ge $Attempts) { throw }
      $delay = [Math]::Min(30, [Math]::Pow(2, $attempt))
      Write-Warning "Gradle download attempt $attempt/$Attempts failed: $($_.Exception.Message). Retrying in ${delay}s."
      Start-Sleep -Seconds $delay
    }
  }
}

if (-not (Test-Path $gradleZip)) {
  Get-GradleDistribution -Uri 'https://services.gradle.org/distributions/gradle-8.10.2-bin.zip' -Destination $gradleZip
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
  & $gradleBat build '-Dorg.gradle.jvmargs=-Xmx384m' '--max-workers=1' '--no-daemon' '--console=plain'
  if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }
} finally {
  Pop-Location
}
