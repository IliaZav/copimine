"""V7-27 synthetic VFX load/packet-budget harness.

Pins the honesty contract of ``EndRiftSyntheticLoadHarness.py``:

* the cost model reads the *real* ``BossSpellGeometryPolicy.stride``,
  ``MAX_VIEWERS`` and ``MAX_POINTS`` out of the Java source, so a geometry
  change cannot silently keep a stale reduction,
* the candidate is ``O(casts)`` (one control packet per cast), not
  ``O(points * viewers * frames)`` like the legacy per-viewer streamer,
* the 2/5/10/11/20 ladder is complete,
* the JSON artifact declares ``SYNTHETIC_MODEL`` and ``native_measurement:
  BLOCKED`` — never a fake native benchmark,
* the emitted diagnostics log is parseable by ``EndRiftSpellLoadReport.py``.
"""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HARNESS = ROOT / "scripts/minecraft/EndRiftSyntheticLoadHarness.py"
REPORT = ROOT / "scripts/minecraft/EndRiftSpellLoadReport.py"
GEOMETRY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossSpellGeometryPolicy.java"
PRESENTATION = ROOT / "copimine-end-event/src/me/copimine/endevent/BossSpellPresentationEffects.java"
ARTIFACT = ROOT / "docs/end-rift/final-26.3/SYNTHETIC_LOAD_PROFILE.json"


def _load(path: Path, name: str):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


H = _load(HARNESS, "end_rift_synthetic_load_harness")
R = _load(REPORT, "end_rift_spell_load_report")


def test_stride_table_reads_the_real_java_policy() -> None:
    table = H._read_stride_table()
    text = GEOMETRY.read_text(encoding="utf-8")
    # Far threshold 28*28, mid 14*14, mid viewers 10, strides 4 and 2.
    assert table[0] == 28 * 28
    assert table[1] == 14 * 14
    assert table[2] == 10
    assert table[3] == 4
    assert table[4] == 2
    assert "distanceSquared > 28 * 28" in text


def test_max_viewers_reads_the_real_presentation_budget() -> None:
    text = PRESENTATION.read_text(encoding="utf-8")
    assert "MAX_VIEWERS = 32" in text
    assert H._read_max_viewers() == 32


def test_stride_matches_java_policy_semantics() -> None:
    table = H._read_stride_table()
    # Close + few viewers -> every point.
    assert H.stride_for(5.0 * 5.0, 2, table) == 1
    # Far -> stride 4.
    assert H.stride_for(30.0 * 30.0, 2, table) == 4
    # Mid distance or many viewers -> stride 2.
    assert H.stride_for(20.0 * 20.0, 2, table) == 2
    assert H.stride_for(5.0 * 5.0, 11, table) == 2
    # Out of range -> 0 (not admitted).
    assert H.stride_for(48.0 * 48.0 + 1, 2, table) == 0


def test_candidate_is_constant_and_baseline_grows_with_roster() -> None:
    table = H._read_stride_table()
    max_points = H._read_max_points()
    baselines = [H.legacy_packets_per_second(n, table, max_points) for n in H.LADDER]
    candidates = [H.candidate_packets_per_second(n) for n in H.LADDER]
    # Baseline grows super-linearly (points * viewers).
    assert baselines == sorted(baselines)
    assert baselines[-1] > baselines[0] * 10
    # Candidate stays tiny and bounded.
    assert all(candidate < 25.0 for candidate in candidates)
    # Real measured reduction at every rung.
    for baseline, candidate in zip(baselines, candidates):
        drop = (baseline - candidate) / baseline * 100.0
        assert drop > 95.0, f"expected a large reduction, got {drop:.1f}%"


def test_ladder_is_complete() -> None:
    model = H.build_model()
    assert [row["players"] for row in model["rungs"]] == list(H.LADDER)
    assert all(row["reduction_percent"] > 95.0 for row in model["rungs"])


def test_artifact_is_honestly_labelled(tmp_path: Path) -> None:
    out = tmp_path / "load.json"
    log_dir = tmp_path / "logs"
    assert H.main(["--json", str(out), "--log-dir", str(log_dir)]) == 0
    artifact = json.loads(out.read_text(encoding="utf-8"))
    assert artifact["evidence_kind"] == "SYNTHETIC_MODEL"
    assert artifact["native_measurement"] == "BLOCKED"
    assert artifact["native_blocked_reason"]
    assert artifact["native_upgrade_commands"]
    assert artifact["model_inputs"]["max_viewers"] == 32
    assert len(artifact["rungs"]) == 5
    # The four metrics this telemetry cannot produce stay explicit.
    joined = " ".join(artifact["not_captured"])
    for required in ("bytes_per_second", "mspt", "packet queue", "client gpu"):
        assert required in joined


def test_emitted_logs_parse_with_the_report_tool(tmp_path: Path) -> None:
    log_dir = tmp_path / "logs"
    H.write_logs(H.build_model(), log_dir)
    candidate = R.parse_log((log_dir / "candidate.log").read_text(encoding="utf-8"))
    baseline = R.parse_log((log_dir / "baseline.log").read_text(encoding="utf-8"))
    assert [sample["players"] for sample in candidate] == list(H.LADDER)
    report = R.build_report(candidate, baseline)
    assert report["ladder_complete"] is True
    for players in ("2", "5", "10", "11", "20"):
        drop = report["rungs"][players]["reduction_percent"]["particle_packets_sec"]
        assert drop > 95.0, f"expected a large drop at {players} players, got {drop}"


def test_shipped_artifact_matches_the_model() -> None:
    if not ARTIFACT.exists():
        return
    shipped = json.loads(ARTIFACT.read_text(encoding="utf-8"))
    model = H.build_model()
    assert shipped["evidence_kind"] == model["evidence_kind"] == "SYNTHETIC_MODEL"
    assert [row["players"] for row in shipped["rungs"]] == list(H.LADDER)
    for shipped_row, model_row in zip(shipped["rungs"], model["rungs"]):
        assert shipped_row["players"] == model_row["players"]
        assert abs(shipped_row["reduction_percent"] - model_row["reduction_percent"]) < 0.05
