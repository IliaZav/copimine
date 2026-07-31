$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$javaPath = Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$botPath = Join-Path $root 'admin-web\backend\discord_bot.py'
$java = Get-Content -LiteralPath $javaPath -Raw -Encoding UTF8
$bot = Get-Content -LiteralPath $botPath -Raw -Encoding UTF8
$errors = New-Object System.Collections.Generic.List[string]

function Require-Text([string]$name, [string]$text, [string]$needle) {
  if (-not $text.Contains($needle)) { $errors.Add("$name missing: $needle") }
}

Require-Text 'Book formatter' $java 'private List<String> applicationBookPages'
Require-Text 'Book question model' $java 'private record ApplicationQuestion'
Require-Text 'Book italic question title' $java 'ChatColor.ITALIC'
Require-Text 'Book JSON answer parser' $java 'parseApplicationAnswers'
Require-Text 'Discord formatter' $bot 'def format_application_answers'
Require-Text 'Discord snapshot formatting' $bot '"statement": format_application_answers'
Require-Text 'Discord candidate summary formatting' $bot 'format_application_answers(row.get("statement")'

if ($errors.Count -gt 0) {
  Write-Host 'CopiMine application presentation validation FAILED:' -ForegroundColor Red
  foreach ($item in $errors) { Write-Host " - $item" -ForegroundColor Red }
  exit 1
}

Write-Host 'CopiMine application presentation validation passed.' -ForegroundColor Green
