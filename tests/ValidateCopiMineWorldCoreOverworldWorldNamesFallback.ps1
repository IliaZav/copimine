. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$worldCore = Read-Utf8 (Join-Path $root 'copimine-world-core\src\me\copimine\worldcore\CopiMineWorldCore.java')

Require-Contains $worldCore 'resolveOverworldWorldNames(FileConfiguration cfg)' 'WorldCore must resolve overworld world names through a safe fallback helper.'
Require-Contains $worldCore 'names.add("world");' 'WorldCore must fall back to the default overworld name when config world_names is empty.'
Require-Contains $worldCore 'world.getEnvironment() == World.Environment.NORMAL' 'WorldCore must fall back to the first NORMAL world if "world" is missing.'
Require-Contains $worldCore 'overworldLimit.worldNames()' 'WorldCore status output must show active overworld names.'

Throw-IfErrors 'ValidateCopiMineWorldCoreOverworldWorldNamesFallback'
