export function createPluginRegistryPages(deps) {
  const {
    $,
    state,
    api,
    safeApi,
    setLoading,
    setView,
    panel,
    table,
    pill,
    kv,
    asArray,
    esc,
    cleanText,
    empty,
    dbPolicyPanel,
    dangerConfirm,
    toast,
  } = deps;

  function pluginRegistryFieldId(pluginId, key) {
    return `plugin-registry-${String(pluginId || "").replace(/[^a-z0-9_-]/gi, "-")}-${String(key || "").replace(/[^a-z0-9_-]/gi, "-")}`;
  }

  function pluginRegistryFieldControl(pluginId, key, rules, value) {
    const safeRules = rules || {};
    const type = String(safeRules.type || "string").toLowerCase();
    const inputId = pluginRegistryFieldId(pluginId, key);
    const label = cleanText(key);
    if (type === "bool") {
      return `<label class="check-line full"><input id="${esc(inputId)}" type="checkbox" ${value ? "checked" : ""} /> ${esc(label)}</label>`;
    }
    if (type === "enum") {
      const options = asArray(safeRules.allow).map((item) => {
        const selected = String(item) === String(value ?? "") ? "selected" : "";
        return `<option value="${esc(item)}" ${selected}>${esc(item)}</option>`;
      }).join("");
      return `<label class="full"><span>${esc(label)}</span><select id="${esc(inputId)}">${options}</select></label>`;
    }
    if (type === "int") {
      const min = Number.isFinite(Number(safeRules.min)) ? `min="${esc(safeRules.min)}"` : "";
      const max = Number.isFinite(Number(safeRules.max)) ? `max="${esc(safeRules.max)}"` : "";
      return `<label><span>${esc(label)}</span><input id="${esc(inputId)}" type="number" step="1" ${min} ${max} value="${esc(value ?? "")}" /></label>`;
    }
    if (type === "int_list") {
      const listValue = Array.isArray(value) ? value.join(", ") : "";
      return `<label class="full"><span>${esc(label)}</span><input id="${esc(inputId)}" type="text" value="${esc(listValue)}" placeholder="Например: 50, 100, 250" /></label>`;
    }
    return `<label class="full"><span>${esc(label)}</span><input id="${esc(inputId)}" type="text" value="${esc(value ?? "")}" /></label>`;
  }

  function pluginRegistryCollectValues(pluginId, schema) {
    const values = {};
    for (const [key, rules] of Object.entries(schema || {})) {
      const inputId = pluginRegistryFieldId(pluginId, key);
      const element = $(inputId);
      if (!element) continue;
      const type = String(rules?.type || "string").toLowerCase();
      if (type === "bool") {
        values[key] = Boolean(element.checked);
        continue;
      }
      if (type === "int") {
        const parsed = Number(element.value);
        if (!Number.isInteger(parsed)) {
          throw new Error(`Поле ${key} должно быть целым числом`);
        }
        values[key] = parsed;
        continue;
      }
      if (type === "int_list") {
        const raw = String(element.value || "").trim();
        const parts = raw ? raw.split(",").map((item) => item.trim()).filter(Boolean) : [];
        const parsed = parts.map((item) => Number(item));
        if (parsed.some((item) => !Number.isInteger(item))) {
          throw new Error(`Поле ${key} должно содержать список целых чисел через запятую`);
        }
        values[key] = parsed;
        continue;
      }
      values[key] = String(element.value || "");
    }
    return values;
  }

  async function loadPluginRegistryState(selectedPluginId = "") {
    const registry = await safeApi("/api/admin/plugins/registry", { plugins: [], count: 0 });
    const plugins = asArray(registry.plugins);
    const selected = selectedPluginId || state.pluginRegistrySelected || plugins[0]?.pluginId || "";
    let status = {};
    let schema = {};
    let config = { values: {} };
    let audit = { audit: [] };
    if (selected) {
      [status, schema, config, audit] = await Promise.all([
        safeApi(`/api/admin/plugins/${encodeURIComponent(selected)}/status`, {}),
        safeApi(`/api/admin/plugins/${encodeURIComponent(selected)}/schema`, { editableKeys: {} }),
        safeApi(`/api/admin/plugins/${encodeURIComponent(selected)}/config`, { values: {} }),
        safeApi(`/api/admin/plugins/${encodeURIComponent(selected)}/audit?limit=40`, { audit: [] })
      ]);
    }
    state.pluginRegistrySelected = selected;
    state.pluginRegistryStatus = status || {};
    state.pluginRegistrySchema = schema.editableKeys || {};
    state.pluginRegistryConfigValues = config.values || {};
    return { plugins, selected, status, schema, config, audit };
  }

  function pluginRegistryPanel(registryState) {
    const plugins = asArray(registryState.plugins);
    const selected = registryState.selected || "";
    const status = registryState.status || {};
    const schema = registryState.schema?.editableKeys || {};
    const configValues = registryState.config?.values || {};
    const auditRows = asArray(registryState.audit?.audit);
    const hasConfigPath = Boolean(status.configPath);
    const hasEditableKeys = Object.keys(schema).length > 0;
    const canBackup = hasConfigPath;
    const canApply = hasConfigPath && hasEditableKeys;
    const canReload = String(status.reloadMode || "none") !== "none";
    const validateLabel = hasEditableKeys ? "Validate" : "Validate недоступен";
    const backupLabel = canBackup ? "Backup" : "Backup недоступен";
    const applyLabel = canApply ? "Apply" : "Apply недоступен";
    const reloadLabel = canReload ? "Reload" : "Reload недоступен";
    return `
      <section class="layout-grid grid-2">
        ${panel("Plugin registry", "Allowlisted plugin config foundation: status, schema, validate, backup, apply и reload без raw file editing.", plugins.length ? table("plugin-registry", plugins, [
          { key: "displayName", label: "Плагин" },
          { key: "pluginId", label: "ID" },
          { key: "reloadMode", label: "Reload" },
          { key: "pluginId", label: "Открыть", render: (value, row) => `<button class="btn btn-secondary" data-click="pluginRegistrySelect('${esc(row.pluginId)}')">${row.pluginId === selected ? "Открыт" : "Открыть"}</button>` }
        ], { pageSize: 8 }) : empty("Registry пуст", "Manifest пока не отдаёт plugins allowlist."))}
        ${panel("Текущий плагин", selected ? `Сейчас открыт ${cleanText(status.displayName || selected)}.` : "Выбери плагин из списка слева.", selected ? `
          ${kv([
            ["Plugin ID", status.pluginId || selected],
            ["Config", status.configExists ? "найден" : "не найден"],
            ["Путь", status.configPath || "—"],
            ["Reload mode", status.reloadMode || "none"],
            ["Reload command", status.reloadCommand || "—"],
            ["Editable keys", hasEditableKeys ? String(Object.keys(schema).length) : "нет"]
          ])}
          <div class="spacer-12"></div>
          <div class="form-grid">
            ${Object.keys(schema).length ? Object.entries(schema).map(([key, rules]) => pluginRegistryFieldControl(selected, key, rules, configValues[key])).join("") : '<div class="notice">Для этого плагина нет allowlisted editable keys.</div>'}
          </div>
          <div class="spacer-12"></div>
          <div class="button-row">
            <button class="btn btn-secondary ${hasEditableKeys ? "" : "disabled"}" ${hasEditableKeys ? `data-click="pluginRegistryValidate('${esc(selected)}')"` : "disabled"}>${validateLabel}</button>
            <button class="btn btn-secondary ${canBackup ? "" : "disabled"}" ${canBackup ? `data-click="pluginRegistryBackup('${esc(selected)}')"` : "disabled"}>${backupLabel}</button>
            <button class="btn btn-primary ${canApply ? "" : "disabled"}" ${canApply ? `data-click="pluginRegistryApply('${esc(selected)}')"` : "disabled"}>${applyLabel}</button>
            <button class="btn btn-secondary ${canReload ? "" : "disabled"}" ${canReload ? `data-click="pluginRegistryReload('${esc(selected)}')"` : "disabled"}>${reloadLabel}</button>
          </div>
        ` : empty("Плагин не выбран", "Слева можно открыть allowlisted plugin и применить безопасные config changes."))}
      </section>
      ${panel("Plugin registry audit", "Каждое backup/apply/reload действие журналируется.", auditRows.length ? table("plugin-registry-audit", auditRows, null, { pageSize: 10 }) : empty("Аудит пуст", "После первых действий здесь появятся записи."))}
    `;
  }

  async function pluginRegistrySelect(pluginId) {
    state.pluginRegistrySelected = String(pluginId || "");
    await loadSources();
  }

  async function pluginRegistryValidate(pluginId) {
    try {
      if (!Object.keys(state.pluginRegistrySchema || {}).length) {
        toast("Для этого плагина нет allowlisted editable keys", true);
        return;
      }
      const values = pluginRegistryCollectValues(pluginId, state.pluginRegistrySchema || {});
      const result = await api(`/api/admin/plugins/${encodeURIComponent(pluginId)}/validate`, {
        method: "POST",
        body: JSON.stringify({ values })
      });
      toast(`Конфиг валиден: ${(result.validated ? Object.keys(result.validated).length : 0)} ключей`);
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function pluginRegistryBackup(pluginId) {
    try {
      if (!state.pluginRegistryStatus?.configPath) {
        toast("У этого плагина нет allowlisted configPath", true);
        return;
      }
      const headers = await dangerConfirm(`Создать backup конфига ${pluginId}?`, "PLUGIN_REGISTRY_BACKUP");
      if (!headers) return;
      const result = await api(`/api/admin/plugins/${encodeURIComponent(pluginId)}/backup`, {
        method: "POST",
        headers,
        body: "{}"
      });
      toast(`Backup создан: ${result.backup?.name || pluginId}`);
      await loadSources();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function pluginRegistryApply(pluginId) {
    try {
      if (!state.pluginRegistryStatus?.configPath || !Object.keys(state.pluginRegistrySchema || {}).length) {
        toast("Для этого плагина недоступно apply изменений", true);
        return;
      }
      const values = pluginRegistryCollectValues(pluginId, state.pluginRegistrySchema || {});
      const headers = await dangerConfirm(`Применить allowlisted config changes для ${pluginId}?`, "PLUGIN_REGISTRY_APPLY");
      if (!headers) return;
      const result = await api(`/api/admin/plugins/${encodeURIComponent(pluginId)}/apply`, {
        method: "POST",
        headers,
        body: JSON.stringify({ values })
      });
      toast(`Config обновлён: ${(result.updatedKeys || []).join(", ") || pluginId}`);
      await loadSources();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function pluginRegistryReload(pluginId) {
    try {
      if (String(state.pluginRegistryStatus?.reloadMode || "none") === "none") {
        toast("Для этого плагина нет allowlisted reload flow", true);
        return;
      }
      const headers = await dangerConfirm(`Перезагрузить ${pluginId} через allowlisted reload flow?`, "PLUGIN_REGISTRY_RELOAD");
      if (!headers) return;
      const result = await api(`/api/admin/plugins/${encodeURIComponent(pluginId)}/reload`, {
        method: "POST",
        headers,
        body: "{}"
      });
      toast(result.message || (result.reloaded ? "Reload выполнен" : "Reload пропущен"));
      await loadSources();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function loadSources() {
    setLoading("Проверяю источники данных");
    const [data, config, access, registryState] = await Promise.all([
      safeApi("/api/data-sources", { sources: [] }),
      safeApi("/api/config", {}),
      safeApi("/api/security/access", {}),
      loadPluginRegistryState()
    ]);
    setView(`
      ${panel("Источники данных", "Плагины, файлы и БД, на которых строится панель", table("sources", asArray(data.sources), [
        { key: "name", label: "Источник" },
        { key: "type", label: "Тип" },
        { key: "status", label: "Статус", render: v => pill(v, v === "connected" ? "good" : "warn") },
        { key: "capabilities", label: "Данные", render: v => asArray(v).map(x => pill(x, "neutral")).join(" ") || "—" },
        { key: "message", label: "Комментарий" }
      ], { pageSize: 20 }))}
      ${dbPolicyPanel(config.dbWritePolicy || access.dbWritePolicy || {}, access)}
      ${pluginRegistryPanel(registryState)}
    `);
  }

  return {
    loadSources,
    pluginRegistrySelect,
    pluginRegistryValidate,
    pluginRegistryBackup,
    pluginRegistryApply,
    pluginRegistryReload,
  };
}
