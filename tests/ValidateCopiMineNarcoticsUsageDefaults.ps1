$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$service = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\config\NarcoticsConfigService.java')

foreach ($marker in @('consume_cooldown_seconds: 5','zhuzevo_forces_overdose: true','usage_window_seconds: 900','overdose_threshold: 100')) {
  if ($config -notmatch [regex]::Escape($marker)) { throw "Approved usage default missing from config: $marker" }
}
foreach ($marker in @('getInt("usage.consume_cooldown_seconds", 5)','getBoolean("usage.zhuzevo_forces_overdose", true)')) {
  if ($service -notmatch [regex]::Escape($marker)) { throw "Approved usage default missing from code: $marker" }
}
if ($service -notmatch [regex]::Escape('migrateLegacyUsageDefaults()')) { throw 'Legacy usage default migration is not wired into reload.' }
if ($service -notmatch [regex]::Escape('consume_cooldown_seconds: 0", "consume_cooldown_seconds: 5')) { throw 'Cooldown migration must rewrite only the legacy zero default.' }
if ($service -notmatch [regex]::Escape('zhuzevo_forces_overdose: false", "zhuzevo_forces_overdose: true')) { throw 'Zhuzevo migration must rewrite only the legacy false default.' }
if ($service -match 'migrateLegacyUsageDefaults[\s\S]{0,900}?saveConfig\(') { throw 'Migration must not rewrite the whole config through saveConfig.' }

$chupsWeight = [regex]::Match($config, '(?m)^\s+chups:\s*$[\s\S]{0,600}?^\s+overdose_weight:\s*30\s*$')
if (-not $chupsWeight.Success) { throw 'Chups must carry an overdose weight of 30 so the fourth use crosses the threshold.' }
Write-Host 'Narcotics usage defaults and legacy migration validation passed.'