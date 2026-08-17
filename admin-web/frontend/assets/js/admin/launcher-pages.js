export function createAdminLauncherPages(deps) {
  const {
    $,
    state,
    api,
    safeApi,
    setLoading,
    setView,
    panel,
    metric,
    pill,
    esc,
    cleanText,
    dt,
    asArray,
    dangerConfirm,
    toast,
  } = deps;

  function releaseLabel(release) {
    return release?.releaseId || "Не опубликован";
  }

  function modsRows(mods) {
    if (!mods.length) return `<tr><td colspan="6" class="muted">В черновике пока нет управляемых модов.</td></tr>`;
    return mods.map((mod) => {
      const id = String(mod.componentId || "");
      const safeId = esc(id);
      return `<tr>
        <td><strong>${esc(mod.displayName || id)}</strong><br><span class="muted">${safeId}</span></td>
        <td><input id="launcherModVersion-${safeId}" value="${esc(mod.version || "")}" aria-label="Версия ${safeId}" /></td>
        <td><input id="launcherModFilename-${safeId}" value="${esc(mod.filename || "")}" aria-label="Файл ${safeId}" /></td>
        <td>${esc(mod.sizeBytes || 0)} байт<br><span class="muted mono">${esc(String(mod.sha256 || "").slice(0, 16))}…</span></td>
        <td><label class="toggle-row compact"><input id="launcherModRequired-${safeId}" type="checkbox"${mod.required === false ? "" : " checked"} /><span>Обязательный</span></label></td>
        <td><div class="action-strip wrap">
          <button class="btn btn-secondary btn-small" data-click="adminLauncherSaveMod('${safeId}')">Сохранить</button>
          <button class="btn btn-danger btn-small" data-click="adminLauncherDeleteMod('${safeId}')">Удалить</button>
        </div></td>
      </tr>`;
    }).join("");
  }

  function renderLauncher(payload = {}) {
    const mods = asArray(payload.mods);
    const releases = asArray(payload.releases);
    const current = releases.find((row) => row.releaseId === payload.currentReleaseId) || null;
    const stats = payload.stats || {};
    const eventCount = Object.values(stats.events || {}).reduce((sum, value) => sum + Number(value || 0), 0);
    const downloadCount = Object.values(stats.downloads || {}).reduce((sum, value) => sum + Number(value || 0), 0);
    const nextSequence = Number(payload.draftRelease?.releaseSequence || 0) + 1;
    const draftId = String(payload.draftRelease?.releaseId || "").trim();

    setView(`
      <section id="launcher-overview" class="layout-grid launcher-admin-grid launcher-admin-metrics">
        ${metric("Текущий релиз", releaseLabel(current), "Подписанный stable manifest", current ? "good" : "warn")}
        ${metric("Моды", mods.length, "В управляемом черновике", mods.length ? "good" : "warn")}
        ${metric("Загрузки", downloadCount, "Файлы и установщики", downloadCount ? "good" : "neutral")}
        ${metric("События", eventCount, "Анонимная диагностика Launcher", eventCount ? "good" : "neutral")}
      </section>

      <section class="layout-grid grid-2 launcher-admin-grid">
        ${panel("Релиз Launcher", "Подготовка публикации не меняет игровой сервер и production-данные.", `
          <div class="kv-grid launcher-release-summary">
            <div><span>Текущий</span><strong>${esc(releaseLabel(current))}</strong></div>
            <div><span>Следующая последовательность</span><strong>${esc(nextSequence)}</strong></div>
            <div><span>Черновик</span><strong>${esc(draftId || "новый")}</strong></div>
            <div><span>Предыдущий</span><strong>${esc(payload.previousReleaseId || "—")}</strong></div>
          </div>
          <div class="form-grid">
            <div class="field-stack"><label for="launcherPublicKeyId">ID публичного ключа</label><input id="launcherPublicKeyId" value="launcher-v1-staging" /></div>
            <div class="field-stack"><label for="launcherReleaseId">ID релиза</label><input id="launcherReleaseId" value="${esc(draftId || `2026.08.17.${nextSequence}`)}" /></div>
            <div class="field-stack"><label for="launcherReleaseSequence">Последовательность</label><input id="launcherReleaseSequence" type="number" min="1" value="${esc(nextSequence)}" /></div>
          </div>
          <div class="action-strip wrap">
            <button class="btn btn-secondary" data-click="adminLauncherValidate()">Проверить готовность</button>
            <button class="btn btn-primary" data-click="adminLauncherPublish()">Опубликовать релиз</button>
            ${current ? `<button class="btn btn-danger" data-click="adminLauncherRollback('${esc(current.releaseId)}')">Откатить на текущий</button>` : ""}
          </div>
        `)}

        ${panel("Статистика и диагностика", "Только агрегаты Launcher: без паролей, device ID, hardware ID и списка модов клиента.", `
          <div class="launcher-stat-list">
            <div><span>launch</span><strong>${esc(stats.events?.launch || 0)}</strong></div>
            <div><span>reconcile_success</span><strong>${esc(stats.events?.reconcile_success || 0)}</strong></div>
            <div><span>reconcile_failure</span><strong>${esc(stats.events?.reconcile_failure || 0)}</strong></div>
            <div><span>game_exit</span><strong>${esc(stats.events?.game_exit || 0)}</strong></div>
          </div>
          <div class="launcher-diagnostic-list">
            ${(asArray(stats.diagnostics).slice(0, 6).map((row) => `<div><span class="mono">${esc(row.code || "UNKNOWN")}</span><small>${esc(dt(row.at))}</small></div>`).join("")) || `<span class="muted">Диагностических кодов пока нет.</span>`}
          </div>
        `)}
      </section>

      ${panel("Управляемые моды", "Загрузка нового файла сама вычисляет SHA-256 на сервере. Публикация остаётся отдельным действием.", `
        <div class="launcher-upload-box">
          <div class="form-grid">
            <div class="field-stack"><label for="launcherModComponent">Component ID</label><input id="launcherModComponent" placeholder="map-helper" /></div>
            <div class="field-stack"><label for="launcherModVersion">Версия</label><input id="launcherModVersion" placeholder="1.0.0" /></div>
            <div class="field-stack"><label for="launcherModFilename">Имя .jar</label><input id="launcherModFilename" placeholder="MapHelper.jar" /></div>
            <div class="field-stack"><label for="launcherModFile">Файл</label><input id="launcherModFile" type="file" accept=".jar,application/java-archive" /></div>
          </div>
          <label class="toggle-row"><input id="launcherModRequired" type="checkbox" checked /><span>Устанавливать как обязательный мод</span></label>
          <div class="action-strip"><button class="btn btn-primary" data-click="adminLauncherUpload()">Добавить или заменить .jar</button></div>
        </div>
        <div class="table-wrap launcher-mod-table"><table><thead><tr><th>Компонент</th><th>Версия</th><th>Файл</th><th>Размер / SHA</th><th>Политика</th><th>Действия</th></tr></thead><tbody>${modsRows(mods)}</tbody></table></div>
      `)}

      ${panel("Новости Launcher", "Структурированные patch notes редактируются отдельной кнопкой и публикуются независимо от релиза.", `
        <div class="launcher-news-preview-list">
          ${(asArray(payload.news).slice(0, 5).map((row) => `<article><div><span class="pill">${esc(row.version || "patch")}</span><strong>${esc(cleanText(row.title || row.slug))}</strong></div><small>${esc(dt(row.publishedAt || row.updatedAt))}</small></article>`).join("")) || `<span class="muted">Опубликованных новостей пока нет.</span>`}
        </div>
        <div class="action-strip"><button class="btn btn-secondary" data-click="setTab('news')">Открыть редактор новостей</button></div>
      `)}
    `);
  }

  async function loadLauncher() {
    setLoading("Загружаю Launcher");
    const [payload, stats] = await Promise.all([
      safeApi("/api/admin/launcher", { mods: [], releases: [], stats: {}, news: [] }),
      safeApi("/api/admin/launcher/stats", {}),
    ]);
    if (stats && typeof stats === "object") payload.stats = stats;
    renderLauncher(payload);
  }

  async function adminLauncherUpload() {
    try {
      const file = $("launcherModFile")?.files?.[0];
      if (!file) throw new Error("Выбери .jar-файл");
      const headers = await dangerConfirm(`Загрузить ${file.name} в черновик Launcher?`, "LAUNCHER_MOD_UPLOAD");
      if (!headers) return;
      const form = new FormData();
      form.append("file", file);
      form.append("component_id", $("launcherModComponent")?.value?.trim() || "");
      form.append("version", $("launcherModVersion")?.value?.trim() || "");
      form.append("filename", $("launcherModFilename")?.value?.trim() || file.name);
      form.append("required", String(Boolean($("launcherModRequired")?.checked)));
      await api("/api/admin/launcher/mods/upload", { method: "POST", headers, body: form });
      toast("Мод добавлен в черновик Launcher");
      await loadLauncher();
    } catch (error) {
      toast(error.message, true);
    }
  }

  async function adminLauncherSaveMod(componentId) {
    try {
      const safe = String(componentId || "");
      const headers = await dangerConfirm(`Сохранить метаданные мода ${safe}?`, "LAUNCHER_MOD_EDIT");
      if (!headers) return;
      await api(`/api/admin/launcher/mods/${encodeURIComponent(safe)}`, {
        method: "PATCH",
        headers,
        body: JSON.stringify({
          version: $(
            `launcherModVersion-${safe}`
          )?.value?.trim() || "",
          filename: $(`launcherModFilename-${safe}`)?.value?.trim() || "",
          required: Boolean($(`launcherModRequired-${safe}`)?.checked),
        }),
      });
      toast("Метаданные мода сохранены");
      await loadLauncher();
    } catch (error) {
      toast(error.message, true);
    }
  }

  async function adminLauncherDeleteMod(componentId) {
    try {
      const safe = String(componentId || "");
      const headers = await dangerConfirm(`Удалить ${safe} из следующего черновика?`, "LAUNCHER_MOD_DELETE");
      if (!headers) return;
      await api(`/api/admin/launcher/mods/${encodeURIComponent(safe)}`, { method: "DELETE", headers });
      toast("Мод удалён из черновика");
      await loadLauncher();
    } catch (error) {
      toast(error.message, true);
    }
  }

  async function adminLauncherValidate() {
    try {
      const key = $("launcherPublicKeyId")?.value?.trim() || "";
      const result = await api(`/api/admin/launcher/release/validate?publicKeyId=${encodeURIComponent(key)}`, { method: "POST" });
      toast(result.ok ? "Релиз готов к публикации" : `Релиз не готов: ${(result.reasons || []).join(", ")}`, !result.ok);
    } catch (error) {
      toast(error.message, true);
    }
  }

  async function adminLauncherPublish() {
    try {
      const headers = await dangerConfirm("Опубликовать подписанный Launcher-релиз?", "LAUNCHER_RELEASE_PUBLISH");
      if (!headers) return;
      await api("/api/admin/launcher/release/publish", {
        method: "POST",
        headers,
        body: JSON.stringify({
          publicKeyId: $("launcherPublicKeyId")?.value?.trim() || "",
          releaseId: $("launcherReleaseId")?.value?.trim() || "",
          releaseSequence: Number($("launcherReleaseSequence")?.value || 0),
        }),
      });
      toast("Launcher-релиз опубликован");
      await loadLauncher();
    } catch (error) {
      toast(error.message, true);
    }
  }

  async function adminLauncherRollback(releaseId) {
    try {
      const headers = await dangerConfirm(`Переключить distribution на immutable-релиз ${releaseId}?`, "LAUNCHER_RELEASE_ROLLBACK");
      if (!headers) return;
      await api("/api/admin/launcher/release/rollback", { method: "POST", headers, body: JSON.stringify({ releaseId: String(releaseId || "") }) });
      toast("Distribution переключён на выбранный релиз");
      await loadLauncher();
    } catch (error) {
      toast(error.message, true);
    }
  }

  return {
    loadLauncher,
    adminLauncherUpload,
    adminLauncherSaveMod,
    adminLauncherDeleteMod,
    adminLauncherValidate,
    adminLauncherPublish,
    adminLauncherRollback,
  };
}
