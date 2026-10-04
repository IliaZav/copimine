from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def method(source, name):
    start = source.index(name)
    open_brace = source.index("{", start)
    depth = 1
    end = open_brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[open_brace:end]


def test_portal_display_transform_uses_authoritative_capture_presentation():
    source = SOURCE.read_text(encoding="utf-8")
    animation = method(source, "private void animatePortalModelVisuals")
    assert "PortalPresentationPolicy" in animation
    assert "wavePortalVisualIndices" in animation


def test_portal_presentation_is_not_conditional_on_probe_text_displays():
    source = SOURCE.read_text(encoding="utf-8")
    spawn = method(source, "private void spawnPortalObjectiveVisuals")
    refresh = method(source, "private void refreshPortalObjectiveVisuals")
    assert "isOfficialAttempt" not in spawn
    assert "TextDisplay" not in spawn + refresh
    assert "shouldRebuild" in refresh


def test_completed_portals_stop_rendering_after_owned_collapse():
    source = SOURCE.read_text(encoding="utf-8")
    render = method(source, "private void renderPortalObjective")
    update = method(source, "private void updatePortalObjective")
    cleanup = method(source, "private void clearLegacyWave3PortalArtifacts")
    assert "visible()" in render
    assert "gaugeSegments()" in render
    assert "portalClosingStartedMillis" in update
    assert "collapseFinished" in update
    assert "clearPortalModelVisuals" in cleanup
