"""Spell presentation is the visible half of the wave 6 caster fight.

Complaint (10) covers "beaten magi cast a strengthened sphere spell", "the sphere casts on its
own", "rich spell animations" and "the HUD inside the sphere". RitualSpellPresentationPolicy
decides which server beams become world glyphs, which become a screen cue, when a glyph is
allowed to fill the victim's camera, and where the HUD panel may sit. It had no behavioural
test - only the artifact check that the class ships in the jar.

The class is plain Java except for three types, which the probe supplies as stubs:
EndEventWorldVfxManager.BeamSnapshot, PrisonerHudLayout.Rect and WaveCombatPresentationPolicy.
"""
from __future__ import annotations

import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_SRC = ROOT / "tools/minecraft-26.3/client/src/main/java"
POLICY = CLIENT_SRC / "me/copimine/client/RitualSpellPresentationPolicy.java"

STUB_VEC3 = "package me.copimine.client;\n\npublic record Vec3(double x, double y, double z) { }\n"

STUB_HUD_LAYOUT = textwrap.dedent(
    """
    package me.copimine.client;

    public final class PrisonerHudLayout {
        private PrisonerHudLayout() { }

        public record Rect(int left, int top, int right, int bottom) {
            public boolean intersects(Rect other) {
                if (other == null) return false;
                return left < other.right && other.left < right && top < other.bottom && other.top < bottom;
            }
        }
    }
    """
)

STUB_VFX = textwrap.dedent(
    """
    package me.copimine.client;

    public final class EndEventWorldVfxManager {
        private EndEventWorldVfxManager() { }

        public record BeamSnapshot(String instanceId, String dimension, Vec3 start,
                                   Vec3 end, int color, float width,
                                   long startedAtMillis, long expiresAtMillis) { }
    }
    """
)

STUB_COMBAT_POLICY = textwrap.dedent(
    """
    package me.copimine.client;

    /** The real class owns wave combat cues; the spell policy only asks whether one exists. */
    public final class WaveCombatPresentationPolicy {
        private WaveCombatPresentationPolicy() { }

        public record Cue(String instanceId) { }

        public static Cue parse(String instance) {
            if (instance == null) return null;
            int marker = instance.indexOf(":world:");
            if (marker <= 0) return null;
            return instance.substring(marker + 7).startsWith("wave-ai-") ? new Cue(instance) : null;
        }
    }
    """
)

PROBE = textwrap.dedent(
    """
    package me.copimine.client;

    import java.util.ArrayList;
    import java.util.List;
    import java.util.function.Consumer;

    public final class SpellPresentationProbe {
        private static int failures = 0;

        private static void check(String name, boolean condition) {
            if (!condition) {
                System.out.println("FAIL " + name);
                failures++;
            }
        }

        private static String instance(String key) {
            return "minecraft:the_end:world:" + key;
        }

        public static void main(String[] args) {
            // --- Parsing is exact and allowlisted -------------------------------------------
            RitualSpellPresentationPolicy.Cue barrage = RitualSpellPresentationPolicy.parse(
                    instance("wave6-spell-glyph-RIFT_BARRAGE-warning"));
            check("glyph cue parses", barrage != null);
            check("glyph channel", barrage.channel() == RitualSpellPresentationPolicy.Channel.GLYPH);
            check("warning stage", barrage.stage() == RitualSpellPresentationPolicy.Stage.WARNING);
            check("glyph spell", barrage.spell() == RitualSpellPresentationPolicy.Spell.RIFT_BARRAGE);

            check("screen cue parses", RitualSpellPresentationPolicy.parse(
                    instance("wave6-spell-screen-SOUL_BRAND-active")) != null);

            // Every spell and stage resolves; an unauthored name does not.
            int resolved = 0;
            for (RitualSpellPresentationPolicy.Spell spell : RitualSpellPresentationPolicy.Spell.values()) {
                for (RitualSpellPresentationPolicy.Stage stage : RitualSpellPresentationPolicy.Stage.values()) {
                    if (RitualSpellPresentationPolicy.parse(
                            instance("wave6-spell-glyph-" + spell.name() + "-"
                                    + (stage == RitualSpellPresentationPolicy.Stage.WARNING
                                        ? "warning" : "active"))) != null) resolved++;
                }
            }
            check("every spell resolves on both stages",
                    resolved == RitualSpellPresentationPolicy.Spell.values().length * 2);

            check("unknown spell refused", RitualSpellPresentationPolicy.parse(
                    instance("wave6-spell-glyph-NOT_A_SPELL-active")) == null);
            check("lowercase refused", RitualSpellPresentationPolicy.parse(
                    instance("wave6-spell-glyph-rift_barrage-active")) == null);
            check("missing stage refused", RitualSpellPresentationPolicy.parse(
                    instance("wave6-spell-glyph-RIFT_BARRAGE")) == null);
            check("null refused", RitualSpellPresentationPolicy.parse(null) == null);
            check("no world segment refused",
                    RitualSpellPresentationPolicy.parse("wave6-spell-glyph-RIFT_BARRAGE-active") == null);
            // Two ":world:" markers is ambiguous, so it must be refused rather than guessed.
            //
            // Honest note: the class's second-marker guard is belt-and-braces, not load-bearing.
            // worldKey takes everything after the FIRST marker, so any payload carrying a second
            // marker also carries that text into the key, and no key can contain ":world:" and
            // still equal an authored suffix. Both cases below therefore pass with or without
            // the guard - verified by mutation. They are kept as behaviour assertions, not as
            // proof that the guard is exercised.
            check("ambiguous instance refused", RitualSpellPresentationPolicy.parse(
                    "minecraft:the_end:world:x:world:wave6-spell-glyph-RIFT_BARRAGE-active") == null);
            check("a trailing second marker is refused", RitualSpellPresentationPolicy.parse(
                    "minecraft:the_end:world:wave6-spell-glyph-RIFT_BARRAGE-active:world:") == null);

            // --- Glyph visibility ------------------------------------------------------------
            check("bystander sees a billboard glyph",
                    RitualSpellPresentationPolicy.glyphVisibleFrom(barrage, 0.01));
            RitualSpellPresentationPolicy.Cue brand = RitualSpellPresentationPolicy.parse(
                    instance("wave6-spell-glyph-SOUL_BRAND-active"));
            check("victim is spared the billboard",
                    !RitualSpellPresentationPolicy.glyphVisibleFrom(brand, 1.0));
            check("victim sees it once far enough",
                    RitualSpellPresentationPolicy.glyphVisibleFrom(brand, 2.25));
            check("wrong channel never glyphs", !RitualSpellPresentationPolicy.glyphVisibleFrom(
                    RitualSpellPresentationPolicy.parse(instance("wave6-spell-screen-SOUL_BRAND-active")), 10.0));
            check("nan distance refused",
                    !RitualSpellPresentationPolicy.glyphVisibleFrom(barrage, Double.NaN));
            check("negative distance refused",
                    !RitualSpellPresentationPolicy.glyphVisibleFrom(barrage, -1.0));

            // --- Glyph quad --------------------------------------------------------------
            List<RitualSpellPresentationPolicy.Point> flat =
                    RitualSpellPresentationPolicy.glyphQuad(barrage);
            check("quad has four corners", flat.size() == 4);
            float radius = RitualSpellPresentationPolicy.Spell.RIFT_BARRAGE.diameter() / 2;
            check("quad spans the authored diameter",
                    Math.abs(flat.get(0).x() + radius) < 1.0E-6 && Math.abs(flat.get(2).x() - radius) < 1.0E-6);
            check("horizontal quad is flat",
                    flat.stream().allMatch(p -> p.y() == 0.0F));
            List<RitualSpellPresentationPolicy.Point> billboard =
                    RitualSpellPresentationPolicy.glyphQuad(brand);
            check("billboard quad stands up",
                    billboard.stream().allMatch(p -> p.z() == 0.0F));
            check("billboard radius matches its own diameter",
                    Math.abs(billboard.get(0).y()
                            + RitualSpellPresentationPolicy.Spell.SOUL_BRAND.diameter() / 2) < 1.0E-6);

            // --- Alpha ramps --------------------------------------------------------------
            check("alpha is zero before the cue starts",
                    RitualSpellPresentationPolicy.glyphAlpha(barrage, 1000, 2000, 999) == 0);
            check("alpha is zero after it expires",
                    RitualSpellPresentationPolicy.glyphAlpha(barrage, 1000, 2000, 2000) == 0);
            check("alpha is zero for a degenerate window",
                    RitualSpellPresentationPolicy.glyphAlpha(barrage, 1000, 1000, 1000) == 0);
            int midGlyph = RitualSpellPresentationPolicy.glyphAlpha(barrage, 1000, 3000, 2000);
            check("glyph alpha is visible mid-cue", midGlyph > 0 && midGlyph <= 210);
            check("active glyph outshines a warning glyph",
                    RitualSpellPresentationPolicy.glyphAlpha(
                            RitualSpellPresentationPolicy.parse(instance("wave6-spell-glyph-SOUL_BRAND-active")),
                            1000, 3000, 2000)
                    > RitualSpellPresentationPolicy.glyphAlpha(
                            RitualSpellPresentationPolicy.parse(instance("wave6-spell-glyph-SOUL_BRAND-warning")),
                            1000, 3000, 2000));
            int midScreen = RitualSpellPresentationPolicy.screenAlpha(
                    RitualSpellPresentationPolicy.Stage.ACTIVE, 1000, 3000, 2000);
            check("screen tint stays a tint, never a wall",
                    midScreen > 0 && midScreen < 64);

            // --- World pass dispatch -------------------------------------------------------
            List<EndEventWorldVfxManager.BeamSnapshot> beams = new ArrayList<>();
            beams.add(new EndEventWorldVfxManager.BeamSnapshot(
                    instance("wave6-spell-glyph-RIFT_BARRAGE-active"), "minecraft:the_end",
                    null, null, 0, 1.0F, 1000, 5000));
            beams.add(new EndEventWorldVfxManager.BeamSnapshot(
                    instance("wave6-ritual-sphere"), "minecraft:the_end",
                    null, null, 0, 1.0F, 1000, 5000));
            beams.add(new EndEventWorldVfxManager.BeamSnapshot(
                    instance("wave1-carrier-core-outline"), "minecraft:the_end",
                    null, null, 0, 1.0F, 1000, 5000));
            List<RitualSpellPresentationPolicy.WorldPass> acquired = new ArrayList<>();
            RitualSpellPresentationPolicy.drawWorldPasses(beams, "minecraft:the_end", 2000,
                    pass -> { acquired.add(pass); return snapshot -> { }; });

            check("a glyph pass is acquired for the glyph spell", acquired.stream().anyMatch(
                    p -> p.kind() == RitualSpellPresentationPolicy.PassKind.GLYPH
                            && p.spell() == RitualSpellPresentationPolicy.Spell.RIFT_BARRAGE));
            check("the ritual sphere acquires a channel pass", acquired.stream().anyMatch(
                    p -> p.kind() == RitualSpellPresentationPolicy.PassKind.CHANNEL));
            check("the wave 1 carrier acquires the core outline", acquired.stream().anyMatch(
                    p -> p.kind() == RitualSpellPresentationPolicy.PassKind.CORE_OUTLINE));
            // A pass is acquired only when it has a live beam, and exactly once: two beams
            // sharing the RIFT_BARRAGE glyph must not acquire the same layer twice.
            long glyphPasses = acquired.stream().filter(
                    p -> p.kind() == RitualSpellPresentationPolicy.PassKind.GLYPH).count();
            check("only the live glyph pass is acquired", glyphPasses == 1);
            check("no duplicate acquisitions",
                    acquired.size() == new java.util.LinkedHashSet<>(acquired).size());

            List<EndEventWorldVfxManager.BeamSnapshot> twin = List.of(
                    new EndEventWorldVfxManager.BeamSnapshot(
                            instance("wave6-spell-glyph-RIFT_BARRAGE-active"), "minecraft:the_end",
                            null, null, 0, 1.0F, 1000, 5000),
                    new EndEventWorldVfxManager.BeamSnapshot(
                            instance("wave6-spell-glyph-RIFT_BARRAGE-warning"), "minecraft:the_end",
                            null, null, 0, 1.0F, 1200, 5000));
            List<RitualSpellPresentationPolicy.WorldPass> shared = new ArrayList<>();
            RitualSpellPresentationPolicy.drawWorldPasses(twin, "minecraft:the_end", 2000,
                    pass -> { shared.add(pass); return snapshot -> { }; });
            check("two beams of one spell still acquire one layer", shared.size() == 1);
            // Expired beams draw nothing.
            List<RitualSpellPresentationPolicy.WorldPass> late = new ArrayList<>();
            RitualSpellPresentationPolicy.drawWorldPasses(beams, "minecraft:the_end", 9000,
                    pass -> { late.add(pass); return snapshot -> { }; });
            check("expired beams draw nothing", late.isEmpty());
            // A beam from another dimension draws nothing.
            List<RitualSpellPresentationPolicy.WorldPass> elsewhere = new ArrayList<>();
            RitualSpellPresentationPolicy.drawWorldPasses(beams, "minecraft:overworld", 2000,
                    pass -> { elsewhere.add(pass); return snapshot -> { }; });
            check("other dimensions draw nothing", elsewhere.isEmpty());

            // A malformed reserved key must never become the core outline.
            List<EndEventWorldVfxManager.BeamSnapshot> malformed = List.of(
                    new EndEventWorldVfxManager.BeamSnapshot(
                            instance("wave1-carrier-core-outline-garbage"), "minecraft:the_end",
                            null, null, 0, 1.0F, 1000, 5000));
            List<RitualSpellPresentationPolicy.WorldPass> garbled = new ArrayList<>();
            RitualSpellPresentationPolicy.drawWorldPasses(malformed, "minecraft:the_end", 2000,
                    pass -> { garbled.add(pass); return snapshot -> { }; });
            check("malformed core-outline key is not the core outline", garbled.stream().noneMatch(
                    p -> p.kind() == RitualSpellPresentationPolicy.PassKind.CORE_OUTLINE));

            // --- Screen cue selection --------------------------------------------------
            List<EndEventWorldVfxManager.BeamSnapshot> screens = List.of(
                    new EndEventWorldVfxManager.BeamSnapshot(
                            instance("wave6-spell-screen-RIFT_BARRAGE-warning"), "minecraft:the_end",
                            null, null, 0, 1.0F, 1000, 5000),
                    new EndEventWorldVfxManager.BeamSnapshot(
                            instance("wave6-spell-screen-SOUL_BRAND-active"), "minecraft:the_end",
                            null, null, 0, 1.0F, 1500, 5000));
            EndEventWorldVfxManager.BeamSnapshot chosen =
                    RitualSpellPresentationPolicy.screenCue(screens, "minecraft:the_end", 2000);
            check("the later stage wins the screen",
                    chosen != null && RitualSpellPresentationPolicy.parse(chosen.instanceId())
                            .stage() == RitualSpellPresentationPolicy.Stage.ACTIVE);
            check("no screen cue when nothing is live",
                    RitualSpellPresentationPolicy.screenCue(screens, "minecraft:the_end", 9000) == null);

            // --- HUD layout ------------------------------------------------------------
            var layout = RitualSpellPresentationPolicy.screenLayout(1280, 720, List.of());
            check("layout visible on a normal screen", layout.visible());
            check("the arena cone stays clear",
                    !layout.panel().intersects(layout.clearView())
                            && layout.edgeCues().stream().noneMatch(r -> r.intersects(layout.clearView())));
            check("side cues hug the edges",
                    layout.edgeCues().stream().allMatch(r -> r.left() == 0 || r.right() == 1280));
            check("a tiny screen hides the layout",
                    !RitualSpellPresentationPolicy.screenLayout(200, 100, List.of()).visible());
            // If the top-right corner is already owned by the prisoner HUD, the panel moves left.
            var reserved = List.of(new PrisonerHudLayout.Rect(1100, 0, 1280, 40));
            var shifted = RitualSpellPresentationPolicy.screenLayout(1280, 720, reserved);
            check("the panel dodges the prisoner HUD",
                    !shifted.panel().intersects(reserved.get(0)));
            // If both corners are taken the layout gives up rather than overlapping.
            var both = List.of(new PrisonerHudLayout.Rect(1100, 0, 1280, 40),
                    new PrisonerHudLayout.Rect(0, 0, 200, 40));
            check("layout hides when both corners are taken",
                    !RitualSpellPresentationPolicy.screenLayout(1280, 720, both).visible());

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


def test_spell_presentation_is_allowlisted_and_keeps_the_hud_clear(tmp_path: Path) -> None:
    assert POLICY.is_file(), f"spell presentation source is missing: {POLICY}"
    javac = _javac()
    java = javac.with_name("java.exe")

    package_dir = tmp_path / "src" / "me" / "copimine" / "client"
    package_dir.mkdir(parents=True, exist_ok=True)
    (package_dir / "Vec3.java").write_text(STUB_VEC3, encoding="utf-8")
    (package_dir / "PrisonerHudLayout.java").write_text(STUB_HUD_LAYOUT, encoding="utf-8")
    (package_dir / "EndEventWorldVfxManager.java").write_text(STUB_VFX, encoding="utf-8")
    (package_dir / "WaveCombatPresentationPolicy.java").write_text(
        STUB_COMBAT_POLICY, encoding="utf-8")
    probe = package_dir / "SpellPresentationProbe.java"
    probe.write_text(PROBE, encoding="utf-8")

    sources = [str(p) for p in sorted(package_dir.glob("*.java"))] + [str(POLICY)]
    compile_result = subprocess.run(
        [str(javac), "-proc:none", "-encoding", "UTF-8", "-d", str(tmp_path / "out")] + sources,
        capture_output=True, text=True, timeout=300,
    )
    assert compile_result.returncode == 0, (
        "probe failed to compile:\n" + compile_result.stdout + compile_result.stderr
    )

    run_result = subprocess.run(
        [str(java), "-cp", str(tmp_path / "out"), "me.copimine.client.SpellPresentationProbe"],
        capture_output=True, text=True, timeout=300,
    )
    combined = run_result.stdout + run_result.stderr
    assert run_result.returncode == 0, "spell presentation probe failed:\n" + combined
    assert "OK" in run_result.stdout, combined
