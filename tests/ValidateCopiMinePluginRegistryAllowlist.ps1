. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$manifestPath = Join-Path $root 'admin-web\backend\plugin_registry_manifest.json'
$registry = Read-Utf8 (Join-Path $root 'admin-web\backend\plugin_registry.py')
$manifest = Read-Utf8 $manifestPath

Require-Contains $mainPy 'PLUGIN_REGISTRY_MANIFEST = APP_ROOT / "backend" / "plugin_registry_manifest.json"' 'Backend must use a fixed plugin registry manifest path.'
Require-Contains $registry 'def require_registry_plugin(plugin_id: str, manifest_path: Path | None = None) -> dict[str, Any]:' 'Plugin registry must resolve plugins through an explicit allowlist helper.'
Require-Contains $registry '(manifest.get("plugins") or {}).get(plugin_id)' 'Plugin registry must only expose plugins declared in the manifest.'
Require-Contains $manifest '"CopiMineArtifacts"' 'Manifest must explicitly allowlist CopiMineArtifacts.'
Require-Contains $manifest '"CopiMineElectionCore"' 'Manifest must explicitly allowlist CopiMineElectionCore.'
Require-Contains $manifest '"CopiMineNarcotics"' 'Manifest must explicitly allowlist CopiMineNarcotics.'
Require-Contains $manifest '"CopiMineWorldCore"' 'Manifest must explicitly allowlist CopiMineWorldCore.'
Require-Contains $manifest '"CopiMineUltimateAdminPlus"' 'Manifest must explicitly allowlist CopiMineUltimateAdminPlus.'
Require-Contains $manifest '"CopiMineEconomyCore"' 'Manifest must explicitly allowlist CopiMineEconomyCore.'

Throw-IfErrors 'ValidateCopiMinePluginRegistryAllowlist'
