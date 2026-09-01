from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_public_motion_reveals_sections_as_they_enter_the_viewport() -> None:
    script = read("admin-web/frontend/assets/js/public/public-motion.js")
    styles = read("admin-web/frontend/assets/css/public-motion.css")

    assert "IntersectionObserver" in script
    assert 'data-motion-reveal' in script
    assert 'window.addEventListener("scroll"' in script
    assert "--scroll-progress" in script
    assert '[data-motion-reveal="ready"]' in styles
    assert ".is-visible" in styles
    assert "prefers-reduced-motion" in script
    assert "prefers-reduced-motion: reduce" in styles


def test_cabinet_motion_waits_for_runtime_views_and_keeps_boot_content_visible() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    script_path = ROOT / "admin-web/frontend/assets/js/cabinet-motion.js"
    styles_path = ROOT / "admin-web/frontend/assets/css/cabinet-motion.css"
    assert script_path.exists(), "cabinet motion module is missing"
    assert styles_path.exists(), "cabinet motion stylesheet is missing"
    script = script_path.read_text(encoding="utf-8")
    styles = styles_path.read_text(encoding="utf-8")
    cabinet = read("admin-web/frontend/assets/cabinet.css")

    assert 'from "./cabinet-motion.js?v=20260901motion1"' in runtime
    assert "initCabinetMotion()" in runtime
    assert "MutationObserver" in script
    assert 'data-cabinet-reveal' in script
    assert 'data-cabinet-motion="ready"' in styles
    assert "--cabinet-scroll-progress" in script
    assert "prefers-reduced-motion: reduce" in styles
    assert '@import url("./css/cabinet-motion.css?v=20260901motion1");' in cabinet


def test_motion_layers_do_not_hide_content_before_javascript_marks_readiness() -> None:
    public_styles = read("admin-web/frontend/assets/css/public-motion.css")
    styles_path = ROOT / "admin-web/frontend/assets/css/cabinet-motion.css"
    assert styles_path.exists(), "cabinet motion stylesheet is missing"
    cabinet_styles = styles_path.read_text(encoding="utf-8")

    assert '[data-motion-reveal="ready"] [data-motion-reveal]' in public_styles
    assert '[data-cabinet-motion="ready"] [data-cabinet-reveal]' in cabinet_styles
    assert ".public-site [data-motion-reveal] {" not in public_styles
    assert ".workspace [data-cabinet-reveal] {" not in cabinet_styles


def test_motion_release_busts_public_and_cabinet_entrypoint_caches() -> None:
    public_style_key = "20260902flat5"
    public_module_key = "20260902motion2"
    cabinet_style_key = "20260902flat5"
    cabinet_app_key = "20260901motion1"
    pages = list((ROOT / "admin-web/frontend").glob("*.html"))
    pages += list((ROOT / "admin-web/frontend/news").glob("*.html"))
    for page in pages:
        source = page.read_text(encoding="utf-8")
        if "/assets/style.css" in source:
            assert f"/assets/style.css?v={public_style_key}" in source, page.name
        if "/assets/js/public/public-page.js" in source:
            assert f"/assets/js/public/public-page.js?v={public_module_key}" in source, page.name

    cabinet_pages = list((ROOT / "admin-web/frontend/cabinet").glob("*.html"))
    for page in cabinet_pages:
        source = page.read_text(encoding="utf-8")
        if "/assets/cabinet.css" in source:
            assert f"/assets/cabinet.css?v={cabinet_style_key}" in source, page.name
        if "/assets/app.js" in source:
            assert f"/assets/app.js?v={cabinet_app_key}" in source, page.name

    assert f'./public-motion.js?v={public_module_key}' in read("admin-web/frontend/assets/js/public/public-page.js")
    assert f'./js/bootstrap.js?v={cabinet_app_key}' in read("admin-web/frontend/assets/app.js")
    assert f'./cabinet-runtime.js?v={cabinet_app_key}' in read("admin-web/frontend/assets/js/bootstrap.js")
