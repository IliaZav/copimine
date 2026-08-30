from __future__ import annotations

import argparse
import re
import shutil
import tempfile
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape


ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = Path(
    r"C:\Users\zavod\.codex\plugins\cache\openai-curated-remote\openai-templates\0.1.1\skills\artifact-template-system-design\assets\reference.docx"
)
ASSET_DIR = ROOT / "docs" / "texture-brief-assets"
W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
WP_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
PIC_NS = "http://schemas.openxmlformats.org/drawingml/2006/picture"


def esc(value: object) -> str:
    return escape(str(value), {"\"": "&quot;"})


def run(text: str, *, bold: bool = False, italic: bool = False, color: str | None = None,
        size: int = 20, font: str = "Helvetica Neue", code: bool = False) -> str:
    if code:
        font = "Courier New"
        color = color or "233447"
        size = min(size, 18)
    props = (
        f'<w:rFonts w:ascii="{esc(font)}" w:cs="{esc(font)}" '
        f'w:eastAsia="{esc(font)}" w:hAnsi="{esc(font)}"/>'
        + ('<w:b w:val="1"/><w:bCs w:val="1"/>' if bold else '')
        + ('<w:i w:val="1"/><w:iCs w:val="1"/>' if italic else '')
        + (f'<w:color w:val="{color}"/>' if color else '')
        + f'<w:sz w:val="{size}"/><w:szCs w:val="{size}"/>'
    )
    return f'<w:r><w:rPr>{props}</w:rPr><w:t xml:space="preserve">{esc(text)}</w:t></w:r>'


def paragraph(*runs: str, style: str | None = None, before: int = 0, after: int = 110,
              line: int = 285, align: str | None = None, keep_next: bool = False,
              num_id: int | None = None, level: int = 0, page_break: bool = False) -> str:
    ppr = []
    if style:
        ppr.append(f'<w:pStyle w:val="{style}"/>')
    if page_break:
        ppr.append('<w:pageBreakBefore w:val="1"/>')
    if keep_next:
        ppr.append('<w:keepNext w:val="1"/>')
    ppr.append(f'<w:spacing w:before="{before}" w:after="{after}" w:line="{line}" w:lineRule="auto"/>')
    if align:
        ppr.append(f'<w:jc w:val="{align}"/>')
    if num_id is not None:
        ppr.append(f'<w:numPr><w:ilvl w:val="{level}"/><w:numId w:val="{num_id}"/></w:numPr>')
    return f'<w:p><w:pPr>{"".join(ppr)}</w:pPr>{"".join(runs)}</w:p>'


def heading(text: str, level: int = 1, *, page_break: bool = False) -> str:
    return paragraph(run(text, bold=True, color="082a4a", size={1: 32, 2: 25, 3: 21}.get(level, 20)),
                     style=f"Heading{level}", before=220 if level == 1 else 150,
                     after=100, line=300, keep_next=True, page_break=page_break)


def bullet(text: str, *, level: int = 0) -> str:
    return paragraph(run(text, size=19, color="233447"), after=65, line=270, num_id=10, level=level)


def number(text: str, *, level: int = 0) -> str:
    return paragraph(run(text, size=19, color="233447"), after=65, line=270, num_id=11, level=level)


def note(title: str, text: str, fill: str = "e6f0f8") -> str:
    return (
        '<w:tbl><w:tblPr><w:tblW w:w="9360" w:type="dxa"/><w:tblLayout w:type="fixed"/>'
        '<w:tblBorders><w:top w:color="ffffff" w:sz="4" w:val="single"/><w:left w:color="ffffff" w:sz="4" w:val="single"/>'
        '<w:bottom w:color="ffffff" w:sz="4" w:val="single"/><w:right w:color="ffffff" w:sz="4" w:val="single"/></w:tblBorders>'
        '</w:tblPr><w:tblGrid><w:gridCol w:w="9360"/></w:tblGrid><w:tr><w:tc>'
        f'<w:tcPr><w:tcW w:w="9360" w:type="dxa"/><w:shd w:fill="{fill}" w:val="clear"/>'
        '<w:tcMar><w:top w:w="170" w:type="dxa"/><w:left w:w="190" w:type="dxa"/><w:bottom w:w="170" w:type="dxa"/><w:right w:w="190" w:type="dxa"/></w:tcMar></w:tcPr>'
        + paragraph(run(title.upper(), bold=True, color="082a4a", size=19), after=45, line=260)
        + paragraph(run(text, size=19, color="233447"), after=0, line=275)
        + '</w:tc></w:tr></w:tbl>'
    )


def code_box(title: str, lines: list[str], fill: str = "f6f9fc") -> str:
    code_runs: list[str] = []
    for index, line in enumerate(lines):
        if index:
            code_runs.append('<w:br/>')
        code_runs.append(run(line, size=16, color="233447", font="Courier New", code=True))
    code_paragraph = paragraph(*code_runs, after=0, line=235)
    return (
        '<w:tbl><w:tblPr><w:tblW w:w="9360" w:type="dxa"/><w:tblLayout w:type="fixed"/>'
        '<w:tblBorders><w:top w:color="ffffff" w:sz="4" w:val="single"/><w:left w:color="ffffff" w:sz="4" w:val="single"/>'
        '<w:bottom w:color="ffffff" w:sz="4" w:val="single"/><w:right w:color="ffffff" w:sz="4" w:val="single"/></w:tblBorders>'
        '</w:tblPr><w:tblGrid><w:gridCol w:w="9360"/></w:tblGrid><w:tr><w:tc>'
        f'<w:tcPr><w:tcW w:w="9360" w:type="dxa"/><w:shd w:fill="{fill}" w:val="clear"/>'
        '<w:tcMar><w:top w:w="150" w:type="dxa"/><w:left w:w="170" w:type="dxa"/><w:bottom w:w="150" w:type="dxa"/><w:right w:w="170" w:type="dxa"/></w:tcMar></w:tcPr>'
        + paragraph(run(title.upper(), bold=True, color="082a4a", size=19), after=55, line=260)
        + code_paragraph
        + '</w:tc></w:tr></w:tbl>'
    )


def cell(content: str, width: int, *, fill: str = "ffffff", header: bool = False,
         align: str = "left") -> str:
    color = "ffffff" if header else "233447"
    props = (
        f'<w:tcW w:w="{width}" w:type="dxa"/><w:shd w:fill="{fill}" w:val="clear"/>'
        '<w:tcMar><w:top w:w="105" w:type="dxa"/><w:left w:w="125" w:type="dxa"/>'
        '<w:bottom w:w="105" w:type="dxa"/><w:right w:w="125" w:type="dxa"/></w:tcMar>'
        '<w:vAlign w:val="center"/>'
    )
    return f'<w:tc><w:tcPr>{props}</w:tcPr>' + paragraph(
        run(content, bold=header, color=color, size=17 if not header else 16),
        after=0, line=250, align=align
    ) + '</w:tc>'


def table(headers: list[str], rows: list[list[str]], widths: list[int], *, compact: bool = False) -> str:
    assert len(headers) == len(widths)
    assert sum(widths) == 9360, (headers, widths, sum(widths))
    borders = (
        '<w:tblBorders><w:top w:color="ffffff" w:sz="5" w:val="single"/><w:left w:color="ffffff" w:sz="5" w:val="single"/>'
        '<w:bottom w:color="ffffff" w:sz="5" w:val="single"/><w:right w:color="ffffff" w:sz="5" w:val="single"/>'
        '<w:insideH w:color="ffffff" w:sz="5" w:val="single"/><w:insideV w:color="ffffff" w:sz="5" w:val="single"/></w:tblBorders>'
    )
    out = [
        f'<w:tbl><w:tblPr><w:tblW w:w="9360" w:type="dxa"/><w:tblLayout w:type="fixed"/>{borders}</w:tblPr>',
        '<w:tblGrid>' + ''.join(f'<w:gridCol w:w="{w}"/>' for w in widths) + '</w:tblGrid>',
        '<w:tr><w:trPr><w:tblHeader w:val="1"/><w:cantSplit w:val="1"/></w:trPr>'
        + ''.join(cell(value, width, fill="082a4a", header=True, align="center") for value, width in zip(headers, widths))
        + '</w:tr>',
    ]
    for index, row in enumerate(rows):
        fill = "e6f0f8" if index % 2 == 0 else "ffffff"
        out.append('<w:tr><w:trPr><w:cantSplit w:val="1"/></w:trPr>' + ''.join(
            cell(value, width, fill=fill, align="center" if column == 0 else "left")
            for column, (value, width) in enumerate(zip(row, widths))
        ) + '</w:tr>')
    out.append('</w:tbl>')
    return ''.join(out)


def image_paragraph(rel_id: str, name: str, width_emu: int, height_emu: int, doc_id: int) -> str:
    return (
        '<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:before="80" w:after="80"/></w:pPr><w:r><w:drawing>'
        f'<wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="{width_emu}" cy="{height_emu}"/>'
        f'<wp:docPr id="{doc_id}" name="{esc(name)}"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>'
        f'<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic>'
        f'<pic:nvPicPr><pic:cNvPr id="{doc_id}" name="{esc(name)}"/><pic:cNvPicPr/></pic:nvPicPr>'
        f'<pic:blipFill><a:blip r:embed="{rel_id}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
        f'<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{width_emu}" cy="{height_emu}"/></a:xfrm>'
        '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>'
        '</wp:inline></w:drawing></w:r></w:p>'
    )


def metadata_table() -> str:
    widths = [3000, 3000, 3360]
    rows = [
        ["ТЗ для художника", "CopiMine End Rift", "29 августа 2026"],
        ["Статус", "Нужно нарисовать и проверить", "Ветка codex/end-rift-event"],
    ]
    return table(["Документ", "Проект", "Дата / версия"], rows, widths)


def add_numbering(content: str) -> str:
    insertion = (
        '<w:abstractNum w:abstractNumId="10"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/>'
        '<w:lvlText w:val="•"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="520" w:hanging="280"/></w:pPr>'
        '<w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/></w:rPr></w:lvl></w:abstractNum>'
        '<w:abstractNum w:abstractNumId="11"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/>'
        '<w:lvlText w:val="%1."/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="520" w:hanging="280"/></w:pPr>'
        '<w:rPr/></w:lvl></w:abstractNum><w:num w:numId="10"><w:abstractNumId w:val="10"/></w:num>'
        '<w:num w:numId="11"><w:abstractNumId w:val="11"/></w:num>'
    )
    return content.replace("</w:numbering>", insertion + "</w:numbering>")


def build_document() -> str:
    body: list[str] = []
    body.append(paragraph(run("CopiMine End Rift", color="5b7085", size=18, italic=True), after=70, line=250))
    body.append(paragraph(run("Техническое задание на текстуры ивента", color="5b7085", size=36), style="Title", after=90, line=300, keep_next=True))
    body.append(paragraph(run("Что рисовать, где сохранить и как проверить в игре", color="233447", size=22), style="Subtitle", after=190, line=300))
    body.append(metadata_table())
    body.append(paragraph(run("", size=8), after=40, line=100))
    body.append(note(
        "Главное правило",
        "Рисуем только новые ассеты End Rift. Ванильные PNG Minecraft не меняем. Не берём файлы из папки narcotics и не переиспользуем их названия: из-за этого в игре подхватывается чужая картинка. Каждый файл ниже должен лежать ровно по указанному пути и иметь ровно указанное имя.",
        fill="fbe5d6",
    ))
    body.append(heading("1. Коротко о задаче"))
    body.append(paragraph(run("Нужен набор пиксельных текстур для пяти волн, эффектов и босса. Игрок должен сразу отличать ядро, свободную руну, занятую руну, обычных противников и пять стадий Стража Разлома. Рисунок должен читаться в Minecraft при обычном освещении и не превращаться в плоскую бумажную иконку.", size=19, color="233447"), after=110, line=290))
    body.append(paragraph(run("От художника нужны PNG. JSON-модели, mixin-код и ванильные текстуры не трогаем. Если хочется изменить форму модели, сначала сообщи об этом: текущие файлы привязаны к существующим UV-развёрткам.", size=19, color="233447"), after=130, line=290))
    body.append(heading("2. Что уже есть в ветке"))
    body.append(paragraph(run("Ниже показаны настоящие PNG из текущей ветки codex/end-rift-event, а не концепты. Они нужны как отправная точка: можно перерисовать их, сохранив имена и размеры.", size=19, color="233447"), after=80, line=280))
    body.append(image_paragraph("rId14", "current_block_item_textures.png", 5029200, 6310400, 14))
    body.append(paragraph(run("Рисунок 1. Текущие блоки и предметы ивента. Фон шахматный только для показа прозрачности; в PNG его быть не должно.", italic=True, color="5b7085", size=16), after=120, line=240, align="center"))
    body.append(paragraph(run("", size=8), page_break=True, after=0, line=100))
    body.append(image_paragraph("rId15", "current_entity_and_boss_textures.png", 5029200, 6310400, 15))
    body.append(paragraph(run("Рисунок 2. Текущие текстуры мобов и пяти стадий босса. Шалкер помечен как резерв: в текущей композиции волн он не нужен, но файл сохранён для совместимости.", italic=True, color="5b7085", size=16), after=120, line=240, align="center"))

    body.append(heading("3. Правила рисования и экспорта", page_break=True))
    for text in [
        "Формат: PNG с прозрачностью RGBA. Не сохранять JPG, индексированный PNG или картинку с фоном.",
        "Пиксели должны быть резкими: без размытия, сглаживания и полупрозрачного ореола по краям.",
        "Не делать чистый однотонный квадрат. У каждого блока нужны читаемые грани, центр и материал.",
        "Симметрия обязательна для ядра и рун: левую половину можно отразить по вертикали, а затем добавить только очень небольшие симметричные световые акценты.",
        "Контраст проверять на тёмном и светлом фоне. Детали не должны исчезать в ночи или под дождём.",
        "Не рисовать текст, цифры и надписи на мобах и блоках: названия и состояние выводятся игрой.",
        "Не менять ванильные файлы Minecraft и не добавлять текстуры в resourcepacks/.../narcotics/.",
    ]:
        body.append(bullet(text))
    body.append(note("Безопасный экспорт", "Сохрани исходник в своей программе отдельно, а в проект передай только чистый PNG. Имя файла и папка важнее названия слоя в программе. После замены текстуры упаковщик сам соберёт ресурс-пак.", fill="e6f0f8"))
    body.append(heading("4. Цветовая схема"))
    body.append(paragraph(run("Цвета не нужно копировать пиксель в пиксель. Это ориентиры, чтобы весь ивент выглядел одной сценой:", size=19, color="233447"), after=70, line=280))
    body.append(table(["Зона", "Основные цвета", "Какой эффект нужен"], [
        ["Ядро", "#120E25 / #251A4A / #6D2BFF / #35E7FF", "Тёмный камень, трещина, энергия в центре"],
        ["Руна свободна", "#24163D / #B85CFF / #55E7FF", "Холодное фиолетовое свечение, рисунок читается"],
        ["Руна занята", "#12351C / #43FF75 / #D7FF65", "Ясно видно, что игрок стоит на руне"],
        ["Мобы волн", "#160D2D / #4B1C83 / #F02BFF", "Обычная форма остаётся узнаваемой"],
        ["Босс", "#6D2BFF → #FF2B4C по стадиям", "Цвет заметно меняется вместе с фазой"],
    ], [1700, 3650, 4010]))

    body.append(heading("5. Текстуры блоков на арене"))
    body.append(paragraph(run("Эти четыре файла видны как реальные блоки/руны на полу. Размер всех четырёх PNG — 32×32 пикселя.", size=19, color="233447"), after=80, line=280))
    body.append(table(["Файл", "Для кого", "Как рисовать", "Проверка"], [
        ["end_event_core.png", "Ядро в обычном состоянии", "Квадратный блок: тёмная каменная основа, симметричный разлом и светящийся центр. Орнамент должен покрывать всю верхнюю грань, а не выглядеть бумажной картинкой.", "В игре наведись на любой блок и сделай его ядром: текстура должна совпасть с кубом по граням."],
        ["end_event_core_charged.png", "Ядро во время зарядки и награды", "Та же форма и границы, но больше света: голубой центр, фиолетовое кольцо, несколько белых пикселей блика. Не менять силуэт блока.", "Проверить зарядку и reward burst из ядра."],
        ["end_event_rune.png", "Свободная руна на полу", "Плоская печать, рассчитанная на верх блока. Круг/крест и четыре направляющих луча должны быть симметричны; рисунок занимает почти всю верхушку.", "После постановки ядра две руны видны на полу, не висят и не проваливаются."],
        ["end_event_rune_occupied.png", "Руна, на которой стоит игрок", "Сохраняет тот же рисунок, но добавляет яркий зелёно-жёлтый контур и более светлый центр. Смена должна быть заметной за один взгляд.", "Встать на руну: она меняет цвет, выйти: возвращается свободный вариант."],
    ], [2300, 1740, 3420, 1900]))
    body.append(note("Важно про положение", "Художник не должен рисовать отдельную летающую картинку над блоком. Руна — это текстура верхней части реального блока, а ядро — полный куб. Если в игре объект висит, это исправляется в модели/трансформации, но PNG всё равно должен быть рассчитан на плоскость верхней грани.", fill="fbe5d6"))

    body.append(heading("6. Иконки предметов и награды"))
    body.append(table(["Файл", "Размер", "Где используется", "Что важно"], [
        ["item/end_event_core.png", "32×32", "Предмет ядра в руке/инвентаре", "Повторяет знак ядра, но читается как предмет; прозрачный фон."],
        ["item/end_event_core_charged.png", "32×32", "Заряженное ядро", "Более яркий центр, не путать с обычным ядром."],
        ["item/end_event_pad.png", "32×32", "Предмет свободной руны", "Тот же знак, что у руны на полу."],
        ["item/end_event_pad_occupied.png", "32×32", "Предмет занятой руны", "Зелёно-жёлтая версия для понятного occupied-состояния."],
        ["item/rift_core_shard.png", "64×64", "Главная награда после босса", "Один выразительный осколок, фиолетовый с голубым бликом; не делать плоский ромб без объёма."],
        ["item/end_event_boss.png", "64×64", "Иконка босса/служебный предмет", "Миниатюрный силуэт Стража Разлома, без мелкого текста."],
    ], [2550, 920, 2530, 3360]))

    body.append(heading("7. Текстуры мобов волн"))
    body.append(paragraph(run("Обычные мобы остаются узнаваемыми: игрок должен сразу понять, что перед ним паук, скелет или эндермен. Новая текстура добавляет роль и тему разлома, но не ломает ванильную форму.", size=19, color="233447"), after=80, line=280))
    body.append(table(["Файл", "Размер", "Роль", "Как отличить"], [
        ["end_rift_spider.png", "64×32", "Обычный быстрый паук волн", "Фиолетовые прожилки по панцирю, яркие глаза; тело и лапы не сливаются в пятно."],
        ["end_rift_skeleton.png", "64×32", "Обычный скелет-стрелок", "Тёмные кости, бирюзовые/фиолетовые вставки; лук и силуэт должны оставаться читаемыми."],
        ["end_rift_elite_skeleton.png", "64×32", "Мини-босс скелет", "Больше светящихся полос и красно-фиолетовый акцент, но не полностью другая палитра."],
        ["end_rift_enderman.png", "64×32", "Обычный эндермен/страж волны", "Тёмное тело, фиолетовые трещины, глаза не теряются в фоне."],
        ["end_rift_elite.png", "64×32", "Элитный эндермен", "Более яркие глаза и плотнее узор трещин; с расстояния сразу видно elite-вариант."],
        ["end_rift_guardian.png", "64×32", "Отдельная роль стража волны", "Золотисто-фиолетовый или выбранный контрастный вариант; не копировать boss."],
        ["end_rift_shulker.png", "64×64", "Резерв, сейчас не спавнится", "Сохранить только для совместимости; новую версию рисовать после отдельного согласования."],
    ], [2380, 900, 2060, 4020]))
    body.append(note("Что сейчас реально используется", "В текущей композиции волн используются пауки, скелеты и эндермены. Шалкер оставлен в каталоге клиента как резерв и не должен быть обязательной частью новой пачки рисунков.", fill="e6f0f8"))

    body.append(heading("8. Страж Разлома: пять текстур босса"))
    body.append(paragraph(run("Все пять файлов имеют размер 128×128 пикселей и рассчитаны на одну и ту же custom-модель. Не менять расположение частей тела и не обрезать прозрачные поля: одна и та же текстура должна работать после смены фазы.", size=19, color="233447"), after=80, line=280))
    body.append(table(["Файл", "Стадия", "Образ", "Что меняется"], [
        ["rift_guardian_awakening.png", "Пробуждение", "Страж только вышел из разлома", "Тёмная оболочка, спокойные фиолетовые трещины, слабое свечение груди."],
        ["rift_guardian_hunter.png", "Охотник", "Он выбрал цель", "Глаза и грудь ярче, трещины острее, добавь холодный синий акцент."],
        ["rift_guardian_distortion.png", "Искажение", "Форма нестабильна", "Розово-фиолетовые разрывы, ощущение провала части тела в пустоту, но симметрия UV сохраняется."],
        ["rift_guardian_absorption.png", "Поглощение", "Он тянет энергию ядра", "Белое/голубое ядро на груди, светлые энергетические жилы; не превращать весь силуэт в белый."],
        ["rift_guardian_catastrophe.png", "Катастрофа", "Оболочка почти разрушена", "Красно-фиолетовый перегрев, самые яркие трещины и несколько нестабильных фрагментов."],
    ], [2840, 1500, 2300, 2720]))
    body.append(paragraph(run("Отдельная иконка босса item/end_event_boss.png не заменяет пять фазовых текстур. Босс в игре использует 3D-модель и эти PNG как материалы, а иконка нужна только для предметного/служебного отображения.", italic=True, color="5b7085", size=17), after=120, line=260))

    body.append(heading("9. Структура папок"))
    body.append(paragraph(run("Создай файлы строго в этой структуре. Названия папок и файлов не переводить:", size=19, color="233447"), after=60, line=280))
    tree = [
        "resourcepacks/src/assets/copimine/textures/block/",
        "  end_event_core.png",
        "  end_event_core_charged.png",
        "  end_event_rune.png",
        "  end_event_rune_occupied.png",
        "resourcepacks/src/assets/copimine/textures/item/",
        "  end_event_core.png   end_event_core_charged.png",
        "  end_event_pad.png    end_event_pad_occupied.png",
        "  end_event_boss.png   rift_core_shard.png",
        "CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/",
        "  end_rift_spider.png  end_rift_skeleton.png",
        "  end_rift_elite_skeleton.png  end_rift_enderman.png",
        "  end_rift_elite.png   end_rift_guardian.png",
        "  end_rift_shulker.png  (резерв)",
        "  rift_guardian_awakening.png  rift_guardian_hunter.png",
        "  rift_guardian_distortion.png  rift_guardian_absorption.png",
        "  rift_guardian_catastrophe.png",
    ]
    body.append(code_box("Дерево каталогов", tree, fill="f6f9fc"))
    body.append(heading("10. Как передать готовые файлы"))
    for text in [
        "Не переименовывай файлы и не складывай всё в одну папку.",
        "Передай PNG вместе с сохранёнными исходниками из программы рисования, если нужна ещё одна правка.",
        "Если изменён только один файл, передавай только его и напиши точный путь, например resourcepacks/src/assets/copimine/textures/block/end_event_core.png.",
        "Не присылай изменённые JSON и не удаляй ванильные файлы.",
        "Для проверки сначала открой PNG на прозрачном фоне, затем дождись сборки resource pack и смотри уже в Minecraft.",
    ]:
        body.append(number(text))

    body.append(heading("11. Приёмка: что проверить в игре"))
    body.append(paragraph(run("Галочки ставятся после проверки в локальном сервере. Одного просмотра PNG недостаточно.", size=19, color="233447"), after=80, line=280))
    acceptance = [
        ["□", "Ядро выглядит как полный куб и совпадает с блоком, а не висит над ним."],
        ["□", "Свободные руны видны после установки ядра и лежат на верхней грани пола."],
        ["□", "Руна меняет цвет, когда на ней стоит игрок, и возвращает свободный вариант после выхода."],
        ["□", "После каждой волны награда появляется из ядра, а ванильные текстуры мира не изменились."],
        ["□", "Паук, скелет, элитный скелет, эндермен и элитный эндермен имеют event-текстуры только у event UUID."],
        ["□", "Босс использует отдельную модель и пять текстур: Пробуждение, Охотник, Искажение, Поглощение, Катастрофа."],
        ["□", "После reconnect/restart привязка текстур повторяется, а обычные мобы остаются ванильными."],
        ["□", "Скриншоты сделаны на локальном сервере: ядро, две руны, моб каждой роли и все пять фаз босса."],
    ]
    body.append(table(["", "Проверка"], acceptance, [700, 8660]))
    body.append(heading("12. Команды для локальной проверки"))
    body.append(table(["Команда", "Что увидеть"], [
        ["/cmend test visuals mobs", "Сервер создаёт event-мобов, отправляет UUID-привязки; на клиенте должны быть новые скины."],
        ["/cmend test visuals boss awakening", "Модель Стража и текстура Пробуждения."],
        ["/cmend test visuals boss hunter", "Переключение на текстуру Охотника."],
        ["/cmend test visuals boss distortion", "Переключение на текстуру Искажения."],
        ["/cmend test visuals boss absorption", "Переключение на текстуру Поглощения."],
        ["/cmend test visuals boss catastrophe", "Переключение на текстуру Катастрофы."],
        ["/cmend status", "Визуальные привязки ядра/рун, стадия босса и состояние ивента."],
    ], [3300, 6060]))
    body.append(note("Если что-то не видно", "Сначала проверь путь и имя файла, затем SHA/дату собранного resource pack и клиентский мод. Не исправляй проблему заменой ванильной текстуры: это маскирует ошибку и ломает обычную игру.", fill="fbe5d6"))
    body.append(heading("13. Итог для художника"))
    body.append(paragraph(run("Нужно нарисовать 4 текстуры блоков, 6 иконок предметов, 6 активных текстур мобов и 5 фазовых текстур босса. Всего 21 обязательный PNG; шалкер — резервный 22-й файл, его можно не перерисовывать в этом заходе. Главное — точные пути, резкие пиксели, симметричные ядро и руны, узнаваемые мобы и заметная смена цвета у пяти фаз босса.", bold=True, color="082a4a", size=20), after=110, line=300))
    body.append(paragraph(run("Документ составлен по текущей структуре файлов ветки. Если в процессе рисования потребуется новая модель или новый размер, сначала согласуйте это с тем, кто собирает ресурс-пак: простая замена PNG не должна менять ванильную игру.", size=19, color="233447"), after=0, line=280))
    return "".join(body)


def create(output: Path) -> None:
    if not TEMPLATE.exists():
        raise FileNotFoundError(TEMPLATE)
    required = [ASSET_DIR / "current_block_item_textures.png", ASSET_DIR / "current_entity_and_boss_textures.png"]
    for path in required:
        if not path.exists():
            raise FileNotFoundError(path)
    output.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix="end-rift-texture-brief-"))
    package: dict[str, bytes] = {}
    with zipfile.ZipFile(TEMPLATE, "r") as source:
        for info in source.infolist():
            package[info.filename] = source.read(info.filename)

    original = package["word/document.xml"].decode("utf-8")
    section = re.search(r"<w:sectPr[\s\S]*?</w:sectPr>", original)
    if section is None:
        raise ValueError("Template document has no section properties")
    document = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" '
        'xmlns:o="urn:schemas-microsoft-com:office:office" '
        'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" '
        'xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" '
        'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" '
        'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" '
        'xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
        f'<w:body>{build_document()}{section.group(0)}</w:body></w:document>'
    )
    package["word/document.xml"] = document.encode("utf-8")
    numbering = package["word/numbering.xml"].decode("utf-8")
    package["word/numbering.xml"] = add_numbering(numbering).encode("utf-8")
    rels = package["word/_rels/document.xml.rels"].decode("utf-8")
    rels = rels.replace(
        "</Relationships>",
        '<Relationship Id="rId14" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/end_rift_texture_brief_blocks.png"/>'
        '<Relationship Id="rId15" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/end_rift_texture_brief_entities.png"/>'
        "</Relationships>",
    )
    package["word/_rels/document.xml.rels"] = rels.encode("utf-8")
    package["word/media/end_rift_texture_brief_blocks.png"] = (ASSET_DIR / "current_block_item_textures.png").read_bytes()
    package["word/media/end_rift_texture_brief_entities.png"] = (ASSET_DIR / "current_entity_and_boss_textures.png").read_bytes()
    for footer_name in ("word/footer1.xml", "word/footer2.xml"):
        if footer_name in package:
            package[footer_name] = package[footer_name].replace(
                b"[Organization Name] | System Design RFC",
                "CopiMine | End Rift — ТЗ на текстуры".encode("utf-8"),
            )
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as dest:
        for name, data in package.items():
            dest.writestr(name, data)
    shutil.rmtree(stage, ignore_errors=True)
    print(f"Created {output}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "docs" / "end-rift-texture-brief.docx")
    args = parser.parse_args()
    create(args.output.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
