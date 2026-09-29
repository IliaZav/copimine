from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)


def method_body(name: str) -> str:
    marker = f"private void {name}()"
    if name != "restorePersistedRitualSphereObjective":
        marker = f"private boolean {name}(World world, Location core)"
    start = EVENT.index(marker)
    body_start = EVENT.index("{", start)
    depth = 0
    for index in range(body_start, len(EVENT)):
        if EVENT[index] == "{":
            depth += 1
        elif EVENT[index] == "}":
            depth -= 1
            if depth == 0:
                return EVENT[body_start + 1:index]
    raise AssertionError(f"Unclosed method body for {name}")


def test_partial_wave6_progress_enters_the_restart_continuation_path():
    restore = method_body("restorePersistedRitualSphereObjective")
    valid_snapshot = restore.index("ritualSphereState.generation() != generation")
    count_mismatch = restore.index("liveRitualCasterCount() != profile.casterCount()")
    rehydrated = restore.index("ritualSphereStateRehydrated = true;")

    assert valid_snapshot < rehydrated < count_mismatch, (
        "a valid persisted Wave 6 must mark rehydration before partial caster/guard counts "
        "can route through startRitualSphereObjective"
    )
    assert "startRitualSphereObjective(Bukkit.getWorld(worldName), core);" in restore

    start = method_body("startRitualSphereObjective")
    assert "recoveredCasters <= recoveredProfile.casterCount()" in start
    assert "recoveredGuards <= recoveredProfile.guardCount()" in start
    assert "recoveredProfile.casterCount() - recoveredCasters" in start
