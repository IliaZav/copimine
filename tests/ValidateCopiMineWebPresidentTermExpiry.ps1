$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\admin-web\backend\main.py'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$term = [regex]::Match($source, '(?s)def active_president_term\(conn: Any\) -> dict\[str, Any\]:.*?(?=\r?\ndef normalize_president_tax_period_hours)')
if (-not $term.Success -or $term.Value -notmatch 'ends_at>%s' -or $term.Value -notmatch 'donation_now_ms\(\)') {
    throw 'Website president term lookup must reject expired terms with the same millisecond clock as the game.'
}

Write-Host 'Website president term expiry contract OK'
