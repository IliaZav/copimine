import { loadPublicEventsPageData } from "./site-data.js?v=20260830events1";

const LOCAL_ASSET_PREFIX = "/assets/events/";
const CURRENT_EVENT_LABEL = "Сейчас";
const UPCOMING_EVENT_LABEL = "Скоро";

function node(tag, className = "", text = "") {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== "") element.textContent = String(text);
  return element;
}

function text(value, fallback = "") {
  const result = String(value ?? "").trim();
  return result || fallback;
}

function localAsset(value) {
  const path = text(value);
  return path.startsWith(LOCAL_ASSET_PREFIX) && !path.includes("..") && !/[<>"']/.test(path) ? path : "";
}

function externalCredit(value) {
  try {
    const url = new URL(String(value || ""));
    if (url.protocol !== "https:" || !["commons.wikimedia.org", "www.minecraft.net"].includes(url.hostname)) return "";
    return url.href;
  } catch (_) {
    return "";
  }
}

function setImage(image, source, alt) {
  const safe = localAsset(source);
  if (!safe) return false;
  image.src = safe;
  image.alt = text(alt);
  image.loading = "lazy";
  return true;
}

function sectionHeading(kicker, title, copy = "") {
  const head = node("div", "event-section-head");
  head.append(node("span", "event-kicker", kicker), node("h2", "", title));
  if (copy) head.append(node("p", "", copy));
  return head;
}

function statusLabel(event) {
  return text(event?.status).toLowerCase() === "current" ? CURRENT_EVENT_LABEL : UPCOMING_EVENT_LABEL;
}

function eventSwitcher(events, selectedSlug, onSelect) {
  const section = node("section", "event-switcher");
  section.append(sectionHeading("Календарь", "Выбери событие", "Текущая страница остаётся одной — меняется только содержание."));
  const list = node("div", "event-switcher-list");
  events.forEach((event) => {
    const button = node("button", `event-switcher-card${event.slug === selectedSlug ? " is-active" : ""}`);
    button.type = "button";
    button.dataset.eventSlug = text(event.slug);
    button.setAttribute("aria-pressed", event.slug === selectedSlug ? "true" : "false");
    button.style.setProperty("--event-accent", text(event.accent, "#b887ff"));
    const badge = node("span", "event-switcher-status", statusLabel(event));
    const title = node("strong", "", text(event.title, "Событие"));
    const summary = node("small", "", text(event.summary, "Подробности появятся позже."));
    button.append(badge, title, summary);
    button.addEventListener("click", () => onSelect(text(event.slug)));
    list.append(button);
  });
  section.append(list);
  return section;
}

function buildHero(event) {
  const hero = node("section", "event-hero");
  hero.style.setProperty("--event-accent", text(event.accent, "#b887ff"));
  hero.style.setProperty("--event-hero-image", `url("${localAsset(event.heroImage)}")`);
  const backdrop = node("div", "event-hero-backdrop");
  const grid = node("div", "event-hero-grid");
  const copy = node("div", "event-hero-copy");
  const status = node("div", "event-hero-meta");
  status.append(node("span", "event-live-dot"), node("span", "", statusLabel(event)));
  copy.append(status, node("p", "event-kicker", text(event.eyebrow, "Событие")), node("h1", "", text(event.title, "Событие")));
  copy.append(node("p", "event-hero-summary", text(event.summary, "Подробности появятся позже.")));
  if (text(event.body)) copy.append(node("p", "event-hero-body", text(event.body)));
  const visual = node("figure", "event-hero-visual");
  const landscape = node("img", "event-hero-landscape");
  if (!setImage(landscape, event.heroImage, `Кадр события «${text(event.title, "Ивенты")}»`)) landscape.remove();
  visual.append(landscape);
  const portrait = node("img", "event-hero-portrait");
  if (setImage(portrait, event.portraitImage, "Эндермен")) visual.append(portrait);
  visual.append(node("figcaption", "event-hero-caption", "Кадры Энда · атмосфера события"));
  grid.append(copy, visual);
  hero.append(backdrop, grid);
  return hero;
}

function buildRequirements(event) {
  const section = node("section", "event-section event-requirements");
  section.append(sectionHeading("Перед входом", "Собери ресурсы", "Это список для подготовки команды к запуску Разлома."));
  const grid = node("div", "event-requirement-grid");
  (Array.isArray(event.requirements) ? event.requirements : []).forEach((item) => {
    const card = node("article", "event-requirement-card");
    card.append(node("strong", "", text(item.value, "—")), node("span", "", text(item.name, "Ресурс")));
    grid.append(card);
  });
  section.append(grid);
  return section;
}

function buildWaves(event) {
  const section = node("section", "event-section");
  section.append(sectionHeading("Шесть волн", "Как идёт рейд", "Темп нарастает постепенно. Финальный бой начинается только после последней волны."));
  const grid = node("div", "event-wave-grid");
  (Array.isArray(event.waves) ? event.waves : []).forEach((wave) => {
    const card = node("article", "event-wave-card");
    card.append(node("span", "event-wave-number", String(wave.number || "")), node("h3", "", text(wave.name, "Волна")), node("p", "", text(wave.description)));
    grid.append(card);
  });
  section.append(grid);
  return section;
}

function buildBoss(event) {
  const section = node("section", "event-section event-boss-section");
  section.append(sectionHeading("Финальный бой", "Пять фаз босса", "Смотри на здоровье и держи команду рядом — у каждой фазы свой ритм."));
  const grid = node("div", "event-boss-grid");
  (Array.isArray(event.bossPhases) ? event.bossPhases : []).forEach((phase, index) => {
    const card = node("article", "event-boss-card");
    card.append(node("span", "event-boss-index", `0${index + 1}`.slice(-2)), node("h3", "", text(phase.name, "Фаза")), node("strong", "", text(phase.range)), node("p", "", text(phase.description)));
    grid.append(card);
  });
  section.append(grid);
  return section;
}

function buildRewards(event) {
  const section = node("section", "event-section event-rewards-section");
  const grid = node("div", "event-rewards-layout");
  const copy = node("div", "event-rewards-copy");
  copy.append(sectionHeading("После победы", "Награды достанутся участникам", "После настоящего финала награды выдаются команде, а проход в Энд остаётся открытым."));
  const list = node("ul", "event-reward-list");
  (Array.isArray(event.rewards) ? event.rewards : []).forEach((reward) => list.append(node("li", "", text(reward))));
  copy.append(list);
  const note = node("aside", "event-reward-note");
  note.append(node("span", "event-reward-note-mark", "END"), node("strong", "", "Разлом закрывается после финала"), node("p", "", "Сначала система завершает выдачу. Затем путь остаётся доступным для игроков."));
  grid.append(copy, note);
  section.append(grid);
  return section;
}

function buildVideos(event) {
  const section = node("section", "event-section event-video-section");
  section.append(sectionHeading("Материалы", "Видео события", "Здесь появятся записи рейда и короткие фрагменты с поля."));
  const videos = Array.isArray(event.videos) ? event.videos : [];
  const grid = node("div", "event-video-grid");
  videos.forEach((item) => {
    const url = localAsset(item.url) || externalCredit(item.url);
    if (!url) return;
    const card = node("article", "event-video-card");
    const video = node("video", "event-video");
    video.controls = true;
    video.preload = "metadata";
    const poster = localAsset(item.posterUrl);
    if (poster) video.poster = poster;
    const source = node("source");
    source.src = url;
    source.type = text(item.mimeType, "video/mp4");
    video.append(source);
    card.append(video, node("h3", "", text(item.title, "Видео")));
    grid.append(card);
  });
  if (!grid.children.length) {
    const empty = node("div", "event-video-empty");
    empty.append(node("span", "event-video-empty-icon", "▶"), node("strong", "", "Видео появится здесь"), node("p", "", "Администратор сможет добавить запись в CMS, когда материал будет готов."));
    grid.append(empty);
  }
  section.append(grid);
  return section;
}

function buildCredits(event) {
  const credits = node("footer", "event-credits");
  const label = node("span", "", text(event.creditsHtml, "Материалы события"));
  credits.append(label);
  const links = node("span", "event-credit-links");
  (Array.isArray(event.credits) ? event.credits : []).forEach((credit) => {
    const url = externalCredit(credit.url);
    if (!url) return;
    const link = node("a", "", text(credit.label, "Источник"));
    link.href = url;
    link.target = "_blank";
    link.rel = "noreferrer noopener";
    links.append(link);
  });
  if (links.children.length) credits.append(links);
  return credits;
}

function buildUpcoming(event) {
  const section = node("section", "event-section event-upcoming");
  section.append(node("span", "event-upcoming-symbol", "✦"), node("p", "event-kicker", "Скоро"), node("h2", "", text(event.title, "Новое событие")), node("p", "event-upcoming-copy", text(event.body, "Событие ещё готовится.")), node("p", "event-upcoming-note", "Когда появятся дата и правила, они будут здесь — коротко и без лишнего шума."));
  return section;
}

function render(payload, selectedSlug) {
  const mount = document.getElementById("eventsPage");
  if (!mount) return;
  const events = Array.isArray(payload?.events) ? payload.events : [];
  const active = events.find((event) => event.slug === selectedSlug) || events.find((event) => event.status === "current") || events[0];
  if (!active) {
    mount.replaceChildren(node("p", "event-empty", "События пока недоступны."));
    return;
  }
  const safeSlug = text(active.slug);
  mount.replaceChildren();
  mount.className = `events-page events-page-${safeSlug}`;
  mount.append(buildHero(active), eventSwitcher(events, safeSlug, (slug) => {
    const next = new URL(window.location.href);
    next.searchParams.set("event", slug);
    window.history.pushState({}, "", next);
    render(payload, slug);
  }));
  if (active.status === "current") {
    mount.append(buildRequirements(active), buildWaves(active), buildBoss(active), buildRewards(active));
  } else {
    mount.append(buildUpcoming(active));
  }
  mount.append(buildVideos(active), buildCredits(active));
}

export async function initEventsPage() {
  const mount = document.getElementById("eventsPage");
  if (!mount) return;
  mount.append(node("div", "event-loading", "Загружаем события…"));
  const payload = await loadPublicEventsPageData();
  const requested = new URLSearchParams(window.location.search).get("event") || "";
  render(payload, requested);
  window.addEventListener("popstate", () => render(payload, new URLSearchParams(window.location.search).get("event") || ""));
}
