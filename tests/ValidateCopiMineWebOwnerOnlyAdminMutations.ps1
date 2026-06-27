. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$legacy = Read-Utf8 $Paths.FrontendLegacy

Require-Contains $mainPy 'async def security_add_admin(data: AdminAccessIn, request: Request, owner: str = Depends(require_owner))' 'Creating panel admins must be owner-only.'
Require-Contains $mainPy 'async def security_update_admin(username: str, data: AdminUpdateIn, request: Request, owner: str = Depends(require_owner))' 'Updating panel admins must be owner-only.'
Require-Contains $mainPy 'async def security_delete_admin(username: str, request: Request, owner: str = Depends(require_owner))' 'Deleting panel admins must be owner-only.'
Require-Contains $legacy 'state.owner' 'Frontend security screen must branch on owner role.'
Require-Contains $legacy 'Owner-only' 'Frontend must explain that admin-account mutations are owner-only.'

Throw-IfErrors 'ValidateCopiMineWebOwnerOnlyAdminMutations'
