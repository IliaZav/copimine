# End Rift survival fixes — локальный runtime evidence

Дата среза: 28 августа 2026 года.

Ветка: `codex/end-rift-event`.

## Граница безопасности

Проверялся только изолированный Paper/Purpur в
`local-runtime/end-rift-server`, локальная PostgreSQL на `127.0.0.1:55433`,
локальный resource-pack HTTP и тестовые offline-клиенты. Боевой сервер,
боевой мир, боевые playerdata/БД, launcher и production site не запускались и
не изменялись.

## Свежие проверки

| Область | Наблюдаемое доказательство | Статус |
|---|---|---|
| Countdown и первая волна | Два Mineflayer-клиента на разных рунах; `COUNTDOWN`, `pads=2/2`, затем `WAVE_1`, `event-mobs=10`, `OBJECTIVE_DIAGNOSTICS trackedMobs=10 liveMobs=10` | GREEN |
| Server performance | `PERF_DIAGNOSTICS tps=19.77/19.68/19.68/19.67 avgTickMs=2.31 online=2 owned=14`; после чистого boot `tps=19.74/19.75/19.86/19.95 avgTickMs=0.57` | GREEN локального сервера |
| Boss 2500 HP | Свежий `/cmend boss spawn`: `hp=2500/2500 physical=2048/2048`; Paper physical cap не используется как логический максимум | GREEN |
| Boss после Absorption | После `damage 1500`: `1000/2500`; cast state завершился `damageable=true`; следующий удар зафиксировал `1250/2500`, последующий — `1150/2500` | GREEN |
| Boss movement/leash | Paper log содержит `BOSS_MOVE_TELEPORT` с `target-too-far`, `arena-too-wide`, `leash`; свежая позиция оставалась в пределах Core radius 20 и вертикали ±3 | GREEN |
| Arena Inferno | Во время заклинания `inferno=987 realFire=987 journal=APPLIED`; после 5 секунд `inferno=0 realFire=0 journal=RESTORED entries=987`, log `restored=987 skipped=0` | GREEN |
| Five waves/objectives | Config and domain contracts cover `AWAKENING`, `HUNT`, `PORTALS`, `TOWER_DEFENSE`, `RIFT_STORM`; wave 1 live smoke passed | GREEN static + Wave 1 live |
| Loot/title/chat | Durable wave reward policy, Core drop location, owner window, full-screen title and chat paths covered by source contracts and build gate | `NOT FULLY VERIFIED`: no complete five-wave reward pickup run in this slice |
| Mob/client textures | Client JAR and resource pack contain the real PNG/model entries; pack negotiation returned SHA-1 `aaa2605b2b0f2751f5eab5258173d5c6699d45be`; texture/asset contracts pass | `NOT FULLY VERIFIED`: current slice had no fresh rendered modded-client screenshot |
| Friend ping | Local server and loopback clients are healthy, but host test to `26.247.105.218:25566` returned `TcpTestSucceeded=False`, source `10.8.1.32`, interface `AmneziaVPN`; Radmin peer route is unreachable | `NOT FULLY VERIFIED`; network/VPN issue, not a Paper tick spike |

## Build gate

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\\tests\\RunEndRiftEventChecks.ps1
```

Result: `177 passed, 14 warnings`, all listed pure Java suites `OK`, plugin
builds, Fabric client build and resource-pack build successful. Warnings are
the existing Bukkit deprecation/Pillow warnings; no test failed.

Current SHA-256 values:

| Artifact | SHA-256 |
|---|---|
| End Event JAR and local installed copy | `68270CDB50B4AC28CF520BE47C71CCB6A911FB5CF17D00E345FF1955B3A43461` |
| Fabric client JAR and `thirdparty/client-mods` copy | `B8621FE61F0DC0DA6F3B58FCE5F62C41B1ABEA8E1EEF02E2AC80B218D62E5E59` |
| Resource pack ZIP | `9D65787D380791D0E873B29F6E126E81A69D17D5BAD4B66D5AB23DB5738974AB` |

Resource pack SHA-1: `aaa2605b2b0f2751f5eab5258173d5c6699d45be`.

## Local connection

- Game: `26.29.99.140:25566`.
- Port: `25566`.
- Resource pack: `http://26.29.99.140:8092/CopiMineResourcePack.zip`.
- Website: `http://127.0.0.1:8093/`.

The final local runner left the event in `COLLECTING`, Core at
`CopiMine 8,68,-39`, `runes=2/2`, `event-mobs=0`, `boss=none`, and the hazard
journal restored.
