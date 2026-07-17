#!/usr/bin/env python3
"""Verify that frontend API references resolve to matching FastAPI routes."""
from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend" / "main.py"
FRONTEND_JS = ROOT / "frontend" / "assets" / "js"

ROUTE_RE = re.compile(
    r'@app\.(get|post|put|patch|delete)\("([^"?]+)',
    re.IGNORECASE,
)
REFERENCE_RE = re.compile(r'(?P<quote>["\'`])(?P<path>/api/.*?)(?P=quote)', re.DOTALL)
CALL_RE = re.compile(
    r'\b(?P<call>api|safeApi|fetch|fetchJson|requestJson)\s*\(\s*(?P<quote>["\'`])(?P<path>/api/.*?)(?P=quote)',
    re.DOTALL,
)
METHOD_RE = re.compile(r'\bmethod\s*:\s*["\'](GET|POST|PUT|PATCH|DELETE)["\']', re.IGNORECASE)


def clean_path(path: str) -> str:
    return path.split("?", 1)[0].rstrip("/") or "/"


def segments(path: str) -> list[str]:
    return [segment for segment in clean_path(path).strip("/").split("/") if segment]


def dynamic_segment(segment: str) -> bool:
    return (segment.startswith("{") and segment.endswith("}")) or "${" in segment


def route_matches(reference: str, route: str) -> bool:
    left = segments(reference)
    right = segments(route)
    return len(left) == len(right) and all(
        a == b or dynamic_segment(a) or dynamic_segment(b)
        for a, b in zip(left, right)
    )


def matching_paren(source: str, opening: int) -> int:
    depth = 0
    quote = ""
    escaped = False
    for index in range(opening, len(source)):
        char = source[index]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = ""
            continue
        if char in {'"', "'", "`"}:
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return index
    return len(source)


def main() -> None:
    backend = BACKEND.read_text(encoding="utf-8")
    routes = {
        (match.group(1).upper(), clean_path(match.group(2)))
        for match in ROUTE_RE.finditer(backend)
    }

    sources = {
        path: path.read_text(encoding="utf-8")
        for path in FRONTEND_JS.rglob("*.js")
        if "legacy" not in path.parts
    }
    references = {
        clean_path(match.group("path"))
        for source in sources.values()
        for match in REFERENCE_RE.finditer(source)
        if clean_path(match.group("path")) != "/api"
    }
    unmatched_paths = sorted(
        reference
        for reference in references
        if not any(route_matches(reference, route) for _, route in routes)
    )

    method_calls: set[tuple[str, str]] = set()
    for source in sources.values():
        for match in CALL_RE.finditer(source):
            if clean_path(match.group("path")) == "/api":
                continue
            opening = source.find("(", match.start())
            closing = matching_paren(source, opening)
            call_source = source[opening:closing]
            method_match = METHOD_RE.search(call_source)
            method = method_match.group(1).upper() if method_match else "GET"
            method_calls.add((method, clean_path(match.group("path"))))

    unmatched_methods = sorted(
        (method, reference)
        for method, reference in method_calls
        if not any(method == route_method and route_matches(reference, route) for route_method, route in routes)
    )

    assert not unmatched_paths, "Frontend API paths without backend routes: " + ", ".join(unmatched_paths)
    assert not unmatched_methods, "Frontend API methods without backend routes: " + ", ".join(
        f"{method} {path}" for method, path in unmatched_methods
    )
    print(
        "Frontend/backend contracts OK: "
        f"routes={len(routes)} references={len(references)} direct_calls={len(method_calls)}"
    )


if __name__ == "__main__":
    main()
