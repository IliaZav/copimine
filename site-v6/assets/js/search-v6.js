/* CopiMine v6 · local admin search. No search queries are collected. */
(function () {
  "use strict";
  const M = window.CM, V = window.CMV4, W = window.CM5, X = window.CM6;
  if (!M || !V || !W || !X) return;
  const H = M.handlers, E = M.esc;
  const groups = [["all", "Всё"], ["functions", "Функции"], ["players", "Игроки"], ["items", "Предметы"], ["orders", "Заказы"]];
  const labels = Object.fromEntries(groups);
  let query = "", group = "all", matches = [], active = 0, inputTimer, dirty = false;
  const normalize = value => String(value || "").normalize("NFKC").toLowerCase().replace(/ё/g, "е").replace(/[^\p{L}\p{N}:_ -]+/gu, " ").replace(/\s+/g, " ").trim();
  const latin = "qwertyuiop[]asdfghjkl;'zxcvbnm,.", russian = "йцукенгшщзхъфывапролджэячсмитьбю";
  function keyboard(value) {
    return [...String(value).toLowerCase()].map(char => {
      const l = latin.indexOf(char), r = russian.indexOf(char);
      return l >= 0 ? russian[l] : r >= 0 ? latin[r] : char;
    }).join("");
  }
  function distance(a, b) {
    if (Math.abs(a.length - b.length) > 2) return 3;
    const rows = [Array.from({ length: b.length + 1 }, (_, i) => i)];
    for (let i = 1; i <= a.length; i++) {
      const row = [i];
      for (let j = 1; j <= b.length; j++) {
        let n = Math.min(row[j - 1] + 1, rows[i - 1][j] + 1, rows[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
        if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) n = Math.min(n, rows[i - 2][j - 2] + 1);
        row[j] = n;
      }
      rows.push(row);
    }
    return rows[a.length][b.length];
  }
  function score(entry, term) {
    const title = normalize(entry.title), hay = normalize([entry.title, entry.detail, entry.aliases].join(" "));
    if (!term) return entry.defaultOrder == null ? 100 : entry.defaultOrder;
    if (title === term) return 0;
    if (title.startsWith(term)) return 4;
    if (hay.includes(term)) return 12;
    const words = term.split(" "), target = hay.split(/[ :_-]+/).filter(Boolean);
    let total = 18;
    for (const word of words) {
      if (target.some(t => t === word)) continue;
      if (target.some(t => t.startsWith(word))) { total += 3; continue; }
      if (word.length < 4) return Infinity;
      let nearest = 3;
      for (const t of target) {
        if (Math.abs(t.length - word.length) <= 2) nearest = Math.min(nearest, distance(word, t));
        if (!nearest) break;
      }
      if (nearest > (word.length > 6 ? 2 : 1)) return Infinity;
      total += nearest * 9;
    }
    return total;
  }
  function currentPlayer() {
    const params = new URLSearchParams(location.hash.split("?")[1] || "");
    return M.state.players.find(p => p.id === params.get("player")) || X.player?.() || M.state.players[0];
  }
  function entries() {
    const st = M.state, player = currentPlayer();
    const playerPath = sub => "#admin/players?sub=" + sub + (player ? "&player=" + encodeURIComponent(player.id) : "");
    const functions = [
      ["Карточка игрока", "Профиль, инвентарь и эндер-сундук", "инвентарь инвентари inventory profile chest сундук эндер вещи", playerPath("profile"), "user"],
      ["Управление игроком", "Действия, предметы и телепортация", "дать выдать give teleport tp тп хил лечение управление команды", playerPath("actions"), "sliders"],
      ["Наказания", "Баны, муты, предупреждения и сроки", "бан разбан снять pardon ban unban mute warn таймер амнистия", playerPath("sanctions"), "shield"],
      ["Баланс и покупки", "AR, CM, донат и персональные лимиты", "баланс деньги пополнение донат limit лимит finance платеж платежи", playerPath("finance"), "wallet"],
      ["Доступ игрока", "Права и подключённые устройства", "права permission доступ сессия sessions role роль", playerPath("access"), "lock"],
      ["Каталог магазина", "Предметы, цены и наличие", "магазин shop catalog каталог товар цена", "#admin/shop?sub=catalog", "bag"],
      ["Статистика сайта", "Посещения, события и экономика", "аналитика analytics статистика graph график трафик", "#admin/statistics", "chart"],
      ["Мир и карта", "Измерения и опубликованный снимок", "map world мир карта измерения незер nether энд end обновление", "#admin/world?sub=map", "map"],
      ["Журналы", "События сервера и действия команды", "logs журнал лог история ошибки error", "#admin/logs", "file"],
      ["Контент сайта", "Страницы, галерея и загрузки", "cms content контент тексты файлы скачивание галерея", "#admin/content", "file"],
      ["Команда и доступ", "Роли и права администрации", "команда администратор модератор roles permissions доступ", "#admin/access", "users"],
      ["Система", "Подключения и состояние сервисов", "system интеграции настройка сервер статус система", "#admin/system", "server"]
    ].map(([title, detail, aliases, href, icon], index) => ({ key: "f" + index, group: "functions", title, detail, aliases, href, icon, defaultOrder: index }));
    const destinations = [
      ["Все игроки", "Найти аккаунт и открыть карточку", "список players list ник аккаунт", "#admin/players?sub=list", "users"],
      ["Предметы Minecraft", "Библиотека названий, ID и спрайтов", "materials материалы vanilla minecraft библиотека справочник", "#admin/shop?sub=materials", "package"],
      ["Выдача заказов", "Очередь и состояние доставки предметов", "delivery выдача очередь доставка заказов", "#admin/shop?sub=delivery", "bag"],
      ["Все покупки", "История заказов игроков", "orders покупки заказы история магазин", "#admin/finance?sub=orders", "bag"],
      ["Донаты игроков", "Суммы и статусы платежей", "payments donate донаты платежи рубли поддержка", "#admin/finance?sub=payments", "wallet"],
      ["Журнал баланса", "AR, CM и основания изменений", "ledger транзакции операции баланс csv", "#admin/finance?sub=ledger", "wallet"],
      ["Закрытые рецепты", "Предметы закрытого каталога", "recipes рецепты закрытые приватные", "#admin/recipes", "package"],
      ["Страницы сайта", "Черновики, публикация и история", "cms pages страница черновик публикация редактор", "#admin/content?sub=pages", "file"],
      ["Главная и ссылки", "Заголовок, Discord, моды и лаунчер", "homepage settings сайт заголовок discord дискорд моды лаунчер скачивание загрузки ссылки", "#admin/content?sub=settings", "settings"],
      ["Вопросы на главной", "Вопросы и ответы для новых игроков", "faq вопросы ответы помощь", "#admin/content?sub=faq", "chat"],
      ["Галерея", "Подписи и порядок изображений", "gallery галерея фото изображения подписи", "#admin/content?sub=gallery", "image"],
      ["Права команды", "Роли и защита административных действий", "roles permissions роли права 2fa защита", "#admin/access?sub=roles", "lock"]
    ].map(([title, detail, aliases, href, icon], index) => ({ key: "d" + index, group: "functions", title, detail, aliases, href, icon }));
    const actions = (X.actions || []).filter(a => !a.requiresPhysicalAdmin).map(a => ({ key: "a" + a.id, group: "functions", kind: a.scope === "server" ? "server-action" : "action", id: a.id, title: a.label || a.name || a.id, detail: [a.group || "Действие", a.scope === "server" ? "Весь сервер" : player?.name].filter(Boolean).join(" · "), aliases: [a.id, a.description, a.group, ...(Array.isArray(a.aliases) ? a.aliases : [a.aliases])].filter(Boolean).join(" "), icon: a.icon || "settings" }));
    const players = (st.players || []).map(p => ({ key: "p" + p.id, group: "players", title: p.name, detail: [p.role || "Игрок", p.uuid || p.id].join(" · "), aliases: ["player игрок", p.name, p.uuid, p.id].join(" "), href: "#admin/players?player=" + encodeURIComponent(p.id) + "&sub=profile", icon: "user", image: p.skin?.head }));
    const catalog = (st.catalog || []).map(p => ({ key: "c" + p.id, group: "items", title: p.name, detail: p.id + " · " + M.money(p.price, p.currency), aliases: [p.material, p.category, p.description].join(" "), kind: "catalog", id: p.id, icon: "package", image: p.image }));
    const catalogMaterials = new Set((st.catalog || []).map(p => p.material));
    const registry = window.CM6_REGISTRY || window.CM5_REGISTRY;
    const materials = (registry?.materials || []).filter(p => !catalogMaterials.has(p.key || p.id)).map(p => ({ key: "m" + p.id, group: "items", title: st.materialRu?.[p.key || p.id] || st.materialRu?.[p.id] || p.ru || p.en || p.id, detail: p.key || p.id, aliases: [p.en, p.ru, p.key, p.id].join(" "), kind: "material", id: p.key || p.id, image: p.image, icon: "package" }));
    const orders = (st.orders || []).slice(0, 600).map(o => {
      const owner = (st.players || []).find(p => p.id === o.playerId || p.name === o.player);
      return { key: "o" + o.id, group: "orders", title: o.id, detail: [o.player, o.name, o.qty ? "× " + o.qty : ""].filter(Boolean).join(" · "), aliases: ["заказ order", o.checkout, o.item, o.status].join(" "), href: owner ? "#admin/players?player=" + encodeURIComponent(owner.id) + "&sub=finance" : "#admin/finance?sub=orders", icon: "bag" };
    });
    return [...functions, ...destinations, ...actions, ...players, ...catalog, ...materials, ...orders];
  }
  function isOpen() { return document.getElementById("modal")?.open === true && !!document.getElementById("v6-search-input"); }
  function choose(index) {
    if (!X.canAdmin() || !Number.isInteger(index) || !matches[index]) return;
    const row = matches[index];
    M.closeModal();
    if (row.kind === "server-action") {
      if (H["v6-server-action"]) return H["v6-server-action"]({ dataset: { id: row.id } });
      return M.navTo("admin/system");
    }
    if (row.kind === "action" && H["v6-player-action"]) {
      const player = currentPlayer();
      if (player) return H["v6-player-action"]({ dataset: { id: row.id, player: player.id } });
      return M.navTo("admin/players?sub=list");
    }
    if (row.kind === "catalog" && H["admin-product"]) return H["admin-product"]({ dataset: { id: row.id } });
    if (row.kind === "material") {
      const player = currentPlayer();
      return M.openModal(E(row.title), `<div class="v6-material-summary">${row.image ? `<img class="mc-item" width="80" height="80" src="${E(M.asset(row.image))}" alt="">` : ""}<div><span class="label">Предмет Minecraft</span><p class="mt12"><code>${E(row.id)}</code></p></div></div><p class="mt">Выбери игрока в разделе управления, чтобы указать количество и причину выдачи.</p><div class="actions mt">${M.link("Открыть управление", "#admin/players?sub=actions" + (player ? "&player=" + encodeURIComponent(player.id) : ""), "arrow-right", "primary")}</div>`);
    }
    if (row.href?.startsWith("#")) M.navTo(row.href.slice(1));
  }
  function mark(index, scroll = false) {
    active = Math.max(0, Math.min(index, matches.length - 1));
    document.querySelectorAll("#v6-search-results [role=option]").forEach((node, i) => {
      node.classList.toggle("selected", i === active);
      node.setAttribute("aria-selected", String(i === active));
      if (i === active && scroll) node.scrollIntoView({ block: "nearest" });
    });
    const input = document.getElementById("v6-search-input");
    if (input) {
      if (matches.length) input.setAttribute("aria-activedescendant", "v6-search-option-" + active);
      else input.removeAttribute("aria-activedescendant");
    }
  }
  function paint() {
    const root = document.getElementById("v6-search-results");
    if (!root) return;
    dirty = false;
    const terms = [...new Set([normalize(query), normalize(keyboard(query))])];
    let scope = group;
    const prefix = normalize(query).match(/^(player|игрок|item|предмет|order|заказ|function|функция):\s*(.*)$/);
    if (prefix) {
      scope = /^(player|игрок)$/.test(prefix[1]) ? "players" : /^(item|предмет)$/.test(prefix[1]) ? "items" : /^(order|заказ)$/.test(prefix[1]) ? "orders" : "functions";
      terms.splice(0, terms.length, normalize(prefix[2]), normalize(keyboard(prefix[2])));
    }
    matches = entries().filter(row => (scope === "all" || row.group === scope) && (query.trim() || row.defaultOrder != null)).map(row => ({ ...row, score: Math.min(...terms.map(term => score(row, term))) })).filter(row => Number.isFinite(row.score)).sort((a, b) => a.score - b.score || a.title.localeCompare(b.title, "ru")).slice(0, 36);
    root.innerHTML = matches.length ? matches.map((row, index) => `<a href="${E(row.href || "#")}" class="v6-search-result" role="option" aria-selected="${index === 0}" id="v6-search-option-${index}" tabindex="-1" data-action="v6-search-open" data-index="${index}"><span class="v6-search-symbol">${row.image ? `<img src="${E(M.asset(row.image))}" width="30" height="30" alt="" loading="lazy">` : window.icon(row.icon || "search")}</span><span class="v6-search-copy"><strong>${E(row.title)}</strong><small>${E(row.detail)}</small></span><span class="v6-search-kind">${E(labels[row.group])}</span></a>`).join("") : `<div class="v6-search-empty"><strong>Ничего не найдено</strong><p>Попробуй часть ника, название предмета или действие.</p></div>`;
    document.getElementById("v6-search-count").textContent = matches.length ? "Найдено: " + matches.length + (matches.length === 36 ? " · показаны первые" : "") : "Нет результатов";
    document.querySelectorAll("[data-action=v6-search-group]").forEach(button => {
      const selected = button.dataset.group === group;
      button.classList.toggle("selected", selected);
      button.setAttribute("aria-pressed", String(selected));
    });
    mark(0);
  }
  function open() {
    if (!X.canAdmin()) return;
    if (isOpen()) { document.getElementById("v6-search-input").focus(); return; }
    query = ""; group = "all"; active = 0;
    M.openModal("Поиск по панели", `<div class="v6-command-search"><div class="v6-search-input-wrap">${window.icon("search")}<input id="v6-search-input" type="search" aria-label="Поиск по функциям, игрокам, предметам и заказам" role="combobox" aria-autocomplete="list" aria-expanded="true" aria-controls="v6-search-results" autocomplete="off" spellcheck="false" placeholder="Игрок, предмет или действие…"><kbd>Esc</kbd></div><div class="v6-search-groups" aria-label="Группы результатов">${groups.map(([id, label]) => `<button type="button" data-action="v6-search-group" data-group="${id}" aria-pressed="${id === "all"}">${E(label)}</button>`).join("")}</div><div id="v6-search-results" class="v6-search-results" role="listbox" aria-label="Результаты поиска"></div><div class="v6-search-footer"><span id="v6-search-count" role="status" aria-live="polite"></span><span>↑ ↓ выбрать · Enter открыть</span></div></div>`, "wide-dialog v6-command-dialog");
    const input = document.getElementById("v6-search-input");
    input.addEventListener("input", () => { query = input.value.slice(0, 160); dirty = true; active = 0; clearTimeout(inputTimer); inputTimer = setTimeout(paint, 65); });
    input.addEventListener("keydown", event => {
      if (event.key === "ArrowDown" || event.key === "ArrowUp") { event.preventDefault(); mark(active + (event.key === "ArrowDown" ? 1 : -1), true); }
      else if (event.key === "Enter") { event.preventDefault(); clearTimeout(inputTimer); if (dirty) paint(); choose(active); }
      else if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); clearTimeout(inputTimer); M.closeModal(); }
    });
    paint(); input.focus();
  }
  H["admin-search"] = open;
  V.after?.push(page => {
    if (page !== "admin") return;
    const trigger = document.querySelector(".admin-top .command-search");
    if (!trigger) return;
    trigger.classList.add("v6-search-trigger");
    trigger.setAttribute("aria-label", "Поиск по функциям, игрокам, предметам и заказам");
    trigger.setAttribute("title", "Найти игрока, товар или действие · Ctrl/⌘+K");
    trigger.innerHTML = window.icon("search") + '<span class="v6-trigger-full">Найти игрока, товар, действие</span><span class="v6-trigger-short">Поиск</span><kbd>Ctrl K</kbd>';
  });
  H["v6-search-group"] = button => { if (!groups.some(([id]) => id === button.dataset.group)) return; group = button.dataset.group; paint(); document.getElementById("v6-search-input")?.focus(); };
  H["v6-search-open"] = button => choose(Number(button.dataset.index));
  document.addEventListener("keydown", event => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k" && X.canAdmin()) { event.preventDefault(); open(); }
  });
  X.search = { open, normalize, keyboard, distance, score };
})();

