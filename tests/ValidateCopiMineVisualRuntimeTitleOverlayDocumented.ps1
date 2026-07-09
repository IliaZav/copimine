. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$config = Read-Utf8 (Join-Path $root 'copimine-narcotics\config.yml')
$runtime = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')

Require-Contains $config 'server_overlay:' 'Narcotics config must declare server overlay runtime settings.'
Require-Contains $config 'use_titles: false' 'Narcotics config must keep title-based overlay disabled.'
Require-Contains $runtime 'server title overlay disabled' 'VisualRuntime status must document that server overlay is disabled.'

Throw-IfErrors 'ValidateCopiMineVisualRuntimeTitleOverlayDocumented'
