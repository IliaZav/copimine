"""The End Event candidate jar has to be reproducible from source alone.

The pinned lock names one digest for build/minecraft-26.3/plugins/jars/
CopiMineEndEvent-26.3.jar, so whatever produces that jar must produce the same
bytes every time. It did not.

build-plugin.ps1 packaged the shared EncounterGenerationPolicy class into the
jar by COPYING a .class file left behind by an earlier Gradle run. On a machine
where Gradle had already built the migration project the class was there and
went in; on a clean checkout it was absent and the jar came out different. The
script only printed a note in that case, so nothing failed: the same sources
produced two different artifacts and the lock could match at most one of them.
That is how GitHub produced 5838fae7 while the lock expected 1742c2ea.

The build must therefore compile that class from its source, unconditionally,
and it must not depend on build history being present.
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD_SCRIPT = ROOT / "copimine-end-event" / "build-plugin.ps1"
BRIDGE_SOURCE = (
    ROOT
    / "tools"
    / "minecraft-26.3"
    / "protocol"
    / "src"
    / "main"
    / "java"
    / "me"
    / "copimine"
    / "protocol"
    / "EncounterGenerationPolicy.java"
)


def strip_comments(text: str) -> str:
    """Drop PowerShell block and line comments so prose cannot satisfy a check."""
    text = re.sub(r"<#.*?#>", "", text, flags=re.DOTALL)
    return re.sub(r"(?m)^\s*#.*$", "", text)


def script() -> str:
    return strip_comments(BUILD_SCRIPT.read_text(encoding="utf-8"))


def test_the_bridge_source_exists_where_the_build_expects_it():
    assert BRIDGE_SOURCE.is_file(), (
        "build-plugin.ps1 compiles the shared class from this source; it is missing"
    )
    source = BRIDGE_SOURCE.read_text(encoding="utf-8")
    assert "package me.copimine.protocol;" in source


def test_the_shared_class_is_compiled_from_source_not_copied_from_build_output():
    body = script()
    assert "EncounterGenerationPolicy.java" in body, (
        "the build must name the .java source it compiles"
    )
    # Copying a .class out of a previous Gradle run is exactly what made the
    # candidate depend on build history. The class name may only appear as the
    # javac destination ($bridgeClass); it must never be a copy source.
    assert "-sourcepath" in body, "javac needs the source root to resolve the class"
    # The whole script is searched, not just one section: a mutation that copies
    # the class from build output slipped into an earlier block survived a
    # section-scoped check.
    copy_lines = [
        line
        for line in body.splitlines()
        if "Copy-Item" in line and "EncounterGenerationPolicy" in line
    ]
    assert not copy_lines, (
        "the bridge class must be compiled, never copied from a .class on disk: "
        + "; ".join(copy_lines)
    )
    assert "if (-not (Test-Path -LiteralPath $bridgeSourceFile -PathType Leaf))" not in body, (
        "the compile must not be conditional on the source appearing"
    )
    section = body.split("$bridgeClass =", 1)[1].split("Remove-Item -LiteralPath $jar", 1)[0]
    assert "& $javacCommand" in section, "the shared class must go through javac"


def test_a_missing_shared_class_after_compilation_fails_the_build():
    body = script()
    tail = body.split("EncounterGenerationPolicy.java")[-1]
    # A missing class must abort the build. A printed note is exactly what let
    # the historical nondeterminism pass silently, so any Write-Host or warning
    # that merely announces the absence is treated as a regression.
    absence_block = tail.split("if (-not (Test-Path -LiteralPath $bridgeClass", 1)
    assert len(absence_block) == 2, (
        "the build must verify the compiled class ended up in the jar staging area"
    )
    if absence_block[1]:
        guard = absence_block[1].split("}", 1)[0]
        assert "throw" in guard, (
            "a missing shared class must throw, not be logged: " + guard.strip()
        )
        assert "Write-Host" not in guard, (
            "announcing the absence without failing is the historical bug: " + guard.strip()
        )


def test_the_build_does_not_read_the_gradle_class_output_for_the_bridge_class():
    body = script()
    for stale in (
        "plugins\\classes\\java\\narcotics",
        "build\\classes\\java\\main",
    ):
        assert stale not in body, (
            f"the build must not copy the shared class out of {stale}; that path only "
            "exists after a Gradle run and makes the digest depend on build history"
        )
