"""Strict, deterministic report generation for one End Rift diagnostic run.

The parser intentionally accepts only the central structured JSONL schema.  A
human console line or a malformed JSON record is an evidence failure, not a
reason to silently skip data.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Iterable, Mapping


MANDATORY_FIELDS = {
    "sequence",
    "timestamp",
    "serverTick",
    "generation",
    "eventId",
    "category",
    "action",
    "severity",
    "wave",
    "phase",
    "correlationId",
    "reason",
}
TERMINAL_PROJECTILE_ACTIONS = {
    "PLAYER_HIT",
    "MISS",
    "TIMEOUT",
    "BLOCK_COLLISION",
}
LIFECYCLE_ACTIONS = {
    "RITUAL_PROJECTILE": {
        "SPAWN", "STEER", "STEER_SAMPLE", "COLLISION", "PLAYER_HIT",
        "MISS", "TIMEOUT", "BLOCK_COLLISION", "REMOVE",
    },
    "ENTITY": {"SPAWN", "REMOVE"},
    "TASK": {"CREATE", "COMPLETE", "CANCEL"},
    "RITUAL_CONTROL": {"START", "STOP"},
    "BOSS_HITBOX": {
        "PROXY_SPAWN", "CLEANUP", "REMOVE", "RECOVERY_FAIL", "SNAPSHOT",
        "REJECT", "RAY_REJECT", "DUPLICATE_DROP", "HIT_ROUTE",
        "PROJECTILE_REJECT", "PROJECTILE_RAY_REJECT",
        "PROJECTILE_DUPLICATE_DROP", "PROJECTILE_ROUTE", "PROJECTILE_ACCEPT",
    },
}
NON_NEGATIVE_FIELD_NAMES = {
    "activeWave", "ritualCasterCount", "ritualGuardCount", "ritualProjectileCount",
    "ritualZoneCount", "ritualControlCount", "ownedEntityCount", "activeTaskCount",
    "bossHitboxProxyCount", "wave7TemporaryBlockCount", "wave7JournalEntryCount",
    "expectedCasters", "actualCasters", "expectedGuards", "actualGuards",
    "expectedBossProxies", "actualBossProxies", "expectedWallCells", "actualWallCells",
    "taskId", "slot", "chamber", "operationMillis", "serverTick", "generation",
}


class DiagnosticReportError(ValueError):
    """Raised when a run cannot be trusted as structured evidence."""


@dataclass
class DiagnosticReport:
    result: str
    event_counts: dict[str, int] = field(default_factory=dict)
    first_timestamp: str = ""
    last_timestamp: str = ""
    tick_range: tuple[int, int] = (0, 0)
    generations: list[int] = field(default_factory=list)
    invariant_failures: list[str] = field(default_factory=list)
    exception_fingerprints: dict[str, int] = field(default_factory=dict)
    projectile_leaks: list[str] = field(default_factory=list)
    projectile_terminal_duplicates: list[str] = field(default_factory=list)
    entity_leaks: list[str] = field(default_factory=list)
    task_leaks: list[str] = field(default_factory=list)
    control_leaks: list[str] = field(default_factory=list)
    boss_proxy_leaks: list[str] = field(default_factory=list)
    wave7_restore_mismatches: list[str] = field(default_factory=list)
    stale_packet_count: int = 0
    diagnostic_events_dropped: int = 0
    write_failures: int = 0
    max_queue_depth: int = 0
    log_rotations: int = 0

    def summary(self) -> dict[str, object]:
        return {
            "result": self.result,
            "eventCounts": self.event_counts,
            "firstTimestamp": self.first_timestamp,
            "lastTimestamp": self.last_timestamp,
            "tickRange": list(self.tick_range),
            "generations": self.generations,
            "invariantFailures": self.invariant_failures,
            "exceptionFingerprints": self.exception_fingerprints,
            "projectileLeaks": self.projectile_leaks,
            "projectileTerminalDuplicates": self.projectile_terminal_duplicates,
            "entityLeaks": self.entity_leaks,
            "taskLeaks": self.task_leaks,
            "controlLeaks": self.control_leaks,
            "bossProxyLeaks": self.boss_proxy_leaks,
            "wave7RestoreMismatches": self.wave7_restore_mismatches,
            "stalePacketCount": self.stale_packet_count,
            "diagnosticEventsDropped": self.diagnostic_events_dropped,
            "writeFailures": self.write_failures,
            "maxQueueDepth": self.max_queue_depth,
            "logRotations": self.log_rotations,
        }


def load_jsonl(path: Path) -> list[dict[str, object]]:
    """Read and validate one central JSONL stream in strict sequence order."""

    if not path.is_file():
        raise DiagnosticReportError(f"diagnostic JSONL file is missing: {path}")
    rows: list[dict[str, object]] = []
    previous_sequence: int | None = None
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw_line.strip():
            raise DiagnosticReportError(f"blank JSONL line at {path}:{line_number}")
        try:
            value = json.loads(raw_line)
        except json.JSONDecodeError as error:
            raise DiagnosticReportError(
                f"malformed JSON at {path}:{line_number}: {error.msg}"
            ) from error
        if not isinstance(value, dict):
            raise DiagnosticReportError(f"JSONL record at {path}:{line_number} is not an object")
        missing = sorted(MANDATORY_FIELDS - value.keys())
        if missing:
            raise DiagnosticReportError(
                f"missing mandatory fields at {path}:{line_number}: {', '.join(missing)}"
            )
        sequence = value["sequence"]
        if isinstance(sequence, bool) or not isinstance(sequence, int) or sequence < 1:
            raise DiagnosticReportError(f"invalid sequence at {path}:{line_number}")
        if previous_sequence is not None and sequence <= previous_sequence:
            raise DiagnosticReportError(
                f"sequence must be strictly increasing at {path}:{line_number}: "
                f"{sequence} after {previous_sequence}"
            )
        previous_sequence = sequence
        server_tick = value["serverTick"]
        generation = value["generation"]
        if isinstance(server_tick, bool) or not isinstance(server_tick, int) or server_tick < 0:
            raise DiagnosticReportError(f"invalid non-negative serverTick at {path}:{line_number}")
        if isinstance(generation, bool) or not isinstance(generation, int) or generation < 0:
            raise DiagnosticReportError(f"invalid non-negative generation at {path}:{line_number}")
        wave = value["wave"]
        if wave is not None and (isinstance(wave, bool) or not isinstance(wave, int)):
            raise DiagnosticReportError(f"invalid wave at {path}:{line_number}")
        for name in ("timestamp", "eventId", "category", "action", "severity", "phase", "correlationId", "reason"):
            if not isinstance(value[name], str):
                raise DiagnosticReportError(f"field {name} must be a string at {path}:{line_number}")
        if not value["correlationId"].strip():
            raise DiagnosticReportError(f"correlationId must not be empty at {path}:{line_number}")
        lifecycle_actions = LIFECYCLE_ACTIONS.get(value["category"])
        if lifecycle_actions is not None and value["action"] not in lifecycle_actions:
            raise DiagnosticReportError(
                f"unknown {value['category']} action at {path}:{line_number}: {value['action']}"
            )
        fields = value.get("fields", {})
        if not isinstance(fields, dict):
            raise DiagnosticReportError(f"fields must be an object at {path}:{line_number}")
        for field_name, field_value in fields.items():
            if field_name in NON_NEGATIVE_FIELD_NAMES and (
                isinstance(field_value, bool)
                or not isinstance(field_value, (int, float))
                or field_value < 0
            ):
                raise DiagnosticReportError(
                    f"field {field_name} must be a non-negative number at {path}:{line_number}"
                )
        try:
            datetime.fromisoformat(value["timestamp"].replace("Z", "+00:00"))
        except ValueError as error:
            raise DiagnosticReportError(f"invalid timestamp at {path}:{line_number}") from error
        rows.append(value)
    return rows


def analyze_rows(rows: Iterable[Mapping[str, object]]) -> DiagnosticReport:
    """Analyze structured rows and flag lifecycle/invariant mismatches."""

    ordered = list(rows)
    if not ordered:
        return DiagnosticReport(result="NOT RUN")
    sequences = [row.get("sequence") for row in ordered]
    if any(not isinstance(sequence, int) for sequence in sequences):
        raise DiagnosticReportError("all rows must have integer sequences")
    if sequences != sorted(set(sequences)):
        raise DiagnosticReportError("rows must have unique strictly increasing sequences")

    event_counts = Counter(
        f"{row.get('category', '')}/{row.get('action', '')}" for row in ordered
    )
    timestamps = [str(row["timestamp"]) for row in ordered]
    ticks = [int(row["serverTick"]) for row in ordered]
    generations = sorted({int(row["generation"]) for row in ordered})

    invariant_failures: set[str] = set()
    exception_fingerprints: Counter[str] = Counter()
    stale_packet_count = 0
    for row in ordered:
        category = str(row.get("category", ""))
        action = str(row.get("action", ""))
        fields = row.get("fields")
        fields_map = fields if isinstance(fields, Mapping) else {}
        if category == "ASSERTION" and action == "INVARIANT_FAIL":
            invariant_failures.add(str(fields_map.get("invariant", row.get("reason", "UNKNOWN"))))
        if category == "ERROR" and action == "FAIL":
            exception_fingerprints[str(fields_map.get("exceptionType", "Unknown"))] += 1
        if category == "PACKET" and action in {"STALE", "DROP"}:
            stale_packet_count += 1

    projectile_leaks, projectile_duplicates = _correlation_lifecycle(
        ordered, "RITUAL_PROJECTILE", "SPAWN", {"REMOVE", *TERMINAL_PROJECTILE_ACTIONS},
        require_remove=True,
    )
    entity_leaks, _ = _correlation_lifecycle(
        ordered, "ENTITY", "SPAWN", {"REMOVE"}, require_remove=True,
        allow_persistent_layout=True,
    )
    task_leaks, _ = _correlation_lifecycle(
        ordered, "TASK", "CREATE", {"CANCEL", "COMPLETE"}, require_remove=False
    )
    control_leaks, _ = _correlation_lifecycle(
        ordered, "RITUAL_CONTROL", "START", {"STOP"}, require_remove=False
    )
    boss_proxy_leaks, _ = _correlation_lifecycle(
        ordered, "BOSS_HITBOX", "PROXY_SPAWN", {"CLEANUP", "REMOVE"}, require_remove=False
    )

    mutated = _wave7_coordinates(ordered, "MUTATE")
    restored = _wave7_coordinates(ordered, "RESTORE")
    wave7_restore_mismatches = sorted(mutated - restored)
    if wave7_restore_mismatches:
        invariant_failures.add("WAVE7_CLEANUP_RESIDUE")

    if _prisoner_health_changed_outside_drain(ordered):
        invariant_failures.add("PRISONER_HEALTH_CHANGED_OUTSIDE_DRAIN")

    leaks = (
        projectile_leaks
        + entity_leaks
        + task_leaks
        + control_leaks
        + boss_proxy_leaks
    )
    result = "FAIL" if invariant_failures or leaks or exception_fingerprints else "PASS"
    return DiagnosticReport(
        result=result,
        event_counts=dict(sorted(event_counts.items())),
        first_timestamp=timestamps[0],
        last_timestamp=timestamps[-1],
        tick_range=(min(ticks), max(ticks)),
        generations=generations,
        invariant_failures=sorted(invariant_failures),
        exception_fingerprints=dict(sorted(exception_fingerprints.items())),
        projectile_leaks=projectile_leaks,
        projectile_terminal_duplicates=projectile_duplicates,
        entity_leaks=entity_leaks,
        task_leaks=task_leaks,
        control_leaks=control_leaks,
        boss_proxy_leaks=boss_proxy_leaks,
        wave7_restore_mismatches=wave7_restore_mismatches,
        stale_packet_count=stale_packet_count,
    )


def write_report(
    run_dir: Path,
    rows: Iterable[Mapping[str, object]],
    metadata: Mapping[str, object] | None,
    artifact_hashes: Mapping[str, str],
) -> DiagnosticReport:
    """Write report.md and summary.json from structured rows, never by hand."""

    run_dir.mkdir(parents=True, exist_ok=True)
    metadata = dict(metadata or {})
    report = analyze_rows(rows)
    report.diagnostic_events_dropped = _nonnegative_int(
        metadata.get("diagnosticEventsDropped", 0), "diagnosticEventsDropped"
    )
    report.write_failures = _nonnegative_int(
        metadata.get("diagnosticWriteFailures", 0), "diagnosticWriteFailures"
    )
    report.max_queue_depth = _nonnegative_int(
        metadata.get("diagnosticMaxQueueDepth", 0), "diagnosticMaxQueueDepth"
    )
    report.log_rotations = _nonnegative_int(
        metadata.get("diagnosticRotations", 0), "diagnosticRotations"
    )
    live_paper_result = str(metadata.get("livePaperResult", "")).upper()
    if report.result == "PASS" and live_paper_result in {"FAIL", "FAILED"}:
        report.result = "FAIL"
    if report.diagnostic_events_dropped or report.write_failures:
        report.result = "INCOMPLETE EVIDENCE"

    summary = {
        **report.summary(),
        "repository": metadata.get("repository", ""),
        "branch": metadata.get("branch", ""),
        "gitHead": metadata.get("gitHead", ""),
        "dirty": bool(metadata.get("dirty", False)),
        "dirtyStatus": metadata.get("dirtyStatus", []),
        "serverVersion": metadata.get("serverVersion", ""),
        "javaVersion": metadata.get("javaVersion", ""),
        "diagnosticMode": metadata.get("diagnosticMode", ""),
        "nativeMinecraft": metadata.get("nativeMinecraft", "NOT VERIFIED"),
        "livePaperResult": metadata.get("livePaperResult", "NOT RECORDED"),
        "diagnosticReportResult": metadata.get("diagnosticReportResult", "NOT RECORDED"),
        "runDirectory": metadata.get("runDirectory", ""),
        "artifactHashes": dict(sorted(artifact_hashes.items())),
    }
    (run_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    (run_dir / "report.md").write_text(
        _render_markdown(report, metadata, artifact_hashes), encoding="utf-8"
    )
    return report


def _render_markdown(
    report: DiagnosticReport,
    metadata: Mapping[str, object],
    artifact_hashes: Mapping[str, str],
) -> str:
    lines = [
        "# END RIFT OBSERVABILITY AND BUG AUDIT",
        "",
        "## Run Identity",
        "",
        f"- repository: `{metadata.get('repository', '')}`",
        f"- branch: `{metadata.get('branch', '')}`",
        f"- baseline/reference SHA: `{metadata.get('referenceSha', '')}`",
        f"- actual tested SHA: `{metadata.get('gitHead', '')}`",
        f"- dirty: `{bool(metadata.get('dirty', False))}`",
        f"- Paper: `{metadata.get('serverVersion', 'unknown')}`",
        f"- Java: `{metadata.get('javaVersion', 'unknown')}`",
        f"- diagnostic mode: `{metadata.get('diagnosticMode', 'unknown')}`",
        f"- diagnostic run directory: `{metadata.get('runDirectory', '')}`",
        "",
        "## Result Summary",
        "",
        f"**{report.result}**",
        "",
        f"- timestamps: `{report.first_timestamp}` → `{report.last_timestamp}`",
        f"- server ticks: `{report.tick_range[0]}` → `{report.tick_range[1]}`",
        f"- generations: `{','.join(map(str, report.generations))}`",
        "",
        "## 1. Diagnostic System Changes",
        "",
        "Central structured events, bounded asynchronous JSONL output, sequence IDs, correlation IDs, burst capture, invariant monitoring, strict report parsing, and artifact identity are enabled for this run.",
        "",
        "## 2. Bugs Found",
        "",
        "No additional runtime bug is promoted to FIXED from this report without a structured reproduction bundle and regression evidence.",
        "",
        "| ID | Severity | Subsystem | Symptom | Root cause | Regression test | Fix commit | Automated result | Live result | Native-client result | Evidence |",
        "|---|---|---|---|---|---|---|---|---|---|---|",
        "| BUG-ER-AUDIT | INFO | observability | audit-only run | see machine-readable lifecycle fields | diagnostic report tests | current tested SHA | see Result Summary | see Live Paper Results | NOT VERIFIED IN NATIVE MINECRAFT | `summary.json`, `end-rift-events.jsonl` |",
        "",
        "## 3. Root Causes",
        "",
        "The machine-readable lifecycle and invariant results below are the source of truth; no success status is inferred from a console summary.",
        "",
        "## 4. Regression Tests",
        "",
        f"- event records analyzed: `{sum(report.event_counts.values())}`",
        f"- event counts: `{json.dumps(report.event_counts, sort_keys=True)}`",
        "",
        "## 5. Runtime Invariant Failures",
        "",
        *(f"- `{item}`" for item in report.invariant_failures),
        "- none" if not report.invariant_failures else "",
        "",
        "## 6. Exceptions",
        "",
        *(f"- `{name}`: `{count}`" for name, count in report.exception_fingerprints.items()),
        "- none" if not report.exception_fingerprints else "",
        "",
        "## 7. Wave 6 Ritual Audit",
        "",
        "Correlated ritual lifecycle records are included in the central JSONL stream and are analyzed for prisoner, projectile, zone, and control leaks.",
        "",
        "## 8. Boss/Hitbox Audit",
        "",
        f"- proxy lifecycle leaks: `{json.dumps(report.boss_proxy_leaks)}`",
        "",
        "## 9. Wave 7 Audit",
        "",
        f"- restore mismatches: `{json.dumps(report.wave7_restore_mismatches)}`",
        "",
        "## 10. Recovery Audit",
        "",
        "Recovery START/COMPLETE records and canonical snapshots are retained in the central stream when a live restart is run.",
        "",
        "## 11. Cleanup Audit",
        "",
        f"- entity leaks: `{json.dumps(report.entity_leaks)}`",
        f"- task leaks: `{json.dumps(report.task_leaks)}`",
        f"- control leaks: `{json.dumps(report.control_leaks)}`",
        f"- projectile leaks: `{json.dumps(report.projectile_leaks)}`",
        "",
        "## 12. Client/Visual Audit",
        "",
        f"- native Minecraft verification: `{metadata.get('nativeMinecraft', 'NOT VERIFIED')}`",
        "- static previews are not native Minecraft proof.",
        "",
        "## 13. Performance and Diagnostic Integrity",
        "",
        f"- diagnostic events dropped: `{report.diagnostic_events_dropped}`",
        f"- write failures: `{report.write_failures}`",
        f"- max queue depth: `{report.max_queue_depth}`",
        f"- log rotations: `{report.log_rotations}`",
        "",
        "## 14. CI Results",
        "",
        f"- CI: `{metadata.get('ciResult', 'NOT RECORDED')}`",
        "",
        "## 15. Live Paper Results",
        "",
        f"- live Paper: `{metadata.get('livePaperResult', 'NOT RECORDED')}`",
        f"- diagnostic report: `{metadata.get('diagnosticReportResult', 'NOT RECORDED')}`",
        "",
        "## 16. Native Minecraft Results",
        "",
        f"- native Minecraft: `{metadata.get('nativeMinecraft', 'NOT VERIFIED')}`",
        "",
        "## 17. Artifact Hashes",
        "",
        *(f"- `{name}`: `{value}`" for name, value in sorted(artifact_hashes.items())),
        "- none recorded" if not artifact_hashes else "",
        "",
        "## 18. Commits",
        "",
        f"- tested SHA: `{metadata.get('gitHead', '')}`",
        "",
        "## 19. Open Issues",
        "",
        "- native Minecraft visual/client behavior remains NOT VERIFIED unless native evidence is indexed for this exact SHA.",
        "",
        "## 20. Evidence Index",
        "",
        "- `summary.json` is generated from the parsed JSONL records.",
        "- `server-events.jsonl`/`end-rift-events.jsonl` is the primary structured evidence stream.",
        "- `evidence-index.json` is authoritative for native screenshot/video evidence; unavailable native evidence is explicitly NOT VERIFIED.",
        "",
    ]
    return "\n".join(lines)


def discover_rows(run_dir: Path) -> list[dict[str, object]]:
    candidates = sorted(run_dir.glob("*.jsonl"))
    if not candidates:
        raise DiagnosticReportError(f"no diagnostic JSONL files found in {run_dir}")
    rows: list[dict[str, object]] = []
    for path in candidates:
        rows.extend(load_jsonl(path))
    rows.sort(key=lambda row: int(row["sequence"]))
    sequences = [int(row["sequence"]) for row in rows]
    if sequences != sorted(set(sequences)):
        raise DiagnosticReportError("duplicate or non-monotonic sequence across diagnostic files")
    return rows


def load_metadata(run_dir: Path) -> dict[str, object]:
    path = run_dir / "metadata.json"
    if not path.is_file():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise DiagnosticReportError(f"malformed metadata JSON: {error.msg}") from error
    if not isinstance(value, dict):
        raise DiagnosticReportError("metadata.json must contain an object")
    return value


def load_artifact_hashes(run_dir: Path) -> dict[str, str]:
    path = run_dir / "artifact-hashes.txt"
    if not path.is_file():
        return {}
    result: dict[str, str] = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        parts = line.split(None, 1)
        if len(parts) != 2 or len(parts[0]) != 64:
            raise DiagnosticReportError(f"malformed artifact hash at {path}:{line_number}")
        try:
            int(parts[0], 16)
        except ValueError as error:
            raise DiagnosticReportError(f"invalid SHA-256 at {path}:{line_number}") from error
        result[parts[1].strip()] = parts[0].lower()
    return result


def _correlation_lifecycle(
    rows: Iterable[Mapping[str, object]],
    category: str,
    start_action: str,
    terminal_actions: set[str],
    *,
    require_remove: bool,
    allow_persistent_layout: bool = False,
) -> tuple[list[str], list[str]]:
    records: defaultdict[str, list[Mapping[str, object]]] = defaultdict(list)
    for row in rows:
        if row.get("category") == category:
            correlation = str(row.get("correlationId", ""))
            records[correlation].append(row)
    leaks: list[str] = []
    duplicate_terminal: list[str] = []
    for correlation, lifecycle_rows in records.items():
        lifecycle = [str(row.get("action", "")) for row in lifecycle_rows]
        if start_action not in lifecycle:
            continue
        terminal = [action for action in lifecycle if action in terminal_actions]
        if allow_persistent_layout and _is_explicit_persistent_layout(lifecycle_rows):
            continue
        if not terminal or (require_remove and "REMOVE" not in lifecycle):
            leaks.append(correlation)
        if sum(action not in {"REMOVE", "CLEANUP"} for action in terminal) > 1:
            duplicate_terminal.append(correlation)
    return sorted(leaks), sorted(duplicate_terminal)


def _is_explicit_persistent_layout(rows: Iterable[Mapping[str, object]]) -> bool:
    """Whitelist only the PDC-marked static layout entities.

    A missing terminal is valid for these objects because they intentionally
    survive an encounter reset. The producer must opt in with both the
    boolean marker and the exact policy string; a generic ``persistent``
    field, a mob, or a display with another role is never enough.
    """

    for row in rows:
        if row.get("action") != "SPAWN":
            continue
        fields = row.get("fields")
        fields_map = fields if isinstance(fields, Mapping) else {}
        if fields_map.get("persistent") is not True:
            continue
        if fields_map.get("persistencePolicy") != "STATIC_EVENT_LAYOUT":
            continue
        kind = fields_map.get("kind")
        entity_type = fields_map.get("entityType")
        if kind in {"CORE", "PAD", "MEMORIAL"}:
            return True
        if kind == "DISPLAY" and entity_type == "TEXT_DISPLAY":
            return True
    return False


def _wave7_coordinates(rows: Iterable[Mapping[str, object]], action: str) -> set[str]:
    result: set[str] = set()
    for row in rows:
        if row.get("category") != "WAVE7_BARRIER" or row.get("action") != action:
            continue
        fields = row.get("fields")
        fields_map = fields if isinstance(fields, Mapping) else {}
        x = fields_map.get("x", fields_map.get("blockX"))
        y = fields_map.get("y", fields_map.get("blockY"))
        z = fields_map.get("z", fields_map.get("blockZ"))
        if x is None or y is None or z is None:
            continue
        result.add(f"{x},{y},{z}")
    return result


def _prisoner_health_changed_outside_drain(rows: Iterable[Mapping[str, object]]) -> bool:
    last_health: dict[str, float] = {}
    authorized_next_snapshot: set[str] = set()
    for row in rows:
        if row.get("category") != "RITUAL_PRISONER":
            continue
        correlation = str(row.get("correlationId", ""))
        fields = row.get("fields")
        fields_map = fields if isinstance(fields, Mapping) else {}
        if row.get("action") == "DRAIN":
            after = fields_map.get("healthAfter")
            if isinstance(after, (int, float)) and not isinstance(after, bool):
                last_health[correlation] = float(after)
                authorized_next_snapshot.add(correlation)
            continue
        if row.get("action") != "SNAPSHOT":
            continue
        health = fields_map.get("health")
        if not isinstance(health, (int, float)) or isinstance(health, bool):
            continue
        if correlation in last_health and float(health) < last_health[correlation] - 1e-9:
            if correlation not in authorized_next_snapshot:
                return True
        authorized_next_snapshot.discard(correlation)
        last_health[correlation] = float(health)
    return False


def _nonnegative_int(value: object, field_name: str = "value") -> int:
    try:
        number = int(value)
    except (TypeError, ValueError):
        raise DiagnosticReportError(f"metadata field {field_name} must be an integer")
    if number < 0:
        raise DiagnosticReportError(f"metadata field {field_name} must be non-negative")
    return number


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_directory", type=Path)
    args = parser.parse_args(argv)
    try:
        metadata = load_metadata(args.run_directory)
        rows = discover_rows(args.run_directory)
        report = write_report(args.run_directory, rows, metadata, load_artifact_hashes(args.run_directory))
    except DiagnosticReportError as error:
        print(f"diagnostic report failed: {error}", file=sys.stderr)
        return 2
    print(f"diagnostic report {report.result}: {args.run_directory / 'report.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
