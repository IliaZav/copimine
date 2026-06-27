. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$legacy = Read-Utf8 $Paths.FrontendLegacy

$createSignature = @'
async def security_add_admin(data: AdminAccessIn, request: Request, username: str = Depends(require_admin))
'@
$createGuard = @'
def ensure_admin_account_create_allowed(actor_username: str, target_username: str, requested_role: str) -> None:
'@
$ownerCreateGuard = @'
if target_role == "owner" and actor_role != "owner":
'@
$ownerExistingGuard = @'
if existing_target_role == "owner" and actor_role != "owner":
'@
$updateSignature = @'
async def security_update_admin(username: str, data: AdminUpdateIn, request: Request, owner: str = Depends(require_owner))
'@
$deleteSignature = @'
async def security_delete_admin(username: str, request: Request, owner: str = Depends(require_owner))
'@
$adminsTabLine = @'
["admins",
'@
$openAdminsCta = @'
data-click="openAdminsTab()"
'@
$ownerOnlyNotice = @'
owner-only
'@

Require-Contains $mainPy $createSignature.Trim() "Creating panel admins must be available to full admins."
Require-Contains $mainPy $createGuard.Trim() "admin-web must guard which roles full admins may create."
Require-Contains $mainPy $ownerCreateGuard.Trim() "Non-owner admins must be blocked from creating owner accounts."
Require-Contains $mainPy $ownerExistingGuard.Trim() "Non-owner admins must not upsert over an existing owner account."
Require-Contains $mainPy $updateSignature.Trim() "Updating panel admins must be owner-only."
Require-Contains $mainPy $deleteSignature.Trim() "Deleting panel admins must be owner-only."
Require-Contains $legacy $adminsTabLine.Trim() "Admin cabinet must have a dedicated admins tab."
Require-Contains $legacy $openAdminsCta.Trim() "Security screen must redirect account management to the dedicated admins tab."
Require-Contains $legacy $ownerOnlyNotice.Trim() "Frontend must explain the remaining owner-only restrictions."

Throw-IfErrors "ValidateCopiMineWebOwnerOnlyAdminMutations"
