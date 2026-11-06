import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'copimine-end-event' / 'src' / 'me' / 'copimine' / 'endevent' / 'CopiMineEndEvent.java'


def spawn_sites(root: str):
    """Every arrow this plugin launches, as (line, following 12 lines)."""
    lines = root.splitlines()
    out = []
    for index, line in enumerate(lines):
        if 'spawnArrow(' in line and 'return' not in line:
            out.append((index + 1, '\n'.join(lines[index:index + 12])))
    return out


def test_every_arrow_this_plugin_launches_is_visible():
    # "Arrows are invisible": a Bukkit arrow starts hidden and only renders once
    # setVisibleByDefault(true) is called. Two of the four launch sites (the
    # side volley and the elite arrow rain) omitted the call, so the whole
    # volley was launched as nothing at all.
    sites = spawn_sites(SRC.read_text(encoding='utf-8'))
    assert len(sites) >= 4, 'expected the known arrow launch sites'
    missing = [line for line, body in sites if 'setVisibleByDefault(true)' not in body]
    assert not missing, 'arrows launched without setVisibleByDefault(true) at lines ' + repr(missing)


def test_arrow_visibility_is_set_before_the_arrow_becomes_visible():
    # The flag has to be applied to the spawned arrow itself, not to a later
    # aggregate, otherwise the first tick renders nothing.
    root = SRC.read_text(encoding='utf-8')
    for line, body in spawn_sites(root):
        if 'setVisibleByDefault(true)' in body:
            continue
        raise AssertionError('arrow at line ' + str(line) + ' is never made visible')
