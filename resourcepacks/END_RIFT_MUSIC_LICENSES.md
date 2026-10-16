# Музыка End Rift Event

В pack входят только инструментальные треки без вокала. Имена ниже совпадают с
каноническими ключами `music.phase` в `copimine-end-event/config.yml`; старые
агрегированные треки в текущий pack не входят.

Исходные музыкальные материалы опубликованы под CC0 и переработаны локальным
скриптом `generate_end_rift_music.py`. Скрипт не скачивает файлы во время
сборки: он читает только уже проверенные OGG из указанной папки и создаёт
детерминированные аранжировки с ограниченной длительностью.

| Группа | Файлы | Назначение |
| --- | --- | --- |
| Ожидание | `ritual_wait.ogg` | Напряжённое ожидание на рунах |
| Волны | `wave_1.ogg` … `wave_7.ogg` | Отдельная аранжировка каждой волны |
| Переходы | `intermission_1.ogg`, `intermission_2.ogg`, `intermission_3.ogg`, `intermission_5.ogg`, `intermission_6.ogg` | Пауза между соседними этапами |
| Восстановление | `core_restoration.ogg` | Возврат ядра после штурма обелисков |
| Подготовка босса | `pre_boss_cooldown.ogg`, `boss_cinematic.ogg` | 20-секундная пауза и выход босса |
| Фазы босса | `boss_awakening.ogg`, `boss_hunt.ogg`, `boss_rift.ogg`, `boss_overload.ogg`, `boss_rage.ogg`, `boss_last_seal.ogg` | Плавная смена музыкального слоя по реальному HP |
| Завершение | `boss_finish.ogg`, `victory.ogg` | Добивание и победный хвост |

Внешние страницы исходных CC0-материалов, использованных для локальных
аранжировок:

- cynicmusic, Battle Theme A — https://opengameart.org/content/battle-theme-a
- SubspaceAudio / Juhani Junkala, Boss Battle Music — https://opengameart.org/content/boss-battle-music
- nene, Boss Battle 2 — https://opengameart.org/content/boss-battle-2-symphonic-metal
- cynicmusic, Dramatic Boss Encounter — https://opengameart.org/content/dramatic-boss-encounter
- cynicmusic, Victory Theme for RPG — https://opengameart.org/content/victory-theme-for-rpg
- CC0 — https://creativecommons.org/publicdomain/zero/1.0/

Вне активного события плагин останавливает все event-owned дорожки. Обычная
игра и ванильные звуки не затрагиваются.
