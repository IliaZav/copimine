"""Final handoff: the boss is started exactly once per official attempt.

Contract 2026-10-31: after Wave 7 the party gets one forty-second transition
and one boss.  A restart, a duplicated scheduler callback, or a stale context
must never heal twice, summon a second boss, or spawn anything at all when the
attempt was already committed.  The commit is durable before any entity exists:
intent is written, then the gateway runs, so a crash in between requires
reconciliation instead of another spawn.

The real methods are extracted from the plugin source and executed, so a
regression in the guard fails here instead of only in a live fight.
"""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]

_HARNESS = """import java.util.*;
public class {name} {{
    static void require(boolean value, String reason) {{ if (!value) throw new AssertionError(reason); }}

    enum EventPhase {{ READY_FOR_PLAYERS, WAVE_1, WAVE_7, PRE_BOSS_COOLDOWN, BOSS_ACTIVE, RECOVERY_REQUIRED }}

    static final class EncounterContext {{
        final String event; final long generation;
        EncounterContext(String event, long generation) {{ this.event = event; this.generation = generation; }}
        boolean owns(String id, long gen) {{ return event != null && event.equals(id) && gen == generation; }}
        static EncounterContext primary() {{ return new EncounterContext("event", 71L); }}
        static EncounterContext foreign() {{ return new EncounterContext("other", 71L); }}
        static EncounterContext staleGeneration() {{ return new EncounterContext("event", 70L); }}
    }}

    String eventId = "event";
    long generation = 71L;
    EventPhase phase = EventPhase.PRE_BOSS_COOLDOWN;
    boolean preBossHandoffCommitted;
    long phaseDeadlineMillis = 1234L;
    int bossSpawns;
    int checkpoints;
    int transitions;
    boolean checkpointSucceeds = true;
    static final List<String> warnings = new ArrayList<>();
    static final class Logger {{ void warning(String message) {{ warnings.add(message); }} }}
    Logger getLogger() {{ return new Logger(); }}

    boolean saveStateSync() {{ checkpoints++; return checkpointSucceeds; }}
    void forcePhase(EventPhase target, String reason) {{ phase = target; }}
    boolean transition(EventPhase target, String reason, String key) {{ transitions++; phase = target; return true; }}
    void spawnOfficialBoss(Object unused) {{ bossSpawns++; }}
    Object liveBoss() {{ return bossSpawns > 0 ? new Object() : null; }}
    void saveStateAsync() {{ }}

{methods}

    public static void main(String[] args) {{
{body}
    }}
}}
"""


def _probe(tmp_path: Path, name: str, body: str) -> str:
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8"
    )
    methods = "\n".join(
        extract(source, signature)
        for signature in (
            "void commitPreBossHandoffAndStart(EncounterContext context)",
            "void startBossFromGateway(EncounterContext context)",
        )
    )
    probe = tmp_path / f"{name}.java"
    probe.write_text(
        _HARNESS.format(name=name, methods=methods, body=body.format(name=name)), encoding="utf-8"
    )
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(probe)],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    run = subprocess.run(["java", "-cp", str(tmp_path), name], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    return run.stdout


def test_handoff_starts_the_boss_exactly_once(tmp_path: Path) -> None:
    body = """
        var owner = new {name}();
        owner.commitPreBossHandoffAndStart(EncounterContext.primary());
        require(owner.bossSpawns == 1, "the first handoff must start exactly one boss");
        require(owner.phase == EventPhase.BOSS_ACTIVE, "the commit must advance to BOSS_ACTIVE");
        require(owner.preBossHandoffCommitted, "the attempt must record the durable commit");

        // A duplicated scheduler callback or a replayed tick must not summon a second boss.
        owner.commitPreBossHandoffAndStart(EncounterContext.primary());
        require(owner.bossSpawns == 1, "a repeated handoff must not summon a second boss");
        require(owner.checkpoints == 1, "a repeated handoff must not write a second checkpoint");
        require(owner.transitions == 1, "a repeated handoff must not transition twice");
    """
    _probe(tmp_path, "HandoffOnceProbe", body)


def test_handoff_refuses_stale_or_foreign_contexts(tmp_path: Path) -> None:
    body = """
        var foreign = new {name}();
        foreign.commitPreBossHandoffAndStart(EncounterContext.foreign());
        require(foreign.bossSpawns == 0, "another attempt's context must not start this boss");
        require(!foreign.preBossHandoffCommitted, "a refused handoff must not burn the commit");
        require(foreign.phase == EventPhase.PRE_BOSS_COOLDOWN, "a refused handoff must not change the phase");

        var stale = new {name}();
        stale.commitPreBossHandoffAndStart(EncounterContext.staleGeneration());
        require(stale.bossSpawns == 0, "a stale generation must not start this boss");
        require(!stale.preBossHandoffCommitted, "a stale handoff must not burn the commit");

        var missing = new {name}();
        missing.commitPreBossHandoffAndStart(null);
        require(missing.bossSpawns == 0, "a missing context must not start a boss");
        require(!missing.preBossHandoffCommitted, "a missing context must not burn the commit");

        var wrongPhase = new {name}();
        wrongPhase.phase = EventPhase.WAVE_7;
        wrongPhase.commitPreBossHandoffAndStart(EncounterContext.primary());
        require(wrongPhase.bossSpawns == 0, "the gateway must refuse to start a boss outside the handoff phase");
        require(wrongPhase.phase == EventPhase.WAVE_7, "a refused gateway must not change the phase");
    """
    _probe(tmp_path, "HandoffContextProbe", body)


def test_handoff_checkpoint_failure_blocks_the_boss_and_never_retries(tmp_path: Path) -> None:
    body = """
        var failing = new {name}();
        failing.checkpointSucceeds = false;
        failing.commitPreBossHandoffAndStart(EncounterContext.primary());
        require(failing.bossSpawns == 0, "a failed durable checkpoint must never create a boss");
        require(failing.phase == EventPhase.RECOVERY_REQUIRED,
                "a failed checkpoint must park the attempt in RECOVERY_REQUIRED");
        require(failing.preBossHandoffCommitted, "the commit must stay durable after a checkpoint failure");

        // Recovery must reconcile the committed intent, not spawn a second boss here.
        failing.commitPreBossHandoffAndStart(EncounterContext.primary());
        require(failing.bossSpawns == 0, "a checkpoint failure must not be retried as a normal start");
        require(failing.checkpoints == 1, "the failure path must not write further checkpoints");
    """
    _probe(tmp_path, "HandoffCheckpointProbe", body)


def test_handoff_commits_intent_before_any_entity_exists() -> None:
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8"
    )
    method = extract(source, "void commitPreBossHandoffAndStart(EncounterContext context)")

    commit_at = method.index("preBossHandoffCommitted = true;")
    checkpoint_at = method.index("saveStateSync()")
    spawn_at = method.index("startBossFromGateway(context)")
    assert commit_at < checkpoint_at < spawn_at, (
        "the handoff must record intent, then persist it, and only then create the boss"
    )