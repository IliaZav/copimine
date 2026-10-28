# TAB Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить ванильный список игроков одной центрированной панелью в стиле референса: тёмный фон с рамкой, крупный логотип `COPIMINE`, строки статистики, строки игроков с иконкой сигнала и подвал TPS/MSPT/Ping — в обоих клиентах (26.3 и 1.21.1).

**Architecture:** Один `@Inject(at = @At("HEAD"), cancellable = true)` в отрисовке списка игроков отменяет ванильный рендер целиком и рисует панель поверх: процедурный `fill`-фон, `outline`-рамка, строки шапки и подвала берутся из `header`/`footer`, которые уже прислал TAB (со стилями), строки игроков — из приватного списка самого класса через `@Invoker`. Логика без маппингов (порог пинга, высота строки) вынесена в `TabPanelLayout` и продублирована в двух деревьях, потому что общего каталога исходников у сборок нет.

**Tech Stack:** Java 25 + Fabric Loom 1.19.1 + official mappings (26.3); Java 21 + Loom 1.8.13 + Yarn 1.21.1+build.3 (legacy); Mixin (SpongePowered); JUnit 5; Python/pytest для контрактных тестов; PowerShell для сборки и гейтов.

**Spec:** `docs/superpowers/specs/2026-10-28-copimine-tab-overlay-design.md`

## Global Constraints

- Оба клиента собираются только холодно: `--no-daemon --project-cache-dir`. Пин артефакта берётся исключительно из такой сборки. Тёплая инкрементальная сборка один раз уже дала неверный хеш и уронила CI.
- Пины до этой задачи: клиент 26.3 `15020050` B / sha512 `30f26144862643fbc6ffa0129648ef9fa92536b40ba5162439f08235bd6c3b6f2661a7b2aa79f9d5b64d571ff394d3d0f06828913a9657f905ed09ecb0fcdbd3`; legacy клиент `13030126` B / sha256 `a92616e9a419ffc52306c6c2de9619540bd3dd63594f194b55fae30315db5cc8`. Оба станут неверными после этой задачи и должны быть перепинены.
- Плагин `CopiMineEndEvent-26.3.jar` (1965231 B / sha256 `e3541f93572bd31f2f5c212ccd7d07c174806ddb3d2422f113052226e71bfd45`) эта задача не меняет — но `copimine-admin-plugin` меняется (Task 5), поэтому его артефакт и пин тоже надо пересчитать, если он входит в релизный набор.
- Три обязательные CI-джобы должны быть зелёными на итоговом SHA: `static-and-contract`, `migration-postgres-contract`, `java-plugins`.
- `tests/ValidateCopiMineTabBannerConfigured.ps1` обязан продолжать проходить: конфиг TAB ссылается на глиф `\uE000`, пак содержит `copimine_logo.png`, `default.json`, `minecraft/gui/copimine_tab.png`.
- Ванильные текстуры, ванильные мобы и ванильный GUI не трогаются. В паке нет ни одного оверрайда `assets/minecraft/textures/**`, кроме `gui/copimine_tab.png`.
- Новых настроек, тумблеров и конфига у панели нет: вид фиксирован референсом.
- Свои линии-разделители не рисуются: `\u2501`-строки уже приходят в шапке и подвале от TAB.
- Логотип отдельным `blit` не рисуется: PNG лежит в серверном паке, а не в jar клиента.
- Никакого нового пакета моста ради TPS/MSPT: клиенту их никто не присылает, а в подвале TAB они уже есть текстом.
- `git add` только явными путями. Никакого `git add -A`, `reset --hard`, `clean -fdx`, force push.
- Тесты не ослабляются. Каждый шаг «прогнать и увидеть падение» выполняется до реализации.
- PowerShell блокирует `.ps1` — всегда `powershell -ExecutionPolicy Bypass -File ...`. Перед Java-шагами выставлять `JAVA_HOME` и дописывать его `bin` в `PATH`.
- pytest на этой машине запускать с `-p no:cacheprovider --basetemp="$env:TEMP\cm-pt-..."`, иначе падение на `PermissionError [WinError 5]` при очистке временного каталога.

## Review Focus

- **Пинг `-1`.** Ваниль рисует отдельный `PING_UNKNOWN_SPRITE`, когда задержка неизвестна. Ожидание: **ноль** зажжённых полосок, а не одна. Ловит `TabPanelLayoutTest.pingBarsOfUnknownLatencyIsZero`.
- **Строка с логотипом.** Глиф `\uE000` имеет `height: 30`, глиф `\uE300` — `52`. Ожидание: шаг строки равен их высоте, иначе логотип налезет на «Президент». Ловит `TabPanelLayoutTest.lineHeightFollowsLogoGlyph`.
- **Пустые строки шапки.** Конфиг TAB начинается с двух `''`. Ожидание: отбивка не переносится в панель, шапка начинается с логотипа. Ловит `TabPanelLayoutTest.blankLinesAreNotDrawn`.
- **Имя игрока в команде.** Ожидание: цвет команды, префикс и `tablist-name-formatting` сохраняются. Ловит контрактный тест на использование `getNameForDisplay`/`getPlayerName` вместо `profile.getName()`.
- **Игрок без шапки.** TAB может не прислать `header`/`footer` (например, выключен). Ожидание: панель рисует хотя бы строки игроков и не падает на `null`. Ловит `null`-guard в обоих миксинах и контрактный тест на его наличие.

---

### Task 1: Чистая логика панели в 26.3-клиенте

**Files:**
- Create: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/TabPanelLayout.java`
- Test: `tools/minecraft-26.3/client/src/test/java/me/copimine/client/TabPanelLayoutTest.java`

**Interfaces:**
- Consumes: ничего.
- Produces:
  - `public final class TabPanelLayout` в пакете `me.copimine.client`
  - `public static final int LINE_HEIGHT = 9;`
  - `public static final int LOGO_LINE_HEIGHT = 30;`
  - `public static final int LARGE_LOGO_LINE_HEIGHT = 52;`
  - `public static int pingBars(int latencyMillis)`
  - `public static int lineHeight(String plainLine)`
  - `public static boolean isDrawnLine(String plainLine)`

- [ ] **Step 1: Написать падающий тест**

`tools/minecraft-26.3/client/src/test/java/me/copimine/client/TabPanelLayoutTest.java`:

```java
package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabPanelLayoutTest {
    @Test
    void pingBarsFollowVanillaThresholds() {
        assertEquals(5, TabPanelLayout.pingBars(0));
        assertEquals(5, TabPanelLayout.pingBars(149));
        assertEquals(4, TabPanelLayout.pingBars(150));
        assertEquals(4, TabPanelLayout.pingBars(299));
        assertEquals(3, TabPanelLayout.pingBars(300));
        assertEquals(3, TabPanelLayout.pingBars(599));
        assertEquals(2, TabPanelLayout.pingBars(600));
        assertEquals(2, TabPanelLayout.pingBars(999));
        assertEquals(1, TabPanelLayout.pingBars(1000));
        assertEquals(1, TabPanelLayout.pingBars(2000));
    }

    @Test
    void pingBarsOfUnknownLatencyIsZero() {
        // Vanilla draws PING_UNKNOWN_SPRITE for latency < 0; our bars must not lit up.
        assertEquals(0, TabPanelLayout.pingBars(-1));
    }

    @Test
    void lineHeightFollowsLogoGlyph() {
        assertEquals(TabPanelLayout.LINE_HEIGHT, TabPanelLayout.lineHeight("Президент: не выбран"));
        assertEquals(TabPanelLayout.LOGO_LINE_HEIGHT, TabPanelLayout.lineHeight("\uE000"));
        assertEquals(TabPanelLayout.LARGE_LOGO_LINE_HEIGHT, TabPanelLayout.lineHeight("a\uE300b"));
    }

    @Test
    void blankLinesAreNotDrawn() {
        assertFalse(TabPanelLayout.isDrawnLine(""));
        assertFalse(TabPanelLayout.isDrawnLine("   "));
        assertTrue(TabPanelLayout.isDrawnLine("\u2501\u2501\u2501"));
    }
}
```

- [ ] **Step 2: Прогнать тест и увидеть падение**

Run: `gradle -p tools/minecraft-26.3/client test --tests me.copimine.client.TabPanelLayoutTest`
Expected: FAIL — `cannot find symbol: class TabPanelLayout`.

- [ ] **Step 3: Реализовать `TabPanelLayout`**

Файл `tools/minecraft-26.3/client/src/main/java/me/copimine/client/TabPanelLayout.java`. Пороги пинга — ванильные 150/300/600/1000; `latency < 0` даёт 0. `lineHeight` проверяет `\uE300` раньше `\uE000`, потому что крупный глиф тоже содержит малую часть кодовой точки. `isDrawnLine` — `!plainLine.isBlank()`.

- [ ] **Step 4: Прогнать тест и увидеть успех**

Run: `gradle -p tools/minecraft-26.3/client test --tests me.copimine.client.TabPanelLayoutTest`
Expected: PASS.

- [ ] **Step 5: Коммит**

```bash
git add tools/minecraft-26.3/client/src/main/java/me/copimine/client/TabPanelLayout.java tools/minecraft-26.3/client/src/test/java/me/copimine/client/TabPanelLayoutTest.java
git commit -m "Add the pure TAB panel geometry helper for the 26.3 client"
```

---

### Task 2: Панель в 26.3-клиенте

**Files:**
- Create: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/mixin/PlayerTabOverlayMixin.java`
- Create: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/mixin/PlayerTabOverlayAccessor.java`
- Modify: `tools/minecraft-26.3/client/src/main/resources/copimineclient-26.3.mixins.json:5-33` (добавить `"PlayerTabOverlayMixin"` и `"PlayerTabOverlayAccessor"` в `client`)
- Test: `tests/test_copimine_tab_overlay_contract.py`

**Interfaces:**
- Consumes: `TabPanelLayout.pingBars(int)`, `TabPanelLayout.lineHeight(String)`, `TabPanelLayout.isDrawnLine(String)` из Task 1.
- Produces:
  - `@Mixin(PlayerTabOverlay.class) public abstract class PlayerTabOverlayMixin`
  - `@Mixin(PlayerTabOverlay.class) public interface PlayerTabOverlayAccessor { @Invoker("getPlayerInfos") List<PlayerInfo> copimine$getPlayerInfos(); }`

- [ ] **Step 1: Написать падающий контрактный тест**

Файл `tests/test_copimine_tab_overlay_contract.py` (создаётся здесь; Task 4 расширит его legacy-частью). Константы путей — `ROOT = Path(__file__).resolve().parents[1]`, `CLIENT_26 = ROOT / "tools/minecraft-26.3/client"`.

Тесты:
- `test_26_3_player_list_mixin_targets_the_real_render_entry` — файл существует, содержит `@Mixin(PlayerTabOverlay.class)` и точный дескриптор `"extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V"`, а также `at = @At("HEAD")` и `cancellable = true`.
- `test_26_3_player_list_mixin_is_registered` — `json.loads` файла `copimineclient-26.3.mixins.json`, `assert "PlayerTabOverlayMixin" in config["client"]` и `assert "PlayerTabOverlayAccessor" in config["client"]`.
- `test_26_3_player_list_mixin_uses_display_and_profile_indirection` — в исходнике есть `copimine$getPlayerInfos()` и **нет** `getOnlinePlayers()`; в исходнике есть `copimine$plainText(` и `isDrawnLine(`.
- `test_player_list_mixins_survive_missing_header_and_footer` — в исходнике есть `if (header == null)` и `if (footer == null)`; тот же ассерт появится для legacy в Task 4.

- [ ] **Step 2: Прогнать тест и увидеть падение**

Run: `python -m pytest -q -p no:cacheprovider --basetemp="$env:TEMP\cm-pt-tab" tests/test_copimine_tab_overlay_contract.py`
Expected: FAIL — `FileNotFoundError` на `PlayerTabOverlayMixin.java`.

- [ ] **Step 3: Реализовать аксессор и миксин**

`PlayerTabOverlayAccessor` — по образцу `ClientWorldAccessor.java`:

```java
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Invoker("getPlayerInfos")
    List<PlayerInfo> copimine$getPlayerInfos();
}
```

`PlayerTabOverlayMixin` — по образцу `EndRiftBossBarHudMixin.java` (`abstract class`, префикс `copimine$`, полный дескриптор). Состав:

- `@Shadow private Component header;` и `@Shadow private Component footer;`
- `@Inject(method = "extractRenderState(...)V", at = @At("HEAD"), cancellable = true)`, хендлер `copimine$drawPanel(GuiGraphicsExtractor context, int width, Scoreboard scoreboard, Objective objective, CallbackInfo callback)`: если `header == null && footer == null` — не отменять ваниль, выйти (панель без данных хуже списка). Иначе `copimine$render(...)` и `callback.cancel()`.
- `@Unique private void copimine$render(...)` — алгоритм ниже.
- `@Unique private static String copimine$plainText(FormattedCharSequence line)` — собирает строку через `line.accept((index, style, codePoint) -> { builder.appendCodePoint(codePoint); return true; })`.
- `@Unique private List<FormattedCharSequence> copimine$lines(Component text, Font font)` — `font.split(text, 1000)`.

Алгоритм `copimine$render` (это то, что тесты не определяют):
1. `Font font = Minecraft.getInstance().font;`
2. `List<FormattedCharSequence> headLines = header == null ? List.of() : copimine$lines(header, font);` то же для `footLines`. Обе фильтруются через `TabPanelLayout.isDrawnLine(copimine$plainText(l))`.
3. `List<PlayerInfo> players = ((PlayerTabOverlayAccessor) this).copimine$getPlayerInfos();`
4. Ширина контента: максимум из `font.width(l)` по шапке и подвалу, и по строке игрока (`font.width(name) + 8 + font.width("Ping: " + ms) + 18`). `panelWidth = contentWidth + 24`, ограничить `width - 4`. `left = (width - panelWidth) / 2`, `right = left + panelWidth`.
5. `y = 2`; `top = y`. Фон: `fill(left, top, right, bottom, 0xE62A3324)`; рамка: `outline(left, top, right, bottom, 0xFF7C8F64)`. `bottom` вычисляется предварительным проходом по всем строкам и игрокам (шапка + строки + подвал), чтобы фон не считался дважды: `bottom = top + sum(lineHeight) + players.size() * 12 + 8`.
6. Шапка: для каждой строки `y += TabPanelLayout.lineHeight(plain)` затем `centeredText(font, line, (left + right) / 2, y - height + 1, 0xFFFFFFFF)`. Цвет — только база для нестилизованного текста; цвета из TAB приходят в `Component`.
7. Строки игроков, высота 12: имя — `getNameForDisplay(player)` в `x = left + 6`, `y + 2`; справа `bars` шириной 14 (5 полос по 2 px, шаг 3, высоты 2/3/5/7/9, нижний край `y + 11`, зажжённые `0xFF3ACB4B`, погасшие `0xFF3A3F35`) на `x = right - 6 - 14`; текст `"Ping: " + latency` цветом `0xFFC2C9CF` справа от полосок с отступом 3, выровненный по правому краю `right - 6 - 14 - 3`.
8. Подвал: как шапка, но после игроков плюс 4 px отступа.
9. Больше 20 игроков — панель просто растягивается вниз; ограничение отметить комментарием `shortcut: >20 rows extend past the screen, add scrolling if servers ever exceed 20 listed players`.

- [ ] **Step 4: Зарегистрировать в JSON**

Добавить `"PlayerTabOverlayAccessor"`, `"PlayerTabOverlayMixin"` в массив `client` файла `copimineclient-26.3.mixins.json`. Старый `copimineclient.mixins.json` в этом дереве не трогать: 26.3 читает только `-26.3`-файл, старый копируется как ресурс.

- [ ] **Step 5: Прогнать контрактный тест и увидеть успех**

Run: `python -m pytest -q -p no:cacheprovider --basetemp="$env:TEMP\cm-pt-tab" tests/test_copimine_tab_overlay_contract.py`
Expected: 4 passed (legacy-тесты ещё не добавлены).

- [ ] **Step 6: Собрать 26.3-клиент и прогнать его unit-тесты**

Run: `gradle -p tools/minecraft-26.3/client test jar --no-daemon`
Expected: BUILD SUCCESSFUL, все тесты клиента зелёные.

- [ ] **Step 7: Коммит**

```bash
git add tools/minecraft-26.3/client/src/main/java/me/copimine/client/mixin/PlayerTabOverlayMixin.java tools/minecraft-26.3/client/src/main/java/me/copimine/client/mixin/PlayerTabOverlayAccessor.java tools/minecraft-26.3/client/src/main/resources/copimineclient-26.3.mixins.json tests/test_copimine_tab_overlay_contract.py
git commit -m "Draw the CopiMine TAB panel in the 26.3 client"
```

---

### Task 3: Чистая логика панели в legacy-клиенте

**Files:**
- Create: `CopiMineClient/src/main/java/me/copimine/client/TabPanelLayout.java`
- Test: `CopiMineClient/src/test/java/me/copimine/client/TabPanelLayoutTest.java`

**Interfaces:**
- Consumes: ничего.
- Produces: тот же публичный контракт, что в Task 1 (`LINE_HEIGHT`, `LOGO_LINE_HEIGHT`, `LARGE_LOGO_LINE_HEIGHT`, `pingBars`, `lineHeight`, `isDrawnLine`).

- [ ] **Step 1: Скопировать тест**

Содержимое теста из Task 1 без изменений: класс не зависит от маппингов и от версии.

- [ ] **Step 2: Прогнать и увидеть падение**

Run: `gradle.bat -p CopiMineClient test --tests me.copimine.client.TabPanelLayoutTest` через `CopiMineClient\.gradle-dist\gradle-8.10.2\bin\gradle.bat` с `JAVA_HOME` = `build\minecraft-26.3\toolchain\jdk-21.0.12.1+1`.
Expected: FAIL — `cannot find symbol: class TabPanelLayout`.

- [ ] **Step 3: Скопировать реализацию**

Тот же файл, что в Task 1, без изменений. Общего каталога исходников у двух сборок нет (`CopiMineClient/build.gradle` без `sourceSets`), поэтому дублирование — осознанное решение из спеки.

- [ ] **Step 4: Прогнать и увидеть успех**

Expected: PASS.

- [ ] **Step 5: Коммит**

```bash
git add CopiMineClient/src/main/java/me/copimine/client/TabPanelLayout.java CopiMineClient/src/test/java/me/copimine/client/TabPanelLayoutTest.java
git commit -m "Add the pure TAB panel geometry helper for the 1.21.1 client"
```

---

### Task 4: Панель в legacy-клиенте

**Files:**
- Create: `CopiMineClient/src/main/java/me/copimine/client/mixin/PlayerListHudMixin.java`
- Create: `CopiMineClient/src/main/java/me/copimine/client/mixin/PlayerListHudAccessor.java`
- Modify: `CopiMineClient/src/main/resources/copimineclient.mixins.json` (добавить `"PlayerListHudAccessor"`, `"PlayerListHudMixin"`)
- Modify: `tests/test_copimine_tab_overlay_contract.py` (добавить legacy-тесты)

**Interfaces:**
- Consumes: `TabPanelLayout` из Task 3.
- Produces:
  - `@Mixin(PlayerListHud.class) public abstract class PlayerListHudMixin`
  - `@Mixin(PlayerListHud.class) public interface PlayerListHudAccessor { @Invoker("collectPlayerEntries") List<PlayerListEntry> copimine$collectPlayerEntries(); }`

- [ ] **Step 1: Добавить падающие legacy-тесты**

В `tests/test_copimine_tab_overlay_contract.py`:
- `test_1_21_1_player_list_mixin_targets_the_real_render_entry` — `@Mixin(PlayerListHud.class)` и дескриптор `"render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"`, плюс `at = @At("HEAD")`, `cancellable = true`.
- `test_1_21_1_player_list_mixin_is_registered` — `json.loads` файла `CopiMineClient/src/main/resources/copimineclient.mixins.json`, оба имени в `config["client"]`.
- `test_both_helpers_exist_in_their_own_tree` — `TabPanelLayout.java` и `TabPanelLayoutTest.java` есть в обоих деревьях.
- Расширить `test_player_list_mixins_survive_missing_header_and_footer` на legacy-файл.

- [ ] **Step 2: Прогнать и увидеть падение**

Run: `python -m pytest -q -p no:cacheprovider --basetemp="$env:TEMP\cm-pt-tab" tests/test_copimine_tab_overlay_contract.py`
Expected: FAIL — `FileNotFoundError` на `PlayerListHudMixin.java`.

- [ ] **Step 3: Реализовать аксессор и миксин**

Тот же алгоритм из Task 2, но на Yarn-API:
- `@Shadow private Text header;` / `@Shadow private Text footer;`
- `@Invoker("collectPlayerEntries") List<PlayerListEntry> copimine$collectPlayerEntries();`
- `Font` → `TextRenderer` (`MinecraftClient.getInstance().textRenderer`), `split` → `wrapLines(...)`, `centeredText` → `drawCenteredTextWithShadow`, `outline` → `drawBorder`, `fill` — тот же, `text` → `drawTextWithShadow`.
- `FormattedCharSequence` → `OrderedText`, `accept` с той же лямбдой.
- Имя — `getPlayerName(entry)`, пинг — `entry.getLatency()`.
- Те же константы цветов и те же размеры.

- [ ] **Step 4: Зарегистрировать в JSON**

Добавить оба имени в `client` файла `CopiMineClient/src/main/resources/copimineclient.mixins.json`.

- [ ] **Step 5: Прогнать контрактный тест**

Run: `python -m pytest -q -p no:cacheprovider --basetemp="$env:TEMP\cm-pt-tab" tests/test_copimine_tab_overlay_contract.py`
Expected: все тесты файла PASS.

- [ ] **Step 6: Собрать legacy-клиент**

Run: `CopiMineClient\.gradle-dist\gradle-8.10.2\bin\gradle.bat -p CopiMineClient test jar --no-daemon` с JDK 21.
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Коммит**

```bash
git add CopiMineClient/src/main/java/me/copimine/client/mixin/PlayerListHudMixin.java CopiMineClient/src/main/java/me/copimine/client/mixin/PlayerListHudAccessor.java CopiMineClient/src/main/resources/copimineclient.mixins.json tests/test_copimine_tab_overlay_contract.py
git commit -m "Draw the CopiMine TAB panel in the 1.21.1 client"
```

---

### Task 5: Пустой президент пишется словами

**Files:**
- Modify: `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java:5649-5652`
- Test: `tests/test_copimine_tab_overlay_contract.py`

**Interfaces:**
- Consumes: ничего.
- Produces: `latestWinnerName()` возвращает `"не выбран"` вместо `"нет"` при пустом результате и при исключении.

- [ ] **Step 1: Написать падающий тест**

В `tests/test_copimine_tab_overlay_contract.py`:

```python
def test_president_placeholder_uses_the_reference_wording():
    source = (
        ROOT
        / "copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java"
    ).read_text(encoding="utf-8")
    method = source[source.index("private String latestWinnerName()") :]
    method = method[: method.index("\n    }")]
    assert '"не выбран"' in method
    assert '"нет"' not in method
```

- [ ] **Step 2: Прогнать и увидеть падение**

Run: `python -m pytest -q -p no:cacheprovider --basetemp="$env:TEMP\cm-pt-tab" tests/test_copimine_tab_overlay_contract.py -k president`
Expected: FAIL на второй проверке.

- [ ] **Step 3: Заменить литерал**

В `latestWinnerName()` три вхождения `"нет"` меняются на `"не выбран"` (два в тернарниках, один в `catch`). Ветку `president` в `case "president" -> plugin.latestWinnerName();` не трогать: она уже делегирует сюда.

- [ ] **Step 4: Прогнать и увидеть успех**

Run: `python -m pytest -q -p no:cacheprovider --basetemp="$env:TEMP\cm-pt-tab" tests/test_copimine_tab_overlay_contract.py -k president`
Expected: PASS. Дополнительно `powershell -ExecutionPolicy Bypass -File tests\ValidateCopiMineConsolidation.ps1` — метод на месте, проверка проходит.

- [ ] **Step 5: Коммит**

```bash
git add copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java tests/test_copimine_tab_overlay_contract.py
git commit -m "Say that nobody was elected instead of a bare no"
```

---

### Task 6: Холодная пересборка, перепин и гейты

**Files:**
- Modify: `tools/minecraft-26.3/profile.lock.json` (пин клиента 26.3)
- Modify: `tests/test_minecraft_migration_profile.py` (ожидаемый пин)
- Modify: `docs/minecraft-26.3/README.md` (упоминание пина, если есть)
- Modify: `tools/minecraft-26.3/server-plugins.lock.json` + `docs/end-rift/final-26.3/OWN_PLUGIN_INVENTORY.json` (если `copimine-admin-plugin` входит в релизный набор — иначе не трогать)
- Modify: `thirdparty/checksums.txt`, `thirdparty/thirdparty_manifest.json`, `admin-web/frontend/assets/public-data/modpack_snapshot.json` (после пересборки модпака)
- Modify: `docs/end-rift/final-26.3/CURRENT_HANDOFF.md`

**Interfaces:**
- Consumes: собранные артефакты Task 2, Task 4, Task 5.
- Produces: новые пины и зелёный CI на итоговом SHA.

- [ ] **Step 1: Собрать оба клиента холодно**

Run:
```
powershell -ExecutionPolicy Bypass -File scripts\minecraft\PrepareMigrationCandidates.ps1 -BuildJavaHome <Temurin 25> -GrimPatchJavaHome <JDK 21>
```
Этот скрипт уже делает холодную сборку 26.3-клиента с `--no-daemon --project-cache-dir`. Legacy-клиент собрать через `CopiMineClient\build-client.ps1`.
Expected: оба BUILD SUCCESSFUL.

- [ ] **Step 2: Снять реальные размеры и хеши**

Run: `Get-FileHash` по `build/minecraft-26.3/client/libs/CopiMineClient-0.1.3+26.3.jar` (SHA-512) и `CopiMineClient/build/libs/CopiMineClient-0.1.1.jar` (SHA-256), плюс `Length`.
Expected: числа, отличные от прежних пинов.

- [ ] **Step 3: Обновить пины и ожидания в тестах**

`tools/minecraft-26.3/profile.lock.json` — новый `size`/`sha512` клиента. `tests/test_minecraft_migration_profile.py` — те же значения в ассертах. Если `copimine-admin-plugin` входит в релизный набор, обновить и его пин с теми же правилами (никаких ручных хешей без соответствующего артефакта).

- [ ] **Step 4: Прогнать целевой набор**

Run: `python -m pytest -q -p no:cacheprovider --basetemp="$env:TEMP\cm-pt-tab" tests/test_copimine_tab_overlay_contract.py tests/test_minecraft_26_3_client_command_api.py tests/test_minecraft_migration_profile.py tests/test_minecraft_migration_server_plugins.py tests/test_end_event_current_contract.py tests/test_copimine_client_mob_artifact.py`
Expected: PASS.

- [ ] **Step 5: Пересобрать модпак**

Run: `powershell -ExecutionPolicy Bypass -File scripts\thirdparty\build_modpack.ps1 -SyncBuiltClient`
Expected: обновлены `thirdparty/CopiMineMods.zip`, `thirdparty/checksums.txt`, `thirdparty/thirdparty_manifest.json`, `admin-web/frontend/assets/public-data/modpack_snapshot.json`.

- [ ] **Step 6: Прогнать гейты**

Run: `powershell -ExecutionPolicy Bypass -File tests\ValidateCopiMineTabBannerConfigured.ps1` — Expected: `ValidateCopiMineTabBannerConfigured passed.`
Run: `powershell -ExecutionPolicy Bypass -File tests\RunEndRiftEventChecks.ps1` — Expected: успех.
Run: полный `python -m pytest -q -rs tests -p no:cacheprovider --basetemp="$env:TEMP\cm-pt-full"` — Expected: 0 failures.
Run: `git diff --check` — Expected: exit 0.

- [ ] **Step 7: Обновить handoff и закоммитить**

`CURRENT_HANDOFF.md`: новый SHA, новые пины клиентов, строка о панели TAB и о том, что нативный вид не проверялся. Затем явные `git add` по изменённым путям и коммит.

- [ ] **Step 8: Пуш и CI**

Push в `origin/codex/copimine-source-of-truth-20261020`, проверить через `git ls-remote`, дождаться зелёных `static-and-contract`, `migration-postgres-contract`, `java-plugins` на итоговом SHA.

---

## Self-Review

**Spec coverage.** Панель и рамка — Task 2/4. Логотип — глиф в шапке, отдельного кода не требует, покрыт высотой строки Task 1/3. Президент и онлайн — текст из шапки TAB плюс формулировка Task 5. Строки игроков и иконка сигнала — Task 2/4. Подвал — текст из подвала TAB. Запреты (ванильные текстуры, `layout`, новый пакет, настройки, головы, логотип `blit`, старый JSON) — не нарушаются ни одной задачей. Пересборка и пины — Task 6.

**Step scan.** Каждый шаг — одно действие с проверяемым результатом. Единственный шаг с алгоритмом (Task 2 Step 3) записан как алгоритм, потому что сигнатура и тесты его не определяют; тела методов не переписаны.

**Type consistency.** `TabPanelLayout.pingBars(int)`, `lineHeight(String)`, `isDrawnLine(String)` и три константы одинаковы в обоих деревьях; `copimine$getPlayerInfos()` в Task 2 совпадает с `@Invoker("getPlayerInfos")`, `copimine$collectPlayerEntries()` в Task 4 — с `@Invoker("collectPlayerEntries")`. Имена миксинов в JSON совпадают с именами классов.

**Review Focus.** Все пять строк получили тест: неизвестный пинг — `pingBarsOfUnknownLatencyIsZero`; высота логотипа — `lineHeightFollowsLogoGlyph`; пустые строки — `blankLinesAreNotDrawn`; имя игрока — контрактный тест на `getPlayerInfos` и на отсутствие `getOnlinePlayers`; отсутствие шапки — `if (header == null)` в обоих миксинах.

**Proportion.** План короче спеки по объёму кода: описан один алгоритм, остальное — сигнатуры, имена тестов и ассерты.