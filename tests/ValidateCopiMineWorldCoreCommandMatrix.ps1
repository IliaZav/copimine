. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$worldJava = Join-Path $root 'copimine-world-core\src\me\copimine\worldcore\CopiMineWorldCore.java'
$java = Read-Utf8 $worldJava

Require-Contains $java "/cmworld border set <radius> [confirm]" 'WorldCore help must document border set.'
Require-Contains $java "/cmworld nether open|close|status" 'WorldCore help must document Nether controls.'
Require-Contains $java "/cmworld end open|close|status" 'WorldCore help must document End controls.'
Require-Contains $java "1000..100000" 'WorldCore must validate border radius.'
Require-Contains $java "cmworld border set " 'WorldCore must require confirm when shrinking border onto players.'
Require-Contains $java "cmworld nether close confirm" 'WorldCore must require confirm when closing Nether with players inside.'
Require-Contains $java "cmworld end close confirm" 'WorldCore must require confirm when closing End with players inside.'

Throw-IfErrors 'ValidateCopiMineWorldCoreCommandMatrix'
