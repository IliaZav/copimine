[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sourceRoot = Join-Path $root 'copimine-narcotics/src'
$harness = Join-Path $root 'tests/ClientReadyAdmissionHarness.java'
$output = Join-Path $root 'artifacts/tests/client-ready-gate'
New-Item -ItemType Directory -Path $output -Force | Out-Null

$engine = Join-Path $sourceRoot 'me/copimine/clientbridge/ClientReadyAdmission.java'
$payloads = Join-Path $sourceRoot 'me/copimine/clientbridge/ClientReadyPayloads.java'
$files = @($engine, $payloads, $harness)
javac -encoding UTF-8 -d $output $files
if ($LASTEXITCODE -ne 0) {
    throw "javac failed for ClientReadyAdmissionHarness."
}

& java -ea -cp $output ClientReadyAdmissionHarness
if ($LASTEXITCODE -ne 0) {
    throw "ClientReadyAdmissionHarness failed."
}

Write-Host 'Client READY admission deterministic harness passed.'
