export function createAdminCommercePages(deps) {
  const {
    $,
    state,
    api,
    safeApi,
    setLoading,
    setView,
    panel,
    metric,
    kv,
    resultBars,
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

  function paymentModeLabel(value) {
    return String(value || "").toUpperCase() === "MOCK_SBP" ? "Тестовый режим" : String(value || "—");
  }

  async function loadEconomy() {
    setLoading("Загружаю экономику");
    const [data, history, ledger, donation, donationCatalog, treasury] = await Promise.all([
      safeApi("/api/economy/ares/overview", {}),
      safeApi("/api/economy/ares/history?limit=40", { snapshots: [], changes: [] }),
      safeApi("/api/economy/ares/ledger?limit=500", { events: [], balances: [], transactions: [], assets: [], scans: [], snapshots: [], summary: {} }),
      safeApi("/api/admin/donation/overview?limit=120", { summary: {}, balances: [], ledger: [], claims: [], sessions: [] }),
      safeApi("/api/admin/shop/donation-items", { items: [], catalogVersion: 0, updatedAt: 0 }),
      safeApi("/api/admin/economy/treasury", { account: {}, pin: {}, ledger: [], ownerName: "" })
    ]);
    const players = asArray(ledger.balances).length ? asArray(ledger.balances).map((x) => ({
      player: x.name,
      amount: x.balance,
      inventory: x.inventory_balance,
      enderChest: x.ender_balance,
      uuid: x.uuid,
      updatedAt: x.updated_at
    })) : asArray(data.players);
    const containers = asArray(data.worldContainers?.rows);
    const econSummary = ledger.summary || {};
    const donationSummary = donation.summary || {};
    setView(`
      <section class="layout-grid grid-4">
        ${metric("AR", econSummary.totalBalance ?? data.totalKnownInPlayerData ?? 0, "Баланс в экономике игроков", "good")}
        ${metric("Счета AR", econSummary.holders ?? players.length, "Игроки с известным AR-балансом")}
        ${metric("Операции AR", econSummary.transactions ?? asArray(ledger.transactions).length, `${econSummary.transfers ?? 0} переводов, ${econSummary.smelts ?? 0} переплавок`)}
        ${metric("AR-активы", econSummary.activeAssets ?? asArray(ledger.assets).length, `${econSummary.events ?? 0} событий и ${econSummary.scans ?? 0} сканов`)}
      </section>
      <section class="layout-grid grid-wide">
        ${panel("Распределение AR", "Где сейчас сосредоточен игровой баланс", resultBars(players, ["player"], ["amount"]))}
        ${panel("Операции", "Снимки и аудит экономики без прямого доступа к БД", `
          <div class="action-strip">
            <button class="btn btn-primary" data-click="createEconomySnapshot()">Создать снимок</button>
            <button class="btn btn-secondary" data-click="scanAresWorld()">Скан предметов AR</button>
          </div>
          <div class="spacer-12"></div>
          ${kv([
            ["ID AR-предметов", asArray(data.itemIds).join(", ") || "не настроены"],
            ["История снимков", history.count ? `${history.count} записей` : "пока пусто"],
            ["Источник журнала", ledger.source || "основной backend"],
            ["AR в инвентарях", econSummary.inventoryBalance ?? "0"],
            ["AR в эндер-сундуках", econSummary.enderBalance ?? "0"],
            ["Переводы", econSummary.transfers ?? "0"],
            ["Переплавки", econSummary.smelts ?? "0"],
            ["Последний снимок", dt(data.lastSnapshotAt || data.createdAt)]
          ])}
        `)}
      </section>
      <section class="layout-grid grid-2">
        ${panel("Игроки с AR", "Балансы, инвентари и эндер-сундуки", table("economy-players", players, [
          { key: "player", label: "Игрок" },
          { key: "amount", label: "Баланс" },
          { key: "inventory", label: "Инвентарь" },
          { key: "enderChest", label: "Эндер" }
        ], { pageSize: 15 }))}
        ${panel("Контейнеры мира", "Подозрительные или крупные хранилища", table("economy-containers", containers, null, { pageSize: 15 }))}
      </section>
      <section class="layout-grid grid-2">
        ${panel("Транзакции AR", "Переводы, переплавки и другие движения", `<div class="economy-transactions">${table("economy-transactions-table", asArray(ledger.transactions), [
          { key: "time", label: "Время", render: value => dt(value) },
          { key: "type", label: "Тип" },
          { key: "from_name", label: "От" },
          { key: "to_name", label: "Кому" },
          { key: "amount", label: "Сумма" },
          { key: "material", label: "Материал" },
          { key: "details", label: "Детали", render: value => short(value || "", 90) }
        ], { pageSize: 12 })}</div>`)}
        ${panel("Активы AR", "Официальные предметы и их текущее состояние", `<div class="economy-assets">${table("economy-assets-table", asArray(ledger.assets), [
          { key: "updated_at", label: "Обновлён", render: value => dt(value) },
          { key: "owner_name", label: "Владелец" },
          { key: "status", label: "Статус" },
          { key: "material", label: "Материал" },
          { key: "source", label: "Источник" },
          { key: "asset_id", label: "Asset", render: value => short(value || "", 12) }
        ], { pageSize: 12 })}</div>`)}
      </section>
      <section class="layout-grid grid-2">
        ${panel("Журнал AR", "Все ключевые события экономики в одном потоке", `<div class="economy-ledger">${ledgerRows(asArray(ledger.events), "economy-ledger")}</div>`)}
        ${panel("История снимков", "Снимки состояния AR для аудита и расследований", table("economy-snapshots", asArray(ledger.snapshots), null, { pageSize: 12 }))}
      </section>
      <section class="layout-grid grid-4">
        ${metric("Счета Donation", donationSummary.accounts ?? 0, "Отдельно от AR-экономики", "good")}
        ${metric("Баланс Donation", formatDonate(donationSummary.totalBalance ?? 0), "Сумма по всем donation accounts", donationSummary.totalBalance ? "good" : "neutral")}
        ${metric("Выдачи в работе", donationSummary.unclaimedItems ?? 0, "Покупки, которые ещё не дошли до финальной выдачи", Number(donationSummary.unclaimedItems || 0) ? "warn" : "good")}
        ${metric("Открытые сессии", donationSummary.openSessions ?? 0, "Тестовый платёжный контур без реального провайдера", Number(donationSummary.openSessions || 0) ? "warn" : "neutral")}
      </section>
      <section class="layout-grid grid-2">
        ${panel("Донат-счёта", "Баланс игроков, который не смешивается с AR.", table("donation-balances", asArray(donation.balances), [
          { key: "player_name", label: "Игрок", render: (value, row) => esc(value || row.player_uuid || "—") },
          { key: "balance", label: "DC", render: value => formatDonate(value || 0) },
          { key: "updated_at", label: "Обновлён", render: value => dt(value) }
        ], { pageSize: 12 }))}
        ${panel("Журнал доната", "Пополнения и списания только по donation balance.", table("donation-ledger", asArray(donation.ledger), [
          { key: "created_at", label: "Время", render: value => dt(value) },
          { key: "player_uuid", label: "Игрок" },
          { key: "delta", label: "Изменение", render: value => formatDonate(value || 0) },
          { key: "balance_after", label: "После", render: value => formatDonate(value || 0) },
          { key: "reason", label: "Причина", render: value => short(value || "", 90) }
        ], { pageSize: 12 }))}
      </section>
      <section class="layout-grid grid-2">
        ${panel("Выдачи предметов", "Что уже оплачено и ждёт выдачи игроку.", table("donation-claims", asArray(donation.claims), [
          { key: "created_at", label: "Создан", render: value => dt(value) },
          { key: "player_uuid", label: "Игрок" },
          { key: "display_name", label: "Предмет", render: (value, row) => `<strong>${esc(cleanText(value || row.item_id || "Предмет"))}</strong><br><span class="muted">${esc(row.item_id || "—")}</span>` },
          { key: "amount", label: "Кол-во" },
          { key: "status", label: "Статус", render: value => pill(statusLabel(value || "pending"), artifactStatusTone(value)) }
        ], { pageSize: 12 }))}
        ${panel("Платёжные сессии", "Тестовые сессии пополнения donation без связи с AR.", table("donation-sessions", asArray(donation.sessions), [
          { key: "created_at", label: "Создана", render: value => dt(value) },
          { key: "player_name", label: "Игрок", render: (value, row) => esc(value || row.player_uuid || "—") },
          { key: "provider", label: "Режим", render: value => paymentModeLabel(value) },
          { key: "amount", label: "Сумма", render: value => formatDonate(value || 0) },
          { key: "status", label: "Статус", render: value => pill(statusLabel(value || "pending"), artifactStatusTone(value)) },
          { key: "id", label: "Управление", render: (value, row) => {
            const status = String(row.status || "").toUpperCase();
            if (status === "PAID") return `<span class="btn btn-secondary btn-small disabled">Оплачено</span>`;
            if (status === "CANCELLED") return `<span class="btn btn-secondary btn-small disabled">Отменена</span>`;
            if (status === "EXPIRED") return `<span class="btn btn-secondary btn-small disabled">Истекла</span>`;
            return `<div class="action-strip"><button class="btn btn-primary btn-small" data-click="adminDonationMarkPaid('${row.id}')">Подтвердить</button><button class="btn btn-secondary btn-small" data-click="adminDonationCancelSession('${row.id}')">Отменить</button></div>`;
          } }
        ], { pageSize: 12 }))}
      </section>
      <section class="layout-grid grid-2">
        ${panel("Донат-баланс", "Ручное пополнение для тестов. Donation остаётся полностью отдельным от AR.", `
          <div class="form-grid">
            <input id="donationAdminUuid" placeholder="Minecraft UUID" />
            <input id="donationAdminName" placeholder="Minecraft-ник" />
            <input id="donationAdminAmount" type="number" min="1" step="1" placeholder="Сумма Donation" />
            <input id="donationAdminReason" class="full" placeholder="Причина, например qa-topup" />
            <button class="btn btn-primary full" data-click="adminDonationAddBalance()">Пополнить вручную</button>
          </div>
        `)}
        ${panel("Казна", "Отдельный казначейский счёт. Его не видят обычные игроки; доступ только у президента и админов.", `
          ${kv([
            ["Счёт", treasury.account?.account_id || treasury.account?.accountId || "—"],
            ["Владелец", treasury.ownerName || "—"],
            ["Баланс", formatAr(treasury.account?.balance || 0)],
            ["PIN казны", treasury.pin?.visiblePin || "не задан"]
          ])}
          <div class="spacer-12"></div>
          <div class="form-grid">
            <input id="treasuryNewPin" type="password" inputmode="numeric" placeholder="Новый PIN казны, 4-8 цифр" />
            <button class="btn btn-secondary full" data-click="adminSetTreasuryPin()">Сменить PIN казны</button>
          </div>
        `)}
        ${panel("Платёжный режим", "Здесь показывается только текущий режим оплаты и подготовленный слот под будущего провайдера, без секретов и токенов.", kv([
          ["Текущий режим", "Тестовый платёжный режим"],
          ["Реальный webhook", "не подключён"],
          ["Курс", "1 ₽ = 1 Donation"],
          ["Пакеты", "50 / 100 / 250 / 500 / 1000"],
          ["Каталог", `${asArray(donationCatalog.items).length} предметов, версия ${donationCatalog.catalogVersion || 0}`]
        ]))}
      </section>
      <section class="layout-grid grid-2">
        ${panel("Donation-лавка", "Каталог donation-предметов и цены.", table("admin-donation-catalog", asArray(donationCatalog.items), [
          { key: "item_id", label: "ID" },
          { key: "display_name", label: "Название" },
          { key: "price_donation", label: "Цена", render: value => formatDonate(value || 0) },
          { key: "effect_profile_id", label: "Профиль" },
          { key: "enabled", label: "Статус", render: value => value ? pill("вкл", "good") : pill("off", "bad") }
        ], { pageSize: 10 }))}
        ${panel("Выдачи и тестовая покупка", "Тестовая покупка создаёт выдачу, а забрать предмет всё равно нужно в игре.", `
          <div class="form-grid">
            <input id="donationTestUuid" placeholder="Minecraft UUID" />
            <input id="donationTestName" placeholder="Minecraft-ник" />
            <input id="donationTestItemId" placeholder="item_id из каталога" />
            <button class="btn btn-secondary full" data-click="adminDonationTestPurchase()">Создать тестовую покупку</button>
          </div>
          <div class="spacer-12"></div>
          <div class="notice">Повторное подтверждение одной и той же сессии не начислит баланс второй раз.</div>
        `)}
      </section>
      ${panel("Скан мира", "AR в контейнерах и подозрительных местах", table("economy-scans", asArray(ledger.scans), null, { pageSize: 12 }))}
    `);
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
      const headers = await dangerConfirm(`Пополнить donation-баланс игрока ${$("donationAdminName")?.value?.trim() || $("donationAdminUuid")?.value?.trim()}`, "DONATION_ADD_BALANCE");
      if (!headers) return;
      await api("/api/admin/donation/add-balance", {
        method: "POST",
        headers,
        body: JSON.stringify({
          minecraft_uuid: $("donationAdminUuid")?.value?.trim() || "",
          minecraft_name: $("donationAdminName")?.value?.trim() || "",
          amount: number($("donationAdminAmount")?.value || 0),
          reason: $("donationAdminReason")?.value?.trim() || "admin-topup",
          idempotency_key: randomActionKey("don-admin-topup")
        })
      });
      toast("Donation-баланс пополнен");
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function adminDonationTestPurchase() {
    try {
      const headers = await dangerConfirm(`Создать тестовую покупку ${$("donationTestItemId")?.value?.trim() || "item"} для ${$("donationTestName")?.value?.trim() || $("donationTestUuid")?.value?.trim()}`, "DONATION_TEST_PURCHASE");
      if (!headers) return;
      await api("/api/admin/donation/test-purchase", {
        method: "POST",
        headers,
        body: JSON.stringify({
          minecraft_uuid: $("donationTestUuid")?.value?.trim() || "",
          minecraft_name: $("donationTestName")?.value?.trim() || "",
          item_id: $("donationTestItemId")?.value?.trim() || ""
        })
      });
      toast("Тестовая покупка создана");
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function adminDonationMarkPaid(sessionId) {
    try {
      const headers = await dangerConfirm(`Отметить session ${sessionId} как PAID?`, "DONATION_MARK_PAID");
      if (!headers) return;
      await api(`/api/admin/donation/sbp/session/${encodeURIComponent(sessionId)}/mark-paid`, {
        method: "POST",
        headers,
        body: JSON.stringify({ note: "admin mark paid" })
      });
      toast("Сессия отмечена как PAID");
      await loadEconomy();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function adminDonationCancelSession(sessionId) {
    try {
      const headers = await dangerConfirm(`Отменить session ${sessionId}?`, "DONATION_CANCEL_SESSION");
      if (!headers) return;
      await api(`/api/admin/donation/sbp/session/${encodeURIComponent(sessionId)}/cancel`, {
        method: "POST",
        headers,
        body: JSON.stringify({ note: "admin cancel" })
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
          account_scope: "TREASURY"
        })
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
    adminDonationAddBalance,
    adminDonationTestPurchase,
    adminDonationMarkPaid,
    adminDonationCancelSession,
    adminSetTreasuryPin,
  };
}
