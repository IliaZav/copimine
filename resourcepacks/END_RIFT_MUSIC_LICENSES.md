# End Rift music credits

`ritual_wait.ogg` — tense instrumental waiting bed with no vocals.  It is
currently a byte-identical copy of `boss_cinematic.ogg` on purpose: the
waiting stage has its own Minecraft sound event and can be replaced by a
separate arrangement later without changing plugin code.  SHA-256:
`EC53DBDFC32FC5F25A6265C91FB1E9C30BC2986A617CADD970E338C845906769`;
duration: about 22 seconds.

Все пять файлов в resource pack — инструментальные треки без слов, перекодированные в OGG для Minecraft. В репозиторий добавлены только производные OGG-файлы и этот список источников. Все источники опубликованы под CC0; для безопасности сохранены авторы и страницы с исходными файлами.

| Фаза | Автор | Источник | Лицензия |
| --- | --- | --- | --- |
| Волны | cynicmusic | https://opengameart.org/content/battle-theme-a | CC0 — https://creativecommons.org/publicdomain/zero/1.0/ |
| Босс 100–50% | SubspaceAudio / Juhani Junkala | https://opengameart.org/content/boss-battle-music | CC0 — https://creativecommons.org/publicdomain/zero/1.0/ |
| Босс ниже 50% | nene | https://opengameart.org/content/boss-battle-2-symphonic-metal | CC0 — https://creativecommons.org/publicdomain/zero/1.0/ |
| Босс ниже 10% | cynicmusic | https://opengameart.org/content/dramatic-boss-encounter | CC0 — https://creativecommons.org/publicdomain/zero/1.0/ |
| Победа | cynicmusic | https://opengameart.org/content/victory-theme-for-rpg | CC0 — https://creativecommons.org/publicdomain/zero/1.0/ |

У трека symphonic metal страница прямо указывает, что версия без вокала. Остальные выбранные страницы помечают файлы как instrumental/music и не содержат текстовых дорожек; в сборку не попадают никакие вокальные или lyric-файлы.

Дополнительная благодарность авторам и страницам: cynicmusic.com, pixelsphere.org и Juhani Junkala. Эти ссылки оставлены даже там, где CC0 не требует атрибуции.

## Собранные файлы

| Файл | Длительность | SHA-256 |
| --- | ---: | --- |
| `sounds/end_rift/waves.ogg` | 95.851837 s | `DF497602C3A736AAF35552A9758931DE6EC58E68BDEB229A8888480EBB06D1DA` |
| `sounds/end_rift/boss.ogg` | 123.428594 s | `CCF82469B09FD1C528B5CABE1A2742159A25D1DA10A4DB6D4299BE23796080BF` |
| `sounds/end_rift/boss_half.ogg` | 26.482766 s | `0F0463B6EB7014DAF3B9A5D5A3ECF8AFB53752DE7FA247BFB67F0CF044ADBFAE` |
| `sounds/end_rift/boss_final.ogg` | 39.916825 s | `9D206C8F34C642783EB777ACEB1A9774DB9F9F7A15D4C58B01CFABA53EAEB468` |
| `sounds/end_rift/victory.ogg` | 20.000000 s | `06F996282802C769D9FF4A007FB02B01835C0803319F3B265E5EF18AD2611ADD` |

У `boss-battle-2-symphonic-metal` архивная подпись opening/loop перепутана самим архивом; для `boss_half.ogg` взят файл `boss_battle_#2_metal_opening.wav`, который страница помечает как фактический loop.

## Фазовые аранжировки

Для коротких переходов и каждой отдельной волны из этих же CC0-источников
собраны производные аранжировки без вокала: у них разные фрагменты, темп,
стереодвижение и тихая синтезаторная подложка. Это не новые скачанные
произведения и не замена лицензии исходников; генератор находится в
`generate_end_rift_music.py`, чтобы сборку можно было воспроизвести локально.

| Файл | Назначение | Длительность | SHA-256 |
| --- | --- | ---: | --- |
| `sounds/end_rift/wave_1.ogg` | Волна I — пробуждение | 48.000000 s | `A4301D38E4FAB18436C577153AEB4E5E57253721AF4C9A1DAE91DEF05980D208` |
| `sounds/end_rift/wave_2.ogg` | Волна II — охота | 46.000000 s | `C842D655671D98612BAEA08380B3145A12487F522A05859F699FF459D878E3E2` |
| `sounds/end_rift/wave_3.ogg` | Волна III — порталы | 44.000000 s | `77C1AC8B3AF6526A1DC04DD95BA3672B5F53604ABA9108C4FDBFA7B2140E7901` |
| `sounds/end_rift/wave_4.ogg` | Волна IV — оборона | 50.000000 s | `6F5D8E68C7DDCAC830A0C5E52846B70192B6D658E27277756CBD8EB432942402` |
| `sounds/end_rift/wave_5.ogg` | Волна V — шторм | 54.000000 s | `92960136FB03E2C384491E44DE5014A7EB4D3FF9603C7C31F0A161E0994D2067` |
| `sounds/end_rift/intermission_1.ogg` | Переход I | 14.000000 s | `B2025C26CDB130852CCECB83F2B01BF38F25D6456ED636B69ECEADFB8061DCDC` |
| `sounds/end_rift/intermission_2.ogg` | Переход II | 14.000000 s | `638AA3615A6782F7616A1E4C4310A45B00A23B0BE1BB9273B2B41EF48BF83150` |
| `sounds/end_rift/intermission_3.ogg` | Переход III | 16.000000 s | `5601B0113997AF8AAE2F8D828095E7088E1FB9CAA156C28AC771C59193C7E46F` |
| `sounds/end_rift/intermission_4.ogg` | Переход IV | 16.000000 s | `B9F04B91EC19897735BECE1F39B09D35A36914DE9E67D4D97C595C297B2D4CFE` |
| `sounds/end_rift/boss_cinematic.ogg` | Вход босса | 22.000000 s | `EC53DBDFC32FC5F25A6265C91FB1E9C30BC2986A617CADD970E338C845906769` |
| `sounds/end_rift/final_drain.ogg` | Высасывание сил | 24.000000 s | `CB1AC36651F4F9B71E372D10DDFE75BDF24AA387870D18EA7C9459C9427F7823` |
| `sounds/end_rift/final_ritual.ogg` | Финальный ритуал | 20.000000 s | `B12503D7D74AB7E1D5C6492C35329329203CB30EC16E783AF7134B7D2923AA49` |
| `sounds/end_rift/final_wave.ogg` | Последняя волна | 48.000000 s | `3C83AD2A37A80E30AEE6EB21DFF3CE45E836D3970A3EC9775D44446DDB97EDAF` |
| `sounds/end_rift/boss_finish.ogg` | Добивание босса | 20.000000 s | `C887FDEEF09229DBCEC2DC07DDB06F1308FC3A58E007C04E053212B4D5BD6E05` |
