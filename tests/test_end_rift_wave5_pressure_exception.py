from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_two_player_wave5_exception_is_narrowly_applied_to_all_production_paths():
    source = SOURCE.read_text(encoding="utf-8")
    initialize = method(source, "private boolean initializeWaveGameplay")
    diagnostics = method(source, "private int plannedWaveCountForDiagnostics")
    restore = method(source, "private void restorePersistedWaveSchedule")

    for body in (initialize, diagnostics, restore):
        assert "WavePressurePolicy.clamp(wave, scaled, players)" in body
    assert "WavePressurePolicy.isApprovedTwoPlayerException(wave, players, scaled)" in initialize
    assert "PressureBudgetController.HARD_CAP" in initialize


def test_wave5_config_match_does_not_capture_elites_from_later_waves():
    config = (
        "  wave-5:\n"
        "    normal-endermen: 4\n"
        "  wave-6:\n"
        "    elite-endermen: 6\n"
        "    elite-skeletons: 2\n"
    )
    match = re.search(r"(?m)^  wave-5:\s*\n(?P<body>(?:^    .*\n)+)", config)
    assert match, "Wave 5 composition must remain explicit in config.yml"
    assert "elite-endermen" not in match.group("body")


def test_wave5_default_config_keeps_the_user_approved_eight_elites():
    config = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")
    match = re.search(r"(?m)^  wave-5:\s*\n(?P<body>(?:^    .*\n)+)", config)
    assert match, "Wave 5 composition must remain explicit in config.yml"
    body = match.group("body")
    assert re.search(r"^    elite-endermen:\s*6\s*$", body, re.M)
    assert re.search(r"^    elite-skeletons:\s*2\s*$", body, re.M)
