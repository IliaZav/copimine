from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read_backend() -> str:
    return (ROOT / "admin-web" / "backend" / "main.py").read_text(encoding="utf-8")


def test_public_status_navigation_budget_is_separate_from_mutating_api_budget() -> None:
    source = read_backend()

    assert 'PUBLIC_STATUS_RATE_LIMIT = max(60, int(os.getenv("PUBLIC_STATUS_RATE_LIMIT", "120")))' in source
    assert 'check_rate_limit(request, "public-status", limit=PUBLIC_STATUS_RATE_LIMIT)' in source
