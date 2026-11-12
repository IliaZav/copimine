# Critical repair and live acceptance — 12 November 2026

Start: `1b36283eae152dcb4a060ed756d85498b83aa83a` on
`codex/rift-guardian-polish-20261110`; fetched GitHub matched. The separate
source-of-truth checkout's dirty JARs, archives and manifests are preserved.
Latest user instructions and `MASTER_REBUILD_CONTRACT.md` override the older
canonical mechanics. Both canonical files were reread and their SHA-256 values
remain `6e1245b4…de50caf` and `0c781e42…a94ae50` (full hashes in `AGENTS.md`).

## Review triage

The CLI cache contains the requested 17 major issues (`coderabbit review
findings --agent`, captured in `C:/Temp/cm-review-17-findings.txt`). The cached
plain-text header identifies this directory and `origin/main`; it does not
expose a base SHA. The user identifies the review baseline as `3f224940`.
Every finding is assessed against current production code, not its old line.

| # | Finding | Current evidence / next action |
|---|---|---|
| 1 | Ordinary-pillar test asks about the next pillar | Confirmed; correcting the test reproduced the same defect in actual `impact()`. Ordinary pillars now retire on their own first hit. |
| 2 | Inferno protected cells compared as full records | Pending coordinate-based reproduction and repair. |
| 3 | Guardian death generation ignored | Pending generation-fenced restore tests. |
| 4 | Fracture state discarded on restore failure | Pending journal failure/retry reproduction. |
| 5 | Fractures erupt at the shared arena anchor | Pending per-cell runtime geometry regression. |
| 6 | Cocoon stage/elapsed/checkpoint disagreement | Earlier adapter fixes are present; recheck actual boundary use and pure-policy API before closing. |
| 7 | Echo copy admission checks unmapped family | Pending mapped-family adapter test. |
| 8 | Full-tree compile gate uses Paper 1.21.1 | Pending pinned 26.3 dependency repair. |
| 9 | Existing fractures omitted from safe-floor budget | Pending occupied-coordinate budget regression. |
| 10 | Ring row zero never damages; rows do not sweep | Pending actual moving rig/contact implementation. |
| 11 | Fog footprints stop shrinking for 5–20 players | Pending capacity and path-preserving progression tests. |
| 12 | Final skeleton is spawned before saved adoption | Confirmed in `restore()`; next ownership/recovery slice. |
| 13 | Inferno heat overwrites safe cells | Pending initialization/tick coordinate tests. |
| 14 | Core cells admitted as safe ground | Pending standable/protected-coordinate tests. |
| 15 | javac argument file does not quote paths | Pending space-containing path regression. |
| 16 | Final pillar has four displays but tick requires three | Reproduced: valid final stages rejected and a final pillar missing one display admitted. Fixed by current pillar type. |
| 17 | Inferno starts fully heated | Pending staged outward-front regression. |

## P0.1a — Ram presentation admission and seven impacts

Additional confirmed defect: `createArmor()` built three pieces while the real
tick required seven. The old runtime probe substituted a seven-piece builder,
hiding the startup failure.

New probe executes production `createArmor`, `addArmor`, `buildPillar`, and the
whole visual-admission branch of `advance`; only Paper display creation is
detached. Before fixes: 15 failures / 1 pass. After correcting armour: six
failures isolated the final-pillar mismatch, including acceptance of incomplete
geometry. After correcting pillar type: all geometry cases pass.

The corrected ordinary-collapse test exposed a production error as well:
`impact()` queried remaining collisions after the ordinal had advanced. It now
uses the struck pillar's ordinal. Four one-hit pillars retire independently;
the final pillar remains for its first two collisions and retires on the third.
Seven physical armour pieces have stable section IDs and shed once per receipt.
The existing six client texture stages are a separate presentation surface;
their final mapping/native appearance still requires acceptance.

Files: `runtime/RiftRamEncounter.java`, `test_rift_ram_runtime.py`, new
`test_rift_ram_visual_admission_runtime.py`.

- Focused builders, seven-impact, roster, permit, stomp and client-stage suites:
  **27 passed**, exit 0. Log: `C:/Temp/cm-ram-p0a-regression.log`.
- `endEventJar`: **BUILD SUCCESSFUL**, exit 0. Log:
  `C:/Temp/cm-ram-p0a-build.log`. Stage-only candidate verification follows the
  actual rebuilt JAR; this does not install it in a running server.
- Paper: **NOT VERIFIED**.
- Minecraft visuals and performance: **NOT VERIFIED**.
- Skeleton restore, final-skeleton death/completion ownership and restart matrix:
  still open; this slice does not claim them fixed.

No production or installed-client replacement is authorized. The live acceptance
run must use a separate correctly initialized Paper/PostgreSQL/client runtime.
