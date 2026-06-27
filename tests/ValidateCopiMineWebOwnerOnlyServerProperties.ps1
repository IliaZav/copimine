. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'OWNER_ONLY_SERVER_PROPERTY_KEYS = {' 'admin-web must define owner-only server.properties keys.'
Require-Regex $mainPy 'async def patch_server_properties\([\s\S]{0,400}Depends\(require_panel_admin_context\)' 'server.properties patch must inspect full panel context, not only require_admin.'
Require-Contains $mainPy 'if keys & OWNER_ONLY_SERVER_PROPERTY_KEYS and normalize_admin_role(context.get("role")) != "owner":' 'server.properties owner-only keys must be blocked for non-owner admins.'
Require-Regex $mainPy 'detail="[^"]*server\.properties[^"]*"' 'server.properties endpoint must return an explicit owner-only error detail.'

Throw-IfErrors 'ValidateCopiMineWebOwnerOnlyServerProperties'
