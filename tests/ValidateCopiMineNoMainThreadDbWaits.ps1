. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$economy = Read-Utf8 $Paths.Economy
$artifacts = Read-Utf8 $Paths.Artifacts
$narcotics = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$admin = Read-Utf8 $Paths.Admin

Require-NotRegex $economy 'public void onInteract\([^)]*\)\s*\{[\s\S]{0,4000}\.join\(' 'EconomyCore onInteract must not block on CompletableFuture.join().'
Require-NotRegex $economy 'public void onInteract\([^)]*\)\s*\{[\s\S]{0,4000}(CompletableFuture|Future)[^;\n]{0,120}\.get\(' 'EconomyCore onInteract must not block on Future.get().'
Require-NotRegex $artifacts 'InventoryClickEvent[\s\S]{0,3000}\.join\(' 'Artifacts inventory handlers must not block on CompletableFuture.join().'
Require-NotRegex $narcotics 'PlayerInteractEvent[\s\S]{0,3000}\.join\(' 'Narcotics interact handlers must not block on CompletableFuture.join().'
Require-Contains $admin 'private void openDatabaseHealthAsync(Player p)' 'AdminPlus must load DB health through an async wrapper.'
Require-Contains $admin 'dbAsyncLoad("openDatabaseHealth"' 'AdminPlus DB health GUI must fetch SQL data off the main thread.'
Require-Contains $admin 'private void openPresidentPanelAsync(Player p)' 'AdminPlus president panel must load through an async wrapper.'
Require-Contains $admin 'private void openChairPanelAsync(Player p)' 'AdminPlus chair panel must load through an async wrapper.'
Require-Contains $admin 'private void openPlayerTimelineAsync(Player admin,String name)' 'AdminPlus player timeline must load through an async wrapper.'
Require-Contains $admin 'private void legacyOpenDatabaseHealthDisabled(Player p)' 'AdminPlus sync DB health implementation must be isolated under a disabled legacy name.'
Require-Contains $admin 'private void legacyOpenPresidentPanelDisabled(Player p)' 'AdminPlus sync president panel implementation must be isolated under a disabled legacy name.'
Require-Contains $admin 'private void legacyOpenChairPanelDisabled(Player p)' 'AdminPlus sync chair panel implementation must be isolated under a disabled legacy name.'
Require-Contains $admin 'private void legacyOpenPlayerTimelineDisabled(Player admin,String name)' 'AdminPlus sync player timeline implementation must be isolated under a disabled legacy name.'
Require-Contains $admin 'private void openDatabaseHealth(Player p)throws Exception{' 'AdminPlus DB health entrypoint must remain as a thin wrapper.'
Require-Contains $admin 'openDatabaseHealthAsync(p);' 'AdminPlus DB health entrypoint must delegate to async loading.'
Require-Contains $admin 'private void openPresidentPanel(Player p) throws Exception{' 'AdminPlus president panel entrypoint must remain as a thin wrapper.'
Require-Contains $admin 'openPresidentPanelAsync(p);' 'AdminPlus president panel entrypoint must delegate to async loading.'
Require-Contains $admin 'private void openChairPanel(Player p) throws Exception{' 'AdminPlus chair panel entrypoint must remain as a thin wrapper.'
Require-Contains $admin 'openChairPanelAsync(p);' 'AdminPlus chair panel entrypoint must delegate to async loading.'
Require-Contains $admin 'private void openPlayerTimeline(Player admin,String name)throws Exception{' 'AdminPlus player timeline entrypoint must remain as a thin wrapper.'
Require-Contains $admin 'openPlayerTimelineAsync(admin,name);' 'AdminPlus player timeline entrypoint must delegate to async loading.'
Require-Contains $admin 'private boolean isChair(Player p){return cachedElectionRole(p).chair();}' 'AdminPlus chair role checks must use cache, not sync SQL.'
Require-Contains $admin 'private boolean isPresident(Player p){return cachedElectionRole(p).president();}' 'AdminPlus president role checks must use cache, not sync SQL.'

Throw-IfErrors 'ValidateCopiMineNoMainThreadDbWaits'
