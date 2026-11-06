"""Two more guards behind the wave 6 prisoner HUD and the guardian's shield.

Complaint (10) says the captured player gets a working HUD. PrisonerTargetRangePolicy is the
client-side distance guard that mirrors the server-validated abilities; it had no behavioural
test at all. Complaint (10) also asks for the guardian's shield to render; the candidate policy
that bounds the client's world scan for those shields had no test either.

Both are small, pure, and load-bearing: a range guard that silently widens lets a client offer
a heal the server will refuse, and a candidate policy that mis-counts either drops a real
shield or scans an unbounded number of entities every frame.
"""
from __future__ import annotations

import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_SRC = ROOT / "tools/minecraft-26.3/client/src/main/java"
RANGE = CLIENT_SRC / "me/copimine/client/PrisonerTargetRangePolicy.java"
SHIELD = CLIENT_SRC / "me/copimine/client/EndRiftGuardianShieldCandidatePolicy.java"

ABILITY_STUB = textwrap.dedent(
    """
    package me.copimine.client;

    public final class PrisonerHudController {
        public enum Ability {
            HEAL(1, "Q"),
            BATTLE_SURGE(2, "W"),
            GUARDIAN_LINK(3, "E"),
            TURNCOAT(4, "R");

            private final int unlockDeathCount;
            private final String keyLabel;

            Ability(int unlockDeathCount, String keyLabel) {
                this.unlockDeathCount = unlockDeathCount;
                this.keyLabel = keyLabel;
            }

            public int unlockDeathCount() { return unlockDeathCount; }
            public String keyLabel() { return keyLabel; }
        }
    }
    """
)

PROBE = textwrap.dedent(
    """
    package me.copimine.client;

    import java.util.LinkedHashSet;
    import java.util.Set;
    import java.util.UUID;

    public final class RangeShieldProbe {
        private static int failures = 0;

        private static void check(String name, boolean condition) {
            if (!condition) {
                System.out.println("FAIL " + name);
                failures++;
            }
        }

        public static void main(String[] args) {
            // --- Support abilities share the shorter range ------------------------------
            double support = PrisonerTargetRangePolicy.SUPPORT_TARGET_RANGE;
            double turncoat = PrisonerTargetRangePolicy.TURNCOAT_TARGET_RANGE;
            check("turncoat reaches further than support", turncoat > support);
            for (PrisonerHudController.Ability ability : new PrisonerHudController.Ability[]{
                    PrisonerHudController.Ability.HEAL,
                    PrisonerHudController.Ability.BATTLE_SURGE,
                    PrisonerHudController.Ability.GUARDIAN_LINK}) {
                check(ability + " allowed at zero", PrisonerTargetRangePolicy.allows(ability, 0.0D));
                check(ability + " allowed exactly at range",
                        PrisonerTargetRangePolicy.allows(ability, support));
                check(ability + " refused just past range",
                        !PrisonerTargetRangePolicy.allows(ability, support + 0.001D));
            }
            check("turncoat allowed exactly at its own range",
                    PrisonerTargetRangePolicy.allows(PrisonerHudController.Ability.TURNCOAT, turncoat));
            check("turncoat refused past its own range",
                    !PrisonerTargetRangePolicy.allows(PrisonerHudController.Ability.TURNCOAT, turncoat + 0.001D));
            // Turncoat must not inherit the shorter support ceiling.
            check("turncoat is not clamped to the support range",
                    PrisonerTargetRangePolicy.allows(PrisonerHudController.Ability.TURNCOAT, support + 1.0D));

            // --- Degenerate inputs fail closed -------------------------------------------
            check("null ability refused", !PrisonerTargetRangePolicy.allows(null, 0.0D));
            check("nan distance refused",
                    !PrisonerTargetRangePolicy.allows(PrisonerHudController.Ability.HEAL, Double.NaN));
            check("infinite distance refused",
                    !PrisonerTargetRangePolicy.allows(PrisonerHudController.Ability.HEAL,
                            Double.POSITIVE_INFINITY));
            check("negative distance refused",
                    !PrisonerTargetRangePolicy.allows(PrisonerHudController.Ability.HEAL, -0.5D));

            // --- Shield candidates are bounded --------------------------------------------
            Set<UUID> admitted = new LinkedHashSet<>();
            check("an empty set is not full",
                    !EndRiftGuardianShieldCandidatePolicy.isFull(admitted));
            check("a null set is not full",
                    !EndRiftGuardianShieldCandidatePolicy.isFull(null));

            UUID bound = UUID.randomUUID();
            check("a bridge-bound entity is admitted",
                    EndRiftGuardianShieldCandidatePolicy.admit(admitted, bound, true, false));
            check("a marked entity is admitted without a binding",
                    EndRiftGuardianShieldCandidatePolicy.admit(admitted, UUID.randomUUID(), false, true));
            check("re-admitting the same entity is a no-op",
                    EndRiftGuardianShieldCandidatePolicy.admit(admitted, bound, true, false)
                            && admitted.size() == 2);

            // Neither bound nor marked is refused.
            check("an unbound, unmarked entity is refused",
                    !EndRiftGuardianShieldCandidatePolicy.admit(admitted, UUID.randomUUID(), false, false));
            check("a null entity is refused",
                    !EndRiftGuardianShieldCandidatePolicy.admit(admitted, null, true, true));
            check("a null set is refused",
                    !EndRiftGuardianShieldCandidatePolicy.admit(null, UUID.randomUUID(), true, true));

            // The cap is exact: fill it, then one more must be refused.
            while (!EndRiftGuardianShieldCandidatePolicy.isFull(admitted)) {
                check("filling stays under the cap while not full",
                        admitted.size() < EndRiftGuardianShieldCandidatePolicy.MAX_CANDIDATES);
                if (!EndRiftGuardianShieldCandidatePolicy.admit(admitted, UUID.randomUUID(), true, false)) break;
            }
            check("the candidate set reaches exactly the cap",
                    admitted.size() == EndRiftGuardianShieldCandidatePolicy.MAX_CANDIDATES);
            check("a full set is reported full",
                    EndRiftGuardianShieldCandidatePolicy.isFull(admitted));
            check("a further candidate is refused once full",
                    !EndRiftGuardianShieldCandidatePolicy.admit(admitted, UUID.randomUUID(), true, true));
            check("an already admitted entity still passes when full",
                    EndRiftGuardianShieldCandidatePolicy.admit(admitted, bound, true, false));

            // --- The world scan is bounded ------------------------------------------------
            check("scan starts allowed",
                    EndRiftGuardianShieldCandidatePolicy.canInspectEntity(0));
            check("scan allowed one before the cap",
                    EndRiftGuardianShieldCandidatePolicy.canInspectEntity(
                            EndRiftGuardianShieldCandidatePolicy.MAX_ENTITY_INSPECTIONS - 1));
            check("scan refused at the cap",
                    !EndRiftGuardianShieldCandidatePolicy.canInspectEntity(
                            EndRiftGuardianShieldCandidatePolicy.MAX_ENTITY_INSPECTIONS));
            check("negative inspected count refused",
                    !EndRiftGuardianShieldCandidatePolicy.canInspectEntity(-1));
            // The scan budget must be large enough to find four shields, or the cap is useless.
            check("the scan budget exceeds the candidate cap",
                    EndRiftGuardianShieldCandidatePolicy.MAX_ENTITY_INSPECTIONS
                            > EndRiftGuardianShieldCandidatePolicy.MAX_CANDIDATES * 4);

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


def test_prisoner_range_and_shield_candidates_are_bounded(tmp_path: Path) -> None:
    assert RANGE.is_file(), f"range policy source is missing: {RANGE}"
    assert SHIELD.is_file(), f"shield candidate source is missing: {SHIELD}"
    javac = _javac()
    java = javac.with_name("java.exe")

    package_dir = tmp_path / "src" / "me" / "copimine" / "client"
    package_dir.mkdir(parents=True, exist_ok=True)
    (package_dir / "PrisonerHudController.java").write_text(ABILITY_STUB, encoding="utf-8")
    probe = package_dir / "RangeShieldProbe.java"
    probe.write_text(PROBE, encoding="utf-8")

    compile_result = subprocess.run(
        [str(javac), "-proc:none", "-encoding", "UTF-8", "-d", str(tmp_path / "out"),
         str(package_dir / "PrisonerHudController.java"),
         str(package_dir / "RangeShieldProbe.java"), str(RANGE), str(SHIELD)],
        capture_output=True, text=True, timeout=300,
    )
    assert compile_result.returncode == 0, (
        "probe failed to compile:\n" + compile_result.stdout + compile_result.stderr
    )

    run_result = subprocess.run(
        [str(java), "-cp", str(tmp_path / "out"), "me.copimine.client.RangeShieldProbe"],
        capture_output=True, text=True, timeout=300,
    )
    combined = run_result.stdout + run_result.stderr
    assert run_result.returncode == 0, "range/shield probe failed:\n" + combined
    assert "OK" in run_result.stdout, combined
