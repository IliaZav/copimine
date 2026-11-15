"""Wave 7 miniboss boss bars: behaviour, identity and art contract.

Wave 7 used to be the only objective in the encounter with no status readout at
all. The fix gives every live miniboss its own bar, and the optional client HUD
repaints those bars with authored art while a player without the mod keeps the
correct vanilla bar.

This test pins three things that can silently rot:

1. the policy's behaviour (role routing, unique identities, priority, progress),
   executed for real in a JVM rather than matched as source text;
2. that the client's own copy of the identity strings still matches the server
   policy -- the HUD matches bars by title, so a drift here means a miniboss
   silently falls back to the vanilla bar;
3. that the authored art exists, is the expected shape, and is distinct per
   miniboss, and that the mixin dispatch order cannot steal other bosses' bars.
"""

import re
import subprocess
import textwrap
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
POLICY = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "domain"
    / "Wave7MinibossBossBarPolicy.java"
)
PLUGIN = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "CopiMineEndEvent.java"
)
CLIENT_HUD = (
    ROOT
    / "tools"
    / "minecraft-26.3"
    / "client"
    / "src"
    / "main"
    / "java"
    / "me"
    / "copimine"
    / "client"
    / "Wave7MinibossBossBarHud.java"
)
MIXIN = (
    ROOT
    / "tools"
    / "minecraft-26.3"
    / "client"
    / "src"
    / "main"
    / "java"
    / "me"
    / "copimine"
    / "client"
    / "mixin"
    / "EndRiftBossBarHudMixin.java"
)
ART = (
    ROOT
    / "CopiMineClient"
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "copimineclient"
    / "textures"
    / "gui"
)

PROBE = textwrap.dedent(
    """
    import me.copimine.endevent.domain.Wave7MinibossBossBarPolicy;
    import me.copimine.endevent.domain.Wave7MinibossBossBarPolicy.Kind;
    import java.util.ArrayList;
    import java.util.HashSet;
    import java.util.List;
    import java.util.Set;

    public final class MinibossBarProbe {
        private static int failures = 0;

        private static void check(boolean condition, String message) {
            if (!condition) {
                failures++;
                System.out.println("FAIL: " + message);
            }
        }

        public static void main(String[] args) {
            // Every named trial role owns exactly one bar identity.
            check(Wave7MinibossBossBarPolicy.kindForTrialRole("WARDEN") == Kind.WARDEN,
                    "WARDEN routes to the Warden bar");
            check(Wave7MinibossBossBarPolicy.kindForTrialRole("MOBILE_MARKSMAN") == Kind.MARKSMAN,
                    "MOBILE_MARKSMAN routes to the bone bar");
            check(Wave7MinibossBossBarPolicy.kindForTrialRole("RIFT_ARCHMAGE") == Kind.ARCHMAGE,
                    "RIFT_ARCHMAGE routes to the rune bar");
            check(Wave7MinibossBossBarPolicy.kindForTrialRole("PERSONAL_ECHO") == Kind.ECHO,
                    "PERSONAL_ECHO routes to the mirror bar");
            // The legacy Trial spellings resolve to the same bars.
            check(Wave7MinibossBossBarPolicy.kindForTrialRole("RIFT_HUNTER") == null,
                    "an unrelated legacy role does not borrow a bar");
            check(Wave7MinibossBossBarPolicy.kindForTrialRole("") == null,
                    "an empty role does not borrow a bar");
            check(Wave7MinibossBossBarPolicy.kindForTrialRole(null) == null,
                    "a null role does not borrow a bar");
            check(Wave7MinibossBossBarPolicy.kindForTrialRole(" warden ") == Kind.WARDEN,
                    "role matching tolerates padding and case");

            // Identities are unique and never collide with bars another
            // encounter already owns: a collision would make the client HUD
            // repaint the wrong boss.
            Set<String> titles = new HashSet<>();
            Set<String> accents = new HashSet<>();
            for (Kind kind : Kind.values()) {
                check(titles.add(kind.title()), "duplicate title: " + kind.title());
                check(accents.add(kind.accentRgb() + ":" + kind.glowRgb()),
                        "duplicate palette: " + kind);
                check(!kind.title().isBlank(), "blank title for " + kind);
                check(kind.accentRgb() != kind.glowRgb(), "flat palette for " + kind);
                check(Wave7MinibossBossBarPolicy.kindForTitle(kind.title()) == kind,
                        "title round-trips for " + kind);
            }
            String[] foreign = {
                "Хранитель Разлома",   // Rift Guardian
                "Страж Разлома",       // wave-6 trial actor / Guardian fallback
                "Таран Разлома",       // Rift Ram
            };
            for (String title : foreign) {
                for (Kind kind : Kind.values()) {
                    check(!kind.title().startsWith(title),
                            kind + " title collides with " + title);
                }
            }

            // A live counter may be appended to a title; the kind still resolves.
            check(Wave7MinibossBossBarPolicy.kindForTitle(
                    Kind.MARKSMAN.title() + " - 3/4") == Kind.MARKSMAN,
                    "a suffixed title still resolves");

            // Progress is clamped and never NaN.
            check(Wave7MinibossBossBarPolicy.progress(50.0D, 100.0D) == 0.5F, "half health");
            check(Wave7MinibossBossBarPolicy.progress(0.0D, 100.0D) == 0.0F, "zero health");
            check(Wave7MinibossBossBarPolicy.progress(120.0D, 100.0D) == 1.0F, "clamped high");
            check(Wave7MinibossBossBarPolicy.progress(-5.0D, 100.0D) == 0.0F, "clamped low");
            check(Wave7MinibossBossBarPolicy.progress(10.0D, 0.0D) == 0.0F, "zero max");
            check(Wave7MinibossBossBarPolicy.progress(Double.NaN, 100.0D) == 0.0F, "NaN health");
            check(!Float.isNaN(Wave7MinibossBossBarPolicy.progress(1.0D, Double.NaN)), "NaN max");

            check(!Wave7MinibossBossBarPolicy.critical(0.5F), "half health is not critical");
            check(Wave7MinibossBossBarPolicy.critical(0.2F), "a fifth is critical");
            check(!Wave7MinibossBossBarPolicy.critical(0.0F), "dead is not critical");

            // Priority orders overlapping trials; equal kinds keep the first.
            check(Wave7MinibossBossBarPolicy.priority(Kind.WARDEN)
                    > Wave7MinibossBossBarPolicy.priority(Kind.ARCHMAGE), "warden outranks archmage");
            check(Wave7MinibossBossBarPolicy.priority(Kind.ARCHMAGE)
                    > Wave7MinibossBossBarPolicy.priority(Kind.MARKSMAN), "archmage outranks marksman");
            check(Wave7MinibossBossBarPolicy.priority(Kind.MARKSMAN)
                    > Wave7MinibossBossBarPolicy.priority(Kind.ECHO), "marksman outranks echo");
            List<Kind> alive = new ArrayList<>();
            alive.add(Kind.ECHO);
            alive.add(Kind.ARCHMAGE);
            alive.add(null);
            check(Wave7MinibossBossBarPolicy.dominant(alive) == Kind.ARCHMAGE,
                    "the dominant bar is the most dangerous trial");
            check(Wave7MinibossBossBarPolicy.dominant(List.of()) == null, "no trials, no bar");

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


def test_policy_behaviour(tmp_path):
    policy_dir = POLICY.parent
    compiled = subprocess.run(
        ["javac", "-d", str(tmp_path), str(POLICY)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stderr
    (tmp_path / "MinibossBarProbe.java").write_text(PROBE, encoding="utf-8")
    compiled_probe = subprocess.run(
        ["javac", "-cp", str(tmp_path), "-d", str(tmp_path), str(tmp_path / "MinibossBarProbe.java")],
        cwd=str(policy_dir),
        capture_output=True,
        text=True,
    )
    assert compiled_probe.returncode == 0, compiled_probe.stderr
    ran = subprocess.run(
        ["java", "-cp", str(tmp_path), "MinibossBarProbe"],
        cwd=str(tmp_path),
        capture_output=True,
        text=True,
    )
    assert ran.returncode == 0, ran.stdout + ran.stderr
    assert "OK" in ran.stdout


def _policy_titles():
    source = POLICY.read_text(encoding="utf-8")
    return set(re.findall(r'^\s+[A-Z]+\("([^"]+)"', source, re.MULTILINE))


def test_client_and_server_agree_on_every_identity():
    server_titles = _policy_titles()
    assert server_titles == {
        "Костяной Стрелок Разлома",
        "Хранитель Бездны",
        "Архимаг Разлома",
        "Эхо Разлома",
    }, server_titles
    client_source = CLIENT_HUD.read_text(encoding="utf-8")
    for title in server_titles:
        assert f'"{title}"' in client_source, (
            f"client HUD no longer recognises the server title {title!r}"
        )


def test_client_hud_is_dispatched_before_the_other_per_boss_bar():
    source = MIXIN.read_text(encoding="utf-8")
    wave7 = source.index("Wave7MinibossBossBarHud.render(")
    ram = source.index("RiftRamBossBarHud.render(")
    assert wave7 < ram, "the Wave 7 bar must be tried before the Ram bar"
    # A bar renderer that matched too broadly would swallow other bosses.
    hud = CLIENT_HUD.read_text(encoding="utf-8")
    assert "startsWith(" in hud
    assert "return false;" in hud


def test_server_creates_and_retires_a_bar_per_live_miniboss():
    source = PLUGIN.read_text(encoding="utf-8")
    assert "import me.copimine.endevent.domain.Wave7MinibossBossBarPolicy;" in source
    assert "private final Map<UUID, BossBar> wave7MinibossBars" in source
    assert "updateWave7MinibossBossBars();" in source
    assert "clearWave7MinibossBossBars();" in source, "shutdown must retire the bars"
    assert "Wave7MinibossBossBarPolicy.ownsBar(role)" in source
    assert "Wave7MinibossBossBarPolicy.progress(" in source
    # The bar is driven by real health, never a constant.
    assert "living.getHealth(), maxHealthOf(living)" in source
    # A stale bar can never survive its actor.
    assert "stale.removeAll();" in source


def test_authored_art_exists_and_is_distinct():
    frames = sorted(ART.glob("wave7_bossbar_*.png"))
    portraits = sorted(ART.glob("wave7_portrait_*.png"))
    assert len(frames) == 4, [p.name for p in frames]
    assert len(portraits) == 4, [p.name for p in portraits]

    seen = set()
    for path in frames:
        with Image.open(path) as image:
            assert image.size == (256, 32), (path.name, image.size)
            assert image.mode == "RGBA", (path.name, image.mode)
            assert image.getextrema()[3][1] > 0, f"{path.name} is fully transparent"
        digest = path.read_bytes()
        assert digest not in seen, f"{path.name} duplicates another frame"
        seen.add(digest)

    portrait_digests = set()
    for path in portraits:
        with Image.open(path) as image:
            assert image.size == (128, 128), (path.name, image.size)
            alpha = image.getchannel("A")
            painted = sum(alpha.histogram()[1:])
            # A portrait that is mostly empty reads as a missing texture.
            assert painted > 128 * 128 * 0.15, (path.name, painted)
        data = path.read_bytes()
        assert data not in portrait_digests, f"{path.name} duplicates another portrait"
        portrait_digests.add(data)


def test_the_generator_reproduces_the_committed_art(tmp_path):
    """The art must be regenerable: a hand-edited PNG nobody can rebuild is a
    one-way change."""
    script = ROOT / "scripts" / "minecraft" / "GenerateWave7BossBarArt.py"
    assert script.exists()
    source = script.read_text(encoding="utf-8")
    assert 'ROOT = Path(__file__).resolve().parents[2]' in source, (
        "the generator must resolve the repository root, not a phantom assets tree"
    )
    assert "CopiMineClient" in source and "textures" in source and "gui" in source