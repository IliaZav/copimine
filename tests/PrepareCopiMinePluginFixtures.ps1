[CmdletBinding()]
param(
  [string]$Root = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Root)) {
  $Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
} else {
  $Root = (Resolve-Path -LiteralPath $Root).Path
}

$pluginDir = Join-Path $Root 'minecraft\server\plugins'
New-Item -ItemType Directory -Path $pluginDir -Force | Out-Null

# These are release fixtures, not unbounded download instructions. Every
# URL, filename, size and SHA-256 is pinned so a clean CI checkout gets the
# same server plugin inputs as the local release audit.
$fixtures = @(
  @{
    Name = 'GrimAC'
    FileName = 'GrimAC.jar'
    Uri = 'https://cdn.modrinth.com/data/LJNGWSvH/versions/sbcMLWYt/grimac-bukkit-2.3.74-40684fb.jar'
    Size = 12476972
    Sha256 = '7f1df9c02b52c1d2f69949a0c465781ed8c53dd0c52234feec01785e675ff5d1'
  },
  @{
    Name = 'Chunky'
    FileName = 'Chunky-Bukkit-1.4.40.jar'
    Uri = 'https://hangarcdn.papermc.io/plugins/pop4959/Chunky/versions/1.4.40/PAPER/Chunky-Bukkit-1.4.40.jar'
    Size = 296244
    Sha256 = '2a5477fc80f71012e15ade1ce34dbeb836e17623b28db112492c0f1443c09721'
  },
  @{
    Name = 'SeeMore'
    FileName = 'SeeMore-1.0.2.jar'
    Uri = 'https://cdn.modrinth.com/data/IEt1Yy3F/versions/QXCh3qCi/SeeMore-1.0.2.jar'
    Size = 107284
    Sha256 = '8d06d342489947a296f07fbd012ea0e9842242f5af8d534f2dc91b9d2f0721e6'
  }
)

foreach ($fixture in $fixtures) {
  $destination = Join-Path $pluginDir $fixture.FileName
  if (-not (Test-Path -LiteralPath $destination -PathType Leaf)) {
    Write-Host "Downloading pinned $($fixture.Name) fixture..."
    Invoke-WebRequest -Uri $fixture.Uri -OutFile $destination
  }

  $item = Get-Item -LiteralPath $destination
  if ($item.Length -ne $fixture.Size) {
    throw "$($fixture.Name) fixture has unexpected size. Expected=$($fixture.Size) Actual=$($item.Length)"
  }
  $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash.ToLowerInvariant()
  if ($actual -ne $fixture.Sha256) {
    throw "$($fixture.Name) fixture SHA-256 mismatch. Expected=$($fixture.Sha256) Actual=$actual"
  }
  Write-Host "Pinned $($fixture.Name) verified SHA256=$actual size=$($item.Length)"
}

# The performance/readiness validator also checks the plugin-owned config
# shape.  These are deterministic release fixtures, not generated server
# state, so a clean CI checkout can validate the same paths as a local stack.
$configFixtures = @(
  @{
    Name = 'Chunky'
    Source = Join-Path $Root 'tests\fixtures\plugin-configs\Chunky\config.yml'
    Destination = Join-Path $pluginDir 'Chunky\config.yml'
  },
  @{
    Name = 'SeeMore'
    Source = Join-Path $Root 'tests\fixtures\plugin-configs\SeeMore\config.yml'
    Destination = Join-Path $pluginDir 'SeeMore\config.yml'
  }
)

foreach ($fixture in $configFixtures) {
  if (-not (Test-Path -LiteralPath $fixture.Source -PathType Leaf)) {
    throw "$($fixture.Name) config fixture is missing: $($fixture.Source)"
  }
  $destinationDirectory = Split-Path -Parent $fixture.Destination
  New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null
  if (-not (Test-Path -LiteralPath $fixture.Destination -PathType Leaf)) {
    Copy-Item -LiteralPath $fixture.Source -Destination $fixture.Destination -Force
    Write-Host "Materialized deterministic $($fixture.Name) config fixture."
  } else {
    Write-Host "Existing $($fixture.Name) config retained."
  }
}

Write-Host 'CopiMine pinned server plugin fixtures are ready.'
