# Task 8 brief — client boss model, phase protocol, and mob texture packaging

Read this file first; it is the exact requirements for this task.

Repository: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`, branch `codex/end-rift-event`.

Implement only the optional Fabric client and its asset/package tests. Do not edit the server plugin, YAML, persistence, runtime, website, launcher, or unrelated tests. Do not dispatch agents. Preserve unrelated working-tree changes. Commit only the client files/assets/tests you change and report to `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\.superpowers\sdd\2026-08-27-end-rift-survival-architecture\task-8-report.md`.

Requirements:

- Extend `EndEventPacket` to accept `END_BOSS_PHASE` safely using the existing bridge-v2 wire mapping; unknown packet types remain rejected/ignored without breaking old clients. The packet must carry event ID, positive generation, boss UUID, binding instance, phase/model ID, and bounded transition duration using the existing fields.
- Extend `EndEventClientState` with UUID-scoped boss phase/model state and `applyBossPhase`. Reject stale generation/event/binding updates. Clear phase/model on unbind, disconnect, world change, and `clear`.
- Add `RiftGuardianModel` and `RiftGuardianModelRenderer` (or an equivalent concrete ModelPart-backed renderer) with heavy torso, broad symmetric shoulders, two arms, floating shards/horns, chest rift, and bounded idle/walk/attack/absorption/judgment transforms. One geometry must support five phase texture variants.
- Update the Enderman renderer mixin so only the bound boss UUID uses the custom model and phase texture. Ordinary Endermen and event elite Endermen keep their existing UUID-bound textures; clients without the mod keep vanilla rendering. Do not globally replace vanilla textures.
- Add five non-empty, real PNG texture assets named `rift_guardian_awakening.png`, `rift_guardian_hunter.png`, `rift_guardian_distortion.png`, `rift_guardian_absorption.png`, and `rift_guardian_catastrophe.png`. They must be 128x128 or another dimension explicitly supported by the model, valid PNGs, and not all byte-identical. Reuse the source texture-generation workflow if present; do not create conceptual screenshots.
- Update the mixin configuration if a new mixin is needed. Build the client twice with `CopiMineClient/build-client.ps1`; inspect the final JAR and assert the JAR contains the model classes, mixin config, five boss textures, plus `end_rift_enderman.png`, `end_rift_elite.png`, `end_rift_spider.png`, and `end_rift_shulker.png`.
- Add `tests/test_end_rift_client_model_contract.py` with concrete ZIP/JAR entry checks and source checks for phase protocol, UUID scoping, ModelPart geometry, and fallback. It must fail for a stale client JAR missing mob textures.
- Do not alter the server resource pack or vanilla `assets/minecraft` textures.

Run a focused pytest command and two client builds; include exact output and SHA-256 in the report. If a Minecraft API detail is uncertain, inspect the locally cached 1.21.1/Yarn merged JAR before coding and make the smallest compile-safe choice.
