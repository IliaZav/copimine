export function createAdminCmsPages(deps) {
  const {
    $,
    state,
    api,
    safeApi,
    setLoading,
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
    dangerConfirm,
    toast,
  } = deps;

  const sectionLabels = {
    home: "Главная",
    news: "Новости",
    faq: "FAQ",
    rules: "Правила",
    shops: "Лавки",
    banners: "Баннеры",
  };

  function sectionLabel(section) {
    return sectionLabels[String(section || "")] || cleanText(section || "Раздел");
  }

  function selectedEntry(items) {
    const key = String(state.cmsSelectedKey || "").trim();
    return asArray(items).find((row) => String(row.key || row.entry_key || "") === key) || asArray(items)[0] || {};
  }

  function sectionOptions(sections, selected) {
    return asArray(sections).map((section) => {
      const value = String(section || "").trim();
      if (!value) return "";
      return `<option value="${esc(value)}"${value === selected ? " selected" : ""}>${esc(sectionLabel(value))}</option>`;
    }).join("");
  }

  function entryOptions(items, selectedKey) {
    return asArray(items).map((row) => {
      const key = String(row.key || row.entry_key || "").trim();
      if (!key) return "";
      const label = `${sectionLabel(row.section)} · ${cleanText(row.title || key)}`;
      return `<option value="${esc(key)}"${key === selectedKey ? " selected" : ""}>${esc(label)}</option>`;
    }).join("");
  }

  async function loadCms() {
    setLoading("Загружаю CMS");
    const payload = await safeApi("/api/admin/cms", { items: [], sections: ["home", "news", "faq", "rules", "shops", "banners"] });
    const items = asArray(payload.items);
    const sections = asArray(payload.sections).length ? asArray(payload.sections) : ["home", "news", "faq", "rules", "shops", "banners"];
    const active = selectedEntry(items);
    const activeKey = String(active.key || active.entry_key || "");
    if (activeKey && !state.cmsSelectedKey) state.cmsSelectedKey = activeKey;
    const selectedKey = String(state.cmsSelectedKey || activeKey || "");
    const formEntry = items.find((row) => String(row.key || row.entry_key || "") === selectedKey) || active;
    const enabledItems = items.filter((row) => row.enabled !== false);
    const disabledItems = items.filter((row) => row.enabled === false);

    setView(`
      <section class="layout-grid grid-4 cms-summary-row">
        ${metric("Записи", items.length, "Новости, правила, FAQ и баннеры", items.length ? "good" : "warn")}
        ${metric("Активные", enabledItems.length, "Показываются на сайте", enabledItems.length ? "good" : "neutral")}
        ${metric("Скрытые", disabledItems.length, "Отключены без удаления истории", disabledItems.length ? "warn" : "good")}
        ${metric("Источник", payload.source || "postgresql", "CMS без правки исходников", "neutral")}
      </section>

      <section class="layout-grid grid-2 cms-workbench">
        ${panel("Редактор CMS", "Текст, баннеры и ссылки сайта без изменения кода.", `
          <div class="field-stack">
            <label for="cmsEntrySelect">Выбрать запись</label>
            <select id="cmsEntrySelect" data-input="adminCmsSelect">${entryOptions(items, selectedKey)}</select>
          </div>
          <div class="form-grid">
            <div class="field-stack">
              <label for="cmsEntryKey">Ключ</label>
              <input id="cmsEntryKey" value="${esc(formEntry.key || formEntry.entry_key || "")}" placeholder="home_intro" />
            </div>
            <div class="field-stack">
              <label for="cmsSection">Раздел</label>
              <select id="cmsSection">${sectionOptions(sections, String(formEntry.section || "home"))}</select>
            </div>
            <div class="field-stack full">
              <label for="cmsTitle">Заголовок</label>
              <input id="cmsTitle" value="${esc(formEntry.title || "")}" placeholder="Короткий заголовок" />
            </div>
            <div class="field-stack full">
              <label for="cmsBody">Текст</label>
              <textarea id="cmsBody" rows="7" placeholder="Текст без HTML">${esc(formEntry.body || "")}</textarea>
            </div>
            <div class="field-stack">
              <label for="cmsImagePath">Локальная картинка</label>
              <input id="cmsImagePath" value="${esc(formEntry.imagePath || formEntry.image_path || "")}" placeholder="/assets/showcase/home-light-v2.png" />
            </div>
            <div class="field-stack">
              <label for="cmsLinkUrl">Ссылка</label>
              <input id="cmsLinkUrl" value="${esc(formEntry.linkUrl || formEntry.link_url || "")}" placeholder="/shops.html" />
            </div>
            <div class="field-stack">
              <label for="cmsSortOrder">Порядок</label>
              <input id="cmsSortOrder" type="number" min="0" step="1" value="${esc(formEntry.sortOrder ?? formEntry.sort_order ?? 100)}" />
            </div>
            <label class="toggle-row" for="cmsEnabled">
              <input id="cmsEnabled" type="checkbox"${formEntry.enabled === false ? "" : " checked"} />
              <span>Показывать на сайте</span>
            </label>
          </div>
          <div class="action-strip wrap">
            <button class="btn btn-primary" data-click="adminCmsSave()">Сохранить</button>
            <button class="btn btn-secondary" data-click="adminCmsNew()">Новая запись</button>
            ${selectedKey ? `<button class="btn btn-danger" data-click="adminCmsDisable('${esc(selectedKey)}')">Скрыть</button>` : ""}
          </div>
        `)}

        ${panel("Предпросмотр", "Так запись будет читаться в публичном API.", `
          <article class="cms-preview-card ${formEntry.enabled === false ? "is-disabled" : ""}">
            ${formEntry.imagePath ? `<img src="${esc(formEntry.imagePath)}" alt="" loading="lazy" />` : ""}
            <span>${esc(sectionLabel(formEntry.section || "home"))}</span>
            <strong>${esc(formEntry.title || "Новая запись")}</strong>
            <p>${esc(short(formEntry.body || "Текст пока не задан.", 420))}</p>
            ${formEntry.linkUrl ? `<small>${esc(formEntry.linkUrl)}</small>` : ""}
          </article>
        `)}
      </section>

      ${panel("Все записи", "Поиск, сортировка и быстрый выбор.", table("cms-entries", items, [
        { key: "section", label: "Раздел", render: (value) => sectionLabel(value) },
        { key: "title", label: "Заголовок", render: (value, row) => `<strong>${esc(cleanText(value || row.key || "Запись"))}</strong><br><span class="muted">${esc(row.key || "")}</span>` },
        { key: "enabled", label: "Статус", render: (value) => value === false ? pill("скрыта", "warn") : pill("активна", "good") },
        { key: "sortOrder", label: "Порядок" },
        { key: "updatedAt", label: "Обновлено", render: (value) => dt(value) },
        { key: "key", label: "Действие", render: (value) => `<button class="btn btn-secondary btn-small" data-click="adminCmsEdit('${esc(value)}')">Открыть</button>` },
      ], { pageSize: 12 }))}
    `);
  }

  function readFormEntry() {
    return {
      entry_key: $("cmsEntryKey")?.value?.trim() || "",
      section: $("cmsSection")?.value || "home",
      title: $("cmsTitle")?.value?.trim() || "",
      body: $("cmsBody")?.value || "",
      image_path: $("cmsImagePath")?.value?.trim() || "",
      link_url: $("cmsLinkUrl")?.value?.trim() || "",
      sort_order: Number($("cmsSortOrder")?.value || 100),
      enabled: Boolean($("cmsEnabled")?.checked),
    };
  }

  async function adminCmsSave() {
    try {
      const body = readFormEntry();
      const headers = await dangerConfirm(`Сохранить CMS-запись ${body.entry_key || body.title}?`, "CMS_SAVE");
      if (!headers) return;
      const result = await api("/api/admin/cms/entries", {
        method: "POST",
        headers,
        body: JSON.stringify(body),
      });
      state.cmsSelectedKey = result.entry?.key || body.entry_key;
      toast("CMS-запись сохранена");
      await loadCms();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function adminCmsDisable(key) {
    try {
      const safeKey = String(key || "").trim();
      const headers = await dangerConfirm(`Скрыть CMS-запись ${safeKey}?`, "CMS_DISABLE");
      if (!headers) return;
      await api(`/api/admin/cms/entries/${encodeURIComponent(safeKey)}`, { method: "DELETE", headers });
      toast("CMS-запись скрыта");
      state.cmsSelectedKey = "";
      await loadCms();
    } catch (err) {
      toast(err.message, true);
    }
  }

  function adminCmsEdit(key) {
    state.cmsSelectedKey = String(key || "").trim();
    void loadCms();
  }

  function adminCmsNew() {
    state.cmsSelectedKey = "";
    setView(panel("Новая CMS-запись", "Заполни поля и сохрани.", `
      <section class="layout-grid grid-2 cms-workbench">
        <div class="panel">
          <div class="form-grid">
            <div class="field-stack">
              <label for="cmsEntryKey">Ключ</label>
              <input id="cmsEntryKey" placeholder="news-short-title" />
            </div>
            <div class="field-stack">
              <label for="cmsSection">Раздел</label>
              <select id="cmsSection">${sectionOptions(["home", "news", "faq", "rules", "shops", "banners"], "news")}</select>
            </div>
            <div class="field-stack full">
              <label for="cmsTitle">Заголовок</label>
              <input id="cmsTitle" placeholder="Заголовок" />
            </div>
            <div class="field-stack full">
              <label for="cmsBody">Текст</label>
              <textarea id="cmsBody" rows="7" placeholder="Текст без HTML"></textarea>
            </div>
            <input id="cmsImagePath" placeholder="/assets/showcase/home-light-v2.png" />
            <input id="cmsLinkUrl" placeholder="/index.html" />
            <input id="cmsSortOrder" type="number" min="0" step="1" value="100" />
            <label class="toggle-row" for="cmsEnabled"><input id="cmsEnabled" type="checkbox" checked /><span>Показывать</span></label>
          </div>
          <div class="action-strip wrap">
            <button class="btn btn-primary" data-click="adminCmsSave()">Сохранить</button>
            <button class="btn btn-secondary" data-click="setTab('cms')">К списку</button>
          </div>
        </div>
      </section>
    `));
  }

  function adminCmsSelect() {
    const key = $("cmsEntrySelect")?.value || "";
    if (key) adminCmsEdit(key);
  }

  return {
    loadCms,
    adminCmsSave,
    adminCmsDisable,
    adminCmsEdit,
    adminCmsNew,
    adminCmsSelect,
  };
}
