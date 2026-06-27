. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'async def player_profile(player: str, context: dict[str, Any] = Depends(require_panel_admin_context)) -> dict[str, Any]:' 'Admin player profile endpoint must inspect full panel context before exposing PIN state.'
Require-Contains $mainPy 'if not bool(context.get("fullAccess")):' 'Only full admins and owners may see another player visible PIN.'
Require-Contains $mainPy 'profile_pin["visiblePin"] = ""' 'Restricted admins must receive a scrubbed visible PIN field.'
Require-Contains $mainPy 'async def player_bank_pin_reset(player: str, request: Request, username: str = Depends(require_admin)) -> dict[str, Any]:' 'Player PIN reset must stay behind the full-admin guard.'

Throw-IfErrors 'ValidateCopiMinePinRevealAdminOnly'
