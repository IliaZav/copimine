export function createAdminCommercePages(deps) {
  const {
    $,
    api,
    safeApi,
    setLoading,
    setView,
    panel,
    metric,
    kv,
    formatAr,
    formatDonate,
    asArray,
    dt,
    table,
    pill,
    artifactStatusTone,
    statusLabel,
    esc,
    cleanText,
    short,
    ledgerRows,
    dangerConfirm,
    number,
    randomActionKey,
    toast,
  } = deps;

  let cachedPlayers = [];
  let cachedEconomySnapshot = null;

  function paymentModeLabel(value) {
    return String(value || "").toUpperCase() === "MOCK_SBP" ? "Тестовый режим" : String(value || "—");
  }

  function playerLabel(row) {
    const name = cleanText(row?.name || row?.player_name || row?.username || row?.minecraft_name || row?.uuid || "");
    const uuid = cleanText(row?.uuid || row?.player_uuid || "");
    return { name, uuid };
  }

  function playerOptions(rows, selectedName = "") {
    return asArray(rows)
      .map((row) => {
        const { name, uuid } = playerLabel(row);
        if (!name) return "";
        const selected = name === selectedName ? " selected" : "";
        return `<option value="${esc(name)}"${selected} data-uuid="${esc(uuid)}">${esc(name)}</option>`;
      })
      .filter(Boolean)
      .join("");
  }

  function catalogOptions(items, selectedItemId = "") {
    return asArray(items)
      .map((item) => {
        const itemId = cleanText(item?.item_id || item?.itemId || "");
        const title = cleanText(item?.display_name || itemId || "Предмет");
        if (!itemId) return "";
        const selected = itemId === selectedItemId ? " selected" : "";
        return `<option value="${esc(itemId)}"${selected}>${esc(title)} · ${esc(itemId)}</option>`;
      })
      .filter(Boolean)
      .join("");
  }

  async function ensurePlayers() {
    if (cachedPlayers.length) return cachedPlayers;
    const data = await safeApi("/api/players", { players: [] });
    cachedPlayers = asArray(data.players)
      .map((row) => ({
        name: cleanText(row.name || row.player || row.username || ""),
        uuid: cleanText(row.uuid || ""),
      }))
      .filter((row) => row.name)
      .sort((left, right) => left.name.localeCompare(right.name, "ru-RU"));
    return cachedPlayers;
  }

  function selectedPlayerBySelect(id) {
    const select = $(id);
    if (!select) return { name: "", uuid: "" };
    const option = select.options[select.selectedIndex];
    return {
      name: cleanText(select.value || ""),
      uuid: cleanText(option?.dataset?.uuid || ""),
    };
  }

  function normalizePlayerKey(value) {
    return cleanText(value || "").trim().toLowerCase();
  }

  function resolveBalanceRow(rows, player) {
    const normalizedName = normalizePlayerKey(player?.name);
    const normalizedUuid = cleanText(player?.uuid || "").trim().toLowerCase();
    return asArray(rows).find((row) => {
      const rowName = normalizePlayerKey(row?.name || row?.player_name || row?.player || row?.minecraft_name || "");
      const rowUuid = cleanText(row?.uuid || row?.player_uuid || "").trim().toLowerCase();
      return (normalizedUuid && rowUuid === normalizedUuid) || (normalizedName && rowName === normalizedName);
    }) || null;
  }

  function bindBalanceEditors(snapshot) {
    const select = $("adminBalancePlayer");
    if (!select) return;
    const sync = () => {
      const player = selectedPlayerBySelect("adminBalancePlayer");
      const arRow = resolveBalanceRow(snapshot?.arPlayers, player) || {};
      const donationRow = resolveBalanceRow(snapshot?.donationBalances, player) || {};
      const arBalance = Number(arRow.balance || arRow.amount || 0);
      const donationBalance = Number(donationRow.balance || 0);
      if ($("adminArBalanceValue")) $("adminArBalanceValue").value = String(arBalance);
      if ($("adminDonationBalanceValue")) $("adminDonationBalanceValue").value = String(donationBalance);
      if ($("adminBalanceArCurrent")) $("adminBalanceArCurrent").textContent = formatAr(arBalance);
      if ($("adminBalanceDonationCurrent")) $("adminBalanceDonationCurrent").textContent = formatDonate(donationBalance);
      if ($("adminBalancePlayerMeta")) {
        $("adminBalancePlayerMeta").textContent = player.name
          ? `${player.name}${player.uuid ? ` · ${short(player.uuid, 18)}` : ""}`
          : "Игрок не выбран";
      }
    };
    select.addEventListener("change", sync);
    sync();
  }

  async function loadEconomy() {
    setLoading("Загружаю экономику");
    const players = await ensurePlayers();
    const [overview, ledger, donation, donationCatalog, treasury, audit] = await Promise.all([
      safeApi("/api/economy/ares/overview", {}),
      safeApi("/api/economy/ares/ledger?limit=200", { events: [], balances: [], transactions: [], assets: [], summary: {} }),
      safeApi("/api/admin/donation/overview?limit=80", { summary: {}, balances: [], ledger: [], claims: [], sessions: [] }),
      safeApi("/api/admin/shop/donation-items", { items: [], catalogVersion: 0, updatedAt: 0 }),
      safeApi("/api/admin/economy/treasury", { account: {}, pin: {}, ledger: [], ownerName: "" }),
      safeApi("/api/audit?limit=120&action=balance", { rows: [] }),
    ]);

    const arPlayers = asArray(ledger.balances).map((row) => ({
      player: cleanText(row.name || row.player_name || row.uuid || "Игрок"),
      amount: row.balance || 0,
      inventory: row.inventory_balance || 0,
      enderChest: row.ender_balance || 0,
      updatedAt: row.updated_at || 0,
    }));
    const transactions = asArray(ledger.transactions);
    const donationBalances = asArray(donation.balances);
    const donationClaims = asArray(donation.claims);
    const donationSessions = asArray(donation.sessions);
    const donationItems = asArray(donationCatalog.items);
    const arSummary = ledger.summary || {};
    const donationSummary = donation.summary || {};
    const firstPlayer = players[0]?.name || "";
    const firstItem = donationItems[0]?.item_id || "";
    const balanceAuditRows = asArray(audit.rows)
      .filter((row) => {
        const action = String(row?.action || "");
        return action.includes("ar.balance.") || action.includes("donation.balance.");
      })
      .map((row) => {
        const action = String(row?.action || "");
        const details = row?.details && typeof row.details === "object" ? row.details : {};
        const isAr = action.startsWith("ar.balance.");
        const mode = action.endsWith(".set") ? "Изменить" : "Пополнить";
        const after = details.after ?? details.balanceAfter;
        const before = details.before;
        const delta = details.delta ?? details.amount ?? 0;
        let changeText = isAr ? formatAr(delta || 0) : formatDonate(delta || 0);
        if (action.endsWith(".set") && after != null) {
          const beforeText = isAr ? formatAr(before || 0) : formatDonate(before || 0);
          const afterText = isAr ? formatAr(after || 0) : formatDonate(after || 0);
          changeText = `${beforeText} -> ${afterText}`;
        }
        return {
          time: row.timestamp || 0,
          actor: cleanText(row.actor || "—"),
          target: cleanText(row.target || details.uuid || "—"),
          currency: isAr ? "AR" : "Donation",
          mode,
          change: changeText,
          reason: cleanText(details.reason || "—"),
        };
      });
    cachedEconomySnapshot = {
      players,
      arPlayers,
      donationBalances,
    };

    setView(`
      <section class="layout-grid grid-4">
        ${metric("AR на руках", formatAr(arSummary.totalBalance ?? overview.totalKnownInPlayerData ?? 0), "Общий баланс игроков", "good")}
        ${metric("Счетов AR", arSummary.holders ?? arPlayers.length, "Игроки с найденным балансом", "neutral")}
        ${metric("Donation", formatDonate(donationSummary.totalBalance ?? 0), "Отдельный донатный баланс", donationSummary.totalBalance ? "good" : "neutral")}
        ${metric("Ожидают выдачи", donationSummary.unclaimedItems ?? 0, "Предметы и сессии, которые ещё не завершены", Number(donationSummary.unclaimedItems || 0) ? "warn" : "good")}
      </section>

      <section class="layout-grid grid-2">
        ${panel("Быстрые действия", "Только рабочие операции без шумной служебной статистики.", `
          <div class="action-strip">
            <button class="btn btn-primary" data-click="createEconomySnapshot()">Снимок AR</button>
            <button class="btn btn-secondary" data-click="scanAresWorld()">Скан мира</button>
          </div>
          <div class="spacer-12"></div>
          ${kv([
            ["Счет казны", treasury.account?.account_id || treasury.account?.accountId || "—"],
            ["Владелец казны", treasury.ownerName || "—"],
            ["Баланс казны", formatAr(treasury.account?.balance || 0)],
            ["PIN казны", treasury.pin?.visiblePin || "не задан"],
            ["Каталог donation", `${donationItems.length} предметов · версия ${donationCatalog.catalogVersion || 0}`],
            ["Последнее обновление каталога", dt(donationCatalog.updatedAt || 0)],
          ])}
        `)}
        ${panel("Баланс игрока", "Выберите игрока, посмотрите его текущие AR и donation, затем задайте точное значение и сохраните.", `
          <div class="field-stack">
            <label for="adminBalancePlayer">Игрок</label>
            <select id="adminBalancePlayer">${playerOptions(players, firstPlayer)}</select>
          </div>
          <div class="detail-chip-row">
            <span class="detail-chip"><strong id="adminBalanceArCurrent">${formatAr(0)}</strong><small>AR сейчас</small></span>
            <span class="detail-chip"><strong id="adminBalanceDonationCurrent">${formatDonate(0)}</strong><small>Donation сейчас</small></span>
            <span class="detail-chip wide"><strong id="adminBalancePlayerMeta">${esc(firstPlayer || "Игрок не выбран")}</strong><small>Текущая цель изменения</small></span>
          </div>
          <div class="form-grid compact-grid">
            <input id="adminArBalanceValue" type="number" min="0" step="1" placeholder="Итоговый баланс AR" />
            <input id="adminArReason" placeholder="Причина изменения AR" />
          </div>
          <button class="btn btn-primary full" data-click="adminArSetBalance()">Сохранить AR-баланс</button>
          <div class="spacer-16"></div>
          <div class="form-grid compact-grid">
            <input id="adminDonationBalanceValue" type="number" min="0" step="1" placeholder="Итоговый баланс donation" />
            <input id="adminDonationReason" placeholder="Причина изменения donation" />
          </div>
          <button class="btn btn-primary full" data-click="adminDonationSetBalance()">Сохранить donation-баланс</button>
          <div class="spacer-16"></div>
          <div class="field-stack">
            <label for="donationTestPlayer">Игрок для тестовой покупки</label>
            <select id="donationTestPlayer">${playerOptions(players, firstPlayer)}</select>
          </div>
          <div class="field-stack">
            <label for="donationTestItemId">Предмет из donation-лавки</label>
            <select id="donationTestItemId">${catalogOptions(donationItems, firstItem)}</select>
          </div>
          <button class="btn btn-secondary full" data-click="adminDonationTestPurchase()">Создать тестовую покупку</button>
        `)}
      </section>

      <section class="layout-grid grid-2">
        ${panel("Журнал правок баланса", "Кто, кому и какую валюту менял на этой панели.", table("balance-admin-audit", balanceAuditRows, [
          { key: "time", label: "Время", render: (value) => dt(value) },
          { key: "actor", label: "Админ" },
          { key: "target", label: "Игрок" },
          { key: "currency", label: "Валюта" },
          { key: "mode", label: "Операция" },
          { key: "change", label: "Изменение" },
          { key: "reason", label: "Причина", render: (value) => short(value || "—", 72) },
        ], { pageSize: 10 }))}
        ${panel("Игроки с AR", "Только баланс и хранилища, без вторичного технического мусора.", table("economy-players", arPlayers, [
          { key: "player", label: "Игрок" },
          { key: "amount", label: "Баланс", render: (value) => formatAr(value || 0) },
          { key: "inventory", label: "Инвентарь", render: (value) => formatAr(value || 0) },
          { key: "enderChest", label: "Эндер", render: (value) => formatAr(value || 0) },
          { key: "updatedAt", label: "Обновлён", render: (value) => dt(value) },
        ], { pageSize: 12 }))}
      </section>

      <section class="layout-grid grid-2">
        ${panel("Последние транзакции AR", "Переводы, переплавки и ключевые движения.", table("economy-transactions-table", transactions, [
          { key: "time", label: "Время", render: (value) => dt(value) },
          { key: "type", label: "Тип" },
          { key: "from_name", label: "От" },
          { key: "to_name", label: "Кому" },
          { key: "amount", label: "Сумма", render: (value) => formatAr(value || 0) },
          { key: "details", label: "Детали", render: (value) => short(value || "", 80) },
        ], { pageSize: 12 }))}
      </section>

      <section class="layout-grid grid-2">
        ${panel("Donation-балансы", "Живые счета игроков по донатной валюте.", table("donation-balances", donationBalances, [
          { key: "player_name", label: "Игрок", render: (value, row) => esc(cleanText(value || row.player_uuid || "—")) },
          { key: "balance", label: "DC", render: (value) => formatDonate(value || 0) },
          { key: "updated_at", label: "Обновлён", render: (value) => dt(value) },
        ], { pageSize: 12 }))}
        ${panel("Предметы в очереди", "Покупки, которые ещё не дошли до финальной выдачи.", table("donation-claims", donationClaims, [
          { key: "created_at", label: "Создан", render: (value) => dt(value) },
          { key: "player_uuid", label: "Игрок" },
          { key: "display_name", label: "Предмет", render: (value, row) => `<strong>${esc(cleanText(value || row.item_id || "Предмет"))}</strong><br><span class="muted">${esc(row.item_id || "—")}</span>` },
          { key: "amount", label: "Кол-во" },
          { key: "status", label: "Статус", render: (value) => pill(statusLabel(value || "pending"), artifactStatusTone(value)) },
        ], { pageSize: 12 }))}
      </section>

      <section class="layout-grid grid-2">
        ${panel("Платёжные сессии", "Только активные и недавно изменённые сессии.", table("donation-sessions", donationSessions, [
          { key: "created_at", label: "Создана", render: (value) => dt(value) },
          { key: "player_name", label: "Игрок", render: (value, row) => esc(cleanText(value || row.player_uuid || "—")) },
          { key: "provider", label: "Режим", render: (value) => paymentModeLabel(value) },
          { key: "amount", label: "Сумма", render: (value) => formatDonate(value || 0) },
          { key: "status", label: "Статус", render: (value) => pill(statusLabel(value || "pending"), artifactStatusTone(value)) },
          {
            key: "id",
            label: "Управление",
            render: (value, row) => {
              const status = String(row.status || "").toUpperCase();
              if (status === "PAID") return `<span class="btn btn-secondary btn-small disabled">Оплачено</span>`;
              if (status === "CANCELLED") return `<span class="btn btn-secondary btn-small disabled">Отменено</span>`;
              if (status === "EXPIRED") return `<span class="btn btn-secondary btn-small disabled">Истекла</span>`;
              return `<div class="action-strip"><button class="btn btn-primary btn-small" data-click="adminDonationMarkPaid('${row.id}')">Подтвердить</button><button class="btn btn-secondary btn-small" data-click="adminDonationCancelSession('${row.id}')">Отменить</button></div>`;
            },
          },
        ], { pageSize: 10 }))}
        ${panel("Журнал экономики", "Короткая лента важных событий по AR и донату.", `<div class="economy-ledger">${ledgerRows(asArray(ledger.events), "economy-ledger")}</div>`)}
      </section>
    `);
    bindBalanceEditors(cachedEconomySnapshot);
  }

  async function createEconomySnapshot() {
    try {
      await api("/api/economy/ares/snapshots", { method: "POST", body: "{}" });
      toast("Снимок экономики создан");
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function scanAresWorld() {
    try {
      toast("Скан мира запущен");
      await api("/api/economy/ares/scan-world", { method: "POST", body: "{}" });
      toast("Скан мира завершён");
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function adminDonationAddBalance() {
    try {
      const player = selectedPlayerBySelect("donationAdminPlayer");
      const headers = await dangerConfirm(`Пополнить donation-баланс игрока ${player.name || "без имени"}`, "DONATION_ADD_BALANCE");
      if (!headers) return;
      await api("/api/admin/donation/add-balance", {
        method: "POST",
        headers,
        body: JSON.stringify({
          minecraft_uuid: player.uuid || "",
          minecraft_name: player.name || "",
          amount: number($("donationAdminAmount")?.value || 0),
          reason: $("donationAdminReason")?.value?.trim() || "admin-topup",
          idempotency_key: randomActionKey("don-admin-topup"),
        }),
      });
      toast("Donation-баланс пополнен");
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function adminArAddBalance() {
    try {
      const player = selectedPlayerBySelect("arAdminPlayer");
      const headers = await dangerConfirm(`Пополнить AR-баланс игрока ${player.name || "без имени"}`, "AR_ADD_BALANCE");
      if (!headers) return;
      await api("/api/admin/economy/ar/add-balance", {
        method: "POST",
        headers,
        body: JSON.stringify({
          minecraft_uuid: player.uuid || "",
          minecraft_name: player.name || "",
          amount: number($("arAdminAmount")?.value || 0),
          reason: $("arAdminReason")?.value?.trim() || "admin-ar-topup",
          idempotency_key: randomActionKey("ar-admin-topup"),
        }),
      });
      toast("AR-баланс пополнен");
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function adminDonationSetBalance() {
    try {
      const player = selectedPlayerBySelect("adminBalancePlayer");
      const headers = await dangerConfirm(`Изменить donation-баланс игрока ${player.name || "без имени"}`, "DONATION_SET_BALANCE");
      if (!headers) return;
      await api("/api/admin/donation/set-balance", {
        method: "POST",
        headers,
        body: JSON.stringify({
          minecraft_uuid: player.uuid || "",
          minecraft_name: player.name || "",
          balance: Math.max(0, number($("adminDonationBalanceValue")?.value || 0)),
          reason: $("adminDonationReason")?.value?.trim() || "admin-balance-edit",
          idempotency_key: randomActionKey("don-admin-set"),
        }),
      });
      toast("Donation-баланс сохранён");
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function adminArSetBalance() {
    try {
      const player = selectedPlayerBySelect("adminBalancePlayer");
      const headers = await dangerConfirm(`Изменить AR-баланс игрока ${player.name || "без имени"}`, "AR_SET_BALANCE");
      if (!headers) return;
      await api("/api/admin/economy/ar/set-balance", {
        method: "POST",
        headers,
        body: JSON.stringify({
          minecraft_uuid: player.uuid || "",
          minecraft_name: player.name || "",
          balance: Math.max(0, number($("adminArBalanceValue")?.value || 0)),
          reason: $("adminArReason")?.value?.trim() || "admin-balance-edit",
          idempotency_key: randomActionKey("ar-admin-set"),
        }),
      });
      toast("AR-баланс сохранён");
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function adminDonationTestPurchase() {
    try {
      const player = selectedPlayerBySelect("donationTestPlayer");
      const itemId = $("donationTestItemId")?.value?.trim() || "";
      const headers = await dangerConfirm(`Создать тестовую покупку ${itemId || "предмета"} для ${player.name || "игрока"}`, "DONATION_TEST_PURCHASE");
      if (!headers) return;
      await api("/api/admin/donation/test-purchase", {
        method: "POST",
        headers,
        body: JSON.stringify({
          minecraft_uuid: player.uuid || "",
          minecraft_name: player.name || "",
          item_id: itemId,
        }),
      });
      toast("Тестовая покупка создана");
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function adminDonationMarkPaid(sessionId) {
    try {
      const headers = await dangerConfirm(`Отметить платёж ${sessionId} как оплаченный?`, "DONATION_MARK_PAID");
      if (!headers) return;
      await api(`/api/admin/donation/sbp/session/${encodeURIComponent(sessionId)}/mark-paid`, {
        method: "POST",
        headers,
        body: JSON.stringify({ note: "admin mark paid" }),
      });
      toast("Платёж отмечен как оплаченный");
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function adminDonationCancelSession(sessionId) {
    try {
      const headers = await dangerConfirm(`Отменить платёж ${sessionId}?`, "DONATION_CANCEL_SESSION");
      if (!headers) return;
      await api(`/api/admin/donation/sbp/session/${encodeURIComponent(sessionId)}/cancel`, {
        method: "POST",
        headers,
        body: JSON.stringify({ note: "admin cancel" }),
      });
      toast("Сессия отменена");
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function adminSetTreasuryPin() {
    try {
      const headers = await dangerConfirm("Сменить PIN казны?", "TREASURY_PIN_SET");
      if (!headers) return;
      const result = await api("/api/admin/economy/treasury/pin", {
        method: "POST",
        headers,
        body: JSON.stringify({
          old_pin: "",
          new_pin: $("treasuryNewPin")?.value || "",
          account_scope: "TREASURY",
        }),
      });
      toast(`PIN казны обновлён: ${result.pin}`);
      if ($("treasuryNewPin")) $("treasuryNewPin").value = "";
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  return {
    loadEconomy,
    createEconomySnapshot,
    scanAresWorld,
    adminArAddBalance,
    adminDonationAddBalance,
    adminArSetBalance,
    adminDonationSetBalance,
    adminDonationTestPurchase,
    adminDonationMarkPaid,
    adminDonationCancelSession,
    adminSetTreasuryPin,
  };
}
