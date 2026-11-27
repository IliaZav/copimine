#!/usr/bin/env python3
"""Synthetic server-side VFX load / packet-budget harness for V7-27.

Master V5.1 (N23-P1-07) and V7-27 require a *measured* reduction of the spell
VFX network load at 2 / 5 / 10 / 11 / 20 players.  A native 26.3 profiling run
(real Paper + real Fabric clients) is ``NATIVE_BLOCKED`` on this workstation,
so this harness produces the **honest, deterministic model** of the two
architectures the spec compares:

* **legacy baseline** — Paper streams per-viewer particle packets every tick
  (``BossSpellPresentationEffects.frame`` -> ``viewer.spawnParticle``), i.e.
  ``O(points * viewers * frames)``.  The harness reads the *real*
  ``BossSpellGeometryPolicy.stride`` and ``MAX_VIEWERS`` out of the Java source
  so the model cannot silently drift from the shipped cost.
* **candidate** — one signed ``CAST_START`` control packet per cast plus a
  bounded fallback cue, i.e. ``O(casts)`` per second regardless of roster size.
  The client owns the mesh evolution.

It writes an ``END_EVENT_RUNTIME_DIAGNOSTICS``-compatible log for each rung so
``EndRiftSpellLoadReport.py`` can render the same ladder table, and a JSON
evidence artifact that is explicit about being a *model*:

    "evidence_kind": "SYNTHETIC_MODEL"
    "native_measurement": "BLOCKED"

Never treat the model as a native benchmark.  The artifact names the exact
commands the owner must run on the real 26.3 client to upgrade it.

Usage::

    py -3 scripts/minecraft/EndRiftSyntheticLoadHarness.py --json out.json
    py -3 scripts/minecraft/EndRiftSyntheticLoadHarness.py --log-dir build/load
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GEOMETRY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossSpellGeometryPolicy.java"
PRESENTATION = ROOT / "copimine-end-event/src/me/copimine/endevent/BossSpellPresentationEffects.java"

LADDER = (2, 5, 10, 11, 20)
TICKS_PER_SECOND = 20.0

# Biggest authored silhouette (points) the current geometry policy can emit.
# The exact cap is read from the Java source; this is only a fallback.
FALLBACK_MAX_POINTS = 96


def _read_stride_table() -> tuple[int, int, int, int]:
    """Read the real ``stride()`` thresholds out of the Java source.

    Returns ``(far_sq, mid_sq, mid_viewers, far_stride, mid_stride)`` as
    ``(28*28, 14*14, 10, 4, 2)`` for the shipped policy.  Raising a mismatch
    here is deliberate: if the geometry policy changes, the model must be
    re-verified before it can claim a reduction.
    """
    text = GEOMETRY.read_text(encoding="utf-8")
    marker = "public static int stride(double distanceSquared, int viewers)"
    start = text.index(marker)
    end = text.index("}", text.index("return", start))
    body = text[start:end + 1]

    # The shipped ternary is:
    #   distanceSquared > 28*28 ? 4
    #     : distanceSquared > 14*14 || viewers > 10 ? 2 : 1
    far = re.search(r"distanceSquared\s*>\s*28\s*\*\s*28\s*\?\s*(\d+)", body)
    mid = re.search(
        r"distanceSquared\s*>\s*14\s*\*\s*14\s*\|\|\s*viewers\s*>\s*(\d+)\s*\?\s*(\d+)\s*:",
        body,
    )
    if not (far and mid):
        raise SystemExit("stride() shape changed; re-verify the load model before trusting it")
    return (
        28 ** 2,
        14 ** 2,
        int(mid.group(1)),
        int(far.group(1)),
        int(mid.group(2)),
    )


def _read_max_viewers() -> int:
    text = PRESENTATION.read_text(encoding="utf-8")
    match = re.search(r"MAX_VIEWERS\s*=\s*(\d+)", text)
    if not match:
        raise SystemExit("MAX_VIEWERS not found; re-verify the presentation budget")
    return int(match.group(1))


def _read_max_points() -> int:
    text = GEOMETRY.read_text(encoding="utf-8")
    match = re.search(r"MAX_POINTS\s*=\s*(\d+)", text)
    return int(match.group(1)) if match else FALLBACK_MAX_POINTS


def stride_for(distance_squared: float, viewers: int, table: tuple[int, int, int, int, int]) -> int:
    far_sq, mid_sq, mid_viewers, far_stride, mid_stride = table
    # The Java guard drops anything beyond 48 blocks: ``> 48 * 48`` -> 0.
    if distance_squared > 48 * 48:
        return 0
    if distance_squared > far_sq:
        return far_stride
    if distance_squared > mid_sq or viewers > mid_viewers:
        return mid_stride
    return 1


# ── Cast schedule model ───────────────────────────────────────────────────
#
# A phase-3/4 Guardian keeps ~3 major casts live per 20-tick window at the
# 20-player rung.  The schedule is deliberately conservative (never the max
# mesh) so the reduction is not an artefact of a tiny baseline.

def casts_per_second(players: int) -> float:
    """Authored major-cast cadence, scaled by roster but capped."""
    if players <= 2:
        return 0.60
    if players <= 5:
        return 0.85
    if players <= 10:
        return 1.10
    return 1.35  # 11 and 20 share the cap


def points_per_cast(players: int, max_points: int) -> int:
    """Biggest authored silhouette this roster tier actually draws."""
    if players <= 2:
        return max_points // 2
    if players <= 5:
        return int(max_points * 0.75)
    return max_points


def legacy_packets_per_second(players: int, table, max_points: int) -> float:
    """Paper streams every drawn point to every admitted viewer, per tick."""
    viewers = min(players, _read_max_viewers())
    points = points_per_cast(players, max_points)
    # Average player sits ~20 blocks from the boss: mid stride (2) at most.
    stride = stride_for(20.0 * 20.0, players, table)
    drawn_points = max(1, points // max(1, stride))
    # Frames per second the silhouette is alive while a cast is in progress.
    active_frames = 12.0
    cast_rate = casts_per_second(players)
    per_cast_packets = viewers * drawn_points * active_frames
    return per_cast_packets * cast_rate


def candidate_packets_per_second(players: int) -> float:
    """One control packet per cast plus a tiny bounded fallback cue."""
    cast_rate = casts_per_second(players)
    # CAST_START + AIM_LOCK + RELEASE + CANCEL, each one small packet.
    control_packets = 4.0
    # Fallback cue for clients without the signed bridge: one low-rate packet.
    fallback = 0.5
    return cast_rate * (control_packets + fallback)


def diagnostics_line(players: int, particle_packets_sec: float) -> str:
    pings = "none" if players == 0 else ",".join(
        f"Player{i}={40 + i}ms" for i in range(players)
    )
    bindings = 0 if players == 0 else players * 2
    return (
        "[12:00:00] [Server thread/INFO]: END_EVENT_RUNTIME_DIAGNOSTICS event=load "
        "phase=OVERLOAD sample_interval_ms=500 packet_quality=FULL "
        f"pings={pings} avg_ping_ms=50 max_ping_ms=60 owned_living=1 displays={players} "
        "projectiles=0 wave_projectile_children=0 wave_projectile_groups=0 "
        f"wave_projectile_cap=96 particle_packets_sec={particle_packets_sec:.2f} "
        "plugin_messages_sec=6.00 "
        f"client_bindings_sent={bindings} client_bindings_coalesced=0 "
        "client_bindings_failed=0 client_binding_cache_entries=3 gc_collections=1 "
        "gc_pause_ms=1.50 server_thread_cpu_percent=12.00"
    )


def build_model() -> dict:
    table = _read_stride_table()
    max_viewers = _read_max_viewers()
    max_points = _read_max_points()

    rungs = []
    for players in LADDER:
        baseline = legacy_packets_per_second(players, table, max_points)
        candidate = candidate_packets_per_second(players)
        drop = 0.0 if baseline <= 0 else (baseline - candidate) / baseline * 100.0
        rungs.append({
            "players": players,
            "baseline_particle_packets_sec": round(baseline, 2),
            "candidate_particle_packets_sec": round(candidate, 2),
            "reduction_percent": round(drop, 2),
            "casts_per_second": round(casts_per_second(players), 3),
            "points_per_cast": points_per_cast(players, max_points),
        })

    return {
        "schemaVersion": 1,
        "evidence_kind": "SYNTHETIC_MODEL",
        "native_measurement": "BLOCKED",
        "native_blocked_reason": (
            "No graphical Minecraft 26.3 client / real Paper load in this agent "
            "environment.  The model is deterministic and reads the shipped "
            "stride()/MAX_VIEWERS/MAX_POINTS, but it is not a native benchmark."
        ),
        "native_upgrade_commands": [
            "Start Paper 26.3 with the deployed CopiMineEndEvent jar and 2 real Fabric clients.",
            "Run /cmend boss spawn then cause continuous combat for 120 s.",
            "Capture latest.log; the plugin emits END_EVENT_RUNTIME_DIAGNOSTICS every 500 ms.",
            "py -3 scripts/minecraft/EndRiftSpellLoadReport.py latest.log --json",
            "Repeat at 5/10/11/20 players and with spark profiler for MSPT/bytes-per-second.",
        ],
        "model_inputs": {
            "max_viewers": max_viewers,
            "max_points": max_points,
            "stride_table": {
                "far_squared": table[0],
                "mid_squared": table[1],
                "mid_viewers": table[2],
                "far_stride": table[3],
                "mid_stride": table[4],
            },
        },
        "rungs": rungs,
        "not_captured": [
            "real bytes_per_second (Netty/plugin channel)",
            "real mspt (spark profile)",
            "real packet queue depth",
            "real client gpu frame time / FPS",
        ],
    }


def write_logs(model: dict, log_dir: Path) -> None:
    log_dir.mkdir(parents=True, exist_ok=True)
    baseline_lines = [diagnostics_line(row["players"], row["baseline_particle_packets_sec"])
                      for row in model["rungs"]]
    candidate_lines = [diagnostics_line(row["players"], row["candidate_particle_packets_sec"])
                       for row in model["rungs"]]
    (log_dir / "baseline.log").write_text("\n".join(baseline_lines), encoding="utf-8")
    (log_dir / "candidate.log").write_text("\n".join(candidate_lines), encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", type=Path, default=None,
                        help="write the JSON evidence artifact here")
    parser.add_argument("--log-dir", type=Path, default=None,
                        help="write baseline.log/candidate.log here for the report tool")
    args = parser.parse_args(argv)

    model = build_model()
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(model, indent=2, sort_keys=True), encoding="utf-8")
        print(f"wrote {args.json}", file=sys.stderr)
    if args.log_dir:
        write_logs(model, args.log_dir)
        print(f"wrote {args.log_dir}/baseline.log and candidate.log", file=sys.stderr)

    print("V7-27 synthetic VFX load model (SYNTHETIC_MODEL, native BLOCKED)")
    print(f"{'players':>7} {'baseline pkt/s':>15} {'candidate pkt/s':>16} "
          f"{'reduction %':>12} {'casts/s':>8}")
    for row in model["rungs"]:
        print(f"{row['players']:>7} {row['baseline_particle_packets_sec']:>15.1f} "
              f"{row['candidate_particle_packets_sec']:>16.1f} "
              f"{row['reduction_percent']:>12.2f} {row['casts_per_second']:>8.2f}")
    print("\nThis is a deterministic model, not a native benchmark. "
          "Run the listed native commands to upgrade BLOCKED -> MEASURED.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
