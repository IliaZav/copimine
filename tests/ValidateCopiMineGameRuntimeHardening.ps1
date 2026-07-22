. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$common = Read-Utf8 (Join-Path $root 'deploy\shared\common.sh')
$fullReplace = Read-Utf8 (Join-Path $root 'deploy\ubuntu\copimine_full_replace.sh')
$unpack = Read-Utf8 (Join-Path $root 'deploy\ubuntu\copimine_unpack_and_verify.sh')
$service = Join-Path $root 'admin-web\deploy\copimine-game-hardening.service'
$applyScript = Join-Path $root 'deploy\ubuntu\apply_game_hardening.sh'
$runtimeScript = Join-Path $root 'deploy\shared\harden_game_runtime.py'
$policy = Join-Path $root 'deploy\templates\game-runtime-hardening.json'
$voiceTemplate = Join-Path $root 'deploy\templates\voicechat-server.properties'
$envExample = Read-Utf8 (Join-Path $root 'admin-web\.env.example')
$serverProperties = Read-Utf8 $Paths.ServerProperties
$runtime = if (Test-Path -LiteralPath $runtimeScript) { Read-Utf8 $runtimeScript } else { '' }

foreach ($path in @($service, $applyScript, $runtimeScript, $policy, $voiceTemplate)) {
    if (-not (Test-Path -LiteralPath $path)) {
        $errors.Add("Missing deployment-managed game hardening artifact: $path")
    }
}

Require-Contains $common 'copimine_sync_game_runtime_hardening()' 'Shared deployment flow must sync tracked game hardening policies.'
Require-Contains $common 'copimine_fix_runtime_plugin_ownership()' 'Runtime-hardening config files must be writable by the Minecraft service user.'
Require-Contains $common 'copimine_apply_post_start_game_hardening()' 'Shared deployment flow must apply the LuckPerms ImageFrame policy after Minecraft starts.'
Require-Contains $common 'copimine_validate_voicechat_security()' 'Shared deployment flow must gate insecure offline-mode voice chat.'
Require-Contains $common 'COPIMINE_ALLOW_INSECURE_OFFLINE_VOICECHAT' 'Voice chat exception must require explicit operator approval.'
Require-Contains $common 'COPIMINE_OFFLINE_VOICECHAT_EXCEPTION_REASON' 'Voice chat exception must include an operator-provided reason.'
Require-Contains $runtime 'lp group default permission set imageframe.create true' 'Default players must be allowed to create ImageFrame photos.'
Require-Contains $runtime 'lp group default permission set imageframe.createlimit.default true' 'Default players must receive the managed ten-photo limit.'
Require-Contains $runtime 'lp group president permission set imageframe.createlimit.president true' 'The president group must receive the managed fifty-photo limit.'
Require-Contains $runtime 'lp group {admin_group} permission set imageframe.createlimit.unlimited true' 'The configured admin group must receive unlimited ImageFrame photos.'
Require-Contains $common 'copimine_sync_game_runtime_hardening' 'Managed refresh must run the runtime hardening sync on install and upgrade.'
Require-Contains $common 'copimine_fix_runtime_plugin_ownership' 'Managed refresh must restore ownership after root-run hardening sync.'
Require-Contains (Read-Utf8 $applyScript) 'copimine_fix_runtime_plugin_ownership' 'Post-start hardening must restore plugin ownership after its root-run sync.'
Require-Contains $fullReplace 'copimine-game-hardening' 'Full replacement must start and verify the post-start hardening service.'
Require-Contains $unpack 'copimine-game-hardening' 'Unpack-and-verify must start and verify the post-start hardening service.'
Require-Contains $envExample 'COPIMINE_ALLOW_INSECURE_OFFLINE_VOICECHAT=0' 'The insecure offline voice-chat exception must be disabled by default.'
Require-Contains $envExample 'COPIMINE_OFFLINE_VOICECHAT_EXCEPTION_REASON=' 'The operator exception reason must be documented in the environment template.'
$installScript = Read-Utf8 (Join-Path $root 'deploy\ubuntu\install_release.sh')
Require-Contains $installScript 'value = ''"'' + value' 'Offline voice-chat reason must be quoted when written to shell-readable .env files.'
Require-Regex $serverProperties '(?m)^online-mode=false$' 'This hardening must not silently change the server online-mode policy.'

if (Test-Path -LiteralPath $service) {
    $serviceText = Read-Utf8 $service
    Require-Contains $serviceText 'After=copimine-minecraft.service' 'Hardening service must run only after Minecraft has started.'
    Require-Contains $serviceText 'apply_game_hardening.sh' 'Hardening service must invoke the tracked post-start script.'
    Require-Contains $serviceText 'RemainAfterExit=yes' 'Successful post-start policy application must be visible to deployment verification.'
}

if (Test-Path -LiteralPath $policy) {
    $policyText = Read-Utf8 $policy
    Require-Contains $policyText '"Enabled": true' 'ImageFrame URL filtering must be enabled.'
    Require-Contains $policyText '"https://avatars.mds.yandex.net/"' 'ImageFrame must retain the approved Yandex image host.'
    Require-Contains $policyText '"https://photos.anysex.com/"' 'ImageFrame must retain the approved photos host.'
    Require-Contains $policyText '"MaxImageFileSize": 8388608' 'ImageFrame file-size limit must be reduced to 8 MiB.'
    Require-Contains $policyText '"MaxProcessingTime": 15' 'ImageFrame processing time must be bounded to 15 seconds.'
    Require-Contains $policyText '"MaxSize": 32' 'ImageFrame map size must be reduced to a bounded value.'
    Require-Contains $policyText '"MapPacketSendingRateLimit": 20' 'ImageFrame map packet sending must have a finite rate limit.'
    Require-Contains $policyText '"default": 10' 'Default players must have a ten-photo ImageFrame creation allowance.'
    Require-Contains $policyText '"president": 50' 'Presidents must have a fifty-photo ImageFrame creation allowance.'
    Require-Contains $policyText '"admin": -1' 'Admins must have unlimited ImageFrame creation.'
    Require-Contains $policyText '"passwordHash": "BCRYPT"' 'AuthMe hardening must use the bundled BCRYPT implementation.'
    Require-Contains $policyText '"legacyHashes": ["SHA256"]' 'AuthMe must retain SHA256 migration for existing credentials.'
    Require-Contains $policyText '"minPasswordLength": 12' 'AuthMe minimum password length must be 12.'
Require-Contains $policyText '"bCryptLog2Round": 12' 'AuthMe BCRYPT cost must remain 12 rounds.'
Require-Contains $runtime 'geoIpDatabase' 'AuthMe GeoIP behavior must be managed explicitly.'
}

if (Test-Path -LiteralPath $voiceTemplate) {
    $voiceText = Read-Utf8 $voiceTemplate
    Require-Contains $voiceText 'bind_address=*' 'The public voice-chat template must be detected by the offline-mode gate rather than silently disabled.'
}

if (Test-Path -LiteralPath $runtimeScript) {
    $selfTest = & python $runtimeScript --self-test 2>&1
    if ($LASTEXITCODE -ne 0) {
        $errors.Add("Game hardening self-test failed: $selfTest")
    } elseif (($selfTest -join "`n") -notmatch 'GAME_RUNTIME_HARDENING_SELFTEST_OK') {
        $errors.Add('Game hardening self-test did not emit its success marker.')
    }
}

Throw-IfErrors 'ValidateCopiMineGameRuntimeHardening'
