"""Supplied Chameleon assets must survive Windows Git checkout byte for byte."""

from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ASSETS = Path("CopiMineClient/src/main/resources/assets/copimineclient/models/entity/end_rift_guardian")


def test_supplied_guardian_json_bytes_survive_windows_checkout(tmp_path):
    checkout = tmp_path / "repo"
    checkout.mkdir()
    attributes = (ROOT / ".gitattributes").read_bytes()
    (checkout / ".gitattributes").write_bytes(attributes)
    supplied = {}
    for source in sorted((ROOT / ASSETS).rglob("*.json")):
        relative = source.relative_to(ROOT)
        supplied[relative] = source.read_bytes()
        target = checkout / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(supplied[relative])
    assert len(supplied) == 8, "exercise geometry and all seven supplied animations"
    for arguments in (
        ["init", "-q"], ["config", "core.autocrlf", "true"], ["add", "."],
        ["checkout-index", "--all", f"--prefix={(tmp_path / 'export').as_posix()}/"],
    ):
        result = subprocess.run(["git", "-C", str(checkout), *arguments],
                                capture_output=True, text=True, timeout=30)
        assert result.returncode == 0, result.stdout + result.stderr
    for relative, expected in supplied.items():
        assert (tmp_path / "export" / relative).read_bytes() == expected, (
            f"Windows Git checkout rewrote the supplied asset {relative}; "
            "preserve bytes with attributes instead of changing assets or hash tests"
        )
