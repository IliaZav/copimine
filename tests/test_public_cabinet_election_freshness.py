from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_cabinet_does_not_render_technical_identity_block():
    source = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    match = re.search(
        r"async function loadPlayerCabinet\(\).*?\n\}\n\nasync function loadPlayerPurchasesHub",
        source,
        re.S,
    )
    assert match, "player cabinet renderer must remain discoverable"
    section = match.group(0)
    assert 'aria-label="Быстрые действия кабинета"' in section
    assert "Быстрые переходы по кабинету." in section
    assert '["Логин", state.user.username' not in section
    assert '["Whitelist", whitelistStatus' not in section


def test_public_elections_refreshes_live_data_and_has_live_summary():
    html = read("admin-web/frontend/elections.html")
    data = read("admin-web/frontend/assets/js/public/site-data.js")
    homepage = read("admin-web/frontend/assets/js/public/homepage.js")
    render = read("admin-web/frontend/assets/js/public/site-render.js")

    for marker in (
        'id="publicElectionHeroStatus"',
        'id="publicElectionHeroCandidates"',
        'id="publicElectionHeroVotes"',
        'id="publicElectionRefresh"',
        "public-page.js?v=20260825siteui17",
    ):
        assert marker in html, marker
    assert 'const freshPath' in data
    assert 'cache: "no-store"' in data
    assert '"Cache-Control": "no-cache"' in data
    assert "function bindElectionRefresh()" in homepage
    assert "setInterval" in homepage
    assert "electionRefreshInFlight" in homepage
    assert "publicElectionHeroStatus" in render
    assert "candidateCount" in render
    assert "electionHeroVotes" in render
