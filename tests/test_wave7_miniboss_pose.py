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

            // Only the four wave tokens are known, and nothing else leaks.
            check(Wave7MinibossPosePolicy.isKnown("WAVE_WINDUP"), "WAVE_WINDUP");
            check(Wave7MinibossPosePolicy.isKnown("WAVE_RELEASE"), "WAVE_RELEASE");
            check(Wave7MinibossPosePolicy.isKnown("WAVE_RECOVER"), "WAVE_RECOVER");
            check(Wave7MinibossPosePolicy.isKnown("WAVE_COMBAT"), "WAVE_COMBAT");
            check(!Wave7MinibossPosePolicy.isKnown("WAVE_ATTACK"), "an unknown token is refused");
            check(!Wave7MinibossPosePolicy.isKnown(null), "null is refused");
            check(!Wave7MinibossPosePolicy.isKnown(""), "blank is refused");

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
    assert sent == {"WAVE_COMBAT", "WAVE_WINDUP", "WAVE_RELEASE", "WAVE_RECOVER"}, sent
    accepted = _known_wave_poses()
    missing = sent - accepted
    assert not missing, f"the client would reject {sorted(missing)}"


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
    for token in ("WAVE_WINDUP", "WAVE_RELEASE", "WAVE_RECOVER"):
        assert token in warden, f"the Warden rig ignores {token}"
    # The vanilla flags were dead for a scripted Warden; they must be ORed,
    # never used alone.
    assert "winding || releasing" in warden
    assert "state.attackAnimationState.isStarted()" in warden

    archmage = ARCHMAGE_MODEL.read_text(encoding="utf-8")
    assert "copimine$wavePose()" in archmage
    assert "boolean casting = state.isCastingSpell || winding || releasing;" in archmage
    # The sigil ring is the telegraph; it must be reachable from the pose.
    casting = archmage[archmage.index("if (casting)") :]
    casting = casting[: casting.index("} else if (recovering)")]
    assert "sigilRing.visible = true;" in casting

    skeleton = SKELETON_MODEL.read_text(encoding="utf-8")
    assert '"WAVE_RELEASE".equals(wavePose)' in skeleton
    assert "loosing" in skeleton


def test_no_pose_is_sent_on_every_tick():
    """A per-tick resend would be 40 packets a second per miniboss."""
    plugin = PLUGIN.read_text(encoding="utf-8")
    body = plugin[plugin.index("private void publishWave7Pose(") :]
    body = body[: body.index("\n    }")]
    assert "if (pose == previous) return;" in body
    assert "wave7MinibossPoses.put(actorId, pose);" in body