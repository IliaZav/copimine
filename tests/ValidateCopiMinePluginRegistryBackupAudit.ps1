. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$registry = Read-Utf8 (Join-Path $root 'admin-web\backend\plugin_registry.py')

Require-Contains $mainPy 'require_sensitive_confirm(request, "PLUGIN_REGISTRY_BACKUP")' 'Plugin registry backup must require explicit sensitive confirmation.'
Require-Contains $mainPy 'require_sensitive_confirm(request, "PLUGIN_REGISTRY_APPLY")' 'Plugin registry apply must require explicit sensitive confirmation.'
Require-Contains $mainPy 'require_sensitive_confirm(request, "PLUGIN_REGISTRY_RELOAD")' 'Plugin registry reload must require explicit sensitive confirmation.'
Require-Contains $mainPy 'result = await bg(backup_registry_config, plugin_id, PLUGIN_REGISTRY_BACKUPS_DIR, PLUGIN_REGISTRY_MANIFEST)' 'Plugin registry backup endpoint must create filesystem backups.'
Require-Contains $mainPy 'result = await bg(apply_registry_values, plugin_id, data.values, PLUGIN_REGISTRY_BACKUPS_DIR, PLUGIN_REGISTRY_MANIFEST)' 'Plugin registry apply endpoint must write config through the safe registry helper.'
Require-Contains $mainPy 'audit_event(username, "plugin.registry.backup"' 'Plugin registry backup must be audited.'
Require-Contains $mainPy 'audit_event(username, "plugin.registry.apply"' 'Plugin registry apply must be audited.'
Require-Contains $mainPy 'audit_event(username, "plugin.registry.reload"' 'Plugin registry reload must be audited when RCON executes reload.'
Require-Contains $mainPy 'append_panel_event("plugin-registry", "backup"' 'Plugin registry backup must be surfaced in panel events.'
Require-Contains $mainPy 'append_panel_event("plugin-registry", "apply"' 'Plugin registry apply must be surfaced in panel events.'
Require-Contains $mainPy 'append_panel_event("plugin-registry", "reload"' 'Plugin registry reload must be surfaced in panel events.'
Require-Contains $registry 'backup = backup_registry_config(plugin_id, backups_dir, manifest_path)' 'Plugin registry apply must take a backup before mutating config.'

Throw-IfErrors 'ValidateCopiMinePluginRegistryBackupAudit'
