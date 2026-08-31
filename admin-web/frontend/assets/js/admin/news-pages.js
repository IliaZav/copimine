export function createAdminNewsPages(deps) {
  const {
    $,
    state,
    api,
    safeApi,
    apiNotice,
    setLoading,
    setView,
    panel,
    metric,
    esc,
    cleanText,
    dt,
    asArray,
    dangerConfirm,
    toast,
  } = deps;

  function selectedRecord(records) {
    const selected = String(state.launcherNewsSelectedSlug || "").trim();
    return asArray(records).find((row) => String(row.slug || "") === selected) || asArray(records)[0] || {};
  }

  function itemsFor(record) {
    const selected = String(state.launcherNewsSelectedSlug || "").trim();
    if (selected === String(record.slug || "") && Array.isArray(state.launcherNewsItems)) return state.launcherNewsItems;
    state.launcherNewsItems = asArray(record.items).map((item) => ({ ...item, changes: asArray(item.changes) }));
    return state.launcherNewsItems;
  }

  function itemEditor(items) {
    if (!items.length) return `<div class="empty-state">Добавь предмет, если обновление касается конкретной вещи.</div>`;
    return items.map((item, index) => `<div class="launcher-news-item-card">
      <div class="form-grid">
        <div class="field-stack"><label for="launcherNewsItemId-${index}">Ключ предмета</label><input id="launcherNewsItemId-${index}" value="${esc(item.itemId || "")}" placeholder="copimine:token" /></div>
        <div class="field-stack"><label for="launcherNewsItemName-${index}">Название</label><input id="launcherNewsItemName-${index}" value="${esc(item.displayName || "")}" /></div>
        <div class="field-stack"><label for="launcherNewsItemIcon-${index}">Текстура</label><input id="launcherNewsItemIcon-${index}" value="${esc(item.iconUrl || "")}" placeholder="/assets/patch-items/token.png" /></div>
        <div class="field-stack full"><label for="launcherNewsItemChanges-${index}">Изменения, по одному в строке</label><textarea id="launcherNewsItemChanges-${index}" rows="3">${esc(asArray(item.changes).join("\n"))}</textarea></div>
      </div>
      <button class="btn btn-danger btn-small" data-click="adminNewsRemoveItem(${index})">Удалить предмет</button>
    </div>`).join("");
  }

  function renderNews(records, responses = []) {
    const list = asArray(records);
    const active = selectedRecord(list);
    const selectedSlug = String(active.slug || "");
    if (selectedSlug && state.launcherNewsSelectedSlug !== selectedSlug) state.launcherNewsSelectedSlug = selectedSlug;
    const items = itemsFor(active);
    const draftCount = list.filter((row) => !row.publishedAt).length;
    const publishedCount = list.length - draftCount;

    setView(`
      ${apiNotice("Новости", responses)}
      <section class="layout-grid launcher-admin-grid launcher-admin-metrics">
        ${metric("Записи", list.length, "Черновики и публикации", list.length ? "good" : "warn")}
        ${metric("Опубликованы", publishedCount, "Видны на странице новостей", publishedCount ? "good" : "neutral")}
        ${metric("Черновики", draftCount, "Можно править без публикации", draftCount ? "warn" : "good")}
        ${metric("Предметы в изменениях", list.filter((row) => asArray(row.items).length).length, "Картинки предметов", "neutral")}
      </section>
      <section id="launcher-news-editor" class="layout-grid grid-2 launcher-admin-grid">
        ${panel("Список обновлений", "Выбери запись, отредактируй её здесь и опубликуй отдельно.", `
          <div class="launcher-news-list">
            ${(list.map((row) => `<button class="launcher-news-list-row${row.slug === selectedSlug ? " is-active" : ""}" data-click="adminNewsEdit('${esc(row.slug)}')"><span><strong>${esc(cleanText(row.title || row.slug))}</strong><small>${esc(row.version || "без версии")}</small></span><span>${row.publishedAt ? "Опубликовано" : "Черновик"}</span></button>`).join("")) || `<div class="empty-state">Новостей пока нет.</div>`}
          </div>
          <div class="action-strip"><button class="btn btn-secondary" data-click="adminNewsNew()">Новая запись</button></div>
        `)}
        ${panel("Публикация", "Страница обновляется только после явного подтверждения.", `
          <div class="launcher-news-status"><span>Текущая запись</span><strong>${esc(selectedSlug || "новая")}</strong><small>${esc(active.publishedAt ? `Опубликовано ${dt(active.publishedAt)}` : "Черновик")}</small></div>
          <div class="action-strip wrap">
            <button class="btn btn-primary" data-click="adminNewsSave()">Сохранить черновик</button>
            ${selectedSlug ? `<button class="btn btn-secondary" data-click="adminNewsPublish('${esc(selectedSlug)}')">Опубликовать</button><button class="btn btn-danger" data-click="adminNewsDelete('${esc(selectedSlug)}')">Удалить черновик</button>` : ""}
          </div>
        `)}
      </section>
      ${panel("Редактор новости", "Пиши обычным текстом: разметка и небезопасные ссылки не принимаются.", `
        <div class="form-grid">
          <div class="field-stack"><label for="launcherNewsSlug">Короткое имя</label><input id="launcherNewsSlug" value="${esc(active.slug || "")}" placeholder="copimine-launcher-1-0-1" /></div>
          <div class="field-stack"><label for="launcherNewsVersion">Версия</label><input id="launcherNewsVersion" value="${esc(active.version || "")}" placeholder="1.0.1" /></div>
          <div class="field-stack full"><label for="launcherNewsTitle">Заголовок</label><input id="launcherNewsTitle" value="${esc(active.title || "")}" placeholder="Что изменилось" /></div>
          <div class="field-stack full"><label for="launcherNewsSummary">Коротко, до 3 пунктов</label><textarea id="launcherNewsSummary" rows="3" placeholder="Один пункт на строку">${esc(asArray(active.summary).join("\n"))}</textarea></div>
          <div class="field-stack"><label for="launcherNewsGeneral">Общее</label><textarea id="launcherNewsGeneral" rows="5">${esc(asArray(active.sections?.general).join("\n"))}</textarea></div>
          <div class="field-stack"><label for="launcherNewsTechnical">Техническое</label><textarea id="launcherNewsTechnical" rows="5">${esc(asArray(active.sections?.technical).join("\n"))}</textarea></div>
          <div class="field-stack full"><label for="launcherNewsBugfixes">Исправления</label><textarea id="launcherNewsBugfixes" rows="5">${esc(asArray(active.sections?.bugfixes).join("\n"))}</textarea></div>
        </div>
        <div class="launcher-news-items-head"><div><h3>Изменения предметов</h3><p class="muted">К каждой вещи можно добавить картинку из папки с предметами.</p></div><button class="btn btn-secondary btn-small" data-click="adminNewsAddItem()">Добавить предмет</button></div>
        <div class="launcher-news-items">${itemEditor(items)}</div>
      `)}
    `);
  }

  async function loadNews() {
    setLoading("Загружаю обновления лаунчера");
    const payload = await safeApi("/api/admin/launcher/news", { news: [] });
    renderNews(asArray(payload.news), [payload]);
  }

  function adminNewsEdit(slug) {
    state.launcherNewsSelectedSlug = String(slug || "").trim();
    state.launcherNewsItems = null;
    void loadNews();
  }

  function adminNewsNew() {
    state.launcherNewsSelectedSlug = "";
    state.launcherNewsItems = [];
    renderNews([{ slug: "", title: "", version: "", summary: [], sections: {}, items: [] }]);
  }

  function adminNewsAddItem() {
    if (!Array.isArray(state.launcherNewsItems)) state.launcherNewsItems = [];
    state.launcherNewsItems.push({ itemId: "", displayName: "", iconUrl: "", changes: [] });
    void loadNews();
  }

  function adminNewsRemoveItem(index) {
    if (!Array.isArray(state.launcherNewsItems)) return;
    state.launcherNewsItems.splice(Number(index), 1);
    void loadNews();
  }

  function lines(id) {
    return String($(id)?.value || "").split(/\r?\n/).map((value) => value.trim()).filter(Boolean);
  }

  function readNewsForm() {
    const items = asArray(state.launcherNewsItems).map((_, index) => ({
      itemId: $(`launcherNewsItemId-${index}`)?.value?.trim() || "",
      displayName: $(`launcherNewsItemName-${index}`)?.value?.trim() || "",
      iconUrl: $(`launcherNewsItemIcon-${index}`)?.value?.trim() || "",
      changes: lines(`launcherNewsItemChanges-${index}`),
    })).filter((item) => item.itemId);
    return {
      slug: $("launcherNewsSlug")?.value?.trim() || "",
      title: $("launcherNewsTitle")?.value?.trim() || "",
      version: $("launcherNewsVersion")?.value?.trim() || "",
      summary: lines("launcherNewsSummary").slice(0, 3),
      sections: { general: lines("launcherNewsGeneral"), technical: lines("launcherNewsTechnical"), bugfixes: lines("launcherNewsBugfixes") },
      items,
    };
  }

  async function adminNewsSave() {
    try {
      const body = readNewsForm();
      if (!body.slug || !body.title) throw new Error("Укажи slug и заголовок новости");
      const headers = await dangerConfirm(`Сохранить черновик ${body.slug}?`, "LAUNCHER_NEWS_SAVE");
      if (!headers) return;
      await api(`/api/admin/launcher/news/${encodeURIComponent(body.slug)}`, { method: "PUT", headers, body: JSON.stringify(body) });
      state.launcherNewsSelectedSlug = body.slug;
      toast("Черновик новости сохранён");
      await loadNews();
    } catch (error) {
      toast(error.message, true);
    }
  }

  async function adminNewsPublish(slug) {
    try {
      const headers = await dangerConfirm(`Опубликовать обновление ${slug}?`, "LAUNCHER_NEWS_PUBLISH");
      if (!headers) return;
      await api(`/api/admin/launcher/news/${encodeURIComponent(slug)}/publish`, { method: "POST", headers });
      toast("Новости опубликованы");
      await loadNews();
    } catch (error) {
      toast(error.message, true);
    }
  }

  async function adminNewsDelete(slug) {
    try {
      const headers = await dangerConfirm(`Удалить черновик ${slug}?`, "LAUNCHER_NEWS_DELETE");
      if (!headers) return;
      await api(`/api/admin/launcher/news/${encodeURIComponent(slug)}`, { method: "DELETE", headers });
      state.launcherNewsSelectedSlug = "";
      toast("Черновик удалён");
      await loadNews();
    } catch (error) {
      toast(error.message, true);
    }
  }

  return { loadNews, adminNewsEdit, adminNewsNew, adminNewsAddItem, adminNewsRemoveItem, adminNewsSave, adminNewsPublish, adminNewsDelete };
}
