from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VALIDATOR = ROOT / "tests" / "ValidateCopiMineReleaseCleanlinessAndGuide.ps1"
ADMIN_SOURCE = ROOT / "copimine-admin-plugin" / "src" / "me" / "copimine" / "ultimateplus" / "CopiMineUltimateAdminPlus.java"
INDEX = ROOT / "admin-web" / "frontend" / "index.html"


def test_release_cleanliness_validator_matches_postgresql_admin_storage_and_current_copy() -> None:
    validator = VALIDATOR.read_text(encoding="utf-8")
    admin_source = ADMIN_SOURCE.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")

    assert "CopiMineUltimateAdmin\\copimine_ultimate.db" not in validator
    assert "POSTGRES_PASSWORD" in admin_source
    assert "Баланс и покупки" in index
    assert "Аккаунт и файлы сервера" not in validator
