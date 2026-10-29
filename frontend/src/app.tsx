import { useEffect, useLayoutEffect, useRef, useState, type ChangeEvent } from "react";
import { createRoot, type Root } from "react-dom/client";
import { createPortal } from "react-dom";
import type { AnalyticsModel } from "./charts";
import { CoverflowGallery, type GallerySlide } from "./coverflow-gallery";

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
  CM6?: {
    request: (path: string, options?: { method?: string; body?: unknown }) => Promise<any>;
    canAdmin?: () => boolean;
    can?: (permission: string) => boolean;
    sessionUser?: any;
  };
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
  const photos = [
    { id: "spawn-day", src: "assets/photos/copimine-spawn-day.webp", position: "50% 52%" },
    { id: "sunset-house", src: "assets/photos/copimine-sunset-house.webp", position: "50% 55%" },
    { id: "riverside-house", src: "assets/photos/copimine-riverside-house.webp", position: "50% 60%" },
    { id: "water-house", src: "assets/photos/copimine-water-house.webp", position: "50% 58%" },
    { id: "night", src: "assets/photos/copimine-night.webp", position: "50% 55%" },
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
          {photos.map((photo, index) => <picture key={photo.id}>
            {/* AVIF вдвое легче webp при том же виде; webp остаётся запасным. */}
            <source
              type="image/avif"
              srcSet={[800, 1440].map(w => `${window.CM.asset(photo.src.replace(/\.webp$/, `-${w}.avif`))} ${w}w`).join(", ")}
              sizes="100vw"
            />
            <img
              className="cm72-hero-photo"
              data-hero-frame=""
              data-active={index === 0 ? "true" : "false"}
              src={window.CM.asset(photo.src)}
              style={{ objectPosition: photo.position }}
              alt=""
              loading={index === 0 ? "eager" : "lazy"}
              decoding="async"
              fetchPriority={index === 0 ? "high" : "auto"}
            />
          </picture>)}
        </div>
      </div>
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
    </section>
  );
}

function GalleryLightbox({ slides, index, onClose, onStep }: {
  slides: GallerySlide[];
  index: number;
  onClose: () => void;
  onStep: (delta: number) => void;
}) {
  const closeRef = useRef<HTMLButtonElement | null>(null);
  const actions = useRef({ onClose, onStep });
  actions.current = { onClose, onStep };
  const slide = slides[index];
  const count = slides.length;

  useEffect(() => {
    const previousFocus = document.activeElement as HTMLElement | null;
    closeRef.current?.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.preventDefault(); actions.current.onClose(); }
      if (event.key === "ArrowRight") { event.preventDefault(); actions.current.onStep(1); }
      if (event.key === "ArrowLeft") { event.preventDefault(); actions.current.onStep(-1); }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus?.();
    };
  }, []);

  return createPortal(
    <div className="cm-lightbox" role="dialog" aria-modal="true" aria-label={"Просмотр скриншота: " + (slide?.title || "")} onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <header className="cm-lightbox-bar">
        <span className="cm-lightbox-count">{index + 1} <i>/ {count}</i></span>
        <button ref={closeRef} className="cm-lightbox-close" type="button" aria-label="Закрыть просмотр" onClick={onClose}>{iconMarkup("x")}</button>
      </header>
      {count > 1 && <button className="cm-lightbox-arrow prev" type="button" aria-label="Предыдущий скриншот" onClick={() => onStep(-1)}>{iconMarkup("arrow-left")}</button>}
      <figure className="cm-lightbox-figure">
        <img key={slide?.src} src={window.CM.asset(slide?.src || "")} alt={slide?.alt || slide?.title || ""} />
        <figcaption>
          <strong>{slide?.title || ""}</strong>
          {slide?.caption && <span>{slide.caption}</span>}
        </figcaption>
      </figure>
      {count > 1 && <button className="cm-lightbox-arrow next" type="button" aria-label="Следующий скриншот" onClick={() => onStep(1)}>{iconMarkup("arrow-right")}</button>}
    </div>,
    document.body
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
        <div className="cm72-map-preview-brand"><img src={window.CM.asset("assets/img/brands/bluemap.webp")} alt="BlueMap" loading="lazy" /><strong>Карта миров</strong></div>
        <span className="cm72-map-preview-link">Открыть {iconMarkup("arrow-up-right")}</span>
      </div>
    </a>
  );
}

function getGallery(): GallerySlide[] {
  return (window.CM?.state?.gallery || [])
    .filter((item: GallerySlide & { published?: boolean; kind?: string }) => item.published && item.kind !== "map" && !String(item.src || "").includes("/map/"))
    .sort((a: any, b: any) => ((a.position || 999) - (b.position || 999)));
}

function HomePage() {
  const M = window.CM;
  const state = M.state;
  const gallery = getGallery();
  const items = ["return_stone", "infinite_torch", "pohuy_na_debaffy_amulet", "gde_moy_lut_blyat_compass"]
    .map((id) => M.item(id))
    .filter((product: any) => product?.enabled);
  const faq = (state.faq || []).filter((entry: any) => entry.published);
  const [lightbox, setLightbox] = useState<number | null>(null);
  const stepLightbox = (delta: number) => setLightbox((current) => {
    if (current === null || !gallery.length) return null;
    return (current + delta + gallery.length) % gallery.length;
  });

  return (
    <>
      <ServerHero />
      <CoverflowGallery slides={gallery} startIndex={window.CM72?.galleryIndex || 0} asset={M.asset} onOpen={(_, index) => setLightbox(index)} />
      {lightbox !== null && gallery[lightbox] && <GalleryLightbox slides={gallery} index={lightbox} onClose={() => setLightbox(null)} onStep={stepLightbox} />}
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

const worldLabel = (id: string) => id === "nether" ? "Незер" : id === "end" ? "Энд" : "Обычный";

const MAPART_HOSTS = new Set([
  "copimine.ru",
  "avatars.mds.yandex.net",
  "i.imgur.com",
  "storage.googleapis.com",
  "cdn.discordapp.com",
  "media.discordapp.net",
  "textures.minecraft.net",
]);

// Exact Paper MapPalette opaque colors used by ImageFrame's default NEAREST_COLOR.
const MAPART_COLORS = [
  0x597d27, 0x6d9930, 0x7fb238, 0x435e1d, 0xaea473, 0xd5c98c, 0xf7e9a3, 0x827b56,
  0x8c8c8c, 0xababab, 0xc7c7c7, 0x696969, 0xb40000, 0xdc0000, 0xff0000, 0x870000,
  0x7070b4, 0x8a8adc, 0xa0a0ff, 0x545487, 0x757575, 0x909090, 0xa7a7a7, 0x585858,
  0x005700, 0x006a00, 0x007c00, 0x004100, 0xb4b4b4, 0xdcdcdc, 0xffffff, 0x878787,
  0x737681, 0x8d909e, 0xa4a8b8, 0x565861, 0x6a4c36, 0x825e42, 0x976d4d, 0x4f3928,
  0x4f4f4f, 0x606060, 0x707070, 0x3b3b3b, 0x2d2db4, 0x3737dc, 0x4040ff, 0x212187,
  0x645432, 0x7b663e, 0x8f7748, 0x4b3f26, 0xb4b1ac, 0xdcd9d3, 0xfffcf5, 0x878581,
  0x985924, 0xba6d2c, 0xd87f33, 0x72431b, 0x7d3598, 0x9941ba, 0xb24cd8, 0x5e2872,
  0x486c98, 0x5884ba, 0x6699d8, 0x365172, 0xa1a124, 0xc5c52c, 0xe5e533, 0x79791b,
  0x599011, 0x6db015, 0x7fcc19, 0x436c0d, 0xaa5974, 0xd06d8e, 0xf27fa5, 0x804357,
  0x353535, 0x414141, 0x4c4c4c, 0x282828, 0x6c6c6c, 0x848484, 0x999999, 0x515151,
  0x35596c, 0x416d84, 0x4c7f99, 0x284351, 0x592c7d, 0x6d3699, 0x7f3fb2, 0x43215e,
  0x24357d, 0x2c4199, 0x334cb2, 0x1b285e, 0x483524, 0x58412c, 0x664c33, 0x36281b,
  0x485924, 0x586d2c, 0x667f33, 0x36431b, 0x6c2424, 0x842c2c, 0x993333, 0x511b1b,
  0x111111, 0x151515, 0x191919, 0x0d0d0d, 0xb0a836, 0xd7cd42, 0xfaee4d, 0x847e28,
  0x409a96, 0x4fbcb7, 0x5cdbd5, 0x307370, 0x345ab4, 0x3f6edc, 0x4a80ff, 0x274387,
  0x009928, 0x00bb32, 0x00d93a, 0x00721e, 0x5b3c22, 0x6f4a2a, 0x815631, 0x442d19,
  0x4f0100, 0x600100, 0x700200, 0x3b0100, 0x937c71, 0xb4988a, 0xd1b1a1, 0x6e5d55,
  0x703919, 0x89461f, 0x9f5224, 0x542b13, 0x693d4c, 0x804b5d, 0x95576c, 0x4e2e39,
  0x4f4c61, 0x605d77, 0x706c8a, 0x3b3949, 0x835d19, 0xa0721f, 0xba8524, 0x624613,
  0x485225, 0x58642d, 0x677535, 0x363d1c, 0x703637, 0x8a4243, 0xa04d4e, 0x542829,
  0x281c18, 0x31231e, 0x392923, 0x1e1512, 0x5f4b45, 0x745c54, 0x876b62, 0x473833,
  0x3d4040, 0x4b4f4f, 0x575c5c, 0x2e3030, 0x56333e, 0x693e4b, 0x7a4958, 0x40262e,
  0x352b40, 0x41354f, 0x4c3e5c, 0x282030, 0x352318, 0x412b1e, 0x4c3223, 0x281a12,
  0x35391d, 0x414624, 0x4c522a, 0x282b16, 0x642a20, 0x7a3327, 0x8e3c2e, 0x4b1f18,
  0x1a0f0b, 0x1f120d, 0x251610, 0x130b08, 0x852122, 0xa3292a, 0xbd3031, 0x641919,
  0x682c44, 0x7f3653, 0x943f61, 0x4e2133, 0x401114, 0x4f1519, 0x5c191d, 0x300d0f,
  0x0f585e, 0x126c73, 0x167e86, 0x0b4246, 0x286462, 0x327a78, 0x3a8e8c, 0x1e4b4a,
  0x3c1f2b, 0x4a2535, 0x562c3e, 0x2d1720, 0x0e7f5d, 0x119b72, 0x14b485, 0x0a5f46,
  0x464646, 0x565656, 0x646464, 0x343434, 0x987b67, 0xba967e, 0xd8af93, 0x725c4d,
  0x597569, 0x6d9081, 0x7fa796, 0x43584f,
];
const MAPART_PALETTE = MAPART_COLORS.map((hex) => [hex >> 16 & 255, hex >> 8 & 255, hex & 255] as const);
const MAPART_CACHE = new Map<number, number>();

// Paper's weighted redmean match; same palette order and strict < as Bukkit.
function mapartIndex(r: number, g: number, b: number, a: number): number {
  if (a < 128) return -1;
  const key = (r << 16) | (g << 8) | b;
  const cached = MAPART_CACHE.get(key);
  if (cached !== undefined) return cached;
  let best = 0, distance = Infinity;
  for (let index = 0; index < MAPART_PALETTE.length; index += 1) {
    const color = MAPART_PALETTE[index];
    const dr = r - color[0], dg = g - color[1], db = b - color[2];
    const mean = (r + color[0]) / 2;
    const candidate = (2 + mean / 256) * dr * dr + 4 * dg * dg + (2 + (255 - mean) / 256) * db * db;
    if (candidate < distance) { distance = candidate; best = index; }
  }
  if (MAPART_CACHE.size > 400000) MAPART_CACHE.clear();
  MAPART_CACHE.set(key, best);
  return best;
}

// Java2D nearest-neighbour sampling, 16.16 fixed point.
function mapartCoord(d: number, source: number, target: number): number {
  const mul = Math.floor(source * 65536 / target);
  return Math.floor((d * 65536 + 32768) * mul / 4294967296);
}

function MapArtPreview({ src, width, height, onNatural, onError }: {
  src: string; width: number; height: number;
  onNatural: (size: { w: number; h: number }) => void;
  onError: () => void;
}) {
  const canvas = useRef<HTMLCanvasElement | null>(null);
  const callbacks = useRef({ onNatural, onError });
  const [problem, setProblem] = useState("");
  callbacks.current = { onNatural, onError };

  useEffect(() => {
    if (!src) return;
    let alive = true;
    setProblem("");
    const image = new Image();
    if (!src.startsWith("blob:")) image.crossOrigin = "anonymous";
    image.onload = () => {
      if (!alive) return;
      const sw = image.naturalWidth, sh = image.naturalHeight;
      callbacks.current.onNatural({ w: sw, h: sh });
      const node = canvas.current;
      const ctx = node?.getContext("2d");
      if (!node || !ctx) return;
      const scale = Math.floor(sw / width);
      if (scale < 1) {
        setProblem("Картинка уже будущей картины. Уменьши ширину или возьми файл побольше.");
        return;
      }
      const cw = width * scale, ch = height * scale;
      const kx = cw / sw, ky = ch / sh;
      let drawW: number, drawH: number, offX = 0, offY = 0;
      if (kx < ky) {
        drawW = cw;
        drawH = Math.round(sh * kx);
        offY = Math.floor((ch - drawH) / 2);
      } else {
        drawH = ch;
        drawW = Math.round(sw * ky);
        offX = Math.floor((cw - drawW) / 2);
      }
      const source = document.createElement("canvas");
      source.width = sw;
      source.height = sh;
      const sourceCtx = source.getContext("2d", { willReadFrequently: true });
      if (!sourceCtx) return;
      sourceCtx.drawImage(image, 0, 0);
      let pixels: Uint8ClampedArray;
      try {
        pixels = sourceCtx.getImageData(0, 0, sw, sh).data;
      } catch {
        setProblem("Не удалось прочитать пиксели: хост не разрешает CORS.");
        return;
      }

      const outWidth = width * 128, outHeight = height * 128;
      node.width = outWidth;
      node.height = outHeight;
      ctx.clearRect(0, 0, outWidth, outHeight);
      const stage1x = new Int32Array(drawW), stage1y = new Int32Array(drawH);
      const stage2x = new Int32Array(outWidth), stage2y = new Int32Array(outHeight);
      for (let x = 0; x < drawW; x += 1) stage1x[x] = mapartCoord(x, sw, drawW);
      for (let y = 0; y < drawH; y += 1) stage1y[y] = mapartCoord(y, sh, drawH);
      for (let x = 0; x < outWidth; x += 1) stage2x[x] = mapartCoord(x, cw, outWidth);
      for (let y = 0; y < outHeight; y += 1) stage2y[y] = mapartCoord(y, ch, outHeight);

      const frame = ctx.createImageData(outWidth, outHeight);
      const out = frame.data;
      let offset = 0;
      for (let y = 0; y < outHeight; y += 1) {
        const cy = stage2y[y];
        for (let x = 0; x < outWidth; x += 1, offset += 4) {
          const cx = stage2x[x];
          if (cx < offX || cx >= offX + drawW || cy < offY || cy >= offY + drawH) continue;
          const sourceIndex = (stage1y[cy - offY] * sw + stage1x[cx - offX]) * 4;
          const index = mapartIndex(pixels[sourceIndex], pixels[sourceIndex + 1], pixels[sourceIndex + 2], pixels[sourceIndex + 3]);
          if (index < 0) continue;
          const color = MAPART_PALETTE[index];
          out[offset] = color[0];
          out[offset + 1] = color[1];
          out[offset + 2] = color[2];
          out[offset + 3] = 255;
        }
      }
      ctx.putImageData(frame, 0, 0);
    };
    image.onerror = () => { if (alive) callbacks.current.onError(); };
    image.src = src;
    return () => { alive = false; };
  }, [src, width, height]);

  return (
    <>
      <canvas ref={canvas} className="cm-mapart-canvas" aria-label="Предпросмотр картины в игровых цветах" />
      {problem && <span className="cm-mapart-approx">{problem}</span>}
    </>
  );
}

function fileBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || "").split(",")[1] || "");
    reader.onerror = () => reject(new Error("Не удалось прочитать файл."));
    reader.readAsDataURL(file);
  });
}

function MapArtPage() {
  const M = window.CM;
  const me = M?.me?.();
  const accountName = me?.minecraftName || me?.displayName || me?.name || "";
  const [mode, setMode] = useState<"file" | "url">("file");
  const [file, setFile] = useState<File | null>(null);
  const [filePreview, setFilePreview] = useState("");
  const [url, setUrl] = useState("");
  const [width, setWidth] = useState(3);
  const [height, setHeight] = useState(3);
  const [name, setName] = useState(() => "art-" + Math.random().toString(36).slice(2, 7));
  const [author, setAuthor] = useState(accountName);
  const [natural, setNatural] = useState<{ w: number; h: number } | null>(null);
  const [previewError, setPreviewError] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<{ url: string; command: string; location?: { world: string; x: number; y: number; z: number } } | null>(null);

  useEffect(() => () => {
    if (filePreview) URL.revokeObjectURL(filePreview);
  }, [filePreview]);

  const preview = mode === "file" ? filePreview : (/^https:\/\//i.test(url.trim()) ? url.trim() : "");
  const maps = width * height;
  let host = "";
  try { host = new URL(url.trim()).hostname; } catch { /* Not a full URL yet. */ }
  const hostAllowed = !host || MAPART_HOSTS.has(host);
  const mismatch = natural ? Math.abs(natural.w / natural.h - width / height) > 0.03 : false;

  const reset = () => { setResult(null); setError(""); };

  const chooseFile = (event: ChangeEvent<HTMLInputElement>) => {
    const chosen = event.currentTarget.files?.[0];
    if (!chosen) return;
    if (!/^image\/(png|jpeg|webp|gif)$/i.test(chosen.type)) {
      setError("Подойдёт PNG, JPG, WebP или GIF.");
      return;
    }
    if (chosen.size > 4 * 1024 * 1024) {
      setError("Картинка должна быть не больше 4 МБ.");
      return;
    }
    setFile(chosen);
    setFilePreview(URL.createObjectURL(chosen));
    setNatural(null);
    setPreviewError(false);
    reset();
  };

  const build = async () => {
    setError("");
    setResult(null);
    try {
      if (mode === "file" && !file) throw new Error("Сначала выбери картинку.");
      const safeName = (name.trim().replace(/[^A-Za-z0-9_-]+/g, "-").replace(/^-+|-+$/g, "") || "copimine-art").slice(0, 32);
      if (!accountName && author.trim() && !/^[A-Za-z0-9_]{3,32}$/.test(author.trim())) {
        throw new Error("Ник — 3–32 символа: латиница, цифры и подчёркивание.");
      }
      let remote = url.trim();
      if (mode === "url") {
        let parsed: URL;
        try { parsed = new URL(remote); } catch { throw new Error("Вставь полную ссылку, начиная с https://."); }
        if (parsed.protocol !== "https:") throw new Error("Ссылка должна начинаться с https://.");
      }
      if (!window.CM6?.request) throw new Error("Сервер недоступен. Попробуй позже.");
      setBusy(true);
      const uploaded = await window.CM6.request("/mapart/image", {
        method: "POST",
        body: mode === "file" && file
          ? { image: await fileBase64(file), name: safeName, width, height, author: author.trim() }
          : { remote, name: safeName, width, height, author: author.trim() },
      });
      if (!uploaded?.url) throw new Error("Сервер не вернул ссылку на картинку.");
      remote = uploaded.url;
      const command = `/imageframe create ${safeName} ${remote} ${width} ${height}`;
      setResult({ url: remote, command, location: uploaded.location });
      M?.toast?.(uploaded.location ? "Команда готова, место загрузки записано." : "Команда готова.");
    } catch (reason: any) {
      setError(reason?.message || "Не удалось подготовить команду.");
    } finally {
      setBusy(false);
    }
  };

  const gridStyle = {
    backgroundImage: "linear-gradient(to right,rgba(255,255,255,.3) 1px,transparent 1px),linear-gradient(to bottom,rgba(255,255,255,.3) 1px,transparent 1px)",
    backgroundSize: `${100 / width}% ${100 / height}%`,
  };

  return (
    <div className="cm-mapart-page">
      <div className="page-head">
        <div>
          <div className="kicker">Инструмент CopiMine</div>
          <h1>Картины из картинок</h1>
          <p>Загрузи картинку, выбери размер и скопируй команду для ImageFrame. Превью покажет, как это будет в игре.</p>
        </div>
      </div>

      <div className="cm-mapart-layout">
        <div className="cm-mapart-work">
          <section className="panel cm-mapart-source">
            <div className="cm-mapart-tabs" role="tablist" aria-label="Источник картинки">
              <button type="button" role="tab" aria-selected={mode === "file"} className={mode === "file" ? "active" : ""} onClick={() => { setMode("file"); reset(); }}>Файл</button>
              <button type="button" role="tab" aria-selected={mode === "url"} className={mode === "url" ? "active" : ""} onClick={() => { setMode("url"); reset(); }}>Ссылка</button>
            </div>
            {mode === "file" ? (
              <label className="cm-mapart-drop">
                <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" onChange={chooseFile} />
                {iconMarkup("grid")}
                <strong>{file ? file.name : "Перетащи картинку или выбери файл"}</strong>
                <small>PNG, JPG, WebP или GIF · до 4 МБ · длинная сторона уменьшится до 2048 px</small>
              </label>
            ) : (
              <div className="cm-mapart-url">
                <label className="field">Ссылка на изображение
                  <input type="url" inputMode="url" placeholder="https://i.imgur.com/..." value={url} onChange={(event) => { setUrl(event.currentTarget.value); setNatural(null); setPreviewError(false); reset(); }} />
                </label>
                <small className={hostAllowed ? "hint" : "cm-mapart-warn"}>{hostAllowed ? "Ссылка должна открываться без авторизации." : `Домен ${host} не в белом списке ImageFrame — сервер может не загрузить картинку. Надёжнее загрузить файл.`}</small>
              </div>
            )}
          </section>

          <section className="panel cm-mapart-stage">
            <div className="cm-mapart-preview" style={{ aspectRatio: `${width} / ${height}`, width: `min(100%, ${Math.round((560 * width) / height)}px)` }}>
              {preview && !previewError ? (
                <>
                  <MapArtPreview src={preview} width={width} height={height} onNatural={setNatural} onError={() => setPreviewError(true)} />
                  <div className="cm-mapart-grid" style={gridStyle} aria-hidden="true" />
                </>
              ) : (
                <div className="cm-mapart-placeholder">
                  {iconMarkup("grid")}
                  <strong>{previewError ? "Картинка не открылась" : "Здесь будет предпросмотр"}</strong>
                  <span>{previewError ? "Проверь ссылку или загрузи файл." : "Выбери картинку — покажу, как она ляжет на карты."}</span>
                </div>
              )}
            </div>
            <div className="cm-mapart-caption">
              <span>{preview && !previewError ? `${natural ? `${natural.w} × ${natural.h} px` : "Изображение"} · ${width * 128} × ${height * 128} px · ${maps} карт` : "Сетка показывает границы карт"}</span>
              {mismatch && <span className="cm-mapart-warn">Пропорции не совпадают: картинка впишется целиком, по краям будут прозрачные поля.</span>}
            </div>
          </section>
        </div>

        <aside className="cm-mapart-side">
          <section className="panel">
            <h2>Размер картины</h2>
            <div className="cm-mapart-size">
              <label className="field">Ширина<select value={width} onChange={(event) => { setWidth(Number(event.currentTarget.value)); reset(); }}>{Array.from({ length: 10 }, (_, index) => index + 1).map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
              <label className="field">Высота<select value={height} onChange={(event) => { setHeight(Number(event.currentTarget.value)); reset(); }}>{Array.from({ length: 10 }, (_, index) => index + 1).map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
            </div>
            <div className="cm-mapart-cost single">
              <div><span>Нужно пустых карт</span><strong>{maps}</strong></div>
            </div>
            <p className="hint">Одна карта — 128 × 128 пикселей. ImageFrame принимает до 100 карт.</p>
          </section>

          <section className="panel">
            <h2>Команда</h2>
            <label className="field">Название<small>Латиница, цифры, дефис или подчёркивание.</small>
              <input value={name} maxLength={32} pattern="[A-Za-z0-9_-]+" onChange={(event) => { setName(event.currentTarget.value); reset(); }} />
            </label>
            {!accountName && <label className="field">Ник в Minecraft<small>Подпишу загрузку твоим ником.</small><input value={author} maxLength={32} placeholder="Steve" onChange={(event) => setAuthor(event.currentTarget.value)} /></label>}
            <button type="button" className="btn primary wide" onClick={build} disabled={busy || (mode === "file" ? !file : !url.trim())}>{busy ? "Загружаем картинку…" : "Получить команду"}{iconMarkup("arrow-right")}</button>
            {error && <p className="cm-mapart-error" role="alert">{error}</p>}
            {result && (
              <div className="cm-mapart-result">
                <code>{result.command}</code>
                <button type="button" className="btn ghost wide" onClick={() => M?.copyText?.(result.command)}>{iconMarkup("copy")}Скопировать команду</button>
                <small>{result.location ? `Точка загрузки: ${worldLabel(result.location.world)} · ${result.location.x} ${result.location.y} ${result.location.z}. Она будет в журнале.` : "Вставь в чат Minecraft. Нужны пустые карты и право imageframe.create."}</small>
              </div>
            )}
          </section>
        </aside>
      </div>

      <section className="panel cm-mapart-help">
        <h2>Как это работает</h2>
        <ol>
          <li>Загрузи картинку или вставь ссылку https://.</li>
          <li>Выбери размер и проверь превью.</li>
          <li>Скопируй команду и вставь её в чат Minecraft.</li>
        </ol>
        <p className="hint">Если ты на сервере, сайт сам запишет точку загрузки. Ссылки — только с доверенных доменов; файл открывается всегда.</p>
      </section>
    </div>
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
          <img src={window.CM.asset("assets/img/brands/bluemap.webp")} alt="BlueMap" />
          <span>BlueMap</span>
        </div>
      </header>
      <p className="cm-react-map-note">На карте видны только места, где уже играли. Исследуйте мир — карта открывается вместе с вами.</p>
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
            <img src={window.CM.asset("assets/img/brands/bluemap.webp")} alt="" />
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
        <a href="#home" aria-label="На главную CopiMine"><img src={window.CM?.asset?.("assets/img/logo.webp") || "/assets/img/logo.webp"} alt="CopiMine" /></a>
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
      renderEmbedded(host, <AdminAnalyticsCharts model={analytics} />);
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
      renderEmbedded(liveHost, <AdminAnalyticsCharts model={analytics} />);
    }).catch(() => {
      if (liveHost.isConnected) liveHost.textContent = "Графики аналитики не загрузились.";
    });
  }
}

function CopiMineApp({ model }: { model: LegacyRouteModel }) {
  const accountLinkUser = window.CMAccountLink?.state?.()?.user || window.CM?.me?.();
  const isAdmin = !!(window.CM6?.canAdmin?.() || window.CM6?.can?.("settings.manage"));
  const accountNeedsLink = !isAdmin && !!accountLinkUser && !accountLinkUser.gameLinkVerified && model.page === "account";
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

  useEffect(() => {
    const root = document.getElementById("page-root");
    if (!root) return;
    root.classList.remove("cm-route-enter");
    void root.offsetWidth;
    root.classList.add("cm-route-enter");
    const timer = window.setTimeout(() => root.classList.remove("cm-route-enter"), 700);
    return () => window.clearTimeout(timer);
  }, [model.revision, model.page, model.tab]);

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
                  : model.page === "mapart" ? <MapArtPage />
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
let embeddedRoots = new Map<HTMLElement, Root>();

// Повторный рендер (например, после догрузки библиотеки предметов) не должен
// создавать второй корень на том же контейнере — React тогда падает на removeChild.
function renderEmbedded(host: HTMLElement, node: React.ReactElement) {
  const existing = embeddedRoots.get(host);
  if (existing) { existing.render(node); return; }
  const root = createRoot(host);
  root.render(node);
  embeddedRoots.set(host, root);
}

export function mountCopiMineReact() {
  const app = document.getElementById("app");
  const dock = document.getElementById("dock-root");
  if (!app || !dock || appRoot) return;
  appRoot = createRoot(app);
  dockRoot = createRoot(dock);
  window.CopimineReactBridge = {
    render(model) {
      // Unmount only roots whose host is still in the document: the legacy HTML swap
      // detaches chart hosts first, and unmounting those throws removeChild.
      embeddedRoots.forEach((root, host) => { if (host.isConnected) root.unmount(); });
      embeddedRoots.clear();
      model.analytics = window.CMAnalyticsReactModel || null;
      model.serviceMetrics = window.CM?.state?.serviceMetrics || [];
      appRoot?.render(<CopiMineApp model={model} />);
      const accountUser = window.CMAccountLink?.state?.()?.user || window.CM?.me?.();
      dockRoot?.render(accountUser && !accountUser.gameLinkVerified ? null : <div dangerouslySetInnerHTML={{ __html: model.dock }} />);
    },
  };
}
