# Server specification and recovery audit

> Execute inline with systematic debugging and test-driven development. All changes belong to this isolated branch.

**Goal:** Independently recheck the current End Rift/plugin implementation, repair reproducible defects, publish reviewed source and truthful acceptance evidence.
**Baseline:** ebb27fe0f9ea8890cff2a0be8312962872a07c2f; branch audit/chatgpt-server-20261030.
**Architecture:** Preserve Paper authority, signed Fabric presentation, event/generation ownership and the existing controllers. Avoid rewrites and fabricated native acceptance.
**Specs:** D:/Downloads/COPIMINE_END_RIFT_WAVES_CANONICAL.md and D:/Downloads/COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md, revision 2026-10-27.1. Both read in full. SHA-256: 6e1245b44a449a0ef1bab9193606398660c68f6bb2de7d7bb6082cc8cde50caf and 0c781e42e274a3f27762e0b568cc1d8ffd7dfd8623e5a0f1deb65ea73a94ae50.

## Constraints
- Protect dirty canonical checkout, other audit branches, player worlds and databases.
- Ordinary phase damage is not silently clipped; Last Seal shield is explicit.
- Seven waves, three portal captures, 45s gaze, Q/W/E/R, cumulative sphere spells, exact anvil scaling, one 40s handoff.
- Defensive/ambush/small Kagune have distinct ownership; defenders use 90 active seconds and at least 15s unshielded grace.
- No manual-player acceptance required by current code-completion scope; no production deployment.

## Review focus
Old WorldCore redirects after unlock; recovery identities; accepted versus cancelled damage; compatible source/build/runtime identity; old APIs leaking into site integration.

## Tasks
1. Recheck old findings against current production paths and tests. Do not repeat resolved bugs as current findings.
2. Add executable regression for stale asynchronous WorldCore redirect; repair revalidation of source/destination policy and safe cleanup.
3. Inspect and correct further demonstrated integration/recovery defects with tests, prioritizing exact approved contracts over cosmetic changes.
4. Run full supported Python suite, relevant Java/EndRift gates and builds with pinned toolchains; record existing failures separately.
5. Review final diff through CodeRabbit, commit own files only, publish audit branch, and produce exact integration/deployment handoff.

## Evidence
Logs: C:/Temp/cm-audit-evidence-20261030, outside tracked source.
Other agents have existing uncommitted WorldCore/site fixes in different worktrees. Do not overwrite or claim their changes.

## Progress
- Current local/GitHub baselines verified; both complete canonical specifications read and hashes matched.
- Reproduced eight failing WorldCore redirect scenarios; all ten scenario tests plus two existing classpath tests pass after the fix.
- Rebuilt pinned 26.3 plugin/client/resource candidates; stage-only installer verified 30 candidates. Updated measured WorldCore candidate hashes and safe replacement allowlist without touching installed runtime data.
- Fresh full Python suite after stable builds: 2598 passed, 19 explicit skips, 2 subtests passed, exit 0.
- Canonical End Rift grouped/pure-Java/persistence/recovery gate: exit 0. Active Gradle classes were verified and staged at the ignored compatibility-gate path; no old installed runtime was used as evidence.
- Independent CodeRabbit review completed with zero findings in the implementation diff. Diff whitespace check passes.
- No human-player, graphical, production PostgreSQL or remote deployment acceptance claimed. Known remaining boundaries are documented in AUDIT_2026-10-30.md.
- Isolated branch is ready for commit/push; integration into other active branches remains a deliberate next step.
