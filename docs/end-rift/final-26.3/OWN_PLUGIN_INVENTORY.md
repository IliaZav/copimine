# First-party plugin inventory and coverage

Source commit: `d44c8f51be00f3b6d911135f7722a155cbf20c13`. Machine-readable source: `OWN_PLUGIN_INVENTORY.json`. Automated check: `tests/test_own_plugin_inventory_contract.py`.

This table covers only first-party plugins. Third-party jars remain pinned in `tools/minecraft-26.3/server-plugins.lock.json`; no third-party fix is claimed.

| Plugin | Version | Dependencies | Commands / permissions | Installed SHA-256 | Automated | Live enable |
|---|---|---|---|---|---|---|
| CopiMineUltimateAdminPlus | 9.2.0-postgres-v4-balance-credit | EconomyCore, ElectionCore | 11 / 11 | `70eb0caa...a9f0` | PASS (contracts) | BLOCKED |
| CopiMineArtifacts | 1.1.0-shops-blocks-prices | EconomyCore | 2 / 8 | `75547398...f109` | PASS (contracts + Java) | BLOCKED |
| CopiMineEconomyCore | 1.0.0 | none | 0 / 0 | `7fd9c779...bd32` | PASS (contracts) | BLOCKED |
| CopiMineElectionCore | 9.2.0-postgres-v4-balance-credit | EconomyCore | 2 / 5 | `d2256c69...5b65` | PASS (contracts) | BLOCKED |
| CopiMineEndEvent | 0.1.0 | WorldCore, Artifacts | 1 / 2 | `e7ef03e6...1742` | PASS (wide suite) | BLOCKED |
| CopiMineNarcotics | 2.1.0-client-bridge | none (ProtocolLib soft) | 2 / 8 | `d7e46fcc...d7e2` | PASS (contracts) | BLOCKED |
| CopiMineWorldCore | 1.0.0 | none | 1 / 1 | `1b6822c1...3f14` | PASS (contracts) | BLOCKED |
| AuthEffects | 1.0.0 | AuthMe soft | 0 / 0 | not installed (candidate `09aa0017...e11c`) | PASS (contracts) | BLOCKED |

## What was actually checked

- Every manifest name, version, main class, dependency/softdependency set, command set and permission count matches the file on disk.
- Every listed test file exists. The plugin builds and the End Rift gate compile/load path are exercised by `RunJavaPluginCI.ps1` and `RunEndRiftEventChecks.ps1`.
- Seven first-party jars in `local-runtime/end-rift-server-26.3/plugins/` match their SHA-256 in the official migration receipt and the pinned plugin lock.
- `AuthEffects` is not part of the installed 27-plugin receipt; only its pinned candidate hash exists. It is not counted as installed.

## Known gaps (not PASS)

- **Economy:** atomic transfer races, insufficient/negative/huge amounts, precision, mismatched idempotency keys, disconnect retry and DB failure-to-zero were not run against live PostgreSQL.
- **Admin:** destructive command/audit behaviour, revoked permission mid-action, foreign-world scope and dry-run confirmation were not executed with an authenticated operator.
- **Artifacts:** live purchase/refund, full inventory, quit/restart compensation and client visual states remain unverified.
- **Narcotics/AuthEffects:** no authenticated V4/AuthMe login/logout or hidden packet exchange. The pinned signer/trust anchor is unavailable.
- **World/Election:** no live foreign-world denial, cabinet rehydration or mandate inventory transfer.
- **EndEvent:** no real 2/5/10/11/20-player wave run, no real restart/death/quit recovery, no native F2/audio.

## Safe next action

Start the disposable Paper runtime with the official initializer/installer, provide the approved V4 signer through its normal protected configuration, then run the existing live runners. Do not disable AuthMe, V4 or ownership checks and do not generate a replacement keypair.
