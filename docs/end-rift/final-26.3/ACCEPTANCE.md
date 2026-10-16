# End Rift 26.3 — обязательная проверка и критерии готовности

## Шаг 0: состояние репозитория и безопасность
Прочесть README, GLOBAL_RULES, отдельные WAVE_01..07, BOSS, KAGUNE, BUG_LEDGER и исторические WAVES_REPAIR_LEDGER/WAVES_TASK_LEDGER. Проверить branch, dirty files, SHA, установленный JAR и pack. Никаких git clean/reset/force push, перезаписи миров/банка/инвентаря.

## Автоматика
Верифицировать параметры команд в актуальных скриптах, затем запускать:
- tests/RunEndRiftEventChecks.ps1 (или -SkipBuilds только когда свежая полная сборка уже независимо сделана), включая Java policy/persistence, Python/Node harness и git diff --check.
- tests/RunEndRiftOfficialTwoPlayerLive.ps1 с безопасным StopAfterWave 3, 5 и 7 и отдельным сценарием до босса.
- tests/RunEndRiftWave6Wave7BoundariesLive.ps1.
- tests/RunEndRiftTentacleLive.ps1 для щупалец.
- актуальные Python 3.13 pytest suites по волнaм и recovery, Java25 Gradle client tests + jar, BuildMigrationPlugins.ps1, ресурспак и asset hash guards.
- clean-checkout reproducibility exact source SHA, GitHub Actions 3 required jobs, independent/CodeRabbit/Security review релевантных diffs.

Тесты должны выявлять досрочное появление guardian до 90 секунд, восстановление всей группы через 15 секунд, неверное число визуальных щитов и повторную смерть/спавн, а также старые W7 trials, недоступность ally highlight, неполную модель портала, double rewards, старые пакеты и recovery generation leak.

## Игровой сценарий
Реальные Minecraft 26.3 Paper + Fabric CopiMineClient, matched 26.3 resourcepack, корректная bridge handshake, 2 игрока минимум, отдельно 5–6. Не путать protocol bots и настоящих игроков с рендером.

1. Проверить все настройки и активацию рун, 10 сек удержания, одноразовый старт каждой волны.
2. W1 три доставки, носитель, луч, удаление кольца, статус до/после.
3. W2 охота, видимый mark/shader, живой AI, target rotation, death/quit.
4. W3 три больших портала, заполнение захвата, последовательное закрытие, отсутствие ghost displays.
5. W4 реальные огненные шары/три отражённых попадания, 3 HP, текстура каждого состояния, нет предсказанной линии.
6. W5 чёрный туман, зелёные безопасные зоны, три цикла, fair damage, AI resume.
7. W6 настоящая сфера, пять магов, анимации, target cyan ally/violet enemy, Q/W/E/R, все пять смертей, HUD, cleanup.
8. W7 четыре УТВЕРЖДЁННЫЕ испытания, персональный Echo, подвижный Стрелок, Архимаг, Страж, 1-блочные прозрачные стены и их истинная коллизия.
9. НЕТ рун после W7, полное HP, ремонт 30% максимальной прочности, ровно 40 сек в сумме, затем boss.
10. Boss six HP phases, заземлённый ход 1–5, над Core 6, музыка/экран/бар/UV и все атаки.
11. Kagune: per-defender 90-second active-combat respawn, 5-second telegraph, one full-health spawn, 15-second guaranteed boss-vulnerability grace after all defenders die, shields equal living guardians, no revive after boss death, persisted cooldown after restart, temporary ambush with coherent grab/throw, and no click-spam damage when immune.

## Снимки и видео
Каждая модель: спереди, сзади, слева, справа, сверху, крупно; по одной сцене до/после. Ключевые анимации: начало/предупреждение/каст/попадание/промах/отход/смерть. Для W3 портал обе стороны и закрытие, W4 4 модели, W6 first-person подсветка игрока и 4 HUD иконки, W7 4 комнаты и стены, boss 6 phases, tentacles размыкание/схват/бросок. F2 screenshot + короткое видео для движущихся фаз, актуальный server/client/pack SHA, user/time/scenario в manifest.

## Крайние случаи
Игрок умирает, отменяется vanilla damage, уходит во время каста, переставляет экипировку, теряет связь, reconnect, меняет мир, рестарт по каждой волне, restart по каждой фазе босса, restart при grabbed игроке, reset/clear, unexpected objective spawn fail, chunk unload, journal rollback fail, stale tasks and client packets. Ноль оставшихся event entities/displays/hazards после cleanup. Event recovery не удаляет/не умножает реальные предметы/AR.

## Критерий готовности
Окончательная приёмка каждого этапа требует: код, воспроизводимые автоматические тесты, чистая сборка и CI, серверная проверка, **реальный клиент 26.3 и визуальные материалы**. Если клиент недоступен — VISUAL NOT VERIFIED; если есть опасный старый snapshot — RECOVERY_REQUIRED с сохранением данных, а не «само исправилось». Итоговый отчёт на русском, по разделам и с конкретными ссылками на доказательства и SHA.

## Новая обязательная матрица на основании аудита 13 октября
См. KAGUNE_90S_ACCEPTANCE.md. Написать воспроизводимый RED/GREEN тест ИСПОЛНЯЮЩЕГО tickCurrentTentacles/restoreTentacleGuardians, а не только policy class. Убить слоты по отдельности, проверить t=89.9s/90s, рано наступивший deadline во время 15с гарантированного окна, предупреждение за 5с до ФАКТИЧЕСКОГО появления, 0/1/2/5/6 щитов, restart с 30/60/89s, 10TPS и kill босса точно на deadline. Протестировать WorldGuard cancel, HIT_RECOVERY и запрещённые состояния.

Волна 3: вызывать двукратную ошибку спавна отдельного модельного слоя, убеждаться, что портал остаётся целостным или вся попытка откатывается без призраков. Босс: мигрировать 5с и 'сектора' магмы на настоящие ~20с с защищёнными блоками, steam vents, контролируемым поджогом и минимальным износом брони без секторов. Волна 7: убедиться, что named admission действительно вызывает новых четырёх противников в реальном спавнере.

Полный Minecraft F2/видео обязателен на точных JAR/pack SHA: отсутствие warning/слоя/подсветки нельзя проверять только существованием Java класса.
