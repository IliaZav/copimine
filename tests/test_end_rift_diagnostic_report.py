from __future__ import annotations

import json
from pathlib import Path

import pytest

from tests.tools.build_end_rift_diagnostic_report import (
    DiagnosticReportError,
    analyze_rows,
    load_jsonl,
    write_report,
)


def event(
    sequence: int,
    category: str,
    action: str,
    correlation: str = "attempt:test:1",
    *,
    generation: int = 1,
    tick: int = 100,
    fields: dict[str, object] | None = None,
) -> dict[str, object]:
    return {
        "sequence": sequence,
        "timestamp": f"2026-09-17T20:00:{sequence:02d}Z",
        "serverTick": tick,
        "generation": generation,
        "eventId": "end-rift-test",
        "category": category,
        "action": action,
        "severity": "INFO",
        "wave": 6,
        "phase": "WAVE_6",
        "playerId": None,
        "entityId": None,
        "relatedEntityId": None,
        "correlationId": correlation,
        "reason": "test",
        "fields": fields or {},
    }


def test_report_flags_projectile_spawn_without_terminal_remove() -> None:
    report = analyze_rows(
        [
            event(1, "RITUAL_PROJECTILE", "SPAWN", "projectile:1:p1"),
        ]
    )

    assert report.projectile_leaks == ["projectile:1:p1"]


def test_report_accepts_one_terminal_and_one_remove() -> None:
    report = analyze_rows(
        [
            event(1, "RITUAL_PROJECTILE", "SPAWN", "projectile:1:p1"),
            event(2, "RITUAL_PROJECTILE", "PLAYER_HIT", "projectile:1:p1"),
            event(3, "RITUAL_PROJECTILE", "REMOVE", "projectile:1:p1"),
        ]
    )

    assert report.projectile_leaks == []
    assert report.projectile_terminal_duplicates == []


def test_report_flags_prisoner_health_change_without_drain() -> None:
    rows = [
        event(1, "RITUAL_PRISONER", "SNAPSHOT", "prisoner:1:u1", fields={"health": 20.0}),
        event(2, "RITUAL_PRISONER", "SNAPSHOT", "prisoner:1:u1", fields={"health": 19.0}),
    ]

    report = analyze_rows(rows)

    assert "PRISONER_HEALTH_CHANGED_OUTSIDE_DRAIN" in report.invariant_failures


def test_report_accepts_prisoner_health_change_caused_by_drain() -> None:
    rows = [
        event(1, "RITUAL_PRISONER", "SNAPSHOT", "prisoner:1:u1", fields={"health": 20.0}),
        event(
            2,
            "RITUAL_PRISONER",
            "DRAIN",
            "prisoner:1:u1",
            fields={"healthBefore": 20.0, "appliedDrain": 2.0, "healthAfter": 18.0},
        ),
    ]

    report = analyze_rows(rows)

    assert "PRISONER_HEALTH_CHANGED_OUTSIDE_DRAIN" not in report.invariant_failures


def test_report_flags_wave7_coordinate_restore_mismatch() -> None:
    rows = [
        event(1, "WAVE7_BARRIER", "MUTATE", "wave7:1:b1", fields={"x": 1, "y": 69, "z": 2}),
        event(2, "WAVE7_BARRIER", "RESTORE", "wave7:1:b1", fields={"x": 1, "y": 69, "z": 3}),
    ]

    report = analyze_rows(rows)

    assert report.wave7_restore_mismatches == ["1,69,2"]


def test_report_accepts_completed_one_shot_task() -> None:
    report = analyze_rows(
        [
            event(1, "TASK", "CREATE", "task:1:17", fields={"taskId": 17}),
            event(2, "TASK", "COMPLETE", "task:1:17", fields={"taskId": 17}),
        ]
    )

    assert report.task_leaks == []


def test_report_whitelists_only_explicit_static_layout_entity() -> None:
    report = analyze_rows(
        [
            event(
                1,
                "ENTITY",
                "SPAWN",
                "entity:1:pad",
                fields={
                    "entityType": "ITEM_DISPLAY",
                    "kind": "PAD",
                    "persistent": True,
                    "persistencePolicy": "STATIC_EVENT_LAYOUT",
                },
            )
        ]
    )

    assert report.entity_leaks == []


def test_report_does_not_hide_generic_persistent_entity() -> None:
    report = analyze_rows(
        [
            event(
                1,
                "ENTITY",
                "SPAWN",
                "entity:1:mob",
                fields={
                    "entityType": "SKELETON",
                    "kind": "WAVE_GUARDIAN",
                    "persistent": True,
                },
            )
        ]
    )

    assert report.entity_leaks == ["entity:1:mob"]


def test_strict_report_rejects_unknown_terminal_action(tmp_path: Path) -> None:
    path = tmp_path / "unknown-terminal.jsonl"
    path.write_text(
        json.dumps(event(1, "RITUAL_PROJECTILE", "UNKNOWN_TERMINAL", "projectile:1:p1"))
        + "\n",
        encoding="utf-8",
    )
    with pytest.raises(DiagnosticReportError, match="unknown RITUAL_PROJECTILE action"):
        load_jsonl(path)


def test_strict_report_rejects_negative_resource_count(tmp_path: Path) -> None:
    path = tmp_path / "negative-count.jsonl"
    path.write_text(
        json.dumps(
            event(
                1,
                "EVENT",
                "SNAPSHOT",
                fields={"ownedEntityCount": -1},
            )
        )
        + "\n",
        encoding="utf-8",
    )
    with pytest.raises(DiagnosticReportError, match="ownedEntityCount"):
        load_jsonl(path)


def test_strict_jsonl_rejects_malformed_and_non_monotonic_sequences(tmp_path: Path) -> None:
    malformed = tmp_path / "malformed.jsonl"
    malformed.write_text("{not-json}\n", encoding="utf-8")
    with pytest.raises(DiagnosticReportError, match="malformed JSON"):
        load_jsonl(malformed)

    non_monotonic = tmp_path / "non-monotonic.jsonl"
    non_monotonic.write_text(
        "".join(json.dumps(event(sequence, "EVENT", "SNAPSHOT")) + "\n" for sequence in (2, 1)),
        encoding="utf-8",
    )
    with pytest.raises(DiagnosticReportError, match="strictly increasing"):
        load_jsonl(non_monotonic)


def test_report_writer_includes_machine_summary_and_run_identity(tmp_path: Path) -> None:
    run_dir = tmp_path / "20260917-200000-deadbeef-g1"
    run_dir.mkdir()
    rows = [event(1, "EVENT", "START")]
    metadata = {
        "repository": "IliaZav/copimine",
        "branch": "codex/end-rift-event",
        "gitHead": "deadbeef",
        "dirty": False,
        "diagnosticEventsDropped": 0,
    }

    report = write_report(run_dir, rows, metadata, {"plugin.jar": "abc123"})

    assert report.result == "PASS"
    assert (run_dir / "report.md").is_file()
    summary = json.loads((run_dir / "summary.json").read_text(encoding="utf-8"))
    assert summary["gitHead"] == "deadbeef"
    assert "EVENT/START" in summary["eventCounts"]
    markdown = (run_dir / "report.md").read_text(encoding="utf-8")
    assert "Run Identity" in markdown
    assert "plugin.jar" in markdown


def test_report_marks_dropped_diagnostics_as_incomplete(tmp_path: Path) -> None:
    run_dir = tmp_path / "run"
    run_dir.mkdir()
    rows = [event(1, "EVENT", "START")]
    metadata = {"gitHead": "deadbeef", "diagnosticEventsDropped": 2}

    report = write_report(run_dir, rows, metadata, {})

    assert report.result == "INCOMPLETE EVIDENCE"


def test_live_probe_rebuilds_report_after_persisting_report_result() -> None:
    script_path = Path(__file__).resolve().parents[1] / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1"
    script = script_path.read_text(encoding="utf-8")

    assert "$script:runMetadata['diagnosticReportResult']" in script
    assert script.count("build_end_rift_diagnostic_report.py") >= 2
