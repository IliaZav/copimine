"""Generate the acceptance matrix from code, so it cannot drift from reality.

Run:  python scripts/acceptance_matrix.py > docs/ACCEPTANCE_MATRIX.md
`tests/test_acceptance_matrix.py` regenerates it and fails when the committed
file differs, so a route that is added, loses its permission check or loses its
test shows up as a failing build instead of a stale table in a document.
"""
from __future__ import annotations

import collections
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
BACKEND = ROOT / "backend"
FRONTEND = ROOT / "frontend" / "assets" / "js"
TESTS = ROOT / "tests"

# Routes are declared on a router (`@router.get(...)`) or directly on the app
# (`@app.get(...)`), and a router may carry an `APIRouter(prefix=...)`.
DEC = re.compile(r'^\s*@(?:router|app)\.(get|post|patch|delete|put)\(\s*[\'"]([^\'"]+)[\'"]', re.M)
PREFIX = re.compile(r'^\s*router\s*=\s*APIRouter\(\s*(?:prefix\s*=\s*)?[\'"]([^\'"]+)[\'"]', re.M)
DEF = re.compile(r'^(?:async )?def (\w+)\(', re.M)
PERM = re.compile(r'current_user\(\s*request\s*(?:,\s*[\'"]([\w.]+)[\'"])?')
GAME_OP = re.compile(r'(?:request\.app\.state\.game|self\.game)\.(rpc|status)\(\s*"([\w.]+)"')
# rpc(action, ...) with a variable: the action comes from SUPPORTED_ACTIONS at
# request time, so the route is a game route too.
GAME_DYNAMIC = re.compile(r'(?:request\.app\.state\.game|self\.game)\.rpc\(\s*[a-zA-Z_]\w*\s*,')
GAME_METHOD = re.compile(r'(?:request\.app\.state\.game|self\.game)\.(link_start|link_confirm|wallet|transfer|checkout|catalog|health)\(')
LEGACY = re.compile(r'legacy\.(\w+)_sync\(')
DATA_KEY = re.compile(r'data\.get\(\s*"(\w+)"')
# Dead modules: map-v6.js and map.js never render (bluemap.js is the live map),
# admin.js is the legacy overview overridden by production.js.
DEAD = {"app-bundle.js", "map-v6.js", "map.js", "admin.js"}
CALL = (
    re.compile(r"X\.request\(\s*['\"]([^'\"]+)['\"]"),
    re.compile(r"api\(\s*['\"]([^'\"]+)['\"]"),
    re.compile(r"fetch\(\s*['\"](/api/[^'\"]+)['\"]"),
)


def routes():
    for path in sorted(BACKEND.glob("*.py")):
        source = path.read_text(encoding="utf-8")
        prefix_match = PREFIX.search(source)
        prefix = prefix_match.group(1) if prefix_match else ""
        marks = [(source[:m.start()].count("\n"), m.group(1).upper(), prefix + m.group(2))
                 for m in DEC.finditer(source)]
        lines = source.split("\n")
        merged = collections.OrderedDict()
        for index, (line_no, method, route) in enumerate(marks):
            end = marks[index + 1][0] if index + 1 < len(marks) else len(lines)
            block = "\n".join(lines[line_no:end])
            if route in merged:
                merged[route]["method"] += "/" + method
                merged[route]["block"] += "\n" + block
                continue
            merged[route] = {"method": method, "block": block, "line": line_no}
        for route, item in merged.items():
            block = item["block"]
            fn = DEF.search(block)
            perm = PERM.search(block)
            yield {
                "file": path.name,
                "func": fn.group(1) if fn else "?",
                "method": item["method"],
                "path": route,
                "perm": (perm.group(1) if perm and perm.group(1) else
                         ("session" if perm else "public")),
                "fresh": "fresh=True" in block,
                "ops": ([op for _, op in GAME_OP.findall(block)]
                        + (["SUPPORTED_ACTIONS[action]"] if GAME_DYNAMIC.search(block) else [])
                        + sorted({name for name in GAME_METHOD.findall(block)})),
                "legacy": [name + "_sync" for name in LEGACY.findall(block)],
                "fields": sorted(set(DATA_KEY.findall(block))),
                "idempotent": "receipt_lookup(" in block,
            }


def frontend_callers():
    """Route path -> JS files that request it.

    Frontend code writes paths relative to the API root (`/admin/audit`) while the
    backend declares full paths (`/api/v6/admin/audit`), so keys are normalized to
    the backend form before matching."""
    callers = collections.defaultdict(set)
    for file in sorted(FRONTEND.glob("*.js")):
        if file.name in DEAD:
            continue
        text = file.read_text(encoding="utf-8", errors="replace")
        for pattern in CALL:
            for raw in pattern.findall(text):
                key = raw.split("?")[0].rstrip("/")
                if key in {"", "/api", "/api/v6", "/api/v7"}:
                    continue
                if not key.startswith("/api/"):
                    key = "/api/v6" + (key if key.startswith("/") else "/" + key)
                if "/api/v7/" in key or key.startswith("/api/v7"):
                    continue  # v7 duplicates v6 route names; count it under v6
                callers[key].add(file.name)
    return callers


def match_callers(route, callers):
    route = route.rstrip("/")
    if route in callers:
        return sorted(callers[route])
    # A route with a {param} is called through its literal prefix.
    prefix = route.split("{")[0].rstrip("/")
    hits = {name for key, files in callers.items()
            if key.rstrip("/") == prefix and len(key) > len("/api/v6") for name in files}
    if hits:
        return sorted(hits)
    suffix = {name for key, files in callers.items()
              if key.startswith(prefix + "/") and len(key) > len("/api/v6")
              for name in files}
    return sorted(suffix)


def _test_blocks(source):
    blocks = {}
    for part in re.split(r"\n(?=def test_)", source):
        found = re.match(r"def (test_\w+)", part)
        if found:
            blocks[found.group(1)] = part
    return blocks


def route_regex(route):
    head = route.split("{")[0]
    if "{" in route:
        return re.escape(head)
    return re.escape(route) + r"(?=['\"?#]|$)"


def matching_tests(route, blocks):
    pattern = re.compile(r"['\"]" + route_regex(route))
    return sorted(name for name, block in blocks.items() if pattern.search(block))


def render():
    test_source = "\n".join(p.read_text(encoding="utf-8", errors="replace")
                            for p in TESTS.glob("*.py"))
    blocks = _test_blocks(test_source)
    callers = frontend_callers()

    rows = []
    for route in routes():
        tests = matching_tests(route["path"], blocks)
        rows.append({**route, "ui": match_callers(route["path"], callers),
                     "tests": tests})
    rows.sort(key=lambda r: (not (r["ops"] or r["legacy"]), not r["tests"], r["path"]))

    total = len(rows)
    with_adapter = sum(1 for r in rows if r["ops"] or r["legacy"])
    tested = sum(1 for r in rows if r["tests"])
    exposed = [r for r in rows if r["ui"]]
    exposed_untested = [r for r in exposed if not r["tests"]]
    game_ops = sum(1 for r in rows if r["ops"])

    out = [
        "# Acceptance matrix",
        "",
        "Generated by `scripts/acceptance_matrix.py` from `backend/`, `frontend/assets/js/`",
        "and `tests/`. `tests/test_acceptance_matrix.py` fails when this file is stale,",
        "so the table cannot quietly stop describing the code.",
        "",
        f"- routes: **{total}**",
        f"- routes that call the game bridge or the legacy economy: **{with_adapter}**",
        f"- routes covered by an automated test: **{tested}**",
        f"- routes called from the shipped frontend: **{len(exposed)}**",
        f"- frontend routes without an automated test: **{len(exposed_untested)}**",
        "",
        "`reauth` means the endpoint calls `current_user(..., fresh=True)` and therefore",
        "requires a password confirmation younger than ten minutes.",
        "",
        "| UI caller | HTTP | Route | Permission | Reauth | Game op | Request fields | Idempotent | Automated test |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for r in rows:
        ui = ", ".join(f"`{n}`" for n in r["ui"]) or "—"
        ops = ", ".join(f"`{n}`" for n in (r["ops"] + r["legacy"])) or "—"
        fields = ", ".join(r["fields"][:6]) or "—"
        tests = ", ".join(f"`{n}`" for n in r["tests"][:2]) or "**missing**"
        out.append("| " + " | ".join([
            ui, r["method"], f"`{r['path']}`", f"`{r['perm']}`",
            "yes" if r["fresh"] else "—", ops, fields,
            "yes" if r["idempotent"] else "—", tests,
        ]) + " |")

    out += ["", "## Frontend routes without an automated test", ""]
    if exposed_untested:
        for r in exposed_untested:
            out.append(f"- `{r['method']} {r['path']}` — `{r['perm']}`, "
                       f"called from {', '.join('`' + n + '`' for n in r['ui'])}")
    else:
        out.append("None.")

    backend = (BACKEND / "domain.py").read_text(encoding="utf-8")
    supported = re.search(r"SUPPORTED_ACTIONS = \{(.*?)\}", backend, re.S)
    ids = sorted(re.findall(r'"([\w.]+)"', supported.group(1))) if supported else []
    bulk = re.search(r"BULK_ACTIONS = \{(.*?)\}", backend, re.S)
    bulk_ids = sorted(re.findall(r'"([\w.]+)"', bulk.group(1))) if bulk else []
    # The endpoint filters the local catalogue by SUPPORTED_ACTIONS, so a
    # supported id without a catalogue entry is not reachable in the UI.
    catalogue = json.loads((ROOT / "private" / "admin-actions.json").read_text(encoding="utf-8"))
    defined = {row["id"] for row in catalogue["actions"]}
    offered = [item for item in ids if item in defined]
    undefined = [item for item in ids if item not in defined]

    out += ["", "## Actions the game bridge does not implement", "",
            "`GET /api/v6/admin/actions` returns ids from",
            "`backend.domain.SUPPORTED_ACTIONS` that also have an entry in",
            "`private/admin-actions.json`, and the player screen renders exactly that",
            "list — an unimplemented action never appears as a working button.", "",
            "- offered by the admin screen: " + ", ".join(f"`{i}`" for i in offered),
            "- in `SUPPORTED_ACTIONS` but absent from the catalogue, so never offered: "
            + (", ".join(f"`{i}`" for i in undefined) or "none"),
            "- bulk-capable: " + ", ".join(f"`{i}`" for i in bulk_ids),
            f"- game-bridge calls made by HTTP routes: **{game_ops}**",
            f"- the other {len(catalogue['actions']) - len(offered)} catalogue entries are not offered by the UI;",
            "  `unsupportedReason` says so in the API response.", ""]
    return "\n".join(out)


if __name__ == "__main__":
    import sys
    text = render()
    # Writing it here avoids PowerShell's UTF-16 redirection mangling the table.
    (ROOT / "docs" / "ACCEPTANCE_MATRIX.md").write_text(text + "\n", encoding="utf-8")
    if "--print" in sys.argv:
        print(text)