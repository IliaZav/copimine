from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
COMMON = (ROOT / "deploy" / "shared" / "common.sh").read_text(encoding="utf-8")
INSTALLER = (ROOT / "deploy" / "ubuntu" / "install_release.sh").read_text(encoding="utf-8")
NGINX_HTTPS = (ROOT / "admin-web" / "deploy" / "nginx-copimine-admin-https.conf").read_text(encoding="utf-8")


def test_legacy_http_vhost_is_retired_by_the_normal_release_install():
    for path in (
        "/etc/nginx/sites-available/copimine-http.conf",
        "/etc/nginx/sites-enabled/copimine-http.conf",
    ):
        assert path in COMMON


def test_standalone_https_reconfigure_also_removes_legacy_http_vhost():
    assert "/etc/nginx/sites-enabled/copimine-http.conf" in INSTALLER
    assert "/etc/nginx/sites-available/copimine-http.conf" in INSTALLER


def test_launcher_feed_without_trailing_slash_redirects_to_canonical_feed_root():
    assert "location = /downloads/launcher" in NGINX_HTTPS
    assert "return 308 /downloads/launcher/;" in NGINX_HTTPS


def test_launcher_metadata_compatibility_alias_uses_the_canonical_public_file():
    assert "location = /downloads/launcher/latest.json" in NGINX_HTTPS
    assert "assets/public-data/launcher/latest.json" in NGINX_HTTPS
