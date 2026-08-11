from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
COMMON = (ROOT / "deploy" / "shared" / "common.sh").read_text(encoding="utf-8")
INSTALLER = (ROOT / "deploy" / "ubuntu" / "install_release.sh").read_text(encoding="utf-8")


def test_legacy_http_vhost_is_retired_by_the_normal_release_install():
    for path in (
        "/etc/nginx/sites-available/copimine-http.conf",
        "/etc/nginx/sites-enabled/copimine-http.conf",
    ):
        assert path in COMMON


def test_standalone_https_reconfigure_also_removes_legacy_http_vhost():
    assert "/etc/nginx/sites-enabled/copimine-http.conf" in INSTALLER
    assert "/etc/nginx/sites-available/copimine-http.conf" in INSTALLER
