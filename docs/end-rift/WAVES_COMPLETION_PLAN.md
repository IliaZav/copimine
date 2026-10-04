# End Rift Waves 1-7 Completion Plan

Baseline: branch `codex/end-rift-event`, HEAD `b8df913666ade6d1e7dbff441c1b6b9f24fc2291`. Follow prompt 01's bugfix checkpoints first, then prompt 02's wave-completion criteria. Scope remains Waves 1-7; no Guardian/Kagune redesign.

## Work Order

### Current execution — 2026-10-03

The complete v2 archive (SHA-256 `687113f3fe89d681a58623c263a64cdda94544ab6b4aa20b3154c62fc6d3a285`) and all seven included images were read before this pass. Local/remote HEAD is still `b8df913666ade6d1e7dbff441c1b6b9f24fc2291`; preexisting dirty source files were preserved in an external patch and source ZIP before edits. Earlier results below are historical evidence only.

- [x] Re-run the existing automated gate as a baseline (`-SkipBuilds`, exit 0); do not treat existing artifact presence as a new build.
- [x] Reproduce U16: two new `PrisonerHudControllerTest` cases fail before the fix; retain the terminated prison identity and reject unknown-session clears; focused test turns green.
- [ ] U07/U09/U11/U16: move the complete numbered-wave initialization behind `initializeWaveGameplay`; official and sandbox adapters supply only session context. Freeze the sandbox roster and refuse a sandbox over an active official encounter. Compare actual starts in isolated Paper.
- [ ] User clarification: expose `test wave 6 capture` and `test wave 6 combat`. Capture retains normal prisoner behavior; combat skips capture while the same major-spell scheduler and five-caster completion run. Provide console controls to kill one real caster through its actual death path and a second participant for ally support testing. No new solo escape mechanic.
- [ ] U13/U14/U16: test native-reach-independent camera selection, vanilla HUD clearance and terminal packet handling, then inspect the actual client.
- [ ] Rebuild exact artifacts and run the full registered gate in an isolated validation checkout; repeat live waves, interruptions and native captures. Record hashes and actual source patch identity, since HEAD alone does not identify dirty code.

No extra implementation approval is required: the user explicitly requested sequential execution of both v2 prompts and the two sandbox variants in this chat.

1. **Baseline and ownership:** preserve existing user changes; map entry adapters, `EncounterResourceScope`, objective state, tasks, owned entities, packets, block journals, client handlers and the exact local server/client artifact paths. Reproduce the CI asset test failure without editing supplied assets.
2. **Shared wave start and cleanup:** add red regressions for the test/official mechanic split, Wave 7 generic-mob fallback, Wave 3 parallel sandbox portals, ignored objective-start failure, and stale task/cleanup behavior. Refactor to one mechanic initializer/runner with explicit mode boundaries for roster, rewards and persistence. Remove the reachable obsolete Wave Front and ensure old callbacks cannot render after any start/reset/abort.
3. **Combat and hit presentation:** prove eligibility, target assignment, path requests and per-role attack/action state. Fix sandbox participation without weakening official attempt membership. Make accepted authoritative damage produce exactly one visible hurt response while preserving single health/progression/death accounting and rejected-hit protections.
4. **Wave 1 and 2:** verify three distinct carrier/charge/delivery cycles, stable Core-to-carrier or holder beam, bounded carrier highlight, pickup inventory edge cases, restart/replacement behavior; verify all three Wave 2 marks, aggro switching and effect removal. Remove obsolete Wave 1 sector/arena pulse mechanics from active paths.
5. **Wave 3 and 4:** verify one active portal at a time in both adapters, hold/decay/collapse, real closure and client view; then verify Wave 4 visual bindings for every health/damage state, weapon firing and reflection against real targets.
6. **Wave 5:** make safe-zone creation a verifiable prerequisite for the fog phase. Exercise journal prepare/apply/restore failures, player outside/inside safe zones, black fog presentation, combat AI restore, three complete cycles, death/quit/restart and no stale effects or blocks.
7. **Wave 6:** audit and implement the required sphere visibility/placement, caster facing and animated channel/cast lifecycle, guards with bounded HP and telegraphed counterable abilities, ordinary pressure roster independent of caster progression, single major-spell scheduler, prisoner/observer separation, Q/W/E/R input (latest user instruction), client HUD/effect clearing, five-death progression and restart safety.
8. **Wave 7:** retain the current feet-level user placement fix; prove four-sided room geometry and actual movement/collision containment. Run one independent trial per room with the four required roles and verify cross-room attacks, player departure/death and completion/transition recovery.
9. **Cross-wave validation:** natural W1-W7 progression without skip-completion, then the same waves via playable sandbox entry; abort at hazardous mid-wave states; death/quit/reconnect/restart; repeated runs; stale entity/task/packet/effect audits. Run focused tests, all repository checks, builds, resource-pack validation and `git diff --check`.
10. **Native evidence and report:** rebuild/install exact server plugin, client mod and pack; hash/fingerprint runtime-loaded files. Capture the required participant and observer Minecraft evidence with wave/stage, command, session, roster, game mode, camera, position, GUI scale and SHA-256. Report automated evidence separately from actual-client evidence and list every blocked item plainly.

## Acceptance Gates

- A defect has a reproduced or source-traced cause, a regression test that fails before the fix, and relevant post-fix coverage.
- Official and playable sandbox paths share wave mechanics, objective sequencing, composition roles, damage, timing and presentation; only roster/storage/reward context differs.
- No supplied source asset or validation is weakened to make a test pass.
- Builds/tests do not count as proof of visual/gameplay behavior. Wave completion requires the real-client evidence in the supplied prompts.
- Preserve all unrelated dirty and untracked user work. The user's follow-up on 2026-10-04 explicitly authorizes committing and pushing the Waves changes to `IliaZav/copimine`, branch `codex/end-rift-event`. Live installation and Minecraft launch remain deferred by the user's current instruction.

## Known Initial Constraints

- User's Wave 7 root-anchor patch and corresponding tests are already dirty and must remain intact.
- GitHub Actions run `36694908681` fails in client supplied-asset hash checks before wave contract tests; exact packaged-resource cause is unknown.
- Current CUA inventory reports no native applications and there is no running Minecraft process. Native verification must remain explicitly unverified unless a real client becomes controllable.

## Checkpoint — 2026-10-01

The code/regression pass and isolated End Rift server, client, and resource-pack builds are complete. The broad local gate passes with `-SkipBuilds`, which intentionally avoids replacing local server/plugin build artifacts. This is not the acceptance endpoint: the real-client gates in steps 4-10 remain outstanding because the current CUA inventory has no native applications.

Resume in this order:

1. Obtain a controllable Minecraft client and rebuild/install the exact reviewed artifacts in the isolated local runtime. Record their hashes before starting the server.
2. Resolve the user's solo Wave 6 choice. Until then, do not claim solo prisoner acceptance or invent an alternate escape mechanic.
3. Run and capture the required participant and observer checks for Waves 1-7, including repeated runs, cleanup, disconnect/death/restart and physical Wave 7 collision. Keep actual client evidence separate from source, tests, and build evidence.
4. Re-run `git diff --check` and the complete local gate, then update `WAVES_REPAIR_LEDGER.md` with each screenshot/evidence path and mark only directly observed criteria as verified.

## Текущий порядок — 2026-10-04

По последнему прямому указанию пользователя Minecraft пока не запускать. Код проверяется в порядке W6 → W7 → W1–7; runtime install, игровые прогоны и фотографии отложены. Исторические ограничения/неотвеченные вопросы выше не являются текущим запросом разрешения: режимы capture/combat и Q/W/E/R уже выбраны пользователем.

- [x] W6: общий official/test gameplay initializer, отдельные capture/combat adapters; actual prisoner command placement и owned high-anchor sender/audience; raised/bobbing sphere, внутренние peripheral arcs, palm beams, отдельные щиты собственных guards, guard post/interception/role abilities, накопление major spells, stronger gravity/chains, textured world/screen/audio feedback, captured-only Q/W/E/R и hover outline.
- [x] W6: executable regressions для captive exclusion в обычном AI и независимом miniboss path, включая поздний capture перед execute/impact; free/released targets и другие волны сохраняют прежние правила. Guard warning обновляется на каждом пяти-тактовом шаге до resolve.
- [x] W7: полная детерминированная геометрия walls, реальные journaled collision blocks, coverage check до мутации, trials из обоих playable adapters, восстановление/open boundaries и once-only completion feedback. Существующее полное стекло принимается по collision shape; двери, slabs и незапланированный воздух не принимаются. Небезопасные floor/gate skips не скрывают дыр в required coverage.
- [x] W1–5: current carrier deadlines/beam/readability без legacy Wave Front; shared combat/accepted-hit feedback; sequential portals с завершением/closing; W4 HP-band/firing/reflection paths; безопасная fog construction, AI pause/restore, три цикла и once-only warning/recovery feedback.
- [x] Общие границы: cleanup retry сохраняет живые entities, независимые recovery rollback callbacks, reentrant task cancellation, protected receipt persistence, terminal packet fencing, bounded fog identities, сохранение prisoner cooldown при reconnect в ту же session identity, inspection adapters без official overwrite/persistence.
- [x] Полный заключительный registered gate после последнего abort исправления: 427 Python tests, 124 pure Java suites, 8 persistence/recovery suites, AuthMe mode и Node harness, validators, `git diff --check`; свежие server/client builds и artifact receipt. Заключительный server/client CodeRabbit: 0 issues в каждой проверке. Новая сборка не установлена в живой runtime.
- [ ] На следующем разрешённом пользователем этапе установить точные reviewed server/client/pack hashes в isolated runtime, выполнить official и test W1–7 с полной interrupt/repeat/death/quit/restart matrix и native screenshots. Текущие unit/adapter/source tests не закрывают этот пункт.
- [ ] Завершить managed Codex Security discovery на актуальном immutable snapshot. Прежний scan не покрывает последние файлы; дополнительный current-source checkpoint не заменяет весь scan.

Конкретные результаты, RED/GREEN журналы и U01–U17 находятся в `WAVES_REPAIR_LEDGER.md`. Ни один визуальный/игровой критерий не объявляется проверенным только по сборке.

## Повторная проверка по новым F2 — 2026-10-04

- [x] Просмотрены все 28 новых F2 за 14:18–14:21 и сопоставлены с установленными server/client/pack hashes. Они показывают прежнюю установленную версию, не новый reviewed build. Установка отложена по прямому указанию пользователя.
- [x] Исполняемые RED/GREEN регрессии ИИ guards: bounded target leash от собственного caster, удержание начатого боя до 6 блоков, возвращение без ежетактовой отмены пути, немедленная отмена chase при возврате и повторная LOS-проверка при resolve заклинания.
- [x] Воспроизведена порча байтов поставленных JSON при Windows Git checkout с `core.autocrlf=true`. Исправлены `.gitattributes`, исходные модели/анимации и hash assertions сохранены.
- [x] Повторный registered gate: 433 Python tests, 124 pure Java suites и 8 persistence/recovery suites; полный локальный pytest: 850 passed, 1 skipped; Fabric: 216 tests без failures/errors/skips. Server, client и resource pack собраны заново.
- [x] Чистый изолированный checkout: fresh server/client/pack builds, byte parity client/pack и server classes, registered gate 434 Python/124 pure Java/8 persistence suites; portability fixes без ослабления checks. Последний полный pytest рабочего каталога: 851 passed, 1 skipped.
- [x] Сформировать коммит проверенного кода `ebd8760a5ba157fdc85ffd7113b808443cb2e15d` и отдельный checkpoint отчёта для GitHub. Source identity, exact artifact hashes и clean pytest 842 passed/1 skipped записаны в `WAVES_CODE_VERIFICATION_20261004.json`. Сторонние boss/native harness изменения сохранены вне коммитов. Remote CI имеет отдельный результат после push.
- [ ] Выполнить оставшийся нативный acceptance matrix после разрешённого пользователем этапа установки/запуска. Предыдущие F2, unit tests и CodeRabbit этот пункт не закрывают.
