/* Наркотики CopiMineNarcotics: рецепты, эффекты и текстуры.
   Проверки повторяют плагин: то, что не прошло здесь, на сервер не уходит. */
(function () {
  'use strict';
  const M = CM, V = CMV4, W = CM5, X = CM6, E = M.esc, H = M.handlers, F = M.forms;
  const ALIASES = { CONFUSION: 'NAUSEA', HARM: 'INSTANT_DAMAGE', SLOW_DIGGING: 'MINING_FATIGUE' };
  const EFFECT_RU = {
    SPEED: 'Скорость', SLOWNESS: 'Замедление', HASTE: 'Спешка', MINING_FATIGUE: 'Усталость',
    STRENGTH: 'Сила', INSTANT_HEALTH: 'Мгновенное лечение', INSTANT_DAMAGE: 'Мгновенный урон',
    JUMP_BOOST: 'Прыгучесть', NAUSEA: 'Тошнота', REGENERATION: 'Регенерация', RESISTANCE: 'Сопротивление',
    FIRE_RESISTANCE: 'Огнестойкость', WATER_BREATHING: 'Подводное дыхание', INVISIBILITY: 'Невидимость',
    BLINDNESS: 'Слепота', NIGHT_VISION: 'Ночное зрение', HUNGER: 'Голод', WEAKNESS: 'Слабость',
    POISON: 'Отравление', WITHER: 'Иссушение', HEALTH_BOOST: 'Прибавка здоровья', ABSORPTION: 'Поглощение',
    SATURATION: 'Насыщение', GLOWING: 'Свечение', LEVITATION: 'Левитация', LUCK: 'Удача', UNLUCK: 'Неудача',
    SLOW_FALLING: 'Медленное падение', CONDUIT_POWER: 'Сила проводника', DOLPHINS_GRACE: 'Грация дельфина',
    BAD_OMEN: 'Дурное предзнаменование', HERO_OF_THE_VILLAGE: 'Герой деревни', DARKNESS: 'Тьма',
    OOZING: 'Слизь', WIND_CHARGED: 'Ветряной заряд', INFESTED: 'Заражение', TRIAL_OMEN: 'Знамение испытания',
    RAID_OMEN: 'Знамение набега',
  };
  const VISUAL_RU = {
    DESATURATE: 'Обесцвечивание', COLOR_CONVOLVE: 'Искажение цвета', SCAN_PINCUSHION: 'Пинкушин',
    GREEN_NOISE: 'Зелёный шум', INVERT: 'Инверсия', WOBBLE: 'Волны', BLOBS: 'Пятна',
    PENCIL: 'Карандаш', CHAOS: 'Хаос',
  };
  let doc = null, version = '', visuals = [], effects = [], current = '', draft = null;
  const drafts = {};
  // Готовые текстуры (из ресурспака): из них можно выбрать любую, не загружая файл.
  let textureOptions = [];
  let picker = { mode: 'material', query: '', page: 0 };
  const fxName = (id) => EFFECT_RU[id] || id;

  // ---- клиентская проверка: те же правила, что в NarcoticsConfigService ----
  function check(document, ids) {
    if (!document || !document.items || !Object.keys(document.items).length) throw Error('Оставь хотя бы один наркотик.');
    const signatures = {}, models = new Set();
    for (const id of Object.keys(document.items)) {
      const it = document.items[id];
      const name = String(it.display_name || '').replace(/[&§][0-9a-fk-or]/gi, '').trim();
      if (!name || name.length > 64) throw Error('«' + id + '»: название от 1 до 64 символов.');
      if (!ids.has(it.material)) throw Error('«' + id + '»: основной материал не из каталога Minecraft.');
      if (it.fallback_material && !ids.has(it.fallback_material)) throw Error('«' + id + '»: запасной материал не из каталога.');
      if (!/^narcotic_[a-z0-9_]{1,56}$/.test(it.texture_key)) throw Error('«' + id + '»: ключ текстуры вида narcotic_<id>.');
      if (!Number.isInteger(it.custom_model_data) || it.custom_model_data < 1) throw Error('«' + id + '»: custom_model_data — целое больше нуля.');
      if (!Number.isInteger(it.overdose_weight) || it.overdose_weight < 0 || it.overdose_weight > 1000) throw Error('«' + id + '»: вес передозировки от 0 до 1000.');
      if (!visuals.includes(it.visual_effect)) throw Error('«' + id + '»: визуальный эффект не из списка плагина.');
      if (id !== 'zhuzevo' && it.recipe.length < 3) throw Error('«' + id + '»: в рецепте минимум 3 ингредиента — иначе котёл не запустится.');
      if (id === 'zhuzevo' && it.recipe.length) throw Error('У zhuzevo рецепт всегда пустой: это эффект передозировки.');
      if (it.recipe.length > 9) throw Error('«' + id + '»: не больше 9 ингредиентов.');
      for (const part of it.recipe) {
        if (part.key.startsWith('MATERIAL:') && !ids.has(part.key.slice(9))) throw Error('«' + id + '»: предмета ' + part.key.slice(9) + ' нет в каталоге.');
        if (!Number.isInteger(part.count) || part.count < 1 || part.count > 64) throw Error('«' + id + '»: количество ингредиента от 1 до 64.');
      }
      for (const [label, list] of [['обычных эффектов', it.normal_effects], ['эффектов передозировки', it.overdose_effects]]) {
        if (list.length > 16) throw Error('«' + id + '»: ' + label + ' не больше 16.');
        for (const row of list) if (!Number.isInteger(row.amplifier) || row.amplifier < 0 || row.amplifier > 255 || !Number.isInteger(row.duration_seconds) || row.duration_seconds < 1 || row.duration_seconds > 86400) throw Error('«' + id + '»: проверь усилитель и длительность эффекта.');
      }
      const signature = it.recipe.flatMap((part) => Array(part.count).fill(part.key)).sort().join('|');
      if (signature) {
        if (signatures[signature]) throw Error('Набор «' + id + '» совпадает с «' + signatures[signature] + '»: рецепты не должны повторяться.');
        signatures[signature] = id;
      }
      const model = it.material + ':' + it.custom_model_data;
      if (models.has(model)) throw Error('custom_model_data ' + it.custom_model_data + ' уже занят для ' + it.material + '.');
      models.add(model);
    }
    return document;
  }

  function materialIds() {
    return new Set((window.CM6_MATERIALS || []).filter((row) => row.giveable !== false && row.id).map((row) => String(row.id).toUpperCase()));
  }

  // ---- разметка ----
  const input = (path, value, extra = '') => `<input data-narc="${path}" value="${E(value ?? '')}" ${extra}>`;
  const num = (path, value, min, max) => `<input data-narc="${path}" type="number" value="${E(value ?? 0)}" min="${min}" max="${max}" step="1" required>`;
  const texSrc = (id, at) => `/api/v6/admin/narcotics/${encodeURIComponent(id)}/texture?preview=1${at ? '&t=' + at : ''}`;

  function itemRow(id, it) {
    const name = String(it.display_name || '').replace(/[&§][0-9a-fk-or]/gi, '').trim() || id;
    return `<a href="#admin/narcotics?item=${encodeURIComponent(id)}" class="cm-narc-row ${id === current ? 'active' : ''}">
      <img data-narc-tex="${E(id)}" src="${texSrc(id)}" alt="" width="28" height="28" loading="lazy">
      <span><strong>${E(name)}</strong><small>${E(id)} · ${it.recipe.length ? it.recipe.length + ' ингр.' : 'без рецепта'}</small></span></a>`;
  }

  function recipeEditor(id) {
    const locked = id === 'zhuzevo';
    const rows = draft.recipe.map((part, index) => {
      const material = part.key.startsWith('MATERIAL:');
      const itemId = 'minecraft:' + part.key.slice(9).toLowerCase();
      return `<li class="cm-narc-part">
        ${material ? W.texture(itemId, 26) : `<span class="cm-narc-potion">${icon('package')}</span>`}
        <span class="cm-narc-part-name">${E(material ? W.materialName(itemId) : 'Зелье: ' + fxName(part.key.slice(7)))}<small>${E(part.key)}</small></span>
        <input data-narc="recipe.${index}.count" type="number" min="1" max="64" step="1" value="${part.count}" aria-label="Количество" class="cm-narc-count">
        <button type="button" class="icon-btn" data-action="narc-part-del" data-index="${index}" aria-label="Убрать ингредиент">${icon('x')}</button>
      </li>`;
    }).join('');
    if (locked) return `<p class="hint">У zhuzevo рецепта нет: это эффект передозировки, плагин не примет набор для него.</p>`;
    return `<ul class="cm-narc-parts">${rows || '<li class="hint">Пока пусто — нужно минимум три ингредиента.</li>'}</ul>
      <div class="actions mt"><button type="button" class="btn small ghost" data-action="narc-part-add" data-kind="material">${icon('plus')}Предмет</button><button type="button" class="btn small ghost" data-action="narc-part-add" data-kind="potion">${icon('plus')}Зелье</button></div>
      <p class="hint mt12">Порядок не важен, считается набор. Один и тот же набор не может быть у двух наркотиков.</p>`;
  }

  function effectEditor(group, label) {
    const list = draft[group];
    return `<div class="cm-narc-fx"><div class="panel-head"><h3>${label}</h3><button type="button" class="btn small ghost" data-action="narc-fx-add" data-group="${group}">${icon('plus')}Эффект</button></div>
      ${list.length ? list.map((row, index) => `<div class="cm-narc-fx-row">
        <select data-narc="${group}.${index}.type" aria-label="Эффект">${effects.map((id) => `<option value="${id}"${id === row.type ? ' selected' : ''}>${E(fxName(id))}</option>`).join('')}</select>
        <label class="cm-narc-mini">Уровень ${num(group + '.' + index + '.amplifier', row.amplifier, 0, 255)}</label>
        <label class="cm-narc-mini">Секунды ${num(group + '.' + index + '.duration_seconds', row.duration_seconds, 1, 86400)}</label>
        <button type="button" class="icon-btn" data-action="narc-fx-del" data-group="${group}" data-index="${index}" aria-label="Убрать эффект">${icon('x')}</button>
      </div>`).join('') : '<p class="hint">Эффектов нет.</p>'}</div>`;
  }

  function editor() {
    const id = current, it = draft;
    return `<section class="panel cm-narc-editor"><div class="panel-head"><div><h2>${E(String(it.display_name || id).replace(/[&§][0-9a-fk-or]/gi, '').trim() || id)}</h2><p class="hint mt12"><code>${E(id)}</code> · ${E(it.material)}:${it.custom_model_data}</p></div><button type="button" class="btn ghost small" data-action="narc-export">${icon('download')}config.yml</button></div>
      <div class="cm-narc-grid">
        <label class="field">Название в игре<input data-narc="display_name" value="${E(it.display_name)}" maxlength="64" required></label>
        <label class="field">Основной материал<input data-narc="material" value="${E(it.material)}" list="cm-narc-materials" maxlength="64" required></label>
        <label class="field">Запасной материал<input data-narc="fallback_material" value="${E(it.fallback_material || '')}" list="cm-narc-materials" maxlength="64"></label>
        <label class="field">Ключ текстуры<input data-narc="texture_key" value="${E(it.texture_key)}" maxlength="64" required></label>
        <label class="field">custom_model_data${num('custom_model_data', it.custom_model_data, 1, 2000000000)}</label>
        <label class="field">Вес передозировки${num('overdose_weight', it.overdose_weight, 0, 1000)}</label>
        <label class="field">Визуальный эффект<select data-narc="visual_effect">${visuals.map((row) => `<option value="${row}"${row === it.visual_effect ? ' selected' : ''}>${E(VISUAL_RU[row] || row)}</option>`).join('')}</select></label>
      </div>
      <datalist id="cm-narc-materials">${(window.CM6_MATERIALS || []).slice(0, 2000).map((row) => `<option value="${E(String(row.id).toUpperCase())}">${E(row.ru || row.en || row.id)}</option>`).join('')}</datalist>
      <div class="cm-narc-texture"><img data-narc-tex="${E(id)}" src="${texSrc(id)}" alt="" width="64" height="64"><div><strong>Текстура предмета</strong><p class="hint">Квадратная картинка. В игре используется текстура из ресурспака этого предмета.</p><div class="actions mt"><label class="btn small ghost">${icon('upload')}Загрузить фото<input type="file" data-narc-texture accept="image/png,image/jpeg,image/webp" hidden></label>${textureOptions.length > 1 ? `<select data-narc-texture-pick aria-label="Выбрать готовую текстуру"><option value="">Взять другую текстуру…</option>${textureOptions.map((row) => `<option value="${E(row.id)}"${row.id === id ? ' selected' : ''}>${E(row.name)}${row.own ? ' (своя)' : ''}</option>`).join('')}</select>` : ''}</div></div></div>
      <h3 class="mt">Рецепт в котле</h3>${recipeEditor(id)}
      ${effectEditor('normal_effects', 'Обычные эффекты')}
      ${effectEditor('overdose_effects', 'Эффекты передозировки')}
      <label class="field mt">Причина изменения<textarea data-narc="__reason" rows="2" maxlength="240" minlength="3" required placeholder="Видна только администрации"></textarea></label>
      <div class="actions"><button type="submit" class="btn primary">${icon('check')}Сохранить наркотик</button><span class="hint">Проверка идёт здесь и повторно на сервере. В игру ничего не пишется.</span></div>
    </section>`;
  }

  function shell() {
    if (!doc) return `<div class="cm-narc-loading" role="status"><span></span><span></span><span></span></div>`;
    const ids = Object.keys(doc.items);
    return `<div class="cm-narc-layout"><aside class="cm-narc-list"><div class="panel-head"><h3>Наркотики</h3>${M.pill(ids.length + ' шт.')}</div>${ids.map((id) => itemRow(id, doc.items[id])).join('')}</aside><div class="cm-narc-main"><form data-form="narcotics-save">${editor()}</form></div></div>`;
  }

  // ---- данные ----
  async function load(force) {
    if (!doc || force) {
      const data = await X.request('/admin/narcotics');
      doc = data.document; version = data.version; visuals = data.visuals || []; effects = data.effects || [];
      // Список готовых текстур нужен для выбора; без него остаётся только загрузка файла.
      try { textureOptions = (await X.request('/admin/narcotics/textures')).items || []; } catch { textureOptions = []; }
    }
    const ids = Object.keys(doc.items);
    const wanted = M.UI.params.get('item');
    current = wanted && doc.items[wanted] ? wanted : (doc.items[current] ? current : ids[0]);
    // Правки разных наркотиков не должны теряться при переключении.
    drafts[current] ||= M.clone(doc.items[current]);
    draft = drafts[current];
  }

  function paint(root) { root.innerHTML = shell(); attachTextureFallback(); }

  function attachTextureFallback() {
    document.querySelectorAll('img[data-narc-tex]').forEach((img) => {
      if (img.dataset.bound) return;
      img.dataset.bound = '1';
      img.addEventListener('error', () => { img.src = M.asset('assets/narcotics/' + img.dataset.narcTex + '.png'); }, { once: true });
    });
  }

  const root = () => document.querySelector('[data-narcotics-admin]');

  async function mount() {
    const host = root();
    if (!host || host.dataset.loaded) return;
    host.dataset.loaded = '1';
    host.innerHTML = `<div class="cm-narc-loading" role="status"><span></span><span></span><span></span></div>`;
    try {
      if (!X.live) { host.innerHTML = M.empty('Только на сервере', 'Наркотики читаются и сохраняются на рабочем сервере.', 'lock'); return; }
      // Пикер и поиск материалов строятся по библиотеке предметов.
      if (!window.CM6_MATERIALS && window.CMMaterials) await window.CMMaterials.ready();
      await load();
      paint(host);
    } catch (error) {
      host.innerHTML = M.empty('Раздел не открылся', error?.message || 'Повтори попытку.', 'alert');
    }
  }

  // ---- правка черновика ----
  function setPath(target, path, value) {
    const keys = path.split('.');
    let node = target;
    for (let i = 0; i < keys.length - 1; i++) node = node[keys[i]];
    node[keys[keys.length - 1]] = value;
  }

  document.addEventListener('input', (event) => {
    const node = event.target.closest('[data-narc]');
    if (!node || !draft) return;
    const path = node.dataset.narc;
    const numeric = node.type === 'number';
    const value = numeric ? (node.value === '' ? NaN : Number(node.value)) : node.value;
    if (path === '__reason') draft.__reason = value;
    else setPath(draft, path, value);
  });
  document.addEventListener('change', (event) => {
    const node = event.target.closest('select[data-narc]');
    if (!node || !draft) return;
    setPath(draft, node.dataset.narc, node.value);
  });

  H['narc-part-del'] = (button) => { draft.recipe.splice(+button.dataset.index, 1); M.render(); };
  H['narc-part-add'] = (button) => openPicker(button.dataset.kind);
  H['narc-fx-add'] = (button) => { if (draft[button.dataset.group].length >= 16) return M.toast('Не больше 16 эффектов.', true); draft[button.dataset.group].push({ type: effects[0], amplifier: 0, duration_seconds: 60 }); M.render(); };
  H['narc-fx-del'] = (button) => { draft[button.dataset.group].splice(+button.dataset.index, 1); M.render(); };

  // ---- пикер предметов и зелий ----
  function openPicker(mode) {
    picker = { mode, query: '', page: 0 };
    M.openModal(mode === 'potion' ? 'Эффект зелья' : 'Предмет Minecraft',
      `<div class="creative-picker">${mode === 'potion' ? '' : `<label class="input-wrap">${icon('search')}<input id="cm-narc-search" placeholder="Камень, stone, minecraft:stone" aria-label="Поиск предмета" autocomplete="off"></label>`}<div class="creative-grid" id="cm-narc-results"></div><div class="picker-description" id="cm-narc-desc">${mode === 'potion' ? 'Ингредиент-зелье: плагин берёт эффект и варит по нему.' : 'Выбери предмет: он попадёт в рецепт.'}</div><div class="picker-pagination">${M.btn('Назад','narc-page','chevron-left','small','data-delta="-1"')}<span id="cm-narc-count"></span>${M.btn('Дальше','narc-page','chevron-right','small','data-delta="1"')}</div></div>`, 'wide-dialog');
    if (mode !== 'potion') {
      const search = document.getElementById('cm-narc-search');
      let timer;
      search.addEventListener('input', () => { picker.query = search.value; picker.page = 0; clearTimeout(timer); timer = setTimeout(pick, 60); });
      search.focus();
    }
    pick();
  }

  function candidates() {
    if (picker.mode === 'potion') {
      const list = effects.map((id) => ({ id, label: fxName(id) }));
      const q = picker.query.trim().toLowerCase();
      return q ? list.filter((row) => (row.id + ' ' + row.label).toLowerCase().includes(q)) : list;
    }
    const rows = (window.CM6_MATERIALS || []).filter((row) => row.giveable !== false && row.id !== 'air');
    const q = picker.query.trim().toLowerCase();
    return rows.filter((row) => !q || [row.id, row.ru, row.en].join(' ').toLowerCase().includes(q)).slice(0, 400);
  }

  function pick() {
    const grid = document.getElementById('cm-narc-results');
    if (!grid) return;
    const list = candidates(), size = 72, pages = Math.max(1, Math.ceil(list.length / size));
    picker.page = Math.min(pages - 1, Math.max(0, picker.page));
    grid.innerHTML = list.slice(picker.page * size, (picker.page + 1) * size).map((row) => picker.mode === 'potion'
      ? `<button type="button" class="cm-narc-effect" data-action="narc-choose" data-kind="potion" data-id="${E(row.id)}" title="${E(row.id)}">${E(row.label)}</button>`
      : `<button type="button" class="creative-slot" data-action="narc-choose" data-kind="material" data-id="${E(String(row.id).toUpperCase())}" title="${E((row.ru || row.en || row.id) + '\n' + row.id)}"><img src="${M.asset(row.image || 'assets/materials/' + row.id + '.png')}" alt="" width="32" height="32" loading="lazy"></button>`).join('') || '<p class="creative-empty">Ничего не найдено.</p>';
    document.getElementById('cm-narc-count').textContent = list.length + ' · ' + (picker.page + 1) + ' / ' + pages;
    const prev = document.querySelector('[data-action="narc-page"][data-delta="-1"]'), next = document.querySelector('[data-action="narc-page"][data-delta="1"]');
    if (prev) prev.disabled = picker.page === 0;
    if (next) next.disabled = picker.page >= pages - 1;
  }

  H['narc-page'] = (button) => { picker.page += +button.dataset.delta; pick(); };
  H['narc-choose'] = (button) => {
    const id = (button.dataset.kind === 'potion' ? 'POTION:' : 'MATERIAL:') + (ALIASES[button.dataset.id] || button.dataset.id);
    if (draft.recipe.length >= 9) return M.toast('Не больше 9 ингредиентов.', true);
    const existing = draft.recipe.find((part) => part.key === id);
    // Набор считается по количеству: повторный выбор увеличивает количество.
    if (existing && existing.count < 64) existing.count += 1;
    else draft.recipe.push({ key: id, count: 1 });
    M.closeModal();
    M.render();
  };

  // ---- сохранение ----
  F['narcotics-save'] = async () => {
    const reason = String(draft.__reason || '').trim();
    if (reason.length < 3) throw Error('Укажи причину изменения: от 3 символов.');
    const clean = M.clone(draft);
    delete clean.__reason;
    const candidate = M.clone(doc);
    candidate.items[current] = clean;
    check(candidate, materialIds());
    if (!X.live) return M.toast('Черновик проверен.', false);
    const saved = await X.request('/admin/narcotics', { method: 'PUT', body: { document: candidate, version, reason } });
    doc = saved.document; version = saved.version;
    drafts[current] = M.clone(doc.items[current]); draft = drafts[current];
    M.render();
    M.toast('Наркотик сохранён. Проверки прошли.');
  };

  H['narc-export'] = async () => {
    const data = await X.request('/admin/narcotics/export');
    M.downloadFile('copimine-narcotics.yml', data.yaml, 'text/yaml;charset=utf-8');
  };

  document.addEventListener('change', async (event) => {
    // Выбор готовой текстуры из ресурспака — без загрузки файла.
    const pick = event.target.closest('select[data-narc-texture-pick]');
    if (pick) {
      const source = pick.value;
      if (!source || !current) return;
      const reasonNode = document.querySelector('[data-narc="__reason"]');
      const reason = String(reasonNode?.value || '').trim();
      if (reason.length < 3) { pick.value = ''; return M.toast('Сначала укажи причину изменения.', true); }
      try {
        await X.request('/admin/narcotics/' + encodeURIComponent(current) + '/texture',
          { method: 'POST', body: { source, reason } });
        document.querySelectorAll('[data-narc-tex="' + current + '"]').forEach((img) => {
          img.src = texSrc(current, Date.now()); img.dataset.bound = '';
        });
        attachTextureFallback();
        M.toast('Текстура выбрана.');
      } catch (error) {
        M.toast(error?.message || 'Не удалось выбрать текстуру.', true);
      } finally { pick.value = ''; }
      return;
    }
    const node = event.target.closest('input[data-narc-texture]');
    if (!node) return;
    const file = node.files?.[0];
    if (!file) return;
    const reasonNode = document.querySelector('[data-narc="__reason"]');
    const reason = String(reasonNode?.value || '').trim();
    if (reason.length < 3) { node.value = ''; return M.toast('Сначала укажи причину изменения.', true); }
    try {
      const dataUrl = await V.imageData(file, 512);
      await X.request('/admin/narcotics/' + encodeURIComponent(current) + '/texture', { method: 'POST', body: { image: dataUrl.split(',')[1], reason } });
      document.querySelectorAll('[data-narc-tex="' + current + '"]').forEach((img) => { img.src = texSrc(current, Date.now()); img.dataset.bound = ''; });
      attachTextureFallback();
      M.toast('Текстура загружена.');
    } catch (error) {
      M.toast(error?.message || 'Не удалось загрузить текстуру.', true);
    } finally { node.value = ''; }
  });

  V.after.push((page, tab) => {
    if (page === 'admin' && tab === 'narcotics') void mount();
  });
})();
