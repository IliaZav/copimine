from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_ci_plugin_fixtures_are_pinned_and_not_latest() -> None:
    script = (ROOT / "tests" / "PrepareCopiMinePluginFixtures.ps1").read_text(encoding="utf-8")
    for marker in (
        "2.3.74-40684fb",
        "1.4.40",
        "1.0.2",
        "7f1df9c02b52c1d2f69949a0c465781ed8c53dd0c52234feec01785e675ff5d1",
        "2a5477fc80f71012e15ade1ce34dbeb836e17623b28db112492c0f1443c09721",
        "8d06d342489947a296f07fbd012ea0e9842242f5af8d534f2dc91b9d2f0721e6",
    ):
        assert marker in script
    uri_lines = [line.lower() for line in script.splitlines() if "uri =" in line.lower()]
    assert uri_lines and all("latest" not in line for line in uri_lines)
    assert "Get-FileHash -Algorithm SHA256" in script
