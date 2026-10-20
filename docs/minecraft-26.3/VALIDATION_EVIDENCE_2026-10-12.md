# Minecraft 26.3 candidate — current validation evidence

Date: 2026-10-12 (Europe/Moscow)
Candidate branch: `codex/minecraft-26-3-migration-candidate-20261010`
PR base commit: `7f722fd5e657a3d0b7307087dd936f5451791c3e`
The detailed run data embedded in this evidence snapshot was initially collected at SHA `876e2f07d9185652f73f730d0b6b9943ce27487c`; those run descriptions are historical. The latest committed exact-SHA checkpoint at this refresh is `bf55bd3e7119a5624d2b58704be74de3d20eb847`, whose GitHub push and PR workflows each passed all three jobs. See [CI acceptance](CI_ACCEPTANCE.md) for exact run IDs and the current GitLab backup status.
Acceptance: migration candidate. Local regression results and exact-SHA GitHub runs are recorded below. A fresh native F2 screenshot proves the Minecraft 26.3 main menu rendered with the installed profile; server login, bridge handshake, server-delivered resource pack, and in-game checks remain pending, so `nativeVerified=false`.

## Scope and artifacts

- Only Minecraft 26.3 migration is active. Waves 1–7 and Rift Guardian redesign have not started.
- The EndEvent server and CopiMineClient now use bridge protocol v3. Client decoders reject stale v2 visuals and preserve v3 state when a v2 clear packet arrives.
- CopiMineNarcotics requires CopiMineClient `0.1.1+26.3` through the exact ID/version/challenge handshake. Other external gameplay or graphics mods are not required by the server.
- Current CopiMineClient artifact installed in `D:\.minecraft\versions\CopiMine\mods`: 14,827,743 bytes, SHA-512 `68be0d8cf07d38c375cf49595e60ec8f21d4897414bff308dd09976e18fbf2127acfa6de84674cbbdd1c67e6b832a4ad52dccfa5b06253ea93e92d0c1b6f559e`. The folder leaf is `CopiMine`, while the profile JSON ID is `Copimine`; the installer now reads the stable JSON ID for receipt validation and permits a case-only directory name difference. The profile receipt records resource-pack SHA-256 `d1178b427b926aee3c2e49f9a72aca7156f5e99917766e63248fb6226d4977dd`.
- EndEvent v3 candidate: 1,349,199 bytes, SHA-256 `c77873078f3eb61bd3f3c5059aec0f3f89b04c7f5b2044c972ecf58a47c113dd`.
- CopiMineNarcotics candidate after fixing the namespaced-command bypass: 225,221 bytes, SHA-256 `d53bafb4ebe9d65b8a0cfa67a82d8acf48ac0328639ceadfd9854dbad5f23a97`, SHA-512 `29dafac22cb604da0e738f8e5b2551a2bf56099ac3d944547c7f581b751f7b2c264b92ee5dce8eddfc4e4808047d552c0160549bdcd59afd3526f2eef5f1d8e3`. The pinned build completed and `install_migration_server_plugins.py --stage-only` verified all 31 locked candidates.
- `install_migration_server_plugins.py --stage-only` verified all 31 locked plugin candidates. The unauthenticated loopback server installer installed 29 entries with AuthMe/AuthEffects intentionally omitted for local testing.
- The installed profile at `D:\.minecraft\versions\Copimine` passed `InstallMigrationProfile.ps1 -ValidateOnly` without mutation. Its updated receipt records 12 external mods and the resource pack.

## Automated checks

| Check | Result | Limits / evidence |
| --- | --- | --- |
| Full Python 3.13 suite after migration fixes, `py -3.13 -m pytest -q` | `1555 passed, 7 skipped, 88 warnings` in 519.93 s | Fresh output: [pytest transcript](evidence/pytest-full-2026-10-12-final.txt). This command did not print skip reasons. The PostgreSQL contract module was separately run against an isolated DB: `27 passed`. |
| Focused protocol/lock regression tests | `4 passed` | Covers server/client protocol v3 and all 31 candidate identities/locks. |
| Installed profile case-only rename regression | `2 passed` | Windows test installs the fixture, renames profile directory `Copimine` to `CopiMine`, and confirms `-ValidateOnly` accepts the unchanged JSON ID and receipt. |
| Pending-mount event registration regression | Red/green verified; final test `1 passed` | Removing the `@EventHandler` annotation made the regression fail for the intended missing-registration message; restoring the annotation passed. |
| Migration-scope PowerShell validators, `RunCopiMineValidators.ps1 -SkipReleaseArtifacts` | `636 passed, 0 failed, 25 skipped` | Fresh run after the legacy release guard and its regression validator were added. Each validator ran in a fresh PowerShell process with its exit code checked. The 25 release-artifact validators were explicitly skipped by this migration-scoped command. [Captured output](evidence/powershell-validators-2026-10-12-final.txt). |
| All PowerShell validators, without exclusions | `649 passed, 11 failed, 0 skipped` of 660 | The 11 failures are legacy release-tree checks that require absent `minecraft/server/plugins` JARs/configuration, permission and command artifacts in this isolated migration worktree. They are `ValidateCopiMineAllPluginsAudit`, `ValidateCopiMineArtifactsStructure`, `ValidateCopiMineArtifactsV4ShopAndBlackMarket`, `ValidateCopiMineAuditLeastPrivilegeAndWorldSafety`, `ValidateCopiMineElectionRolesAndCandidateBooks`, `ValidateCopiMineNarcoticsPlugin`, `ValidateCopiMineNarcoticsTextureDefaultVanilla`, `ValidateCopiMinePermissions`, `ValidateCopiMinePluginCommands`, `ValidateCopiMineReleaseCleanlinessAndGuide`, and `ValidateCopiMineSinglePluginAndWorkflows`. The migration-scope 635 checks all passed; the full run was not green. [Captured output](evidence/powershell-all-validators-2026-10-11.txt). |
| CopiMineClient Gradle `test jar --rerun-tasks`, Java 25 | `BUILD SUCCESSFUL`; 31 suites, 127 tests, no failures/errors/skips | Fresh output: [Gradle transcript](evidence/client-gradle-tests-2026-10-11.txt). JUnit XML totals were read from the build reports after this run. Generated and installed JARs match `profile.lock.json`: 14,827,743 bytes, SHA-512 `68be0d8cf07d38c375cf49595e60ec8f21d4897414bff308dd09976e18fbf2127acfa6de84674cbbdd1c67e6b832a4ad52dccfa5b06253ea93e92d0c1b6f559e`. |
| First-party 26.3 Paper plugin build | Passed after bridge guard changes | CopiMineNarcotics build hash and size are recorded above. |
| Local PostgreSQL integration contracts, `tests/test_minecraft_26_3_custom_plugin_database_contracts.py` | `27 passed` | Re-run with the isolated loopback database at `127.0.0.1:55434`; tests use disposable schemas. [Captured output](evidence/postgres-contracts-2026-10-11.txt). Two corresponding cases still appear as skips in the default full-suite run because no DSN is exported by default. No production database was changed.
| Installed profile `-ValidateOnly` | Passed without mutation | Rechecked the actual profile at `D:\.minecraft\versions\CopiMine`; validates CopiMineClient 0.1.1+26.3, mixin file, migration receipt, and locked mods. [Captured output](evidence/client-profile-validate-only-2026-10-12.txt). |
| CopiMineClient installed-artifact audit | `6 passed` | Re-run with `COPIMINE_PROFILE_CLIENT_JAR` set to the installed CopiMineClient JAR. |
| Live server plugin inventory | `29 expected / 29 active / 29 in receipt`, exact SHA-256/SHA-512 match | Read-only `validate_active_plugin_inventory` verified the active loopback runtime against the unauthenticated-test lock selection. [Captured result](evidence/plugin-inventory-runtime-2026-10-11.txt). The installer’s `--validate-only` was blocked by its lifecycle mutex while the server remained available; the read-only verifier completed without taking the mutex. |
| Paper startup and database readiness | Server reached `Done (28.562s)`; economy, election, artifacts and admin PostgreSQL readiness appeared in logs | [Captured startup excerpt](evidence/server-runtime-2026-10-11.txt). ProtocolLib explicitly warns upstream 26.3 has not been tested; this is an unresolved compatibility warning. Windows OSHI/Perflib diagnostics also appear during host telemetry initialization. |
| Current local status handshake | Paper returned `Paper 26.3`, protocol `777`, online `0/1` on `127.0.0.1:25568` | [Captured status result](evidence/server-status-2026-10-11.txt). This is a Minecraft status probe, not a player login or bridge negotiation. |

### ProtocolLib 26.3 provenance recheck

On 2026-10-12 the lock entry was compared with the official ProtocolLib `dev-build` release and upstream history. The locked artifact is `dev-d89a5de`, source commit `d89a5deb3eb625b16846da57fbbb04bfdd0d031d`, size `10,567,022`, SHA-256 `ca3ec620315ff92bee2d6d09af297d77362e99b4791b163135b745262df75ecc`, SHA-512 `6372787a9d8a63425184f60b06f082ea6ab1bb7415c2e7fd6a38c39f7d808ed8b44305b299bb5b4ecc3c432bb04987d0c5c26b9e776590c93961da677476e604`. GitHub release metadata reports the same SHA-256 and byte size for `ProtocolLib.jar`.

The upstream release changelog includes the 26.3 play-packet mapping fix (`583353e`), the 26.3 item mirror and level-chunk fixes (`3c616ca`, `1ea47d6`), and the Paper plugin classloader fix (`d89a5de`). The exact pinned build includes those commits. The focused local regression covering the lock, runtime jar SHA-256, and 26.3 packet entries passed; the paired remaining-server-plugin lock check also passed (`2 passed`). See the [official dev-build release](https://github.com/dmulloy2/ProtocolLib/releases/tag/dev-build), [26.3 item-mirror fix](https://github.com/dmulloy2/ProtocolLib/commit/3c616ca), and [pinned source commit](https://github.com/dmulloy2/ProtocolLib/commit/d89a5deb3eb625b16846da57fbbb04bfdd0d031d).

The upstream `MAXIMUM_MINECRAFT_VERSION` still reports `26.2`; consequently its runtime warning on 26.3 is accurate. Packet mappings and merged code fixes establish the provenance of this candidate, but do not prove a player joined successfully. Keep `compatibilityStatus=requires-runtime-validation` until a real 26.3 client session is recorded.

The 88 Python warnings include existing FastAPI/Pillow deprecations. Exact transcripts are the final post-rebuild runs and replace earlier stale captures.

Transcript SHA-256: `pytest-full-2026-10-12-final.txt` — `F3FFDF73428CCC2B58B0C156EB406D49668315A24550EABB3FD9BD8408437DE8`; `powershell-validators-2026-10-12-final.txt` — `7EB8DA7C0ADAE7372D0AB451AD0F3B6FC18B6CD58C529639D0E950E6A4261259`; `client-profile-validate-only-2026-10-12.txt` — `F563C54271FF1FCEBC9A50C8823AD71DF5B8BE4CC2FFFAC36A604E8D9AC96CD9`; `client-gradle-tests-2026-10-11.txt` — `CD6BB6C226DAC92F01C046D6798E83BC793096A273BF4FC98FBB47BEA12BFA1F`; `pytest-full-2026-10-11.txt` — `70DCD63E08B48A1CF942549A857BE1EB86BBFC5C396D35378F5563E66A0F15C6`; `postgres-contracts-2026-10-11.txt` — `1987C0EA1759A6082D159F0C075A18E3D7FDAAFCFB4B39A518D9404A330004EF`; `plugin-inventory-runtime-2026-10-11.txt` — `03D30EAC41A0FF52D4EC042D0A64978A5ED0A293AC66C8482E00A494E44F9CC2`; `server-runtime-2026-10-11.txt` — `7928469FCD3CDCDE889838AA5F6FF517415A4914D1D3744C58FB8B6FA149FA32`; `server-status-2026-10-11.txt` — `1B1005B77ED143A5488828001ADC9F543EAE9B3F32B88D122055CDF546E8A501`; `powershell-all-validators-2026-10-11.txt` — `0F51CA40F9C6BEE4D906E09A7F92677B1570E20CD2D56EA7BC35882EC67BFD4F`. The unexcluded legacy run had 11 failures from absent release-tree fixtures/artifacts; it is not a green result. The new Python and PowerShell transcripts have workstation/user-root absolute paths redacted and trailing whitespace removed; test commands, warnings, skips, results, and line endings are unchanged.

## Current local runtime

The Paper 26.3 process currently listens on `127.0.0.1:25568` (PID 34632). This confirms only a loopback listener; no player has joined and live bridge negotiation is not proven.

AuthMe and AuthEffects are disabled only in the loopback test runtime. AuthMe 6.0.1 is a migration candidate, not an accepted production authentication replacement. `nativeVerified=false`.

## Reviews and remote gates

- Earlier CodeRabbit findings were fixed, including protocol wording, pending-handshake vehicle-mount handling, validator child-exit reporting, migration-script state, client-bridge config validation, and stale artifact/signing docs. Later review found legacy packaging bypasses in YAML parsing; the release guard now uses PyYAML SafeLoader and fails closed before creating release output. CodeRabbit reported `0 issues` on the final migration guard changes and the separate CI dependency pin; independent local review reported no actionable findings. [Review evidence](evidence/review-results-2026-10-12.md).
- Independent local review of the final SafeLoader guard and regressions reported no actionable findings. It covered escaped keys, anchors/merge aliases, duplicate keys, comments, continuations, legacy version values and the no-output-before-guard requirement. Earlier independent review confirmed the shared command-policy class and tests, client JAR/lock match, and mount event registration. A modified client can still forge the public HELLO response: current enforcement rejects ordinary vanilla/wrong-version clients but is not proof of the installed JAR. That limitation is documented in `AUTH_AND_CLIENT_MOD_ENFORCEMENT.md`.
- Historical GitHub CI snapshot at `876e2f07`: draft [PR #5](https://github.com/IliaZav/copimine/pull/5) was open on that exact head. Push run [#887](https://github.com/IliaZav/copimine/actions/runs/38189806517) and PR run [#888](https://github.com/IliaZav/copimine/actions/runs/38189810311) had `static-and-contract` and `migration-postgres-contract` pass while both `java-plugins` jobs were still running at evidence-capture time. Their final results and later exact-SHA runs are listed in [CI acceptance](CI_ACCEPTANCE.md). Native-acceptance workflow steps were skipped because the server login/handshake gate had not been satisfied.
- Historical GitLab backup pipelines at `876e2f07`: push [#2936836952](https://gitlab.com/copimine-group/Copimine-project/-/pipelines/2936836952) and MR [#2936836994](https://gitlab.com/copimine-group/Copimine-project/-/pipelines/2936836994) failed before starting jobs; the API returned zero jobs. This was not a failed test assertion. Current backup-pipeline status is recorded in [CI acceptance](CI_ACCEPTANCE.md).
- Codex Security scan `88a0db78-8b3a-4a20-99f5-1c62e80502bc` completed 25/25 semantic review items with zero reportable findings for source range `00f2c6927c9db3c58599fc77e257c6d1ea623061..67449a3088ec5d951af584f213c1b85eae886662`. It predates the final CI-only PyYAML pin, which was separately reviewed. The client-handshake spoof candidate was judged not applicable to server-security boundaries because it grants no extra privileges; the known compatibility limitation remains disclosed.

## Native client smoke check (partial)

- Launched the installed profile at `D:\.minecraft\versions\CopiMine` with its migration candidate JAR. The fresh client log reports Minecraft `26.3`, Fabric Loader `0.19.5`, and `79 mods`, including `copimineclient 0.1.1+26.3`. Resource-manager startup includes `CopiMineResourcePack-26.3.zip` and the other selected client-side packs.
- Minecraft itself saved [native-client-main-menu-2026-10-12.png](evidence/native-client-main-menu-2026-10-12.png) at 2026-10-12 04:19:57 MSK. Its SHA-256 is `7e710b323d7f5719dc5f8558fee7695e56ea92f7da2129d339aabf406d447260`. The screenshot visibly shows the modified Minecraft 26.3 main menu. Computer Use's Windows.Graphics.Capture image of the OpenGL surface is black, so the inspected F2 framebuffer screenshot is retained as the rendering evidence.
- The smoke launch used a dummy offline test identity with no real credentials. Mojang user-properties and Realms requests returned HTTP 401/authentication errors, as expected for the fabricated token; the client still rendered the main menu. These messages do not prove or disprove a Paper login.
- Opening Multiplayer surfaced a Windows Firewall security dialog asking whether OpenJDK may communicate on public/private networks. Computer Use rules forbid the agent from acting on Windows security permission prompts. No permission was granted; further client interaction is blocked until the user dismisses the dialog manually. The client did not reach the saved server entry, and no player joined the loopback server.
- `nativeVerified=false`. Not yet verified: multiplayer connection, required-client handshake, vanilla or wrong-version rejection, server resource-pack delivery/rendering, active gameplay, plugin behavior with a real player, and restart/recovery through Minecraft.
- Iris shader processing was temporarily disabled (`enableShaders=false`) for this diagnostic launch; the original `iris.properties` was backed up with SHA-256 `3dde0d0e1128e47260421b9cd72fd8db930d7e0560431423bf22b727ebbe43df2`. Restore the original after the client is closed. This machine-profile change is not part of the migration commit.
- Waves 1–7 and Rift Guardian remain deferred until migration acceptance passes.

## Повторно подтверждено 2026-10-12 05:05 MSK — SHA d12b526

Команды gh в текущей среде отсутствуют; статусы Actions получены через GitHub connector непосредственно для run/job IDs, а ветки — через git ls-remote.

| Контроль | Результат | Граница доказательства |
| --- | --- | --- |
| GitHub push Actions для d12b52638c90b6a5479a9508547165604ad1f1c9 | Run [38191785993](https://github.com/IliaZav/copimine/actions/runs/38191785993): static-and-contract, migration-postgres-contract, java-plugins — success | Все три job прошли на точном SHA; native-evidence steps пропущены условием workflow, так как в checkout отсутствовали файлы artifacts/minecraft-26.3/native-acceptance/. Отдельно, player login/handshake остаются непроверенными |
| GitHub PR Actions для того же SHA | Run [38191788666](https://github.com/IliaZav/copimine/actions/runs/38191788666): те же три jobs — success | Подтверждает PR-trigger CI и shared Java regression gate, не реальную Minecraft multiplayer session |
| Git remote refs | origin/codex/minecraft-26-3-migration-candidate-20261010 и gitlab/... равны d12b52638c90b6a5479a9508547165604ad1f1c9 | Синхронизация branch refs, не GitLab CI |
| Локальный Paper listener | 127.0.0.1:25568, PID 34632; процесс Paper 26.3 build 169 | Listener и текущие EndEvent idle diagnostics; нет player ping/session |
| Установленный runtime inventory | Последняя read-only проверка: 29/29 выбранных offline-test plugin JAR совпали с lock и receipt | AuthMe/AuthEffects исключены в тестовой конфигурации; installer validate-only не выполнялся, пока mutex удерживался активным сервером |
| Native connection and bridge | Не выполнено; nativeVerified=false | Windows Firewall system prompt и недоступный Computer Use bridge блокируют безопасное управление Minecraft GUI |

GitLab backup pipelines для этого candidate завершились без jobs. Они не дают результата тестов и не являются доказательством отказа конкретного теста. Историю предыдущих exact-SHA результатов и их пределы см. в разделах выше.


## Повторный полный старт Paper 26.3 — 2026-10-12, база SHA 892b6980e88d40439f9364d9f951220ecd53c50c

### Проверяемые результаты

| Проверка | Результат |
| --- | --- |
| Установленный Fabric-профиль | InstallMigrationProfile.ps1 -ValidateOnly подтвердил CopiMineClient 0.1.1+26.3 и copimineclient-26.3.mixins.json |
| Focused migration acceptance tests | 148 passed, 2 skipped за 83.26s на Python 3.14.3 с зафиксированными cryptography/rfc8785/pytest; см. [native-acceptance-tests-2026-10-12.txt](evidence/native-acceptance-tests-2026-10-12.txt) |
| Основной тестовый сервер | 29/29 выбранных по lock JAR совпали по SHA-256 и SHA-512; listener только 127.0.0.1:25568 |
| Отдельный полный набор | Один зафиксированный после старта inventory snapshot: 31/31 JAR имеют SHA-256/SHA-512, совпадающие с lock; AuthMe и AuthEffects входят в этот набор |
| Старт fullset #1 | Paper 26.3 build 169: Done=1, Enabling=31, plugin-load failures=0, SEVERE=0; 1 nonfatal OSHI/Perflib ERROR |
| Старт fullset #2 | Done=1, Enabling=31, plugin-load failures=0, SEVERE=0; 1 nonfatal OSHI/Perflib ERROR; AuthMe 6.0.1 build n.2770 и AuthEffects включились |
| PacketEvents update check | Во втором полном старте асинхронная проверка версии получила HTTP 403 от GitHub API и вывела WARN/parse exception; блок mappings V_26_3 и загрузка плагинов продолжились. Причина 403 из лога не установлена; проверить в последующем плагинном аудите. |
| Остановка и порты | Оба fullset процесса остановлены; 25569 и 24457 закрыты; основной тестовый сервер остался на 25568 |
| Native connection | Не состоялся; клиентский log не содержит подключения/join. nativeVerified=false |

### Логи и пределы

Один read-only inventory manifest фиксирует plugin name, одно фактическое поле `filename`, а также expected/actual SHA-256 и SHA-512: 29/29 точных совпадений для основного loopback test runtime и 31/31 для отдельного полного набора после старта; SHA-256 manifest — `0DEBCFFC901544724BE61288B7928AD00F927523F552F33023E895F0E3B0DB3D`. В обоих стартах `Done=1`, `Enabling=31`, plugin-load failures=0, `SEVERE=0`; каждый лог содержит одну нефатальную OSHI/Perflib `ERROR` о Windows performance counters. Во втором старте также зафиксирован сбой только асинхронной проверки обновлений PacketEvents: GitHub API ответил HTTP 403; точная причина отказа не установлена. AuthMe печатает benign `No MaxMind credentials found`; секретных значений эта строка не содержит.

Tracked sanitized копии: [fullset-start-1-2026-10-12-redacted.log](evidence/fullset-start-1-2026-10-12-redacted.log) и [fullset-start-2-2026-10-12-redacted.log](evidence/fullset-start-2-2026-10-12-redacted.log). В них удалены строки с настройками auth/database и UUID, а локальный путь заменён нейтральным маркером `<TEST_RUNTIME>`. Оригиналы остаются локально в ACL-защищённой папке %LOCALAPPDATA%\CopiMine\minecraft-26.3\fullset-test-20261012-01\acceptance-evidence. Сканирование tracked-копий не обнаружило значений секретов, UUID, имени пользователя или исходных локальных путей; безопасная строка AuthMe «No MaxMind credentials found» оставлена. Manifest содержит поле `filename` для каждого JAR и ожидаемые/фактические SHA-256/SHA-512 для обоих наборов: [plugin-inventory-hashes-2026-10-12.json](evidence/plugin-inventory-hashes-2026-10-12.json), SHA-256 `0DEBCFFC901544724BE61288B7928AD00F927523F552F33023E895F0E3B0DB3D`. Хэши tracked log #1 и #2: `A7567A2AD39F49F5A087DA6B794B6C4C75C581E2386FD6F04AF54FFDD69AD9B6` и `5FCB60E0F715BAA81273C93786E77790FC1898352ECC612FA4E30AA4C3F67810`.

Зелёный старт не проверяет вход, команды, экономику, permissions, auth flow, PacketEvents/ProtocolLib с живым клиентом, визуальную доставку resource pack или корректность восстановления после перезапуска. AuthMe GeoIP остался без локальной GeoIP базы/ключа, а upstream ProtocolLib всё ещё обозначает 26.2 как максимум протестированной версии. Эти пункты сохраняют статус runtime/native validation.
