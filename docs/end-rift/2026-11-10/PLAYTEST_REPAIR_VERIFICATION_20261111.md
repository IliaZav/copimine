# Playtest repairs and overnight review — 11 November 2026

This is an implementation/evidence ledger, not native gameplay acceptance.
Both 27 October canonical documents were re-read and their SHA-256 hashes
matched the AGENTS.md gate. The 31 October contract and the player's latest
playtest corrections take priority.

## Overnight work retained and reviewed

Commits `aec77c25` through `44e65b58` were retained. They repair charge custody,
test-wave combat phase admission, phantom creation, obelisk cadence/death,
portal closing, Wave 7 particle payloads, Marksman squad ownership, event music
ownership, miniboss pursuit and separate ability signatures. No rollback of
that work was performed. CRLF-only churn was excluded when reviewing meaning.

The production logs of the actual player session still describe the older
`249076c4` runtime. No skeleton bow shots were recorded despite skeleton creation;
the overnight test-wave AI admission repair had not yet been installed there.
The session did record physical elite web casts. Thus absence of a screenshot
projectile is not, by itself, proof that no web cast was attempted.

## Additional repairs

- Wave 5 evacuates owned hostile bodies from safe chambers, including the
  overhang of a wide spider. A bounded collision-checked exit search preserves
  world blocks and mob identity. A failed evacuation enters explicit recovery.
- Actual baked Wave 7 body faces mapped to transparent pixels of the existing
  atlases. Three native-model atlas tests failed before repair. The UV repair
  uses painted material islands without changing source PNGs, mesh positions,
  normals or animation hierarchy; intentional rune transparency is retained.
- Ram armour uses three accepted impacts. Its custom bar displays real HP
  separately from remaining armour; armour loss cannot fabricate HP loss.
  Eye/snort effects follow the enlarged head geometry. Ram brood receives
  an articulated carapace/spine model using the existing event skin, scoped to
  the signed Endermite binding. Natural Endermites keep their normal renderer.
- Kagune attack deadlines survive flinches, and due attacks cannot be postponed
  by repeated accepted hits. Missing reconciliation deadlines are stored once.
  Accepted HP changes flash the rig without restarting its committed clip.
  Ambush/temporary roles retain smaller scale through blending and differ in
  tint. Eight main roots fit a 5.5-block ring without overlapping root spacing.
- Defeating the guardian shield releases the boss hover while vulnerability
  lasts. The existing independent guardian respawn deadlines are preserved.
- Grounded boss tactics include deliberate close approaches. A nearby current
  focus can receive one authored swipe between major casts, with an independent
  eight-second cooldown, line-of-sight/control checks and the existing contact
  marker. This does not create another direct damage path.
- Lethal `/cmend boss damage` on a disposable boss starts its death scene rather
  than setting HP directly to zero. Repeated requests cannot replay the scene.
  Administrative lethal damage cannot authorize official rewards or End unlock.
- Custom HUD callbacks and mixin registration are checked explicitly; a class
  merely existing on disk is insufficient evidence that it runs.

## Evidence boundaries

Client: 369 tests passed, including baked atlas coverage, unchanged mesh geometry,
Kagune role scale/HP flashes, Ram HUD armour parsing and Endermite articulation.
Server bridge: 112 tests, zero failures/errors, one explicitly skipped test.
Focused repaired adapter/contract suite: 114 passed. The real admin-death adapter
and accepted/cancelled Kagune damage adapters were executed in Java probes.

The first full pytest run exposed stale adapter stubs, superseded fog timing/HUD
assertions, an out-of-date legacy refmap build, and old release pins. Those
failures are retained in `C:/Temp/cm-playtest-all-20261111.log`; they are not a
passing result. After repair the full local suite reports 3224 passed, 18 skipped
and 2 failures that only occur on this workstation: `pwsh` is absent there and
the machine-wide execution policy is `Restricted`, so the two tests that shell
out to the real packaging tail cannot start PowerShell at all. CI runs on
`windows-latest` with `pwsh`, where those two execute.

The `End Rift current local checks passed` gate is green, and the client jar is
now reproducible: two packaged resources under a `text eol=lf` rule had been
edited into CRLF on a Windows checkout, so the same sources built a jar 17 bytes
larger than on a clean runner. Normalising them made the local build and the CI
build byte-identical at 15 013 152 bytes / `4810de65…`, which is why the pin
changed in `a60b758b` rather than the code.

## Installed runtime and public downloads

Candidate `a60b758b` (`722ad4d6…`) is installed on the production service and
reports healthy. The three other artifacts reproduce their pins byte for byte
from a clean rebuild.

| Artifact | Size | SHA-256 |
|---|---|---|
| `CopiMineEndEvent.jar` | 2 282 005 | `722ad4d617…878dc7` |
| `CopiMineClient-0.1.3+26.3.jar` | 15 013 152 | `30e9c1aac3…d2f123` |
| `CopiMineNarcotics.jar` | 370 499 | `026787808e…835fb1` |
| `CopiMineResourcePack-26.3.zip` | 33 371 080 | `17aa6d3b2e…d8eb9` |

Measured after the restart:

- `systemctl is-active copimine-game.service` → `active`;
- `[CopiMineEndEvent] Enabling CopiMineEndEvent v0.1.0` present once;
- `END_EVENT_RUNTIME_DIAGNOSTICS … phase=COLLECTING`, `owned_living=0`;
- `grep -cE 'SEVERE|ERROR' logs/latest.log` → `0`;
- installed plugin digest equals the pinned candidate;
- `/assets/downloads/CopiMineClient-0.1.3+26.3.jar` and
  `/assets/CopiMineResourcePack-26.3-17aa6d3b2edf626f.zip` were fetched back
  over HTTPS through the site's own vhost and hash-matched the stage inputs.

Old client jars were removed from public downloads, leaving only the current
immutable URL and the mutable `CopiMineClient-0.1.3+26.3.jar` alias.

The first deploy attempt died when the SSH session dropped during the
multi-minute backup tar step, after `systemctl stop` but before any file was
replaced. The service was restarted on the unchanged predecessor, the completed
backup was verified (`SHA256.json`), and the retry ran detached so a disconnect
could not interrupt it. Nothing was left half-installed.

`nativeVerified=false` for this candidate. The player's earlier F2 captures
establish the reported defects and prior transport acceptance. They do not
visually verify this new build. Fog readability, model appearance, phase music,
Inferno spectacle, final-phase dynamics and the victory scene remain subject to
fresh player screenshots/gameplay; no computer control or player login was used.
