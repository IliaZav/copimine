. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$config = Read-Utf8 (Join-Path $root 'copimine-narcotics\config.yml')
$runtime = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')

Require-Contains $config 'server_overlay:' 'Narcotics config must declare server overlay runtime settings.'
Require-Contains $config 'use_titles: true' 'Narcotics config must declare whether title-based overlay fallback is used.'
Require-Contains $runtime 'supported via title glyph fallback; may temporarily override other titles' 'VisualRuntime status must document that server overlay is title-glyph fallback.'

Throw-IfErrors 'ValidateCopiMineVisualRuntimeTitleOverlayDocumented'
