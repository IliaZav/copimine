import { readRecipeDragIndex, writeRecipeDragIndex } from "../shared/recipe-drag.js";

const RECIPE_ITEM_TABS = [
  {
    id: "basic",
    label: "Ингредиенты",
    items: [
      ["SUGAR", "Сахар"], ["WHITE_DYE", "Белый краситель"], ["GLOWSTONE_DUST", "Светопыль"],
      ["RABBIT_FOOT", "Кроличья лапка"], ["DIAMOND", "Алмаз"], ["JUNGLE_LEAVES", "Листья джунглей"],
      ["SLIME_BLOCK", "Блок слизи"], ["TURTLE_SCUTE", "Черепаший панцирь"], ["EMERALD", "Изумруд"],
      ["GOLD_INGOT", "Золотой слиток"], ["GOLDEN_CARROT", "Золотая морковь"], ["STRING", "Нить"],
      ["BONE", "Кость"], ["IRON_BLOCK", "Железный блок"], ["GHAST_TEAR", "Слеза гаста"],
      ["AMETHYST_BLOCK", "Аметистовый блок"], ["END_ROD", "Энд-стержень"], ["IRON_INGOT", "Железный слиток"],
      ["BLUE_STAINED_GLASS", "Синее стекло"], ["COCOA_BEANS", "Какао-бобы"], ["IRON_NUGGET", "Железный самородок"],
      ["LARGE_FERN", "Большой папоротник"], ["DRIED_KELP_BLOCK", "Блок сушёной ламинарии"], ["SUGAR_CANE", "Сахарный тростник"]
    ]
  },
  {
    id: "blocks",
    label: "Блоки",
    items: [
      ["NETHERRACK", "Незерак"], ["SOUL_SAND", "Песок душ"], ["REDSTONE_BLOCK", "Редстоуновый блок"],
      ["LAPIS_BLOCK", "Лазуритовый блок"], ["COPPER_BLOCK", "Медный блок"], ["OBSIDIAN", "Обсидиан"],
      ["CRYING_OBSIDIAN", "Плачущий обсидиан"], ["HONEY_BLOCK", "Блок мёда"], ["MAGMA_BLOCK", "Магмовый блок"],
      ["SEA_LANTERN", "Морской фонарь"], ["SCULK", "Скалк"], ["TINTED_GLASS", "Тонированное стекло"]
    ]
  },
  {
    id: "potions",
    label: "Зелья",
    potion: true,
    items: [
      ["SPEED", "Зелье скорости"], ["WEAKNESS", "Зелье слабости"], ["POISON", "Зелье отравления"],
      ["SLOWNESS", "Зелье замедления"], ["REGENERATION", "Зелье регенерации"], ["STRENGTH", "Зелье силы"],
      ["WATER_BREATHING", "Зелье подводного дыхания"], ["INVISIBILITY", "Зелье невидимости"],
      ["FIRE_RESISTANCE", "Зелье огнестойкости"], ["HARM", "Зелье вреда"]
    ]
  },
  {
    id: "tools",
    label: "Редкие",
    items: [
      ["PHANTOM_MEMBRANE", "Мембрана фантома"], ["BLAZE_POWDER", "Огненный порошок"], ["FERMENTED_SPIDER_EYE", "Маринованный паучий глаз"],
      ["PRISMARINE_CRYSTALS", "Призмариновые кристаллы"], ["ECHO_SHARD", "Осколок эха"], ["AMETHYST_SHARD", "Осколок аметиста"],
      ["ENDER_PEARL", "Эндер-жемчуг"], ["BREEZE_ROD", "Стержень бриза"], ["NETHER_STAR", "Звезда Незера"]
    ]
  }
];

const BLOCKED_RECIPE_MATERIALS = new Set(["DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"]);
const RECIPE_PICKER_TABS = [
  { id: "items", label: "Все предметы", potion: false },
  { id: "potions", label: "Зелья", potion: true },
];

export function createAdminNarcoticsRecipePages(deps) {
  const { $, state, api, safeApi, setLoading, setView, panel, metric, esc, cleanText, dangerConfirm, toast } = deps;

  function recipeState() {
    if (!state.narcoticsRecipeEditor) {
      state.narcoticsRecipeEditor = { selected: "", tab: "items", query: "", pickerOpen: false, recipes: [], itemCatalog: [], potionCatalog: [] };
    }
    if (!Array.isArray(state.narcoticsRecipeEditor.itemCatalog)) state.narcoticsRecipeEditor.itemCatalog = [];
    if (!Array.isArray(state.narcoticsRecipeEditor.potionCatalog)) state.narcoticsRecipeEditor.potionCatalog = [];
    return state.narcoticsRecipeEditor;
  }

  function normalizeToken(token) {
    return String(token || "").trim().toUpperCase();
  }

  function tokenParts(token) {
    const raw = normalizeToken(token);
    const [kind, value] = raw.includes(":") ? raw.split(":", 2) : ["MATERIAL", raw];
    return { kind, value };
  }

  function iconFor(value, potion = false) {
    if (potion) return "/assets/mc-icons/item/potion.png";
    return `/assets/mc-icons/item/${String(value || "").toLowerCase()}.png`;
  }

  function displayName(token) {
    const { kind, value } = tokenParts(token);
    const rs = recipeState();
    const source = kind === "POTION" ? rs.potionCatalog : rs.itemCatalog;
    const found = source.find((item) => String(item.id || "").toLowerCase() === value.toLowerCase());
    if (found?.name) return found.name;
    const fallback = RECIPE_ITEM_TABS.flatMap((entry) => entry.items || []).find(([id]) => id === value);
    return fallback?.[1] || value.replace(/_/g, " ").toLowerCase();
  }

  function catalogItemFor(token) {
    const { kind, value } = tokenParts(token);
    const rs = recipeState();
    const source = kind === "POTION" ? rs.potionCatalog : rs.itemCatalog;
    return source.find((item) => String(item.id || "").toLowerCase() === value.toLowerCase()) || null;
  }

  function recipeById(id) {
    return recipeState().recipes.find((row) => row.id === id) || recipeState().recipes[0] || null;
  }

  function selectedRecipe() {
    const rs = recipeState();
    const selected = recipeById(rs.selected);
    if (selected && !rs.selected) rs.selected = selected.id;
    return selected;
  }

  function recipeOptions() {
    const selected = selectedRecipe()?.id || "";
    return recipeState().recipes.map((row) => `<option value="${esc(row.id)}"${row.id === selected ? " selected" : ""}>${esc(row.name || row.id)}</option>`).join("");
  }

  function renderTape(recipe) {
    const items = Array.isArray(recipe?.recipe) ? recipe.recipe : [];
    const slots = items.map((token, index) => {
      const { kind, value } = tokenParts(token);
      const potion = kind === "POTION";
      const catalogItem = catalogItemFor(token);
      const fallbackIcon = iconFor(value, potion);
      const primaryIcon = catalogItem?.iconUrl || fallbackIcon;
      return `
        <button class="recipe-slot recipe-slot-removable" draggable="true" title="${esc(displayName(token))}" aria-label="Удалить ${esc(displayName(token))}"
          data-drag-token="${esc(token)}" data-index="${index}"
          data-click="adminRecipeRemove(${index})">
          <img src="${esc(primaryIcon)}" data-fallback-icon="${esc(fallbackIcon)}" alt="${esc(displayName(token))}" loading="lazy" onerror="if(this.dataset.fallbackIcon && this.src !== this.dataset.fallbackIcon){this.src=this.dataset.fallbackIcon;}else{this.style.visibility='hidden';}" />
          <b class="recipe-remove-cross" aria-hidden="true">×</b>
          <span>${esc(displayName(token))}</span>
        </button>`;
    }).join("");
    return `${slots}<button class="recipe-slot recipe-add-slot" type="button" title="Добавить ингредиент" data-click="adminRecipeTogglePicker()"><strong>+</strong><span>Добавить ингредиент</span></button>`;
  }

  function renderInventory() {
    const rs = recipeState();
    if (!rs.pickerOpen) {
      return `<div class="recipe-inventory-closed">Нажмите плюс в ленте рецепта, чтобы открыть список предметов.</div>`;
    }
    const active = RECIPE_PICKER_TABS.find((tab) => tab.id === rs.tab) || RECIPE_PICKER_TABS[0];
    const query = String(rs.query || "").trim().toLowerCase();
    const source = active.potion ? rs.potionCatalog : rs.itemCatalog;
    const items = source
      .filter((item) => !BLOCKED_RECIPE_MATERIALS.has(String(item.id || "").toUpperCase()))
      .filter((item) => !query || `${item.id || ""} ${item.name || ""}`.toLowerCase().includes(query));
    return `
      <div class="recipe-inventory-head">
        <div class="segmented recipe-tabs">
          ${RECIPE_PICKER_TABS.map((tab) => `<button class="${tab.id === rs.tab ? "active" : ""}" data-click="adminRecipeTab('${tab.id}')">${esc(tab.label)}</button>`).join("")}
        </div>
        <label class="recipe-search-field"><span>Поиск по предметам</span><input id="recipeItemSearch" data-input="adminRecipeSearch" value="${esc(rs.query)}" placeholder="Например, diamond или сахар" autocomplete="off" /></label>
      </div>
      <div class="creative-inventory-grid">
        ${items.map((item) => {
          const token = String(item.token || `${active.potion ? "potion" : "material"}:${item.id || ""}`).toLowerCase();
          const fallbackIcon = iconFor(item.id, active.potion);
          const primaryIcon = item.iconUrl || fallbackIcon;
          return `<button class="creative-item" title="${esc(item.name || item.id || "Предмет")}" data-click="adminRecipeAdd('${esc(token)}')">
            <img src="${esc(primaryIcon)}" data-fallback-icon="${esc(fallbackIcon)}" alt="${esc(item.name || item.id || "Предмет")}" loading="lazy" onerror="if(this.dataset.fallbackIcon && this.src !== this.dataset.fallbackIcon){this.src=this.dataset.fallbackIcon;}else{this.style.visibility='hidden';}" />
            <span>${esc(item.name || item.id || "Предмет")}</span>
          </button>`;
        }).join("") || `<div class="recipe-empty">Ничего не найдено.</div>`}
      </div>`;
  }

  function renderEditor() {
    const recipe = selectedRecipe();
    if (!recipe) {
      setView(panel("Рецепты наркотиков", "Нет доступных рецептов.", `<div class="empty">Конфиг CopiMineNarcotics пуст.</div>`));
      return;
    }
    const count = Array.isArray(recipe.recipe) ? recipe.recipe.length : 0;
    setView(`
      <section class="layout-grid grid-3 recipe-summary-row">
        ${metric("Наркотик", recipe.name || recipe.id, recipe.id, "good")}
        ${metric("Ингредиентов", count, count >= 3 ? "готов к сохранению" : "минимум 3", count >= 3 ? "good" : "warn")}
        ${metric("Доступ", "админка", "сохранение и применение отдельными кнопками", "neutral")}
      </section>
      ${panel("Редактор рецепта", "Соберите ленту ингредиентов. Порядок в игре не важен.", `
        <div class="recipe-toolbar">
          <label>
            <span>Рецепт</span>
            <select id="recipeDrugSelect" data-input="adminRecipeSelect">${recipeOptions()}</select>
          </label>
          <button class="btn btn-secondary" data-click="adminRecipeClear()">Очистить ленту</button>
          <button class="btn btn-secondary" data-click="adminRecipeSave('save')">Сохранить</button>
          <button class="btn btn-primary" data-click="adminRecipeSave('apply')">Сохранить и применить</button>
        </div>
        <p class="recipe-apply-hint">«Сохранить» меняет конфиг без перезагрузки. «Сохранить и применить» перезапускает только Minecraft, сайт при этом продолжает работать.</p>
        <div class="recipe-tape" id="recipeTape">${renderTape(recipe)}</div>
        <div class="recipe-trash" id="recipeTrash">Отпустите ингредиент здесь, чтобы удалить.</div>
      `)}
      ${panel("Инвентарь предметов", "Поиск и вкладки работают как в креативе. Алмазная руда недоступна.", renderInventory())}
    `);
    wireRecipeDrag();
  }

  function wireRecipeDrag() {
    const tape = $("recipeTape");
    if (!tape) return;
    tape.querySelectorAll("[draggable='true']").forEach((node) => {
      node.addEventListener("dragstart", (event) => {
        writeRecipeDragIndex(event.dataTransfer, Number(node.dataset.index));
        document.body.classList.add("recipe-dragging");
        $("recipeTrash")?.classList.add("is-live");
      });
      node.addEventListener("dragend", () => {
        document.body.classList.remove("recipe-dragging");
        $("recipeTrash")?.classList.remove("is-live");
      });
      node.addEventListener("dragover", (event) => event.preventDefault());
      node.addEventListener("drop", (event) => {
        event.preventDefault();
        const recipe = currentRecipeMutable();
        const from = readRecipeDragIndex(event.dataTransfer, recipe?.recipe?.length || 0);
        if (from === null) return;
        const to = Number(node.dataset.index || 0);
        moveRecipeItem(from, to);
      });
    });
    $("recipeTrash")?.addEventListener("dragover", (event) => event.preventDefault());
    $("recipeTrash")?.addEventListener("drop", (event) => {
      event.preventDefault();
      const recipe = currentRecipeMutable();
      const from = readRecipeDragIndex(event.dataTransfer, recipe?.recipe?.length || 0);
      if (from === null) return;
      document.body.classList.remove("recipe-dragging");
      $("recipeTrash")?.classList.remove("is-live");
      removeRecipeItem(from);
    });
  }

  function currentRecipeMutable() {
    const recipe = selectedRecipe();
    if (!recipe) return null;
    if (!Array.isArray(recipe.recipe)) recipe.recipe = [];
    return recipe;
  }

  function removeRecipeItem(index) {
    const recipe = currentRecipeMutable();
    if (!recipe || !Number.isInteger(index) || index < 0 || index >= recipe.recipe.length) return;
    recipe.recipe.splice(index, 1);
    renderEditor();
  }

  function moveRecipeItem(from, to) {
    const recipe = currentRecipeMutable();
    if (!recipe || !Number.isInteger(from) || !Number.isInteger(to) || from < 0 || to < 0 || from >= recipe.recipe.length || to >= recipe.recipe.length || from === to) return;
    const [item] = recipe.recipe.splice(from, 1);
    recipe.recipe.splice(to, 0, item);
    renderEditor();
  }

  async function loadRecipes() {
    setLoading("Открываем рецепты");
    const data = await safeApi("/api/admin/narcotics/recipes", { recipes: [] });
    const rs = recipeState();
    rs.recipes = Array.isArray(data.recipes) ? data.recipes : [];
    rs.itemCatalog = Array.isArray(data.minecraftItems) ? data.minecraftItems : [];
    rs.potionCatalog = Array.isArray(data.potionItems) ? data.potionItems : [];
    if (!rs.selected && rs.recipes[0]) rs.selected = rs.recipes[0].id;
    renderEditor();
  }

  function adminRecipeSelect(value) {
    recipeState().selected = String(value || "");
    renderEditor();
  }

  function adminRecipeSearch(value) {
    const input = $("recipeItemSearch");
    const selectionStart = Number(input?.selectionStart ?? String(value || "").length);
    const selectionEnd = Number(input?.selectionEnd ?? selectionStart);
    recipeState().query = String(value || "");
    renderEditor();
    const nextInput = $("recipeItemSearch");
    if (nextInput) {
      nextInput.focus();
      nextInput.setSelectionRange(Math.min(selectionStart, nextInput.value.length), Math.min(selectionEnd, nextInput.value.length));
    }
  }

  function adminRecipeTab(id) {
    recipeState().tab = String(id || "items");
    renderEditor();
  }

  function adminRecipeTogglePicker() {
    const rs = recipeState();
    rs.pickerOpen = !rs.pickerOpen;
    renderEditor();
  }

  function adminRecipeAdd(token) {
    const { kind, value } = tokenParts(token);
    if (kind === "MATERIAL" && BLOCKED_RECIPE_MATERIALS.has(value)) {
      toast("Алмазная руда не используется в рецептах.", true);
      return;
    }
    const recipe = currentRecipeMutable();
    if (!recipe) return;
    recipe.recipe.push(`${kind.toLowerCase()}:${value}`);
    recipeState().pickerOpen = true;
    renderEditor();
  }

  function adminRecipeRemove(index) {
    removeRecipeItem(Number(index));
  }

  function adminRecipeClear() {
    const recipe = currentRecipeMutable();
    if (!recipe) return;
    recipe.recipe = [];
    renderEditor();
  }

  async function adminRecipeSave(applyMode = "save") {
    const rs = recipeState();
    const recipes = {};
    for (const row of rs.recipes) {
      const list = Array.isArray(row.recipe) ? row.recipe : [];
      if (list.length < 3) {
        toast(`В рецепте ${row.name || row.id} меньше трёх ингредиентов.`, true);
        return;
      }
      recipes[row.id] = list;
    }
    const mode = String(applyMode || "save").toLowerCase() === "apply" ? "apply" : "save";
    const headers = await dangerConfirm(
      mode === "apply" ? "Сохранить рецепты и перезапустить Minecraft, чтобы изменения вступили в силу?" : "Сохранить рецепты без перезагрузки сервера?",
      mode === "apply" ? "NARCOTICS_RECIPES_APPLY" : "NARCOTICS_RECIPES_SAVE",
    );
    if (!headers) return;
    const result = await api("/api/admin/narcotics/recipes", {
      method: "POST",
      headers,
      body: JSON.stringify({ recipes, apply_mode: mode }),
    });
    const reload = result.reload || {};
    const reloadMessage = mode === "save"
      ? (reload.message || "Конфиг сохранён без применения.")
      : (reload.message || (reload.reloaded ? "Рецепты применены." : "Рецепты сохранены, но применение не завершилось."));
    toast(`Рецепты сохранены: ${result.updated?.length || 0}. ${reloadMessage}`, mode === "apply" && !reload.reloaded);
    await loadRecipes();
  }

  return {
    loadRecipes,
    adminRecipeSelect,
    adminRecipeSearch,
    adminRecipeTab,
    adminRecipeTogglePicker,
    adminRecipeAdd,
    adminRecipeRemove,
    adminRecipeClear,
    adminRecipeSave,
  };
}
