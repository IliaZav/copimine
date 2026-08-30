from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_event_video_upload_is_unlimited_in_both_nginx_templates():
    for relative in (
        "admin-web/deploy/nginx-copimine-admin-https.conf",
        "deploy/ubuntu/install_release.sh",
    ):
        text = read(relative)
        assert "client_max_body_size 0;" in text
        assert "client_max_body_size 8m;" not in text


def test_event_video_backend_streams_upload_without_fixed_application_limit():
    text = read("admin-web/backend/main.py")
    assert "1024 * 1024" in text
    assert "sha256" in text
    assert "os.replace" in text
    assert "MAX_VIDEO" not in text
