export function createPlayerAccountPages(deps) {
  const {
    state,
    setLoading,
    setView,
    panel,
    kv,
    safetyRail,
    safeApi,
    dt,
    bankPinState,
  } = deps;

  async function loadPlayerSettings() {
    setLoading("Загрузка настроек");
    const me = await safeApi("/api/player/me", { account: state.user || {} });
    const account = me.account || state.user || {};
    setView(`
      <section class="layout-grid grid-2">
        ${panel("Профиль", "Основные данные игрового кабинета.", kv([
          ["Логин", account.username || "игрок"],
          ["Minecraft-ник", account.minecraftName || "не привязан"],
          ["Email", account.email || "не указан"],
          ["Создан", dt(account.createdAt)]
        ]), `<button class="btn btn-secondary" data-click="setTab('link')">Настроить Minecraft</button>`)}
        ${panel("Что можно настроить", "Основные настройки кабинета.", safetyRail([
          ["Привязка", "Свяжи кабинет с Minecraft-ником и открой банк.", account.minecraftName ? "good" : "warn"],
          ["Безопасность", "Проверь пароль и PIN банка.", "neutral"],
          ["История", "Здесь можно посмотреть переводы, покупки и операции по счёту.", "good"]
        ]))}
      </section>
    `);
  }

  async function loadPlayerSecurity() {
    setLoading("Загрузка безопасности");
    const bank = await safeApi("/api/player/bank", { pin: {}, ledger: [] });
    const pin = bank.pin || {};
    setView(`
      <section class="layout-grid grid-2">
        ${panel("PIN банка", "PIN используется для переводов и защищённых операций.", kv([
          ["PIN задан", pin.set || false],
          ["Статус", bankPinState(pin)],
          ["Заблокирован", pin.locked || false],
          ["Нужна замена", pin.mustChange || false]
        ]), `<button class="btn btn-primary" data-click="setTab('bank')">Открыть банк</button>`)}
        ${panel("Сессии", "Быстрые действия для защиты аккаунта.", `
          <div class="notice">Если заметил подозрительную активность, смени пароль и PIN, а затем выйди из кабинета на текущем устройстве.</div>
          <button class="btn btn-secondary full" data-click="logout(true)">Выйти из текущей сессии</button>
        `)}
      </section>
    `);
  }

  async function loadPlayerSupport() {
    setLoading("Загрузка поддержки");
    setView(`
      <section class="layout-grid grid-2">
        ${panel("Как обратиться за помощью", "Куда писать, если нужна помощь.", safetyRail([
          ["/report в игре", "Если проблема случилась прямо на сервере, отправь сообщение через игровую команду /report.", "good"],
          ["Команда сервера", "По банку, привязке ника и спорным операциям можно обратиться к администрации напрямую.", "neutral"],
          ["Не отправляй PIN", "Пароль и PIN нельзя пересылать никому, даже если сообщение выглядит официально.", "warn"]
        ]))}
        ${panel("Частые вопросы", "Коротко о главном.", safetyRail([
          ["Банк", "Сначала привяжи Minecraft-ник и задай PIN, после этого откроются операции по счёту.", "good"],
          ["Артефакты", "Игровая лавка открывается кликом по специальному блоку в мире.", "neutral"],
          ["Выдача", "Если инвентарь был полон, предмет останется в ожидающей выдаче и его можно будет забрать позже.", "warn"]
        ]))}
      </section>
    `);
  }

  return {
    loadPlayerSettings,
    loadPlayerSecurity,
    loadPlayerSupport,
  };
}
