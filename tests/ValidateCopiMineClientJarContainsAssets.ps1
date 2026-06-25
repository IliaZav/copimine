$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$jar = Join-Path $root 'CopiMineClient\build\libs\CopiMineClient-0.1.0.jar'
if (-not (Test-Path $jar)) { throw 'CopiMineClient jar not found. Build the client first.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($jar)
try {
  foreach ($entry in @(
    'fabric.mod.json',
    'me/copimine/client/CopiMineClient.class',
    'me/copimine/client/ClientBridgeProtocol.class',
    'assets/copimineclient/textures/visuals/green_noise_overlay.png',
    'assets/copimineclient/textures/visuals/scanlines.png'
  )) {
    if (-not ($zip.Entries | Where-Object FullName -eq $entry)) {
      throw "Missing CopiMineClient jar entry: $entry"
    }
  }
} finally {
  $zip.Dispose()
}
Write-Host 'CopiMineClient jar asset validation passed.'
