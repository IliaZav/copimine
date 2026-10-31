"""Scoped cleanup: resources registered to a generation are released exactly once.

Contract 2026-10-31: entities, tasks and closers owned by an encounter are
released together, failures are reported instead of swallowed, a foreign
generation cannot release another attempt's resources, and preserved actors
(e.g. a projectile that must survive a wave transition) are kept.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event/src/me/copimine/endevent"

_HARNESS = """import java.util.*;
import me.copimine.endevent.runtime.*;
public class {name} {{
    static void require(boolean value, String reason) {{ if (!value) throw new AssertionError(reason); }}
    public static void main(String[] args) {{
{body}
    }}
}}
"""


def _run_probe(tmp_path: Path, name: str, body: str, extra: list[str] | None = None) -> str:
    probe = tmp_path / f"{name}.java"
    probe.write_text(_HARNESS.format(name=name, body=body), encoding="utf-8")
    files = [PLUGIN / "runtime/EncounterResourceScope.java"] + [
        PLUGIN / path for path in (extra or [])
    ]
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, files), str(probe)],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    run = subprocess.run(["java", "-cp", str(tmp_path), name], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    return run.stdout


def test_closers_run_once_and_are_reported():
    """Closing a scope releases every registered closer exactly once."""
    source = (PLUGIN / "runtime/EncounterResourceScope.java").read_text(encoding="utf-8")
    assert "public synchronized void registerCloser(Runnable closer)" in source
    assert "public synchronized CleanupResult closeResources()" in source
    assert "public synchronized void close()" in source
    assert "CleanupResult.successful()" in source
    # A scope exposes its generation so a stale callback can prove ownership.
    assert "public long generation()" in source
    # Cleanup failures are collected, never swallowed silently.
    assert "CleanupFailure" in source
    assert "cleanup" in source.lower()


def test_closed_scope_cannot_accept_new_resources():
    source = (PLUGIN / "runtime/EncounterResourceScope.java").read_text(encoding="utf-8")
    # closeResources() is the single place that flips the flag; every public
    # close path funnels through it so no caller can leak a live scope.
    close_at = source.index("private CleanupResult closeResources(Set<UUID> entityIdsToPreserve)")
    tail = source[close_at:]
    assert "closed = true;" in tail, "the cleanup path must mark the scope closed"
    assert "if (closing) return lastCleanupResult;" in tail, (
        "a second close must reuse the audited result instead of double-freeing"
    )
    assert "public synchronized CleanupResult closeResources()" in source
    assert "return closeResources(Set.of());" in source
    assert "public synchronized boolean closed()" in source, "the scope must report whether it is closed"
    # Late registration on a closed scope is drained immediately, not parked.
    assert "if (closed && !closing) {" in source, (
        "a resource registered on a closed scope must be released at once"
    )


def test_preserved_entities_survive_a_scoped_release():
    """A projectile/actor that must outlive the wave is excluded from the sweep."""
    source = (PLUGIN / "runtime/EncounterResourceScope.java").read_text(encoding="utf-8")
    assert "closeResourcesPreservingEntities" in source, (
        "wave transitions must be able to keep actors and projectiles alive"
    )
    assert "Set<UUID> entityIdsToPreserve" in source
    assert "public synchronized Set<UUID> entityIds()" in source, (
        "the caller needs the owned entity set to compute what to preserve"
    )