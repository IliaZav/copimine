"""Keep the Wave 2 hunt mark to its agreed glow-only presentation."""

from pathlib import Path
import shutil
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
LAYOUT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/EndEventPlayerVisualLayout.java"


def _declaration(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated method: {signature}")


def test_hunt_target_does_not_emit_world_particles() -> None:
    source = EVENT.read_text(encoding="utf-8")
    update = _declaration(source, "private void updateMarkedTargetObjective(long now)")
    assert "spawnEventParticle" not in update
    assert "waveTwoNextVisualMillis" not in update
    assert "PotionEffectType.SPEED" not in update
    assert "PotionEffectType.RESISTANCE" not in update


def test_hunt_screen_layout_is_empty_but_carrier_layout_is_kept(tmp_path: Path) -> None:
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        pytest.skip("a local Java runtime is required for the detached visual policy check")

    package = tmp_path / "me/copimine/client"
    package.mkdir(parents=True)
    manager = package / "EndEventPlayerVisualManager.java"
    manager.write_text(
        """package me.copimine.client;
public final class EndEventPlayerVisualManager {
  public enum Kind { CARRIER, HUNT }
  public record VisualState(Kind kind, long startedAtMillis, long expiresAtMillis) {}
}
""",
        encoding="utf-8",
    )
    check = package / "HuntLayoutCheck.java"
    check.write_text(
        """package me.copimine.client;
public final class HuntLayoutCheck {
  public static void main(String[] args) {
    var hunt = new EndEventPlayerVisualManager.VisualState(
        EndEventPlayerVisualManager.Kind.HUNT, 1000L, 11000L);
    if (!EndEventPlayerVisualLayout.edges(1920, 1080, hunt, 2000L).isEmpty())
      throw new AssertionError("HUNT mark must not draw a screen overlay");
    var carrier = new EndEventPlayerVisualManager.VisualState(
        EndEventPlayerVisualManager.Kind.CARRIER, 1000L, 2500L);
    if (EndEventPlayerVisualLayout.edges(1920, 1080, carrier, 1500L).isEmpty())
      throw new AssertionError("carrier feedback unexpectedly disappeared");
  }
}
""",
        encoding="utf-8",
    )
    result = subprocess.run(
        [javac, "-encoding", "UTF-8", "-d", str(tmp_path), str(manager), str(LAYOUT), str(check)],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr
    result = subprocess.run(
        [java, "-cp", str(tmp_path), "me.copimine.client.HuntLayoutCheck"],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr
