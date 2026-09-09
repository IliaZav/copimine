from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = (ROOT / "scripts" / "local-stack.ps1").read_text(encoding="utf-8")


def test_local_stack_uses_runtime_independent_sha1_and_bounded_jvm_overrides() -> None:
    assert "function Get-Sha1Hex" in RUNNER
    assert "Get-FileHash" not in RUNNER
    assert "MC_JVM_XMS" in RUNNER
    assert "MC_JVM_XMX" in RUNNER
    assert "-Xms$MinecraftJvmXms" in RUNNER
    assert "-Xmx$MinecraftJvmXmx" in RUNNER
