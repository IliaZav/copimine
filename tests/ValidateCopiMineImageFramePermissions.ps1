$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sourcePath = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($needle in @(
    'PermissionAttachment',
    'imageframe.create',
    'imageframe.createlimit.unlimited',
    'imageframe.createlimit.president',
    'refreshImageFramePermissions',
    'onQuit(PlayerQuitEvent'
)) {
    if (-not $source.Contains($needle)) { $errors.Add("ImageFrame role permission support is missing: $needle") }
}

if ($errors.Count -gt 0) {
    throw ('ValidateCopiMineImageFramePermissions failed:' + "`n - " + ($errors -join "`n - "))
}

Write-Host 'ValidateCopiMineImageFramePermissions passed.'
