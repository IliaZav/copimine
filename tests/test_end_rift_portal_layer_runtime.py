"""Execute production portal adapters at detached Bukkit spawn/chunk boundaries."""
from pathlib import Path
import os
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


def declaration(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


@pytest.fixture(scope="module")
def portal_probe(tmp_path_factory):
    source = Path(os.environ.get("END_RIFT_EVENT_SOURCE", str(ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"))).read_text(encoding="utf-8")
    signatures = ["private void spawnPortalObjectiveVisuals(", "private List<UUID> spawnPortalModelVisual(",
                  "private ItemDisplay spawnPortalModelLayer(",
                  "private void refreshPortalObjectiveVisuals(",
                  "private void failPortalVisualRecovery(",
                  "private void removePortalModelVisual(",
                  "private void clearPortalModelVisuals(",
                  "private boolean isLiveOwnedEntity(",
                  "private String portalVisualLayerObservationText(",
                  "private void startPortalVisualAnimation(",
                  "private void cancelPortalVisualAnimation("]
    methods = [declaration(source, signature) for signature in signatures]
    for signature in ["private boolean isLivePortalModelLayer(",
                      "public void onPortalVisualEntitiesLoad(",
                      "private boolean hasPendingPortalVisualRebuild("]:
        if signature in source:
            methods.append(declaration(source, signature))
    if "public void onPortalVisualEntitiesLoad(" not in source:
        methods.append("public void onPortalVisualEntitiesLoad(EntitiesLoadEvent event) {}")
    directory = tmp_path_factory.mktemp("portal-layer-runtime")
    template = (ROOT / "tests/fixtures/PortalLayerRuntimeProbe.java.template").read_text(encoding="utf-8")
    java = directory / "PortalLayerRuntimeProbe.java"
    java.write_text(template.replace("// PRODUCTION_METHODS", "\n".join(methods)), encoding="utf-8")
    result = subprocess.run(["javac", "-J-Xmx128m", "-encoding", "UTF-8", "-d", str(directory),
                             str(DOMAIN / "PortalCapturePolicy.java"),
                             str(DOMAIN / "PortalPresentationPolicy.java"), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("scenario", [
    "frame", "inner", "shard", "transient", "invalid", "dead", "unregistered",
    "wrong-owner", "wrong-slot", "null", "exception", "duplicate", "exhausted-reload", "permanent",
    "closed", "close-during-spawn", "generation", "restart", "empty-start", "exhausted-task",
    "chunk-adoption", "chunk-duplicate", "exception-cleanup", "stale-chunk", "closed-collapse",
    "reentrant-refresh", "reload-during-spawn",
    "timer-close", "timer-generation", "all-exhausted", "repeat-chunk",
    "close-all-missing",
    "seeded-exhausted-reload", "exhaustion-safe-recovery", "exhausted-loaded-sibling",
    "explicit-restart-after-exhaustion", "multi-portal-exhaustion",
    "sibling-removed-during-transaction",
    "three-portals-observed", "missing-observed-layer",
])
def test_portal_layer_lifecycle(portal_probe, scenario):
    result = subprocess.run(["java", "-Xms8m", "-Xmx64m", "-cp", str(portal_probe),
                             "PortalLayerRuntimeProbe", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_wave3_live_runner_checks_all_nine_observed_portal_layers():
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8"
    )
    debug = declaration(source, "private void handleDebug(")
    observation = declaration(source, "private String portalVisualLayerObservationText(")
    runner = (ROOT / "tests/RunEndRiftVisualFivePlayerLive.ps1").read_text(encoding="utf-8")
    objectives = debug.split('if ("all".equals(section) || "objectives".equals(section))', 1)[1]
    objectives = objectives.split('if ("all".equals(section) || "hazards".equals(section))', 1)[0]

    assert "portalVisualLayerObservationText()" in objectives
    assert "isLivePortalModelLayer(" in observation
    assert "wavePortalVisualIndices" in observation and "wavePortalVisualLayers" in observation
    assert 'getLogger().info("END_RIFT_PORTALS_READY event=" + eventId + " count=3 sequential=true")' in source
    assert 'getLogger().info("END_RIFT_OBELISK_ASSAULT_READY event=" + eventId' in source
    assert '" real_blocks=true journal_entries=" + journalEntries.size()' in source
    assert "portalLayers=portals=3 observed=9/9 slots=" in runner
    assert "GATE\\+INNER\\+SHARD,1:GATE\\+INNER\\+SHARD,2:GATE\\+INNER\\+SHARD" in runner
    assert "visuals=(\\d+)" not in runner
    assert "WAVE_FRONT_STARTED" not in runner
    assert "END_RIFT_PORTALS_READY.*count=3.*sequential=true" in runner
    assert "END_RIFT_OBELISK_ASSAULT_READY.*real_blocks=true" in runner
