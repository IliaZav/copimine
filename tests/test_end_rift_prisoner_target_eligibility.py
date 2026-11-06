"""The captive's HUD must only offer targets the server actually authorised.

Complaint (10) includes "HUD in the sphere" and "does not aggro the player in the sphere".
PrisonerTargetEligibility is the client-side allowlist that decides which ability buttons
are live, but it had no behavioural test at all - only an artifact-presence check that the
class file ships. A malformed or truncated payload would silently widen or narrow the HUD.

The parser is pure Java with no client dependencies, so we drive it for real through a
throwaway main compiled against the client sources.
"""
from __future__ import annotations

import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_SRC = ROOT / "tools/minecraft-26.3/client/src/main/java"

PROBE = textwrap.dedent(
    """
    package me.copimine.client;

    import java.util.UUID;

    public final class PrisonerEligibilityProbe {
        private static int failures = 0;

        private static void check(String name, boolean condition) {
            if (!condition) {
                System.out.println("FAIL " + name);
                failures++;
            }
        }

        public static void main(String[] args) {
            UUID supportA = UUID.fromString("11111111-2222-3333-4444-555555555555");
            UUID supportB = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
            UUID turncoat = UUID.fromString("99999999-8888-7777-6666-555555555555");

            String payload = "PRISONER_TARGETS_V1|"
                    + supportA + "," + supportB + "|" + turncoat;
            PrisonerTargetEligibility parsed = PrisonerTargetEligibility.parse(payload);

            check("support A heal", parsed.allows(PrisonerHudController.Ability.HEAL, supportA));
            check("support B surge", parsed.allows(PrisonerHudController.Ability.BATTLE_SURGE, supportB));
            check("support A link", parsed.allows(PrisonerHudController.Ability.GUARDIAN_LINK, supportA));
            check("turncoat allowed", parsed.allows(PrisonerHudController.Ability.TURNCOAT, turncoat));

            // Groups never bleed into each other.
            check("support is not a turncoat target",
                    !parsed.allows(PrisonerHudController.Ability.TURNCOAT, supportA));
            check("turncoat is not a support target",
                    !parsed.allows(PrisonerHudController.Ability.HEAL, turncoat));
            check("unknown player denied", !parsed.allows(PrisonerHudController.Ability.HEAL,
                    UUID.fromString("00000000-0000-0000-0000-000000000000")));

            // Nulls never grant an ability.
            check("null ability denied", !parsed.allows(null, supportA));
            check("null target denied", !parsed.allows(PrisonerHudController.Ability.HEAL, null));

            // Fail closed on every malformed shape.
            check("null payload", !PrisonerTargetEligibility.parse(null)
                    .allows(PrisonerHudController.Ability.HEAL, supportA));
            check("blank payload", !PrisonerTargetEligibility.parse("   ")
                    .allows(PrisonerHudController.Ability.HEAL, supportA));
            check("wrong prefix", !PrisonerTargetEligibility.parse("OTHER|" + supportA + "|")
                    .allows(PrisonerHudController.Ability.HEAL, supportA));
            check("missing group", !PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1|" + supportA)
                    .allows(PrisonerHudController.Ability.HEAL, supportA));
            check("extra group", !PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1|" + supportA + "||")
                    .allows(PrisonerHudController.Ability.HEAL, supportA));
            check("bad uuid", !PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1|not-a-uuid|")
                    .allows(PrisonerHudController.Ability.HEAL, supportA));
            // A non-canonical (unpadded) UUID must be rejected, not silently normalised.
            check("noncanonical uuid rejected",
                    !PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1|1111111-2222-3333-4444-555555555555|")
                            .allows(PrisonerHudController.Ability.HEAL, supportA));

            // An overlong payload is refused wholesale.
            StringBuilder huge = new StringBuilder("PRISONER_TARGETS_V1|");
            for (int i = 0; i < 300; i++) {
                if (i > 0) huge.append(',');
                huge.append(supportA);
            }
            huge.append("|");
            check("overlong payload refused",
                    !PrisonerTargetEligibility.parse(huge.toString())
                            .allows(PrisonerHudController.Ability.HEAL, supportA));

            // More than 64 targets in one group is refused.
            StringBuilder many = new StringBuilder("PRISONER_TARGETS_V1|");
            for (int i = 0; i < 65; i++) {
                if (i > 0) many.append(',');
                many.append(UUID.randomUUID());
            }
            many.append("|");
            check("too many targets refused",
                    !PrisonerTargetEligibility.parse(many.toString())
                            .allows(PrisonerHudController.Ability.HEAL, supportA));

            // Empty groups are legal and grant nothing.
            PrisonerTargetEligibility none = PrisonerTargetEligibility.parse("PRISONER_TARGETS_V1||");
            check("empty groups grant nothing",
                    !none.allows(PrisonerHudController.Ability.HEAL, supportA)
                            && !none.allows(PrisonerHudController.Ability.TURNCOAT, turncoat));

            System.out.println(failures == 0 ? "OK" : "FAILURES " + failures);
            if (failures != 0) System.exit(1);
        }
    }
    """
)


# PrisonerTargetEligibility only needs the four ability names; PrisonerHudController itself
# pulls in the whole client (render, packet, policy) graph. A stub with the real enum keeps
# the probe hermetic while still exercising the real parser and the real switch.
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


def _javac() -> Path:
    candidates = sorted(ROOT.glob("local-runtime/toolchains/temurin-*/jdk-*/bin/javac.exe"))
    assert candidates, "no local JDK found under local-runtime/toolchains"
    return candidates[0]


def test_prisoner_target_eligibility_is_deny_by_default(tmp_path: Path) -> None:
    javac = _javac()
    java = javac.with_name("java.exe")
    # javac resolves the stub by package path, so it has to sit under me/copimine/client.
    package_dir = tmp_path / "src" / "me" / "copimine" / "client"
    package_dir.mkdir(parents=True, exist_ok=True)
    probe = package_dir / "PrisonerEligibilityProbe.java"
    probe.write_text(PROBE, encoding="utf-8")
    stub = package_dir / "PrisonerHudController.java"
    stub.write_text(ABILITY_STUB, encoding="utf-8")
    eligibility = CLIENT_SRC / "me/copimine/client/PrisonerTargetEligibility.java"
    assert eligibility.is_file(), f"eligibility source is missing: {eligibility}"

    compile_result = subprocess.run(
        [str(javac), "-proc:none", "-encoding", "UTF-8", "-d", str(tmp_path / "out"),
         str(stub), str(eligibility), str(probe)],
        capture_output=True, text=True, timeout=300,
    )
    assert compile_result.returncode == 0, (
        "probe failed to compile:\n" + compile_result.stdout + compile_result.stderr
    )

    run_result = subprocess.run(
        [str(java), "-cp", str(tmp_path / "out"), "me.copimine.client.PrisonerEligibilityProbe"],
        capture_output=True, text=True, timeout=300,
    )
    combined = run_result.stdout + run_result.stderr
    assert run_result.returncode == 0, "prisoner eligibility probe failed:\n" + combined
    assert "OK" in run_result.stdout, combined
