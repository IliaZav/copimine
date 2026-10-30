"""Раздаваемый app-bundle.js должен совпадать со своими исходниками.

Иначе можно поправить файл в assets/js/ и выложить старый бандл: сайт будет
работать по-старому, а тесты и git — зелёными.
"""

import re
from pathlib import Path

ROOT = Path(__file__).parents[1] / "frontend"
BUNDLE = ROOT / "assets" / "js" / "app-bundle.js"
HEADER = ("/* CopiMine browser bundle. Source files stay in assets/js/; rebuild this\n"
          "   file after changing any of them (order matters). */\n\n")


def test_bundle_is_exactly_its_sources_in_order():
    text = BUNDLE.read_text(encoding="utf-8")
    sources = re.findall(r"/\* (assets/js/[^*]+) \*/", text)
    assert len(sources) == len(set(sources)) == 50, "список исходников бандла изменился"

    expected = HEADER + "".join(
        "/* %s */%s\n" % (name, (ROOT / name).read_text(encoding="utf-8")) for name in sources
    )
    assert text == expected, (
        "app-bundle.js разошёлся с исходниками. Пересобери его, а не правь файл в assets/js/ отдельно."
    )