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


def test_invisible_portal_only_skips_its_iteration_and_generation_still_aborts():
    source = SOURCE.read_text(encoding="utf-8")
    task = method(source, "private void startPortalVisualAnimation")
    refresh = method(source, "private void refreshPortalObjectiveVisuals")
    animate = method(source, "private void animatePortalModelVisuals")

    task_generation_guard = task.index("portalVisualGeneration != generation || generation != callbackGeneration")
    task_callback = task.index("refreshPortalObjectiveVisuals(")
    refresh_generation_guard = refresh.index(
        "if (generation != expectedGeneration || portalVisualGeneration != expectedGeneration) return;"
    )
    refresh_loop = refresh.index("for (int index = 0; index < Math.min(states.size(), portals.size());")
    invisible_guard = animate.index("if (!frame.visible())")

    assert task_generation_guard < task_callback
    assert refresh_loop < refresh_generation_guard
    assert "portalVisualGeneration != expectedGeneration" in refresh
    assert "removePortalModelVisual(modelId, \"portal-collapse-complete\"); continue;" in animate[invisible_guard:]
    assert "if (!frame.visible()) { removePortalModelVisual(modelId, \"portal-collapse-complete\"); continue; }" in animate
    assert "generation != expectedGeneration || !frame.visible()" not in refresh + animate


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
