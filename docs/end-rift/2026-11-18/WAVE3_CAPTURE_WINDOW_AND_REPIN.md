# Wave 3 portal capture window restored to five seconds

Date: 2026-11-18
Scope: Wave 3 portal objective timing, indicator fixture, candidate re-pin.
Spec: `COPIMINE_END_RIFT_WAVES_CANONICAL.md` section 5.2.

Status vocabulary:

* **IMPLEMENTED** - the code path exists in the shipped sources.
* **AUTOMATED-TESTED** - a gate test drives the real code path.
* **LIVE-PAPER-VERIFIED** - exercised on a live Paper server.
* **VISUAL-VERIFIED** - confirmed on a real client.
* **NOT VERIFIED** - neither of the above.

## 1. What the canonical text requires

Section 5.2 of the canonical Wave 3 text asks for a portal that closes after
"roughly five continuous seconds" of real eligible occupancy inside the capture
region. The capture region stays at roughly 2.5 blocks, and the 450 ms grace
window that forgives a single stepped-out tick is unchanged.

## 2. The defect

`PortalCapturePolicy.CAPTURE_MILLIS` held `18_000L`, an eighteen-second window.
The only provenance for that number was a local commit message; no recorded
user decision asked for it. It directly contradicted:

* the canonical section 5.2 wording;
* the repository's own requirement matrix, where `W3-02` pins the boundary at
  4.9 s (not complete), 5.0 s (complete) and 5.1 s (complete);
* the same file's own comment, which still described the retired five-second
  behaviour as the thing the eighteen seconds replaced.

Under the authority order in canonical section 0, an implementation description
sits below the specification, so the eighteen-second value was a defect rather
than a variant.

## 3. The change

* `PortalCapturePolicy.CAPTURE_MILLIS` is `5_000L` again.
* The comment now cites canonical section 5.2 and records why the eighteen
  seconds were removed instead of restating them.
* `PortalCaptureIndicatorPolicy` no longer hard-codes "18 s" in its segment
  comment, so the ring description cannot drift from the timing again.
* `tests/PortalCapturePolicyTest.java` and
  `tests/EndRiftWave34IndicatorsTest.java` now pin the exact boundary: 4_999 ms
  must not complete, 5_000 ms must complete. Indicator fixtures derive their
  progress from `CAPTURE_MILLIS` instead of the removed magic 9_000 / 17_990 /
  18_000 constants, so a future timing change cannot leave the ring asserting a
  stale fraction.

This is a narrowed, not a loosened, guard: the old test only required "at least
eighteen seconds"; the new one requires exactly five seconds and still rejects
an early close by one millisecond.

## 4. Evidence

| Claim | Status |
|---|---|
| Capture window is five continuous seconds | IMPLEMENTED |
| 4_999 ms does not close, 5_000 ms does | AUTOMATED-TESTED (`tests/PortalCapturePolicyTest.java`, `tests/EndRiftWave34IndicatorsTest.java`) |
| Indicator ring follows the authoritative fraction | AUTOMATED-TESTED (`tests/EndRiftWave34IndicatorsTest.java`) |
| Full repository pytest run | AUTOMATED-TESTED: `3679 passed, 23 skipped` |
| End Rift event gate (`tests/RunEndRiftEventChecks.ps1`) | AUTOMATED-TESTED: exit 0, `127 passed, 2 skipped`, wave-hit harness `27 passed` |
| Portal closes at five seconds in live play at 2/5/10/20 players | NOT VERIFIED - no players online, `/cmend test wave 3` refused an empty sandbox roster |
| Ring fill and collapse read correctly on screen | NOT VERIFIED - needs a real client |
| Portal geometry in the built world | NOT VERIFIED - needs a real client |

## 5. Re-pin

The End Event sources changed, so the compiled jar moved and the candidate was
re-pinned to `2578631` /
`637a08f3372cfe103e78fcf5ed18a2910e54627ef74931f236751a98767142ac`.

The replaced `0023966595152218288af1f02db1e4bb122f8a2178b043077930b185addaa6bb`
digest was prepended to `replaceSha256`, following the prepend convention in
`docs/end-rift/CANONICAL_SPEC_PINS.md` section 5, so an already-staged older
candidate is still accepted.

`docs/end-rift/final-26.3/OWN_PLUGIN_INVENTORY.json` carries the same new
`candidateSha256` and `sha256`.

The historical `docs/end-rift/2026-11-18/MARKSMAN_HIDE_COOLDOWN_AND_REVEAL.md`
keeps the previous pin as a record of that repair; it is deliberately not
rewritten.