export function createPlayerAccountPages(deps) {
  const {
    state,
    setLoading,
    setView,
    panel,
    kv,
    safetyRail,
    safeApi,
    api,
    dt,
    bankPinState,
  } = deps;

  async function loadPlayerSettings() {
    setLoading("Загрузка настроек");
    const me = await safeApi("/api/player/me", { account: state.user || {} });
    const account = me.account || state.user || {};
    setView(`
      <section class="layout-grid grid-2">
        ${panel("Профиль", "Основные данные аккаунта игрока.", kv([
          ["Логин", account.username || "игрок"],
          ["Minecraft-ник", account.minecraftName || "не привязан"],
          ["Email", account.email || "не указан"],
          ["Создан", dt(account.createdAt)]
        ]), `<button class="btn btn-secondary" data-click="setTab('link')">Настроить Minecraft</button>`)}
        ${panel("Что можно настроить", "В кабинете доступны только реальные действия без декоративных переключателей.", safetyRail([
          ["Привязка", "Свяжи кабинет с Minecraft-ником и открой банк.", account.minecraftName ? "good" : "warn"],
          ["Безопасность", "Смени пароль и проверь PIN банка.", "neutral"],
          ["История", "Здесь же можно посмотреть переводы, покупки и налог.", "good"]
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
        ${panel("PIN банка", "PIN используется для переводов и защищённых операций", kv([
          ["PIN задан", pin.set || false],
          ["Статус", bankPinState(pin)],
          ["Заблокирован", pin.locked || false],
          ["Нужна замена", pin.mustChange || false]
        ]), `<button class="btn btn-primary" data-click="setTab('bank')">Открыть банк</button>`)}
        ${panel("Сессии", "Базовые действия безопасности", `
          <div class="notice">Если заметил подозрительную активность, смени пароль и PIN, затем выйди из аккаунта на всех устройствах.</div>
          <button class="btn btn-secondary full" data-click="logout(true)">Выйти из текущей сессии</button>
        `)}
      </section>
    `);
  }

  async function loadPlayerSupport() {
    setLoading("Загрузка поддержки");
    setView(`
      <section class="layout-grid grid-2">
        ${panel("Как обратиться за помощью", "В этом патче не показываем неработающие формы. Доступны только реальные способы связи.", safetyRail([
          ["/report в игре", "Если проблема произошла на сервере, отправь сообщение через игровую команду /report.", "good"],
          ["Команда сервера", "Для банка, привязки и налогов можно обратиться к администрации напрямую.", "neutral"],
          ["Не присылай PIN", "Никогда не отправляй пароль или PIN в сообщениях.", "warn"]
        ]))}
        ${panel("Частые вопросы", "Короткие ответы по самым частым ситуациям.", safetyRail([
          ["Банк", "Сначала привяжи Minecraft-ник и задай PIN.", "good"],
          ["Артефакты", "Лавка открывается кликом по игровому блоку.", "neutral"],
          ["Выдача", "Если инвентарь был полон, предмет ждёт в отложенной выдаче.", "warn"]
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
