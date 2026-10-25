import { useLayoutEffect, useRef, useState } from "react";
import { createRoot, type Root } from "react-dom/client";
import type { AnalyticsModel } from "./charts";
import { CoverflowGallery, type GallerySlide } from "./coverflow-gallery";
import { RisingLines } from "./rising-lines";

type LegacyRouteModel = {
  page: string;
  tab: string;
  raw: string;
  html: string;
  header: string;
  footer: string;
  dock: string;
  revision: number;
  same: boolean;
  scrollY: number;
  sideScroll: number;
  focusId?: string;
  focusStart?: number | null;
  analytics?: AnalyticsModel | null;
  serviceMetrics?: Array<Record<string, unknown>>;
};

type CopiMineWindow = Window & {
  CM?: any;
  CM6Domain?: any;
  CMBlueMap?: any;
  CMAccountLink?: { state: () => { user?: any; pending?: { name?: string; expiresAt?: number } | null } };
  CMDomain?: { markdown?: (source: string) => string };
  CM72?: { galleryIndex?: number };
  CMAnalyticsReactModel?: AnalyticsModel | null;
  CMSiteMotion?: any;
  icon?: (name: string, className?: string) => string;
  CopimineReactBridge?: { render: (model: LegacyRouteModel) => void };
};

declare const window: CopiMineWindow;

const iconMarkup = (name: string, className = "") => {
  const html = window.icon?.(name, className) || "";
  return <span className={className} aria-hidden="true" dangerouslySetInnerHTML={{ __html: html }} />;
};

function ServerHero() {
  const model = window.CM?.state || {};
  const config = model.config || {};
  const heroRef = useRef<HTMLElement | null>(null);
  const [paused, setPaused] = useState(false);
  const photos = [
    { id: "copimine-night-wide", src: "assets/photos/copimine-night.webp", position: "42% 25%" },
    { id: "copimine-night-pan", src: "assets/photos/copimine-night.webp", position: "64% 59%" },
  ];
  const lines = String(config.headline || "CopiMine —\nMinecraft-сервер.").split(/\r?\n/);
  const first = lines.shift()?.trim() || "CopiMine";
  const second = lines.join(" ").trim();
  const address = config.serverIp || "copimine.ru";

  useLayoutEffect(() => window.CMSiteMotion?.heroAtmosphere?.(heroRef.current), []);

  return (
    <section ref={heroRef} className="cm72-hero cm-react-hero">
      <div className="cm72-hero-frames" aria-hidden="true">
        <div className="cm72-hero-camera">
          {photos.map((photo, index) => <img
            key={photo.id || photo.src || index}
            className="cm72-hero-photo"
            data-hero-frame=""
            data-active={index === 0 ? "true" : "false"}
            src={window.CM.asset(photo.src || "assets/photos/copimine-night.webp")}
            style={{ objectPosition: photo.position }}
            alt=""
            loading={index === 0 ? "eager" : "lazy"}
            decoding="async"
            fetchPriority={index === 0 ? "high" : "auto"}
          />)}
        </div>
      </div>
      <RisingLines className="cm-react-hero-lines" particles={160} color="#5ce5b2" opacity={0.38} />
      <div className="cm72-hero-content">
        <h1 className="cm-react-hero-title">
          {first}{second && <><br /><span>{second}</span></>}
        </h1>
        <div className="cm-react-hero-actions">
          <button className="btn primary cm72-start" type="button" data-action="play">Начать играть {iconMarkup("arrow-right")}</button>
          <a className="btn ghost" href="#map">{iconMarkup("map")}Посмотреть карту</a>
        </div>
        <button className="cm72-address" type="button" data-action="copy-ip" aria-label={"Скопировать адрес сервера " + address}>
          <span>{address}</span>{iconMarkup("copy")}
        </button>
      </div>
      {photos.length > 1 && <button
        className="cm72-hero-motion-toggle"
        type="button"
        aria-label={paused ? "Продолжить анимацию фона" : "Приостановить анимацию фона"}
        aria-pressed={paused}
        title={paused ? "Продолжить анимацию фона" : "Приостановить анимацию фона"}
        onClick={() => {
          const nextPaused = !paused;
          if (heroRef.current) heroRef.current.dataset.heroPaused = String(nextPaused);
          setPaused(nextPaused);
        }}
      >
        {paused
          ? <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m8 5 11 7-11 7z" /></svg>
          : <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M9 5v14M15 5v14" /></svg>}
      </button>}
    </section>
  );
}

function ProductCard({ product }: { product: any }) {
  const M = window.CM;
  return (
    <article className="product reveal">
      <div className="product-image">
        <span className="tag">{product.category}</span>
        <img src={M.asset(product.image)} alt={product.name} width={86} height={86} loading="lazy" />
        <button className="icon-btn" type="button" data-action="product" data-id={product.id} aria-label={"Подробнее: " + product.name}>{iconMarkup("arrow-up-right")}</button>
      </div>
      <div className="product-body">
        <h3><button type="button" data-action="product" data-id={product.id}>{product.name}</button></h3>
        <p>{product.description}</p>
        <div className="product-bottom"><span className="price">{Number(product.price).toLocaleString("ru-RU")} <small>{product.currency}</small></span><button className="btn" type="button" data-action="add-cart" data-id={product.id}>{iconMarkup("plus")}В корзину</button></div>
      </div>
    </article>
  );
}

function MapPreview() {
  return (
    <a href="#map" className="cm72-map-image cm72-map-preview" aria-label="Открыть карту миров CopiMine">
      <img src={window.CM.asset("assets/img/map-world.webp")} alt="" loading="lazy" decoding="async" />
      <div className="cm72-map-preview-content">
        <div className="cm72-map-preview-brand"><img src={window.CM.asset("assets/img/brands/bluemap.png")} alt="BlueMap" loading="lazy" /><strong>Карта миров</strong></div>
        <span className="cm72-map-preview-link">Открыть {iconMarkup("arrow-up-right")}</span>
      </div>
    </a>
  );
}

function getGallery(): GallerySlide[] {
  return (window.CM?.state?.gallery || [])
    .filter((item: GallerySlide & { published?: boolean; kind?: string }) => item.published && item.kind !== "map" && !String(item.src || "").includes("/map/"))
    .sort((a: any, b: any) => (a.position || 0) - (b.position || 0));
}

function HomePage() {
  const M = window.CM;
  const state = M.state;
  const gallery = getGallery();
  const items = ["return_stone", "infinite_torch", "pohuy_na_debaffy_amulet", "gde_moy_lut_blyat_compass"]
    .map((id) => M.item(id))
    .filter((product: any) => product?.enabled);
  const faq = (state.faq || []).filter((entry: any) => entry.published);
  const openGalleryItem = (slide: GallerySlide) => {
    M.openModal(M.esc(slide.title || "Скриншот"), '<div class="cm72-lightbox"><img src="' + M.asset(slide.src || "") + '" alt="' + M.esc(slide.alt || slide.title || "") + '"><div class="cm72-lightbox-bottom"><p>' + M.esc(slide.caption || "") + '</p></div></div>', "cm72-lightbox-dialog");
  };

  return (
    <>
      <ServerHero />
      <CoverflowGallery slides={gallery} startIndex={window.CM72?.galleryIndex || 0} asset={M.asset} onOpen={openGalleryItem} />
      <section className="cm72-shop-section cm72-reveal cm-react-shop-section">
        <div className="cm72-section-heading"><div><h2>Магазин</h2></div><a className="text-link" href="#shop">Весь каталог {iconMarkup("arrow-right")}</a></div>
        <div className="product-grid four">{items.map((product: any) => <ProductCard key={product.id} product={product} />)}</div>
      </section>
      <section className="cm72-map-section cm72-reveal cm-react-map-section">
        <MapPreview />
      </section>
      {faq.length > 0 && <section className="cm72-faq cm72-reveal">
        <div><h2>FAQ</h2><a className="text-link" href="#faq">Все вопросы {iconMarkup("arrow-right")}</a></div>
        <div className="faq-list">{faq.map((entry: any) => <details key={entry.id || entry.question} name="cm72-faq"><summary>{entry.question}{iconMarkup("plus")}</summary><p>{entry.answer}</p></details>)}</div>
      </section>}
    </>
  );
}

function MapPage() {
  const map = window.CMBlueMap?.state?.() || {};
  const dimensions: [string, string][] = [
    ["overworld", "Обычный мир"],
    ["nether", "Незер"],
    ["end", "Энд"],
  ];
  const dimension = map.dimension || "overworld";

  return (
    <section className="cm-react-map-page" aria-labelledby="map-title">
      <header className="cm-react-map-header">
        <h1 id="map-title">Карта</h1>
        <div className="cm-react-map-service" aria-label="BlueMap">
          <img src={window.CM.asset("assets/img/brands/bluemap.png")} alt="BlueMap" />
          <span>BlueMap</span>
        </div>
      </header>
      <div className="cm-react-map-toolbar" role="toolbar" aria-label="Управление картой">
        <div className="cm-react-map-worlds" role="group" aria-label="Измерение">
          {dimensions.map(([id, name]) => (
            <button key={id} type="button" data-action="production-map-dimension" data-dimension={id} aria-pressed={dimension === id}>
              {name}
            </button>
          ))}
        </div>
        <button className="cm-react-map-fullscreen" type="button" data-action="v6-map-fullscreen" aria-label="Открыть карту на весь экран">На весь экран {iconMarkup("expand")}</button>
      </div>
      <div className="cm-react-map-stage production-map-stage" id="production-map-stage">
        {map.viewerUrl ? (
          <iframe className="production-map-frame" id="production-map-frame" src={map.viewerUrl} title={`BlueMap — ${dimensions.find(([id]) => id === dimension)?.[1] || "карта"}`} allow="fullscreen" referrerPolicy="same-origin" />
        ) : (
          <div className="cm-react-map-empty" role={map.error ? "alert" : "status"}>
            <img src={window.CM.asset("assets/img/brands/bluemap.png")} alt="" />
            <p>{map.error ? "Карта сейчас недоступна." : map.loaded ? "Карта ещё не опубликована." : "Загрузка карты…"}</p>
            {map.error && <button className="btn" type="button" data-action="production-map-retry">Повторить</button>}
          </div>
        )}
      </div>
    </section>
  );
}

function RulesPage({ html }: { html: string }) {
  const page = (window.CM?.state?.pages || []).find((item: any) => item?.slug === "rules" && item?.published);
  const published = page?.published;
  if (!published || !window.CMDomain?.markdown) return <LegacyPage html={html} />;
  return (
    <article className="cm-react-rules">
      <h1>{published.title || "Правила"}</h1>
      <div className="prose" dangerouslySetInnerHTML={{ __html: window.CMDomain.markdown(published.body || "") }} />
      <a className="cm-react-rules-support" href="#faq">Вопросы и поддержка {iconMarkup("arrow-right")}</a>
    </article>
  );
}

function MinecraftLinkPage() {
  const { user, pending } = window.CMAccountLink?.state?.() || {};
  if (!user) return <section className="cm-link-flow"><h1>Войди в аккаунт</h1><a className="btn primary" href="#auth/login">Войти</a></section>;
  if (user.gameLinkVerified) {
    return <section className="cm-link-flow cm-link-complete"><p>Привязан к аккаунту Minecraft</p><h1>{user.minecraftName || user.name}</h1><a className="btn primary" href="#account">Открыть кабинет {iconMarkup("arrow-right")}</a></section>;
  }

  return (
    <section className="cm-link-flow" aria-labelledby="cm-link-title" data-link-panel>
      <div className="cm-link-progress" aria-label={pending ? "Шаг 2 из 2: код" : "Шаг 1 из 2: ник"}>
        <span className={pending ? "is-done" : "is-current"} aria-current={pending ? undefined : "step"}><b>01</b><small>Ник</small></span><i /><span className={pending ? "is-current" : ""} aria-current={pending ? "step" : undefined}><b>02</b><small>Код</small></span>
      </div>
      <span className="kicker">Привязка аккаунта</span>
      <h1 id="cm-link-title">{pending ? "Подтверди ник в игре" : "Укажи ник Minecraft"}</h1>
      {!pending ? (
        <>
          <p>Сначала войди на сервер под нужным ником. Сайт проверит игровую сессию и отправит одноразовый код в чат.</p>
          <form data-form="production-mc-code" className="cm-link-form">
            <label className="field">Ник, с которым ты вошёл на сервер<input name="name" type="text" required pattern="[A-Za-z0-9_]{3,16}" minLength={3} maxLength={16} autoComplete="off" autoCapitalize="none" spellCheck={false} /></label>
            <div className="form-error" role="alert" />
            <button className="btn primary" type="submit">Отправить код в чат {iconMarkup("arrow-right")}</button>
          </form>
          <p className="cm-link-note">Нужен активный вход на сервер. Один игровой профиль можно связать только с одним аккаунтом сайта.</p>
        </>
      ) : (
        <>
          <p>Код отправлен в игровой чат для <strong>{pending.name}</strong>. Не выходя с сервера, открой чат, скопируй код и введи его здесь.</p>
          <ol className="cm-link-steps"><li>Оставь Minecraft запущенным под этим ником.</li><li>Введи одноразовый код из чата. После проверки личный кабинет откроется автоматически.</li></ol>
          <form data-form="production-mc-confirm" className="cm-link-form">
            <label className="field">Одноразовый код<input name="code" type="text" required minLength={6} maxLength={12} autoComplete="one-time-code" autoCapitalize="characters" /></label>
            <div className="form-error" role="alert" />
            <button className="btn primary" type="submit">Подтвердить и открыть кабинет {iconMarkup("arrow-right")}</button>
          </form>
          <p className="cm-link-status" data-link-status role="status">Код действует до {pending.expiresAt ? new Date(pending.expiresAt).toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" }) : "через 10 минут"}. Проверяем привязку автоматически.</p>
          <button className="cm-link-restart" type="button" data-action="production-mc-restart">Изменить ник или запросить новый код</button>
        </>
      )}
    </section>
  );
}

function MinecraftLinkGate() {
  const { user } = window.CMAccountLink?.state?.() || {};
  return (
    <div className="cm-account-link-gate" data-link-gate>
      <header className="cm-link-gate-header" data-link-enter>
        <img src={window.CM?.asset?.("assets/img/logo.png") || "/assets/img/logo.png"} alt="CopiMine" />
        <div className="cm-link-gate-session"><span>Вошёл как <strong>{user?.name || ""}</strong></span><button type="button" className="cm-link-gate-logout" data-action="logout">{iconMarkup("log-out")}Выйти</button></div>
      </header>
      <main id="view" className="cm-link-gate-main" aria-label="Привязка Minecraft">
        <MinecraftLinkPage />
      </main>
    </div>
  );
}

function LegacyPage({ html }: { html: string }) {
  return <div dangerouslySetInnerHTML={{ __html: html }} />;
}

function restoreNavigationState(model: LegacyRouteModel) {
  const app = document.getElementById("app");
  app?.removeAttribute("aria-busy");
  window.CM?.mountRoute?.(model.page, model.tab);
  const mobileToggle = document.querySelector(".header-actions button[aria-label='Открыть меню']");
  mobileToggle?.classList.add("mobile-nav-toggle");
  const adminNav = document.querySelector<HTMLElement>(".v71-admin-nav");
  if (adminNav && model.page === "admin") adminNav.scrollTop = model.sideScroll;
  if (model.same) {
    const focus = model.focusId ? document.getElementById(model.focusId) as (HTMLElement & { setSelectionRange?: (start: number, end: number) => void }) | null : null;
    focus?.focus({ preventScroll: true });
    if (focus && typeof model.focusStart === "number") {
      try { focus.setSelectionRange?.(model.focusStart, model.focusStart); } catch { /* Non-text controls do not expose selections. */ }
    }
  } else window.scrollTo({ top: 0, behavior: "instant" });
}

function mountReactAnalytics(model: LegacyRouteModel) {
  const host = document.getElementById("cm-react-analytics-charts");
  if (host && model.analytics) {
    const analytics = model.analytics;
    void import("./charts").then(({ AdminAnalyticsCharts }) => {
      if (!host.isConnected || document.getElementById(host.id) !== host) return;
      const root = createRoot(host);
      root.render(<AdminAnalyticsCharts model={analytics} />);
      embeddedRoots.push(root);
    }).catch(() => {
      if (host.isConnected) host.textContent = "Графики аналитики не загрузились.";
    });
    return;
  }
  const liveHost = document.getElementById("cm-react-live-chart");
  if (liveHost) {
    const analytics: AnalyticsModel = { data: { live: { samples: model.serviceMetrics || [] } }, sub: "live" };
    void import("./charts").then(({ AdminAnalyticsCharts }) => {
      if (!liveHost.isConnected || document.getElementById(liveHost.id) !== liveHost) return;
      const root = createRoot(liveHost);
      root.render(<AdminAnalyticsCharts model={analytics} />);
      embeddedRoots.push(root);
    }).catch(() => {
      if (liveHost.isConnected) liveHost.textContent = "Графики аналитики не загрузились.";
    });
  }
}

function CopiMineApp({ model }: { model: LegacyRouteModel }) {
  const accountLinkUser = window.CMAccountLink?.state?.()?.user || window.CM?.me?.();
  const accountNeedsLink = !!accountLinkUser && !accountLinkUser.gameLinkVerified;
  const gateRoot = useRef<HTMLElement | null>(null);
  useLayoutEffect(() => {
    if (accountNeedsLink) {
      document.getElementById("app")?.removeAttribute("aria-busy");
      if (model.page !== "account" || model.tab !== "link") {
        window.CM?.navTo?.("account/link");
        return;
      }
      window.CM?.mountRoute?.("account", "link");
      const root = document.querySelector<HTMLElement>("[data-link-gate]");
      if (root && root !== gateRoot.current) {
        gateRoot.current = root;
        window.CMSiteMotion?.linkGate?.(root, document.documentElement.dataset.motion !== "off");
      }
      return;
    }
    gateRoot.current = null;
    restoreNavigationState(model);
    mountReactAnalytics(model);
  }, [model.revision, accountNeedsLink]);

  if (accountNeedsLink) return <MinecraftLinkGate />;

  return (
    <>
      <div id="persistent-header" dangerouslySetInnerHTML={{ __html: model.header }} />
      <div id="page-root">
        {model.page === "admin"
          ? <LegacyPage html={model.html} />
          : <>
              <main id="view" className={model.page === "map" ? undefined : "container"}>
                {model.page === "home" ? <HomePage />
                  : model.page === "map" ? <MapPage />
                  : model.page === "rules" ? <RulesPage html={model.html} />
                  : model.page === "account" && model.tab === "link" ? <><LegacyPage html={model.html} /><MinecraftLinkPage /></>
                  : model.page === "account" ? <LegacyPage html={model.html} />
                  : <LegacyPage html={model.html} />}
              </main>
              {model.page !== "map" && <div className="container"><div dangerouslySetInnerHTML={{ __html: model.footer }} /></div>}
            </>}
      </div>
    </>
  );
}

let appRoot: Root | null = null;
let dockRoot: Root | null = null;
let embeddedRoots: Root[] = [];

export function mountCopiMineReact() {
  const app = document.getElementById("app");
  const dock = document.getElementById("dock-root");
  if (!app || !dock || appRoot) return;
  appRoot = createRoot(app);
  dockRoot = createRoot(dock);
  window.CopimineReactBridge = {
    render(model) {
      embeddedRoots.forEach((root) => root.unmount());
      embeddedRoots = [];
      model.analytics = window.CMAnalyticsReactModel || null;
      model.serviceMetrics = window.CM?.state?.serviceMetrics || [];
      appRoot?.render(<CopiMineApp model={model} />);
      const accountUser = window.CMAccountLink?.state?.()?.user || window.CM?.me?.();
      dockRoot?.render(accountUser && !accountUser.gameLinkVerified ? null : <div dangerouslySetInnerHTML={{ __html: model.dock }} />);
    },
  };
}
