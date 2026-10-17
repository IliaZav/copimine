# v5 verification

## Environment and evidence
Chromium via Playwright. Browser plugin not available. Loaded the actual complete standalone HTML with page.set_content; no generated design image. Direct file:// and localhost browser navigation are restricted by this environment and were not represented as passed. The optional Python HTTP adapter is separately covered by its unit tests.
Viewports: 1440, 768 and 390 pixels; desktop target 1440x1100 and mobile 390x844 in final captures. Offline page identity/nonblank content, JavaScript errors, image decoding and overflow were checked. Final native inventory, material picker, map and log captures are rendered HTML, not mock images.

## Results
- JavaScript unit tests: 30 passed.
- Python tests: 11 passed (5 local-preview adapter, 6 notification transport/preview validation).
- Browser flow/layout assertions: 61 passed, no relevant runtime errors.
- Function routes and shell/audit checks: 56 passed; 41 indexed destinations, 675 alias strings.
- Additional checks: mobile C 40x40 contain; imported log metadata cannot create injected HTML; logout confirmation/cancel and registration remember option.

## Interaction loops
Catalogue -> search/filter -> product/basket; stable header -> players -> head dropdown -> snapshot import -> skin PNG -> inventory detail; live function search (RU/EN/wrong keyboard) -> open result -> sidebar clears search; logs -> player/action filters -> readable/technical -> multiline import/redaction -> pause/resume; recipe -> native material picker -> quantity -> save/publish/history -> private plugin log; recovery -> verified contact -> local outbox -> one-use code -> reject replay; nomination/programme -> admin review; map four-region bounds.
Old lexical audit callers and new module callers now write to the same redacted event stream exactly once. Same-route actions preserve scroll/focus, list filters preserve search fields. No location.reload is used.

## Visual comparison to approved v3/v4 direction
1. Retained dark forest/graphite surfaces and green primary controls; no new light/SaaS theme.
2. Global wordmark/header persists in cabinet and admin, per user request; mobile C is not squashed.
3. Retained typography, subtle outlines, spacing and single-family SVG controls; no emoji.
4. Player detail replaces letter avatar/table-only inventory with real GUI positions, equipment and texture images. Inventory width reduced to 440px to fit a laptop viewport.
5. Search and log filters update content in place, not the whole shell; readable/raw tabs share one stream.
6. Gallery contains genuine supplied materials only; previous duplicated crops and incorrectly attributed reference map removed. Map is a real region-snapshot render.
Approved admin reference and final browser player/material captures were visually inspected. Changes above are explicit user-requested extensions, not unexplained design drift.

## Known scope limits
No real server inventory/event connection, VK flow, SMTP delivery, SMS delivery, payment, ban, PIN reset or item grant was exercised. SMTP/SMS tests use fakes and default local-preview output. Real personal skins require the server's authoritative assignment; default/demo skins are labelled. 608/1617 material labels are Russian; remaining EN/ID names and dictionary import are intentional, documented coverage gaps. Private recipe drafts are not a complete cauldron schema adapter.
file:// storage persistence and native showOpenFilePicker follow-mode are browser-dependent; import fallback is available. WebCrypto is unavailable in the test's about:blank context, so site-password crypto fails visibly there rather than being replaced with weak hashing; the local bank-PIN recovery state loop was tested. A future real-site security audit and live integration tests remain mandatory.
