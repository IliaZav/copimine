. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$registry = Read-Utf8 (Join-Path $root 'admin-web\backend\plugin_registry.py')
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $registry 'def _resolve_project_path(relative_path: str) -> Path:' 'Plugin registry must resolve requested paths through a guarded project-root helper.'
Require-Contains $registry 'if root not in path.parents and path != root:' 'Plugin registry must reject paths escaping the repository root.'
Require-Contains $mainPy 'validate_managed_resourcepack_apply(url: str, sha1: str)' 'Managed resource pack apply must validate URL/hash instead of trusting arbitrary file paths.'

Throw-IfErrors 'ValidateCopiMineNoPathTraversal'
