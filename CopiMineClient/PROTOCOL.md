# CopiMineClient Protocol v3

Channel:
- `copimine:client_bridge`

Protocol version:
- `3`

Transport model:
- CopiMineClient is mandatory for joining the Minecraft 26.3 CopiMine server;
- the server accepts a client challenge response only when it reports mod ID `copimineclient` and the configured exact version; other gameplay and graphics mods are optional;
- Fabric Loader and Fabric API remain client launch dependencies because this mod uses their APIs, but the server does not check them as separate mod identities;
- server never requires Iris or OptiFine;
- if the client does not answer before the handshake timeout, reports a different ID or version, or sends a malformed response, the server disconnects the client;
- while the challenge is pending, movement, interaction, inventory actions and chat are blocked; only authentication commands `/login`, `/l`, `/register`, `/reg`, `/2fa`, `/premium`, and `/freemium` remain available;
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

Required client check:
1. On join, the Paper bridge creates a new random challenge ID and expiration time for that player connection.
2. It sends `client_mod_challenge` with the current protocol, challenge ID in `sessionId`, configured required version in `clientVersion`, and required mod ID in `mode`.
3. CopiMineClient answers with `hello`, echoing the challenge ID and reporting its loaded Fabric metadata version and mod ID.
4. While a challenge is pending, the server accepts only the exact ID, exact version, protocol 3, and matching unexpired challenge, then acknowledges the session with `ping`.
5. If no response arrives before timeout, the player is disconnected. While a challenge is pending, a response with a different challenge ID, malformed payload, mismatched ID/version, or wrong protocol is rejected. A repeated `hello` for an already verified session is acknowledged with `ping`; it does not repeat the initial check. The handshake timeout and required version live in `copimine-narcotics/config.yml` and are checked against `tools/minecraft-26.3/profile.lock.json`.

This challenge makes ordinary vanilla clients and wrong CopiMineClient releases fail the compatibility gate. It is not cryptographic client attestation: a deliberately modified client can implement the same public protocol and claim the expected ID/version. The payload format contains no shared secret.

Server -> Client:
  - `client_mod_challenge`
  - fields used: `sessionId` (one-connection challenge), `clientVersion` (required version), `mode` (required mod ID)
  - `visual_start`
  - fields used now: `seq`, `effectId`, `shaderpack`, `durationMillis`, `intensity`, `fadeInMillis`, `fadeOutMillis`, `source`, `clearPolicy`
  - client tries built-in ZIP shaderpack switching first
  - if that fails, client falls back to local post-processing
  - `visual_ack` status now distinguishes real routes such as `STARTED:IRIS_SHADERPACK` and `STARTED:FALLBACK_POST_PROCESS`
- `visual_stop`
- `visual_clear_all`
- `ping`
- `END_EVENT:END_BOSS_BAR` (optional End Rift HUD snapshot)
  - `seq`: event generation
  - `sessionId`: event id
  - `clientVersion`: boss binding instance id
  - `intensity`: health progress from `0.0` to `1.0`
  - `fadeInMillis`: rounded current boss health
  - `fadeOutMillis`: rounded configured maximum health
  - `mode`: UUID of the bound Rift Guardian
  - `shaderpack` / `clearPolicy`: `PHASE|ABILITY_STATE`, for example `RIFT|EXECUTING`
  - the client accepts the snapshot only when both the boss UUID and binding instance match;
    server-side BossBar fallback remains available if a verified client cannot render this optional presentation
- `END_EVENT:END_BOSS_PHASE` (optional UUID-bound boss model state)
  - `mode`: `PHASE|ANIMATION`, for example `LAST_SEAL|FINAL_STRIKE`
  - the client swaps the vanilla Enderman model only for the bound boss UUID;
    every other Enderman keeps the vanilla renderer and texture
  - canonical animation IDs are `IDLE_BREATH`, `RUN`, `MELEE_SWIPE`,
    `CHEST_STRIKE`, `GROUND_SLAM`, `MARK_CONTROL`, `SUMMON_CHANNEL`, `HURT`,
    `PHASE_TRANSITION`, `FINAL_STRIKE`, `DYING`, `TELEPORT_RIP`,
    `CAST_CHARGE`, `CAST_RELEASE`, `CAST_IMPACT`, `RECOVERY` and the
    spell-specific `SPELL_*` release poses
  - the supplied artist names are aliases, not separate wire IDs:
    `Running2`, `Swipe2`, `Hurt2`, `Dying2`, `udar_iz_grudi`,
    `udar_po_zemle2`; an unknown value becomes an explicit `UNKNOWN` pose and
    is logged instead of silently becoming idle

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
- the server remains authoritative for gameplay effects. Separately, a player may finish joining and receive CopiMine client-side rendering only after the server accepts a challenge response reporting the required CopiMineClient ID and version. This protocol does not prove that an unmodified copy of the mod is installed.
