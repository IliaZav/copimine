# Echo source admission checkpoint

> Execute inline under the supplied V3 Wave 7 Task C/E plan. This checkpoint
> does not activate an incomplete official trial or prove native client parity.

**Goal:** Protect the existing local Echo slice with the same exact pair rule
that the personal duel needs, including indirect effects and stale launches.

**Architecture:** A small immutable pair/scope policy and a Paper listener use
the existing five-tick loop. The scene supplies its actual identities and live
owner/capability state. No second encounter engine, global world scan, team or
PvP setting is introduced.

**Baseline:** `a44d66715742cd7eeffe5ec1f86d509dc8a0f796`, branch
`codex/end-rift-event`, Paper/Minecraft 1.21.1. Preserve unrelated dirty files.

**Root cause:** The shield/armor observers validate the current owner but do not
authorize the attacker. The local carrier is deliberately outside the generic
wave direct-health handler. Room permissions alone do not establish an exact
owner/replica source pair.

**Files and interfaces:**

- `domain/wave7/EchoCombatAdmission.java`: immutable Pair, Context and launch
  Scope; exact UUID membership, hostile counterpart and self-effect checks.
- `runtime/wave7/EchoCombatAdmissionListener.java`: accepted launch receipts,
  damage/fire/target/hit/splash/cloud filtering; `tick()` and retryable `clear()`.
  Receives `Supplier<Context>`, never invents roster or capability acknowledgments.
- Existing probe/state: expose actual duel identity and non-mutating combat
  liveness. Existing plugin: register listener, bind identity after successful
  creation and clean its resources on every existing close path.
- `tests/test_wave7_echo_combat_admission.py`: execute actual listener/policy
  against narrow Paper boundaries; plugin build checks the real pinned API.
- Registered End Rift gate, acceptance document and requirement ledger.

## Test cycle

- [x] Reproduce foreign melee/sweep/Thorns, owner-pet attribution and foreign
  arrow/potion influence using permissive baseline behavior; retain RED output.
- [x] Implement exact source admission. Pets are not a substitute for the owner;
  a claimed causing entity cannot override a foreign direct entity.
- [x] Bind projectile receipt at accepted launch to event/generation/epoch/duel,
  source and world; no rebind or TTL refresh on duplicate callbacks. Reject
  missing, changed-source, expired or stale scopes. Retain ordinary unrelated
  interactions and environmental physics.
- [x] Filter splash intensities and cloud target lists before native effects,
  including beneficial healing and self effects. Bind lingering clouds only
  from admitted launches; scope cannot gain access when a context changes.
- [x] Bound to 64 tracked projectiles and 16 clouds, TTL at most 1,200 ticks,
  expire only tracked handles in the existing loop; cancel overflow launches.
  Cleanup is idempotent and retains handles if native removal throws.
- [x] Run focused/full regressions, pinned plugin build, registered gate,
  repository validators and `git diff --check`; review the actual scoped diff.
- [ ] Commit/push owned paths, verify remote SHA and actual own-SHA Actions.
  Install the current plugin on the authorized isolated server.

## Explicit boundaries and rulings

Ruling: Native fall/fire/environment damage remains governed by Minecraft;
unattributed external plugin effects are not accepted as proven isolation. The
listener cannot infer provenance that another plugin omits. Full official
room geometry, body collision, per-owner pause/resume, durable outcomes and
native multi-owner acceptance remain separate open Task A/E/H gates.

Ruling: Connect the policy to the current local slice before enabling autonomous
outgoing attacks. Activating unfinished official Echo would expose incomplete
hitbox/physics and persistence behavior. This costs one later controller binding,
not a second combat implementation.

No changes to the main Rift Guardian/Kagune or Wave 6 semantics. Do not launch
Minecraft during this code pass. Native two-owner melee/arrow/potion/Thorns,
helper collision and cleanup recordings remain NOT PERFORMED until exercised.
