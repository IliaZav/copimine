"""The plugin build-dependency probe must follow the build script it validates.

`ValidateCopiMinePluginBuildCompileDependencies.ps1` copies a minimal fixture of
the encounter protocol sources and asserts that `copimine-end-event/build-plugin.ps1`
compiles each of them exactly once. It used to restate the helper list itself,
so when the build script grew from three helpers to six the probe kept copying
three: the fixture then failed with "Required encounter protocol source missing"
and the `java-plugins` CI job went red on a build that was correct.

The probe therefore derives the list from the build script. These checks pin
that wiring: a helper added to the build must be covered by the probe without a
second edit, and a helper missing from the protocol tree must fail closed.
"""

import re

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "tests/ValidateCopiMinePluginBuildCompileDependencies.ps1"
BUILD_SCRIPT = ROOT / "copimine-end-event/build-plugin.ps1"
PROTOCOL_ROOT = ROOT / "tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol"

# The private helper compiler is anchored on EncounterGenerationPolicy: the
# probe refuses a declaration that dropped it, so the anchor is the one name
# the probe may mention itself.
ANCHOR_HELPER = "EncounterGenerationPolicy"


def declared_protocol_helpers() -> list[str]:
    text = BUILD_SCRIPT.read_text(encoding="utf-8")
    declaration = re.search(r"foreach \(\$helper in @\(([^)]*)\)\)", text)
    assert declaration, "build-plugin.ps1 must declare its encounter protocol helper list"
    return re.findall(r"'([A-Za-z0-9_]+)'", declaration.group(1))


def test_probe_reads_the_helper_list_from_the_build_script():
    probe = PROBE.read_text(encoding="utf-8")

    assert "copimine-end-event/build-plugin.ps1" in probe, (
        "the probe must locate the End Event build script to read its helper list"
    )
    assert re.search(r"\$protocolHelpers\s*=\s*@\(\s*'", probe) is None, (
        "the probe must not restate the protocol helper list; that stale copy is what "
        "broke the java-plugins CI job"
    )
    assert re.search(
        r"\$protocolHelpers\s*=\s*@\(\[regex\]::Matches\(", probe
    ), "the probe must parse the helper names out of the build script declaration"


def test_every_declared_helper_is_covered_by_the_probe_and_present_on_disk():
    helpers = declared_protocol_helpers()
    probe = PROBE.read_text(encoding="utf-8")

    assert ANCHOR_HELPER in helpers, "the probe anchors on EncounterGenerationPolicy"
    assert len(helpers) >= 6, (
        "the End Event build compiles the full encounter protocol bundle; "
        f"declared helpers: {helpers}"
    )
    for helper in helpers:
        assert (PROTOCOL_ROOT / f"{helper}.java").is_file(), (
            f"build-plugin.ps1 declares a protocol helper that does not exist: {helper}"
        )

    duplicated = [
        helper for helper in helpers
        if helper != ANCHOR_HELPER and f"'{helper}'" in probe
    ]
    assert duplicated == [], (
        f"the probe duplicates the helper list instead of reading it: {duplicated}"
    )


def test_probe_fails_closed_on_a_declared_helper_with_no_source():
    probe = PROBE.read_text(encoding="utf-8")

    missing_source_guard = probe.index("declares a protocol helper that does not exist")
    copy_loop = probe.index("Copy-Item -LiteralPath $helperSource")
    assert missing_source_guard < copy_loop, (
        "the probe must refuse a declared helper before it copies the fixture"
    )
    assert probe.index("must declare its encounter protocol helper list") > 0
