"""The Git checkout must be deployable without an untracked developer data folder."""
import json
from pathlib import Path

from test_auth import client, login  # noqa: F401

ROOT = Path(__file__).resolve().parents[1]
PUBLIC_REFERENCE_FILES = (
    "catalog-current.json", "catalog-v6-public.json", "content-v6-public.json",
    "materials-base.json", "materials-v6.json", "materials.json",
    "map-manifest.json", "map-source.json",
)


def test_required_reference_files_are_packaged_and_valid_json():
    for name in PUBLIC_REFERENCE_FILES:
        path = ROOT / "frontend" / "data" / name
        assert path.is_file(), f"Missing release input: {path}"
        assert isinstance(json.loads(path.read_text(encoding="utf-8")), (dict, list))
    private = ROOT / "private" / "admin-actions.json"
    assert private.is_file(), "Administrative definitions belong to the private release inputs"
    assert json.loads(private.read_text(encoding="utf-8"))["actions"]
    assert not (ROOT / "frontend" / "data" / "admin-actions.json").exists()


def test_static_data_allowlist_does_not_expose_unpublished_files(client):
    assert client.get("/data/materials-v6.json").status_code == 200
    assert client.head("/data/materials-v6.json").status_code == 200
    assert client.get("/data/admin-actions.json").status_code == 404
    path = ROOT / "frontend" / "data" / "not-a-release-input.json"
    assert not path.exists()
    try:
        path.write_text('{"private": true}', encoding="utf-8")
        assert client.get("/data/not-a-release-input.json").status_code == 404
    finally:
        path.unlink(missing_ok=True)


def test_admin_actions_use_protected_api_not_public_static_data(client):
    assert client.get("/api/v6/admin/actions").status_code == 401
    login(client)
    result = client.get("/api/v6/admin/actions")
    assert result.status_code == 200, result.text
    assert result.json()["actions"]
    assert client.get("/data/admin-actions.json").status_code == 404


def test_reference_catalog_search_aliases_are_not_blank():
    for name in ("catalog-current.json", "catalog-v6-public.json"):
        catalog = json.loads((ROOT / "frontend" / "data" / name).read_text(encoding="utf-8"))
        for item in catalog:
            assert all(isinstance(alias, str) and alias.strip() for alias in item.get("aliases", [])), item["id"]
