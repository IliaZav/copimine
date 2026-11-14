# Wave 6/7 code and music release — 2026-11-14

The user explicitly authorized immediate production installation and delegated
all tests/gameplay acceptance to DeepSeek. No tests or Minecraft launch performed.
This supersedes the previous no-production instruction for this release only.

## Implemented

* Ram visual identity now accepts all seven impact receipts (IDs 0 through 7).
  The previous body catalog stopped at 5; the HUD stopped at 3. Both now follow
  the server's actual accepted impact count, with seven remaining armour icons.
  The existing vanilla hide and real owned 3D armour pieces remain unchanged.
* Accepted player contact no longer aborts a charge before its pillar. Each player
  still has one charge-hit receipt. The locked charge continues with eye/snort/
  hoof effects and bounded positional steps until pillar, obstruction or expiry.
* The Archmage now has explicit footwork between casts: approach into its existing
  engage range, retreat if crowded, otherwise move along a stable flank. Every
  step is chamber/actor-clearance checked and uses the shared steady movement
  controller. No movement override is introduced during an active spell tell.
* Four original procedurally composed recordings: Ram (96 BPM, 7-beat meter),
  chamber trials (112 BPM, 5-beat meter), final melee fury (148 BPM, 4-beat meter),
  and intermission 4 (68 BPM, 3-beat meter). They contain no external recordings.
* Existing seven-wave and six-phase music remains separately identified. Wave
  6/7 now use independent compositions instead of related source arrangements;
  cocoon break changes to a distinct fury track. Intermission 4 no longer aliases
  intermission 3. Other authored themes and their credits are preserved.
* The required client now embeds the event sound catalog and OGGs; event music
  does not depend on the server pack being loaded or an optional shader mod.

## Build/acceptance boundary

Server: clean endEventJar narcoticsJar succeeded, 16 s. Client: clean jar succeeded,
18 s. No test tasks were invoked. Resource pack built and migrated using the
pinned vanilla definitions, with updated exact source/candidate hashes. Warm and
cold server artifacts agreed. Native visual/audio quality, combat/recovery, party
scaling, regression tests and exact-SHA CI are NOT VERIFIED for these changes.

DeepSeek: extend obsolete HUD tests that require only three plates; verify all
seven impacts, late-stage bindings, uninterrupted charge through players, collision
receipts, Archmage footwork/telegraphs and all track switches/loop boundaries.
Inspect all new Guardian models from the preceding source commits. Regress prior
Inferno journal and final-fury/target-selection repairs. No broad tests were weakened.

Prepared artifacts and deployment installer: artifacts/20261114-wave67-production/.
Production receipt is created only after full runtime/database backup, allowlisted
old hashes, verified atomic install and actual service/signer startup. Deployment
status must be read from installed-receipt.json; this document alone is not proof.
