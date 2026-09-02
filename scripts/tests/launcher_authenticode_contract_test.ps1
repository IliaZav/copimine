$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptRoot)
$buildScript = Get-Content -LiteralPath (Join-Path $repoRoot 'scripts/build_copimine_launcher.ps1') -Raw

function Assert-Contains([string] $text, [string] $expected, [string] $description) {
    if ($text.IndexOf($expected, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw "Authenticode contract failed: $description. Missing: $expected"
    }
}

Assert-Contains $buildScript '[string] $SignToolPath' 'build accepts an explicit Windows SDK signtool path'
Assert-Contains $buildScript '[string] $SigningCertificateThumbprint' 'build selects a certificate without putting a private key in source'
Assert-Contains $buildScript '[string] $TimestampUrl' 'build requires a trusted timestamp endpoint'
Assert-Contains $buildScript '[switch] $RequireAuthenticodeSignature' 'release gate can require a trusted signature'
Assert-Contains $buildScript 'signtool sign' 'build invokes Authenticode signing'
Assert-Contains $buildScript '/fd SHA256' 'file digest algorithm is pinned'
Assert-Contains $buildScript '/tr $TimestampUrl' 'signature receives a timestamp'
Assert-Contains $buildScript '/td SHA256' 'timestamp digest algorithm is pinned'
Assert-Contains $buildScript 'Get-AuthenticodeSignature' 'build verifies the resulting signature'
Assert-Contains $buildScript 'Status -ne ''Valid''' 'build fails closed when signing is invalid'

Write-Output 'LAUNCHER_AUTHENTICODE_CONTRACT=PASS'
