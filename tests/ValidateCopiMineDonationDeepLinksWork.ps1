. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$app = Read-FrontendBundle

Require-Contains $app 'function parseHashRoute(hashValue)' 'Frontend must parse donation deep links from location.hash.'
Require-Contains $app 'function parseInitialRouteState()' 'Frontend must bootstrap tab and donation params through the unified route parser.'
Require-Contains $app 'fromHash.params.get("session")' 'Frontend must hydrate donation session from hash query.'
Require-Contains $app 'fromHash.params.get("item")' 'Frontend must hydrate focused donation item from hash query.'
Require-Contains $app 'window.addEventListener("hashchange"' 'Frontend must react to donation deep-link navigation changes.'
Require-Contains $app 'state.donationSessionId = sessionId;' 'Hashchange handler must refresh the active donation session id.'
Require-Contains $app 'state.donationFocusItemId = itemId;' 'Hashchange handler must refresh the focused donation item id.'
Require-Contains $app '["donation-balance", "donation-shop", "donation-items"].includes(state.tab)' 'Hashchange handler must reload donation screens when only query params change.'
Require-Contains $app 'appRouteHref("donation-balance", { session: state.donationSessionId })' 'Frontend must generate direct payment links with session query.'

Throw-IfErrors 'ValidateCopiMineDonationDeepLinksWork'
