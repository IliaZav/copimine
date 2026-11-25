"""N23-P1-07: the load-measurement harness must measure, never invent.

Master V5.1 requires a *measured* reduction of spell VFX load at 2/5/10/11/20
players.  A native capture is `NATIVE_BLOCKED` on this workstation (no running
Paper, no GUI clients), so the honest deliverable is the tool that turns the
server's own diagnostics log into that table - and refuses to look like proof
when the evidence is incomplete.

The log lines below are **format fixtures**, not measurements: they exercise the
parser and the guard rails.  The test pins four behaviours that keep the report
honest:

* the online-player count comes from the real ping roster,
* metrics are medians per player count,
* an incomplete 2/5/10/11/20 ladder exits non-zero and says what is missing,
* metrics the log does not carry (bytes/s, MSPT, queue, GPU) are listed as
  "still to capture", never emitted as measured numbers.
"""
from __future__ import annotations

import importlib.util
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/minecraft/EndRiftSpellLoadReport.py"


def _load_module():
    spec = importlib.util.spec_from_file_location("end_rift_spell_load_report", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


REPORT = _load_module()


def line(players: int, particle: float = 120.0, messages: float = 6.0,
         sent: float = 3.0, gc: float = 1.5, cpu: float = 12.0,
         failed: float = 0.0) -> str:
    pings = "none" if players == 0 else ",".join(
        f"Player{i}={40 + i}ms" for i in range(players)
    )
    return (
        "[12:00:00] [Server thread/INFO]: END_EVENT_RUNTIME_DIAGNOSTICS event=e1 "
        "phase=WAVE_6 sample_interval_ms=500 packet_quality=FULL "
        f"pings={pings} avg_ping_ms=50 max_ping_ms=60 owned_living=1 displays=2 "
        "projectiles=0 wave_projectile_children=0 wave_projectile_groups=0 "
        f"wave_projectile_cap=96 particle_packets_sec={particle:.2f} "
        f"plugin_messages_sec={messages:.2f} client_bindings_sent={sent} "
        f"client_bindings_coalesced=0 client_bindings_failed={failed} "
        f"client_binding_cache_entries=3 gc_collections=1 gc_pause_ms={gc:.2f} "
        f"server_thread_cpu_percent={cpu:.2f}"
    )


def test_player_count_comes_from_the_ping_roster() -> None:
    assert REPORT.player_count("none") == 0
    assert REPORT.player_count("Ann=42ms") == 1
    assert REPORT.player_count("Ann=42ms,Bob=88ms,Cid=7ms") == 3


def test_samples_are_parsed_and_medians_taken_per_player_count() -> None:
    text = "\n".join([
        line(5, particle=100.0),
        line(5, particle=300.0),
        line(2, particle=50.0),
    ])
    samples = REPORT.parse_log(text)
    assert len(samples) == 3
    assert [sample["players"] for sample in samples] == [5, 5, 2]

    summary = REPORT.summarise(samples)
    assert summary[5]["samples"] == 2
    assert summary[5]["particle_packets_sec"] == 200.0  # median of 100 and 300
    assert summary[2]["particle_packets_sec"] == 50.0


def test_unavailable_values_are_skipped_not_coerced_to_zero() -> None:
    text = line(5).replace("particle_packets_sec=120.00",
                           "particle_packets_sec=unavailable")
    samples = REPORT.parse_log(text)
    assert "particle_packets_sec" not in samples[0]


def test_incomplete_ladder_is_reported_and_missing_metrics_stay_explicit() -> None:
    text = "\n".join(line(n) for n in (2, 5))
    report = REPORT.build_report(REPORT.parse_log(text), None)

    assert report["ladder_complete"] is False
    assert report["cadence_rungs_missing"] == [10, 11, 20]
    # The four spec metrics this telemetry cannot produce are named, not faked.
    joined = " ".join(report["not_captured"])
    for required in ("bytes_per_second", "mspt", "packet queue", "client gpu"):
        assert required in joined
    # And none of them leaked into the measured metrics.
    for row in report["rungs"].values():
        for metric in ("bytes_per_second", "mspt", "packet_queue", "gpu_frame_ms"):
            assert metric not in row["candidate"]


def test_complete_ladder_reduces_against_a_real_baseline() -> None:
    baseline = "\n".join(line(n, particle=200.0) for n in (2, 5, 10, 11, 20))
    candidate = "\n".join(line(n, particle=50.0) for n in (2, 5, 10, 11, 20))
    report = REPORT.build_report(REPORT.parse_log(candidate), REPORT.parse_log(baseline))

    assert report["ladder_complete"] is True
    assert report["cadence_rungs_missing"] == []
    for players in ("2", "5", "10", "11", "20"):
        drop = report["rungs"][players]["reduction_percent"]["particle_packets_sec"]
        assert drop == 75.0, f"expected a 200 -> 50 drop at {players} players, got {drop}"


def test_a_rung_without_a_baseline_prints_no_reduction() -> None:
    candidate = "\n".join(line(n, particle=50.0) for n in (2, 5, 10, 11, 20))
    baseline = "\n".join(line(n, particle=200.0) for n in (2, 5, 10))
    report = REPORT.build_report(REPORT.parse_log(candidate), REPORT.parse_log(baseline))

    assert report["rungs"]["11"]["reduction_percent"] == {}
    assert report["rungs"]["2"]["reduction_percent"]["particle_packets_sec"] == 75.0


def test_cli_exits_non_zero_on_a_partial_capture(tmp_path: Path) -> None:
    log = tmp_path / "partial.log"
    log.write_text("\n".join(line(n) for n in (2, 5)), encoding="utf-8")

    result = subprocess.run(
        [sys.executable, str(SCRIPT), str(log)],
        capture_output=True, text=True, timeout=120,
    )
    assert result.returncode == 3, result.stdout + result.stderr
    assert "LADDER INCOMPLETE" in result.stdout
    assert "Still to capture by hand" in result.stdout


def test_cli_exits_zero_on_a_complete_capture(tmp_path: Path) -> None:
    log = tmp_path / "complete.log"
    log.write_text("\n".join(line(n) for n in (2, 5, 10, 11, 20)), encoding="utf-8")

    result = subprocess.run(
        [sys.executable, str(SCRIPT), str(log)],
        capture_output=True, text=True, timeout=120,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    assert "Ladder complete" in result.stdout
