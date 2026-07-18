$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$builder = Join-Path $root 'resourcepacks\build-resourcepack.py'
$zipFile = Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip'
$shaFile = Join-Path $root 'resourcepacks\build\CopiMineResourcePack.sha1'
$serverProperties = Join-Path $root 'minecraft\server\server.properties'
$builderSource = Get-Content -Raw -Encoding UTF8 $builder

if ($builderSource -notmatch [regex]::Escape('ZIP_STORED')) {
  throw 'Resource-pack builder must use ZIP_STORED so the release hash is independent of zlib implementation.'
}

function Resolve-PythonInterpreter {
  foreach ($candidate in @('python', 'python3')) {
    $command = Get-Command $candidate -ErrorAction SilentlyContinue
    if (-not $command) {
      continue
    }
    $previousErrorAction = $ErrorActionPreference
    try {
      $ErrorActionPreference = 'Continue'
      $versionOutput = @(& $command.Source -c 'import sys; print(sys.version_info[0], sys.version_info[1], sep=chr(46))' 2>$null | Select-Object -First 1)
      $version = if ($versionOutput.Count) { [string]$versionOutput[0] } else { '' }
    } finally {
      $ErrorActionPreference = $previousErrorAction
    }
    $version = $version.Trim()
    if ($version -match '^3\.(1[0-9]|[2-9][0-9])$') {
      return $command.Source
    }
  }
  throw 'Python 3.10 or newer interpreter is required to build the resource pack.'
}

function Get-ConfiguredResourcePackSha {
  return ([regex]::Match((Get-Content -Raw -Encoding UTF8 $serverProperties), '(?m)^resource-pack-sha1=([0-9a-f]{40})\r?$')).Groups[1].Value.ToLowerInvariant()
}

$python = Resolve-PythonInterpreter
$storedZipSha = (Get-FileHash -Algorithm SHA1 -LiteralPath $zipFile).Hash.ToLowerInvariant()
$storedSidecarSha = (Get-Content -Raw -Encoding UTF8 $shaFile).Trim().ToLowerInvariant()
$configuredBeforeBuild = Get-ConfiguredResourcePackSha
if ($storedZipSha -ne $storedSidecarSha -or $storedZipSha -ne $configuredBeforeBuild) {
  throw "Stored resource-pack SHA1 values are out of sync: zip=$storedZipSha sidecar=$storedSidecarSha server=$configuredBeforeBuild."
}

function Invoke-ResourcePackBuild {
  & $python $builder | Out-Host
  if ($LASTEXITCODE -ne 0) {
    throw 'Resource-pack build failed while checking reproducibility.'
  }
  $actual = (Get-FileHash -Algorithm SHA1 -LiteralPath $zipFile).Hash.ToLowerInvariant()
  $reported = (Get-Content -Raw -Encoding UTF8 $shaFile).Trim().ToLowerInvariant()
  if ($actual -ne $reported) {
    throw "Resource-pack SHA1 sidecar does not match archive bytes: zip=$actual sidecar=$reported."
  }
  return $actual
}

$first = Invoke-ResourcePackBuild
Start-Sleep -Seconds 3
$second = Invoke-ResourcePackBuild

if ($first -ne $second) {
  throw "Resource-pack archive is not reproducible: first SHA1 $first, second SHA1 $second."
}

$configured = Get-ConfiguredResourcePackSha
if ($configured -ne $second) {
  throw "server.properties hash $configured does not match reproducible resource-pack SHA1 $second."
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($zipFile)
try {
  foreach ($entry in $archive.Entries) {
    if ($entry.FullName -notmatch '\.(json|mcmeta)$') {
      continue
    }
    $reader = [System.IO.StreamReader]::new($entry.Open(), [System.Text.Encoding]::UTF8, $true)
    try {
      $text = $reader.ReadToEnd()
    } finally {
      $reader.Dispose()
    }
    if ($text.Contains("`r")) {
      throw "Resource-pack text entry $($entry.FullName) contains CRLF/CR bytes and is not cross-platform reproducible."
    }
  }
} finally {
  $archive.Dispose()
}

$compressedEntries = @(& $python -c 'import sys, zipfile; archive=zipfile.ZipFile(sys.argv[1]); print(chr(10).join(entry.filename for entry in archive.infolist() if entry.compress_type != zipfile.ZIP_STORED))' $zipFile 2>$null | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if ($LASTEXITCODE -ne 0) {
  throw 'Could not inspect resource-pack ZIP compression methods.'
}
if ($compressedEntries.Count -gt 0) {
  throw "Resource-pack entries must use ZIP_STORED: $($compressedEntries -join ', ')"
}

Write-Host 'ValidateCopiMineResourcePackReproducible passed.'
