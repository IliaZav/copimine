"""V6 §6.1/§7: the Last Seal reveal may demolish only approved geometry.

The reveal's per-cell decision is delegated to a Bukkit-free policy so this
probe can execute the real rule. It proves the concealed corridor is the only
demolishable interior volume (and only above the floor), the outer wall exists
only when no corridor is configured and only on the hall boundary ring, and an
outer floor fissure opens only outside the hall at exactly floor level — so a
regression cannot quietly start removing a real block outside the approved
corridor.
"""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
POLICY = DOMAIN / "LastSealStructuralGeometryPolicy.java"


def test_cell_geometry_keeps_demolition_inside_the_approved_corridor(tmp_path):
    assert POLICY.exists(), "the reveal geometry policy must exist"
    probe = tmp_path / "LastSealGeometryProbe.java"
    probe.write_text(
        """
import me.copimine.endevent.domain.LastSealStructuralGeometryPolicy;
import me.copimine.endevent.domain.LastSealStructuralGeometryPolicy.Box;

public class LastSealGeometryProbe {
    static int checks = 0;
    static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    static boolean approved(String kind, int x, int y, int z, Box square, int floor, Box corridor) {
        return LastSealStructuralGeometryPolicy.cellKindApproved(kind, x, y, z, square, floor, corridor);
    }

    public static void main(String[] args) {
        Box square = new Box(0, 60, 0, 20, 80, 20);
        int floor = 64;
        Box corridor = new Box(5, 65, 5, 15, 75, 15);

        // Concealed corridor: the only demolishable interior, and only above floor.
        require(approved("corridor", 10, 70, 10, square, floor, corridor),
                "a corridor cell inside the corridor above the floor is approved");
        require(!approved("corridor", 10, 64, 10, square, floor, corridor),
                "a corridor cell at floor level is refused");
        require(!approved("corridor", 10, 76, 10, square, floor, corridor),
                "a corridor cell above the corridor box is refused");
        require(!approved("corridor", 25, 70, 10, square, floor, corridor),
                "a corridor cell outside the corridor is refused");
        require(!approved("corridor", 10, 70, 10, square, floor, null),
                "a corridor cell is refused when no corridor is configured");

        // Outer wall: only with no corridor configured, only on the boundary ring.
        require(approved("wall", 0, 70, 10, square, floor, null),
                "an outer-wall cell on the hall boundary above the floor is approved");
        require(approved("wall", 20, 80, 20, square, floor, null),
                "an outer-wall cell at the hall corner and top is approved");
        require(!approved("wall", 10, 70, 10, square, floor, null),
                "an interior wall cell is refused");
        require(!approved("wall", 0, 64, 10, square, floor, null),
                "a wall cell at floor level is refused");
        require(!approved("wall", 0, 81, 10, square, floor, null),
                "a wall cell above the hall is refused");
        require(!approved("wall", 0, 70, 10, square, floor, corridor),
                "the outer wall is refused while a concealed corridor is configured");

        // Floor fissure: only outside the hall, only at exactly floor level.
        require(approved("fissure", 30, 64, 30, square, floor, null),
                "an outer floor fissure at floor level is approved");
        require(!approved("fissure", 10, 64, 10, square, floor, null),
                "an interior fissure is refused");
        require(!approved("fissure", 30, 70, 30, square, floor, null),
                "an outer fissure above the floor is refused");

        // Fail closed for anything unrecognised.
        require(!approved("bogus", 30, 64, 30, square, floor, null),
                "an unknown cell kind is refused");
        require(!approved(null, 30, 64, 30, square, floor, null),
                "a null cell kind is refused");
        require(!approved("fissure", 30, 64, 30, null, floor, null),
                "a missing hall box is refused");

        System.out.println("PASS last-seal structural geometry kept " + checks + " demolition checks");
    }
}
""",
        encoding="utf-8",
    )
    built = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(POLICY), str(probe)],
        capture_output=True,
        text=True,
    )
    assert built.returncode == 0, built.stdout + built.stderr
    run = subprocess.run(
        ["java", "-cp", str(tmp_path), "LastSealGeometryProbe"],
        capture_output=True,
        text=True,
    )
    assert run.returncode == 0, run.stdout + run.stderr
