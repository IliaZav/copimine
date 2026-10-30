"""The acceptance matrix must describe the code that is actually shipped.

Regenerates `docs/ACCEPTANCE_MATRIX.md` in memory and compares it with the
committed file. A new route, a dropped permission check or a deleted test makes
this fail, which is the point: a hand-maintained table rots silently.
"""
import importlib.util
import pathlib

import pytest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "acceptance_matrix.py"
DOC = ROOT / "docs" / "ACCEPTANCE_MATRIX.md"


def _module():
    spec = importlib.util.spec_from_file_location("acceptance_matrix", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_matrix_document_is_current():
    rendered = _module().render()
    if not DOC.exists():
        pytest.fail("docs/ACCEPTANCE_MATRIX.md is missing: run scripts/acceptance_matrix.py")
    committed = DOC.read_text(encoding="utf-8").rstrip("\n")
    assert committed == rendered.rstrip("\n"), (
        "docs/ACCEPTANCE_MATRIX.md is out of date: run python scripts/acceptance_matrix.py"
    )


def test_every_admin_route_requires_a_permission():
    """No admin endpoint may rely on 'logged in' alone."""
    module = _module()
    bare = [r["path"] for r in module.routes()
            if r["path"].startswith("/api/v") and "/admin/" in r["path"]
            and r["perm"] in {"session", "public"}]
    assert bare == [], "admin routes without an explicit permission: " + ", ".join(bare)


def test_mutating_admin_routes_ask_for_a_fresh_password():
    """Destructive admin operations re-authenticate; the matrix column shows it."""
    module = _module()
    dangerous = (
        "/api/v6/admin/accounts",
        "/api/v6/admin/actions",
        "/api/v6/admin/actions/bulk",
        "/api/v6/admin/players/{identifier}/inventory/remove",
        "/api/v6/admin/releases",
        "/api/v7/ban",
        "/api/v7/recovery",
    )
    missing = [r["path"] for r in module.routes()
               if any(r["path"] == p or r["path"].startswith(p + "/") for p in dangerous)
               and set(r["method"].split("/")) & {"POST", "PATCH", "DELETE", "PUT"}
               and not r["fresh"]]
    assert missing == [], "dangerous routes without a fresh re-authentication: " + ", ".join(missing)


def test_frontend_calls_only_routes_the_backend_declares():
    """A typo in a JS request path would otherwise surface as a silent 404."""
    module = _module()
    declared = {r["path"] for r in module.routes()}
    prefixes = tuple(path.split("{")[0] for path in declared)
    callers = module.frontend_callers()
    unknown = []
    for path, files in callers.items():
        if path in declared or any(path.startswith(p) and p.endswith("/") for p in prefixes):
            continue
        if any(path == p.rstrip("/") for p in prefixes):
            continue
        unknown.append(f"{path} ({', '.join(sorted(files))})")
    assert sorted(unknown) == [], "frontend requests undeclared routes: " + "; ".join(sorted(unknown))