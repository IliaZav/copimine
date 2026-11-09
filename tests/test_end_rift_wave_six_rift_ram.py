"""Wave 6 Rift Ram: the authoritative rules for the new encounter.

Wave 6 used to be a ritual sphere with casters, guards and a prisoner, and its
completion depended on mage deaths, a broken prison and sphere cleanup. It is
replaced by one oversized Ravager that the party must lure into the pillar five
times. These tests pin the rules that make that loop playable: the charge line
is locked, one charge can crack the pillar at most once, duplicates cannot bank a
second impact, and the Ravager cannot die before the fifth impact.
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
            // --- The encounter needs exactly five pillar impacts -----------------
            check("five impacts are required", RiftRamPolicy.REQUIRED_IMPACTS == 5);
            check("impact five is still accepted", RiftRamPolicy.mayAcceptImpact(State.CHARGING, 4, false));
            check("impact four is refused", !RiftRamPolicy.mayAcceptImpact(State.CHARGING, 5, false));
            check("a banked charge cannot bank again",
                    !RiftRamPolicy.mayAcceptImpact(State.CHARGING, 1, true));
            check("only a charging ram can crack the pillar",
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
            check("one impact means two fifths", RiftRamPolicy.damageMultiplier(1) == 0.35D);
            check("two impacts mean 65 percent", RiftRamPolicy.damageMultiplier(2) == 0.50D);
            check("five impacts mean full damage", RiftRamPolicy.damageMultiplier(5) == 1.0D);
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
            check("the first four impacts stun", RiftRamPolicy.stateAfterImpact(1) == State.STUNNED
                    && RiftRamPolicy.stateAfterImpact(2) == State.STUNNED);
            check("the fifth impact exposes", RiftRamPolicy.stateAfterImpact(5) == State.EXPOSED);
            check("after a stun the ram hunts again",
                    RiftRamPolicy.stateAfterStun(2) == State.CHASING);
            check("after the final stun it stays exposed",
                    RiftRamPolicy.stateAfterStun(5) == State.EXPOSED);

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

            // --- Swept pillar collision --------------------------------------------
            check("a charge straight through the pillar hits",
                    RiftRamPolicy.segmentHitsPillar(-10.0D, 100.0D, 0.0D, 10.0D, 100.0D, 0.0D,
                            0.0D, 100.0D, 0.0D));
            check("a charge that stops short misses",
                    !RiftRamPolicy.segmentHitsPillar(-10.0D, 100.0D, 0.0D, -6.0D, 100.0D, 0.0D,
                            0.0D, 100.0D, 0.0D));
            check("a sidestep misses",
                    !RiftRamPolicy.segmentHitsPillar(-10.0D, 100.0D, 8.0D, 10.0D, 100.0D, 8.0D,
                            0.0D, 100.0D, 0.0D));
            // The whole point of the swept test: one step jumps clean over the pillar.
            check("a long step cannot tunnel through",
                    RiftRamPolicy.segmentHitsPillar(-20.0D, 100.0D, 0.0D, 20.0D, 100.0D, 0.0D,
                            0.0D, 100.0D, 0.0D));
            check("a charge high above misses",
                    !RiftRamPolicy.segmentHitsPillar(-10.0D, 120.0D, 0.0D, 10.0D, 120.0D, 0.0D,
                            0.0D, 100.0D, 0.0D));
            check("a charge below the pillar misses",
                    !RiftRamPolicy.segmentHitsPillar(-10.0D, 80.0D, 0.0D, 10.0D, 80.0D, 0.0D,
                            0.0D, 100.0D, 0.0D));
            check("the vertical band is symmetric",
                    RiftRamPolicy.segmentHitsPillar(-10.0D, 102.9D, 0.0D, 10.0D, 102.9D, 0.0D,
                            0.0D, 100.0D, 0.0D)
                    && RiftRamPolicy.segmentHitsPillar(-10.0D, 97.1D, 0.0D, 10.0D, 97.1D, 0.0D,
                            0.0D, 100.0D, 0.0D));
            check("non-finite geometry never hits",
                    !RiftRamPolicy.segmentHitsPillar(Double.NaN, 100.0D, 0.0D, 10.0D, 100.0D, 0.0D,
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

            // --- Pillar roster ----------------------------------------------------
            check("no pillar exists before the first impact",
                    RiftRamPolicy.activePillarNumber(0) == 1);
            check("the fifth pillar is the last one",
                    RiftRamPolicy.activePillarNumber(4) == 5);
            check("after five pillars none may be raised",
                    RiftRamPolicy.activePillarNumber(5) == 0
                    && !RiftRamPolicy.mayRaisePillar(5));
            check("a negative count never raises a pillar",
                    !RiftRamPolicy.mayRaisePillar(-1));
            check("four pillars become the exposed state",
                    RiftRamPolicy.activePillarNumber(4) > 0
                    && RiftRamPolicy.damageMultiplier(4) == 0.80D);

            // --- Endermite cap ----------------------------------------------------
            check("an empty arena releases the full roll",
                    RiftRamPolicy.endermitesToRelease(0, 4) == 4);
            check("the roll is clamped to the authored band",
                    RiftRamPolicy.endermitesToRelease(0, 99) == 4
                    && RiftRamPolicy.endermitesToRelease(0, 0) == 2);
            check("the cap counts the living mites, not the pillar",
                    RiftRamPolicy.endermitesToRelease(5, 4) == 1);
            check("a full arena releases nothing but stays legal",
                    RiftRamPolicy.endermitesToRelease(6, 4) == 0
                    && RiftRamPolicy.endermitesToRelease(99, 4) == 0);

            // --- Seismic stomp timing ---------------------------------------------
            long stompGap = RiftRamPolicy.STOMP_COOLDOWN_TICKS * 50L;
            check("a stomp is refused while charging",
                    !RiftRamPolicy.mayStartStomp(State.CHARGING, stompGap, 0L));
            check("a stomp is refused while stunned",
                    !RiftRamPolicy.mayStartStomp(State.STUNNED, stompGap, 0L));
            check("a stomp is refused while warning",
                    !RiftRamPolicy.mayStartStomp(State.WARNING, stompGap, 0L));
            check("a stomp is refused during the spawn frame",
                    !RiftRamPolicy.mayStartStomp(State.SPAWNING, stompGap, 0L));
            check("a stomp is allowed while chasing off cooldown",
                    RiftRamPolicy.mayStartStomp(State.CHASING, stompGap, 0L));
            check("a stomp is allowed while exposed off cooldown",
                    RiftRamPolicy.mayStartStomp(State.EXPOSED, stompGap, 0L));
            check("the stomp cooldown is respected",
                    !RiftRamPolicy.mayStartStomp(State.CHASING, stompGap - 1L, 0L));
            check("the slowness lasts three seconds",
                    RiftRamPolicy.stompSlowTicks() == 60);
            check("the slowness is Slowness II",
                    RiftRamPolicy.STOMP_SLOW_AMPLIFIER == 1);

            // --- Health floor while armoured --------------------------------------
            check("the armoured ram cannot be finished early",
                    RiftRamPolicy.healthFloor(0, 400.0D) > 0.0D);
            check("the floor shrinks with each impact",
                    RiftRamPolicy.healthFloor(0, 400.0D) > RiftRamPolicy.healthFloor(2, 400.0D)
                    && RiftRamPolicy.healthFloor(2, 400.0D) > RiftRamPolicy.healthFloor(4, 400.0D));
            check("the exposed ram has no floor",
                    RiftRamPolicy.healthFloor(5, 400.0D) == 0.0D);
            check("a zero maximum yields no floor",
                    RiftRamPolicy.healthFloor(1, 0.0D) == 0.0D);
            check("a non-finite maximum yields no floor",
                    RiftRamPolicy.healthFloor(1, Double.NaN) == 0.0D);


            // --- Completion ------------------------------------------------------
            check("five impacts alone do not finish the wave",
                    !RiftRamPolicy.isComplete(5, true));
            check("a dead ram without five impacts does not finish the wave",
                    !RiftRamPolicy.isComplete(2, false));
            check("five impacts and a dead ram finish the wave",
                    RiftRamPolicy.isComplete(5, false));

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


def test_owned_displays_move_through_the_internal_permit_path() -> None:
    """A display moved by raw teleport() is cancelled by the wave teleport guard.

    Caught on the local server: every armour section, the pillar and the debris
    were placed with entity.teleport(...), so the guard cancelled all of them and
    logged WAVE_TELEPORT_BLOCKED reason=no-internal-permit once per display per
    tick, while cmstay reported a healthy wave. The armour never left its spawn
    frame and the pillar never rose.

    Behaviour: the encounter must delegate display movement to its host, and the
    host implementation must go through teleportCombatEntity (the permit path).
    """
    encounter = (ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/RiftRamEncounter.java").read_text(encoding="utf-8")
    server = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")

    # No owned presentation entity may be repositioned by a raw teleport call.
    for raw in ("piece.display().teleport(", "d.display().teleport(", " d.teleport(at)"):
        assert raw not in encounter, (
            f"owned displays must not be moved with {raw!r}; the wave guard cancels it")
    assert "host.move(piece.display(), at)" in encounter
    assert "host.move(d.display(), at)" in encounter
    assert "boolean move(Entity entity, Location destination);" in encounter, (
        "the encounter must ask the host to move a display")

    # The adapter's implementation is the permit path, not a bare teleport.
    move = server[server.index("public boolean move(Entity entity, Location destination)"):]
    move = move[: move.index("\n            }")]
    assert "return teleportCombatEntity(entity, destination)" in move, (
        "the host move must preserve acceptance or refusal from the permit path")
    assert ".teleport(" not in move, (
        "the host move must not bypass the permit with a raw teleport")
