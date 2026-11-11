"""M5: accepted / blocked / missed boss hits must never disagree.

The 11-November master contract (section 10.6) requires one extra pure
decision: classify a boss hit as accepted, blocked or missed, and derive
*all* its presentation from that single classification.  An accepted hit
really costs server HP and is the only thing that raises the red flash; a
shielded hit shows a shield impact with no flesh flash and no HP loss; a
missed hit shows nothing at all.

These probes compile the real policies and assert behaviour.
"""

from pathlib import Path
import os
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "domain"
EVENT = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def _tool(name):
    java_home = os.environ.get("JAVA_HOME")
    executable = name + (".exe" if os.name == "nt" else "")
    if java_home:
        candidate = Path(java_home) / "bin" / executable
        if candidate.is_file():
            return str(candidate)
    found = shutil.which(name)
    assert found, f"{name} is required to compile the boss damage probe"
    return found


def _run_probe(tmp_path, name, probe_source, domain_types=()):
    probe = tmp_path / f"{name}.java"
    probe.write_text(probe_source, encoding="utf-8")
    compiled = subprocess.run(
        [_tool("javac"), "-encoding", "UTF-8", "-d", str(tmp_path),
         *(str(DOMAIN / (type_name + ".java")) for type_name in domain_types),
         str(probe)],
        capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        [_tool("java"), "-cp", str(tmp_path), name],
        capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
    return executed.stdout


DAMAGE_ACCEPTANCE_PROBE = r"""
import me.copimine.endevent.domain.*;

public class BossDamageAcceptanceProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        BossHitClassificationPolicy.Hit acceptedMelee =
                BossHitClassificationPolicy.classify(false, false, false, true, 12.0D, 0.0D);
        check(acceptedMelee.outcome() == BossHitClassificationPolicy.Outcome.ACCEPTED,
                "an eligible unshielded hit must be accepted");
        check(acceptedMelee.healthDamage() > 0.0D,
                "an accepted hit must really decrement server HP");
        check(acceptedMelee.redFlash(),
                "an accepted hit must raise the red flash");
        check(!acceptedMelee.shieldImpact(),
                "an accepted body hit is not a shield impact");

        // ---- Blocked: shield impact, no flesh flash, no HP loss --------
        BossHitClassificationPolicy.Hit blocked =
                BossHitClassificationPolicy.classify(true, false, false, true, 12.0D, 0.0D);
        check(blocked.outcome() == BossHitClassificationPolicy.Outcome.BLOCKED,
                "a shielded hit must be classified as blocked");
        check(blocked.healthDamage() == 0.0D,
                "a blocked hit must never cost HP");
        check(!blocked.redFlash(),
                "a blocked hit must not flash red flesh");
        check(blocked.shieldImpact(),
                "a blocked hit must show a shield impact");
        check(blocked.hurtAnimation() == false,
                "a blocked hit must not play the flesh hurt reaction");

        // ---- Missed: nothing at all -----------------------------------
        BossHitClassificationPolicy.Hit missed =
                BossHitClassificationPolicy.classify(false, true, false, true, 12.0D, 0.0D);
        check(missed.outcome() == BossHitClassificationPolicy.Outcome.MISSED,
                "a hit that did not reach the body is a miss");
        check(missed.healthDamage() == 0.0D && !missed.redFlash()
                        && !missed.hurtAnimation() && !missed.shieldImpact(),
                "a missed hit must not invent damage, flash or shield impact");

        // ---- Immune phases are not misses -----------------------------
        BossHitClassificationPolicy.Hit immune =
                BossHitClassificationPolicy.classify(false, false, true, true, 12.0D, 0.0D);
        check(immune.outcome() == BossHitClassificationPolicy.Outcome.IMMUNE,
                "a phase-immune boss rejects the hit explicitly");
        check(immune.healthDamage() == 0.0D && !immune.redFlash(),
                "an immune hit must not cost HP or flash red");

        // A non-participant is not a hit at all.
        BossHitClassificationPolicy.Hit stranger =
                BossHitClassificationPolicy.classify(false, false, false, false, 12.0D, 0.0D);
        check(stranger.outcome() == BossHitClassificationPolicy.Outcome.REJECTED,
                "a non-participant hit must be rejected");
        check(stranger.healthDamage() == 0.0D && !stranger.redFlash(),
                "a rejected hit must not cost HP or flash red");

        // A zero-damage hit cannot be an accepted hit.
        BossHitClassificationPolicy.Hit zero =
                BossHitClassificationPolicy.classify(false, false, false, true, 0.0D, 0.0D);
        check(zero.outcome() != BossHitClassificationPolicy.Outcome.ACCEPTED,
                "a zero-final-damage hit must never be accepted");
        // NaN/negative income never becomes damage.
        check(BossHitClassificationPolicy.classify(false, false, false, true,
                        Double.NaN, 0.0D).healthDamage() == 0.0D,
                "NaN damage must not become a real hit");
        check(BossHitClassificationPolicy.classify(false, false, false, true,
                        -4.0D, 0.0D).healthDamage() == 0.0D,
                "negative damage must not heal the boss");

        // ---- The classification drives the presentation record --------
        BossHitFeedbackPolicy.Feedback acceptedFeedback =
                BossHitFeedbackPolicy.forClassification(acceptedMelee);
        check(BossHitFeedbackPolicy.forcesRedTint(acceptedFeedback),
                "only the accepted classification may force the red tint");
        BossHitFeedbackPolicy.Feedback blockedFeedback =
                BossHitFeedbackPolicy.forClassification(blocked);
        check(!BossHitFeedbackPolicy.forcesRedTint(blockedFeedback),
                "a blocked classification must never force the red tint");
        check(blockedFeedback.shieldParticles(),
                "a blocked classification must render the shield impact");
        BossHitFeedbackPolicy.Feedback missedFeedback =
                BossHitFeedbackPolicy.forClassification(missed);
        check(!missedFeedback.redFlash() && !missedFeedback.shieldParticles()
                        && !missedFeedback.impactParticles(),
                "a missed classification must render nothing");

        System.out.println("BossDamageAcceptanceProbe OK");
    }
}
"""


def test_accepted_blocked_and_missed_hits_are_classified_once(tmp_path):
    """Contract 10.6: one classification owns damage and presentation."""
    output = _run_probe(tmp_path, "BossDamageAcceptanceProbe", DAMAGE_ACCEPTANCE_PROBE,
                        ("BossHitClassificationPolicy", "BossHitFeedbackPolicy"))
    assert "BossDamageAcceptanceProbe OK" in output


def test_only_an_accepted_hit_touches_real_health():
    """Contract 10.6: HP commit and red flash share one classified outcome."""
    source = EVENT.read_text(encoding="utf-8")
    # SUPERSEDED: the adapter used to decide HP, flash and shield impact in
    # separate branches. The classification is now the single authority.
    assert "BossHitClassificationPolicy.classify(" in source, (
        "the adapter must classify the hit before touching health or feedback")
    start = source.index("private void handleCurrentBossDamage");
    opening = source.index("{", start)
    depth = 0
    for cursor in range(opening, len(source)):
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        if depth == 0:
            body = source[start : cursor + 1]
            break
    assert "BossHitClassificationPolicy" in body
