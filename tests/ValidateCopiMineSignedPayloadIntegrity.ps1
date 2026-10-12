. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$root = Split-Path -Parent $PSScriptRoot

$verifier = Read-Utf8 (Join-Path $root 'deploy\shared\verify_payload_manifest.py')
foreach ($needle in @('payloadFiles', 'sha256', 'sizeBytes', 'unsigned payload files', 'symlink')) {
    if (-not $verifier.Contains($needle)) { $errors.Add("Signed payload verifier is missing: $needle") }
}

foreach ($relative in @('deploy\ubuntu\copimine_full_replace.sh', 'deploy\ubuntu\copimine_unpack_and_verify.sh')) {
    $text = Read-Utf8 (Join-Path $root $relative)
    foreach ($needle in @('TRUSTED_SIGNING_ALLOWED', 'PAYLOAD_VERIFIER', 'ssh-keygen -Y verify', 'verify_payload_manifest.py')) {
        if (-not $text.Contains($needle)) { $errors.Add("$relative is missing signed-release control: $needle") }
    }
    if ($text.Contains('$PAYLOAD_ROOT/deploy/release-signing.allowed" -I')) {
        $errors.Add("$relative still verifies against the archive-supplied allowlist.")
    }
}

$common = Read-Utf8 (Join-Path $root 'deploy\shared\common.sh')
foreach ($needle in @('/etc/copimine/release-signing.allowed', 'root-owned', 'group/world writable')) {
    if (-not $common.Contains($needle)) { $errors.Add("Shared release verification is missing: $needle") }
}
$ubuntuInstaller = Read-Utf8 (Join-Path $root 'deploy\ubuntu\install_release.sh')
if (-not $ubuntuInstaller.Contains('--configure-release-trust') -or -not $ubuntuInstaller.Contains('/etc/copimine/verify_payload_manifest.py') -or -not $ubuntuInstaller.Contains('copimine_unpack_and_verify.sh') -or -not $ubuntuInstaller.Contains('install -o root -g root -m 0644')) {
    $errors.Add('Ubuntu installer is missing explicit root-owned release trust provisioning.')
}

$rollback = Read-Utf8 (Join-Path $root 'deploy\windows\rollback.ps1')
foreach ($needle in @('TrustedSigningAllowed', 'ssh-keygen.exe', 'Assert-SignedPayload', 'payloadFiles')) {
    if (-not $rollback.Contains($needle)) { $errors.Add("Windows rollback is missing: $needle") }
}

$backup = Read-Utf8 (Join-Path $root 'deploy\windows\backup.ps1')
foreach ($needle in @('Copy-RedactedBackupTree', 'redacted', 'database dumps and SQLite files')) {
    if (-not $backup.Contains($needle)) { $errors.Add("Windows backup redaction is missing: $needle") }
}

$ci = Read-Utf8 (Join-Path $root '.github\workflows\ci.yml')
if (-not $ci.Contains('RunJavaPluginCi.ps1')) {
    $errors.Add('GitHub CI is missing the shared Java plugin release gate.')
}
$javaPluginCi = Read-Utf8 (Join-Path $root 'scripts\minecraft\RunJavaPluginCi.ps1')
$compileDependencyLock = Read-Utf8 (Join-Path $root 'tools\minecraft-26.3\java-plugin-compile-dependencies.lock.json')
if ($javaPluginCi -notmatch 'java-plugin-compile-dependencies\.lock\.json' -or
    $javaPluginCi -notmatch 'Save-PinnedArtifact\s+-Uri\s+\$artifactUri[\s\S]*?-ExpectedHash\s+\$locked\.sha256\s+-ExpectedSize\s+\$locked\.size\s+-MaximumBytes\s+\$locked\.size' -or
    $javaPluginCi -notmatch 'Get-FileHash\s+-Algorithm\s+SHA256' -or
    $javaPluginCi -match 'maven-dependency-plugin' -or
    $compileDependencyLock -notmatch '"artifactRepository":\s*"https://repo\.papermc\.io/repository/maven-public"') {
    $errors.Add('Shared Java CI compile dependencies must be downloaded with byte caps and verified against the content lock.')
}

Throw-IfErrors 'ValidateCopiMineSignedPayloadIntegrity'
