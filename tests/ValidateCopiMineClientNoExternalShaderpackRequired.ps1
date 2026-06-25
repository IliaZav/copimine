$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$readme = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\README_INSTALL_RU.md')
$protocol = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\PROTOCOL.md')
foreach ($needle in @('Iris','OptiFine','shaderpack')) {
  if ($readme -notmatch [regex]::Escape($needle)) {
    throw "README_INSTALL_RU.md must mention $needle in the optional-client explanation."
  }
}
if ($readme -match 'required shaderpack' -or $readme -match 'must install Iris' -or $readme -match 'must install OptiFine') {
  throw 'README_INSTALL_RU.md must not describe an external shaderpack or Iris/OptiFine as required.'
}
if ($protocol -notmatch 'Iris' -or $protocol -notmatch 'OptiFine' -or $protocol -notmatch 'shaderpack') {
  throw 'PROTOCOL.md must describe the no-Iris/no-OptiFine/no-shaderpack contract.'
}
Write-Host 'CopiMineClient no-external-shaderpack-required validation passed.'
