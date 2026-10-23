# Current handoff — End Rift Waves 1–7

Updated: 2026-10-23. Baseline source commit: `d44c8f51be00f3b6d911135f7722a155cbf20c13` (branch `codex/copimine-source-of-truth-20261020`).

## Source and CI

- `2d76ea3f` fixed the Wave 6 guard-kill diagnostic command and full-coordinate anvil landing selection.
- `d44c8f51` rebuilt the EndEvent candidate after those fixes, updated the pinned lock/receipt and installed it through the official migration installer.
- Hosted CI run `39865534641` on `d44c8f51` completed all three mandatory jobs successfully: `migration-postgres-contract`, `static-and-contract`, `java-plugins`.
- The new evidence/docs/test commit in this pass has its own CI status; do not use the previous run as evidence for it.

## Installed first-party artifacts (disposable runtime)

| Plugin | SHA-256 | Size |
|---|---|---|
| CopiMineEndEvent | `d0b61f8c8d9e073729fdb67c946314625c13d018ecec78d8a16659f80a578209` | 1,899,571 |
| CopiMineArtifacts | `75547398ec77fdc083c37de5a5d6449b166efb92c8af942ab0bddc5d675cf109` | 363,393 |
| CopiMineNarcotics | `d7e46fcc2bcfb8f31216cb1ea51c53bf3f492d29bc3b8c227540cb38e8ced7e2` | 372,524 |
| CopiMineUltimateAdminPlus | `70eb0caa57ae48d918170fd9fd4f9ce853bfd8c967315f774cccc606b4abe9f0` | 272,236 |
| CopiMineEconomyCore | `7fd9c779e0a5ca80b5dad4effcc0bf59d5cbcd181be7400e10432b6e69cabd32` | 170,216 |
| CopiMineElectionCore | `d2256c6976cb88a2a98b372d5f7e1b1db63013a52fdcfe519a065e3896395b65` | 234,331 |
| CopiMineWorldCore | `1b6822c1733b8ddc3dc12760e8319caed014f3148493bf1c826ca6f5153e3f14` | 36,778 |

`AuthEffects` is **not installed** in the 27-plugin receipt; only its pinned candidate hash exists.

## Evidence added this pass

- `REQUIREMENT_TEST_MATRIX.json/.md`: exactly 168 registry IDs, tests and honest statuses.
- `SOUND_CUE_MATRIX.json/.md`: 28 server cues, all Waves 1–7, both anvil volleys and four Wave 7 trials; `nativeAudioVerified=false`.
- `OWN_PLUGIN_INVENTORY.json/.md`: eight first-party manifests/builds/installed hashes and known gaps.
- `BOT_ACCEPTANCE.json/.md` and `PERFORMANCE_REPORT.md`: blocked, with no fake PASS.
- New tests: sound cue matrix, requirement matrix, own-plugin inventory contract.

## Hard blockers

1. `COPIMINE_CLIENT_BRIDGE_PRIVATE_KEY_FILE` is absent; the existing signing secret does not match the pinned V4 trust anchor. No approved test-anchor override exists.
2. Paper and PostgreSQL are stopped; canonical listeners `25567`/`55434` are absent.
3. No real 2/5/10/11/20-player runs, restart/death/quit recovery, combat performance or native F2/video/audio evidence.
4. GitLab push/CI remains unavailable (previous HTTP 401); GitHub is the active remote.

Do not mark Waves 1–7 fully accepted until those blockers are cleared. Automated source/contract coverage is broad but is not gameplay acceptance.

## Next safe actions

1. Provide the approved signer through normal protected configuration; do not disable V4/AuthMe and do not generate a replacement keypair.
2. Start the disposable runtime with the official initializer/installer and loopback-only ports.
3. Run the existing live runners: 2, 5, 10, 11, 20 players; all waves; death/quit/reconnect; restart; targeted boundaries; second seed.
4. Record accepted hit/death/objective receipts, timings, TPS/MSPT/memory and exact hashes.
5. Capture native F2/video+audio and update `nativeVisualVerified`/`nativeAudioVerified` only after review.
