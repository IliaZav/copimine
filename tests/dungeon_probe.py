"""Compile the real pure dungeon core with ``javac`` and run fixture mains.

The ``copimine-dungeon`` module keeps its entire editor/command/store logic in
plain Java with no Bukkit imports. That means the pytest suite can compile the
*production* sources directly (the same javac-probe idea used by
``tests/test_crushing_gravity_runtime.py``) and exercise the real code instead
of a mirror. Only the thin Bukkit shell (``DungeonCommand``,
``DungeonBuilderWand``, the listener and the plugin main) is excluded, because it
needs the server API.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
MODULE_SRC = ROOT / "copimine-dungeon" / "src"
PACKAGE = MODULE_SRC / "me" / "copimine" / "dungeon"

# Editor classes that are pure Java (no Bukkit imports).
PURE_EDITOR = {
    "WandToken.java",
    "EditorPermissions.java",
    "EditorMode.java",
    "EditorSession.java",
    "EditorSessionManager.java",
}
# Command classes that need the server API and therefore cannot be compiled here.
BUKKIT_COMMAND = {"DungeonCommand.java"}


def pure_sources():
    """Every production Java file that has no Bukkit dependency."""
    files = []
    for folder in ("domain", "store", "command"):
        files.extend(sorted((PACKAGE / folder).glob("*.java")))
    files = [path for path in files if path.name not in BUKKIT_COMMAND]
    files.extend(sorted(path for path in (PACKAGE / "editor").glob("*.java")
                        if path.name in PURE_EDITOR))
    return files


def compile_core(out_dir):
    """Compile the pure core into ``out_dir`` and return it."""
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    sources = [str(path) for path in pure_sources()]
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-proc:none", "-d", str(out_dir), *sources],
        text=True, capture_output=True, timeout=180, encoding="utf-8", errors="replace",
    )
    assert result.returncode == 0, result.stdout + result.stderr
    return out_dir


def compile_fixture(tmp_path, core_dir, source, class_name):
    """Compile a fixture main against the already-compiled core."""
    java = Path(tmp_path) / f"{class_name}.java"
    java.write_text(source, encoding="utf-8")
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-proc:none", "-cp", str(core_dir),
         "-d", str(core_dir), str(java)],
        text=True, capture_output=True, timeout=120, encoding="utf-8", errors="replace",
    )
    assert result.returncode == 0, result.stdout + result.stderr
    return core_dir


def run_fixture(core_dir, class_name, *args, timeout=30):
    """Run a compiled fixture main and return its stdout (asserting success)."""
    result = subprocess.run(
        ["java", "-cp", str(core_dir), class_name, *args],
        text=True, capture_output=True, timeout=timeout, encoding="utf-8", errors="replace",
    )
    assert result.returncode == 0, result.stdout + result.stderr
    return result.stdout
