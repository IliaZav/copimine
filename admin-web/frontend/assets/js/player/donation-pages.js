export function createPlayerDonationPages(deps) {
  const {
    state,
    setLoading,
    api,
    safeApi,
    setView,
    panel,
    metric,
    kv,
    dt,
    esc,
    short,
    formatDonate,
    asArray,
    cleanText,
    table,
    safetyRail,
    pill,
    number,
    donationSessionKey,
    statusLabel,
    artifactStatusTone,
    randomActionKey,
    setStoredUiState,
    removeStoredUiState,
    copyText,
    toast,
  } = deps;

  async function loadPlayerDonationBalance() {
    setLoading("Загрузка donation-баланса");
    const me = await api("/api/player/me");
    state.user = me.account || {};
    const linked = Boolean(state.user.linked);
    if (!linked) {
      setView(panel("Донат-баланс закрыт", "Сначала привяжи Minecraft-ник к кабинету.", `
        <div class="notice">После привязки появятся пакеты 50 / 100 / 250 / 500 / 1000, история пополнений и mock SBP QR.</div>
      `, `<button class="btn btn-primary" data-click="setTab('link')">Открыть привязку</button>`));
      return;
    }
    const [balance, packs, history] = await Promise.all([
      safeApi("/api/player/donation/balance", { linked: true, balance: 0 }),
      safeApi("/api/player/donation/packs", { packs: [], provider: "MOCK_SBP", rubPerUnit: 1 }),
      safeApi("/api/player/donation/history", { history: [] }),
    ]);
    let session = null;
    if (state.donationSessionId) {
      const result = await safeApi(`/api/player/donation/sbp/session/${encodeURIComponent(state.donationSessionId)}`, null);
      session = result?.session || null;
      if (!session) {
        state.donationSessionId = "";
        removeStoredUiState("copimineDonationSessionId");
      }
    }
    const packButtons = asArray(packs.packs).map((pack) => `
      <button class="btn btn-primary" data-click="playerCreateDonationSession(${number(pack.amount || 0)})">${esc(`${pack.amount} Donation`)}</button>
    `).join("");
    const sessionPanel = session ? `
      <div class="qr-block">
        <img class="qr-image" src="/api/player/donation/sbp/session/${esc(donationSessionKey(session))}/qr.png?_fresh=${Date.now()}" alt="QR mock SBP" />
        <div class="qr-copy">
          ${kv([
            ["Сессия", donationSessionKey(session)],
            ["Код", session.session_code || short(donationSessionKey(session), 8)],
            ["Сумма", formatDonate(session.amount || session.donation_units || 0)],
            ["Статус", statusLabel(session.status || "created")],
            ["Истекает", dt(session.expires_at)],
            ["Провайдер", session.provider || "MOCK_SBP"],
          ])}
          <div class="action-strip">
            <button class="btn btn-secondary" data-click="playerCopyDonationPaymentUrl()">Скопировать ссылку</button>
            <button class="btn btn-secondary" data-click="playerCopyDonationSessionCode()">Скопировать код</button>
            <button class="btn btn-secondary" data-click="playerRefreshDonationSession()">Обновить статус</button>
            <button class="btn btn-secondary" data-click="playerForgetDonationSession()">Скрыть сессию</button>
          </div>
          <div class="notice">Баланс пополнится только после статуса PAID. Пока активен MOCK_SBP foundation, реальный провайдер не подключён.</div>
        </div>
      </div>
    ` : `<div class="notice">Активной payment session пока нет. Выбери один из фиксированных пакетов, чтобы открыть mock SBP QR и ссылку оплаты.</div>`;
    setView(`
      <section class="layout-grid grid-4">
        ${metric("Donation", formatDonate(balance.balance || 0), "Отдельно от AR", "good")}
        ${metric("Курс", `${packs.rubPerUnit || 1} ₽ = 1 DC`, "Фиксированный для всех пакетов", "neutral")}
        ${metric("Провайдер", packs.provider || "MOCK_SBP", "Реальный провайдер пока не подключён", "warn")}
        ${metric("Сессия", session ? statusLabel(session.status || "created") : "нет", session ? `Код ${session.session_code || short(donationSessionKey(session), 8)}` : "Создай новую сессию", session ? "neutral" : "good")}
      </section>
      ${panel("Донат-баланс", "Этот баланс тратится только на donation shop и не смешивается с AR.", kv([
        ["Статус", linked ? "привязан" : "нет привязки"],
        ["Баланс", formatDonate(balance.balance || 0)],
        ["Пополнение", "только через фиксированные пакеты"],
        ["Выдачи", "предметы забираются только в игре"],
      ]), `<button class="btn btn-secondary" data-click="setTab('donation-items')">Мои донат-предметы</button>`)}
      ${panel("Пополнить", "Создай payment session и оплати её через mock SBP foundation. Сайт остаётся канонической точкой оплаты.", `
        <div class="action-strip wrap">${packButtons}</div>
        <div class="spacer-12"></div>
        <div class="notice">Пакеты фиксированы: 50 / 100 / 250 / 500 / 1000. Donation нельзя обменять на AR.</div>
      `)}
      ${panel("Mock SBP Session", "QR генерируется локально backend-ом. Если QR не нужен, можно использовать ссылку и код сессии.", sessionPanel)}
      ${panel("История пополнений и списаний", "Только donation balance, без AR и без скрытых списаний.", table("player-donation-history", asArray(history.history), [
        { key: "created_at", label: "Время", render: (value) => dt(value) },
        { key: "delta", label: "Изменение", render: (value) => formatDonate(value || 0) },
        { key: "balance_after", label: "После", render: (value) => formatDonate(value || 0) },
        { key: "reason", label: "Причина", render: (value) => short(value || "", 80) },
        { key: "source", label: "Источник", render: (value) => cleanText(value || "—") },
      ], { pageSize: 12 }))}
    `);
  }

  async function loadPlayerDonationShop() {
    setLoading("Загрузка donation-лавки");
    const me = await api("/api/player/me");
    state.user = me.account || {};
    const linked = Boolean(state.user.linked);
    if (!linked) {
      setView(panel("Донат-лавка закрыта", "Сначала привяжи Minecraft-ник.", `
        <div class="notice">После привязки сайт сможет создавать purchase-intent и подсказывать, что предмет нужно забрать в игре.</div>
      `, `<button class="btn btn-primary" data-click="setTab('link')">Открыть привязку</button>`));
      return;
    }
    const [catalog, balance] = await Promise.all([
      safeApi("/api/player/shop/donation-items", { items: [], catalogVersion: 0 }),
      safeApi("/api/player/donation/balance", { balance: 0 }),
    ]);
    const rows = asArray(catalog.items).map((row) => {
      const status = row.owned_active ? "Уже у тебя" : (row.claim_available ? "Можно забрать" : (row.enabled ? "Не куплен" : "Отключён"));
      const action = row.owned_active
        ? `<span class="btn btn-secondary btn-small disabled">Уже у тебя</span>`
        : row.claim_available
          ? `<button class="btn btn-secondary btn-small" data-click="setTab('donation-items')">Забрать в игре</button>`
          : row.enabled
            ? `<button class="btn btn-primary btn-small" data-click='playerBuyDonationItem(${JSON.stringify(String(row.item_id || ""))}, ${JSON.stringify(cleanText(row.display_name || "предмет"))}, ${number(row.price_donation || 0)})'>Купить</button>`
            : `<span class="btn btn-secondary btn-small disabled">Отключён</span>`;
      return {
        ...row,
        status_text: status,
        action_html: action,
      };
    });
    const focusItemId = String(state.donationFocusItemId || "").trim().toLowerCase();
    if (focusItemId) {
      rows.sort((a, b) => {
        const aMatch = String(a.item_id || "").toLowerCase() === focusItemId ? 0 : 1;
        const bMatch = String(b.item_id || "").toLowerCase() === focusItemId ? 0 : 1;
        return aMatch - bMatch;
      });
    }
    setView(`
      <section class="layout-grid grid-4">
        ${metric("Баланс", formatDonate(balance.balance || 0), "Отдельно от AR", "good")}
        ${metric("Каталог", asArray(catalog.items).length, `Версия ${catalog.catalogVersion || 0}`, "neutral")}
        ${metric("Готово к claim", rows.filter((row) => row.claim_available).length, "Предмет уже куплен и ждёт выдачи", rows.some((row) => row.claim_available) ? "warn" : "good")}
        ${metric("Активные", rows.filter((row) => row.owned_active).length, "Предмет уже выдан и активен", rows.some((row) => row.owned_active) ? "good" : "neutral")}
      </section>
      ${panel("Донатная лавка", "Покупка происходит только на сайте. После оплаты предмет нужно забрать в игре через лавку доната.", `
        ${focusItemId ? `<div class="notice">Открыт товар по прямой ссылке: <strong>${esc(focusItemId)}</strong>.</div>` : ""}
        ${table("player-donation-shop", rows, [
          { key: "display_name", label: "Предмет", render: (value) => `<strong>${esc(cleanText(value || "Предмет"))}</strong>` },
          { key: "price_donation", label: "Цена", render: (value) => formatDonate(value || 0) },
          { key: "effect_description", label: "Эффект", render: (value) => short(value || "", 110) },
          { key: "cooldown_seconds", label: "Кулдаун", render: (value) => value ? `${value} сек.` : "—" },
          { key: "status_text", label: "Статус", render: (value, row) => pill(value, row.owned_active ? "good" : (row.claim_available ? "warn" : "neutral")) },
          { key: "action_html", label: "Действие", render: (value) => value },
        ], { pageSize: 10 })}
      `)}
      ${panel("Правила выдачи", "Игровой мир остаётся единственной точкой физической выдачи и возврата.", safetyRail([
        ["Покупка", "После списания donation появится claim со статусом «Заберите предмет в игре».", "good"],
        ["Выдача", "Физическая выдача идёт только через блок лавки в Minecraft.", "warn"],
        ["Возврат", "Если предмет потерян внешне, бесплатный возврат идёт только через экран «Вернуть утерянные предметы».", "neutral"],
      ]), `<button class="btn btn-secondary" data-click="setTab('donation-items')">Открыть мои донат-предметы</button>`)}
    `);
  }

  async function loadPlayerDonationItems() {
    setLoading("Загрузка донат-предметов");
    const me = await api("/api/player/me");
    state.user = me.account || {};
    const linked = Boolean(state.user.linked);
    if (!linked) {
      setView(panel("Мои донат-предметы", "Сначала привяжи Minecraft-ник.", `
        <div class="notice">Без привязки нельзя понять, какие claims и item instances принадлежат твоему игровому персонажу.</div>
      `, `<button class="btn btn-primary" data-click="setTab('link')">Открыть привязку</button>`));
      return;
    }
    const owned = await safeApi("/api/player/shop/owned", { linked: true, claims: [], instances: [], summary: {} });
    setView(`
      <section class="layout-grid grid-4">
        ${metric("Выдачи", asArray(owned.claims).length, "Ожидают или уже завершены", asArray(owned.claims).length ? "warn" : "neutral")}
        ${metric("Активные", owned.summary?.active || 0, "Сейчас у игрока", (owned.summary?.active || 0) ? "good" : "neutral")}
        ${metric("Reclaimable", owned.summary?.reclaimable || 0, "Можно вернуть через лавку", (owned.summary?.reclaimable || 0) ? "warn" : "neutral")}
        ${metric("Ожидают выдачи", owned.summary?.claimPending || 0, "Выдача и review идут только в игре", (owned.summary?.claimPending || 0) ? "warn" : "good")}
      </section>
      ${panel("Выдачи и покупки", "Сайт показывает статус, но физическая выдача идёт только через игровую лавку.", table("player-donation-owned-claims", asArray(owned.claims), [
        { key: "purchase_created_at", label: "Покупка", render: (value) => dt(value) },
        { key: "display_name", label: "Предмет", render: (value, row) => `<strong>${esc(cleanText(value || row.item_id || "Предмет"))}</strong><br><span class="muted">${esc(row.item_id || "—")}</span>` },
        { key: "price_donation", label: "Цена", render: (value) => formatDonate(value || 0) },
        { key: "status", label: "Claim", render: (value) => pill(statusLabel(value || "pending"), artifactStatusTone(value)) },
        { key: "purchase_status", label: "Покупка", render: (value) => pill(statusLabel(value || "pending"), artifactStatusTone(value)) },
      ], { pageSize: 12 }))}
      ${panel("Выданные экземпляры", "Только база статусов решает, является ли предмет активным, потерянным или недействительным.", table("player-donation-owned-instances", asArray(owned.instances), [
        { key: "updated_at", label: "Обновлён", render: (value) => dt(value) },
        { key: "display_name", label: "Предмет", render: (value, row) => `<strong>${esc(cleanText(value || row.item_id || "Предмет"))}</strong><br><span class="muted">${esc(row.item_id || "—")}</span>` },
        { key: "status", label: "Статус", render: (value) => pill(statusLabel(value || "pending"), artifactStatusTone(value)) },
      ], { pageSize: 12 }))}
      ${panel("Как это работает", "Возврат и anti-dupe проверяются только в игре и только через официальный workflow.", safetyRail([
        ["Claim", "Если статус UNCLAIMED, предмет нужно забрать в игровой лавке.", "good"],
        ["Reclaim", "LOST_RECLAIMABLE возвращается по одному предмету за раз.", "warn"],
        ["Broken/Consumed", "BROKEN и CONSUMED бесплатно не возвращаются.", "bad"],
      ]), `<button class="btn btn-secondary" data-click="setTab('donation-shop')">Вернуться в донат-лавку</button>`)}
    `);
  }

  async function playerCreateDonationSession(amount) {
    if (state.donationBusy) return;
    try {
      state.donationBusy = true;
      const result = await api("/api/player/donation/sbp/session", {
        method: "POST",
        body: JSON.stringify({ amount: number(amount || 0), idempotency_key: randomActionKey("don-session") }),
      });
      state.donationSessionId = donationSessionKey(result?.session);
      if (state.donationSessionId) setStoredUiState("copimineDonationSessionId", state.donationSessionId);
      toast(`Создана сессия на ${number(amount || 0)} Donation.`);
      await loadPlayerDonationBalance();
    } catch (err) {
      toast(err.message, true);
    } finally {
      state.donationBusy = false;
    }
  }

  async function playerRefreshDonationSession() {
    if (!state.donationSessionId) {
      toast("Активной сессии нет.", true);
      return;
    }
    await loadPlayerDonationBalance();
  }

  function playerForgetDonationSession() {
    state.donationSessionId = "";
    removeStoredUiState("copimineDonationSessionId");
    void loadPlayerDonationBalance();
  }

  async function playerCopyDonationSessionCode() {
    if (!state.donationSessionId) return;
    await copyText(state.donationSessionId.slice(-8), "Код сессии скопирован");
  }

  async function playerCopyDonationPaymentUrl() {
    if (!state.donationSessionId) return;
    await copyText(`${location.origin}/#donation-balance?session=${encodeURIComponent(state.donationSessionId)}`, "Ссылка оплаты скопирована");
  }

  async function playerBuyDonationItem(itemId, displayName = "предмет", price = 0) {
    try {
      if (!window.confirm(`Списать ${number(price || 0)} Donation за «${cleanText(displayName || itemId || "предмет")}»?`)) {
        return;
      }
      const result = await api("/api/player/shop/purchase-intent", {
        method: "POST",
        body: JSON.stringify({ item_id: String(itemId || ""), idempotency_key: randomActionKey("don-buy") }),
      });
      toast(`Покупка создана. Предмет ${result.itemId} забирается в игре.`);
      await loadPlayerDonationShop();
    } catch (err) {
      toast(err.message, true);
    }
  }

  return {
    loadPlayerDonationBalance,
    loadPlayerDonationShop,
    loadPlayerDonationItems,
    playerCreateDonationSession,
    playerRefreshDonationSession,
    playerForgetDonationSession,
    playerCopyDonationSessionCode,
    playerCopyDonationPaymentUrl,
    playerBuyDonationItem,
  };
}
