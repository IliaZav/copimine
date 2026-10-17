# CopiMine v5 implementation handoff

Preserve the approved dark green visual design and persistent global header. This deliverable is a LOCAL REVIEW PROTOTYPE, not production code with a complete authorization/security layer. UI operations work against browser state; SQLite is a companion fixture catalogue only. Never deploy the demo dock, default accounts, raw fixtures, services environment file or secret/private plugin registry publicly.

## Implemented review surfaces
Public home, gallery, map, catalogue/search/category/currency filters, product descriptions, basket, donation/payment states, download continuation, rules/CMS pages; guest sign-in/register, remember-me, confirmation logout, account orders/support/settings, launcher-request-only confirmation. No live provider is implied.
Administrator: 12 consolidated sections, 41 searchable function destinations and 675 alias strings. Live function results replace only content, preserve focus and header; wrong-layout and near-typo search. Player selector with heads, common skin resolver, original inventory GUI with snapshots, recoveries; readable/technical logs, local event stream and optional explicitly selected file following; private recipe editor; elections and treasury; catalogue/finance/jobs, roles, CMS and integrations.

## Sources of truth
- data/catalog-current.json and sources/catalog-source.json: reviewed site-launcher-audit branch, blob 8d564579d1b10dd44edce6b409e8951abf93bd28; 41 records, 31 public. No teleport compass. Never silently fall back to main or merge the different event-branch night_cloak.
- data/materials.json: IDs, actual images, official EN and 608 RU labels. Finish localization from the current official language asset, not guessed names.
- data/map-source.json: supplied Anvil snapshot, 4096 chunks, 1024 square blocks. Current renderer and the tiny JourneyMap capture are different sources. Keep source timestamp and bounds truthful.
- docs/INTEGRATIONS.md: contracts and trust boundaries; services/notifications.py: transport skeleton, default preview.

## Feature ownership
Keep source modules focused: workspace-v5 player/snapshot/skin/recovery; domain-v5 normalization/redaction/inventory validation; logs-v5 viewer/import/follow; recipes-v5 local ingredient/editor history; elections-v5 phase/programme/treasury presentation; search-v5 feature aliases; admin-v4 and operations-v4 retain earlier commerce/content/role controls; app.js provides the shared shell and legacy audit bridge. Full navigation does not use location.reload. Same-route actions restore focus/scroll; filters mutate their own list/results region.

Do not migrate the mixed prototype persistence or direct mutable handlers as-is. Production must put commands behind authenticated typed endpoints and a job bus; UI should distinguish pending, accepted, applied, failed and stale. Keep existing CSS/component look, but replace data access with explicit stores/adapters rather than extending a monolith.

## Acceptance gates before public deployment
1. Compare deployed catalogue and database constraints against the selected source snapshot; owner resolves differences.
2. Enforce server authorization per operation, audit sensitive reads/writes; deny hidden catalogue entries in API, not only CSS.
3. Session cookies HttpOnly/Secure/SameSite, CSRF and rotation/revocation; external login state/nonce/PKCE checks; secure launcher single-use approval exchange.
4. Reset requests scoped to verified contacts, one-use expiry and abuse prevention. Keys only server side, no passwords in notifications/logs. Actual delivery tested explicitly by owner.
5. Authoritative server-assigned skin store including offline accounts; no nickname-only skin lookup. Real inventory capture time and stale state.
6. Per-plugin event adapters, redaction before storage, SSE cursor and disconnect states. Unknown action preserved. Never turn a warning into an automatic permanent ban.
7. Atomic purchase/delivery and AR/CM transaction handling, provider webhook verification and no duplicate issuance. Recovery/rollback preserves protected plugin DB state.
8. Existing full election/RP constraints and treasury transitions tested against the game plugin, not just local UI phase buttons.
9. Delayed map publication enforced server-side, private snapshots inaccessible; CMS revisions and uploads validated.
10. Replace synthetic fixtures with authorized imports; run security/integration/browser tests and compare screenshot/layout against this prototype on desktop and mobile.

## Build/review
Open CopiMine_Prototype_v5.html directly, or OPEN_DEMO.bat. Optional START.bat runs a localhost-only Python preview; no production services are installed. Rebuild with python build.py --output CopiMine_Prototype_v5.html. Unit scripts in tests. QA environment/limitations in docs/QA.md. Asset files included; no client JAR, font files, region source, provider secrets or production database dump.
