import { loadPublicEventsPageData } from "./site-data.js?v=20260831events2";

const LOCAL_ASSET_PREFIX = "/assets/events/";
const EVENT_ORDER = ["end-rift", "future-1", "future-2"];
const CURRENT_EVENT_LABEL = "Сейчас";
const UPCOMING_EVENT_LABEL = "Скоро";

const EVENT_VIEW_COPY = {
  "end-rift": {
    status: "current",
    eyebrow: "Энд",
    title: "Разлом Энда",
    summary: "В Энде снова не тихо.",
    body: "Большой проработанный данж. Волны врагов. Сильный проработанный босс.",
    accent: "#c09aff",
    sceneImage: "/assets/events/end-rift/end-city.jpg",
    dragonImage: "/assets/events/end-rift/end-landscape.png",
    portraitImage: "/assets/events/end-rift/enderman.png",
    creditsHtml: "Реальный игровой кадр с Эндер-драконом: Wikimedia Commons, CC BY 3.0",
    credits: [
      { label: "Кадр с драконом", url: "https://commons.wikimedia.org/wiki/File:Screenshot_from_the_Minecraft_End.png" },
      { label: "Кадр города Края", url: "https://commons.wikimedia.org/wiki/File:Minecraft_-_End_city.jpg" },
      { label: "Кадр эндермена", url: "https://commons.wikimedia.org/wiki/File:Minecraft_Enderman.png" },
    ],
  },
  "future-1": {
    status: "upcoming",
    eyebrow: "Скоро",
    title: "Скоро",
    summary: "Здесь появится следующее событие.",
    body: "Пока без названия и без дат.",
    accent: "#8e89ff",
    sceneImage: "/assets/events/end-rift/end-city.jpg",
    dragonImage: "/assets/events/end-rift/end-landscape.png",
    portraitImage: "",
    creditsHtml: "",
    credits: [],
  },
  "future-2": {
    status: "upcoming",
    eyebrow: "Скоро",
    title: "Скоро",
    summary: "Ещё одно событие появится здесь.",
    body: "Пока без названия и без дат.",
    accent: "#a987e8",
    sceneImage: "/assets/events/end-rift/end-landscape.png",
    dragonImage: "/assets/events/end-rift/end-landscape.png",
    portraitImage: "",
    creditsHtml: "",
    credits: [],
  },
};

const MYSTERY_NOTES = [
  ["01", "Большой проработанный данж"],
  ["02", "Волны врагов"],
  ["03", "Сильный проработанный босс"],
];

function node(tag, className = "", value = "") {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (value !== "") element.textContent = String(value);
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

function setImage(image, source, alt = "") {
  const safe = localAsset(source);
  if (!safe) return false;
  image.src = safe;
  image.alt = text(alt);
  image.decoding = "async";
  image.loading = "lazy";
  return true;
}

function sectionHeading(kicker, title, copy = "") {
  const head = node("div", "event-section-head");
  head.append(node("span", "event-kicker", kicker), node("h2", "", title));
  if (copy) head.append(node("p", "", copy));
  return head;
}

function createClock(size = "") {
  const clock = node("span", `event-clock ${size}`.trim());
  clock.setAttribute("aria-hidden", "true");
  for (let index = 0; index < 12; index += 1) {
    const mark = node("span", "event-clock-mark");
    mark.style.setProperty("--clock-step", String(index));
    clock.append(mark);
  }
  clock.append(
    node("span", "event-clock-hand event-clock-hand-hour"),
    node("span", "event-clock-hand event-clock-hand-minute"),
    node("span", "event-clock-hand event-clock-hand-second"),
    node("span", "event-clock-pin"),
  );
  return clock;
}

function buildVines(zone = "") {
  const vines = node("div", `event-vines ${zone}`.trim());
  vines.setAttribute("aria-hidden", "true");
  [
    ["7%", "138px", "-8deg", "0s"],
    ["19%", "86px", "5deg", "-2.4s"],
    ["39%", "172px", "-4deg", "-5.5s"],
    ["63%", "112px", "7deg", "-1.2s"],
    ["82%", "152px", "-6deg", "-4s"],
    ["94%", "74px", "4deg", "-7s"],
  ].forEach(([left, height, rotation, delay]) => {
    const vine = node("span", "event-vine");
    vine.style.setProperty("--vine-left", left);
    vine.style.setProperty("--vine-height", height);
    vine.style.setProperty("--vine-rotation", rotation);
    vine.style.setProperty("--vine-delay", delay);
    vines.append(vine);
  });
  return vines;
}

function getEventRecord(payload, slug) {
  const records = Array.isArray(payload?.events) ? payload.events : [];
  const record = records.find((event) => text(event?.slug) === slug) || {};
  const copy = EVENT_VIEW_COPY[slug] || EVENT_VIEW_COPY["future-1"];
  const videos = Array.isArray(record.videos) ? record.videos : [];
  return { ...copy, slug, videos };
}

function eventStatus(event) {
  return event.status === "current" ? CURRENT_EVENT_LABEL : UPCOMING_EVENT_LABEL;
}

function buildCalendar(payloadEvents, selectedSlug, onSelect) {
  const section = node("section", "event-calendar-stage event-reveal");
  section.dataset.reveal = "calendar";
  const calendar = node("div", "event-calendar");
  const dial = node("div", "event-calendar-dial");
  dial.append(
    node("span", "event-calendar-dial-ring"),
    node("span", "event-calendar-dial-label", "без дат"),
    createClock("event-clock-large"),
    node("span", "event-calendar-dial-caption", "время идёт"),
  );

  const content = node("div", "event-calendar-content");
  content.append(
    node("span", "event-kicker", "Расписание"),
    node("h1", "", "Ивенты"),
    node("p", "event-calendar-lead", "Сейчас открыто одно событие."),
  );

  const slots = node("div", "event-calendar-slots");
  EVENT_ORDER.forEach((slug, index) => {
    const event = getEventRecord({ events: payloadEvents }, slug);
    const card = node("button", `event-calendar-card${selectedSlug === slug ? " is-selected" : ""}`);
    card.type = "button";
    card.dataset.eventSlug = slug;
    card.style.setProperty("--event-accent", event.accent);
    card.setAttribute("aria-label", `${eventStatus(event)}: ${event.title}`);
    const art = node("span", "event-card-art");
    art.setAttribute("aria-hidden", "true");
    const artImage = node("img");
    if (setImage(artImage, event.sceneImage)) art.append(artImage);
    const label = node("span", "event-calendar-card-label", eventStatus(event));
    const miniClock = createClock("event-clock-mini");
    const title = node("strong", "event-calendar-card-title", event.title);
    const summary = node("span", "event-calendar-card-copy", event.summary);
    const arrow = node("span", "event-calendar-card-arrow", "↗");
    card.append(art, label, miniClock, title, summary, arrow);
    card.addEventListener("click", () => onSelect(slug));
    slots.append(card);
    if (index === 0) card.dataset.current = "true";
  });

  const note = node("p", "event-calendar-note", "Даты появятся позже.");
  content.append(slots, note);
  calendar.append(dial, content);
  section.append(buildVines("event-vines-calendar"), calendar);
  return section;
}

function buildBackButton(onBack) {
  const wrap = node("div", "event-detail-toolbar event-reveal");
  wrap.dataset.reveal = "back";
  const button = node("button", "event-back", "← Все события");
  button.type = "button";
  button.addEventListener("click", onBack);
  wrap.append(button);
  return wrap;
}

function buildHero(event) {
  const hero = node("section", "event-hero event-reveal");
  hero.dataset.reveal = "hero";
  hero.style.setProperty("--event-accent", event.accent);

  const scene = node("img", "event-hero-scene");
  scene.setAttribute("aria-hidden", "true");
  if (!setImage(scene, event.sceneImage)) scene.remove();

  const dragon = node("img", "event-dragon-flight");
  dragon.setAttribute("aria-hidden", "true");
  if (!setImage(dragon, event.dragonImage)) dragon.remove();

  const veil = node("span", "event-hero-veil");
  const glow = node("span", "event-hero-glow");
  const content = node("div", "event-hero-content");
  const meta = node("div", "event-hero-meta");
  meta.append(node("span", "event-live-dot"), node("span", "", eventStatus(event)));
  content.append(meta, node("p", "event-kicker", event.eyebrow), node("h1", "", event.title));
  content.append(node("p", "event-hero-summary", event.summary), node("p", "event-hero-body", event.body));

  const stamp = node("div", "event-hero-stamp");
  stamp.append(createClock("event-clock-small"), node("span", "", "без точной даты"));
  content.append(stamp);
  hero.append(scene, dragon, buildVines("event-vines-hero"), veil, glow, content);
  return hero;
}

function buildMystery() {
  const section = node("section", "event-section event-mystery event-reveal");
  section.dataset.reveal = "mystery";
  section.append(sectionHeading("Намёки", "Дальше — тишина.", "Три вещи уже известны."));
  const grid = node("div", "event-mystery-grid");
  MYSTERY_NOTES.forEach(([index, title], position) => {
    const card = node("article", "event-mystery-card event-reveal");
    card.dataset.reveal = `mystery-${position}`;
    card.style.setProperty("--event-delay", `${position * 90}ms`);
    card.append(node("span", "event-mystery-index", index), node("strong", "", title), node("span", "event-mystery-line", "Детали скрыты."));
    grid.append(card);
  });
  section.append(grid);
  return section;
}

function buildGallery(event) {
  const section = node("section", "event-section event-gallery event-reveal");
  section.dataset.reveal = "gallery";
  section.append(sectionHeading("Кадры", "Кадры Энда", "Из игры."));
  const grid = node("div", "event-gallery-grid");
  const images = [
    [event.dragonImage, "Эндер-дракон над островами Энда", "event-gallery-item event-gallery-item-wide"],
    [event.sceneImage, "Город Края в темноте", "event-gallery-item"],
    [event.portraitImage, "Эндермен", "event-gallery-item"],
  ];
  images.forEach(([source, alt, className], index) => {
    const figure = node("figure", `${className} event-reveal`);
    figure.dataset.reveal = `image-${index}`;
    figure.style.setProperty("--event-delay", `${index * 100}ms`);
    const image = node("img");
    if (!setImage(image, source, alt)) {
      figure.remove();
      return;
    }
    figure.append(image, node("figcaption", "", alt));
    grid.append(figure);
  });
  section.append(grid);
  return section;
}

function buildClockStrip() {
  const section = node("section", "event-clock-strip event-reveal");
  section.dataset.reveal = "clock";
  const clocks = node("div", "event-clock-cluster");
  clocks.append(createClock("event-clock-medium"), createClock("event-clock-small"), createClock("event-clock-mini"));
  const copy = node("div", "event-clock-copy");
  copy.append(node("span", "event-kicker", "Часы идут"), node("h2", "", "Дата скрыта."));
  section.append(clocks, copy);
  return section;
}

function buildVideos(event) {
  const section = node("section", "event-section event-video-section event-reveal");
  section.dataset.reveal = "videos";
  section.append(sectionHeading("Видео", "Запись события"));
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
    card.append(video, node("h3", "", text(item.title, "Видео события")));
    grid.append(card);
  });
  if (!grid.children.length) {
    const empty = node("div", "event-video-empty");
    empty.append(createClock("event-clock-small"), node("strong", "", "Записи пока нет."));
    grid.append(empty);
  }
  section.append(grid);
  return section;
}

function buildCredits(event) {
  const credits = node("footer", "event-credits event-reveal");
  credits.dataset.reveal = "credits";
  credits.append(node("span", "", text(event.creditsHtml, "Материалы события")));
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
  const section = node("section", "event-upcoming event-reveal");
  section.dataset.reveal = "upcoming";
  section.style.setProperty("--event-accent", event.accent);
  section.append(createClock("event-clock-medium"), node("p", "event-kicker", event.eyebrow), node("h1", "", "Скоро"), node("p", "event-upcoming-copy", event.summary), node("p", "event-upcoming-note", "Название и дата — позже."));
  return section;
}

let revealObserver = null;

function initReveal(mount) {
  revealObserver?.disconnect();
  const items = [...mount.querySelectorAll("[data-reveal]")];
  mount.dataset.motion = "ready";
  const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches === true;
  if (reduced || !("IntersectionObserver" in window)) {
    items.forEach((item) => item.classList.add("is-visible"));
    return;
  }
  revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add("is-visible");
      revealObserver?.unobserve(entry.target);
    });
  }, { threshold: 0.12, rootMargin: "0px 0px -8%" });
  items.forEach((item) => revealObserver.observe(item));
}

function mergeEvents(payload) {
  return EVENT_ORDER.map((slug) => getEventRecord(payload, slug));
}

function render(payload, selectedSlug, navigate) {
  const mount = document.getElementById("eventsPage");
  if (!mount) return;
  const events = mergeEvents(payload);
  const validSlug = EVENT_ORDER.includes(selectedSlug) ? selectedSlug : "";
  mount.className = `events-page${validSlug ? ` events-page-detail events-page-${validSlug}` : " events-page-calendar"}`;
  mount.replaceChildren();
  if (!validSlug) {
    document.title = "Ивенты · CopiMine";
    mount.append(buildCalendar(events, "", navigate));
    initReveal(mount);
    return;
  }

  const active = events.find((event) => event.slug === validSlug) || events[0];
  document.title = `${active.title} · CopiMine`;
  mount.append(buildBackButton(() => navigate("")));
  if (active.status === "current") {
    mount.append(buildHero(active), buildMystery(), buildGallery(active), buildClockStrip(), buildVideos(active), buildCredits(active));
  } else {
    mount.append(buildUpcoming(active), buildClockStrip());
  }
  initReveal(mount);
}

export async function initEventsPage() {
  const mount = document.getElementById("eventsPage");
  if (!mount) return;
  mount.append(node("div", "event-loading", "Загружаем события…"));
  const payload = await loadPublicEventsPageData();
  const navigate = (slug) => {
    const next = new URL(window.location.href);
    if (slug) next.searchParams.set("event", slug);
    else next.searchParams.delete("event");
    window.history.pushState({}, "", next);
    render(payload, slug, navigate);
  };
  render(payload, new URLSearchParams(window.location.search).get("event") || "", navigate);
  window.addEventListener("popstate", () => render(payload, new URLSearchParams(window.location.search).get("event") || "", navigate));
}
