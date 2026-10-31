# Unified Specification Register — 2026-10-31

Companion to `APPROVED_WAVE_CONTRACT.md`. This file records the **newest**
authority document supplied by the user and what changed relative to the earlier
three-prompt contract, so later work cannot silently follow a stale reading.

## 1. New source of authority

| Field | Value |
|---|---|
| Document | `CopiMine_End_Rift_Unified_Specification_2026-10-31.md` |
| Specification ID | `CM-END-RIFT-UNIFIED-WAVES-20261031` |
| Revision | `1.0` |
| Size | 202850 bytes |
| SHA-256 | `c8025ed40f47fb10dab3caad92e198e73f55267d74108645fed7367ae7acdfec` |
| Status | consolidated target specification; **not** a claim that anything is implemented |

Authority order is unchanged in shape, with this document now sitting at the top
alongside the three implementation prompts:

1. This unified specification (revision 1.0) and the three 30–31 October prompts.
2. `docs/end-rift/2026-10-31/APPROVED_WAVE_CONTRACT.md` supersession list.
3. `D:\Downloads\COPIMINE_END_RIFT_WAVES_CANONICAL.md` (2026-10-27.1).
4. `D:\Downloads\COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md` (2026-10-27.1).
5. Historical `docs/end-rift/**`. Old v5 ritual/End proposals are never imported.

## 2. Requirement tags used by the specification

Every requirement carries one tag. The register below is binding for how a
result may be reported.

| Tag | Meaning | Reporting rule |
|---|---|---|
| `[U]` | User requirement | Must be implemented; cannot be traded away for convenience |
| `[A]` | Architecture / engineering decision | A documented, configurable default — never quoted as a user choice |
| `[H]` | Preserve compatible existing accepted behavior | Keep what the code actually does after inspection; do not invent a replacement |
| `[O]` | Deferred / own-authority topic | Must **not** be silently fabricated or counted as a completed redesign |

The audit's earlier failure mode was reporting `[A]`/`[O]` work as if the user
had approved it. The Russian report must keep user requirements separate from
engineering defaults.

## 3. What this document changes relative to the earlier contract

These are additive or sharpening. Nothing in this section relaxes an earlier
`[U]` requirement.

### 3.1 Wave 6

- Five **distinct mage identities** with a persisted, randomized order
  (`W6-002`). At most one mage stage active (`W6-002`, `W6-004`).
- Signature ownership is **by mage identity**, not death ordinal (`W6-016`).
  The prisoner Q/W/E/R unlock stays **ordinal** (`PRISON-007`). The two systems
  are independent and must not be conflated.
- Explicit `mageId -> combatProfileId -> signatureId` table (`W6-015`), named
  scaled miniboss profile, **not** the generic elite formula and **not** the
  Wave 7 1000 HP floor.
- Five signature identities: Barrage, Anchor, accepted Soul Brand, Heavenly
  Judgment, and the existing Barrage-charges-Anchor combination used as the
  fifth, independently usable (`W6-017`).
- Sphere must **actually cast** real released hazards; a packet/beam without a
  released projectile fails acceptance (`W6-020`).
- Guard table frozen on total admitted roster including the prisoner, max five,
  applied once (`W6-008`, `W6-009`).
- Stage lifecycle is explicit: PREPARE, GUARDED, EXPOSED, TRANSFER, DRAIN,
  NEXT_STAGE, FINAL_RELEASE, FINAL_CLEAR, COMPLETE (`14.2`).
- One major-action arbiter; eight seconds **after** the previous major cast is
  actually cleaned up (`SPHERE-001`, `SPHERE-002`).
- Anvil roster modes restated exactly (`SPELL-010`): 2–3 one per free player;
  4–10 one 3×3 per free player, up to 81 visuals at ten total; 11–20 exactly two
  sequential shared 5×5 volleys, 25 concurrent maximum, never 25 per player.
- Never fix the endless wave with an unconditional `wave6Complete = true`
  (`W6-024`).

### 3.2 Wave 7

- Sequential `WARDEN -> MARKSMAN -> ARCHMAGE -> PERSONAL_ECHO` on the open arena,
  no nested room barriers or extra rune waits (`W7-001`, `W7-002`).
- Miniboss HP ≥ 1000 real, curve `baseHP * (1 + 0.25 * (admittedPlayers - 2))`
  (`W7-006`, `W7-007`). Echoes are the explicit exception.
- Marksman: three fans of eight, five-second invisibility with reveal tell and
  finite cooldown, exactly one Giant defender near 50 % HP (`MARK-001`..`MARK-009`).
- Archmage: Fangs/Spears/Step/Beam chosen by context, bounded Vex caps
  2/4/6 for 2–4 / 5–9 / 10–20 participants (`MAGE7-003`).
- No endless ordinary pack spam between minibosses (`W7-005`).

### 3.3 Personal Echo

- Every admitted participant gets exactly one copy, all simultaneous, no queue
  (`ECHO-001`).
- Balanced split `2-9=all`, `10=5/5`, `11=6/6+5/5`, `15=8/7`, `20=10/10`, frozen
  at admission, difference at most one (`ECHO-002`).
- Copy HP 2–3× original, default 2.5×; damage bonus 1.25× applied once
  (`ECHO-011`, `ECHO-012`).
- Reciprocal personal-duel default: only the original damages its own copy
  (`ECHO-007`); collision admission is part of isolation (`ECHO-008`).
- Behaviour is a **conditional profile**, not five global weights
  (`BRAIN-001`); three recorded styles must produce measurably different legal
  decisions under identical observation (`BRAIN-013`).
- Recording separates journal, aggregates and compiled profile; tick-thread I/O
  forbidden (`REC-003`, `REC-010`).

### 3.4 Shared

- Elite HP exactly ×3 ordinary under the same frozen scaling, no hidden cap;
  allies pass arrows, terrain still blocks (`ESK-002`, `AUD-14`).
- Wave 5: exactly five cycles, opaque safe-volume mask, vanilla emerald floors,
  one 10–15 pack after **every** cycle including the fifth (`W5-003`..`W5-012`).
- TAB: clip the ACTUAL styled string at a top-centred neutral dark panel
  (`TAB-001`, `TAB-002`).
- CleanLag/EntityClearer silence at the configuration source in **both**
  repository template and installed runtime config (`OPS-002`).
- The user's Iris shaderpack and resource stack are never switched or disabled
  (`VIS-007`).

## 4. Measured baseline vs audit claims

The audit quoted 752 selected tests. The actual collection on this tree measured
**2922 collected** tests. The number is recorded here so the final report cannot
quietly reuse the audit figure.

Four of the five known release-provenance failures are genuine drift between
built, locked and installed artifacts; the fifth is a client artifact size
documented in `README.md`. They are repaired by reconciling provenance, never by
relaxing an assertion.

## 5. Implementation order adopted for this pass

The user directed the order explicitly: **Waves 6–7 first (including the Wave 7
elite strengthening), then Wave 5, then Waves 1–4**, each followed by the
required gates, then publication.

That order is preserved even though the specification's §31 work-package list
starts from shared foundations, because the user owns the priority.

Work that the specification marks `[O]` stays untouched and is reported as such:

- Warden move redesign (`WARD-001`) — existing three attack identities preserved.
- Soul Brand semantics (`SPELL-008`) — preserved as currently accepted.
- Final Rift Guardian / Kagune disputed mechanics (`BOSS-004`).

## 6. Verified state and open gaps at handoff

This section records what the code on disk actually does, so no later report can
claim a requirement is done because a policy class exists. Policies are pure
domain contracts; "implemented" additionally requires the runtime to call them on
the real production path (spec §33.1).

### 6.1 Release provenance — repaired and verified

The three sources of truth disagreed: the lock named `4730adaa…` as the Wave 6
candidate, the installed runtime jar was the older `440e081e…`, and the receipt
recorded `db2a4e73…`. The split was repaired by rebuilding, re-locking and
installing, never by relaxing an assertion:

- `build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar` — 2 003 199 B,
  SHA-256 `4730adaa…6bd5c3`, rebuilt from the current sources (0 javac errors).
- `local-runtime/end-rift-server-26.3/plugins/CopiMineEndEvent.jar` — installed
  through `install_migration_server_plugins.py` (30 plugins installed and
  verified) and now byte-identical to the candidate.
- `migration-plugin-receipt.json`, `server-plugins.lock.json` and
  `OWN_PLUGIN_INVENTORY.json` now agree with the built and installed bytes.
- The previously installed `440e081e…` was added to the candidate's
  `replaceSha256` allowlist as a legitimate predecessor, which is how the
  installer is designed to authorize an upgrade. The old
  `migration-local-end-event-build.json` override record was removed because the
  runtime jar it described has been superseded; the replaced jar is preserved
  under `migration-backups/plugins/20261030T195725Z/`.
- The legacy 1.21.1 client was rebuilt with its own pinned Gradle 8.10.2 (Loom
  1.8.13 produces an empty jar under Gradle 9.8.1), and the staged copy plus
  `CopiMineMods.zip` were re-synchronized from that build.

### 6.2 Mandatory regressions — written and executed

`tests/test_end_rift_arena_relocation.py`, `test_end_rift_participant_lifecycle.py`,
`test_end_rift_scoped_cleanup.py`, `test_end_rift_release_provenance.py` and
`test_end_rift_final_handoff_once.py` all execute real compiled Java probes.

### 6.3 Wave 6 — what is real and what is still missing

Implemented and wired into the runtime:

- Five mage identities with a frozen Fisher-Yates order per attempt, persisted in
  `ritual-sphere.mage-order` / `ritual-sphere.mage-defeated`
  (`W6-002`, `W6-016` binding half). An unbound or duplicate death is refused
  with `WAVE6_CASTER_DEATH_WITHOUT_IDENTITY` and grants no progression.
- Guard health de-double-scaled: a guard keeps the single shared elite `×3`
  value instead of being multiplied a second time (`W6-008`).
- Scope fencing, recovery checkpoints and receipt-idempotent restart.

Still missing (must not be reported as done):

- `WaveSixStageState` is compiled but the runtime never advances it: all five
  squads still spawn together at `startRitualSphereObjective`, so there is no
  `GUARDED → EXPOSED` transition on one visible mage (`W6-001`..`W6-006`).
- Mage bodies are still plain endermen with the ordinary role's health;
  `WaveSixRosterPolicy.mageHealth(...)` is never called, so the mage is not the
  miniboss the roster policy describes.
- Two of the five signatures have no spell implementation:
  `HEAVENLY_JUDGMENT` and `CONVERGENCE` map to nothing, while
  `RIFT_BARRAGE`/`GRAVITY_WELL`/`SOUL_BRAND`/`RIFT_CHAINS` already exist. Sphere
  casting of acquired signatures (`W6-018`) is therefore partial.
- There is no per-stage ordinary pack after each mage victory (`W6-004`,
  `W6-005`) and no 10-second rune gate between stages.