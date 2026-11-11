"""Phase 3-5 of the Rift Guardian are structural mechanics, so they must be provable offline.

The complaint behind this milestone is that phase 3 "draws particle rings over the floor" and
that the later phases are decoration. The rebuild replaces that with real, reversible block
mutation, a real geyser with real damage, a full-arena inferno with visibly safe ground, and two
distinct Echo systems. None of that can be proven by a particle or a sound, so every decision that
can be pure was moved into a policy class and is proven here by executing the real Java:

* fracture cell selection, the widening crack, the warning band, the geyser column and the
  "one damage per eruption per player" ledger,
* the inferno's heating wave, its ~20 s duration, its visible cold patches and the ban on
  unavoidable per-tick global damage,
* Echo Type A's timeline, its single-hit guarantee and its dodgeability,
* Echo Type B's 50 HP / five-alive / 150 s cooldown contract plus the invariants that keep the
  encounter winnable (no shared boss HP, no defeat trigger, no loot, no orphans),
* the 14.6 admission gate that keeps skeleton towers out of every boss phase.

What this file cannot prove is listed in the milestone report: real BlockData strings, collision
and damage at the visible geyser, particles and sounds, and the entity lifecycle of the Echoes
still need a live Paper server.
"""
from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
SCOPE = DOMAIN / "BossPhase.java"

# (probe class, policy sources it needs)
PROBES = (
    ("RiftFractureEruptionPolicyTest", ("RiftFractureEruptionPolicy.java",)),
    ("InfernoArenaLifecyclePolicyTest", ("InfernoArenaLifecyclePolicy.java",)),
    ("EchoAttackReplicaPolicyTest", ("EchoAttackReplicaPolicy.java",)),
    ("EchoDecoyPolicyTest", ("EchoDecoyPolicy.java",)),
    ("BossSummonAdmissionPolicyTest",
     ("BossSummonAdmissionPolicy.java", "BossPhase.java")),
)


def _jdk_bin(name: str) -> Path:
    """Find a JDK tool on CI as well as in this checkout.

    CI installs Temurin into the runner's tool cache and exposes it through JAVA_HOME; only this
    workstation keeps a JDK under local-runtime/toolchains, so searching that directory alone
    made every probe in this repository fail on CI.
    """
    executable = name + (".exe" if os.name == "nt" else "")
    for variable in ("JAVA_HOME", "JAVA_HOME_25_X64", "JAVA_HOME_21_X64"):
        configured = os.environ.get(variable)
        if configured:
            candidate = Path(configured) / "bin" / executable
            if candidate.is_file():
                return candidate
    candidates = sorted(ROOT.glob("local-runtime/toolchains/temurin-*/jdk-*/bin/" + executable))
    if candidates:
        return candidates[0]
    found = shutil.which(name)
    assert found, f"{name} is required to compile this probe but was not found"
    return Path(found)


@pytest.mark.parametrize("probe_name,sources", PROBES)
def test_boss_phase_3_to_5_policies_hold(tmp_path: Path, probe_name: str, sources: list[str]) -> None:
    assert SCOPE.is_file(), f"boss phase source is missing: {SCOPE}"
    probe = ROOT / "tests" / f"{probe_name}.java"
    assert probe.is_file(), f"probe source is missing: {probe}"

    policy_sources = []
    for name in sources:
        path = DOMAIN / name
        assert path.is_file(), f"policy source is missing: {path}"
        policy_sources.append(str(path))

    javac = _jdk_bin("javac")
    java = javac.with_name("java.exe" if os.name == "nt" else "java")
    out = tmp_path / "out"

    compile_result = subprocess.run(
        [str(javac), "-proc:none", "-encoding", "UTF-8", "-d", str(out), str(probe), *policy_sources],
        capture_output=True, text=True, timeout=300,
    )
    assert compile_result.returncode == 0, (
        f"{probe_name} failed to compile:\n" + compile_result.stdout + compile_result.stderr
    )

    run_result = subprocess.run(
        [str(java), "-cp", str(out), probe_name],
        capture_output=True, text=True, timeout=300,
    )
    combined = run_result.stdout + run_result.stderr
    assert run_result.returncode == 0, f"{probe_name} failed:\n" + combined
    assert "OK" in run_result.stdout, combined
