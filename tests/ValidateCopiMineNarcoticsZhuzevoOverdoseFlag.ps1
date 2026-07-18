$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\use\OverdoseService.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$consume = [regex]::Match($source, '(?s)public void consume\(Player player, NarcoticDefinition definition\) \{.*?(?=\r?\n\s*public boolean shouldBlockMilk)')
if (-not $consume.Success) {
    throw 'Could not locate narcotics consumption flow.'
}

$body = $consume.Value
$flag = $body.IndexOf('configService.zhuzevoForcesOverdose()')
$overdose = $body.IndexOf('applyOverdose(player, definition,')
$normal = $body.IndexOf('applyZhuzevo(player, definition,')
if ($flag -lt 0 -or $overdose -lt 0 -or $normal -lt 0 -or $flag -gt $normal -or $overdose -gt $normal) {
    throw 'Zhuzevo force-overdose flag must route to the universal overdose path before normal Zhuzevo effects.'
}

Write-Host 'Zhuzevo force-overdose flag contract OK'
