# First-party plugin inventory and coverage

Current artifact snapshot: **2026-10-30**, from [OWN_PLUGIN_INVENTORY.json](OWN_PLUGIN_INVENTORY.json). The [completion audit](AUDIT_2026-10-30_EVENT_COMPLETION.md) records the final build, installation, startup/restart and verification evidence. Automated identity check: tests/test_own_plugin_inventory_contract.py.

This table covers all **eight installed first-party plugins** in the isolated local **30-plugin** runtime. Third-party artifacts are pinned in tools/minecraft-26.3/server-plugins.lock.json; no ownership of third-party code is claimed.

| Plugin | Version | Required dependencies | Commands / permissions | Installed SHA-256 | Local startup and restart |
|---|---|---|---|---|---|
| CopiMineUltimateAdminPlus | 9.2.0-postgres-v4-balance-credit | CopiMineEconomyCore, CopiMineElectionCore | 11 / 11 | `8b4b7922be1aa0ce8ba1c6a856139bf8405a03239d6ea53a810386b44efbf3a2` | observed enabled |
| CopiMineArtifacts | 1.1.0-shops-blocks-prices | CopiMineEconomyCore | 2 / 8 | `a5e0420d503850cb604445c52705f5af71fdc65770a78b943c5d2bc252fabcc5` | observed enabled |
| CopiMineEconomyCore | 1.0.0 | none | 0 / 0 | `d4051688d7a56e1c59b8b31023783d054724be6fac3554611ed6bf65d8568e89` | observed enabled |
| CopiMineElectionCore | 9.2.0-postgres-v4-balance-credit | CopiMineEconomyCore | 2 / 5 | `ce4749dc346134680366fdccd044cfb0d61cb8fcbf7c8ee1a90e69917a4a4182` | observed enabled |
| CopiMineEndEvent | 0.1.0 | CopiMineWorldCore, CopiMineArtifacts | 1 / 2 | `c32924bcf3283606a15b233d91214edbafcc986bb593ad7b198224107686fe7d` | observed enabled |
| CopiMineNarcotics | 2.1.0-client-bridge | none | 2 / 8 | `bea72432491f7afa0a0cbd23b608654b56899b8e463720f98dbfef0f39f2ef0d` | observed enabled |
| CopiMineWorldCore | 1.0.0 | none | 1 / 1 | `e6a2703e8091053f2d7e4fb53fb9b278a8d1ad62956250c64da334c780a742bc` | observed enabled |
| AuthEffects | 1.0.0 | none | 0 / 0 | `3ae5874bad098f6ac97c983806eb6e7143acc6fde5e57ea4ff9508fe6fef4e8f` | observed enabled |

AuthEffects is installed and enabled with AuthMe present. AuthMe is a soft dependency of AuthEffects. Additional soft dependencies are retained in the machine-readable inventory and actual plugin manifests.

## What was actually checked

- The inventory's manifest identity, version, entry point, dependency sets, command sets and permission counts were checked against source manifests.
- All eight installed first-party JARs match their candidate hashes, the official migration receipt and the pinned plugin lock.
- The supported installer installed and verified 30 selected plugins. All 30 enabled on both the first updated startup and a subsequent cold startup, with zero startup ERROR/SEVERE entries.
- EndEvent restored the same COLLECTING event identity and reported zero invariant failures. The actual runtime max-health cap is 20000.0.
- The complete Python run passed 2909 tests with 9 disclosed skips; the complete Java release gate and its 666-validator block passed. Detailed scope and warnings are in the completion audit.

## Historical coverage fields

The JSON's original sourceCommit d44c8f51be00f3b6d911135f7722a155cbf20c13 and generatedAt identify the **2026-10-29 acceptance snapshot**, not the source or runtime state of these newly measured binaries. Its sourceBuild, unitContract, realServerEnable, negativeCoverage, knownGaps and blockedReason fields remain explicitly dated by acceptanceStatusAsOf. Earlier statements that the server was stopped or AuthEffects was absent must not be read as current installation facts. runtimeArtifactSnapshot identifies the new measured inventory.

Startup is confirmed separately from authenticated gameplay. Retaining historical acceptance fields does not turn pending gameplay scenarios into successful tests.

## Remaining gameplay coverage

- Economy: real concurrent transfers, extreme balances, idempotency conflicts and player disconnect/retry scenarios were not accepted through live player operations.
- Admin: destructive commands, revoked permissions during an action and foreign-world scope were not exercised by an authenticated operator.
- Artifacts: live purchases/refunds, full-inventory compensation and every native item rendering state remain unverified.
- Narcotics/AuthEffects: authenticated signed V4 client exchange and real AuthMe login/logout effect cleanup remain unverified.
- World/Election: player-driven world-access checks, cabinet rehydration and mandate inventory transfer remain unverified. Automated WorldCore durable-state and victory read-back tests passed; this is distinct from those player operations.
- EndEvent: no complete 2/5/10/11/20-player event, native F2/video, heard-audio timing or combat-stage restart acceptance was observed. The two observed server startups restored the collecting stage.

## Next acceptance step

Use an authorized native client and the normal protected signing/authentication configuration for the remaining gameplay checks. Preserve AuthMe, signed V4 and ownership checks; do not fabricate client acknowledgements. nativeVerified remains false until actual visual, audio and player-combat evidence exists.
