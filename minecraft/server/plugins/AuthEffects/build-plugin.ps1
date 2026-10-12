param([string]$PaperApi = "")
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = Join-Path $root 'src/main/java'
$resources = Join-Path $root 'src/main/resources'
$classes = Join-Path $root 'build/classes'
$jar = Join-Path $root '..\AuthEffects.jar'
$mavenRepo = if ($env:COPIMINE_MAVEN_REPOSITORY) {
  $env:COPIMINE_MAVEN_REPOSITORY
} else {
  Join-Path $env:USERPROFILE '.m2\repository'
}
if (-not $PaperApi) { $PaperApi = $env:PAPER_API_JAR }
if (-not $PaperApi) {
  $PaperApi = Get-ChildItem -LiteralPath $mavenRepo -Recurse -Filter 'paper-api-*-R0.1-SNAPSHOT.jar' -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $PaperApi -or -not (Test-Path -LiteralPath $PaperApi)) { throw 'Paper API jar not found.' }
$cp = @($PaperApi)
if ($env:PAPER_COMPILE_DEPS) {
  $cp += $env:PAPER_COMPILE_DEPS -split [IO.Path]::PathSeparator
} else {
  if (Test-Path $mavenRepo) {
    foreach ($group in @('net\kyori', 'net\md-5', 'org\joml')) {
      $groupPath = Join-Path $mavenRepo $group
      if (Test-Path $groupPath) {
        $cp += Get-ChildItem -Path $groupPath -Filter '*.jar' -Recurse | ForEach-Object FullName
      }
    }
  }
  $serverLibraries = Join-Path (Split-Path -Parent (Split-Path -Parent $root)) 'libraries'
  if (Test-Path $serverLibraries) {
    $cp += Get-ChildItem -Path $serverLibraries -Filter '*.jar' -Recurse | ForEach-Object FullName
  }
}
Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = Get-ChildItem -LiteralPath $src -Recurse -Filter '*.java' -File | Select-Object -ExpandProperty FullName
javac -proc:none -encoding UTF-8 -cp ($cp -join [IO.Path]::PathSeparator) -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed for AuthEffects.' }
Copy-Item -LiteralPath (Join-Path $resources 'plugin.yml') -Destination (Join-Path $classes 'plugin.yml') -Force
Remove-Item -LiteralPath $jar -Force -ErrorAction SilentlyContinue
jar --create --file $jar --date=2020-01-01T00:00:00Z -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar packaging failed for AuthEffects.' }
Write-Host "Built $jar"
