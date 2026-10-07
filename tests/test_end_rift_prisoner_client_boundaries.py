"""Source boundary guards supplement executable ray and layout fixtures in Gradle."""
from pathlib import Path


CLIENT = Path(__file__).resolve().parents[1] / "CopiMineClient/src/main/java/me/copimine/client"


def test_prisoner_aim_is_independent_of_vanilla_melee_crosshair_reach():
    selector = (CLIENT / "PrisonerTargetSelector.java").read_text(encoding="utf-8")
    assert "client.crosshairTarget" not in selector, (
        "Prisoner support must select along its own bounded camera ray; vanilla entity hits stop at melee reach"
    )


def test_prisoner_renderer_delegates_placement_to_status_aware_layout():
    renderer = (CLIENT / "PrisonerHudRenderer.java").read_text(encoding="utf-8")
    assert "PrisonerHudLayout" in renderer, (
        "Fixed bottom placement intersects the hotbar and health/armor; use the tested status-aware layout"
    )
