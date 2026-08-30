$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$stage = Join-Path $root 'resourcepacks\build\_stage\assets'
$clock = Get-Content -Raw -Encoding UTF8 (Join-Path $stage 'minecraft\models\item\clock.json') | ConvertFrom-Json
if (@($clock.overrides | Where-Object { $_.predicate.time -ne $null }).Count -ne 65) { throw 'Vanilla clock time predicates were not preserved.' }
if (@($clock.overrides | Where-Object { $_.predicate.custom_model_data -eq 20009 }).Count -ne 1) { throw 'Donation clock override is missing.' }
if (Test-Path (Join-Path $stage 'minecraft\models\item\shield.json')) { throw 'The resource pack must not override the vanilla shield model.' }
if ((Get-ChildItem (Join-Path $stage 'copimine\textures\item\artifacts') -Filter 'gde_moy_lut_blyat_compass_*.png' -ErrorAction SilentlyContinue).Count -ne 0) { throw 'Retired donation compass textures must not be shipped.' }
if ((Get-ChildItem (Join-Path $stage 'copimine\textures\item\artifacts') -Filter 'vremya_platit_nalogi_clock_*.png').Count -ne 64) { throw 'Donation clock directional frames are incomplete.' }
Write-Host 'Directional special-item model validation passed.'
