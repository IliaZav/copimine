export const RECIPE_DRAG_MIME = "application/x-copimine-recipe-index";

function validRecipeIndex(value, itemCount) {
  return Number.isInteger(value) && value >= 0 && value < itemCount;
}

export function writeRecipeDragIndex(dataTransfer, index) {
  if (!dataTransfer || !Number.isInteger(index) || index < 0) return;
  dataTransfer.setData(RECIPE_DRAG_MIME, String(index));
}

export function readRecipeDragIndex(dataTransfer, itemCount) {
  const types = Array.from(dataTransfer?.types || []);
  if (!types.includes(RECIPE_DRAG_MIME)) return null;
  const raw = String(dataTransfer.getData(RECIPE_DRAG_MIME) || "");
  if (!/^(0|[1-9]\d*)$/.test(raw)) return null;
  const index = Number(raw);
  return validRecipeIndex(index, itemCount) ? index : null;
}
