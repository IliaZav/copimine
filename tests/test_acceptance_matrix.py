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
    # "/{filename}.html" reduces to "/" and would match every path; drop it.
    prefixes = tuple(p for p in (path.split("{")[0] for path in declared) if p.rstrip("/"))
    callers = module.frontend_callers()
    unknown = []
    for path, files in callers.items():
        if path in declared or any(path.startswith(p) and p.endswith("/") for p in prefixes):
            continue
        if any(path == p.rstrip("/") for p in prefixes):
            continue
        unknown.append(f"{path} ({', '.join(sorted(files))})")
    # Paths the bundled sources still contain but the shipped build cannot
    # reach. Each one is listed with the reason instead of being silently
    # allowed, and the assertion below fails if a real route ever appears.
    unreachable = {
        "/api/local-materials": "no such route; base-v4.js guards on !CM_SERVER_MODE, cms-v4.js sits in the content tab that cms-production-v8.js replaces, public-v4.js only after a published modUrl is missing",
        "/api/v6/admin": "admin-v6.js saveCollection('/admin/'+kind) is in the content tab cms-production-v8.js replaces",
        "/api/v6/admin/config": "admin-v6.js form v6-site-config is not in production.js allowedForms; /admin/site-settings replaced it",
        "/api/v6/admin/pages": "admin-v6.js cms-* forms are not in allowedForms; /admin/content/{section} replaced them",
        "/api/v6/admin/sanctions": "players-v6.js form v6-sanction-revoke is not in allowedForms, so the submit throws before any request",
        "/api/v6/payments": "site-v6.js form create-payment is not in allowedForms: no payment provider is configured",
    }
    assert not (unreachable.keys() & declared), (
        "a route now exists, drop it from the unreachable set: "
        + ", ".join(sorted(unreachable.keys() & declared))
    )
    unknown = [item for item in unknown if item.split(" ")[0] not in unreachable]
    assert sorted(unknown) == [], "frontend requests undeclared routes: " + "; ".join(sorted(unknown))