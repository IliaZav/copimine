"""Release provenance: a projectile owns exactly the identity it was fired with.

Contract 2026-10-31: an End Rift entity that damages a player must prove it
belongs to the encounter and to the shooter that produced it.  The Wave 6
sphere barrage is the strictest case: the shared ritual marker is too loose a
proof, so every sphere arrow must additionally carry a finite origin, an owner
equal to its logical shooter, an intended target that equals the target it
actually hit, and an expiry tick that has not passed.

The policy is exercised through real compiled Java (not by reading the source),
so a regression in the accept/reject boundary fails here rather than silently
in production.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event/src/me/copimine/endevent"

_HARNESS = """import java.util.*;
import me.copimine.endevent.domain.*;
public class {name} {{
    static void require(boolean value, String reason) {{ if (!value) throw new AssertionError(reason); }}
    static final java.util.UUID OWNER = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final java.util.UUID OTHER = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final java.util.UUID TARGET = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    static final java.util.UUID TARGET2 = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    static final long NOW = 1_000L;
    static final RitualSphereProjectileProvenancePolicy.Vec3 ORIGIN =
            new RitualSphereProjectileProvenancePolicy.Vec3(0.0, 64.0, 0.0);

    static boolean accepts(boolean marker, java.util.UUID owner, java.util.UUID shooter,
                           java.util.UUID intended, java.util.UUID hit,
                           RitualSphereProjectileProvenancePolicy.Vec3 origin,
                           long expiresAt, long now) {{
        return RitualSphereProjectileProvenancePolicy.accepts(
                marker, owner, shooter, intended, hit, origin, expiresAt, now);
    }}

    public static void main(String[] args) {{
{body}
    }}
}}
"""


def _run_probe(tmp_path: Path, name: str, body: str) -> str:
    probe = tmp_path / f"{name}.java"
    probe.write_text(_HARNESS.format(name=name, body=body), encoding="utf-8")
    files = [PLUGIN / "domain/RitualSphereProjectileProvenancePolicy.java"]
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, files), str(probe)],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    run = subprocess.run(["java", "-cp", str(tmp_path), name], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    return run.stdout


def test_sphere_projectile_is_accepted_only_with_complete_provenance(tmp_path: Path) -> None:
    body = """
        require(accepts(true, OWNER, OWNER, TARGET, TARGET, ORIGIN, NOW + 1, NOW),
                "a fully identified sphere arrow must be accepted");
        require(!accepts(false, OWNER, OWNER, TARGET, TARGET, ORIGIN, NOW + 1, NOW),
                "a sphere arrow without the ritual marker is not an encounter projectile");
        require(!accepts(true, null, OWNER, TARGET, TARGET, ORIGIN, NOW + 1, NOW),
                "an owner-less projectile guesses at ownership");
        require(!accepts(true, OWNER, null, TARGET, TARGET, ORIGIN, NOW + 1, NOW),
                "a projectile without a logical shooter cannot be attributed");
        require(!accepts(true, OWNER, OTHER, TARGET, TARGET, ORIGIN, NOW + 1, NOW),
                "an owner that differs from the shooter must be refused");
        require(!accepts(true, OWNER, OWNER, null, TARGET, ORIGIN, NOW + 1, NOW),
                "a sphere arrow without an intended target is unattributable");
        require(!accepts(true, OWNER, OWNER, TARGET, null, ORIGIN, NOW + 1, NOW),
                "a hit without a hit target proves nothing");
        require(!accepts(true, OWNER, OWNER, TARGET, TARGET2, ORIGIN, NOW + 1, NOW),
                "a hit on a different player than the intended one must be refused");
    """
    _run_probe(tmp_path, "ProvenanceIdentityProbe", body)


def test_sphere_projectile_rejects_untrusted_geometry_and_lifetime(tmp_path: Path) -> None:
    body = """
        var nan = new RitualSphereProjectileProvenancePolicy.Vec3(Double.NaN, 64.0, 0.0);
        var inf = new RitualSphereProjectileProvenancePolicy.Vec3(0.0, Double.POSITIVE_INFINITY, 0.0);
        require(!accepts(true, OWNER, OWNER, TARGET, TARGET, nan, NOW + 1, NOW),
                "a NaN origin means the shooter position was never resolved");
        require(!accepts(true, OWNER, OWNER, TARGET, TARGET, inf, NOW + 1, NOW),
                "an infinite origin means the shooter position was never resolved");
        require(!accepts(true, OWNER, OWNER, TARGET, TARGET, null, NOW + 1, NOW),
                "a missing origin must be refused");
        require(!accepts(true, OWNER, OWNER, TARGET, TARGET, ORIGIN, NOW, NOW),
                "an already expired projectile must be refused");
        require(!accepts(true, OWNER, OWNER, TARGET, TARGET, ORIGIN, NOW - 1, NOW),
                "a projectile expiring in the past must be refused");
        require(accepts(true, OWNER, OWNER, TARGET, TARGET, ORIGIN, NOW + 1, NOW),
                "the boundary case of one tick of remaining life is valid");
    """
    _run_probe(tmp_path, "ProvenanceGeometryProbe", body)


def test_sphere_projectile_policy_is_a_pure_domain_boundary() -> None:
    source = (PLUGIN / "domain/RitualSphereProjectileProvenancePolicy.java").read_text(encoding="utf-8")

    # A provenance rule that can reach into the server cannot be tested or trusted.
    for forbidden in ("import org.bukkit", "import io.papermc", "Bukkit.", "Player ", "World "):
        assert forbidden not in source, f"provenance policy must not depend on {forbidden!r}"
    assert "private RitualSphereProjectileProvenancePolicy()" in source, "policy must not be instantiable"