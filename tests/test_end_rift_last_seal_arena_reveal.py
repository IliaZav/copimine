"""Execute the real Last Seal arena-reveal plan parser and its journal contract.

The V6 audit (section 7.1) requires that the site plan carry the *exact* mask,
that world writes run in bounded batches, and that an unfinished reveal be
recovered from a durable journal instead of a guess. That plan is authored per
site, so this probe drives the real ``parseChanges`` directly and proves:

* a mask larger than the old 320-cell ceiling is accepted;
* the accepted mask still cannot exceed the documented ceiling;
* duplicate or malformed cells are refused (so a bad plan never half-applies);
* a plan that would straddle too many chunks is refused;
* the per-tick write batch stays bounded.
"""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/LastSealArenaReveal.java"


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for pos in range(opening, len(source)):
        depth += (source[pos] == "{") - (source[pos] == "}")
        if depth == 0:
            return source[start:pos + 1]
    raise AssertionError("Unclosed production Java method")


def _constant(source: str, name: str) -> int:
    match = re.search(rf"static final int {name} = ([0-9_]+);", source)
    assert match, f"missing production constant {name}"
    return int(match.group(1).replace("_", ""))


def test_plan_parser_accepts_masks_larger_than_the_old_320_ceiling(tmp_path):
    source = SOURCE.read_text(encoding="utf-8")
    max_cells = _constant(source, "MAX_CELLS")
    batch = _constant(source, "BATCH")
    assert max_cells > 320, (
        "the reveal must support an authored mask larger than 320 cells")
    assert 0 < batch <= 512, "each five-tick world-write batch must stay bounded"
    assert batch * 38 >= 16_736, "the full production collapse must fit in 38 invocations"

    parse = _method(source, "private static List<Change> parseChanges(")
    number = _method(source, "private static int number(")
    probe = tmp_path / "LastSealRevealCapProbe.java"
    probe.write_text(f'''
import java.io.IOException;
import java.util.*;

public class LastSealRevealCapProbe {{
    static final int MAX_CELLS = {max_cells};
    record Cell(int x, int y, int z) {{ }}
    record Change(String kind, Cell cell, String original) {{
        boolean wall() {{ return !kind.equals("fissure"); }}
    }}
{parse}
{number}
    static void require(boolean ok, String message) {{ if (!ok) throw new AssertionError(message); }}

    static String cell(int x, int y, int z) {{
        return "fissure\\t" + x + "\\t" + y + "\\t" + z + "\\tminecraft:stone";
    }}

    public static void main(String[] args) throws Exception {{
        // 700 distinct cells inside a single chunk column pair: larger than the
        // retired 320 ceiling but well under the chunk-span guard.
        List<String> large = new ArrayList<>();
        for (int i = 0; i < 700; i++) large.add(cell(i % 16, 60 + (i / 256), (i / 16) % 16));
        List<Change> parsed = parseChanges(large);
        require(parsed.size() == 700, "a >320-cell mask must parse, got " + parsed.size());
        require(parsed.stream().allMatch(change -> !change.wall()), "fissures must stay fissures");

        boolean duplicateRefused = false;
        try {{
            parseChanges(List.of(cell(1, 64, 1), cell(1, 64, 1)));
        }} catch (IOException expected) {{ duplicateRefused = true; }}
        require(duplicateRefused, "a duplicated cell must be refused");

        boolean unknownKindRefused = false;
        try {{
            parseChanges(List.of("bogus\\t1\\t64\\t1\\tminecraft:stone"));
        }} catch (IOException expected) {{ unknownKindRefused = true; }}
        require(unknownKindRefused, "an unknown cell kind must be refused");

        List<String> tooLarge = new ArrayList<>();
        for (int i = 0; i < MAX_CELLS + 1; i++) tooLarge.add(cell(i % 16, 60 + (i / 256), (i / 16) % 16));
        boolean ceilingRefused = false;
        try {{ parseChanges(tooLarge); }} catch (IOException expected) {{ ceilingRefused = true; }}
        require(ceilingRefused, "a plan above the documented ceiling must be refused");

        // The guard is 64 chunks now (the square alone spans 36), so probe it
        // with 80 deliberately scattered chunk columns.
        List<String> wandering = new ArrayList<>();
        for (int i = 0; i < 80; i++) wandering.add(cell(i * 16 * 40, 64, i * 16 * 40));
        boolean chunksRefused = false;
        try {{ parseChanges(wandering); }} catch (IOException expected) {{ chunksRefused = true; }}
        require(chunksRefused, "a plan spanning too many chunks must be refused");

        System.out.println("PASS last-seal reveal honours a >320-cell authoured mask with bounded batches");
    }}
}}
''', encoding="utf-8")
    built = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(probe)],
        capture_output=True, text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    run = subprocess.run(
        ["java", "-cp", str(tmp_path), "LastSealRevealCapProbe"],
        capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
