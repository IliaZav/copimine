$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$stage = Join-Path $root 'resourcepacks\build\_stage\assets'
if (-not (Test-Path (Join-Path $stage 'minecraft\models\item\compass.json'))) { throw 'Build the resource pack before validating special item models.' }
$compass = Get-Content -Raw -Encoding UTF8 (Join-Path $stage 'minecraft\models\item\compass.json') | ConvertFrom-Json
$clock = Get-Content -Raw -Encoding UTF8 (Join-Path $stage 'minecraft\models\item\clock.json') | ConvertFrom-Json
$shield = Get-Content -Raw -Encoding UTF8 (Join-Path $stage 'minecraft\models\item\shield.json') | ConvertFrom-Json
if (@($compass.overrides | Where-Object { $_.predicate.angle -ne $null }).Count -ne 33) { throw 'Vanilla compass angle predicates were not preserved.' }
if (@($compass.overrides | Where-Object { $_.predicate.custom_model_data -eq 20010 }).Count -ne 1) { throw 'Donation compass override is missing.' }
if (@($clock.overrides | Where-Object { $_.predicate.time -ne $null }).Count -ne 65) { throw 'Vanilla clock time predicates were not preserved.' }
if (@($clock.overrides | Where-Object { $_.predicate.custom_model_data -eq 20009 }).Count -ne 1) { throw 'Donation clock override is missing.' }
if (@($shield.overrides | Where-Object { $_.predicate.blocking -ne $null }).Count -ne 1 -or @($shield.overrides | Where-Object { $_.predicate.custom_model_data -eq 20006 }).Count -ne 1) { throw 'Shield vanilla/custom overrides are incomplete.' }
$shieldCustomIndex = [array]::IndexOf([array]$shield.overrides, @($shield.overrides | Where-Object { $_.predicate.custom_model_data -eq 20006 })[0])
$shieldBlockingIndex = [array]::IndexOf([array]$shield.overrides, @($shield.overrides | Where-Object { $_.predicate.blocking -ne $null })[0])
if ($shieldCustomIndex -lt 0 -or $shieldBlockingIndex -lt 0 -or $shieldBlockingIndex -le $shieldCustomIndex) {
    throw 'Shield blocking override must come after the custom model override so a blocking custom shield keeps the vanilla entity renderer.'
}
if ((Get-ChildItem (Join-Path $stage 'copimine\textures\item\artifacts') -Filter 'gde_moy_lut_blyat_compass_*.png').Count -ne 32) { throw 'Donation compass directional frames are incomplete.' }
if ((Get-ChildItem (Join-Path $stage 'copimine\textures\item\artifacts') -Filter 'vremya_platit_nalogi_clock_*.png').Count -ne 64) { throw 'Donation clock directional frames are incomplete.' }
Write-Host 'Directional special-item model validation passed.'
