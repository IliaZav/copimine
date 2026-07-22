$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()

function Read-Utf8([string]$relativePath) {
    $path = Join-Path $root $relativePath
    if (-not (Test-Path -LiteralPath $path)) {
        $script:errors.Add("Missing required release file: $relativePath")
        return ''
    }
    return (Get-Content -LiteralPath $path -Raw -Encoding UTF8) -replace "`r", ''
}

function Require-Contains([string]$text, [string]$needle, [string]$message) {
    if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

function Require-NotContains([string]$text, [string]$needle, [string]$message) {
    if ($text.Contains($needle)) { $script:errors.Add($message) }
}

$common = Read-Utf8 'deploy/shared/common.sh'
$properties = Read-Utf8 'minecraft/server/server.properties'
$spigot = Read-Utf8 'minecraft/server/spigot.yml'
$fullReplace = Read-Utf8 'deploy/ubuntu/copimine_full_replace.sh'
$unpack = Read-Utf8 'deploy/ubuntu/copimine_unpack_and_verify.sh'
$migrationRunner = Read-Utf8 'deploy/ubuntu/migrate.sh'
$cleanWorldState = Read-Utf8 'db/runtime/clean_world_state.sql'
$migration006 = Read-Utf8 'db/migrations/20260612_006_elections_hardening.sql'
$package = Read-Utf8 'scripts/package_full_release.ps1'
$checksums = Read-Utf8 'thirdparty/checksums.txt'
$prepareEmotecraftSh = Read-Utf8 'scripts/thirdparty/prepare_emotecraft.sh'
$prepareEmotecraftPs1 = Read-Utf8 'scripts/thirdparty/prepare_emotecraft.ps1'
$envExample = Read-Utf8 'admin-web/.env.example'
$imageFrameConfig = Read-Utf8 'minecraft/server/plugins/ImageFrame/config.yml'
$main = Read-Utf8 'admin-web/backend/main.py'

Require-Contains $common 'COPIMINE_APP_USER:-copimine' 'Common deployment default must match the dedicated copimine service account.'
Require-Contains $common 'copimine_ensure_app_user()' 'Installer must create the dedicated service account on a clean Ubuntu host.'
Require-Contains $common 'copimine_sync_server_secrets()' 'Installer must synchronize the generated RCON secret into server.properties.'
Require-Contains $common 'copimine_apply_migrations()' 'Install/update flow must apply ordered database migrations.'
Require-Contains $common 'umask 077' 'Backup creation must start with restrictive permissions.'
Require-Contains $common 'chmod 600 "$backup_path"' 'Backup archive must be readable only by root.'
Require-NotContains $common 'python3 - "$env_example" "$COPIMINE_ENV_FILE" "$postgres_password"' 'Deployment must not pass PostgreSQL passwords as Python command-line arguments.'
Require-NotContains $common "CREATE ROLE copimine LOGIN PASSWORD '" 'Deployment must not interpolate PostgreSQL passwords into SQL source text.'
Require-Contains $common ":'db_password'" 'Deployment must pass the PostgreSQL password to psql as a quoted SQL literal.'
Require-Contains $common '\gexec' 'Deployment must execute the safely quoted PostgreSQL role statement through psql.'
Require-Contains $common 'ALLOW_INSECURE_HTTP_AUTH' 'HTTP-only installer configuration must explicitly control insecure cookie authentication.'
Require-Contains $common 'TLS configuration requires ADMIN_PUBLIC_BASE_URL to use https://' 'Installer must reject a TLS configuration that advertises an HTTP public URL.'
Require-Contains $envExample 'ALLOW_INSECURE_HTTP_AUTH=0' 'HTTP authentication must be disabled by default.'
Require-Contains $envExample 'Only use HTTP authentication on a trusted temporary network.' 'The HTTP authentication tradeoff must be documented next to the setting.'

if ($properties -notmatch '(?m)^rcon\.password=__COPIMINE_RCON_PASSWORD_AT_INSTALL__$') {
    $errors.Add('Tracked server.properties must contain only the install-time RCON placeholder.')
}

Require-Contains $spigot 'restart-script: ./start.sh' 'Spigot restart must use the maintained Linux start script.'
if (-not (Test-Path -LiteralPath (Join-Path $root 'minecraft/server/start.sh'))) {
    $errors.Add('Minecraft Linux restart script is missing.')
}

foreach ($service in @(
    'admin-web/deploy/copimine-admin.service',
    'admin-web/deploy/copimine-discord-bot.service',
    'admin-web/deploy/copimine-minecraft-discord-bridge.service',
    'admin-web/deploy/copimine-minecraft.service'
)) {
    Require-Contains (Read-Utf8 $service) 'User=copimine' "$service must use the dedicated copimine service account."
}

Require-Contains $migrationRunner 'copimine_schema_migrations' 'Migration runner must keep a durable schema migration ledger.'
Require-Contains $migrationRunner 'pg_advisory_xact_lock' 'Migration runner must serialize concurrent installers.'
Require-Contains $migrationRunner 'Migration checksum mismatch' 'Migration runner must reject a modified migration that was already applied.'
Require-Contains $fullReplace 'copimine_restore_database_safely' 'Full replacement must restore the database through the guarded helper.'
Require-Contains $unpack 'copimine_restore_database_safely' 'Unpack replacement must restore the database through the guarded helper.'
Require-Contains $cleanWorldState 'artifact_pending_deliveries' 'Fresh gameplay reset must clear deferred deliveries together with the old economy.'
Require-NotContains $cleanWorldState "'site_accounts'" 'Gameplay reset must preserve registered site accounts.'
Require-Contains $migration006 'IF NOT EXISTS' 'Election hardening migration must be safe to run on a clean schema.'

Require-Contains $package 'archive --format=tar' 'Release packager must stage tracked files from Git, not copy ignored runtime files.'
Require-Contains $package '$generatedReleaseFiles' 'Release packager must use an explicit allowlist for generated artifacts.'
Require-NotContains $package '$sourceDirs = Get-ChildItem -LiteralPath $ProjectRoot -Force' 'Release packager must not recursively copy every local project entry.'
Require-NotContains $checksums 'SHA1  ' 'Third-party checksum manifest must use SHA-256 only.'
Require-Contains $prepareEmotecraftSh 'fabric-api-0.116.11+1.21.1.jar' 'Shell Fabric API preparer must use the packaged Fabric API version.'
Require-Contains $prepareEmotecraftPs1 'fabric-api-0.116.11+1.21.1.jar' 'PowerShell Fabric API preparer must use the packaged Fabric API version.'
Require-NotContains $prepareEmotecraftSh '0.116.12' 'Shell Fabric API preparer must not stage a different version.'
Require-NotContains $prepareEmotecraftPs1 '0.116.12' 'PowerShell Fabric API preparer must not stage a different version.'
Require-Contains $imageFrameConfig 'Enabled: true' 'ImageFrame URL filtering must be enabled.'
Require-Contains $imageFrameConfig 'https://avatars.mds.yandex.net/' 'ImageFrame must keep the approved Yandex host.'
Require-Contains $imageFrameConfig 'https://photos.anysex.com/' 'ImageFrame must keep the approved photos host.'
Require-Contains $imageFrameConfig 'MaxImageFileSize: 8388608' 'ImageFrame must cap downloaded image sizes.'
Require-Contains $imageFrameConfig 'MapPacketSendingRateLimit: 64' 'ImageFrame must keep map packet sending rate-limited.'
Require-Contains $imageFrameConfig 'Host: 127.0.0.1' 'Disabled ImageFrame upload service must remain bound to loopback.'
if ($imageFrameConfig -notmatch '(?ms)^  RestrictImageUrl:\s*\r?\n^    Enabled: true\s*$') {
    $errors.Add('ImageFrame remote image URL restriction must be enabled for SSRF protection.')
}

# Release-overhaul hardening checks.
Require-Contains $main 'ord(char) < 0x20' 'server.properties values must reject control characters.'
Require-Contains $main 'player = clean_mc_player(player)' 'RCON player names must be validated.'
Require-Contains $main 'target = clean_mc_player(data.target)' 'RCON target names must be validated.'
Require-Contains $main 'is_reserved_admin_username(username)' 'Player registration must reserve panel usernames.'
Require-Contains $main 'is_reserved_admin_username(new_username)' 'Player username changes must reserve panel usernames.'
Require-Contains $main '"visiblePin": ""' 'Player bank responses must not reveal the treasury PIN.'
Require-Contains $main 'visible_pin = visible_account_pin(conn, TREASURY_ACCOUNT_ID)' 'Authorized treasury views may resolve the PIN through the treasury helper.'
Require-NotContains $main "VALUES(%s,%s,%s,%s,%s,'AUTO_APPROVED'" 'Registration must not auto-approve whitelist requests.'

if ($errors.Count -gt 0) {
    throw ("Release safety hardening validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Release safety hardening validation passed.'
