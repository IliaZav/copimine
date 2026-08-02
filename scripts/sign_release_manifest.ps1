param(
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [Parameter(Mandatory = $true)][string]$SigningKeyPath
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

$signaturePath = "$ManifestPath.sig"
if (Test-Path -LiteralPath $signaturePath) {
    Remove-Item -LiteralPath $signaturePath -Force
}

& ssh-keygen -Y sign -f $SigningKeyPath -n copimine-release $ManifestPath
if ($LASTEXITCODE -ne 0) {
    throw "ssh-keygen failed to sign the release manifest (exit code $LASTEXITCODE)."
}
if (-not (Test-Path -LiteralPath $signaturePath -PathType Leaf)) {
    throw "ssh-keygen did not produce the expected signature: $signaturePath"
}

Write-Host "Signed release manifest: $signaturePath"
