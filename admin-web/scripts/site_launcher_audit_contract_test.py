"""Contract checks for the public site/admin navigation and Launcher release.

The checks intentionally use only repository files and synthetic local release
artifacts. They do not connect to production services or databases.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FRONTEND = ROOT / "admin-web" / "frontend"


def release_root() -> Path:
    configured = os.getenv("COPIMINE_SITE_RELEASE_ROOT", "").strip()
    if configured:
        return Path(configured).resolve()
    candidates = [
        path
        for path in (ROOT / "artifacts" / "launcher" / "Release").glob("site-*")
        if (path / "assets" / "public-data" / "launcher" / "latest.json").is_file()
    ]
    if candidates:
        return max(candidates, key=lambda path: path.stat().st_mtime_ns)
    return FRONTEND


def fail(message: str) -> None:
    raise AssertionError(message)


def read(path: Path) -> str:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def check_navigation() -> None:
    index = read(FRONTEND / "index.html")
    if index.count('id="downloadLauncherBtn"') != 1:
        fail("home page must have exactly one Launcher CTA")
    if "downloadModsBtn" in index or "Скачать модпак" in index:
        fail("home page still exposes the obsolete duplicate modpack CTA")

    cabinet_files = sorted((FRONTEND / "cabinet").glob("*.html"))
    if not cabinet_files:
        fail("cabinet pages were not found")
    for page in cabinet_files:
        html = read(page)
        if 'class="public-nav' not in html and 'href="/mods.html"' not in html:
            continue
        if re.search(r'<a\s+href="/mods\.html">\s*Модпак\s*</a>', html):
            fail(f"legacy Modpack header link remains in {page.name}")
        if not re.search(r'<a\s+href="/launcher\.html">\s*Лаунчер\s*</a>', html):
            fail(f"canonical Launcher header link is missing in {page.name}")

    mods = read(FRONTEND / "mods.html")
    if 'http-equiv="refresh"' in mods.lower():
        fail("mods compatibility page must not paint a legacy page before redirect")
    if not re.search(r"location\.replace\(['\"]/launcher\.html['\"]\)", mods):
        fail("mods compatibility page must redirect to /launcher.html before rendering")

    runtime = read(FRONTEND / "assets" / "js" / "cabinet-runtime.js")
    if "adminSearchSectionItems.unshift" in runtime:
        fail("admin search still prepends a duplicate shop section")
    if re.search(r'\["shops",\s*"Лавки"', runtime):
        fail("shops and artifacts still use the same admin label")

    site_data = read(FRONTEND / "assets" / "js" / "public" / "site-data.js")
    homepage_start = site_data.index("export async function loadPublicHomepageData")
    homepage_end = site_data.index("export async function loadPublicAuthState", homepage_start)
    homepage_loader = site_data[homepage_start:homepage_end]
    for retired_loader in ("loadPublicHomePageData(", "loadPublicServerPageData(", "loadPublicShopsPageData(", "fetchModpackPayload("):
        if retired_loader in homepage_loader:
            fail(f"homepage loader still duplicates retired request group: {retired_loader}")


def check_launcher_release() -> None:
    launcher_page = read(FRONTEND / "launcher.html")
    if launcher_page.count('id="launcherFolderBtn"') != 1:
        fail("launcher page must expose exactly one folder-picker installer button")
    if "MSI" in launcher_page or "CopiMineLauncherSetup-" in launcher_page:
        fail("launcher page exposes a legacy installer option")

    published_root = release_root()
    metadata_path = published_root / "assets" / "public-data" / "launcher" / "latest.json"
    metadata = json.loads(read(metadata_path))
    for field in ("version", "filename", "downloadUrl", "sha256"):
        if not metadata.get(field):
            fail(f"launcher metadata field is empty: {field}")

    for filename_field, url_field, hash_field, size_field in (
        ("filename", "downloadUrl", "sha256", "sizeBytes"),
        ("msiFilename", "msiDownloadUrl", "msiSha256", "msiSizeBytes"),
    ):
        filename = metadata.get(filename_field)
        url = metadata.get(url_field)
        expected_hash = metadata.get(hash_field)
        expected_size = metadata.get(size_field)
        if not all((filename, url, expected_hash, expected_size)):
            fail(f"launcher metadata is missing the {filename_field} package")
        if url != f"/downloads/launcher/{filename}":
            fail(f"unsafe or inconsistent download URL for {filename}")
        artifact = published_root / "downloads" / "launcher" / filename
        if not artifact.is_file():
            fail(f"published installer is missing: {artifact.relative_to(ROOT)}")
        digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
        if artifact.stat().st_size != expected_size or digest != expected_hash:
            fail(f"installer metadata does not match bytes: {filename}")

    stable = published_root / "launcher" / "stable"
    manifest_path = stable / "instance-manifest.json"
    signature_path = stable / "instance-manifest.sig"
    if not manifest_path.is_file() or not signature_path.is_file():
        fail("signed stable instance manifest is incomplete")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    managed_root = published_root / "launcher" / "files"
    for item in manifest.get("files", []):
        digest = item.get("sha256")
        if not digest:
            fail("manifest file entry has no sha256")
        managed_file = managed_root / digest
        if not managed_file.is_file():
            fail(f"manifest managed file is missing: {digest}")
        if managed_file.stat().st_size != item.get("size"):
            fail(f"manifest size mismatch: {digest}")
        if hashlib.sha256(managed_file.read_bytes()).hexdigest() != digest:
            fail(f"manifest hash mismatch: {digest}")
    java = manifest.get("javaRuntime", {})
    runtime = manifest.get("minecraftRuntime", {})
    for entry in (java, runtime):
        digest = entry.get("sha256")
        if not digest or not (managed_root / digest).is_file():
            fail("manifest runtime artifact is missing")

    assets_feed_path = published_root / "downloads" / "launcher" / "assets.win.json"
    if not assets_feed_path.is_file():
        fail("Velopack assets.win.json is missing")
    assets_feed = json.loads(read(assets_feed_path))
    for asset in assets_feed:
        filename = asset.get("RelativeFileName")
        if not isinstance(filename, str) or not re.fullmatch(r"[A-Za-z0-9._-]+", filename):
            fail(f"unsafe Velopack asset filename: {filename}")
        if not (published_root / "downloads" / "launcher" / filename).is_file():
            fail(f"Velopack asset listed in assets.win.json is missing: {filename}")


def main() -> int:
    check_navigation()
    check_launcher_release()
    print("Site/Launcher audit contracts OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
