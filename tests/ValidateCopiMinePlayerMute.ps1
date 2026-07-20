$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$plugin = Get-Content -Raw (Join-Path $root 'copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java')
$backend = Get-Content -Raw (Join-Path $root 'admin-web/backend/main.py')
$runtime = Get-Content -Raw (Join-Path $root 'admin-web/frontend/assets/js/cabinet-runtime.js')

function Assert-Contains([string]$text, [string]$needle, [string]$message) {
    if ($text.IndexOf($needle, [StringComparison]::Ordinal) -lt 0) {
        throw $message
    }
}

Assert-Contains $plugin 'MicrophonePacketEvent' 'Voice packet enforcement is not wired.'
Assert-Contains $plugin 'voiceMute' 'Persistent voice mute state is missing.'
Assert-Contains $plugin 'mute <player> <5|10|30|60|off>' 'Game mute command is missing.'
Assert-Contains $plugin 'Set.of(5, 10, 30, 60)' 'Allowed mute durations are not restricted.'
Assert-Contains $backend '/api/players/{player}/mute' 'Admin mute API route is missing.'
Assert-Contains $runtime 'mutePlayer' 'Admin player card mute action is missing.'
Assert-Contains $runtime '5 мин' 'Mute duration buttons are missing.'

Write-Host 'CopiMine player mute contract: PASS'
