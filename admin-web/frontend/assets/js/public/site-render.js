import { makeElement, replaceChildrenSafe } from "../shared/dom.js";

const publicFeatures = {
  bank: {
    kicker: "Банк AR",
    title: "Один счёт для банкомата, сайта и игровых переводов",
    text: "Баланс, переводы, PIN, личный счёт и доступ к казне у президента работают через единый безопасный банковский контур без ручных обходов.",
    icon: "/assets/mc-icons/item/emerald_block.png",
  },
  elections: {
    kicker: "Выборы",
    title: "Игровая политика без ручного хаоса",
    text: "Заявки, участки, бюллетени, подсчёт и результаты проходят через реальные игровые сценарии, а сайт показывает только подтверждённые данные.",
    icon: "/assets/mc-icons/item/writable_book.png",
  },
  president: {
    kicker: "Казна",
    title: "Отдельный бюджетный счёт сервера",
    text: "Президент и админы работают с отдельным счётом казны. Игроки видят только публичную витрину баланса и истории, без служебных реквизитов.",
    icon: "/assets/mc-icons/item/nether_star.png",
  },
  artifacts: {
    kicker: "Лавки",
    title: "AR-предметы и защищённая выдача",
    text: "Артефакты покупаются и чинятся по реальной экономике AR. Сайт не рисует фейковые статусы и честно показывает, если выдача или получение требуют внимания.",
    icon: "/assets/mc-icons/item/netherite_sword.png",
  },
};

function formatAr(value) {
  const amount = Number(value || 0);
  return `${(Number.isFinite(amount) ? amount : 0).toLocaleString("ru-RU")} AR`;
}

function formatDate(value) {
  const raw = Number(value || 0);
  if (!raw) return "—";
  const date = raw > 1000000000000 ? new Date(raw) : new Date(raw * 1000);
  if (!Number.isFinite(date.getTime())) return "—";
  return date.toLocaleString("ru-RU");
}

function formatLatency(value) {
  const ms = Number(value);
  if (!Number.isFinite(ms) || ms <= 0) return "нет ответа";
  return `${ms.toFixed(1)} мс`;
}

function formatPlayers(server = {}) {
  const online = Number(server.playersOnline || 0);
  const cap = Number(server.playerCap || 0);
  if (!cap) return `${online} игроков онлайн`;
  return `${online} / ${cap} игроков`;
}

function formatMegabytes(bytes) {
  const value = Number(bytes || 0);
  if (!Number.isFinite(value) || value <= 0) return "0 МБ";
  return `${(value / (1024 * 1024)).toFixed(2)} МБ`;
}

function shortSha(value) {
  const sha = String(value || "").trim();
  if (!sha) return "нет";
  return sha.length > 12 ? `${sha.slice(0, 12)}…` : sha;
}

function roleRoute(role) {
  return role === "player" ? "#cabinet" : "#dashboard";
}

function roleLabel(role) {
  switch (String(role || "")) {
    case "player":
      return "Личный кабинет";
    case "junior_admin":
      return "Панель младшего админа";
    case "owner":
      return "Панель владельца";
    case "admin":
      return "Админ-панель";
    default:
      return "Личный кабинет";
  }
}

function cardStrong(title, value, note = "") {
  const card = makeElement("article", "showcase-card");
  card.append(
    makeElement("strong", "", title),
    makeElement("p", "", value),
  );
  if (note) {
    card.append(makeElement("span", "treasury-history-date", note));
  }
  return card;
}

function buildModpackMeta(label, value) {
  const card = makeElement("article", "modpack-stat");
  card.append(
    makeElement("span", "", label),
    makeElement("strong", "", value),
  );
  return card;
}

export function createHomepageRenderer() {
  const budgetCounter = document.getElementById("presidentBudgetCounter");
  const budgetDetail = document.getElementById("presidentBudgetDetail");
  const budgetOwner = document.getElementById("presidentBudgetOwner");
  const historyMount = document.getElementById("publicTreasuryHistory");
  const presidentName = document.getElementById("presidentCardName");
  const presidentMeta = document.getElementById("presidentCardMeta");
  const skinShell = document.getElementById("presidentSkinShell");
  const skinTilt = document.getElementById("presidentSkinTilt");
  const skinImage = document.getElementById("presidentSkinImage");
  const leftButton = document.getElementById("presidentSkinLeft");
  const rightButton = document.getElementById("presidentSkinRight");
  const featurePanel = document.getElementById("publicFeaturePanel");
  const featureTabs = document.getElementById("publicFeatureTabs");
  const serverIpText = document.getElementById("serverIpText");
  const serverPulseText = document.getElementById("serverPulseText");
  const downloadModsBtn = document.getElementById("downloadModsBtn");
  const statusGrid = document.getElementById("publicStatusGrid");
  const onlineBoard = document.getElementById("publicOnlineBoard");
  const cabinetButton = document.getElementById("publicCabinetBtn");
  const heroMiniTitle = document.getElementById("heroMiniTitle");
  const heroMiniText = document.getElementById("heroMiniText");
  const modpackSummaryLead = document.getElementById("modpackSummaryLead");
  const modpackMetaGrid = document.getElementById("modpackMetaGrid");
  const modpackFileGrid = document.getElementById("modpackFileGrid");
  const modpackNotes = document.getElementById("modpackNotes");
  const cabinetRoleCards = Array.from(document.querySelectorAll("[data-cabinet-role]"));

  let currentBudgetValue = 0;
  let currentRotation = -16;
  let currentFeature = "bank";

  function bindCabinetButton() {
    if (!cabinetButton || cabinetButton.dataset.bound === "true") return;
    cabinetButton.dataset.bound = "true";
    cabinetButton.addEventListener("click", () => {
      const routeTarget = cabinetButton.dataset.routeTarget || "";
      if (!routeTarget) return;
      window.dispatchEvent(new CustomEvent("copimine:legacy-runtime-request"));
      window.location.hash = routeTarget;
    });
  }

  function animateCounter(nextValue) {
    if (!budgetCounter) return;
    const target = Math.max(0, Number(nextValue || 0));
    const startedAt = performance.now();
    const start = currentBudgetValue;
    const duration = 850;
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches) {
      currentBudgetValue = target;
      budgetCounter.textContent = formatAr(target);
      return;
    }
    const step = (now) => {
      const progress = Math.min(1, (now - startedAt) / duration);
      const eased = 1 - Math.pow(1 - progress, 3);
      const frame = Math.round(start + (target - start) * eased);
      budgetCounter.textContent = formatAr(frame);
      if (progress < 1) {
        requestAnimationFrame(step);
        return;
      }
      currentBudgetValue = target;
      budgetCounter.textContent = formatAr(target);
    };
    requestAnimationFrame(step);
  }

  function renderFeaturePanel(featureId = "bank") {
    if (!featurePanel) return;
    const feature = publicFeatures[featureId] || publicFeatures.bank;
    const card = makeElement("article", "showcase-card");
    const icon = makeElement("img");
    icon.src = feature.icon;
    icon.alt = "";
    card.append(
      icon,
      makeElement("span", "hero-kicker", feature.kicker),
      makeElement("strong", "", feature.title),
      makeElement("p", "", feature.text),
    );
    replaceChildrenSafe(featurePanel, [card]);
  }

  function bindFeatureTabs() {
    if (!featureTabs || featureTabs.dataset.bound === "true") return;
    featureTabs.dataset.bound = "true";
    featureTabs.addEventListener("click", (event) => {
      const button = event.target instanceof Element ? event.target.closest("[data-public-tab]") : null;
      if (!button) return;
      const nextFeature = String(button.getAttribute("data-public-tab") || "bank");
      currentFeature = nextFeature;
      featureTabs.querySelectorAll("[data-public-tab]").forEach((node) => {
        node.classList.toggle("active", node === button);
      });
      renderFeaturePanel(nextFeature);
    });
    renderFeaturePanel(currentFeature);
  }

  function renderModpack(modpack = {}, config = {}) {
    const manifest = modpack && typeof modpack === "object" ? (modpack.manifest || {}) : {};
    const files = Array.isArray(manifest.files) ? manifest.files : [];
    const notes = Array.isArray(manifest.notes) ? manifest.notes : [];
    const available = Boolean(modpack.available);
    const downloadUrl = modpack.downloadUrl || config.modpackDownloadPath || "/downloads/CopiMineMods.zip";

    if (heroMiniTitle) {
      heroMiniTitle.textContent = available
        ? `${manifest.loader || "Клиент"} ${manifest.minecraftVersion || config.serverVersion || ""}`.trim()
        : "Клиент и моды";
    }
    if (heroMiniText) {
      heroMiniText.textContent = available
        ? `В архиве ${files.length || 0} client jars. SHA1: ${shortSha(modpack.sha1)}.`
        : "Архив модов пока не опубликован или backend ещё не подтвердил его доступность.";
    }

    if (modpackSummaryLead) {
      modpackSummaryLead.textContent = available
        ? `Архив ${modpack.filename || "CopiMineMods.zip"} готов к скачиванию. Он собирается локально и отдаётся сайтом без внешних CDN и сторонних генераторов.`
        : "Архив модов пока недоступен. Сайт не рисует фейковый статус и честно ждёт готовую сборку.";
    }

    if (modpackMetaGrid) {
      const metaCards = [
        buildModpackMeta("Minecraft", manifest.minecraftVersion || config.serverVersion || "1.21.1"),
        buildModpackMeta("Loader", manifest.loader || "Fabric"),
        buildModpackMeta("Файлов", String(files.length || 0)),
        buildModpackMeta("Размер", formatMegabytes(modpack.size || 0)),
        buildModpackMeta("SHA1", shortSha(modpack.sha1)),
        buildModpackMeta("Обновлён", modpack.modified ? formatDate(modpack.modified * 1000) : "нет данных"),
      ];
      replaceChildrenSafe(modpackMetaGrid, metaCards);
    }

    if (modpackFileGrid) {
      if (!available || !files.length) {
        replaceChildrenSafe(modpackFileGrid, [
          (() => {
            const card = makeElement("article", "modpack-file-card");
            card.append(
              makeElement("span", "modpack-file-badge", "mods"),
              makeElement("strong", "", "Архив пока недоступен"),
              makeElement("p", "", "Как только backend увидит готовый zip и manifest, список client jars появится здесь автоматически."),
            );
            return card;
          })(),
        ]);
      } else {
        const cards = files.map((file) => {
          const card = makeElement("article", "modpack-file-card");
          const badge = makeElement("span", "modpack-file-badge", "mods");
          const title = makeElement("strong", "", String(file.component || file.path || "Мод"));
          const copy = makeElement(
            "p",
            "",
            `${String(file.version || "без версии")} · ${String(file.license || "лицензия не указана")}`,
          );
          const meta = makeElement("div", "modpack-file-meta");
          meta.append(
            makeElement("span", "", String(file.path || "mods/unknown.jar")),
          );
          if (file.source) {
            const sourceChip = makeElement("span", "", "есть source");
            sourceChip.title = String(file.source);
            meta.append(sourceChip);
          }
          card.append(badge, title, copy, meta);
          return card;
        });
        replaceChildrenSafe(modpackFileGrid, cards);
      }
    }

    if (modpackNotes) {
      const noteCards = (notes.length ? notes : [
        available
          ? "Manifest не вернул дополнительных заметок. Это допустимо, если архив уже собран и проверен."
          : "После появления архива здесь отобразятся примечания по клиентской сборке и опциональным модам.",
      ]).map((note) => {
        const card = makeElement("article", "modpack-note-card");
        card.append(
          makeElement("strong", "", "Примечание"),
          makeElement("p", "", String(note)),
        );
        return card;
      });
      replaceChildrenSafe(modpackNotes, noteCards);
    }

    if (downloadModsBtn && available) {
      downloadModsBtn.href = downloadUrl;
    }
  }

  function renderServerHero(config = {}, status = {}, modpack = {}) {
    const server = status.server || {};
    const serverAddress = String(config.serverAddress || "").trim();
    const address = serverAddress || "Адрес сервера пока не указан";
    const onlineText = server.online
      ? `Сервер онлайн, ${formatPlayers(server)}`
      : "Сервер сейчас офлайн или не отвечает";
    const pulseText = server.online
      ? `${onlineText}, задержка ${formatLatency(server.latencyMs)}`
      : "Backend не получил подтверждение онлайн-статуса";

    if (serverIpText) {
      serverIpText.textContent = address;
      serverIpText.dataset.serverAddress = serverAddress;
    }
    if (serverPulseText) {
      serverPulseText.textContent = pulseText;
    }
    if (downloadModsBtn) {
      if (modpack.available) {
        const sizeText = formatMegabytes(modpack.size || 0);
        downloadModsBtn.href = modpack.downloadUrl || config.modpackDownloadPath || "/downloads/CopiMineMods.zip";
        downloadModsBtn.textContent = `Скачать моды (${sizeText})`;
        downloadModsBtn.classList.remove("btn-disabled");
        downloadModsBtn.removeAttribute("aria-disabled");
      } else {
        downloadModsBtn.href = "#mods";
        downloadModsBtn.textContent = "Архив модов готовится";
        downloadModsBtn.classList.add("btn-disabled");
        downloadModsBtn.setAttribute("aria-disabled", "true");
      }
    }
    renderModpack(modpack, config);
  }

  function renderStatus(status = {}, config = {}) {
    if (!statusGrid) return;
    const server = status.server || {};
    const elections = status.elections || {};
    const cards = [
      cardStrong("Сервер", server.online ? "Онлайн" : "Оффлайн", formatLatency(server.latencyMs)),
      cardStrong("Игроки", formatPlayers(server), server.playerListAvailable ? "Список игроков подтверждён" : "Список игроков временно недоступен"),
      cardStrong("Выборы", elections.title || "Выборы CopiMine", elections.active ? `Кандидатов: ${Number(elections.candidates || 0)}` : "Сейчас нет активной кампании"),
      cardStrong("Resource pack", config.resourcePackRequired ? "Обязателен" : "Опционален", config.serverVersion ? `Версия ${config.serverVersion}` : ""),
    ];
    replaceChildrenSafe(statusGrid, cards);
  }

  function renderOnline(server = {}) {
    if (!onlineBoard) return;
    const players = Array.isArray(server.samplePlayers) ? server.samplePlayers.filter(Boolean) : [];
    if (!players.length) {
      replaceChildrenSafe(onlineBoard, [
        cardStrong("Игроки онлайн", "Список игроков пока недоступен", server.online ? "RCON не вернул актуальный список" : "Сервер не ответил"),
      ]);
      return;
    }
    const cards = players.slice(0, 12).map((player) => cardStrong("Онлайн", String(player)));
    replaceChildrenSafe(onlineBoard, cards);
  }

  function renderHistory(items = []) {
    if (!historyMount) return;
    const rows = Array.isArray(items) ? items : [];
    if (!rows.length) {
      replaceChildrenSafe(historyMount, [
        cardStrong("История пока пустая", "Публичных операций ещё нет", "Как только появятся доходы или выплаты, они появятся здесь."),
      ]);
      return;
    }
    const cards = rows.slice(0, 6).map((row) => {
      const card = makeElement("article", "treasury-history-card");
      const head = makeElement("div", "treasury-history-row");
      head.append(
        makeElement("span", "treasury-history-type", String(row.label || row.type || "Операция")),
        makeElement("strong", "", formatAr(row.amount || row.amount_ar || 0)),
      );
      card.append(
        head,
        makeElement("p", "", String(row.comment || row.item_name || row.public_actor_name || row.actor || "Публичная операция казны")),
        makeElement("span", "treasury-history-date", formatDate(row.createdAt || row.created_at)),
      );
      return card;
    });
    replaceChildrenSafe(historyMount, cards);
  }

  function renderPresidentCard(president = {}) {
    if (!presidentName || !presidentMeta) return;
    const name = String(president.current_president_name || president.ownerName || "").trim();
    const uuid = String(president.current_president_uuid || president.ownerUuid || "").trim();
    if (!name) {
      presidentName.textContent = "Президент пока не избран";
      presidentMeta.textContent = "Карточка появится автоматически после подтверждения активного президентского срока.";
      skinShell?.classList.add("hidden");
      if (skinImage) skinImage.removeAttribute("src");
      return;
    }
    presidentName.textContent = name;
    presidentMeta.textContent = "Карточка использует только публичный API и не показывает служебные данные.";
    if (!uuid || !skinImage || !skinShell) {
      skinShell?.classList.add("hidden");
      return;
    }
    skinImage.src = `/api/public/president/skin/body?uuid=${encodeURIComponent(uuid)}`;
    skinImage.alt = `Скин президента ${name}`;
    skinShell.classList.remove("hidden");
  }

  function renderBudget(payload = {}) {
    const balance = Number(payload.balance_ar ?? payload.balance ?? 0);
    const currentPresidentName = String(payload.current_president_name || payload.ownerName || "").trim();
    if (budgetOwner) {
      budgetOwner.textContent = currentPresidentName
        ? `Казной управляет ${currentPresidentName}`
        : "Пока без активного президента";
    }
    if (budgetDetail) {
      budgetDetail.textContent = payload.updated_at || payload.updatedAt
        ? `Публичные данные обновлены ${formatDate(payload.updated_at || payload.updatedAt)}`
        : "Источник не вернул время обновления, поэтому показываем только подтверждённый баланс.";
    }
    animateCounter(balance);
    renderPresidentCard(payload);
  }

  function renderStatusPayload(detail = {}) {
    const status = detail.status || detail || {};
    const config = detail.config || {};
    const modpack = detail.modpack || {};
    const treasury = status.treasury || {};
    renderServerHero(config, status, modpack);
    renderStatus(status, config);
    renderOnline(status.server || {});
    renderBudget({
      balance_ar: treasury.balance,
      current_president_name: treasury.ownerName || status.elections?.president || "",
      current_president_uuid: treasury.ownerUuid || "",
      updated_at: status.generatedAt || Date.now(),
    });
    renderHistory((treasury.history || []).map((row) => ({
      ...row,
      public_actor_name: row.actor || "",
      created_at: row.createdAt || row.created_at || 0,
    })));
  }

  function renderAuthState(auth = {}) {
    const role = String(auth.role || "");
    const authed = Boolean(role || auth.cookieAuth);
    if (cabinetButton) {
      cabinetButton.classList.toggle("hidden", !authed);
      cabinetButton.dataset.routeTarget = authed ? roleRoute(role) : "";
      cabinetButton.textContent = roleLabel(role);
    }
    cabinetRoleCards.forEach((card) => {
      const cardRole = String(card.getAttribute("data-cabinet-role") || "");
      const active = Boolean(
        role &&
        (role === cardRole || (role === "owner" && cardRole === "admin")),
      );
      card.classList.toggle("cabinet-role-active", active);
    });
  }

  function renderUnavailableState() {
    if (budgetDetail) {
      budgetDetail.textContent = "Источник публичной казны временно недоступен. Попробуйте позже.";
    }
    if (serverPulseText) {
      serverPulseText.textContent = "Публичный backend временно не ответил. Сайт не показывает выдуманные данные.";
    }
    if (modpackSummaryLead) {
      modpackSummaryLead.textContent = "Публичный backend не вернул сведения по модпаку. Сайт не подменяет это фейковыми карточками.";
    }
  }

  function setSkinRotation(nextRotation) {
    if (!skinTilt) return;
    currentRotation = nextRotation;
    skinTilt.style.transform = `rotateX(4deg) rotateY(${nextRotation}deg)`;
  }

  function bindSkinControls() {
    if (leftButton && leftButton.dataset.bound !== "true") {
      leftButton.dataset.bound = "true";
      leftButton.addEventListener("click", () => setSkinRotation(currentRotation - 18));
    }
    if (rightButton && rightButton.dataset.bound !== "true") {
      rightButton.dataset.bound = "true";
      rightButton.addEventListener("click", () => setSkinRotation(currentRotation + 18));
    }
    if (skinImage && skinImage.dataset.bound !== "true") {
      skinImage.dataset.bound = "true";
      skinImage.addEventListener("error", () => {
        skinShell?.classList.add("hidden");
        skinImage.removeAttribute("src");
      });
    }
  }

  bindFeatureTabs();
  bindSkinControls();
  bindCabinetButton();
  setSkinRotation(currentRotation);

  return {
    renderBudget,
    renderHistory,
    renderStatus,
    renderOnline,
    renderStatusPayload,
    renderServerHero,
    renderPresidentCard,
    renderFeaturePanel,
    renderAuthState,
    renderUnavailableState,
    renderModpack,
  };
}
