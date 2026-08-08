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
if ($ci -notmatch "expectedSha512\s*=\s*'8beac8d11ef208f1e2a8df0682b9448a9a363d2ad13ca74af43705549e72e74c9378823bf689287801cbbfc2f6ea9596201d19ccacfdfb682ee8a2ff4c4418ba'") {
    $errors.Add('CI Maven download is missing its pinned SHA512.')
}

Throw-IfErrors 'ValidateCopiMineSignedPayloadIntegrity'
