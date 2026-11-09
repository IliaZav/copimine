# Wave 5: November 9 user revision

This revision supersedes the historical fixed three/five-cycle rules and the
requirement to provide one safe cell for every player. It describes the current
implementation; it is not a native visual acceptance record.

| Admission roster | Fog cycles | Initial zones | Final zones |
| --- | --- | --- | --- |
| 2 | 5 | 1 | 1 |
| 5 | 6 | 2 | 1 |
| 10 | 7 | 4 | 2 |
| 11 | 8 | 4 | 2 |
| 20 | 10 | 7 | 3 |

The roster and cycle target are frozen at Wave 5 initialization. All rosters
start with 3x3 shelters, pass through 2x2, and finish with 1x1 shelters. Counts
decrease over the encounter. Late shelters deliberately cannot hold the whole
party. Reconnect/death cannot reroll or expand them.

The visible warning is three seconds. Additional reach time decreases from four
seconds to one (total warning/reach: seven to four seconds). Fog duration grows
from eighteen to twenty-six seconds. Combat segments grow from twenty-five to a
bounded thirty-five seconds. Each shelter has an exact square green dust outline
at half-block spacing, at most twenty-four points per zone twice per second.

Layouts use an event/generation/cycle seed and bounded candidate search. A zone
must have a complete clear footprint; islands cannot touch. Floors with block
entities are refused. The original-block journal must prepare successfully
before any floor is changed. Empty/invalid layouts still fail closed. Scarce
capacity is intentional and does not itself prevent the hazard.

Fog uses the custom arena-bounded depth/volume post-process in the active 26.3
client. It does not require Iris or a shaderpack. Server debuff is Slowness II
outside shelter. Blindness, Darkness, Wither and the arena-wide ink particle
substitute were removed from this hazard. Existing effect ownership restores
unrelated player effects. Periodic damage follows the existing bounded
non-lethal policy without the old 1.6 multiplier.

Each completed fog owes one finite pressure pack. Genuine dead ordinary roles
are recorded; two to six can be resurrected within a pack, limited by actual
recorded deaths. The ledger is consumed only after a complete successful spawn;
partial placement rolls back without settling the receipt. Resurrected entities
give no second configured loot or XP. Packs contain ten to fifteen bodies, plus
three after a full clear. Live pressure remains capped. At most three
clear-triggered pre-fog refills can occur in one combat segment.

An optional elite roll occurs once per fog, halfway through the volume. Chance
starts at eight percent and grows to at most eighteen percent, or thirty percent
after a full clear. The role is Enderman, skeleton or spider. There can be at most
two live event-owned elites from the ordinary elite category; the overall live
cap also applies. An optional refused spawn does not reroll indefinitely.

Snapshot version 3 stores the frozen cycle target. Old version 1/2 checkpoints
retain their bytes and require explicit recovery rather than silently adopting
the new encounter. As before, transient Wave 5 combat is not automatically
recreated after a cold restart. Death ledgers and roll masks cannot be replayed
through a live automatic resume.

## Evidence boundaries

- `artifacts/20261109-escalation-regression.txt`: 204 passed.
- `artifacts/20261109-escalation-tests3.txt`: 35 passed before the additional
  terrain/seed scenarios.
- `artifacts/20261109-escalation-client.txt`: active 26.3 tests and JAR build passed.
- `artifacts/20261109-post-review-plugins.txt`: migration plugins and bridge tests passed.
- Final expanded tests, full suite and CodeRabbit review are recorded in
  `artifacts/20261109-final-fog-checks.txt`,
  `artifacts/20261109-escalation-all-tests.txt` and
  `artifacts/20261109-escalation-review.ndjson`; read their actual results.

Native visual verification is **NOT VERIFIED**. Computer-use automation was
explicitly prohibited by the user. These edits do not assert that production has
been updated. Release pins, reproducibility, installation and final runtime
acceptance must refer to the final source/artifacts, not an earlier green build.
