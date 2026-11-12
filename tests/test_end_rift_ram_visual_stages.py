"""Wave 6 Rift Ram: six armour stages, each with its own visual id.

``RiftRamVisualPolicy`` is the whole of the Ram's damage progression on the
client. Had no test, so a wrong stage map - the ram silently never changing
appearance, or going invisible on a malformed payload - would have passed
every gate.
"""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"

# The contract's own stage count, written out here so a shortened policy
# constant cannot make this file agree with itself.
REQUIRED_STAGES = 6


def run_probe(tmp_path, name, body):
    probe = tmp_path / (name + ".java")
    probe.write_text(body, encoding="utf-8")
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(CLIENT / "RiftRamVisualPolicy.java"), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        ["java", "-cp", str(tmp_path), name], capture_output=True, text=True
    )
    assert executed.returncode == 0, executed.stdout + executed.stderr
    return executed.stdout


def test_every_stage_has_its_own_visual(tmp_path):
    """Seven collisions total: four minor and three final, so six stages."""
    run_probe(
        tmp_path,
        "StageProbe",
        """
import me.copimine.client.*;
import java.util.*;

public final class StageProbe {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    public static void main(String[] args) {
        int stages = __STAGES__;

        // Each stage 0..5 maps to a distinct, non-null id. A duplicate here
        // means the ram stops visibly changing halfway through the fight.
        Set<String> seen = new HashSet<>();
        for (int s = 0; s < stages; s++) {
            String id = RiftRamVisualPolicy.visualForArmorStage(s);
            check(id != null && !id.isBlank(), "stage " + s + " has no visual");
            check(seen.add(id), "stage " + s + " reuses a previous visual: " + id);
            check(RiftRamVisualPolicy.isRiftRamVisual(id),
                    "stage " + s + " id is not recognised as a rift ram visual");
        }
        check(seen.size() == stages, "expected " + stages + " stages, found " + seen.size());

        // Every id is in the wave 6 family and already upper case, or the
        // renderer lookup will miss it.
        for (String id : seen) {
            check(id.startsWith("END_RIFT_"), "id is outside the family: " + id);
            check(id.equals(id.toUpperCase(Locale.ROOT)), "id is not upper case: " + id);
        }

        // Stage 0 is the intact ram. The server sends the counted impact, so
        // a fresh encounter must not start already damaged.
        check(RiftRamVisualPolicy.visualForArmorStage(0).equals(RiftRamVisualPolicy.ARMOR0_VISUAL),
                "stage 0 must be the intact armour");

        // Out-of-range payloads clamp instead of rendering nothing: an
        // invisible ravager reads to the player as a broken encounter.
        check(RiftRamVisualPolicy.visualForArmorStage(-1).equals(RiftRamVisualPolicy.ARMOR0_VISUAL),
                "a negative stage clamps to intact");
        check(RiftRamVisualPolicy.visualForArmorStage(-999).equals(RiftRamVisualPolicy.ARMOR0_VISUAL),
                "a wildly negative stage clamps to intact");
        String top = RiftRamVisualPolicy.visualForArmorStage(stages - 1);
        check(RiftRamVisualPolicy.visualForArmorStage(stages).equals(top),
                "one past the top clamps to the last stage");
        check(RiftRamVisualPolicy.visualForArmorStage(999).equals(top),
                "a wildly high stage clamps to the last stage");
        check(!RiftRamVisualPolicy.visualForArmorStage(Integer.MAX_VALUE).isBlank(),
                "MAX_VALUE must still produce a renderable visual");

        // Stage monotonicity: the last stage is the exposed ram, so it must
        // differ from the intact one, which the uniqueness check already
        // covers, but assert it directly for a clear failure message.
        check(!RiftRamVisualPolicy.visualForArmorStage(0)
                .equals(RiftRamVisualPolicy.visualForArmorStage(stages - 1)),
                "the exposed ram must not look intact");
    }
}
""".replace("__STAGES__", str(REQUIRED_STAGES)),
    )


def test_foreign_visuals_are_not_claimed(tmp_path):
    """The recogniser must not claim another wave's ids."""
    run_probe(
        tmp_path,
        "ClaimProbe",
        """
import me.copimine.client.*;

public final class ClaimProbe {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    public static void main(String[] args) {
        check(!RiftRamVisualPolicy.isRiftRamVisual(null), "null is not a ram");
        check(!RiftRamVisualPolicy.isRiftRamVisual(""), "blank is not a ram");
        check(!RiftRamVisualPolicy.isRiftRamVisual("   "), "spaces are not a ram");
        check(!RiftRamVisualPolicy.isRiftRamVisual("vanilla"), "vanilla is not a ram");
        // A neighbouring family must not be swallowed by a loose prefix test.
        check(!RiftRamVisualPolicy.isRiftRamVisual("END_RIFT_RAM_ARMOR6_V1"),
                "an unknown armour index is not a ram");
        check(!RiftRamVisualPolicy.isRiftRamVisual("END_RIFT_WAVE7_WARDEN_V1"),
                "a wave 7 id is not a ram");
        check(!RiftRamVisualPolicy.isRiftRamVisual("END_RIFT_RAM_ARMOR0_V2"),
                "a future revision is not claimed by this one");

        // Case and whitespace tolerance: the server trims and upper-cases, so
        // a lower-case or padded payload must still be recognised.
        String id = RiftRamVisualPolicy.visualForArmorStage(0);
        check(RiftRamVisualPolicy.isRiftRamVisual(id.toLowerCase(java.util.Locale.ROOT)),
                "a lower-case id is still a ram");
        check(RiftRamVisualPolicy.isRiftRamVisual("  " + id + "  "),
                "a padded id is still a ram");
    }
}
""",
    )
