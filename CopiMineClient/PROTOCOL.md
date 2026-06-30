# CopiMineClient Protocol v2

Channel:
- `copimine:client_bridge`

Protocol version:
- `2`

Transport model:
- optional client mod bridge over plugin messaging;
- server never requires Iris or OptiFine;
- if the client mod is missing or stops responding, the server falls back to server overlay or particle visuals;
- primary visual route is client-side post-processing plus overlay composition inside `CopiMineClient`;
- if the player already runs an Iris shaderpack, CopiMineClient does not inject into that shaderpack and does not replace it.

Message envelope:
1. `type:string`
2. `protocol:int`
3. `seq:long`
4. `timestampMillis:long`
5. `sessionId:string`
6. `clientVersion:string`
7. `clientVisuals:boolean`
8. `clientOverlay:boolean`
9. `clientShaderLike:boolean`
10. `trueIrisShader:boolean`
11. `supportedEffectsCount:int`
12. `supportedEffectIds:string[]`
13. `effectId:string`
14. `durationMillis:int`
15. `intensity:float`
16. `mode:string`
17. `clearPolicy:string`
18. `source:string`
19. `reason:string`
20. `status:string`

Client -> Server:
- `hello`
  - announces protocol `2`, current `sessionId`, client version, capabilities, supported effects
- `heartbeat`
  - keeps capability state alive for the current `sessionId`
- `visual_ack`
  - acknowledges `visual_start`, `visual_stop`, or `visual_clear_all`
  - statuses used now: `STARTED`, `STOPPED`, `CLEARED`, `ERROR`
- `visual_finished`
  - client-side effect finished normally or was cleared locally
- `visual_error`
  - client-side effect could not start or continue

Server -> Client:
- `ping`
  - handshake acknowledgement and liveness probe
- `visual_start`
  - starts a specific visual effect for the current `sessionId`
  - fields used: `seq`, `effectId`, `durationMillis`, `intensity`, `source`, `clearPolicy`
- `visual_stop`
  - stops one specific effect id for the current session
- `visual_clear_all`
  - clears all active client visuals for the current session

Session and reliability rules:
- the client generates a fresh `sessionId` on join;
- `hello` is retried until a server `ping` is received or retry budget is exhausted;
- heartbeats start only after the handshake is acknowledged;
- server commands are tracked by `seq`;
- if the server does not receive `visual_ack` in time, it falls back automatically;
- if session ids stop matching, the server drops stale client messages.

Supported effect ids:
- `DESATURATE`
- `COLOR_CONVOLVE`
- `SCAN_PINCUSHION`
- `GREEN_NOISE`
- `INVERT`
- `WOBBLE`
- `BLOBS`
- `PENCIL`
- `CHAOS`

Notes:
- `supports_true_iris_shader=false` is intentional: the mod uses its own post-process pipeline and does not depend on an external shader loader;
- `trueIrisShader=true` only means the player already has an Iris shaderpack enabled; it does not switch CopiMineClient into a different renderer and does not let the server force a true Iris post-process pass;
- without the client mod, gameplay still works; only the visual route degrades to server fallback;
- Paper cannot force true per-player post-processing shaders through a normal server resource pack, so the server delegates the rich path to `CopiMineClient` and keeps honest fallback routes for everyone else.
