$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$text = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$forbidden = @('FICTIONAL_ADMIN_GIVE_ONLY_NO_BLACK_MARKET','lunar_dust','mushroom_amber','northern_fog','old black market','admin-give-only')
$errors = @()
foreach ($marker in $forbidden) {
  if ($text -match [regex]::Escape($marker)) { $errors += "Old narcotics runtime marker remains: $marker" }
}
if ($errors.Count -gt 0) { throw ($errors -join "`n") }
Write-Host 'Old runtime removal validation passed.'
