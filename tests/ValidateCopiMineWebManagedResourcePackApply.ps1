. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'MANAGED_RESOURCEPACK_URL = os.getenv(' 'admin-web must expose a managed resource-pack URL setting.'
Require-Contains $mainPy 'MANAGED_RESOURCEPACK_ZIP = PROJECT_ROOT / "resourcepacks" / "build" / "CopiMineResourcePack.zip"' 'admin-web must verify the managed local resource-pack zip.'
Require-Contains $mainPy 'def validate_managed_resourcepack_apply(url: str, sha1: str) -> tuple[str, str]:' 'admin-web must validate resource-pack apply against the managed artifact.'
Require-Contains $mainPy 'if normalized_url != MANAGED_RESOURCEPACK_URL:' 'resource-pack apply must reject arbitrary URLs.'
Require-Contains $mainPy '"resource-pack": managed_url' 'resource-pack apply must write the managed URL.'
Require-Contains $mainPy '"resource-pack-sha1": managed_sha1' 'resource-pack apply must write the verified managed SHA1.'
Require-Contains $mainPy 'async def resourcepack_apply(data: ResourcePackApplyIn, username: str = Depends(require_owner))' 'resource-pack apply must be owner-only.'
Require-Contains $mainPy '"managedSha1": local_sha1' 'resource-pack status must expose the local managed SHA1 for verification.'

Throw-IfErrors 'ValidateCopiMineWebManagedResourcePackApply'
