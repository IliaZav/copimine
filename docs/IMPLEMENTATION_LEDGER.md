# Implementation ledger: 2026-10-23-site-production

Source template: CopiMine_Site_v7_3_2_Visual (9).zip.
Read-only game source baseline: bf04d44faa58a2d281c4da6e810e64a2da784a38.
Workspace: D:/Desktop/Copimine/copimine-site.
Branch: codex/site-v7-production-20261023; new root tree, no game source history.

Ruling: Continue execution without another design approval round. The user chose
the supplied template and explicitly asked to proceed with integration foundations
despite absent mail/VK accounts. Routine architecture decisions are delegated.

Ruling: Reuse existing website backend game contracts as website code under
backend/legacy; configure its game root externally. Do not modify the original
backend or the End Rift checkout.

Task 1: auth/admin/game-boundary tests observed RED then GREEN; persistent
sessions, CSRF, hierarchy, scoped CMS and unavailable-game errors implemented.
Task 2: adapter and non-event runtime prepared. Remote Java 25.0.4.1,
Paper 26.3-205 and six CopiMine plugins started against fresh PostgreSQL.
Dedicated authenticated game bridge remains to implement and verify.
Task 3: pending.
Task 4: pending.
Task 5: pending.

Scope addition (user pasted BlueMap specification, read fully): implement official
BlueMap 5.29 integration, six scoped maps, shared exploration filtering, durable
12-hour/manual jobs, immutable atomic publication and protected private assets.
Preserve existing template. Never install Chunky, pre-generate terrain, alter
world borders or touch event work. BlueMap itself must not generate chunks.

Map tests: eight RED failures before implementation. Initial implementation
exposed rate-limit argument and Windows readonly-cleanup errors; repaired without
relaxing privacy/publication assertions. Worker and rendered browser tests pending.

Ruling: Fresh remote Paper/Java runtime follows the earlier website integration
plan; the BlueMap addition does not authorize upgrading any existing runtime or
resetting worlds. Cost if wrong: incompatible plugin/game behavior; verify exact
runtime versions and preserve newly created worlds on every subsequent deploy.

Read-only canonical checkout rechecked after preparation: other agent now has
tracked event/client/artifact changes. None are included or modified here.

Milestone 2026-10-23 10:40 MSK: 37 tests passing, all frontend JavaScript files
parse successfully, staged diff whitespace check clean. Source fixes have
RED/GREEN evidence for authentication, roles, inventory guards, map isolation,
publication failures, provider challenges, current catalog format, account
disable, concurrent idempotency reservation and unchanged-publication I/O.

Remote milestone: API/Nginx/worker/Paper active; PostgreSQL-backed owner login
verified in browser on https://copimine.ru. Manual map job
4159422d877142f7af773bff35cc3fcf completed: 312 files, 7,110,054 new bytes,
213 reused immutable files, six maps. Native Minecraft/terrain verification
remains false: all three new worlds currently have zero saved region chunks.
No Chunky or pre-generation was performed. UI has an honest empty-world message.

Bridge installed outside the website repository:
CopiMineWebsiteBridge 1.0.0, SHA256
f1b2f048cbb850af88531acf7fd67efa5934d98feda92e896cf9f204aa6b4f09.
Source: ../copimine-site-game-integration-20261023. Real health confirms the six
required CopiMine plugins, AuthMe and BlueMap. No event plugin installed.

Provider milestone: local Postfix/OpenDKIM active, public DKIM record exported;
mail domain flag remains disabled. VK PKCE/state implemented and mocked callback
tested; real account/application is absent. HTTPS certificate renewed successfully
using webroot/http challenge; expiry 2027-01-21, certbot timer enabled.

Ruling: BlueMap 5.29 normalizes map IDs to underscores. Keep logical dimensions
stable and explicitly scope real IDs public_overworld/public_nether/public_end
and admin_overworld/admin_nether/admin_end. Cost if wrong: missing or mis-scoped
assets; test direct paths and public/private settings against actual output.

Ruling: Official BlueMap viewer's translation compiler requires unsafe-eval.
Permit it only on the pinned BlueMap viewer asset responses; retain self-only
scripts, same-origin framing and server-side RBAC/no-store for private assets.
Cost if wrong: a vulnerability in the trusted viewer has a larger script surface;
no player marker HTML or third-party custom scripts are published.

Outstanding: CMS/catalog UI completion, deployed final patches, four-width visual
checks, privacy/runtime failure/restart checks, final independent review and
website-only GitHub push. Do not report those as already complete.
