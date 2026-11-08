"""Wave 6 Rift Ram: the authoritative rules for the new encounter.

Wave 6 used to be a ritual sphere with casters, guards and a prisoner, and its
completion depended on mage deaths, a broken prison and sphere cleanup. It is
replaced by one oversized Ravager that the party must lure into the Core three
times. These tests pin the rules that make that loop playable: the charge line
is locked, one charge can crack the Core at most once, duplicates cannot bank a
second impact, and the Ravager cannot die before the third impact.
"""
import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"

PROBE = textwrap.dedent("""
    import me.copimine.endevent.domain.RiftRamPolicy;
    import me.copimine.endevent.domain.RiftRamPolicy.State;

    public final class RiftRamProbe {
        private static int checks = 0;

        private static void check(String label, boolean value) {
            if (!value) throw new AssertionError("FAIL " + label);
            checks++;
        }

        public static void main(String[] args) {
            // --- The encounter needs exactly three Core impacts -----------------
            check("three impacts are required", RiftRamPolicy.REQUIRED_IMPACTS == 3);
            check("impact three is still accepted", RiftRamPolicy.mayAcceptImpact(State.CHARGING, 2, false));
            check("impact four is refused", !RiftRamPolicy.mayAcceptImpact(State.CHARGING, 3, false));
            check("a banked charge cannot bank again",
                    !RiftRamPolicy.mayAcceptImpact(State.CHARGING, 1, true));
            check("only a charging ram can crack the core",
                    !RiftRamPolicy.mayAcceptImpact(State.CHASING, 0, false)
                    && !RiftRamPolicy.mayAcceptImpact(State.WARNING, 0, false)
                    && !RiftRamPolicy.mayAcceptImpact(State.STUNNED, 0, false));

            // --- Health curve: floor at a solo party, ceiling at 20 --------------
            check("one player gets the floor", RiftRamPolicy.healthForPlayers(1) == 180.0D);
            check("twenty players get the ceiling", RiftRamPolicy.healthForPlayers(20) == 400.0D);
            check("the curve never exceeds the ceiling", RiftRamPolicy.healthForPlayers(60) == 400.0D);
            check("an empty party still gets a real ram", RiftRamPolicy.healthForPlayers(0) == 180.0D);
            check("two players sit above the floor", RiftRamPolicy.healthForPlayers(2) > 180.0D);
            check("the curve is monotonic",
                    RiftRamPolicy.healthForPlayers(5) < RiftRamPolicy.healthForPlayers(10));

            // --- Damage gating: always takes damage, never dies early -----------
            check("no impact means a fifth", RiftRamPolicy.damageMultiplier(0) == 0.20D);
            check("one impact means two fifths", RiftRamPolicy.damageMultiplier(1) == 0.40D);
            check("two impacts mean 65 percent", RiftRamPolicy.damageMultiplier(2) == 0.65D);
            check("three impacts mean full damage", RiftRamPolicy.damageMultiplier(3) == 1.0D);
            check("the multiplier is never zero, so hits always register",
                    RiftRamPolicy.damageMultiplier(0) > 0.0D);
            check("an out-of-range count clamps", RiftRamPolicy.damageMultiplier(99) == 1.0D
                    && RiftRamPolicy.damageMultiplier(-3) == 0.20D);

            // --- Charge timing ---------------------------------------------------
            long cooldown = RiftRamPolicy.cooldownMillis();
            check("the cooldown is nine seconds", cooldown == 9000L);
            check("a charge may not start while chasing off cooldown",
                    !RiftRamPolicy.mayStartCharge(State.CHASING, 0, cooldown - 1L, 0L));
            check("a charge may start once the cooldown elapses",
                    RiftRamPolicy.mayStartCharge(State.CHASING, 0, cooldown, 0L));
            check("a stunning ram cannot charge",
                    !RiftRamPolicy.mayStartCharge(State.STUNNED, 0, 999999L, 0L));
            check("an exposed ram cannot charge",
                    !RiftRamPolicy.mayStartCharge(State.EXPOSED, 3, 999999L, 0L));
            check("a completed encounter cannot charge",
                    !RiftRamPolicy.mayStartCharge(State.COMPLETED, 0, 999999L, 0L));

            // --- State machine ---------------------------------------------------
            check("the first two impacts stun", RiftRamPolicy.stateAfterImpact(1) == State.STUNNED
                    && RiftRamPolicy.stateAfterImpact(2) == State.STUNNED);
            check("the third impact exposes", RiftRamPolicy.stateAfterImpact(3) == State.EXPOSED);
            check("after a stun the ram hunts again",
                    RiftRamPolicy.stateAfterStun(2) == State.CHASING);
            check("after the final stun it stays exposed",
                    RiftRamPolicy.stateAfterStun(3) == State.EXPOSED);

            // --- Locked charge direction ----------------------------------------
            double[] locked = RiftRamPolicy.lockChargeDirection(0.0D, 64.0D, 0.0D,
                    10.0D, 64.0D, 0.0D);
            check("a locked direction is a unit vector",
                    Math.abs(Math.hypot(locked[0], locked[1]) - 1.0D) < 1.0E-9D);
            check("the direction points at the target", locked[0] > 0.99D);
            check("a target on top of the ram yields no charge",
                    RiftRamPolicy.lockChargeDirection(3.0D, 64.0D, 3.0D, 3.0D, 64.0D, 3.0D) == null);
            check("a non-finite target yields no charge",
                    RiftRamPolicy.lockChargeDirection(0.0D, 64.0D, 0.0D,
                            Double.NaN, 64.0D, 0.0D) == null);

            // --- Swept Core collision --------------------------------------------
            check("a charge straight through the core hits",
                    RiftRamPolicy.segmentHitsCore(-10.0D, 100.0D, 0.0D, 10.0D, 100.0D, 0.0D,
                            0.0D, 100.0D, 0.0D));
            check("a charge that stops short misses",
                    !RiftRamPolicy.segmentHitsCore(-10.0D, 100.0D, 0.0D, -6.0D, 100.0D, 0.0D,
                            0.0D, 100.0D, 0.0D));
            check("a sidestep misses",
                    !RiftRamPolicy.segmentHitsCore(-10.0D, 100.0D, 8.0D, 10.0D, 100.0D, 8.0D,
                            0.0D, 100.0D, 0.0D));
            // The whole point of the swept test: one step jumps clean over the core.
            check("a long step cannot tunnel through",
                    RiftRamPolicy.segmentHitsCore(-20.0D, 100.0D, 0.0D, 20.0D, 100.0D, 0.0D,
                            0.0D, 100.0D, 0.0D));
            check("a charge high above misses",
                    !RiftRamPolicy.segmentHitsCore(-10.0D, 120.0D, 0.0D, 10.0D, 120.0D, 0.0D,
                            0.0D, 100.0D, 0.0D));
            check("a charge below the core misses",
                    !RiftRamPolicy.segmentHitsCore(-10.0D, 80.0D, 0.0D, 10.0D, 80.0D, 0.0D,
                            0.0D, 100.0D, 0.0D));
            check("the vertical band is symmetric",
                    RiftRamPolicy.segmentHitsCore(-10.0D, 102.9D, 0.0D, 10.0D, 102.9D, 0.0D,
                            0.0D, 100.0D, 0.0D)
                    && RiftRamPolicy.segmentHitsCore(-10.0D, 97.1D, 0.0D, 10.0D, 97.1D, 0.0D,
                            0.0D, 100.0D, 0.0D));
            check("non-finite geometry never hits",
                    !RiftRamPolicy.segmentHitsCore(Double.NaN, 100.0D, 0.0D, 10.0D, 100.0D, 0.0D,
                            0.0D, 100.0D, 0.0D));

            // --- Charge contact with players -------------------------------------
            check("a player on the path is hit",
                    RiftRamPolicy.chargeHitsPlayer(0.0D, 0.0D, 10.0D, 0.0D, 5.0D, 0.5D, false));
            check("the same player is not hit twice in one charge",
                    !RiftRamPolicy.chargeHitsPlayer(0.0D, 0.0D, 10.0D, 0.0D, 5.0D, 0.5D, true));
            check("a player who stepped aside is missed",
                    !RiftRamPolicy.chargeHitsPlayer(0.0D, 0.0D, 10.0D, 0.0D, 5.0D, 4.0D, false));
            check("a player behind the start is missed",
                    !RiftRamPolicy.chargeHitsPlayer(0.0D, 0.0D, 10.0D, 0.0D, -3.0D, 0.0D, false));
            check("a player past the end is missed",
                    !RiftRamPolicy.chargeHitsPlayer(0.0D, 0.0D, 10.0D, 0.0D, 14.0D, 0.0D, false));

            // --- Completion ------------------------------------------------------
            check("three impacts alone do not finish the wave",
                    !RiftRamPolicy.isComplete(3, true));
            check("a dead ram without three impacts does not finish the wave",
                    !RiftRamPolicy.isComplete(2, false));
            check("three impacts and a dead ram finish the wave",
                    RiftRamPolicy.isComplete(3, false));

            System.out.println("OK " + checks);
        }
    }
""")


def test_rift_ram_rules(tmp_path):
    java = tmp_path / "RiftRamProbe.java"
    java.write_text(PROBE, encoding="utf-8")
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(DOMAIN / "RiftRamPolicy.java"), str(java)],
        capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    ran = subprocess.run(["java", "-cp", str(tmp_path), "RiftRamProbe"],
                         capture_output=True, text=True)
    assert ran.returncode == 0, ran.stdout + ran.stderr
    assert ran.stdout.strip().startswith("OK ")


def test_wave_six_is_the_ram_not_the_sphere():
    objective = (DOMAIN / "EndRiftObjective.java").read_text(encoding="utf-8")
    assert "case 6 -> Objective.RIFT_RAM;" in objective, (
        "Wave 6 must map to the new canonical objective")
    assert 'RIFT_RAM("Таран Разлома")' in objective
    # The old objective survives only so an old snapshot can be read.
    assert "RITUAL_SPHERE(" in objective
    legacy = objective[objective.index("public boolean isLegacy()"):]
    assert "this == RITUAL_SPHERE" in legacy and "this == COLLAPSE_RINGS" in legacy
