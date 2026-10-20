from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def test_debug_surface_exposes_machine_readable_boss_and_event_entity_snapshots():
    source = SOURCE.read_text(encoding="utf-8")

    assert '"boss".equals(section) && hasJsonFlag(args)' in source
    assert '"evententities".equals(section) && hasJsonFlag(args)' in source
    assert "handleStructuredBossDiagnosticsJson(sender);" in source
    assert "handleStructuredEventEntitiesDiagnosticsJson(sender);" in source
    assert '"serverAuthoritativeHealth"' in source
    assert '"nativeEntityMaxHealth"' in source
    assert '"visualId"' in source
    assert '"animationId"' in source


def test_tentacle_snapshot_reports_rig_segments_and_server_contact_state():
    source = SOURCE.read_text(encoding="utf-8")

    assert '"tentacles".equals(section) && hasJsonFlag(args)' in source
    assert "handleStructuredTentacleDiagnosticsJson(sender);" in source
    assert '"segmentUuids"' in source
    assert '"segmentCount"' in source
    assert '"serverLogicalLength"' in source
    assert '"clientRenderScale"' in source
    assert '"hitboxWidth"' in source
    assert '"target"' in source
