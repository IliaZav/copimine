"""The boss's silhouette and its health bar are both projections, and both must stay narrow.

Complaint (1) is "the boss has no model from the user's assets". The supplied Bedrock guardian
is drawn at its authored scale, and it is grounded by a fixed origin correction. Complaint
(1) also means the boss must read as a boss, which is where the health projection comes in:
Minecraft's client attribute clamps large boss values, so the client substitutes the server's
max health - but ONLY for the one entity the server actually bound. Both classes had no
behavioural test, only the artifact check that they ship in the jar.

A wrong projection here is not cosmetic: a too-wide allowlist makes every mob's health bar
adopt the boss's maximum.
"""
from __future__ import annotations

import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_SRC = ROOT / "tools/minecraft-26.3/client/src/main/java"
SCALE = CLIENT_SRC / "me/copimine/client/EndRiftBossWorldScalePolicy.java"
HEALTH = CLIENT_SRC / "me/copimine/client/EndRiftBossHealthProjection.java"

STUB_STATE = textwrap.dedent(
    """
    package me.copimine.client;

    /** Only the boss bar snapshot the health projection reads. */
    public final class EndEventClientState {
        private EndEventClientState() { }

        public record BossBarState(String instanceId, String bossUuid, String phaseId,
                                   String castState, int health, int maxHealth, float progress,
                                   boolean visible, long updatedAtMillis) { }
    }
    """
)

PROBE = textwrap.dedent(
    """
    package me.copimine.client;

    public final class BossProjectionProbe {
        private static int failures = 0;

        private static void check(String name, boolean condition) {
            if (!condition) {
                System.out.println("FAIL " + name);
                failures++;
            }
        }

        private static EndEventClientState.BossBarState bar(String bossUuid, boolean visible,
                                                           int maxHealth, float progress) {
            return new EndEventClientState.BossBarState("END_RIFT_GENERATION_1", bossUuid, "PHASE_1",
                    "IDLE", 100, maxHealth, progress, visible, 1000L);
        }

        public static void main(String[] args) {
            String boss = "11111111-2222-3333-4444-555555555555";
            String other = "99999999-8888-7777-6666-555555555555";

            // --- Authored silhouette -----------------------------------------------------
            check("the supplied guardian keeps its authored scale",
                    EndRiftBossWorldScalePolicy.renderScale() == 1.0F);
            check("the authored scale constant agrees",
                    EndRiftBossWorldScalePolicy.SUPPLIED_GUARDIAN_SCALE == 1.0F);
            // The correction lifts the imported mesh foot (0.018) onto the vanilla 1.501 baseline.
            float correction = EndRiftBossWorldScalePolicy.modelOriginCorrectionY();
            check("the origin correction is positive", correction > 1.0F && correction < 2.0F);
            check("the origin correction grounds the mesh within a millimetre",
                    Math.abs(correction - (1.501F - 0.0182939F)) < 1.0E-4F);
            // A correction of zero would leave the boss sunk into the floor.
            check("the boss is not left floor-sunk", correction != 0.0F);

            // --- Health projection is bound to exactly one entity --------------------------
            EndEventClientState.BossBarState own = bar(boss, true, 4800, 0.42F);
            check("the bound boss projects", EndRiftBossHealthProjection.isProjected(boss, boss, own));
            check("the bound boss takes the server maximum",
                    EndRiftBossHealthProjection.resolveMaxHealth(boss, 20.0D, boss, own) == 4800.0D);

            // Every other entity keeps the native attribute.
            check("another entity is untouched",
                    EndRiftBossHealthProjection.resolveMaxHealth(other, 20.0D, boss, own) == 20.0D);
            check("another entity is not projected",
                    !EndRiftBossHealthProjection.isProjected(other, boss, own));

            // An unbound client projects nothing at all.
            check("no bound uuid means no projection",
                    EndRiftBossHealthProjection.resolveMaxHealth(boss, 20.0D, null, own) == 20.0D);
            check("blank bound uuid means no projection",
                    EndRiftBossHealthProjection.resolveMaxHealth(boss, 20.0D, "  ", own) == 20.0D);
            check("null entity means no projection",
                    EndRiftBossHealthProjection.resolveMaxHealth(null, 20.0D, boss, own) == 20.0D);
            check("null snapshot means no projection",
                    EndRiftBossHealthProjection.resolveMaxHealth(boss, 20.0D, boss, null) == 20.0D);

            // A hidden bar must not resurrect a dead boss's health pool.
            check("a hidden bar projects nothing",
                    EndRiftBossHealthProjection.resolveMaxHealth(boss, 20.0D, boss,
                            bar(boss, false, 4800, 0.42F)) == 20.0D);

            // The snapshot must be about the same boss the entity is.
            check("a snapshot for another boss is ignored",
                    EndRiftBossHealthProjection.resolveMaxHealth(boss, 20.0D, boss,
                            bar(other, true, 4800, 0.42F)) == 20.0D);

            // The entity must also BE the bound boss. This is the check that stops the boss
            // maximum leaking onto a second entity: here the entity and the snapshot agree
            // with each other but not with what the client is actually bound to, so the
            // later snapshot comparison alone would happily project. Verified by mutation.
            check("an entity that is not the bound boss is not projected",
                    !EndRiftBossHealthProjection.isProjected(other, boss, bar(other, true, 4800, 0.42F)));
            check("an entity that is not the bound boss keeps its native maximum",
                    EndRiftBossHealthProjection.resolveMaxHealth(other, 20.0D, boss,
                            bar(other, true, 4800, 0.42F)) == 20.0D);

            // Degenerate payloads are refused rather than adopted.
            check("a blank instance id is refused",
                    EndRiftBossHealthProjection.resolveMaxHealth(boss, 20.0D, boss,
                            new EndEventClientState.BossBarState("  ", boss, "P", "I", 1, 4800, 0.4F,
                                    true, 1L)) == 20.0D);
            check("a non-positive maximum is refused",
                    EndRiftBossHealthProjection.resolveMaxHealth(boss, 20.0D, boss,
                            bar(boss, true, 0, 0.4F)) == 20.0D);
            check("a non-finite progress is refused",
                    EndRiftBossHealthProjection.resolveMaxHealth(boss, 20.0D, boss,
                            bar(boss, true, 4800, Float.NaN)) == 20.0D);
            check("an infinite progress is refused",
                    EndRiftBossHealthProjection.resolveMaxHealth(boss, 20.0D, boss,
                            bar(boss, true, 4800, Float.POSITIVE_INFINITY)) == 20.0D);

            System.out.println(failures == 0 ? "OK" : "FAILURES " + failures);
            if (failures != 0) System.exit(1);
        }
    }
    """
)


def _javac() -> Path:
    candidates = sorted(ROOT.glob("local-runtime/toolchains/temurin-*/jdk-*/bin/javac.exe"))
    assert candidates, "no local JDK found under local-runtime/toolchains"
    return candidates[0]


def test_boss_scale_and_health_projection_stay_narrow(tmp_path: Path) -> None:
    assert SCALE.is_file(), f"boss scale source is missing: {SCALE}"
    assert HEALTH.is_file(), f"boss health projection source is missing: {HEALTH}"
    javac = _javac()
    java = javac.with_name("java.exe")

    package_dir = tmp_path / "src" / "me" / "copimine" / "client"
    package_dir.mkdir(parents=True, exist_ok=True)
    (package_dir / "EndEventClientState.java").write_text(STUB_STATE, encoding="utf-8")
    probe = package_dir / "BossProjectionProbe.java"
    probe.write_text(PROBE, encoding="utf-8")

    compile_result = subprocess.run(
        [str(javac), "-proc:none", "-encoding", "UTF-8", "-d", str(tmp_path / "out"),
         str(package_dir / "EndEventClientState.java"),
         str(package_dir / "BossProjectionProbe.java"), str(SCALE), str(HEALTH)],
        capture_output=True, text=True, timeout=300,
    )
    assert compile_result.returncode == 0, (
        "probe failed to compile:\n" + compile_result.stdout + compile_result.stderr
    )

    run_result = subprocess.run(
        [str(java), "-cp", str(tmp_path / "out"), "me.copimine.client.BossProjectionProbe"],
        capture_output=True, text=True, timeout=300,
    )
    combined = run_result.stdout + run_result.stderr
    assert run_result.returncode == 0, "boss projection probe failed:\n" + combined
    assert "OK" in run_result.stdout, combined
