export function createPlayerArtifactPages(deps) {
  const {
    $,
    state,
    setLoading,
    api,
    safeApi,
    setView,
    panel,
    metric,
    table,
    pill,
    esc,
    cleanText,
    short,
    dt,
    asArray,
    empty,
    formatAr,
    number,
    statusLabel,
    artifactStatusTone,
    randomActionKey,
    toast,
  } = deps;

  let lastPayload = null;

  function itemTitle(row) {
    return cleanText(row?.display_name || row?.name || row?.item_id || "Предмет");
  }

  function itemId(row) {
    return String(row?.item_id || "").trim();
  }

  function itemPrice(row) {
    return number(row?.price_ar || 0);
  }

  function itemOption(row, selectedId) {
    const id = itemId(row);
    if (!id) return "";
    const selected = id === selectedId ? " selected" : "";
    return `<option value="${esc(id)}"${selected}>${esc(itemTitle(row))} · ${esc(formatAr(itemPrice(row)))}</option>`;
  }

  function filterCatalog(catalog) {
    const q = cleanText(state.playerArtifactSearch || "").toLowerCase();
    if (!q) return catalog;
    return catalog.filter((row) => {
      const haystack = [
        itemTitle(row),
        itemId(row),
        row.effect_description,
        row.category,
      ].map((value) => cleanText(value).toLowerCase()).join(" ");
      return haystack.includes(q);
    });
  }

  function resolveSelectedItem(catalog) {
    const enabled = catalog.filter((row) => row.enabled !== false);
    const selectedId = String(state.playerArSelectedItemId || "").trim();
    return enabled.find((row) => itemId(row) === selectedId) || enabled[0] || catalog[0] || null;
  }

  function purchaseCard(catalog, bank) {
    const enabled = catalog.filter((row) => row.enabled !== false);
    const selected = resolveSelectedItem(catalog);
    const selectedId = itemId(selected);
    state.playerArSelectedItemId = selectedId;
    const pinReady = Boolean(bank.pin?.set);
    const enoughBalance = selected ? number(bank.account?.balance || 0) >= itemPrice(selected) : false;
    const helper = !selected
      ? "Каталог пуст."
      : !pinReady
        ? "Сначала задай PIN в разделе банка."
        : enoughBalance
          ? "Покупка создаст выдачу предмета в игре."
          : "На счёте недостаточно AR.";

    return `
      <article class="artifact-purchase-card">
        <div class="artifact-purchase-head">
          <span>AR-лавка</span>
          <strong>${selected ? esc(itemTitle(selected)) : "Нет предметов"}</strong>
          <p>${esc(helper)}</p>
        </div>
        <div class="artifact-purchase-price">
          <span>Цена</span>
          <strong>${selected ? esc(formatAr(itemPrice(selected))) : "—"}</strong>
        </div>
        <div class="form-grid artifact-buy-form">
          <div class="field-stack full">
            <label for="playerArtifactSearch">Поиск</label>
            <input id="playerArtifactSearch" data-input="playerArtifactSearch" value="${esc(state.playerArtifactSearch || "")}" placeholder="Название, ID или эффект" autocomplete="off" />
          </div>
          <div class="field-stack full">
            <label for="playerArItemSelect">Предмет</label>
            <select id="playerArItemSelect" data-input="playerSelectArItem">${enabled.map((row) => itemOption(row, selectedId)).join("")}</select>
          </div>
          <div class="field-stack">
            <label for="playerArPin">PIN банка AR</label>
            <input id="playerArPin" type="password" inputmode="numeric" autocomplete="one-time-code" placeholder="PIN" />
          </div>
          <div class="field-stack artifact-buy-action">
            <label>&nbsp;</label>
            <button class="btn btn-primary" data-click="playerBuyArItem()"${selected && pinReady && enoughBalance ? "" : " disabled"}>Купить</button>
          </div>
        </div>
        <div class="artifact-buy-note">
          <span>Выдача</span>
          <code>/cmartifacts claim</code>
        </div>
      </article>
    `;
  }

  function catalogCards(catalog) {
    const filtered = filterCatalog(catalog);
    if (!filtered.length) {
      return empty("Ничего не найдено", "Измени поисковую строку или проверь доступность каталога.");
    }
    return `
      <div class="artifact-catalog-grid">
        ${filtered.map((row) => {
          const id = itemId(row);
          const enabled = row.enabled !== false;
          return `
            <article class="artifact-item-card ${enabled ? "" : "is-disabled"}">
              <div>
                <span>${esc(row.category || "предмет")}</span>
                <strong>${esc(itemTitle(row))}</strong>
                <p>${esc(short(row.effect_description || "Описание появится после настройки предмета.", 130))}</p>
              </div>
              <div class="artifact-item-meta">
                <b>${esc(formatAr(itemPrice(row)))}</b>
                <small>${esc(id || "без ID")}</small>
              </div>
              <button class="btn ${enabled ? "btn-secondary" : "btn-ghost"} btn-small" data-click='playerSelectArItem(${JSON.stringify(id)})'${enabled ? "" : " disabled"}>Выбрать</button>
            </article>
          `;
        }).join("")}
      </div>
    `;
  }

  function renderArtifacts(payload) {
    lastPayload = payload;
    const { data, catalogPayload, bank } = payload;
    if (!data.linked) {
      setView(panel("Артефакты", "Сначала привяжи Minecraft-аккаунт", empty("Minecraft-ник не привязан", "После привязки здесь будут покупки, выдача и ремонт предметов.")));
      return;
    }

    const catalog = asArray(catalogPayload.items);
    const purchases = asArray(data.purchases);
    const pending = asArray(data.pending);
    const repairs = asArray(data.repairs);

    setView(`
      <section class="artifact-dashboard">
        ${metric("Баланс AR", formatAr(bank.account?.balance || 0), bank.pin?.set ? "PIN настроен" : "Нужно задать PIN", bank.pin?.set ? "good" : "warn")}
        ${metric("Каталог", catalog.length, "Доступные предметы", catalog.length ? "good" : "neutral")}
        ${metric("К выдаче", pending.length, "Предметы ждут в игре", pending.length ? "warn" : "good")}
      </section>
      <section class="layout-grid grid-2 artifact-workbench">
        ${purchaseCard(catalog, bank)}
        ${panel("Последние операции", "Последние покупки, выдачи и изменения статусов.", table("player-artifact-purchases", purchases, [
          { key: "created_at", label: "Время", render: (value) => dt(value) },
          { key: "item_id", label: "Предмет" },
          { key: "price_ar", label: "AR", render: (value) => formatAr(value || 0) },
          { key: "status", label: "Статус", render: (value) => pill(statusLabel(value), artifactStatusTone(value)) },
        ], { pageSize: 8 }))}
      </section>
      ${panel("Каталог AR", "Выбор предметов, цены и покупка со счёта AR.", catalogCards(catalog))}
      <section class="layout-grid grid-2">
        ${panel("Ожидают выдачи", "Забери предметы на сервере.", table("player-artifact-pending", pending, [
          { key: "created_at", label: "Создано", render: (value) => dt(value) },
          { key: "item_id", label: "Предмет" },
          { key: "status", label: "Статус", render: (value) => pill(statusLabel(value || "pending"), artifactStatusTone(value)) },
        ], { pageSize: 8 }), `<div class="notice">В игре используй /cmartifacts claim.</div>`)}
        ${panel("Ремонт", "История восстановления официальных предметов.", table("player-artifact-repairs", repairs, [
          { key: "created_at", label: "Время", render: (value) => dt(value) },
          { key: "item_id", label: "Предмет" },
          { key: "repair_cost_ar", label: "AR", render: (value) => formatAr(value || 0) },
          { key: "status", label: "Статус", render: (value) => pill(statusLabel(value), artifactStatusTone(value)) },
        ], { pageSize: 8 }))}
      </section>
    `);
  }

  async function loadPlayerArtifacts() {
    setLoading("Загрузка AR-лавки");
    const [data, catalogPayload, bank] = await Promise.all([
      safeApi("/api/player/artifacts", { linked: false, purchases: [], pending: [], repairs: [] }),
      safeApi("/api/player/shop/ar-items", { items: [] }),
      safeApi("/api/player/bank", { account: { balance: 0 }, pin: { set: false }, linked: false }),
    ]);
    renderArtifacts({ data, catalogPayload, bank });
  }

  function playerArtifactSearch(value) {
    state.playerArtifactSearch = String(value || "").trim();
    if (lastPayload) renderArtifacts(lastPayload);
  }

  function playerSelectArItem(itemId) {
    state.playerArSelectedItemId = String(itemId || "").trim();
    if (lastPayload) renderArtifacts(lastPayload);
  }

  async function playerBuyArItem(itemId = "") {
    try {
      const selected = String(itemId || state.playerArSelectedItemId || $("playerArItemSelect")?.value || "").trim();
      const pin = $("playerArPin")?.value?.trim() || "";
      if (!selected) return toast("Выбери предмет из каталога.", true);
      if (!pin) return toast("Введи PIN банка AR.", true);
      const result = await api("/api/player/shop/ar-purchase-intent", {
        method: "POST",
        body: JSON.stringify({
          item_id: selected,
          pin,
          idempotency_key: randomActionKey("ar-buy"),
        }),
      });
      if ($("playerArPin")) $("playerArPin").value = "";
      toast(`Покупка создана: ${cleanText(result.itemId || selected)}. Забери предмет в игре через /cmartifacts claim.`);
      await loadPlayerArtifacts();
    } catch (err) {
      toast(err.message, true);
    }
  }

  return {
    loadPlayerArtifacts,
    playerArtifactSearch,
    playerSelectArItem,
    playerBuyArItem,
  };
}
