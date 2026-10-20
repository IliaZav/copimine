from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPAIR = ROOT / "tools" / "Repair-LocalRadmin.ps1"


def test_radmin_repair_is_scoped_to_local_copimine_endpoints() -> None:
    text = REPAIR.read_text(encoding="utf-8")

    assert "RvControlSvc" in text
    assert "Radmin VPN" in text
    assert "CopiMine Local Radmin TCP 25566" in text
    assert "CopiMine Local Radmin TCP 8092" in text
    assert "CopiMine Local Radmin ICMPv4" in text
    assert "-InterfaceAlias 'Radmin VPN'" in text
    assert "-Verb RunAs" in text
    assert "minecraft/server" not in text.lower()
    assert "production" not in text.lower()


def test_radmin_repair_keeps_interface_tuning_deterministic() -> None:
    text = REPAIR.read_text(encoding="utf-8")

    assert "-InterfaceMetric 1" in text
    assert "-NlMtuBytes 1500" in text
    assert "26.0.0.0/8" in text
    assert "26.202.172.80" in text
