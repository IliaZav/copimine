"""Wave 7 miniboss poses: the animation the scripted minibosses never had.

The Warden, the Marksman and the Archmage are all run with native AI disabled:
the plugin resolves every swing, shot and cast itself. That had two
consequences nobody had pinned down in a test:

* `WardenRenderState.attackAnimationState` / `sonicBoomAnimationState` and the
  Evoker's `isCastingSpell` are only set by vanilla AI, so they stayed false for
  the whole trial and the three minibosses rendered as statues that slid around
  the arena;
* the Archmage's sigil ring -- its entire "a spell is coming" telegraph -- is
  gated on that same dead flag, so it was never visible.

The fix drives the pose from the state the fight is already in: the durable cast
receipt for the Archer and the Archmage, the runtime action field for the
Warden. This test pins the mapping as *behaviour* (the policy is compiled and
executed), and then pins the three seams that would silently undo it: the
server publishing, the client accepting the visual id, and each rig consuming
the pose.
"""

import re
import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POLICY = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "domain"
    / "Wave7MinibossPosePolicy.java"
)
PLUGIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"
CLIENT = ROOT / "tools" / "minecraft-26.3" / "client" / "src" / "main" / "java" / "me" / "copimine" / "client"
STATE = CLIENT / "EndEventClientState.java"
WARDEN_MODEL = CLIENT / "Wave7WardenModel.java"
ARCHMAGE_MODEL = CLIENT / "Wave7ArchmageModel.java"
SKELETON_MODEL = CLIENT / "RiftEventSkeletonModel.java"

PROBE = textwrap.dedent(
    """
    import me.copimine.endevent.domain.Wave7MinibossPosePolicy;
    import me.copimine.endevent.domain.Wave7MinibossPosePolicy.Pose;

    public final class PoseProbe {
        private static int failures = 0;

        private static void check(boolean condition, String message) {
            if (!condition) {
                failures++;
                System.out.println("FAIL: " + message);
            }
        }

        public static void main(String[] args) {
            // A cast receipt maps onto exactly one pose at every stage.
            check(Wave7MinibossPosePolicy.castPose(true, false, false) == Pose.WINDUP,
                    "an open telegraph is a windup");
            check(Wave7MinibossPosePolicy.castPose(true, true, false) == Pose.RELEASE,
                    "the resolving tick is a release");
            check(Wave7MinibossPosePolicy.castPose(false, false, true) == Pose.RECOVER,
                    "the cooldown is a recovery");
            check(Wave7MinibossPosePolicy.castPose(false, false, false) == Pose.COMBAT,
                    "an idle miniboss is in combat stance");

            // The Warden's action field is 0 neutral, 1 telegraph, 2 lunge.
            check(Wave7MinibossPosePolicy.wardenPose(1, false) == Pose.WINDUP, "slam telegraph");
            check(Wave7MinibossPosePolicy.wardenPose(2, false) == Pose.RELEASE, "sonic lunge");
            check(Wave7MinibossPosePolicy.wardenPose(0, true) == Pose.RECOVER, "post-attack recovery");
            check(Wave7MinibossPosePolicy.wardenPose(0, false) == Pose.COMBAT, "neutral");
            // The lunge outranks the recovery flag: it is the release.
            check(Wave7MinibossPosePolicy.wardenPose(2, true) == Pose.RELEASE,
                    "a lunge is never reported as a recovery");

            // The release frame is inserted rather than skipped.
            check(Wave7MinibossPosePolicy.insertRelease(Pose.WINDUP, Pose.RECOVER) == Pose.RELEASE,
                    "windup to recovery must pass through the release");
            check(Wave7MinibossPosePolicy.insertRelease(Pose.WINDUP, Pose.COMBAT) == Pose.RELEASE,
                    "windup to combat must pass through the release");
            check(Wave7MinibossPosePolicy.insertRelease(Pose.WINDUP, Pose.RELEASE) == Pose.RELEASE,
                    "an explicit release is not doubled");
            check(Wave7MinibossPosePolicy.insertRelease(Pose.RELEASE, Pose.RECOVER) == Pose.RECOVER,
                    "a release is not repeated");
            check(Wave7MinibossPosePolicy.insertRelease(null, Pose.WINDUP) == Pose.WINDUP,
                    "the first pose is not invented");
            check(Wave7MinibossPosePolicy.insertRelease(Pose.COMBAT, Pose.WINDUP) == Pose.WINDUP,
                    "combat to windup is direct");

            // Leases are bounded: a zero-length or absurd lease would either
            // drop the pose instantly or hold it for the rest of the event.
            check(Wave7MinibossPosePolicy.leaseMillis(Pose.RELEASE, 0L, 0L)
                    == Wave7MinibossPosePolicy.RELEASE_MILLIS, "release lease is its own constant");
            check(Wave7MinibossPosePolicy.leaseMillis(Pose.WINDUP, -500L, 0L)
                    == Wave7MinibossPosePolicy.MIN_LEASE_MILLIS, "a spent deadline floors the lease");
            check(Wave7MinibossPosePolicy.leaseMillis(Pose.WINDUP, 600L, 0L) == 600L,
                    "a windup leases exactly its telegraph");
            check(Wave7MinibossPosePolicy.leaseMillis(Pose.RECOVER, 1_000_000L, 0L)
                    == Wave7MinibossPosePolicy.MAX_LEASE_MILLIS, "a long cooldown is capped");
            check(Wave7MinibossPosePolicy.leaseMillis(Pose.COMBAT, 0L, 0L) >=
                    Wave7MinibossPosePolicy.MIN_LEASE_MILLIS, "combat still gets a lease");

            // Only the wave tokens are known, and nothing else leaks.
            check(Wave7MinibossPosePolicy.isKnown("WAVE_WINDUP"), "WAVE_WINDUP");
            check(Wave7MinibossPosePolicy.isKnown("WAVE_RELEASE"), "WAVE_RELEASE");
            check(Wave7MinibossPosePolicy.isKnown("WAVE_RECOVER"), "WAVE_RECOVER");
            check(Wave7MinibossPosePolicy.isKnown("WAVE_COMBAT"), "WAVE_COMBAT");
            check(Wave7MinibossPosePolicy.isKnown("WAVE_HIT"), "WAVE_HIT");
            check(Wave7MinibossPosePolicy.isKnown("WAVE_DEATH"), "WAVE_DEATH");
            check(!Wave7MinibossPosePolicy.isKnown("WAVE_ATTACK"), "an unknown token is refused");
            check(!Wave7MinibossPosePolicy.isKnown(null), "null is refused");
            check(!Wave7MinibossPosePolicy.isKnown(""), "blank is refused");

            // The hit reaction is a bounded flinch and the death is a bounded
            // collapse. Neither may inherit the combat lease, or a struck
            // miniboss would hold a flinch for a second and a corpse would
            // snap back to combat stance before it is removed.
            check(Wave7MinibossPosePolicy.leaseMillis(Pose.HIT, 60_000L, 0L)
                    == Wave7MinibossPosePolicy.HIT_MILLIS, "a hit keeps its own lease");
            check(Wave7MinibossPosePolicy.leaseMillis(Pose.DEATH, 0L, 0L)
                    == Wave7MinibossPosePolicy.DEATH_MILLIS, "a death keeps its own lease");
            check(Wave7MinibossPosePolicy.HIT_MILLIS >= 150L,
                    "a flinch shorter than three ticks is invisible at 20 TPS");
            check(Wave7MinibossPosePolicy.HIT_MILLIS <= 400L,
                    "a flinch longer than a fifth of a second reads as hit-stun");
            check(Wave7MinibossPosePolicy.DEATH_MILLIS > Wave7MinibossPosePolicy.HIT_MILLIS,
                    "the collapse must outlast the flinch");

            // A hit and a death interrupt: the player caused them, so they are
            // drawn on the first frame instead of after a fabricated release.
            check(Wave7MinibossPosePolicy.insertRelease(Pose.WINDUP, Pose.HIT) == Pose.HIT,
                    "a hit must not wait behind a release frame");
            check(Wave7MinibossPosePolicy.insertRelease(Pose.WINDUP, Pose.DEATH) == Pose.DEATH,
                    "a death must not wait behind a release frame");
            check(Wave7MinibossPosePolicy.insertRelease(Pose.RELEASE, Pose.HIT) == Pose.HIT,
                    "a hit after a release is direct");
            check(Wave7MinibossPosePolicy.insertRelease(Pose.HIT, Pose.COMBAT) == Pose.COMBAT,
                    "recovering from a hit is not a release");

            if (failures == 0) {
                System.out.println("OK");
            } else {
                System.out.println("FAILURES=" + failures);
                System.exit(1);
            }
        }
    }
    """
)


def test_policy_behaviour_in_a_jvm(tmp_path):
    compiled = subprocess.run(
        ["javac", "-d", str(tmp_path), str(POLICY)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stderr
    probe = tmp_path / "PoseProbe.java"
    probe.write_text(PROBE, encoding="utf-8")
    compiled_probe = subprocess.run(
        ["javac", "-cp", str(tmp_path), "-d", str(tmp_path), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled_probe.returncode == 0, compiled_probe.stderr
    ran = subprocess.run(
        ["java", "-cp", str(tmp_path), "PoseProbe"],
        cwd=str(tmp_path),
        capture_output=True,
        text=True,
    )
    assert ran.returncode == 0, ran.stdout + ran.stderr
    assert "OK" in ran.stdout


def _known_wave_poses():
    source = STATE.read_text(encoding="utf-8")
    block = source[source.index("knownWavePose(String phase)") :]
    block = block[: block.index("}")]
    return set(re.findall(r'"([^"]+)"', block))


def test_every_pose_the_server_sends_is_one_the_client_accepts():
    policy = POLICY.read_text(encoding="utf-8")
    sent = set(re.findall(r'[A-Z]+\("(WAVE_[A-Z]+)"\)', policy))
    assert sent == {"WAVE_COMBAT", "WAVE_WINDUP", "WAVE_RELEASE", "WAVE_RECOVER",
                    "WAVE_HIT", "WAVE_DEATH"}, sent
    accepted = _known_wave_poses()
    missing = sent - accepted
    assert not missing, f"the client would reject {sorted(missing)}"


# The client state machine imports only java.util; the classes around it reach
# into Minecraft/Fabric. Compiling the real state against tiny stubs is what
# turns "the allow-list mentions the token" into "the token reaches the rig".
STATE_STUBS = {
    "CopiMineClientLogger.java": """
        package me.copimine.client;
        public final class CopiMineClientLogger {
            public static void warnFromServer(String message) { }
            public static void info(String message) { }
        }
        """,
    "BridgePayload.java": """
        package me.copimine.client;
        public record BridgePayload(String clearPolicy, String shaderpack, String sessionId,
                long seq, String clientVersion, long durationMillis, String mode, String source) { }
        """,
    "EndRiftTentacleModel.java": """
        package me.copimine.client;
        public final class EndRiftTentacleModel {
            public static final String VISUAL_ID = "END_RIFT_TENTACLE_V1";
            public static boolean supportsAnimation(String animationId) { return true; }
        }
        """,
    "EndRiftTentacleAnimator.java": """
        package me.copimine.client;
        public final class EndRiftTentacleAnimator {
            public static long durationTicks(String animationId) { return 0L; }
            public static boolean sameClip(String a, String b) { return a != null && a.equals(b); }
            // Attacks that flow into each other are one continuous clip, so the
            // state machine must not restart the pose at the boundary.
            public static boolean continuousAttackTransition(String a, String b) { return false; }
        }
        """,
    "EndRiftTentaclePose.java": """
        package me.copimine.client;
        public final class EndRiftTentaclePose {
            public record TentaclePose(float rigScale) {
                public static TentaclePose identity() { return new TentaclePose(1.0F); }
                public TentaclePose withRigScale(float scale) { return new TentaclePose(scale); }
                public static TentaclePose lerp(TentaclePose a, TentaclePose b, float t) { return b; }
            }
        }
        """,
    "EndRiftTentacleRenderer.java": """
        package me.copimine.client;
        public final class EndRiftTentacleRenderer {
            public static EndRiftTentaclePose.TentaclePose poseFor(String visualId,
                    String animationId, long elapsedMillis) { return null; }
            public static EndRiftTentaclePose.TentaclePose poseForAt(String visualId,
                    String animationId, long elapsedMillis, long durationMillis, long seed) {
                return null;
            }
        }
        """,
    "EndEventTextureCatalog.java": """
        package me.copimine.client;
        public final class EndEventTextureCatalog {
            public static String textureForVisual(String visual) { return ""; }
            public static boolean isAvailable(String texture) { return false; }
        }
        """,
    "EndermanRendererSelection.java": """
        package me.copimine.client;
        public final class EndermanRendererSelection {
            public static String diagnosticLineForVisual(String visual, String texture,
                    boolean available) { return ""; }
        }
        """,
}

STATE_PROBE = """
    import me.copimine.client.EndEventClientState;
    import me.copimine.client.EndEventPacket;

    public final class StateProbe {
        private static int failures = 0;
        private static void check(boolean ok, String message) {
            if (!ok) { failures++; System.out.println("FAIL: " + message); }
        }
        public static void main(String[] args) {
            EndEventClientState state = new EndEventClientState();
            check(state.apply(new EndEventPacket("END_ENTITY_BIND", "event", 1L, "i", 0L,
                    "warden", "END_RIFT_WAVE7_WARDEN_V1", "", ""), 100L), "the trial actor binds");
            check(state.apply(new EndEventPacket("END_ENTITY_PHASE", "event", 1L, "i", 300L,
                    "warden", "", "WAVE_HIT", ""), 200L), "a hit packet is accepted");
            check("WAVE_HIT".equals(state.wavePoseForEntity("warden", 300L)),
                    "a hit reaches the rig: " + state.wavePoseForEntity("warden", 300L));
            check(state.apply(new EndEventPacket("END_ENTITY_PHASE", "event", 1L, "i", 1500L,
                    "warden", "", "WAVE_DEATH", ""), 1000L), "a death packet is accepted");
            check("WAVE_DEATH".equals(state.wavePoseForEntity("warden", 1500L)),
                    "a death reaches the rig: " + state.wavePoseForEntity("warden", 1500L));
            check("WAVE_COMBAT".equals(state.wavePoseForEntity("warden", 2600L)),
                    "the collapse lease expires instead of holding forever");
            check(!state.apply(new EndEventPacket("END_ENTITY_PHASE", "event", 1L, "i", 300L,
                    "warden", "", "WAVE_ATTACK", ""), 2700L), "an unknown token is refused");
            check("WAVE_COMBAT".equals(state.wavePoseForEntity("warden", 2800L)),
                    "a refused packet does not disturb the pose already showing");
            System.out.println(failures == 0 ? "OK" : "FAILURES=" + failures);
            if (failures != 0) System.exit(1);
        }
    }
    """


def test_the_client_state_machine_really_reaches_the_rig_with_hit_and_death(tmp_path):
    """A allow-list string is not behaviour; the real state must produce the pose."""
    package = tmp_path / "src" / "me" / "copimine" / "client"
    package.mkdir(parents=True)
    protocol = ROOT / "tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol"
    for name in ("EndEventClientState.java", "EndEventPacket.java", "BossAnimationId.java",
                 "RiftRamVisualPolicy.java"):
        (package / name).write_bytes((CLIENT / name).read_bytes())
    for name, body in STATE_STUBS.items():
        (package / name).write_text(textwrap.dedent(body), encoding="utf-8")

    classes = tmp_path / "classes"
    classes.mkdir()
    # The trial and Guardian actor clocks are real protocol records: compile the
    # shipped types so the state machine is proven against the wire format the
    # server actually encodes, not a re-declared stand-in.
    compiled = subprocess.run(
        ["javac", "-d", str(classes)]
        + [str(path) for path in sorted(package.glob("*.java"))]
        + [str(protocol / name) for name in ("BossAnimationPlayback.java",
                                            "Wave7AnimationFrame.java",
                                            "GuardianActorFrame.java")],
        capture_output=True, text=True,
    )
    assert compiled.returncode == 0, compiled.stderr

    probe = tmp_path / "StateProbe.java"
    probe.write_text(textwrap.dedent(STATE_PROBE), encoding="utf-8")
    compiled_probe = subprocess.run(
        ["javac", "-cp", str(classes), "-d", str(classes), str(probe)],
        capture_output=True, text=True,
    )
    assert compiled_probe.returncode == 0, compiled_probe.stderr

    ran = subprocess.run(["java", "-cp", str(classes), "StateProbe"],
                         cwd=str(tmp_path), capture_output=True, text=True)
    assert ran.returncode == 0, ran.stdout + ran.stderr
    assert "OK" in ran.stdout


def test_the_server_publishes_a_hit_and_a_death_from_the_real_events():
    """The two poses the player causes must come from the events that cause them."""
    plugin = PLUGIN.read_text(encoding="utf-8")

    hurt = plugin[plugin.index("public void onNamedWave7TrialActorHurt(") :]
    hurt = hurt[: hurt.index("\n    }")]
    # Accepted damage only: at MONITOR a canceled or refused hit never arrives.
    assert "EventPriority.MONITOR" in plugin[plugin.index("onNamedWave7TrialActorHurt(") - 200 :
                                              plugin.index("onNamedWave7TrialActorHurt(")]
    assert "isNamedWave7TrialActor(entity)" in hurt
    assert "Wave7MinibossPosePolicy.Pose.HIT" in hurt
    # A killing blow is the death pose, not a flinch that swallows it.
    assert "living.getHealth() - event.getFinalDamage() <= 0.0D" in hurt

    death = plugin[plugin.index("public void onOwnedEntityDeath(") :]
    death = death[: death.index("\n    }")]
    assert "isNamedWave7TrialActor(entity)" in death
    assert "Wave7MinibossPosePolicy.Pose.DEATH" in death

    # ...and the presentation handler never mutates the combat transaction.
    for forbidden in ("setDamage", "setCancelled", "setHealth"):
        assert forbidden not in hurt, f"the hit pose must not touch {forbidden}"


def test_the_client_accepts_the_wave7_visual_ids():
    """The allow-list is the gate that made every earlier attempt a no-op."""
    state = STATE.read_text(encoding="utf-8")
    block = state[state.index("wavePoseVisual(String visual)") :]
    block = block[: block.index(".contains(visual)")]
    for visual in ("END_RIFT_WAVE7_WARDEN_V1", "END_RIFT_WAVE7_ARCHMAGE_V1", "END_RIFT_WAVE7_MARKSMAN_V1"):
        assert visual in block, f"{visual} is not allowed to animate"
    # ...and they are the ids the server actually binds.
    plugin = PLUGIN.read_text(encoding="utf-8")
    for constant in ("CLIENT_VISUAL_WAVE7_WARDEN", "CLIENT_VISUAL_WAVE7_ARCHMAGE", "CLIENT_VISUAL_WAVE7_MARKSMAN"):
        assert constant in plugin, constant


def test_the_server_publishes_the_pose_from_the_same_record_it_damages_from():
    plugin = PLUGIN.read_text(encoding="utf-8")
    assert "import me.copimine.endevent.domain.Wave7MinibossPosePolicy;" in plugin
    # The cast pose is read back from the receipt after the tick wrote it,
    # so the arms cannot disagree with the attack that just resolved.
    assert "publishWave7CastPose(living, trial);" in plugin
    assert "readNamedTrialActorCheckpoint(actor, trial)" in plugin
    assert "Wave7MinibossPosePolicy.castPose(" in plugin
    # The Warden's pose is derived from its action field.
    assert "publishWave7WardenPose(living, wardenState);" in plugin
    assert "Wave7MinibossPosePolicy.wardenPose(" in plugin
    # It actually reaches the wire, through the existing sender.
    assert "sendWaveMobPose(actor, pose.wire()" in plugin
    # ...and it is retired with the bars, so no pose outlives its trial.
    assert "wave7MinibossPoses.clear();" in plugin


def test_each_rig_consumes_the_pose():
    warden = WARDEN_MODEL.read_text(encoding="utf-8")
    assert "copimine$wavePose()" in warden
    for token in ("WAVE_WINDUP", "WAVE_RELEASE", "WAVE_RECOVER", "WAVE_HIT", "WAVE_DEATH"):
        assert token in warden, f"the Warden rig ignores {token}"
    # The vanilla flags were dead for a scripted Warden; they must be ORed,
    # never used alone.
    assert "winding || releasing" in warden
    assert "state.attackAnimationState.isStarted()" in warden

    archmage = ARCHMAGE_MODEL.read_text(encoding="utf-8")
    assert "copimine$wavePose()" in archmage
    assert "boolean casting = state.isCastingSpell || winding || releasing;" in archmage
    for token in ("WAVE_HIT", "WAVE_DEATH"):
        assert token in archmage, f"the Archmage rig ignores {token}"
    # The sigil ring is the telegraph; it must be reachable from the pose.
    casting = archmage[archmage.index("if (casting)") :]
    casting = casting[: casting.index("} else if (recovering)")]
    assert "sigilRing.visible = true;" in casting

    skeleton = SKELETON_MODEL.read_text(encoding="utf-8")
    assert '"WAVE_RELEASE".equals(wavePose)' in skeleton
    assert "loosing" in skeleton
    for token in ("WAVE_HIT", "WAVE_DEATH"):
        assert token in skeleton, f"the Marksman rig ignores {token}"


def test_the_hit_and_death_reactions_are_drawn_not_just_accepted():
    """A token the rigs only read is not a reaction; each branch must pose."""
    warden = WARDEN_MODEL.read_text(encoding="utf-8")
    dying = warden[warden.index("if (dying) {") :]
    dying = dying[: dying.index("} else if (hit)")]
    assert "body.xRot += 0.88F" in dying
    assert "riftCore.yScale = 0.40F" in dying
    hit = warden[warden.index("} else if (hit) {") :]
    hit = hit[: hit.index("} else if (winding)")]
    assert "body.xRot -= 0.16F" in hit
    assert "riftCore.yScale *= 0.72F" in hit

    archmage = ARCHMAGE_MODEL.read_text(encoding="utf-8")
    dying = archmage[archmage.index("if (dying) {") :]
    dying = dying[: dying.index("} else if (hit)")]
    assert "body.xRot = 0.92F" in dying
    assert "focusOrb.visible = false;" in dying
    hit = archmage[archmage.index("} else if (hit) {") :]
    hit = hit[: hit.index("} else if (casting)")]
    assert "focusOrb.yScale = 0.55F" in hit

    skeleton = SKELETON_MODEL.read_text(encoding="utf-8")
    dying = skeleton[skeleton.index("if (dying) {") :]
    dying = dying[: dying.index("} else if (hit)")]
    assert "body.xRot = 0.78F" in dying
    assert "marksmanSight.xRot = 0.30F" in dying
    hit = skeleton[skeleton.index("} else if (hit) {") :]
    hit = hit[: hit.index("} else if (drawing)")]
    assert "body.xRot -= 0.12F" in hit


def test_no_pose_is_sent_on_every_tick():
    """A per-tick resend would be 40 packets a second per miniboss."""
    plugin = PLUGIN.read_text(encoding="utf-8")
    body = plugin[plugin.index("private void publishWave7Pose(") :]
    body = body[: body.index("\n    }")]
    # A repeated pose is refused for as long as its own lease is still running:
    # an idle tick therefore costs nothing, while a pose that is still live
    # keeps its remaining time on the rig instead of being re-stamped.
    repeat_guard = "if (pose == previous && now < wave7PoseLeaseEnds.getOrDefault(actorId, 0L)) return;"
    assert repeat_guard in body, body
    # The guard has to sit before the send, or the packet is already emitted;
    # and the method may only carry one send site at all.
    assert body.index(repeat_guard) < body.index("sendWaveMobPose(")
    assert body.count("sendWaveMobPose(") == 1
    assert "wave7MinibossPoses.put(actorId, pose);" in body
    # The ledger is written only after the guards, so a refused pose cannot
    # overwrite the pose that is actually being drawn.
    assert body.index("wave7MinibossPoses.put(actorId, pose);") > body.index(repeat_guard)
    # A death is terminal: no later pose may replace the collapse...
    assert "if (previous == Wave7MinibossPosePolicy.Pose.DEATH) return;" in body
    # ...and a flinch or release holds its own lease against a downgrade, so
    # the player always sees the reaction they caused.
    assert "previous == Wave7MinibossPosePolicy.Pose.HIT || previous == Wave7MinibossPosePolicy.Pose.RELEASE" in body
    assert "now < wave7PoseLeaseEnds.getOrDefault(actorId, 0L)) return;" in body