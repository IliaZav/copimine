"""Withdrawing a published shaderpack must never destroy the player's own file.

Older builds wrote the event's runtime copy to the top level of shaderpacks/,
which is the folder Iris scans for its menu. Consumers saw the drug packs listed
by name next to their own Bliss, BSL and Sildur's packs. Pulling those stale
files back out is correct, but the first implementation of the withdrawal did
this:

    private Path removeIfDifferent(Path target, byte[] bytes) throws Exception {
        if (target == null || !Files.isRegularFile(target)) {
            return null;
        }
        Files.deleteIfExists(target);      // bytes was never compared
        return target;
    }

The method takes the expected bytes and ignores them, so a file is deleted on
the strength of its name alone. A player who keeps a pack under the same name
as an event effect loses it on the next launch. These tests pin the rule that
made the deletion safe: a file is only removed when its bytes prove it is one of
our exports, and anything removed is backed up first.
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXPORTER = (
    ROOT
    / "tools"
    / "minecraft-26.3"
    / "client"
    / "src"
    / "main"
    / "java"
    / "me"
    / "copimine"
    / "client"
    / "ShaderpackExporter.java"
)
WRITER = (
    ROOT
    / "tools"
    / "minecraft-26.3"
    / "client"
    / "src"
    / "main"
    / "java"
    / "me"
    / "copimine"
    / "client"
    / "ShaderpackExportFileWriter.java"
)


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    return re.sub(r"(?m)//.*$", "", text)


def method_body(source: str, name: str) -> str:
    """Return the body of the named method declaration, matched by brace depth.

    The declaration is anchored on a modifier so a call site such as
    removeIfDifferent(runtimeTarget, prepared.bytes()); cannot be mistaken for
    the definition itself.
    """
    marker = re.search(
        rf"\b(?:private|static|public|protected)[^;{{}}]*\b{name}\s*\([^)]*\)[^{{]*\{{",
        source,
    )
    assert marker, f"{name} declaration is missing"
    start = marker.end()
    depth = 1
    index = start
    while index < len(source) and depth:
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
        index += 1
    return source[start:index]


def test_the_withdrawal_compares_bytes_before_deleting():
    body = method_body(strip_comments(EXPORTER.read_text(encoding="utf-8")), "removeIfDifferent")
    # A deletion guarded only by the file name is the bug: same name, different
    # owner. The method must consult the bytes it is given.
    assert "isOurExport(" in body, (
        "the withdrawal must prove the file is ours before removing it"
    )
    delete_position = body.find("Files.deleteIfExists")
    proof_position = body.find("isOurExport(")
    assert delete_position > proof_position >= 0, (
        "provenance must be established before the file is deleted"
    )


def test_a_file_is_only_ours_when_it_matches_a_copimine_export():
    source = strip_comments(EXPORTER.read_text(encoding="utf-8"))
    body = method_body(source, "isOurExport")
    # Either the current bundle or the copy a previous build left in the private
    # folder. Nothing else counts, so foreign content survives.
    assert "MessageDigest.isEqual" in body, (
        "comparison must be a real byte comparison, not a length or name check"
    )
    assert "runtimeShaderpackDir" in body, (
        "a stale export is recognised by the private copy the previous build left"
    )
    assert "sha256(" in body


def test_an_unreadable_candidate_is_left_in_place():
    body = method_body(strip_comments(EXPORTER.read_text(encoding="utf-8")), "removeIfDifferent")
    assert "catch" in body, (
        "a file that cannot be read cannot be proven to be ours, so the failure "
        "must be handled instead of aborting the whole export"
    )


def test_a_withdrawn_file_is_backed_up_first():
    body = method_body(strip_comments(EXPORTER.read_text(encoding="utf-8")), "removeIfDifferent")
    backup_position = body.find("writeIfChanged(")
    delete_position = body.find("Files.deleteIfExists")
    assert backup_position >= 0, (
        "an unexpected withdrawal must leave a restorable copy behind"
    )
    assert backup_position < delete_position, (
        "the backup must be written before the published copy is removed"
    )


def test_the_writer_itself_only_rewrites_when_the_bytes_differ():
    body = method_body(strip_comments(WRITER.read_text(encoding="utf-8")), "writeIfChanged")
    assert "MessageDigest.isEqual" in body, (
        "an identical file must be left untouched; rewriting it would churn the "
        "folder on every launch"
    )
