# Wave 7 return: loaded room boundary

Baseline: [9864eb2c](https://github.com/IliaZav/copimine/commit/9864eb2cc3f36872437ef965e5ddc67d077c2395),
branch `codex/end-rift-event`. This is part of V3 Task B/H participant return and
bounded world inspection. Named Echo/Marksman/Archmage integration remains open.

The entrance resolver already refused unloaded candidates, but staging's final
room search called the inherited Core floor sampler and read candidate blocks
before checking chunk availability. A loaded candidate centre could also hide
an unloaded adjacent chunk needed for the player's full clearance. These calls
could synchronously load chunks during admission despite the entrance check.

`test_wave7_return_chunk_boundary.py` executes the production search, staging
loop and room-permission validator with the real lifecycle and movement
policies. Before repair, its two cases failed on an unloaded block read and the
inherited Core floor scan. Extending the probe to the actual permission
validator exposed a second Core floor scan after candidate selection; that
failure was observed and repaired as well.

Staging now reuses the physically validated entrance's feet level and passes
that same anchor to room permission validation. The original room/destination
rules still decide admission. Official Wave 7 player candidate searches check
all clearance chunks, world height and world-border corners before reading any
blocks. Missing clearance rejects a candidate; it never substitutes a forced
chunk load, Core placement, reward or reset. The existing 48-candidate search
remains bounded. Other wave and boss callers keep their existing behavior.

The regression also covers a loaded centre with an unloaded neighbouring
footprint, fully loaded deterministic selection, world-border/height refusal,
successful staged admission, refusal without teleport when room chunks are
unloaded, and stale generation. It is registered in `RunEndRiftEventChecks.ps1`.
The first focused integrated run passed **26** cases. The full Python run passed
**981 tests, 1 skipped**; the End Event build and registered End Rift gate passed.
CodeRabbit completed the immutable six-file source/test/document slice with
**zero issues**. Its sealed Codex Security diff review found no confirmed
vulnerability with explicit runtime/composition gaps. Publication is tracked
separately; these checks do not establish native gameplay acceptance.

This source-executing regression does not establish native return presentation,
real-client chunk-unload timing, bed/quit/reconnect coverage or new actor
HP/supply persistence. [Actual-server receipts](WAVE7_RETURN_RUNTIME_ACCEPTANCE.md)
belong to their separately recorded source, not automatically to this change.
No native verification status is granted by this checkpoint.
