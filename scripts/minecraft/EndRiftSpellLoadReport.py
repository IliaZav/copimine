#!/usr/bin/env python3
"""Turn the server's spell-load telemetry into the N23-P1-07 measurement table.

Master V5.1 (N23-P1-07) requires a *measured* reduction of the spell VFX load at
2 / 5 / 10 / 11 / 20 real players - "demonstrate a large measured reduction, not
a guessed percentage".  The End Event plugin already logs the counters that
matter on every diagnostics tick:

    END_EVENT_RUNTIME_DIAGNOSTICS event=... phase=... sample_interval_ms=...
        packet_quality=... pings=Ann=42ms,Bob=88ms avg_ping_ms=65 max_ping_ms=88
        owned_living=... displays=... projectiles=... wave_projectile_...
        particle_packets_sec=... plugin_messages_sec=...
        client_bindings_sent=... client_bindings_coalesced=...
        client_bindings_failed=... client_binding_cache_entries=...
        gc_collections=... gc_pause_ms=... server_thread_cpu_percent=...

This tool groups those samples by the number of online players, takes the median
per metric, and prints the ladder.  It deliberately refuses to invent numbers:

* ``bytes/second``, ``MSPT``, the Netty packet queue and client GPU frame time
  are **not** in this line.  They are listed under "still to capture by hand"
  so a reader can never mistake the table for a complete measurement.
* A reduction is printed only when both a baseline and a candidate log contain
  the same player count.  Otherwise the rung is reported as missing.
* The ladder is not treated as complete unless 2/5/10/11/20 are all present;
  a partial capture exits non-zero instead of looking like proof.

Usage::

    py -3 scripts/minecraft/EndRiftSpellLoadReport.py latest.log
    py -3 scripts/minecraft/EndRiftSpellLoadReport.py latest.log --baseline before.log
    py -3 scripts/minecraft/EndRiftSpellLoadReport.py --json latest.log
"""

from __future__ import annotations

import argparse
import json
import re
import statistics
import sys
from pathlib import Path

MARKER = "END_EVENT_RUNTIME_DIAGNOSTICS"
FIELD = re.compile(r"(\w+)=(\S+)")
LADDER = (2, 5, 10, 11, 20)

# Metrics that are present in the diagnostics line and worth reporting.
SERVER_METRICS = (
    "particle_packets_sec",
    "plugin_messages_sec",
    "client_bindings_sent",
    "client_bindings_coalesced",
    "client_bindings_failed",
    "client_binding_cache_entries",
    "gc_collections",
    "gc_pause_ms",
    "server_thread_cpu_percent",
    "avg_ping_ms",
)

# Metrics the spec requires but this telemetry does not carry.  Kept explicit so
# the report can never claim them as measured.
MANUAL_METRICS = (
    "bytes_per_second (Netty/plugin channel counters or spark)",
    "mspt (spark profile or server tick timings)",
    "packet queue depth (Netty channel metrics)",
    "client gpu frame time / FPS (in-game F3 or a frame-time mod)",
)


def player_count(pings: str) -> int:
    """The online-player count implied by the ping roster."""
    if not pings or pings.strip().lower() == "none":
        return 0
    return sum(1 for entry in pings.split(",") if "=" in entry)


def _number(token: str) -> float | None:
    try:
        return float(token)
    except (TypeError, ValueError):
        return None


def parse_log(text: str) -> list[dict]:
    """Every diagnostics sample in ``text``, as a flat dict (players + metrics)."""
    samples: list[dict] = []
    for line in text.splitlines():
        if MARKER not in line:
            continue
        fields = dict(FIELD.findall(line.split(MARKER, 1)[1]))
        players = player_count(fields.get("pings", ""))
        sample: dict = {"players": players}
        for metric in SERVER_METRICS:
            value = _number(fields.get(metric, ""))
            if value is not None:
                sample[metric] = value
        samples.append(sample)
    return samples


def summarise(samples: list[dict]) -> dict[int, dict]:
    """Median per metric, grouped by online-player count."""
    grouped: dict[int, list[dict]] = {}
    for sample in samples:
        grouped.setdefault(sample["players"], []).append(sample)
    summary: dict[int, dict] = {}
    for players, rows in grouped.items():
        entry: dict = {"samples": len(rows)}
        for metric in SERVER_METRICS:
            values = [row[metric] for row in rows if metric in row]
            if values:
                entry[metric] = statistics.median(values)
        summary[players] = entry
    return summary


def reduction(baseline: float, candidate: float) -> float | None:
    """Percent drop from baseline to candidate, or ``None`` if undefined."""
    if baseline <= 0.0:
        return None
    return (baseline - candidate) / baseline * 100.0


def build_report(candidate_samples: list[dict], baseline_samples: list[dict] | None) -> dict:
    candidate = summarise(candidate_samples)
    baseline = summarise(baseline_samples) if baseline_samples else {}
    rungs: dict[str, dict] = {}
    for players in sorted(set(candidate) | set(baseline)):
        cand = candidate.get(players, {})
        base = baseline.get(players, {})
        row: dict = {"players": players, "candidate": cand, "baseline": base}
        drops: dict[str, float | None] = {}
        for metric in SERVER_METRICS:
            if metric in cand and metric in base:
                drops[metric] = reduction(base[metric], cand[metric])
        row["reduction_percent"] = drops
        rungs[str(players)] = row
    missing = [players for players in LADDER if players not in candidate]
    return {
        "cadence_rungs_present": sorted(candidate),
        "cadence_rungs_missing": missing,
        "ladder_complete": not missing,
        "rungs": rungs,
        "not_captured": list(MANUAL_METRICS),
    }


def _fmt(value: float | None) -> str:
    return "-" if value is None else f"{value:.2f}"


def print_table(report: dict, out=sys.stdout) -> None:
    print("N23-P1-07 spell-load ladder (median per online-player count)", file=out)
    print(f"{'players':>7} {'samples':>7} {'particle_pkt/s':>15} "
          f"{'plugin_msg/s':>13} {'bind_sent':>9} {'bind_fail':>9} "
          f"{'gc_pause_ms':>11} {'cpu%':>6}", file=out)
    for key in sorted(report["rungs"], key=int):
        row = report["rungs"][key]
        cand = row["candidate"]
        print(f"{row['players']:>7} {cand.get('samples', 0):>7} "
              f"{_fmt(cand.get('particle_packets_sec')):>15} "
              f"{_fmt(cand.get('plugin_messages_sec')):>13} "
              f"{_fmt(cand.get('client_bindings_sent')):>9} "
              f"{_fmt(cand.get('client_bindings_failed')):>9} "
              f"{_fmt(cand.get('gc_pause_ms')):>11} "
              f"{_fmt(cand.get('server_thread_cpu_percent')):>6}", file=out)

    if any(row["reduction_percent"] for row in report["rungs"].values()):
        print("\nMeasured reduction vs baseline (percent, median):", file=out)
        for key in sorted(report["rungs"], key=int):
            row = report["rungs"][key]
            drops = {k: v for k, v in row["reduction_percent"].items() if v is not None}
            if not drops:
                continue
            joined = ", ".join(f"{name} {value:.1f}%" for name, value in sorted(drops.items()))
            print(f"  {row['players']:>2} players: {joined}", file=out)

    print("\nStill to capture by hand (not in this telemetry):", file=out)
    for item in report["not_captured"]:
        print(f"  - {item}", file=out)

    if report["ladder_complete"]:
        print("\nLadder complete: 2/5/10/11/20 all present.", file=out)
    else:
        present = ", ".join(str(p) for p in report["cadence_rungs_present"]) or "none"
        missing = ", ".join(str(p) for p in report["cadence_rungs_missing"])
        print(f"\nLADDER INCOMPLETE - present: {present}; missing: {missing}.", file=out)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+", type=Path)
    parser.add_argument("--baseline", action="append", type=Path, default=None,
                        help="a log captured before the optimisation (repeat for several)")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--allow-partial", action="store_true",
                        help="exit 0 even when a ladder rung is missing")
    args = parser.parse_args(argv)

    candidate_text = "\n".join(path.read_text(encoding="utf-8", errors="replace")
                               for path in args.logs)
    samples = parse_log(candidate_text)
    if not samples:
        print(f"no {MARKER} lines found", file=sys.stderr)
        return 2

    baseline_samples = None
    if args.baseline:
        baseline_text = "\n".join(path.read_text(encoding="utf-8", errors="replace")
                                  for path in args.baseline)
        baseline_samples = parse_log(baseline_text)
        if not baseline_samples:
            print(f"no {MARKER} lines in the baseline logs", file=sys.stderr)
            return 2

    report = build_report(samples, baseline_samples)
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print_table(report)

    if not report["ladder_complete"] and not args.allow_partial:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
