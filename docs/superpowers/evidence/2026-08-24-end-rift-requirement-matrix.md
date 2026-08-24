# End Rift requirement matrix — 2026-08-24

This matrix separates source/contract evidence from observations made in the
isolated local Paper/Fabric runtime. It is intentionally tied to the current
addendum; the older v5 wording about Endermites and a 1000 HP boss is
superseded by the later user decisions recorded in
`docs/superpowers/specs/2026-08-24-end-rift-completion-design.md`.

| Requirement | Result | Evidence |
| --- | --- | --- |
| Core uses the exact selected real block without changing vanilla material | Confirmed | Paper status `coreOverlay=true`, Core display `830002`, client screenshot `09.19.09`; visual regression contracts |
| Core/rune event art is 32x32 and not narcotics fallback art | Confirmed | Resource-pack contracts, pack ZIP hash, 32x32 PNG inspection, client screenshots |
| Russian, material-colored resource progress and charged state | Confirmed | `CREATIVE_TEST_RESOURCES charged=true`, final RCON progress, client-side Russian text |
| Runes appear/rebuild after resources are complete | Confirmed | `runes=2/2` in RCON and Creative markers; rune repair watchdog and actual idle screenshot |
| Occupied rune changes color/model on a player | Confirmed | `occupied=1`, model `830005`, occupied green screenshot; returns to `occupied=0`/`830003` |
| Runes lie on the floor top surface and cover the block top | Confirmed | Exact display coordinates, model geometry contracts, idle/occupied screenshots |
| Arena boundary is 20 horizontal / 3 vertical and has timed particle preview | Confirmed | Config and source contracts; bounded boundary renderer and command tests |
| OP Core break opens a confirmation GUI | Confirmed | Actual OP GUI screenshot `09.26.11`; click/ownership/cleanup contracts |
| Core removal stops session and removes current/stale event entities | Confirmed | `removeCore` ownership contracts, generation/session cleanup paths, Creative cleanup runtime (`event-mobs=0`, `boss=none`) |
| Waves contain spiders, Endermen, elites, and shulkers; no Endermites | Confirmed | Creative Paper compositions and source/config/resource tests; `rg` audit has no Endermite spawn path |
| Spider wave stats are +10 health / +2 damage | Confirmed | Paper `SPIDER_STATS health=26.0 attack=4.0` markers |
| Mini-bosses have one stable spell each | Confirmed | Runtime `miniBossSpells=[rift_step, void_snare, echo_pulse]` and telegraph/flight markers |
| Boss is 1200 HP with 600 and 120 thresholds, final health 200 | Confirmed | Paper BossBar/Creative markers and threshold tests |
| Boss and spells use visible particle telegraph/flight | Confirmed | `BOSS_SPELL_TELEGRAPH`, `BOSS_SPELL_FLIGHT`, `BOSS_SPELL_CAST`; real boss screenshot |
| Mob/boss containment stays within 20 horizontal and +/-3 vertical | Confirmed | `WAVE_AI_LEASH`/`BOSS_AI_LEASH` runtime markers and AI contract tests |
| Final drain, final wave, finish, and disposable cleanup | Confirmed | Creative markers through `CREATIVE_TEST_COMPLETE`; final RCON has zero event mobs/boss |
| Two-point gate opens inclusive cuboid top-down with particles | Confirmed | Paper gate layer log y=72 -> 70, progress 6/6, gate status `OPENED` |
| Gate interruption restores full snapshot on restart | Confirmed | Local Paper restart caught `OPENING progress=2/6`, then `RESTORED_ON_BOOT`; recovery backup |
| Gate open/victory operation is idempotent | Confirmed | Source idempotency contract and repeated local open after `OPENED` |
| Event-specific custom entity visuals are UUID/generation scoped | Confirmed | Fabric renderer contracts, client JAR build, resource-pack tests, live Enderman/Guardian screenshots |
| Vanilla peer textures remain untouched | Confirmed | Renderer mapping tests, no global model override, no vanilla block texture overrides |
| All source/build/asset/durable checks are part of the repeatable gate | Confirmed | `RunEndRiftEventChecks.ps1`: 105 Python passes, domain/durable OK, hashes recorded |
| Production safety boundary | Confirmed | Local-only config/runtime/loopback evidence; launcher/site files untouched |
