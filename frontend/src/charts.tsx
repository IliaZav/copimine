import { motion } from "motion/react";

type DayRecord = { at?: string | number; day?: string; visits?: number; visitors?: number; AR?: number; CM?: number; [key: string]: unknown };
type AnalyticsData = {
  from?: string;
  to?: string;
  days?: DayRecord[];
  events?: Array<{ day?: string; type?: string; page?: string; count?: number }>;
  pages?: Array<{ page?: string; count?: number }>;
  top?: Array<{ id?: string; name?: string; qty?: number; revenue?: number; currency?: string; orders?: number }>;
  live?: { samples?: Array<Record<string, unknown>> };
  regions?: { features?: Array<{ type?: string; properties?: Record<string, unknown>; geometry?: { type?: string; coordinates?: unknown } }> };
  revenue?: Record<string, number>;
  [key: string]: unknown;
};

export type AnalyticsModel = { data?: AnalyticsData | null; sub?: string; range?: string; loading?: boolean; error?: string };
type Series = { id: string; label: string; color: string; values: number[] };
type Point = { label: string; value: number; [key: string]: unknown };

const colors = ["#38dda4", "#92e8bf", "#71a9d3", "#c8ba84", "#b18dc4", "#e48d82"];
const publicPages = new Set(["home", "shop", "cart", "donate", "map", "rules", "support"]);
const pageLabels: Record<string, string> = {
  home: "Главная", shop: "Магазин", cart: "Корзина", donate: "Донат",
  map: "Карта", rules: "Правила", support: "Поддержка",
};
const eventLabels: Record<string, string> = {
  page_view: "Просмотры", product_view: "Предметы", item_view: "Предметы",
  add_to_cart: "Корзина", cart_add: "Корзина", cart_remove: "Удаления",
  checkout_start: "Оформление", checkout_started: "Оформление",
  checkout_complete: "Покупки", checkout_completed: "Покупки",
  donation_start: "Пополнение", payment_created: "Пополнение",
  download_mods: "Сборка", download_launcher: "Лаунчер", play: "Вход",
};
const number = (value: unknown) => Number.isFinite(Number(value)) ? Math.max(0, Number(value)) : 0;
const format = (value: number) => Math.round(value).toLocaleString("ru-RU");
const dayValue = (value: string | number | undefined) => {
  if (typeof value === "number") return new Date(value < 10_000_000_000 ? value * 1000 : value).toISOString().slice(0, 10);
  return String(value || "").slice(0, 10);
};

function visitSeries(data: AnalyticsData): Point[] {
  const byDay = new Map<string, number>();
  for (const event of data.events || []) {
    if (event.type !== "page_view") continue;
    const day = dayValue(event.day);
    if (day) byDay.set(day, (byDay.get(day) || 0) + number(event.count));
  }
  const source = (data.days || []).map((row) => {
    const day = dayValue(row.at || row.day);
    return { label: day, value: number(row.visits ?? byDay.get(day) ?? 0) };
  }).filter((row) => row.label);
  if (!source.length) return [...byDay].map(([label, value]) => ({ label, value })).sort((a, b) => a.label.localeCompare(b.label));
  return source.sort((a, b) => a.label.localeCompare(b.label)).slice(-90);
}

function pageSeries(data: AnalyticsData): Point[] {
  return (data.pages || [])
    .filter((row) => publicPages.has(String(row.page)))
    .map((row) => ({ label: pageLabels[String(row.page)] || String(row.page), value: number(row.count) }))
    .filter((row) => row.value > 0)
    .sort((a, b) => b.value - a.value)
    .slice(0, 8);
}

function eventPages(data: AnalyticsData) {
  const groups = new Map<string, Map<string, number>>();
  for (const row of data.events || []) {
    if (!publicPages.has(String(row.page))) continue;
    const event = String(row.type || "other");
    const page = String(row.page || "");
    if (!groups.has(event)) groups.set(event, new Map());
    const pages = groups.get(event)!;
    pages.set(page, (pages.get(page) || 0) + number(row.count));
  }
  return [...groups].map(([event, pages]) => ({ event, label: eventLabels[event] || event.replace(/_/g, " "), pages: [...pages].map(([page, value]) => ({ page, value })) }))
    .sort((a, b) => b.pages.reduce((sum, row) => sum + row.value, 0) - a.pages.reduce((sum, row) => sum + row.value, 0));
}

function EmptyChart({ title, detail }: { title: string; detail: string }) {
  return <div className="cm-react-chart-empty"><strong>{title}</strong><p>{detail}</p></div>;
}

function ChartCard({ title, children, detail }: { title: string; children: React.ReactNode; detail?: string }) {
  return <article className="cm-react-chart-card"><h3>{title}</h3>{children}{detail && <p>{detail}</p>}</article>;
}

function AreaChart({ title, data, series, suffix = "" }: { title: string; data: Point[]; series: Series[]; suffix?: string }) {
  if (!data.length || !series.length || !data.some((point) => series.some((line) => number(point[line.id]) > 0))) {
    return <ChartCard title={title}><EmptyChart title="Пока нет данных" detail="График заполнится после появления событий в выбранном периоде." /></ChartCard>;
  }
  const width = 720, height = 210, pad = 18, base = 170;
  const max = Math.max(1, ...series.flatMap((line) => line.values));
  const pointsFor = (line: Series) => data.map((point, index) => {
    const x = pad + index * (width - pad * 2) / Math.max(1, data.length - 1);
    const y = base - number(point[line.id]) / max * 138;
    return [x, y] as const;
  });
  return (
    <ChartCard title={title}>
      <svg viewBox={"0 0 " + width + " " + height} role="img" aria-label={title + ". Максимум " + format(max) + suffix}>
        <line x1={pad} x2={width - pad} y1={base} y2={base} stroke="rgba(206,218,214,.22)" />
        {series.map((line) => {
          const coordinates = pointsFor(line);
          if (!coordinates.length) return null;
          const linePath = coordinates.map(([x, y], index) => (index ? "L" : "M") + x + " " + y).join(" ");
          const areaPath = linePath + " L " + coordinates[coordinates.length - 1][0] + " " + base + " L " + coordinates[0][0] + " " + base + " Z";
          return <g key={line.id}>
            <motion.path d={areaPath} fill={"url(#area-" + line.id + ")"} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.65 }} />
            <motion.path d={linePath} fill="none" stroke={line.color} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" initial={{ pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ duration: 0.9, ease: "easeOut" }} />
            {coordinates.filter((_, index) => data.length < 14 || index === 0 || index === coordinates.length - 1 || index % Math.ceil(data.length / 8) === 0).map(([x, y], index) => {
              const sourceIndex = data.length < 14 ? index : Math.min(data.length - 1, Math.round(x * (data.length - 1) / width));
              return <circle key={index} cx={x} cy={y} r="3.5" fill="#17201e" stroke={line.color} strokeWidth="2"><title>{data[sourceIndex]?.label}: {format(number(data[sourceIndex]?.[line.id]))}{suffix}</title></circle>;
            })}
            <defs><linearGradient id={"area-" + line.id} x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor={line.color} stopOpacity=".28" /><stop offset="100%" stopColor={line.color} stopOpacity="0" /></linearGradient></defs>
          </g>;
        })}
      </svg>
      <div className="cm-react-chart-axis"><span>{data[0].label}</span><span>{data[data.length - 1].label}</span></div>
      <div className="cm-react-chart-legend">{series.map((line) => <div className="cm-react-chart-legend-row" key={line.id}><span><i style={{ background: line.color }} />{line.label}</span><strong>{format(line.values.reduce((sum, value) => sum + value, 0))}{suffix}</strong></div>)}</div>
    </ChartCard>
  );
}

function BarChart({ title, rows, suffix = "" }: { title: string; rows: Point[]; suffix?: string }) {
  const max = Math.max(1, ...rows.map((row) => row.value));
  if (!rows.length || !rows.some((row) => row.value > 0)) return <ChartCard title={title}><EmptyChart title="Пока пусто" detail="Здесь появятся значения, как только накопятся данные." /></ChartCard>;
  return (
    <ChartCard title={title}>
      <div className="cm-react-bars" role="img" aria-label={title}>
        {rows.map((row, index) => <div className="cm-react-bar-row" key={row.label}>
          <span title={row.label}>{row.label}</span>
          <div className="cm-react-bar-track"><motion.i initial={{ scaleX: 0 }} animate={{ scaleX: row.value / max }} transition={{ duration: 0.55, delay: index * 0.045, ease: [0.22, 1, 0.36, 1] }} style={{ transformOrigin: "left", background: colors[index % colors.length] }} /></div>
          <strong>{format(row.value)}{suffix}</strong>
        </div>)}
      </div>
    </ChartCard>
  );
}

function DonutChart({ title, rows }: { title: string; rows: Point[] }) {
  const total = rows.reduce((sum, row) => sum + row.value, 0);
  if (!total) return <ChartCard title={title}><EmptyChart title="Нет распределения" detail="Разбивка появится вместе с первыми посещениями." /></ChartCard>;
  const circumference = 2 * Math.PI * 43;
  let offset = 0;
  return (
    <ChartCard title={title}>
      <div className="cm-react-donut">
        <svg viewBox="0 0 120 120" role="img" aria-label={title + ". " + format(total) + " всего"}>
          <circle cx="60" cy="60" r="43" fill="none" stroke="rgba(206,218,214,.12)" strokeWidth="13" />
          {rows.map((row, index) => {
            const length = circumference * row.value / total;
            const currentOffset = offset;
            offset += length;
            return <motion.circle key={row.label} cx="60" cy="60" r="43" fill="none" stroke={colors[index % colors.length]} strokeWidth="13" strokeDasharray={length + " " + (circumference - length)} strokeDashoffset={-currentOffset} transform="rotate(-90 60 60)" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.35, delay: index * 0.06 }}><title>{row.label}: {format(row.value)}</title></motion.circle>;
          })}
          <text x="60" y="58" textAnchor="middle" className="cm-react-donut-total">{format(total)}</text>
          <text x="60" y="73" textAnchor="middle" className="cm-react-donut-caption">просмотров</text>
        </svg>
        <div className="cm-react-pie-legend">{rows.map((row, index) => <div className="cm-react-chart-legend-row" key={row.label}><span><i style={{ background: colors[index % colors.length] }} />{row.label}</span><strong>{Math.round(row.value / total * 100)}%</strong></div>)}</div>
      </div>
    </ChartCard>
  );
}

function HeatmapChart({ title, data }: { title: string; data: AnalyticsData }) {
  const byDay = new Map<string, number>();
  for (const row of data.events || []) {
    if (row.type !== "page_view") continue;
    const key = dayValue(row.day);
    if (key) byDay.set(key, (byDay.get(key) || 0) + number(row.count));
  }
  if (!byDay.size) return <ChartCard title={title}><EmptyChart title="Пока нет активности" detail="Дни с просмотрами появятся после сбора первых событий." /></ChartCard>;
  const end = new Date();
  end.setUTCHours(0, 0, 0, 0);
  const start = new Date(end.getTime() - 90 * 86400000);
  const max = Math.max(1, ...byDay.values());
  const cells = Array.from({ length: 91 }, (_, index) => {
    const date = new Date(start.getTime() + index * 86400000);
    const key = date.toISOString().slice(0, 10);
    const value = byDay.get(key) || 0;
    const level = value === 0 ? 0 : Math.min(4, Math.ceil(value / max * 4));
    return { key, value, level };
  });
  return (
    <ChartCard title={title} detail="Просмотры за последние 13 недель.">
      <div className="cm-react-heatmap" role="img" aria-label={title}>
        {cells.map((cell) => <span className="cm-react-heatmap-cell" key={cell.key} data-level={cell.level} tabIndex={0} aria-label={cell.key + ": " + format(cell.value)} title={cell.key + ": " + format(cell.value)} />)}
      </div>
    </ChartCard>
  );
}

function SankeyChart({ title, data }: { title: string; data: AnalyticsData }) {
  const groups = eventPages(data).slice(0, 4);
  const pages = [...new Set(groups.flatMap((group) => group.pages.map((row) => row.page)))].slice(0, 5);
  const links = groups.flatMap((group) => group.pages.filter((row) => pages.includes(row.page)).map((row) => ({ event: group.label, page: row.page, value: row.value })));
  const max = Math.max(1, ...links.map((link) => link.value));
  if (!links.length) return <ChartCard title={title}><EmptyChart title="Нет событий" detail="Поток появится после первых посещений и действий на сайте." /></ChartCard>;
  return (
    <ChartCard title={title} detail="Количество событий каждого типа на публичных страницах. Это распределение, не путь отдельного пользователя.">
      <svg className="cm-react-sankey" viewBox="0 0 620 240" role="img" aria-label={title}>
        {links.map((link, index) => {
          const fromIndex = groups.findIndex((group) => group.label === link.event);
          const toIndex = pages.indexOf(link.page);
          const y1 = 28 + fromIndex * 46;
          const y2 = 26 + toIndex * 41;
          const thickness = Math.max(2, link.value / max * 28);
          return <path key={index} d={"M 165 " + y1 + " C 290 " + y1 + ", 330 " + y2 + ", 455 " + y2} strokeWidth={thickness}><title>{link.event} → {pageLabels[link.page] || link.page}: {format(link.value)}</title></path>;
        })}
        {groups.map((group, index) => <text key={group.event} x="12" y={32 + index * 46}>{group.label}</text>)}
        {pages.map((page, index) => <text key={page} x="470" y={30 + index * 41}>{pageLabels[page] || page}</text>)}
      </svg>
    </ChartCard>
  );
}

function LiveLineChart({ samples }: { samples: Array<Record<string, unknown>> }) {
  const candidates = ["players", "online", "tps", "mspt"];
  const metric = candidates.find((key) => samples.some((sample) => Number.isFinite(Number(sample[key])))) || "";
  const rows = samples.slice(-60).map((sample) => ({
    label: dayValue(sample.at as string | number) || String(sample.at || ""),
    value: number(metric ? sample[metric] : 0),
  })).filter((row) => row.label);
  if (!metric || !rows.length) return <ChartCard title="Снимки сервера"><EmptyChart title="Метрики сервера не подключены" detail="Показатель появится после подключения источника мониторинга игрового сервера." /></ChartCard>;
  return <AreaChart title={"Снимки сервера · " + metric.toUpperCase()} data={rows} series={[{ id: "value", label: metric.toUpperCase(), color: colors[0], values: rows.map((row) => row.value) }]} />;
}

function ChoroplethChart({ title, regions }: { title: string; regions: NonNullable<AnalyticsData["regions"]>["features"] }) {
  const features = regions || [];
  const coordinates: number[][] = [];
  const collect = (input: unknown) => {
    if (!Array.isArray(input)) return;
    if (input.length >= 2 && typeof input[0] === "number" && typeof input[1] === "number") coordinates.push([input[0], input[1]]);
    else input.forEach(collect);
  };
  features.forEach((feature) => collect(feature.geometry?.coordinates));
  if (!coordinates.length) return <ChartCard title={title}><EmptyChart title="Карта регионов недоступна" detail="В источнике пока нет координатных данных для карты." /></ChartCard>;
  const minX = Math.min(...coordinates.map((point) => point[0])), maxX = Math.max(...coordinates.map((point) => point[0]));
  const minY = Math.min(...coordinates.map((point) => point[1])), maxY = Math.max(...coordinates.map((point) => point[1]));
  const x = (value: number) => 10 + (value - minX) / Math.max(0.0001, maxX - minX) * 540;
  const y = (value: number) => 190 - (value - minY) / Math.max(0.0001, maxY - minY) * 170;
  const pathFor = (input: unknown): string => {
    if (!Array.isArray(input) || !input.length) return "";
    if (Array.isArray(input[0]) && typeof input[0][0] === "number") return input.map((point: number[], index) => (index ? "L" : "M") + x(point[0]) + " " + y(point[1])).join(" ") + " Z";
    return input.map(pathFor).join(" ");
  };
  const maximum = Math.max(1, ...features.map((feature) => number(feature.properties?.value)));
  return (
    <ChartCard title={title}>
      <svg viewBox="0 0 560 210" role="img" aria-label={title}>
        {features.map((feature, index) => {
          const value = number(feature.properties?.value);
          const level = Math.min(1, value / maximum);
          const hue = 152;
          return <path className="cm-react-map-feature" key={String(feature.properties?.id || index)} d={pathFor(feature.geometry?.coordinates)} fill={"hsl(" + hue + " 56% " + (18 + level * 34) + "%)"}><title>{String(feature.properties?.name || "Регион")}: {format(value)}</title></path>;
        })}
      </svg>
    </ChartCard>
  );
}

export function AdminAnalyticsCharts({ model }: { model: AnalyticsModel }) {
  if (model.sub === "live") {
    return <div className="cm-react-chart-section"><LiveLineChart samples={model.data?.live?.samples || []} /></div>;
  }
  const data = model.data || {};
  const visits = visitSeries(data);
  const pageRows = pageSeries(data);
  const activity = eventPages(data);
  const showEconomy = model.sub === "economy";
  const dailyAR = (data.days || []).map((row) => ({ label: dayValue(row.at), AR: number(row.AR), CM: number(row.CM), value: number(row.AR) + number(row.CM) })).filter((row) => row.label).slice(-90);
  const currencyRows = [
    { label: "AR", value: number(data.revenue?.AR) },
    { label: "CM", value: number(data.revenue?.CM) },
  ].filter((row) => row.value > 0);
  const productRows = (data.top || []).slice(0, 8).map((row) => ({ label: row.name || row.id || "Предмет", value: number(row.qty) }));
  const chartCards = showEconomy
    ? <>
        <AreaChart title="Оборот по дням" data={dailyAR} series={[
          { id: "AR", label: "AR", color: colors[0], values: dailyAR.map((row) => row.AR) },
          { id: "CM", label: "CM", color: colors[2], values: dailyAR.map((row) => row.CM) },
        ]} />
        <DonutChart title="Оборот по валюте" rows={currencyRows} />
        <BarChart title="Выданные предметы" rows={productRows} suffix=" шт." />
      </>
    : model.sub === "events"
      ? <>
          <HeatmapChart title="Активность сайта" data={data} />
          <SankeyChart title="События по страницам" data={data} />
        </>
      : model.sub === "pages"
        ? <>
            <AreaChart title="Просмотры по дням" data={visits} series={[{ id: "value", label: "Просмотры", color: colors[0], values: visits.map((row) => row.value) }]} />
            <DonutChart title="Доля страниц" rows={pageRows} />
          </>
        : <>
            <AreaChart title="Просмотры по дням" data={visits} series={[{ id: "value", label: "Просмотры", color: colors[0], values: visits.map((row) => row.value) }]} />
            <BarChart title="Популярные страницы" rows={pageRows} />
            <HeatmapChart title="Активность за 13 недель" data={data} />
            <DonutChart title="Доля страниц" rows={pageRows} />
            <SankeyChart title="Действия по страницам" data={data} />
          </>;
  const hasRegions = Boolean(data.regions?.features?.length);
  const liveSamples = data.live?.samples || [];
  return (
    <section className="cm-react-chart-section" aria-label="Графики CopiMine">
      <div className="cm-react-chart-grid">{chartCards}</div>
      {(hasRegions || liveSamples.length > 0) && <div className="cm-react-chart-grid">
        {hasRegions && <ChoroplethChart title="Активность по регионам" regions={data.regions?.features} />}
        {liveSamples.length > 0 && <LiveLineChart samples={liveSamples} />}
      </div>}
      {activity.length === 0 && model.sub !== "economy" && pageRows.length === 0 && visits.length === 0
        ? <p className="cm-react-no-chart-data">Данные появятся после первых посещений сайта.</p>
        : null}
    </section>
  );
}
