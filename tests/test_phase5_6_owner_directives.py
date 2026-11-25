"""Owner directives after the Phase 4 increment:

* Phase 4 transition cinematic (3-6 s staged transformation).
* Phase 4 music actually enabled, and Phase 6 music no longer lost to the
  `LAST_SEAL` -> `last_seal` spelling bug.
* Splash/critical damage feedback on the boss, with the red flash preserved.
* The Phase 6 dramatic boss bar (breathing accent + tentacle coil).

The probes compile the real Bukkit-free domain policies. The client HUD cannot
be compiled here (it imports Minecraft), so its animation helpers are checked by
source and by direct arithmetic mirrors.
"""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
CLIENT_HUD = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/EndRiftBossBarHud.java"

SOURCES = [
    "BossPhase",
    "BossHitClassificationPolicy",
    "BossHitFeedbackPolicy",
    "BossHitFlavorPolicy",
    "BossPhaseMusicPolicy",
    "InfernoTransitionCinematicPolicy",
    "EndRiftAiPolicy",
    "PlayerEchoCopyPolicy",
]


def compile_and_run(tmp_path, name, body):
    probe = tmp_path / (name + ".java")
    probe.write_text(body, encoding="utf-8")
    files = [DOMAIN / (stem + ".java") for stem in SOURCES]
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, files), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        ["java", "-cp", str(tmp_path), name], capture_output=True, text=True
    )
    assert executed.returncode == 0, executed.stdout + executed.stderr
    return executed.stdout


def test_phase_music_keys_are_explicit_and_phase_four_is_enabled(tmp_path):
    output = compile_and_run(
        tmp_path,
        "PhaseMusicProbe",
        """
import me.copimine.endevent.domain.*;
import me.copimine.endevent.domain.BossPhase;
import java.util.*;

public class PhaseMusicProbe {
    static int checks = 0;
    static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // Every phase owns a real, distinct config key.
        Set<String> keys = new HashSet<>();
        for (BossPhase phase : BossPhase.values()) {
            String key = BossPhaseMusicPolicy.musicKey(phase);
            require(key.startsWith("boss-"), "phase key is namespaced: " + key);
            require(keys.add(key), "phase keys are distinct: " + key);
        }
        require(keys.size() == BossPhase.values().length, "one key per phase");

        // The exact owner-requested keys, including the Phase 4 track.
        require(BossPhaseMusicPolicy.musicKey(BossPhase.OVERLOAD).equals("boss-overload"),
                "Phase 4 (OVERLOAD) owns boss-overload");
        require(BossPhaseMusicPolicy.musicKey(BossPhase.LAST_SEAL).equals("boss-last-seal"),
                "Phase 6 (LAST_SEAL) owns boss-last-seal, not boss-last_seal");
        require(BossPhaseMusicPolicy.musicKey(BossPhase.RIFT).equals("boss-rift"), "RIFT key");
        require(BossPhaseMusicPolicy.musicKey(BossPhase.RAGE).equals("boss-rage"), "RAGE key");

        // Packet spellings that must still resolve (no stranded finale).
        require(BossPhaseMusicPolicy.musicKey("LAST_SEAL").equals("boss-last-seal"),
                "enum spelling resolves");
        require(BossPhaseMusicPolicy.musicKey("last-seal").equals("boss-last-seal"),
                "kebab spelling resolves");
        require(BossPhaseMusicPolicy.musicKey("last_seal").equals("boss-last-seal"),
                "snake spelling resolves");
        require(BossPhaseMusicPolicy.musicKey((String) null).equals("boss-awakening"),
                "null falls back safely");
        require(BossPhaseMusicPolicy.isPhaseFour(BossPhase.OVERLOAD), "OVERLOAD is Phase 4");
        require(!BossPhaseMusicPolicy.isPhaseFour(BossPhase.RAGE), "RAGE is not Phase 4");

        System.out.println("PASS phase music kept " + checks + " checks");
    }
}
""",
    )
    assert "PASS phase music" in output


def test_phase_four_transition_cinematic_is_staged_and_bounded(tmp_path):
    output = compile_and_run(
        tmp_path,
        "InfernoTransitionProbe",
        """
import me.copimine.endevent.domain.InfernoTransitionCinematicPolicy;
import me.copimine.endevent.domain.InfernoTransitionCinematicPolicy.Stage;

public class InfernoTransitionProbe {
    static int checks = 0;
    static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // V6 asks for a 3-6 second transformation.
        long millis = InfernoTransitionCinematicPolicy.durationMillis();
        require(millis >= 3_000L && millis <= 6_000L, "3-6 second cinematic: " + millis);

        // Ordered, non-skipping staging.
        require(InfernoTransitionCinematicPolicy.stageFor(0) == Stage.GATHER, "starts in gather");
        require(InfernoTransitionCinematicPolicy.stageFor(
                        InfernoTransitionCinematicPolicy.GATHER_TICKS) == Stage.LEVITATE,
                "then shards levitate");
        require(InfernoTransitionCinematicPolicy.stageFor(
                        InfernoTransitionCinematicPolicy.GATHER_TICKS
                                + InfernoTransitionCinematicPolicy.LEVITATE_TICKS) == Stage.VORTEX,
                "then the overhead vortex");
        require(InfernoTransitionCinematicPolicy.stageFor(
                        InfernoTransitionCinematicPolicy.releaseTick()) == Stage.RELEASE,
                "then one release");
        require(InfernoTransitionCinematicPolicy.stageFor(
                        InfernoTransitionCinematicPolicy.TOTAL_TICKS) == Stage.ENDED,
                "then the scene ends");

        // The release fires on exactly one tick.
        int releases = 0;
        for (int tick = 0; tick <= InfernoTransitionCinematicPolicy.TOTAL_TICKS + 4; tick++) {
            if (InfernoTransitionCinematicPolicy.isReleaseTick(tick)) releases++;
        }
        require(releases == 1, "the pressure release is exactly once");
        require(InfernoTransitionCinematicPolicy.isReleaseTick(
                        InfernoTransitionCinematicPolicy.releaseTick()),
                "the announced tick is the release");

        // The vortex rises and is bounded; shards never exceed their cap.
        double low = InfernoTransitionCinematicPolicy.vortexHeight(
                InfernoTransitionCinematicPolicy.GATHER_TICKS);
        double high = InfernoTransitionCinematicPolicy.vortexHeight(
                InfernoTransitionCinematicPolicy.TOTAL_TICKS - 1);
        require(high > low && low >= 0.0D, "the vortex rises over the scene");
        require(InfernoTransitionCinematicPolicy.vortexHeight(0) == 0.0D,
                "no vortex before the transformation");
        for (int tick = 0; tick < InfernoTransitionCinematicPolicy.TOTAL_TICKS; tick++) {
            int shards = InfernoTransitionCinematicPolicy.visibleShardCount(tick);
            require(shards >= 0
                            && shards <= InfernoTransitionCinematicPolicy.SHARD_COUNT,
                    "shard count is bounded");
        }
        require(InfernoTransitionCinematicPolicy.beats().size() == 4, "four staging beats");

        System.out.println("PASS inferno transition kept " + checks + " checks");
    }
}
""",
    )
    assert "PASS inferno transition" in output


def test_hit_flavour_classifies_criticals_and_splashes(tmp_path):
    output = compile_and_run(
        tmp_path,
        "HitFlavorProbe",
        """
import me.copimine.endevent.domain.BossHitFlavorPolicy;
import me.copimine.endevent.domain.BossHitFlavorPolicy.Flavor;
import me.copimine.endevent.domain.BossHitClassificationPolicy;
import me.copimine.endevent.domain.BossHitFeedbackPolicy;

public class HitFlavorProbe {
    static int checks = 0;
    static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Flavor none = BossHitFlavorPolicy.none();
        require(!none.critical() && !none.splash(), "a plain hit is neither");

        // Melee flavour follows the real attacker state.
        require(BossHitFlavorPolicy.melee(true, false).critical(), "a falling strike is a crit");
        require(BossHitFlavorPolicy.melee(false, false).critical() == false, "grounded is plain");
        require(BossHitFlavorPolicy.melee(false, true).splash(), "a sweep is splash");
        require(!BossHitFlavorPolicy.melee(true, true).critical(),
                "a sweep is never a crit even while falling");

        // Projectile flavour follows the real projectile state.
        require(BossHitFlavorPolicy.projectile(true, false).critical(), "a crit arrow is a crit");
        require(BossHitFlavorPolicy.projectile(false, true).splash(), "an area projectile is splash");
        require(!BossHitFlavorPolicy.projectile(false, false).critical(), "a plain arrow is plain");

        // Presentation is bounded.
        require(BossHitFlavorPolicy.extraSparks(none) == 0, "plain hits add no sparks");
        require(BossHitFlavorPolicy.extraSparks(BossHitFlavorPolicy.melee(true, false)) <= 20,
                "crit sparks are bounded");
        require(BossHitFlavorPolicy.splashRadius(none) == 0.0D, "plain hits have no ring");
        require(BossHitFlavorPolicy.splashRadius(BossHitFlavorPolicy.projectile(false, true)) > 0.0D,
                "a splash gets a real ring");

        // The accepted feedback keeps the red flash and encodes the flavour.
        BossHitClassificationPolicy.Hit accepted = BossHitClassificationPolicy.classify(
                false, false, false, true, 12.0D, 1.0D);
        BossHitFeedbackPolicy.Feedback crit = BossHitFeedbackPolicy.forClassification(accepted, true, false);
        require(crit.outcome() == BossHitFeedbackPolicy.Outcome.ACCEPTED_MELEE,
                "a crit is still an accepted hit");
        require(crit.critical() && !crit.splash(), "the crit flag is carried");
        require(crit.redFlash(), "a crit still flashes red");
        require(BossHitFeedbackPolicy.forcesRedTint(crit), "the red tint is forced for a crit");
        require(crit.soundCue().equals("ENTITY_PLAYER_ATTACK_CRIT"), "crit has its own cue");

        BossHitFeedbackPolicy.Feedback splash = BossHitFeedbackPolicy.forClassification(accepted, false, true);
        require(splash.splash() && !splash.critical(), "the splash flag is carried");
        require(splash.redFlash(), "a splash still flashes red");
        require(splash.physicalRecoilHorizontal()
                        < BossHitFeedbackPolicy.forAcceptedHit(false).physicalRecoilHorizontal(),
                "a splash shoves the body less than a focused blow");

        // A blocked hit is never presented as a crit/splash.
        BossHitClassificationPolicy.Hit blocked = BossHitClassificationPolicy.classify(
                true, false, false, true, 12.0D, 1.0D);
        BossHitFeedbackPolicy.Feedback blockedFeedback =
                BossHitFeedbackPolicy.forClassification(blocked, true, true);
        require(!blockedFeedback.redFlash(), "a blocked hit never flashes flesh red");
        require(!blockedFeedback.critical() && !blockedFeedback.splash(),
                "a blocked hit is not a crit or splash");

        // A missed/rejected hit stays silent even if flagged.
        BossHitClassificationPolicy.Hit missed = BossHitClassificationPolicy.classify(
                false, true, false, true, 12.0D, 1.0D);
        BossHitFeedbackPolicy.Feedback missedFeedback =
                BossHitFeedbackPolicy.forClassification(missed, true, true);
        require(!missedFeedback.redFlash() && !missedFeedback.impactParticles(),
                "a missed hit renders nothing");

        System.out.println("PASS hit flavour kept " + checks + " checks");
    }
}
""",
    )
    assert "PASS hit flavour" in output


def test_server_wiring_for_the_new_owner_directives():
    source = MONOLITH.read_text(encoding="utf-8")
    # Phase 4 music and the LAST_SEAL spelling fix.
    assert "import me.copimine.endevent.domain.BossPhaseMusicPolicy;" in source
    assert "config.phaseMusic(BossPhaseMusicPolicy.musicKey(bossPhase))" in source
    # Phase 4 transition cinematic and fiery body.
    assert "import me.copimine.endevent.domain.InfernoTransitionCinematicPolicy;" in source
    assert "private void startInfernoTransitionCinematic(" in source
    assert "InfernoTransitionCinematicPolicy.isReleaseTick(" in source
    assert "private void renderOverloadBossEmbers(" in source
    assert "renderOverloadBossEmbers(boss);" in source
    # Splash / critical presentation.
    assert "import me.copimine.endevent.domain.BossHitFlavorPolicy;" in source
    assert "private BossHitFlavorPolicy.Flavor meleeHitFlavor(" in source
    assert "private BossHitFlavorPolicy.Flavor projectileHitFlavor(" in source
    assert "BossHitFeedbackPolicy.forClassification(hit," in source
    assert "BossHitFeedbackPolicy.forAcceptedHit(true," in source
    assert "case \"ENTITY_PLAYER_ATTACK_CRIT\" -> Sound.ENTITY_PLAYER_ATTACK_CRIT;" in source
    assert "Particle.SWEEP_ATTACK, ring, 1," in source


def test_client_boss_bar_has_the_phase_six_coil():
    source = CLIENT_HUD.read_text(encoding="utf-8")
    assert "drawLastSealAccents(context, x, y, now, reduced, phaseColor);" in source
    assert "private static void drawLastSealAccents(" in source
    assert "static float lastSealPulse(" in source
    assert "static int coilOffset(" in source
    assert "COIL_PERIOD_MILLIS" in source
    assert "LAST_SEAL_PULSE_MILLIS" in source
    # Reduced motion must suppress the whole accent.
    assert "if (reduced) {\n            return;\n        }" in source
    # Phase 6 is the LAST_SEAL index in the authored atlas.
    assert 'case "LAST_SEAL" -> 5;' in source


def test_phase5_player_echo_copies_reproduce_victim_and_chest_beam(tmp_path):
    output = compile_and_run(
        tmp_path,
        "PlayerEchoProbe",
        """
import me.copimine.endevent.domain.PlayerEchoCopyPolicy;

public class PlayerEchoProbe {
    static int checks = 0;
    static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // V6 963: a copy reproduces the victim's own action family.
        require(PlayerEchoCopyPolicy.familyForPlayerAction("MELEE").equals("MELEE_SWIPE"),
                "a melee victim is echoed as a swipe");
        require(PlayerEchoCopyPolicy.familyForPlayerAction("RANGED").equals("CHEST_STRIKE"),
                "a ranged victim is echoed as a chest strike");
        require(PlayerEchoCopyPolicy.familyForPlayerAction("AREA").equals("GROUND_SLAM"),
                "an area victim is echoed as a ground slam");
        require(PlayerEchoCopyPolicy.familyForPlayerAction(null).equals("MELEE_SWIPE"),
                "a null action still echoes a real family");
        require(PlayerEchoCopyPolicy.familyForPlayerAction("wat").equals("MELEE_SWIPE"),
                "an unknown action still echoes a real family");

        require(PlayerEchoCopyPolicy.isSemiTransparent(), "copies are semi-transparent");
        require(PlayerEchoCopyPolicy.copiesEquipment(), "copies mirror equipment");
        require(!PlayerEchoCopyPolicy.usesGlobalSpells(), "copies do not use global spells");

        double solo = PlayerEchoCopyPolicy.chestBeamChance(1);
        double raid = PlayerEchoCopyPolicy.chestBeamChance(20);
        require(solo >= raid, "solo sees the beam at least as often as a raid");
        require(raid > 0.0D && solo < 1.0D, "chance is strictly between 0 and 1");
        for (int players = 0; players <= 25; players++) {
            double chance = PlayerEchoCopyPolicy.chestBeamChance(players);
            require(chance > 0.0D && chance < 1.0D, "clamped chance: " + players);
        }
        require(PlayerEchoCopyPolicy.rollsChestBeam(1, 0.0D), "roll 0 always beams when solo");
        require(!PlayerEchoCopyPolicy.rollsChestBeam(20, 0.99D), "a high roll never beams at raid size");
        require(!PlayerEchoCopyPolicy.rollsChestBeam(5, Double.NaN), "a non-finite roll never beams");

        require(PlayerEchoCopyPolicy.chooseFamily("MELEE", 1, 0.0D).equals("CHEST_STRIKE"),
                "a low roll is the Guardian chest beam");
        require(PlayerEchoCopyPolicy.chooseFamily("MELEE", 20, 0.99D).equals("MELEE_SWIPE"),
                "a raid melee victim echoes its own swipe");
        require(PlayerEchoCopyPolicy.chooseFamily("AREA", 20, 0.99D).equals("GROUND_SLAM"),
                "a raid area victim echoes a ground slam");

        require(PlayerEchoCopyPolicy.describe(4, "MELEE", "MELEE_SWIPE", 0.5D)
                .startsWith("PLAYER_ECHO_COPY"), "diagnostic label is stable");

        System.out.println("PASS player echo kept " + checks + " checks");
    }
}
""",
    )
    assert "PASS player echo" in output


def test_boss_bar_coil_arithmetic_is_bounded():
    """Mirror the pure client helpers: no dependency on Minecraft is needed."""
    import math

    period = 2600
    segments = 26
    for now in (0, 1, 999, 1300, 2599, 2600, 123456):
        for segment in range(segments):
            travel = (now % period) / period
            t = segment / max(1, segments - 1)
            offset = round(math.sin((t * 2.0 + travel) * math.pi * 2.0) * 3.0)
            assert -3 <= offset <= 3
    for now in (0, 449, 900, 1350, 1799, 1800, 54321):
        phase = (now % 1800) / 1800.0
        pulse = 0.5 + 0.5 * math.sin(phase * math.pi * 2.0)
        assert 0.0 - 1e-9 <= pulse <= 1.0 + 1e-9
