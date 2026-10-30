# End Rift completion review — 2026-10-30

## Scope and source

This review continues `codex/copimine-integration-20261030` from `48c5eb91f8a149dd8d64ceeda6b4de88b994e9ae`. Existing staged work was preserved, reviewed and tested. The user authorized the same-branch fixes, changes to the Windows checkout, a refreshed isolated local test installation and publication to GitHub. The remote deployment is a separate follow-up.

The two full canonical specifications were read from the user's source-of-truth directory and re-read after compaction. Both are revision `2026-10-27.1`:

| Specification | SHA-256 |
|---|---|
| Waves 1–7 | `6e1245b44a449a0ef1bab9193606398660c68f6bb2de7d7bb6082cc8cde50caf` |
| Guardian/Kagune | `0c781e42e274a3f27762e0b568cc1d8ffd7dfd8623e5a0f1deb65ea73a94ae50` |

The earlier [integration audit](AUDIT_2026-10-30.md) remains historical evidence. Its old artifact hashes and test counts do not describe this new release.

## Confirmed defects and corrections

### Wave 6: converted allies

Turncoat's scripted fallback used the enemy acquisition radius as attack reach. It could damage an enemy through a wall or with a large vertical separation. Native AI now owns normal attacks; the fallback is restricted to disabled/unaware AI, real nearby overlapping collision volumes, line of sight and the victim's damage-immunity window. Hit particles follow an actual loss of health or absorption.

The generic navigation and skeleton handlers rejected hostile mobs as targets even after a valid conversion. They now dispatch converted actors to the conversion faction policy. The policy checks the current Wave 6 lifecycle, a live owned source and an unexpired conversion lease. Native AI and awareness are restored by the next existing five-tick AI reconciliation. Converted bow shots carry their owner and conversion identity and use the existing bounded projectile tracker. Damage and collision reject players, friendly actors, expired conversions, stale generations and wrong worlds. Old hostile arrows cannot regain admission after their source converts or disappears. An orphan converted actor restored from an unloaded chunk is removed through the owned-entity cleanup path.

### Wave 7: attacks, obstruction and audio

The Archmage could remain idle behind cover: its native AI is disabled, while target selection required line of sight before it could select a Step. An eligible same-room target can now trigger the existing bounded safe reposition when no visible target is available. Damaging spells retain their own visibility and room checks.

Scripted projectile identity and shooter ownership are checked before admission. Canceled spawns resolve as misses, with partial Fangs formations removed. Coincident target positions do not create a nonfinite direction. Named-trial warning sounds are emitted once for a committed attack instead of every warning tick.

### Inferno and current-version effects

During ERUPT, the production helper requests three GEYSER_PLUME and two GEYSER_BASE components with typed data, plus one minecraft:block.potent_sulfur.geyser_eruption cue for each nearby eligible recipient. The existing WARN → ERUPT → RECOVER → REMOVE lifecycle and gameplay timing remain unchanged. Legacy API capability fallback remains bounded. No sulfur block, terrain generation or new damage controller was added. This describes the API requests, not an observed native rendering.

The typed-particle adapter supplies the required Color for FLASH and uses a Float for DRAGON_BREATH only on APIs that require that type. This fixes compatibility failures at existing effect sites.

### Boss and defensive Kagune

Boss damage admission requires an active officially admitted player owner, including indirect projectile attribution. Prior cancellation, stale actors and unrelated players cannot alter HP, phase, hit feedback or victory credit.

The persisted guardian slot records now retain the committed five-second warning. Restarting at active second 89 preserves the actual remaining delay instead of restarting a full warning. Invalid and orphan warning receipts fail safely. Independent 90-active-second clocks and the minimum unshielded window are preserved.

### Victory and global End access

WorldCore persists the desired access state and operation receipt together, through a forced temporary file and atomic replacement. A successful return requires read-back; a failed or ambiguous write does not become in-memory success. Replaying an old open operation cannot override a newer administrative close. Same-state requests still record their operation identity.

A published configuration awaiting reconciliation blocks subsequent stale writes, including border updates and reloads. The EndEvent victory path requires both an accepted WorldCore result and authoritative open-state read-back. It does not infer a completed operation merely because the End was already open. Replaying a completed victory cannot reopen a later administrative lock.

### Actual server health prerequisite

The isolated runtime still had Spigot's 1024 HP maximum despite the repository template allowing 20000. After a consistent stopped-runtime backup, only that runtime value was changed to 20000; all other parsed settings were compared and preserved.

The supported startup validator now rejects a missing, duplicate, nonnumeric, nonfinite or insufficient maximum. Fresh runtime preparation seeds the supported value. Validation does not rewrite an existing operator configuration.

## Timing contracts inspected in the production paths

These values are the existing production contracts; this review does not rebalance them.

| Mechanic | Current timing | Clock and admission conditions |
|---|---|---|
| Prisoner Q / Heal | 20 s cooldown | Absolute wall-clock deadline in PrisonerAbilityController |
| Prisoner W / Battle Surge | 30 s cooldown; 7.5 s duration | Same owner/generation and valid accepted target |
| Prisoner E / Guardian Link | 30 s cooldown; 6 s duration | Same controller and server validation |
| Prisoner R / Turncoat | 55 s cooldown; 22.5 s duration | Conversion lease additionally fences target, damage and arrows |
| Major sphere spell | Normally 14 s between starts at 20 TPS | Both a 14000 ms gate and a 280-server-tick minimum start interval, plus the active-cast/IDLE ownership gate |
| Defensive guardian | 90 s ACTIVE combat time per dead slot | Individual persisted clock; 5 s warning before effective spawn; at least 15 s unshielded after the last living guardian dies |
| Wave 7 boss handoff | 800 server ticks | 40 s at 20 TPS; one gateway; offline time does not advance scheduler ticks |
| Inferno | Configured 400 server ticks | 20 s at 20 TPS; 16-tick vent warning precedes damaging eruption |
| Edge Volley | 75 s wall-clock cooldown after successful relocation | Five salvos at ticks 20/36/52/68/84, with phase, cluster dwell and cast-ownership checks |

Q/W/E/R are Java constants, not invented YAML settings. Invalid or stale requests do not consume the successful-use cooldown. Rejoining the same prisoner/generation retains its deadlines.

Sphere cancellation due to an owning mage's death clears that cast and can request selection after 250 ms; the regular 14 s cadence is not an unconditional post-cancellation delay. Tick-based windows lengthen at reduced TPS. The guardian clock uses bounded monotonic active-combat deltas and excludes paused/empty/offline time.

The Archmage production adapter converts its policy values to wall-clock deadlines: Fangs warning/cooldown 1000/2900 ms, Spear 1200/3100 ms, Step 900/2300 ms and Beam 1300/3900 ms. An unavailable/canceled safe Step resolves as a miss with the regular cooldown. Loss of the legal target uses the existing 1000 ms recovery.

Sources: PrisonerAbilityController, RitualSpellController, RitualSphereScalingPolicy, TentacleGuardianPolicy, PreBossTransitionController, ArenaInfernoPolicy, BossEdgeVolleyPolicy, Wave7NamedTrialActorPolicy and their actual CopiMineEndEvent adapters. Executed gate evidence follows below.

## Behavioral verification

New regression tests execute extracted production handlers or the actual helper class, with controlled actors, clocks and file failures. They include negative cases and were observed failing before the corresponding repair.

- Boss admission and guardian recovery: 164 passed, 2 existing explicit skips.
- The saved focused WorldCore durability/redirect run records 19 passed, including real file I/O and injected post-publication read failure; the complete final suite below covers the final expanded tests.
- Victory WorldCore read-back/replay: 15 passed.
- Inferno geyser and particle compatibility: six deterministic fixture tests passed. Separate probes compiled and executed the production helper against the pinned Paper 26.3 and legacy 1.21.1 APIs with proxy recipients, validating payloads and calls. They do not establish live packet delivery or native rendering.
- Runtime max-health preparation/validation selection: 56 passed.
- The connected Wave 6 combat chain and final integrated release gates are recorded in the final evidence section below.

These focused groups overlap other suites and must not be added together as an independent total.

## Client and resource artifacts

The active 26.3 client was rebuilt with the pinned tools; its Gradle test/jar invocation completed successfully. The compatibility client and resource pack were also rebuilt. Resource/profile/sound validators executed 57 passing checks and one Windows-specific POSIX-mode skip; both PowerShell manifest validators passed.

All 24 registered End Rift Ogg tracks were also decoded completely offline through the existing LWJGL STBVorbis 3.4.3+4 client dependency on pinned Java 25.0.4.1. Every file produced stereo 44100 Hz PCM, matched its declared frame count, returned decoder error 0 and contained nonzero samples, peak and RMS. Their combined duration is 824 seconds. All pack entries are byte-identical to their source files; the resource pack hash stayed unchanged. The decoder process exited 0. No Minecraft or audio device was opened: gameplay playback, volume, mixing and heard cue timing remain unverified. The private decoding report SHA-256 is `e7012f8993f1685bf143391f1ddd2c869108edd35da649cbb6f66b63e2a3b8b0`.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Active client 0.1.3+26.3 | 15025651 | `a83a9d54a0e2ee444ea5ab3b6d72c5459faa86ec174f8dc0ecfacce036d57ada` |
| Compatibility client 0.1.1 | 13035847 | `34ecc5a8bde30ce0cf531bedf983a8cacb605ba15df4d6d28add04ec0813713c` |
| 26.3 resource pack | 36252428 | `56771a2256367c22f73f3e1b0c96d4151d9562cc11730ff79bbb6cd5401d228c` |
| Existing modpack ZIP | 25216796 | `6e796c4a7fb31e69aabbb75f1e71699ca5c14456a4368e2189780cb44935492f` |

The compatibility client is byte-identical to both its tracked JAR and its member in the existing modpack. No unnecessary modpack change was made.

## Release and isolated runtime evidence

The pinned migration build completed successfully. The supported installer then installed and verified all 30 selected plugins, and the installed inventory was refreshed from the actual bytes.

| Plugin | Candidate filename | Runtime filename | Bytes | SHA-256 |
|---|---|---|---:|---|
| EndEvent | CopiMineEndEvent-26.3.jar | CopiMineEndEvent.jar | 1995773 | `c32924bcf3283606a15b233d91214edbafcc986bb593ad7b198224107686fe7d` |
| WorldCore | CopiMineWorldCore-26.3.jar | CopiMineWorldCore.jar | 39664 | `e6a2703e8091053f2d7e4fb53fb9b278a8d1ad62956250c64da334c780a742bc` |

Both installed JARs match their final built candidates. Their hashes are pinned in the server plugin lock. The inventory explicitly dates its older acceptance fields; its new runtime artifact snapshot is not a claim of native acceptance.

The updated isolated Paper 26.3-205 server started, saved all worlds, shut down normally and started again. Each startup enabled all 30 plugins with no startup ERROR/SEVERE entries or plugin disable. EndEvent restored the same COLLECTING event state; diagnostics reported zero invariant failures, three owned displays, zero combat tasks and zero boss proxies. The running-server read-only validator confirmed the owned listener, pinned Paper artifact and isolated PostgreSQL identity. AuthMe and AuthEffects stayed enabled. The actual runtime health cap is 20000.0.

The first complete Java release-gate attempt found a missing new guardian-warning field in a test adapter. The production plugins and the preceding 666-validator block had passed; the adapter was then repaired to include the actual saved warning receipt, and its focused four-file rerun passed all 19 checks. The first complete Python run then found an exact predecessor-allowlist fixture that had not yet been extended with the three measured old EndEvent builds. Both expected lists were updated without removing identity, hash, file-size or installed-byte assertions. The full installer/max-health selection subsequently passed 169 checks with two environment skips.

The final complete Python run, using pinned Python 3.13, Java 25, PowerShell 7 and the existing isolated PostgreSQL test database, completed with exit code 0:

- 2909 passed, 9 skipped, 124 warnings and 2 subtests passed.
- Elapsed test time: 589.66 seconds.
- All 140 cases in the four converted-mob contact/target/lifecycle/arrow suites passed.
- The final WorldCore durability suite passed all 14 cases; victory read-back/replay passed all 15.
- Both real PostgreSQL migration scenarios ran and passed; they were not skipped for a missing DSN.

The nine skips comprise two deliberately delegated physical-projectile cover variants, one optional installed-client-profile audit, two Grim fixture/toolchain-location checks and four Windows POSIX/symlink limitations. These skips are disclosed, not counted as passes. The dedicated Java release gate prepares its own supported build inputs. The 124 warnings are recorded separately from failures.

The repeated complete Java release gate also finished with exit code 0 in 1453.89 seconds. All 666 validators passed with zero failures and zero skips. The supported End Rift child process completed with exit code 0, including its actual PrisonerAbilityControllerTest boundary/reconnect checks and the wave, boss, Kagune, persistence and recovery selections. The enclosing gate reported success with pinned Temurin 21.0.12.1 and restored all pre-existing local build outputs and plugin data.

Local release checks are complete. Publication acceptance additionally requires static-and-contract, migration-postgres-contract and java-plugins to succeed for the commit containing this report; consult that exact commit's GitHub Actions run. The old 48c5eb91 workflow is not evidence for this change.

## Review and acceptance boundaries

Independent local reviewers traced boss recovery, Wave 6/7 combat and particle/audio compatibility. No new CodeRabbit result is claimed: automatic approval review rejected sending this new source/test diff to that third-party service without explicit authorization. The work continued using local independent review, executed regression tests and the required release gates.

`nativeVerified` remains `false`. No authenticated graphical Minecraft battle, heard-audio recording or player-driven event completion was observed in this pass. Compilation, API compatibility, asset checks and server startup are separate evidence levels. They cannot establish visual presentation, player balance or the absence of every possible bug.

No remote production deployment, world replacement or server authentication bypass is part of this release. The [remote deployment handoff](REMOTE_PLUGIN_DEPLOYMENT_HANDOFF.md) records exact artifacts, the local-only script boundary, preservation requirements and post-installation checks without publishing connection details.
