"""Master contract 13.2: Phase 3 really mutates the arena floor, and really puts it back.

RiftFractureEruptionPolicy answers the contract perfectly on its own, and
tests/test_end_rift_boss_phase_mutation_and_echoes.py already proves that. The
defect this file guards against lives at the call sites: the phase can draw a
particle ring over an unchanged floor, or mutate real blocks with no journal
record, and every pure-policy test stays green.

So every assertion below is about the monolith wiring, not the policy:

* the phase selects its cells with RiftFractureEruptionPolicy.selectCells and
  never with the obelisk placement search,
* the journal is written BEFORE the first setBlockData and says APPLIED only
  after it,
* the geyser column, not a circle on the floor, is the damage volume, and one
  eruption can never hurt the same player twice,
* the restore puts the exact original BlockData back and then retires the
  journal,
* the teardown runs on phase change, wave end and plugin disable.
"""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def source() -> str:
    return MONOLITH.read_text("utf-8")


def method(name: str) -> str:
    """Body of one monolith method, from its signature to its closing brace."""
    text = source()
    start = text.index("private " + name + "(")
    end = text.index("\n    }\n", start)
    return text[start:end]


# --- 1. real cells, chosen by the contract policy ---------------------------

def test_fractures_select_cells_with_the_eruption_policy() -> None:
    body = method("void startCurrentRiftFractures")
    assert "RiftFractureEruptionPolicy.selectCells(" in source(), (
        "the phase must select real floor cells with the contract policy"
    )
    assert "ObeliskPlacementPolicy.searchCandidates" not in body, (
        "the retired obelisk placement search must not drive the fracture cells"
    )
    assert "selectRiftFractureCells(" in body


def test_cells_are_built_from_real_blocks_around_the_arena_floor() -> None:
    body = method("List<RiftFractureEruptionPolicy.Cell> selectRiftFractureCells")
    assert "world.getBlockAt(x, floorY, z)" in body, (
        "cells must come from real world blocks, not from a synthetic ring"
    )
    assert "floor.getBlockData().getAsString()" in body, (
        "the exact original BlockData must be captured, not just the material"
    )
    assert "isRiftFractureReservedCell(" in body, (
        "core, runes and gates must be handed to the policy as protected cells"
    )
    assert "currentRiftFractureCells.containsKey(key)" in body, (
        "cells already owned by a live fracture must be reported as occupied"
    )
    assert "isLiveInfernoCell(" in body, (
        "cells the Phase 4 inferno holds must be reported as occupied"
    )
    assert "RiftFractureEruptionPolicy.MINIMUM_SAFE_RATIO" in body, (
        "the arena must keep a standable share of floor"
    )


def test_protected_cells_cover_core_runes_and_gates() -> None:
    body = method("boolean isRiftFractureReservedCell")
    assert "Math.abs(x - coreX) <= 2 && Math.abs(z - coreZ) <= 2" in body, (
        "the Core footprint is never fractured"
    )
    assert "pads" in body, "the rune pads are never fractured"
    assert "isGateLocation(at)" in body, "gate cells are never fractured"


# --- 2. save-before-mutate --------------------------------------------------

def test_journal_is_prepared_before_any_block_changes() -> None:
    body = method("void startCurrentRiftFractures")
    prepare = body.index("hazardJournal.prepare(eventId, generation, world.getName(), journalEntries)")
    apply_call = body.index("applyRiftFractureSurface(state, 0.0D, false)")
    assert prepare < apply_call, (
        "save-before-mutate: the journal must be durable before the surface changes"
    )
    assert "journalEntries.add(new HazardMutationJournal.Entry(" in body
    assert "new BlockCellKey(world.getUID(), cell.x(),\n                    combatFloorY(), cell.z())" in body or \
           "combatFloorY(), cell.z())" in body


def test_a_failed_prepare_never_touches_the_floor() -> None:
    body = method("void startCurrentRiftFractures")
    guard = body.index("!hazardJournal.prepare(")
    apply_call = body.index("applyRiftFractureSurface(state, 0.0D, false)")
    assert guard < apply_call
    between = body[guard:apply_call]
    assert "setBlockData" not in between, (
        "a refused prepare must not mutate a single block"
    )
    assert "return;" in between, "a refused prepare must abandon the batch"


def test_mark_applied_follows_the_real_mutation() -> None:
    body = method("void startCurrentRiftFractures")
    apply_call = body.index("applyRiftFractureSurface(state, 0.0D, false)")
    mark = body.index("hazardJournal.markApplied()")
    assert apply_call < mark, (
        "the journal may only claim APPLIED after the blocks really changed"
    )
    # A failed apply must go straight to restore, never to a fake APPLIED.
    assert "if (!applied) {" in body
    failed = body.index("if (!applied) {")
    assert "restoreCurrentRiftFractureBlocks(state)" in body[failed:]


def test_a_busy_journal_skips_the_batch_instead_of_forcing_it() -> None:
    body = method("void startCurrentRiftFractures")
    busy = body.index('hazardJournalIsIdle("rift-fracture-batch")')
    prepare = body.index("hazardJournal.prepare(")
    assert busy < prepare, (
        "the shared journal must be proven idle before this phase prepares on it"
    )
    guard = method("boolean hazardJournalIsIdle")
    assert "HazardMutationJournal.Status.EMPTY" in guard
    assert "HazardMutationJournal.Status.RESTORED" in guard
    assert not re.search(r"journal\.(prepare|markApplied)", guard)


def test_the_journal_step_contract_is_not_reimplemented_locally() -> None:
    """13.4: the policy owns which journal step the adapter owes next."""
    body = method("void startCurrentRiftFractures") + method(
        "boolean applyRiftFractureSurface")
    for retired in ("RiftFracturePolicy.TELEGRAPH_TICKS", "RiftFracturePolicy.ACTIVE_TICKS"):
        assert retired not in body, f"{retired} pinned the retired particle-ring timing"


# --- 3. the stages come from the policy ------------------------------------

def test_stage_timing_comes_from_the_policy_not_local_constants() -> None:
    body = method("void startCurrentRiftFractures")
    assert "RiftFractureEruptionPolicy.Stage.forBatch(" in body
    assert "stage.warningTicks(" in body, "the warning length must be the policy's"
    assert "RiftFractureEruptionPolicy.batchGapTicks(" in body, (
        "the batch cadence must be the policy's"
    )
    assert "RiftFractureEruptionPolicy.batchSizeForPlayers(" in body


def test_the_crack_widens_across_prepare_and_warning() -> None:
    body = method("void tickCurrentRiftFractures")
    assert "RiftFractureEruptionPolicy.Phase.WARNING" in body
    assert "stage.width(eventTickCounter)" in body, (
        "the crack must widen with the policy's progress value"
    )
    assert "applyRiftFractureSurface(state, progress, false)" in body
    surface = method("boolean applyRiftFractureSurface")
    assert "RiftFractureEruptionPolicy.surfaceBlockDataFor(progress, exploded)" in surface, (
        "the surface block data must come from the policy"
    )
    assert "block.setBlockData(data, false)" in surface


# --- 4. the geyser is the damage volume ------------------------------------

def test_the_geyser_column_is_the_damage_volume() -> None:
    body = method("void damageRiftFractureColumn")
    assert "RiftFractureEruptionPolicy.GEYSER_RADIUS_BLOCKS" in body, (
        "the hit radius must be the authored column radius"
    )
    assert "RiftFractureEruptionPolicy.MIN_GEYSER_HEIGHT" in body
    assert "RiftFractureEruptionPolicy.MAX_GEYSER_HEIGHT" in body
    assert "stage.geyserHeight(eventTickCounter)" in body, (
        "the hit volume must follow the visible column height"
    )
    assert "RiftFractureEruptionPolicy.eruptionDamage(participants.size())" in body, (
        "damage must come from the policy"
    )
    assert "dx * dx + dz * dz > radius * radius" in body, "only the column hurts"
    assert "if (dy < -1.0D || dy > height)" in body, (
        "a player outside the column height is never inside the hit volume"
    )


def test_one_eruption_never_damages_the_same_player_twice() -> None:
    body = method("void damageRiftFractureColumn")
    assert "RiftFractureEruptionPolicy.mayDamagePlayer(" in body, (
        "the per-player ledger is the only authority on repeat damage"
    )
    assert "RiftFractureEruptionPolicy.scopedKey(\n                    eruptionTick, playerKey)" in body or \
           "scopedKey(" in body
    check = body.index("mayDamagePlayer(")
    add = body.index("state.damagedPlayers().add(")
    hurt = body.index("player.damage(damage")
    assert check < add < hurt, (
        "the ledger must be asked, then written, before the hit is dealt"
    )
    assert "continue;" in body[check:hurt]


def test_damage_happens_only_inside_the_eruption_stage() -> None:
    body = method("void tickCurrentRiftFractures")
    eruption = body.index("RiftFractureEruptionPolicy.Phase.ERUPTION")
    assert body.index("damageRiftFractureColumn(") > eruption, (
        "damage must sit inside the ERUPTION branch, never the warning or collapse"
    )
    # The prepare/warning branch must not call the damage helper at all.
    warning = body.index("RiftFractureEruptionPolicy.Phase.WARNING")
    warning_block = body[warning:]
    assert "damageRiftFractureColumn(" not in warning_block


# --- 5. the exact original data comes back ---------------------------------

def test_restore_writes_the_original_block_data_back() -> None:
    body = method("boolean restoreCurrentRiftFractureBlocks")
    assert "restoreBlock(block, entry.getValue())" in body, (
        "the journal's exact BlockData string must be written back"
    )
    assert "setType(" not in body, (
        "restoring a material type is not a restore: BlockData carries the state"
    )
    assert "hazardJournal.markRestored()" in body, (
        "the journal is retired only after the originals are back"
    )
    mark = body.index("hazardJournal.markRestored()")
    restore = body.index("restoreBlock(block, entry.getValue())")
    assert restore < mark, "restore first, then commit the journal"


def test_restore_refuses_to_clobber_foreign_edits() -> None:
    body = method("boolean restoreCurrentRiftFractureBlocks")
    assert "isRiftFractureSurface(block.getType())" in body, (
        "a block nobody of ours wrote must not be overwritten on restore"
    )
    guard = method("boolean isRiftFractureSurface")
    assert "Material.MAGMA_BLOCK" in guard
    assert "Material.CRACKED_POLISHED_BLACKSTONE_BRICKS" in guard


def test_a_failed_journal_commit_keeps_the_originals() -> None:
    body = method("boolean restoreCurrentRiftFractureBlocks")
    assert "RIFT_FRACTURE_RESTORE_JOURNAL_FAILED" in body
    failed = body.index("if (!committed) {")
    tail = body[failed:]
    assert "return false;" in tail
    assert "state.cells()" not in tail.split("return false;")[0], (
        "a failed commit must not forget the cells it could not retire"
    )


# --- 6. cleanup is generation-guarded and total ---------------------------

def test_teardown_restores_before_it_forgets() -> None:
    body = method("void clearCurrentRiftFractures")
    restore = body.index("restoreCurrentRiftFractureBlocks(state)")
    forget = body.index("activeCurrentRiftFractures.clear()")
    assert restore < forget, (
        "the teardown must restore the floor before dropping its own state"
    )
    assert "currentRiftFractureCells.clear()" in body


def test_teardown_runs_on_phase_change_and_wave_end() -> None:
    text = source()
    for reason in ('"Current stage exit from RIFT"', '"rift-phase-exit"',
                   '"session cancellation"', '"combat AI reset"',
                   '"disposable-boss-defeat"'):
        assert "clearCurrentRiftFractures(" + reason + ")" in text, (
            f"no teardown is scheduled for {reason}"
        )
    body = method("void tickCurrentRiftFractures")
    assert "bossPhase != BossPhase.RIFT" in body and "clearCurrentRiftFractures(" in body, (
        "leaving the RIFT phase must tear every fracture down on its own"
    )


def test_teardown_runs_on_plugin_disable() -> None:
    text = source()
    disable = text.index("public void onDisable()")
    body = text[disable: text.index("\n    }\n", disable)]
    assert "clearCurrentRiftFractures(" in body, (
        "a disabled plugin must not leave cracked blocks or magma behind"
    )


def test_cleanup_logs_the_generation_it_was_armed_for() -> None:
    body = method("void clearCurrentRiftFractures")
    assert 'generation=" + generation' in body


# --- 7. the retired particle ring is gone ---------------------------------

def test_no_particle_ring_helper_survives_as_the_mechanic() -> None:
    body = method("void renderCurrentRiftFracture")
    assert "spawnPatternRing(" not in body, (
        "a ring on the floor was the retired mechanic"
    )
    assert "RiftFractureEruptionPolicy.GEYSER_RADIUS_BLOCKS" in body, (
        "the visible pillar must share the hit radius"
    )
    # The pillar itself must be emitted, not merely described: a constant that
    # is present but never used is exactly how the retired ring survived review.
    assert "if (height > 0.0D) {" in body, (
        "the geyser pillar must be rendered while the column exists"
    )
    assert "if (false) {" not in body
    assert body.index("for (double y = 0.0D; y <= height; y += 1.0D)") > body.index(
        "if (height > 0.0D) {"
    ), "the column loop must sit inside the pillar branch"
