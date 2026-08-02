$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$common = Join-Path $root 'deploy\shared\common.sh'
$backup = Join-Path $root 'deploy\ubuntu\backup.sh'
$service = Join-Path $root 'admin-web\deploy\copimine-backup.service'
$timer = Join-Path $root 'admin-web\deploy\copimine-backup.timer'
$commonText = Get-Content -Raw -LiteralPath $common
$backupText = Get-Content -Raw -LiteralPath $backup
$serviceText = Get-Content -Raw -LiteralPath $service
$timerText = Get-Content -Raw -LiteralPath $timer

foreach ($needle in @(
    'copimine_backup_database',
    'pg_dump',
    '--format=custom',
    '--no-owner',
    'copimine_prune_daily_backups',
    'keep="${1:-3}"',
    'copimine-daily-[0-9]{8}-[0-9]{6}',
    'rm -f -- "$COPIMINE_BACKUP_DIR/${stem}${suffix}"'
)) {
    if ($commonText -notmatch [regex]::Escape($needle) -and $commonText -notmatch $needle) {
        throw "Shared backup helper is missing required hardening: $needle"
    }
}
foreach ($needle in @('copimine_backup_snapshot', 'copimine_backup_database', 'copimine_prune_daily_backups 3', '.manifest')) {
    if ($backupText -notmatch [regex]::Escape($needle)) { throw "Daily backup script is missing: $needle" }
}
foreach ($needle in @('Type=oneshot', 'ExecStart=/opt/copimine/deploy/ubuntu/backup.sh')) {
    if ($serviceText -notmatch [regex]::Escape($needle)) { throw "Backup service is missing: $needle" }
}
foreach ($needle in @('OnCalendar=', 'Persistent=true', 'RandomizedDelaySec=', 'Unit=copimine-backup.service')) {
    if ($timerText -notmatch [regex]::Escape($needle)) { throw "Backup timer is missing: $needle" }
}
if ($commonText -notmatch 'copimine-backup\.timer') { throw 'Installer must install the backup timer.' }
if ($commonText -notmatch 'systemctl enable copimine-backup\.timer') { throw 'Installer must enable the backup timer.' }

Write-Host 'Daily backup validation passed.'
