# Hybrid combat ownership - 13 November 2026

Base: ae8b735c. The November 11 master and later explicit user decisions apply.
The two October 27 canonical hashes were checked unchanged before this pass.

## Integration contract for subsequent DeepSeek changes

The boss body has coordinated, separate movement and damage owners. Preserve
these runtime boundaries when adding spell art or modifying AI:

- `tickBossCastLocomotion` keeps target selection and bounded path updates alive
  during compatible casts. It still returns ownership to the caller, which must
  not admit a second major spell or melee action before the cast has finished.
- `allowsBossCastLocomotion` permits contact recovery only after the existing
  coordinator publishes RECOVERY. Shared animation playback has already retimed
  the contact marker. A second hand-written clock would drift from the hit.
- `bossEdgeVolleyActive` keeps all five salvos anchored, even though ordinary
  RIFT_ARROWS permits locomotion. Final Seal hover/fall retain their stronger
  position owner. A navigation change must respect both exceptions.
- Pursuit selection and spell targeting stay separate. Movement never rewrites
  a committed mark/target. Invalid admitted focus is reconciled promptly, while
  refused target writes and unavailable paths share bounded retry budgets.
- Failed destination selection/pathfinding consumes the 500 ms path budget,
  rather than retrying the expensive operation on every server tick.

## Real damage ownership

Native Enderman navigation remains enabled, but its unowned melee events cannot
create an extra hit beside the authored contact. `Player.damage` carries a
transient `PendingPlayerDamageOutcome`. LOWEST captures the matching event
identity before protection callbacks; HIGHEST checks participant, encounter and
identity and applies the existing phase bonus once. MONITOR retains the final
event only for that attempt. A nested damage call cannot reuse the receipt or
replace its outcome. Cancellation remains cancellation, including an outer
strike cancelled by a protection listener. This introduces no second HP pool.

## Evidence

The mobile-cast regression initially failed because the production tick had no
navigation path before its cast-state early return. The native-contact probe
initially failed because an unowned native hit was accepted. The real adapters
now execute telegraph/execution/recovery, all spell families, pinned hover,
Edge Volley, path refusal, target refusal, native/authored/nested/cancelled
damage and phase/generation/participant fences. Focused combat suites passed
140 tests, with 2 existing environment skips. Modern server compilation passed.

The general Java release gate from the prior run remains failed on downloaded
legacy client tooling/dependencies; its failed output was not renamed a pass.
The interrupted local gate outputs were recovered with the repository snapshot
verifier before this work, retaining a verified pre-recovery backup.

Real Paper combat, native animation/impact pictures, party performance and full
official encounter acceptance remain NOT VERIFIED. No production or working
Prism replacement is performed by this source patch.
