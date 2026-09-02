from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVICE = (ROOT / "copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java").read_text(
    encoding="utf-8"
)


def section(source: str, start: str, end: str) -> str:
    start_index = source.index(start)
    return source[start_index : source.index(end, start_index)]


def test_finished_brew_is_committed_before_public_output_is_materialized():
    completion = section(SERVICE, "private void finishBrewing", "private void simulateWrongMixExplosion")

    assert "database.completeBrewingState" in completion
    assert "dropCompletedBrewingOutput(dropLocation, definition, outputId)" in completion
    assert "database.markBrewingOutputWorldDropped(outputId)" in completion
    assert "dropItemNaturally" not in completion


def test_failed_partial_state_save_tombstones_and_compensates_the_consumed_ingredient():
    queue = section(SERVICE, "private boolean queueIngredients", "private void clearState")

    assert "database.saveBrewingState(key, version, frozen)" in queue
    assert "database.tombstoneBrewingState(key, version)" in queue
    assert "database.queuePendingIngredientRefunds" in queue
    assert "Brewing database save failed" in queue
