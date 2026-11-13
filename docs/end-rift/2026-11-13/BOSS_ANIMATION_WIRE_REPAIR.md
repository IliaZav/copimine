# Boss animation integration defect: legacy cue versus strict V4 token

Reproduction uses the real final `sendClientPacket` adapter and the real
`EndEventBridgeBodyCodec`, not a fake accept-all transport. Before the repair,
the first `AWAKENING|IDLE_BREATH` cue was rejected. The adapter returned false;
the client could bind the model but never receive that animation transition.
The same incompatible shape was used by attacks, phase cues and heartbeats.

Root cause: the plugin retained `PHASE|ANIMATION` in its internal presentation
calls, but signed V4 `END_BOSS_PHASE.visualId` permits only a canonical animation
token. The independent sender forwards the visual field unchanged, and the
strict codec correctly refused the compound string before signing it.

`BossAnimationWirePolicy` now adapts the plugin's legacy cue at its final send
boundary. It validates the optional health-phase prefix, normalizes the known
artist aliases, and emits one known animation ID. Blank, unknown, NONE, malformed
and multi-separator values are refused. Boss-bar `PHASE|CAST_STATE` is a separate
message and remains unchanged. The signed codec/envelope are not relaxed.

Integration rule for subsequent work: do not make V4 accept arbitrary compound
animation strings. New clips need a deliberate entry in the server/client
catalogues and the strict body catalogue; the plugin adapter owns conversion
from its internal phase cue.

Evidence: `C:/Temp/cm-20261113-boss-wire-red.log` reproduces rejection before the
fix. `tests/test_boss_animation_wire_adapter.py` executes the actual adapter for
every health phase and known animation, artist aliases, invalid values and an
unchanged boss-bar packet through the real binary codec. Current focused repair
groups passed; final build/native acceptance is recorded separately. A green
older suite without this production boundary probe was insufficient evidence.
