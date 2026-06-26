. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$registry = Read-Utf8 (Join-Path $root 'admin-web\backend\plugin_registry.py')

Require-Contains $registry 'def _resolve_project_path(relative_path: str) -> Path:' 'Plugin registry must resolve config paths through a project-root guard.'
Require-Contains $registry 'if root not in path.parents and path != root:' 'Plugin registry must reject config paths escaping the project root.'
Require-Contains $registry 'return dict(plugin.get("editableKeys") or {})' 'Plugin registry must use explicit editable key allowlists.'
Require-Contains $registry 'schema = registry_schema(plugin)' 'Plugin registry writes must validate against schema before applying values.'
Require-Contains $registry 'if key not in schema:' 'Plugin registry validation must reject keys outside the allowlist.'
Require-Contains $registry 'yaml.safe_load' 'Plugin registry must load YAML safely.'
Require-Contains $registry 'yaml.safe_dump' 'Plugin registry must write YAML safely.'
Require-NotContains $registry 'open(config_path, "w")' 'Plugin registry should not bypass the guarded config path writer pattern.'

Throw-IfErrors 'ValidateCopiMinePluginRegistryNoArbitraryWrite'
