# End Rift Event V2 — implementation contract

This repository copy records the user-approved V2 contract supplied on 2026-09-07. It is intentionally a concise, executable contract; the original master prompt, full design specification, artist brief, Blockbench model archive and reference images remain the source material supplied with the task.

## Non-negotiable rules

- Gameplay is server-authoritative. The Fabric client renders models, animations, beams and VFX only.
- Boss V2 has real Bukkit entity HP. The legacy virtual-health PDC path is removed from official combat and must not remain as a hidden fallback.
- Diagnose intermittent missing damage with a structured Combat Trace before changing damage handling. Do not reset `noDamageTicks`, add arbitrary delay, or disable safety checks as a substitute for a root cause.
- Do not use action-bar text, spell names, percentages, or technical timers as the primary explanation of combat mechanics.
- All temporary entities, displays, projectiles, tasks, mutation-journal entries and visual overlays are cleared on victory, wipe, Core removal, reload, recovery and plugin disable. A restart during an active attempt returns safely to `READY_FOR_PLAYERS`.
- Preserve the current local map and local-only boundary. No production server, production database, launcher, or website modification is in scope.

## Official event flow

`UNCONFIGURED → COLLECTING → READY_FOR_PLAYERS → START_RITUAL → WAVE_1 → INTERMISSION_1 → WAVE_2 → INTERMISSION_2 → WAVE_3 → INTERMISSION_3 → WAVE_4 → INTERMISSION_4 → WAVE_5 → INTERMISSION_5 → WAVE_6 → PRE_BOSS_COOLDOWN → BOSS_CINEMATIC → BOSS_ACTIVE → BOSS_FINISH → VICTORY_PROCESSING → UNLOCKED`.

Every transition is durable and idempotent. Old `COUNTDOWN`, `FINAL_DRAIN`, `FINAL_RITUAL`, `FINAL_WAVE`, and `VICTORY` flow names are only read as migration aliases; no official V2 path can enter them.

## Gameplay requirements used by the implementation plan

- Five waves have five-second transition-rune checks. Wave 6 is chamber based and has a twenty-second pre-boss safe period instead of a rune check.
- Scale challenge with special-target HP, bounded concurrent pressure, target pressure and mechanic count, not unlimited raw mob damage. Support 2, 3, 10 and 20 participants; Wave 6 uses participants physically inside each chamber.
- Wave 3 contains three sequential, depth-correct Rift portals and only two pushers in a defending pack. VFX must be actual client-space geometry, not a black-purple mass or particle line.
- Wave 4 has three combat segments and shrinking emerald safe zones with capacities, protection barriers, fog timing and journaled arena mutations.
- Wave 5 uses three rotating rings with a hard 72-display budget, duo revival, prisoner objective and bounded guard pressure.
- Wave 6 has 2–4 isolated chambers. Target selection, pathing, projectile, AoE and teleports cannot cross a closed chamber.
- Boss real HP scales from 5,000 at two players through the V2 table to 20,000 at twenty. Boss stages are Awakening, Hunt, Rift, Overload, Rage and Last Seal.
- Rift Obelisks are a one-shot Rift-stage mechanic. They are not the old recurring spell. Any remaining visual/logic support must be bounded and cleared.
- Permanent and temporary tentacles follow the supplied artist brief: skeleton segments, a `grab_socket`, claws, all named animations and server-timed gameplay markers. A placeholder cube/pole/particle substitute is not acceptable.
- Official boss rewards are personal and idempotent: guaranteed `rift_core_shard`; independently persisted 30% `night_cloak` roll before issuing; existing Artifacts API authenticity and owner binding only.
- The shard title and the single lore line `Из него всё ещё доносятся отголоски хаоса Разлома.` are yellow. Its V2 abilities are server-authoritative and its lore does not list abilities.

## Evidence gate

Each implementation unit follows red → green → targeted tests → relevant suite → plugin/client/resource-pack build → local Paper scenario where needed → cleanup/restart check. Final release evidence must include 2-player completion, 10-player scale verification, 20-player performance smoke test, visual screenshots, persistence/reward idempotency, artifact hashes and commit SHA.
