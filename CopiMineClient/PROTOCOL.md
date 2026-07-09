# CopiMineClient Protocol v2

Channel:
- `copimine:client_bridge`

Protocol version:
- `2`

Transport model:
- optional client mod bridge over plugin messaging;
- server never requires Iris or OptiFine;
- if the client mod is missing or stops responding, the server falls back to server overlay or particle visuals;
- primary route is built-in ZIP shaderpack switching through Iris when Iris runtime is available;
- fallback route is CopiMineClient post-processing plus overlay composition;
- `white_sharp_1_2.zip` is kept as a spare built-in Iris shaderpack for manual tests and random visual selection.

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
14. `shaderpack:string`
15. `durationMillis:int`
16. `intensity:float`
17. `fadeInMillis:int`
18. `fadeOutMillis:int`
19. `mode:string`
20. `clearPolicy:string`
21. `source:string`
22. `reason:string`
23. `status:string`

Server -> Client:
- `visual_start`
  - fields used now: `seq`, `effectId`, `shaderpack`, `durationMillis`, `intensity`, `fadeInMillis`, `fadeOutMillis`, `source`, `clearPolicy`
  - client tries built-in ZIP shaderpack switching first
  - if that fails, client falls back to local post-processing
  - `visual_ack` status now distinguishes real routes such as `STARTED:IRIS_SHADERPACK` and `STARTED:FALLBACK_POST_PROCESS`
- `visual_stop`
- `visual_clear_all`
- `ping`

Client -> Server:
- `hello`
- `capabilities_update`
- `heartbeat`
- `visual_ack`
- `visual_finished`
- `visual_error`

Runtime rules:
- built-in ZIP shaderpacks are exported to `.minecraft/shaderpacks/CopiMine`;
- if Iris runtime switching works, CopiMineClient stores the previous shaderpack state, enables the requested CopiMine pack, then restores the old state after the effect;
- if the player already had an Iris shaderpack active, the previous state is restored after the CopiMine effect unless local config blocks override;
- if Iris is absent, unsupported, or a ZIP is not Iris-compatible, CopiMineClient uses fallback post-processing;
- non-Iris shaderpack ZIPs are not embedded in the active runtime set; unsupported visuals use fallback post-processing instead of pretending a ZIP was enabled;
- the server still applies gameplay effects even when no client mod is present.
