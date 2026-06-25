$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$build = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\build.gradle')
if ($build -match '(?i)iris|optifine|sodium') { throw 'CopiMineClient build.gradle must not depend on Iris/OptiFine/Sodium.' }
Write-Host 'CopiMineClient dependency validation passed.'
