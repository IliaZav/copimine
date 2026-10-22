"""The 2-3 participant Anvil branch must use distinct landing points, not shared columns."""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def declaration(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_single_target_anvils_keep_players_on_the_same_column(tmp_path) -> None:
    source = read(SERVER)
    method = declaration(source, "private static List<LandingPoint> selectSingleAnvilLandingPoints(")
    fixture = tmp_path / "SingleAnvilPointsProbe.java"
    fixture.write_text(
        """
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SingleAnvilPointsProbe {
    record LandingPoint(double x, double y, double z) {}

""" + method + """

    public static void main(String[] args) {
        List<LandingPoint> selected = selectSingleAnvilLandingPoints(Arrays.asList(
                new LandingPoint(1.0, 68.0, 3.0),
                null,
                new LandingPoint(1.0, 70.0, 3.0),
                new LandingPoint(1.0, 68.0, 3.0)));
        if (selected.size() != 2) {
            throw new AssertionError("same column, different height keeps one anvil per player: " + selected);
        }
        if (!selected.get(0).equals(new LandingPoint(1.0, 68.0, 3.0))
                || !selected.get(1).equals(new LandingPoint(1.0, 70.0, 3.0))) {
            throw new AssertionError("selection must preserve landing heights and order: " + selected);
        }
        if (!selectSingleAnvilLandingPoints(List.of()).isEmpty()) {
            throw new AssertionError("an empty candidate list stays empty");
        }
    }
}
""",
        encoding="utf-8",
    )
    compiled = subprocess.run(
        ["javac", "-J-Xmx96m", "-encoding", "UTF-8", "-d", str(tmp_path), str(fixture)],
        capture_output=True,
        text=True,
        timeout=30,
    )
    assert compiled.returncode == 0, compiled.stderr
    executed = subprocess.run(
        ["java", "-Xms8m", "-Xmx96m", "-XX:+UseSerialGC", "-cp", str(tmp_path), "SingleAnvilPointsProbe"],
        capture_output=True,
        text=True,
        timeout=30,
    )
    assert executed.returncode == 0, executed.stdout + executed.stderr
