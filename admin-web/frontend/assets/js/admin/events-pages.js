export function createAdminEventsPages(deps) {
  const {
    $,
    state,
    api,
    safeApi,
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

  function selectedEvent(events) {
    const selected = String(state.eventSelectedSlug || "").trim();
    return asArray(events).find((event) => String(event.slug || "") === selected) || asArray(events)[0] || {};
  }

  function mediaRows(event) {
    const videos = asArray(event.videos);
    if (!videos.length) return `<p class="muted">Видео пока не загружены.</p>`;
    return videos.map((video) => `<div class="event-admin-media-row">
      <div><strong>${esc(cleanText(video.title || video.filename || "Видео"))}</strong><small>${esc(video.filename || "")} · ${esc(video.sizeBytes || 0)} байт</small><small class="mono">${esc(String(video.sha256 || "").slice(0, 16))}…</small></div>
      <button class="btn btn-danger btn-small" data-click="adminEventDeleteVideo('${esc(event.slug)}',${Number(video.id || 0)})">Удалить</button>
    </div>`).join("");
  }

  function renderEvents(events) {
    const list = asArray(events);
    const active = selectedEvent(list);
    const selectedSlug = String(active.slug || "");
    state.eventRecords = list;
    if (selectedSlug && state.eventSelectedSlug !== selectedSlug) state.eventSelectedSlug = selectedSlug;
    const currentCount = list.filter((event) => event.status === "current" && event.enabled !== false).length;
    const videoCount = list.reduce((total, event) => total + asArray(event.videos).length, 0);
    const requirements = asArray(active.requirements);
    const waves = asArray(active.waves);
    const phases = asArray(active.bossPhases);
    const rewards = asArray(active.rewards);

    setView(`
      <section class="layout-grid grid-4 event-admin-metrics">
        ${metric("События", list.length, "Страницы в календаре", list.length ? "good" : "warn")}
        ${metric("Сейчас", currentCount, "Активные события", currentCount === 1 ? "good" : "warn")}
        ${metric("Видео", videoCount, "Записи на страницах", videoCount ? "good" : "neutral")}
        ${metric("Волны", waves.length, "У выбранного события", waves.length ? "good" : "neutral")}
      </section>

      <section class="layout-grid grid-2 event-admin-workbench">
        ${panel("События", "Выбери страницу и правь её в этой же панели.", `
          <div class="event-admin-event-list">
            ${(list.map((event) => `<button class="event-admin-event-row${event.slug === selectedSlug ? " is-active" : ""}" data-click="adminEventSelect('${esc(event.slug)}')"><span><strong>${esc(cleanText(event.title || event.slug))}</strong><small>${esc(event.slug)}</small></span><span class="pill ${event.status === "current" ? "good" : "neutral"}">${esc(event.status === "current" ? "Сейчас" : "Скоро")}</span></button>`).join("")) || `<p class="muted">Событий пока нет.</p>`}
          </div>
        `)}
        ${panel("Материалы", "Видео хранится в папке события и появляется на публичной странице после загрузки.", `
          <div class="event-admin-upload">
            <div class="field-stack"><label for="eventVideoTitle">Название ролика</label><input id="eventVideoTitle" placeholder="Финальная волна" /></div>
            <div class="field-stack"><label for="eventVideoPoster">Постер, необязательно</label><input id="eventVideoPoster" placeholder="/assets/events/end-rift/poster.jpg" /></div>
            <div class="field-stack"><label for="eventVideoFile">Видео</label><input id="eventVideoFile" type="file" accept="video/mp4,video/webm,video/ogg,video/quicktime,.mp4,.webm,.ogv,.m4v,.mov" /></div>
            <button class="btn btn-primary" data-click="adminEventUploadVideo('${esc(selectedSlug)}')">Загрузить видео</button>
          </div>
          <div class="event-admin-media-list">${mediaRows(active)}</div>
        `)}
      </section>

      ${panel("Редактор события", "Короткий текст и состояние страницы. Списки боя остаются в исходном сценарии, пока их не меняют отдельно.", `
        <div class="form-grid">
          <div class="field-stack"><label for="eventSlug">Ключ</label><input id="eventSlug" value="${esc(active.slug || "")}" placeholder="end-rift" /></div>
          <div class="field-stack"><label for="eventStatus">Состояние</label><select id="eventStatus"><option value="current"${active.status === "current" ? " selected" : ""}>Сейчас</option><option value="upcoming"${active.status === "upcoming" ? " selected" : ""}>Скоро</option><option value="archived"${active.status === "archived" ? " selected" : ""}>Архив</option></select></div>
          <div class="field-stack"><label for="eventEyebrow">Подпись</label><input id="eventEyebrow" value="${esc(active.eyebrow || "")}" /></div>
          <div class="field-stack"><label for="eventAccent">Цвет #RRGGBB</label><input id="eventAccent" value="${esc(active.accent || "#b887ff")}" /></div>
          <div class="field-stack full"><label for="eventTitle">Заголовок</label><input id="eventTitle" value="${esc(active.title || "")}" /></div>
          <div class="field-stack full"><label for="eventSummary">Коротко</label><textarea id="eventSummary" rows="3">${esc(active.summary || "")}</textarea></div>
          <div class="field-stack full"><label for="eventBody">Текст страницы</label><textarea id="eventBody" rows="6">${esc(active.body || "")}</textarea></div>
          <div class="field-stack"><label for="eventHeroImage">Фон</label><input id="eventHeroImage" value="${esc(active.heroImage || "")}" placeholder="/assets/events/end-rift/end-city.jpg" /></div>
          <div class="field-stack"><label for="eventSortOrder">Порядок</label><input id="eventSortOrder" type="number" min="0" value="${esc(active.sortOrder ?? 100)}" /></div>
          <label class="toggle-row"><input id="eventEnabled" type="checkbox"${active.enabled === false ? "" : " checked"} /><span>Показывать страницу</span></label>
        </div>
        <div class="action-strip wrap"><button class="btn btn-primary" data-click="adminEventSave()">Сохранить событие</button><button class="btn btn-secondary" data-click="adminEventNew()">Новое событие</button></div>
      `)}

      ${panel("Содержание выбранной страницы", "Проверка перед публикацией: эти цифры помогают заметить пустой или случайно обрезанный сценарий.", `
        <div class="event-admin-content-check"><span>Ресурсы <strong>${requirements.length}</strong></span><span>Волны <strong>${waves.length}</strong></span><span>Фазы босса <strong>${phases.length}</strong></span><span>Награды <strong>${rewards.length}</strong></span><span>Обновлено <strong>${esc(dt(active.updatedAt))}</strong></span></div>
      `)}
    `);
  }

  async function loadEvents() {
    setLoading("Загружаю ивенты");
    const payload = await safeApi("/api/admin/events", { events: [] });
    renderEvents(asArray(payload.events));
  }

  function adminEventSelect(slug) {
    state.eventSelectedSlug = String(slug || "").trim();
    renderEvents(state.eventRecords || []);
  }

  function adminEventNew() {
    state.eventSelectedSlug = "";
    state.eventRecords = [{ slug: "", status: "upcoming", eyebrow: "Скоро", title: "", summary: "", body: "", heroImage: "", accent: "#b887ff", requirements: [], waves: [], bossPhases: [], rewards: [], videos: [], enabled: true, sortOrder: 100 }];
    renderEvents(state.eventRecords);
  }

  function readEventForm() {
    const active = selectedEvent(state.eventRecords || []);
    return {
      slug: $("eventSlug")?.value?.trim() || "",
      eyebrow: $("eventEyebrow")?.value?.trim() || "Событие",
      title: $("eventTitle")?.value?.trim() || "",
      status: $("eventStatus")?.value || "upcoming",
      summary: $("eventSummary")?.value || "",
      body: $("eventBody")?.value || "",
      hero_image: $("eventHeroImage")?.value?.trim() || "",
      portrait_image: String(active.portraitImage || "").trim(),
      accent: $("eventAccent")?.value?.trim() || "#b887ff",
      requirements: asArray(active.requirements),
      waves: asArray(active.waves),
      boss_phases: asArray(active.bossPhases),
      rewards: asArray(active.rewards),
      sort_order: Number($("eventSortOrder")?.value || 100),
      enabled: Boolean($("eventEnabled")?.checked),
    };
  }

  async function adminEventSave() {
    try {
      const body = readEventForm();
      if (!body.slug || !body.title) throw new Error("Укажи ключ и заголовок события");
      const headers = await dangerConfirm(`Сохранить событие ${body.slug}?`, "EVENTS_SAVE");
      if (!headers) return;
      await api(`/api/admin/events/${encodeURIComponent(body.slug)}`, { method: "PUT", headers, body: JSON.stringify(body) });
      state.eventSelectedSlug = body.slug;
      toast("Событие сохранено");
      await loadEvents();
    } catch (error) {
      toast(error.message, true);
    }
  }

  async function adminEventUploadVideo(slug) {
    try {
      const file = $("eventVideoFile")?.files?.[0];
      if (!file) throw new Error("Выбери видео");
      const safeSlug = String(slug || "").trim();
      if (!safeSlug) throw new Error("Сначала выбери событие");
      const headers = await dangerConfirm(`Загрузить ${file.name} в событие ${safeSlug}?`, "EVENTS_MEDIA_UPLOAD");
      if (!headers) return;
      const form = new FormData();
      form.append("file", file);
      form.append("title", $("eventVideoTitle")?.value?.trim() || file.name);
      form.append("poster_url", $("eventVideoPoster")?.value?.trim() || "");
      await api(`/api/admin/events/${encodeURIComponent(safeSlug)}/videos`, { method: "POST", headers, body: form });
      toast("Видео загружено");
      await loadEvents();
    } catch (error) {
      toast(error.message, true);
    }
  }

  async function adminEventDeleteVideo(slug, mediaId) {
    try {
      const headers = await dangerConfirm("Удалить это видео из события?", "EVENTS_MEDIA_DELETE");
      if (!headers) return;
      await api(`/api/admin/events/${encodeURIComponent(String(slug || ""))}/videos/${Number(mediaId || 0)}`, { method: "DELETE", headers });
      toast("Видео удалено");
      await loadEvents();
    } catch (error) {
      toast(error.message, true);
    }
  }

  return { loadEvents, adminEventSelect, adminEventNew, adminEventSave, adminEventUploadVideo, adminEventDeleteVideo };
}
