"""Wave 2 presents 70 client-only observers, not 35.

The approved contract asks for roughly twice the previous phantom population per
marked player. Two independent budgets had to move together:

  WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT  (how many are selected)
  WaveTwoPhantomLayoutManager.PHANTOM_ID     (which ids are admitted at all)

Widening only the first shows nothing extra: the client silently drops a
payload whose id the pattern rejects. This file pins both, plus the candidate
headroom that lets 70 survive blocked spots.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"


def read(name: str) -> str:
    return (CLIENT / name).read_text(encoding="utf-8")


def test_the_layout_targets_seventy_phantoms():
    text = read("WaveTwoPhantomLayout.java")
    assert "TARGET_PHANTOM_COUNT = 70;" in text, (
        "the approved Wave 2 presents about seventy observers")
    assert "TARGET_PHANTOM_COUNT = 35;" not in text


def test_the_candidate_rings_supply_headroom_for_seventy():
    text = read("WaveTwoPhantomLayout.java")
    radii = re.search(r"RING_RADII = \{([^}]*)\}", text).group(1)
    rings = [value for value in radii.split(",") if value.strip()]
    per_ring = int(re.search(r"PHANTOMS_PER_RING = (\d+)", text).group(1))
    assert len(rings) * per_ring > 70, (
        "candidates must exceed the target so blocked spots can be dropped "
        f"without falling short; got {len(rings)} rings of {per_ring}")


def test_the_manager_admits_every_id_the_layout_can_emit():
    text = read("WaveTwoPhantomLayoutManager.java")
    pattern = re.search(r'PHANTOM_ID = Pattern\.compile\("([^"]+)"\)', text).group(1)
    import subprocess, sys, tempfile, textwrap, os
    probe = textwrap.dedent("""
        import java.util.regex.Pattern;
        public final class IdProbe {
            public static void main(String[] args) {
                Pattern p = Pattern.compile(args[0]);
                int admitted = 0;
                for (int i = 0; i < 100; i++) {
                    String id = "phantom-" + (i < 10 ? "0" + i : Integer.toString(i));
                    if (p.matcher(id).matches()) admitted++;
                }
                System.out.println(admitted);
            }
        }
    """)
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "IdProbe.java"
        src.write_text(probe, encoding="utf-8")
        subprocess.run(["javac", "-d", tmp, str(src)], check=True, capture_output=True)
        out = subprocess.run(
            ["java", "-cp", tmp, "IdProbe", pattern.replace("\\", "\\")],
            check=True, capture_output=True, text=True).stdout.strip()
    assert int(out) >= 70, (
        f"the client admits only {out} phantom ids; every id beyond that is "
        "dropped silently and the scene stays at the old population")


def test_the_id_pattern_still_refuses_garbage():
    text = read("WaveTwoPhantomLayoutManager.java")
    pattern = re.search(r'PHANTOM_ID = Pattern\.compile\("([^"]+)"\)', text).group(1)
    regex = re.compile(pattern)
    for good in ("phantom-00", "phantom-69", "phantom-42"):
        assert regex.fullmatch(good), f"{good} must be admitted"
    for bad in ("phantom-70", "phantom-0", "phantom-x", "phantom-000"):
        assert not regex.fullmatch(bad), f"{bad} must stay refused"
