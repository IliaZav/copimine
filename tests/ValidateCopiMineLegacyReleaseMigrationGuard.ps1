$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$packageScript = Join-Path $repositoryRoot 'scripts/package_full_release.ps1'
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ('copimine-legacy-release-guard-' + [guid]::NewGuid().ToString('N'))
$migrationProject = Join-Path $temporaryRoot 'migration-project'
$quotedConfigProject = Join-Path $temporaryRoot 'quoted-config-project'
$unquotedConfigProject = Join-Path $temporaryRoot 'unquoted-config-project'
$blockScalarConfigProject = Join-Path $temporaryRoot 'block-scalar-config-project'
$anchoredBlockScalarProject = Join-Path $temporaryRoot 'anchored-block-scalar-project'
$continuedAfterCommentConfigProject = Join-Path $temporaryRoot 'continued-after-comment-config-project'
$unicodeEscapedKeyConfigProject = Join-Path $temporaryRoot 'unicode-escaped-key-config-project'
$legacyProject = Join-Path $temporaryRoot 'legacy-project'
$legacyScalarProject = Join-Path $temporaryRoot 'legacy-scalar-project'
$legacyInlineCommentProject = Join-Path $temporaryRoot 'legacy-inline-comment-project'

function Assert-GuardRejects([string]$ProjectPath, [string]$ExpectedText) {
    $releasePath = Join-Path $ProjectPath 'release-output'
    $failureText = $null
    try {
        & $packageScript -ProjectRoot $ProjectPath -ReleaseDir $releasePath
    } catch {
        $failureText = $_.Exception.Message
    }
    if ($failureText -notlike ('*' + $ExpectedText + '*')) {
        throw ('Expected legacy-release guard message was not returned for ' + $ProjectPath + ': ' + $failureText)
    }
    if (Test-Path -LiteralPath $releasePath) {
        throw ('Legacy-release guard created output before rejecting ' + $ProjectPath)
    }
}

try {
    New-Item -ItemType Directory -Path (Join-Path $migrationProject 'tools/minecraft-26.3') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $quotedConfigProject 'copimine-narcotics') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $unquotedConfigProject 'copimine-narcotics') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $blockScalarConfigProject 'copimine-narcotics') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $anchoredBlockScalarProject 'copimine-narcotics') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $continuedAfterCommentConfigProject 'copimine-narcotics') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $unicodeEscapedKeyConfigProject 'copimine-narcotics') -Force | Out-Null
    New-Item -ItemType Directory -Path $legacyProject -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $legacyScalarProject 'copimine-narcotics') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $legacyInlineCommentProject 'copimine-narcotics') -Force | Out-Null
    New-Item -ItemType File -Path (Join-Path $migrationProject 'tools/minecraft-26.3/profile.lock.json') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $quotedConfigProject 'copimine-narcotics/config.yml') `
        -Value "client_bridge:`n  required_client_mod_version: '0.2.0+26.3'`n" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $unquotedConfigProject 'copimine-narcotics/config.yml') `
        -Value "client_bridge:`n  required_client_mod_version: 0.2.0+26.3`n" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $blockScalarConfigProject 'copimine-narcotics/config.yml') `
        -Value "client_bridge:`n  required_client_mod_version: >-`n    0.2.0+26.3`n" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $anchoredBlockScalarProject 'copimine-narcotics/config.yml') `
        -Value "client_bridge:`n  required_client_mod_version: &required_version >-`n    0.2.0+26.3`n" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $continuedAfterCommentConfigProject 'copimine-narcotics/config.yml') `
        -Value "client_bridge:`n  required_client_mod_version: # comment`n    0.2.0+26.3`n" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $unicodeEscapedKeyConfigProject 'copimine-narcotics/config.yml') `
        -Value "client_bridge:`n  `"required_client_mod_\u0076ersion`": `"0.2.0+26.3`"`n" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $legacyScalarProject 'copimine-narcotics/config.yml') `
        -Value "client_bridge:`n  required_client_mod_version: '0.1.1+1.21.1'`n" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $legacyInlineCommentProject 'copimine-narcotics/config.yml') `
        -Value "client_bridge:`n  required_client_mod_version: '0.1.1+1.21.1' # old required_client_mod_version: example`n" -Encoding UTF8

    $expectedGuard = 'cannot ship the Minecraft 26.3 client bridge'
    Assert-GuardRejects -ProjectPath $migrationProject -ExpectedText $expectedGuard
    Assert-GuardRejects -ProjectPath $quotedConfigProject -ExpectedText $expectedGuard
    Assert-GuardRejects -ProjectPath $unquotedConfigProject -ExpectedText $expectedGuard
    Assert-GuardRejects -ProjectPath $unicodeEscapedKeyConfigProject -ExpectedText $expectedGuard
    Assert-GuardRejects -ProjectPath $blockScalarConfigProject -ExpectedText $expectedGuard
    Assert-GuardRejects -ProjectPath $anchoredBlockScalarProject -ExpectedText $expectedGuard
    Assert-GuardRejects -ProjectPath $continuedAfterCommentConfigProject -ExpectedText $expectedGuard

    foreach ($legacyPath in @($legacyProject, $legacyScalarProject, $legacyInlineCommentProject)) {
        $legacyReleasePath = Join-Path $legacyPath 'release-output'
        $legacyFailure = $null
        try {
            & $packageScript -ProjectRoot $legacyPath -ReleaseDir $legacyReleasePath
        } catch {
            $legacyFailure = $_.Exception.Message
        }
        if ($legacyFailure -notlike '*Release packaging requires -SigningKeyPath or COPIMINE_RELEASE_SIGNING_KEY.*') {
            throw ('A valid legacy project was incorrectly rejected before signing-key validation: ' + $legacyFailure)
        }
        if (Test-Path -LiteralPath $legacyReleasePath) {
            throw 'Legacy release probe created output before signing-key validation'
        }
    }
    Write-Output 'Legacy full-release migration guard checks passed.'
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}
