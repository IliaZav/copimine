from pathlib import Path
import re


ROOT = Path(__file__).parents[2]
RECIPE = (ROOT / "copimine-narcotics/src/me/copimine/narcotics/recipe/NarcoticsRecipeService.java").read_text(encoding="utf-8")
PLUGIN = (ROOT / "copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java").read_text(encoding="utf-8")


def _method(source: str, signature: str, next_signature: str) -> str:
    start = source.index(signature)
    end = source.index(next_signature, start)
    return source[start:end]


def test_cauldron_rejects_items_that_cannot_be_round_tripped_before_buffering():
    entry = _method(
        RECIPE,
        "public IngredientEntry cauldronIngredientEntry(ItemStack stack)",
        "public NarcoticDefinition matchExact",
    )
    safety = _method(
        RECIPE,
        "private boolean isRoundTripSafeIngredient(ItemStack stack)",
        "private boolean isPotion(ItemStack stack)",
    )

    assert "if (!isRoundTripSafeIngredient(stack))" in entry
    assert "BlockStateMeta" in safety
    assert "stack.hasItemMeta()" in safety
    assert "potionMeta.hasCustomEffects()" in safety


def test_finished_narcotics_are_removed_from_vanilla_crafting_result():
    handler = _method(
        PLUGIN,
        "public void onPrepareCraft(PrepareItemCraftEvent event)",
        "@EventHandler(ignoreCancelled = true)\n    public void onDispense",
    )

    assert "event.getInventory().getMatrix()" in handler
    assert "case CRAFTING, WORKBENCH, CRAFTER" in handler
    assert "itemFactory.isOfficialFinishedItem" in handler
    assert "event.getInventory().setResult(null)" in handler
