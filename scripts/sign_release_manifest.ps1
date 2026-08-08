param(
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [Parameter(Mandatory = $true)][string]$SigningKeyPath,
    [string]$SignaturePath = ""
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command ssh-keygen -ErrorAction SilentlyContinue)) {
    throw 'ssh-keygen is required to sign the release manifest.'
}
if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
    throw "Release manifest not found: $ManifestPath"
}
if (-not (Test-Path -LiteralPath $SigningKeyPath -PathType Leaf)) {
    throw "Release signing key not found: $SigningKeyPath"
}

$generatedSignaturePath = "$ManifestPath.sig"
$signaturePath = if ($SignaturePath) { $SignaturePath } else { $generatedSignaturePath }
if (Test-Path -LiteralPath $generatedSignaturePath) {
    Remove-Item -LiteralPath $generatedSignaturePath -Force
}
if (Test-Path -LiteralPath $signaturePath) {
    Remove-Item -LiteralPath $signaturePath -Force
}

& ssh-keygen -Y sign -f $SigningKeyPath -n copimine-release $ManifestPath
if ($LASTEXITCODE -ne 0) {
    throw "ssh-keygen failed to sign the release manifest (exit code $LASTEXITCODE)."
}
if ($signaturePath -ne $generatedSignaturePath) {
    Move-Item -LiteralPath $generatedSignaturePath -Destination $signaturePath -Force
}
if (-not (Test-Path -LiteralPath $signaturePath -PathType Leaf)) {
    throw "ssh-keygen did not produce the expected signature: $signaturePath"
}

Write-Host "Signed release manifest: $signaturePath"
