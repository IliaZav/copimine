"""Consent-gated public-site analytics and aggregated admin reports."""

from datetime import date, datetime, time as day_time, timedelta, timezone
import json
import math
import re
import time
import uuid

from fastapi import APIRouter, Request

from .security import current_user, digest, fail

router = APIRouter()

PUBLIC_PAGES = {"home", "shop", "cart", "donate", "map", "rules", "support"}
EVENT_TYPES = {
    "page_view", "product_view", "item_view", "add_to_cart", "cart_add", "cart_remove",
    "checkout_start", "checkout_started", "checkout_complete", "checkout_completed",
    "donation_start", "payment_created", "download_mods", "download_launcher", "play",
}
VISITOR_RE = re.compile(r"^[A-Za-z0-9-]{16,80}$")
ITEM_RE = re.compile(r"^[A-Za-z0-9_.:-]{1,128}$")
LANG_RE = re.compile(r"^[A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8})?$")
SCREEN_RE = re.compile(r"^\d{3,5}x\d{3,5}$")
SOURCE_RE = re.compile(r"^[a-z0-9.-]{1,64}$")
UTM_RE = re.compile(r"^[A-Za-z0-9._~ -]{1,64}$")
BROWSER_RE = re.compile(r"^[A-Za-z0-9 ._+-]{1,40}$")
DEVICES = {"desktop", "mobile", "tablet", "other"}
RETENTION_SECONDS = 400 * 86400
CONSENT_RETENTION_SECONDS = 3 * 365 * 86400
CONSENT_POLICY_VERSION = "2026-10-27"


def _date_range(request):
    start_raw = request.query_params.get("from", "")
    end_raw = request.query_params.get("to", "")
    try:
        start = date.fromisoformat(start_raw)
        end = date.fromisoformat(end_raw)
    except (TypeError, ValueError):
        fail(400, "invalid_range", "Укажите даты отчёта в формате ГГГГ-ММ-ДД.")
    if start.isoformat() != start_raw or end.isoformat() != end_raw or end < start or (end - start).days > 365:
        fail(400, "invalid_range", "Период отчёта должен быть от одного до 366 дней.")
    if end > datetime.now(timezone.utc).date():
        fail(400, "invalid_range", "Дата окончания отчёта не может быть в будущем.")
    start_s = int(datetime.combine(start, day_time.min, tzinfo=timezone.utc).timestamp())
    end_s = int(datetime.combine(end + timedelta(days=1), day_time.min, tzinfo=timezone.utc).timestamp())
    return start, end, start_s, end_s


def _amount(value):
    if isinstance(value, bool):
        return 0.0
    try:
        result = float(value)
    except (TypeError, ValueError, OverflowError):
        return 0.0
    return result if math.isfinite(result) and result > 0 else 0.0


def _clean(value, pattern=None, maxlen=64):
    if not isinstance(value, str):
        return ""
    value = value.strip()[:maxlen]
    if not value or (pattern is not None and not pattern.fullmatch(value)):
        return ""
    return value


def _context(payload):
    context = payload.get("context")
    if not isinstance(context, dict):
        context = {}
    utm = context.get("utm")
    if not isinstance(utm, dict):
        utm = {}
    device = _clean(context.get("device"), maxlen=16)
    browser = _clean(context.get("browser"), BROWSER_RE, 40)
    system = _clean(context.get("os"), BROWSER_RE, 40)
    lang = _clean(context.get("lang"), LANG_RE, 12)
    screen = _clean(context.get("screen"), SCREEN_RE, 12)
    source = _clean(context.get("source"), SOURCE_RE, 64).lower()
    session = _clean(context.get("session"), VISITOR_RE, 80)
    return (
        session,
        device if device in DEVICES else "",
        browser,
        system,
        lang,
        screen,
        source,
        _clean(utm.get("source"), UTM_RE, 64),
        _clean(utm.get("medium"), UTM_RE, 64),
        _clean(utm.get("campaign"), UTM_RE, 64),
    )


@router.post("/api/v6/analytics/events")
async def collect_events(request: Request):
    payload = await request.json()
    if not isinstance(payload, dict):
        fail(400, "invalid_analytics_payload", "Пакет событий должен быть объектом JSON.")
    if payload.get("consent") is not True:
        fail(403, "analytics_consent_required", "Для передачи статистики нужно разрешение посетителя.")
    events = payload.get("events")
    if not isinstance(events, list) or not 1 <= len(events) <= 40:
        fail(400, "invalid_analytics_batch", "Пакет должен содержать от 1 до 40 событий.")

    stored = []
    for event in events:
        if not isinstance(event, dict):
            fail(400, "invalid_analytics_event", "Событие статистики имеет неверный формат.")
        event_type = event.get("type")
        page = event.get("page")
        visitor = event.get("visitor")
        if not isinstance(event_type, str) or event_type not in EVENT_TYPES or not isinstance(page, str) or page not in PUBLIC_PAGES or not isinstance(visitor, str) or not VISITOR_RE.fullmatch(visitor):
            fail(400, "invalid_analytics_event", "Событие статистики содержит неизвестную страницу или тип.")
        item_id = event.get("itemId", "")
        if item_id and (not isinstance(item_id, str) or not ITEM_RE.fullmatch(item_id)):
            fail(400, "invalid_analytics_event", "Идентификатор предмета в событии имеет неверный формат.")
        quantity = event.get("quantity", 0)
        if isinstance(quantity, bool) or not isinstance(quantity, int) or quantity < 0 or quantity > 2304:
            fail(400, "invalid_analytics_event", "Количество в событии должно быть целым числом от 0 до 2304.")
        stored.append((uuid.uuid4().hex, event_type, page, digest(visitor), item_id or "", quantity))

    context = _context(payload)
    database = request.app.state.database
    now = int(time.time())
    with database.transaction() as db:
        db.execute("DELETE FROM cm_site_analytics_events WHERE created_s < %s", (now - RETENTION_SECONDS,))
        for event_id, event_type, page, visitor_hash, item_id, quantity in stored:
            db.execute(
                "INSERT INTO cm_site_analytics_events(id,event_type,page,visitor_hash,item_id,quantity,"
                "session_hash,device,browser,os,lang,screen,source,utm_source,utm_medium,utm_campaign,created_s) "
                "VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                (event_id, event_type, page, visitor_hash, item_id, quantity, *context, now),
            )
    return {"accepted": len(stored)}


@router.post("/api/v6/analytics/consent")
async def record_consent(request: Request):
    payload = await request.json()
    if not isinstance(payload, dict):
        fail(400, "invalid_consent_payload", "Запись согласия должна быть объектом JSON.")
    consent_id = payload.get("consentId")
    if not isinstance(consent_id, str) or not VISITOR_RE.fullmatch(consent_id):
        fail(400, "invalid_consent", "Не удалось определить запись согласия.")
    analytics = payload.get("analytics") is True
    preferences = payload.get("preferences") is True
    policy = _clean(payload.get("policyVersion"), maxlen=32) or CONSENT_POLICY_VERSION
    source = payload.get("source")
    source = "settings" if source == "settings" else "banner"
    visitor = payload.get("visitor")
    visitor_hash = digest(visitor) if isinstance(visitor, str) and VISITOR_RE.fullmatch(visitor) else ""
    user_agent = str(request.headers.get("user-agent") or "")[:300]
    consent_hash = digest(consent_id)
    database = request.app.state.database
    now = int(time.time())
    with database.transaction() as db:
        db.execute("DELETE FROM cm_site_consents WHERE created_s < %s", (now - CONSENT_RETENTION_SECONDS,))
        previous = db.execute(
            "SELECT analytics,preferences,created_s FROM cm_site_consents WHERE consent_hash=%s ORDER BY created_s DESC LIMIT 1",
            (consent_hash,),
        ).fetchone()
        changed = (
            previous is None
            or int(previous["analytics"]) != int(analytics)
            or int(previous["preferences"]) != int(preferences)
            or now - int(previous["created_s"]) > 300
        )
        if changed:
            db.execute(
                "INSERT INTO cm_site_consents(id,consent_hash,visitor_hash,necessary,analytics,preferences,"
                "policy_version,source,user_agent,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                (uuid.uuid4().hex, consent_hash, visitor_hash, 1, int(analytics), int(preferences), policy, source, user_agent, now),
            )
    return {"ok": True, "analytics": analytics, "preferences": preferences, "policyVersion": policy}


@router.get("/api/v6/admin/analytics")
def admin_analytics(request: Request):
    current_user(request, "analytics.read")
    start, end, start_s, end_s = _date_range(request)
    database = request.app.state.database
    date_expression = "strftime('%Y-%m-%d', created_s, 'unixepoch')" if database.sqlite else "to_char(to_timestamp(created_s) AT TIME ZONE 'UTC', 'YYYY-MM-DD')"
    now = int(time.time())

    with database.transaction() as db:
        db.execute("DELETE FROM cm_site_analytics_events WHERE created_s < %s", (now - RETENTION_SECONDS,))
        event_rows = db.execute(
            f"SELECT {date_expression} AS day,event_type AS type,page,COUNT(*) AS count "
            "FROM cm_site_analytics_events WHERE created_s >= %s AND created_s < %s "
            f"GROUP BY {date_expression},event_type,page ORDER BY day,event_type,page",
            (start_s, end_s),
        ).fetchall()
        page_rows = db.execute(
            "SELECT page,COUNT(*) AS count FROM cm_site_analytics_events "
            "WHERE event_type='page_view' AND created_s >= %s AND created_s < %s GROUP BY page",
            (start_s, end_s),
        ).fetchall()
        visit_rows = db.execute(
            f"SELECT {date_expression} AS day,COUNT(*) AS count FROM cm_site_analytics_events "
            "WHERE event_type='page_view' AND created_s >= %s AND created_s < %s "
            f"GROUP BY {date_expression} ORDER BY day",
            (start_s, end_s),
        ).fetchall()
        visitor_rows = db.execute(
            f"SELECT {date_expression} AS day,COUNT(DISTINCT visitor_hash) AS count "
            "FROM cm_site_analytics_events WHERE event_type='page_view' AND created_s >= %s AND created_s < %s "
            f"GROUP BY {date_expression}",
            (start_s, end_s),
        ).fetchall()
        audience = {}
        for key, column in (("devices", "device"), ("browsers", "browser"), ("os", "os"), ("languages", "lang"), ("screens", "screen")):
            rows = db.execute(
                f"SELECT {column} AS name,COUNT(*) AS count FROM cm_site_analytics_events "
                f"WHERE event_type='page_view' AND {column}<>'' AND created_s >= %s AND created_s < %s "
                f"GROUP BY {column} ORDER BY count DESC",
                (start_s, end_s),
            ).fetchall()
            audience[key] = [{"name": row["name"], "count": int(row["count"])} for row in rows[:20]]
        source_rows = db.execute(
            "SELECT source AS name,COUNT(*) AS count FROM cm_site_analytics_events "
            "WHERE event_type='page_view' AND source<>'' AND created_s >= %s AND created_s < %s "
            "GROUP BY source ORDER BY count DESC",
            (start_s, end_s),
        ).fetchall()
        utm_rows = db.execute(
            "SELECT utm_source,utm_medium,utm_campaign,COUNT(*) AS count FROM cm_site_analytics_events "
            "WHERE event_type='page_view' AND utm_source<>'' AND created_s >= %s AND created_s < %s "
            "GROUP BY utm_source,utm_medium,utm_campaign ORDER BY count DESC",
            (start_s, end_s),
        ).fetchall()
        session_row = db.execute(
            "SELECT COUNT(DISTINCT session_hash) AS count FROM cm_site_analytics_events "
            "WHERE session_hash<>'' AND created_s >= %s AND created_s < %s",
            (start_s, end_s),
        ).fetchone()
        unique_row = db.execute(
            "SELECT COUNT(DISTINCT visitor_hash) AS count FROM cm_site_analytics_events "
            "WHERE event_type='page_view' AND created_s >= %s AND created_s < %s",
            (start_s, end_s),
        ).fetchone()
        returning_row = db.execute(
            "SELECT COUNT(*) AS count FROM ("
            "SELECT visitor_hash,MIN(created_s) AS first_s FROM cm_site_analytics_events "
            "WHERE visitor_hash IN (SELECT visitor_hash FROM cm_site_analytics_events "
            "WHERE event_type='page_view' AND created_s >= %s AND created_s < %s) GROUP BY visitor_hash"
            ") AS first_seen WHERE first_s < %s",
            (start_s, end_s, start_s),
        ).fetchone()
        consent_row = db.execute(
            "SELECT COUNT(*) AS total,SUM(CASE WHEN analytics=1 THEN 1 ELSE 0 END) AS analytics,"
            "SUM(CASE WHEN preferences=1 THEN 1 ELSE 0 END) AS preferences FROM cm_site_consents "
            "WHERE created_s >= %s AND created_s < %s",
            (start_s, end_s),
        ).fetchone()
        consent_recent = db.execute(
            "SELECT analytics,preferences,source,created_s FROM cm_site_consents "
            "WHERE created_s >= %s AND created_s < %s ORDER BY created_s DESC LIMIT 20",
            (start_s, end_s),
        ).fetchall()
        account_row = db.execute(
            "SELECT COUNT(*) AS total,"
            "SUM(CASE WHEN created_s >= %s AND created_s < %s THEN 1 ELSE 0 END) AS new_users,"
            "SUM(CASE WHEN last_login_s >= %s AND last_login_s < %s THEN 1 ELSE 0 END) AS active_users,"
            "SUM(CASE WHEN minecraft_uuid<>'' THEN 1 ELSE 0 END) AS linked_users "
            "FROM cm_site_users",
            (start_s, end_s, start_s, end_s),
        ).fetchone()
        receipts = db.execute(
            "SELECT actor_id,result,created_s FROM cm_site_receipts "
            "WHERE operation='checkout' AND status='COMMITTED' AND created_s >= %s AND created_s < %s",
            (start_s, end_s),
        ).fetchall()

    days = {}
    for row in visit_rows:
        day = str(row["day"])
        days[day] = {"at": day, "AR": 0, "CM": 0, "rubles": 0, "visits": int(row["count"])}
    for row in visitor_rows:
        day = str(row["day"])
        days.setdefault(day, {"at": day, "AR": 0, "CM": 0, "rubles": 0, "visits": 0})["visitors"] = int(row["count"])

    pages = sorted(
        ({"page": row["page"], "count": int(row["count"])} for row in page_rows),
        key=lambda row: (-row["count"], row["page"]),
    )
    events = [
        {"day": str(row["day"]), "type": row["type"], "page": row["page"], "count": int(row["count"])}
        for row in event_rows
    ]
    visits = sum(int(row["count"]) for row in visit_rows)
    visitors = sum(int(row["count"]) for row in visitor_rows)

    revenue = {"AR": 0.0, "CM": 0.0}
    sold_orders = set()
    buyers = set()
    item_total = 0
    pending_orders = set()
    failed_orders = set()
    refunded_orders = set()
    top = {}
    for receipt in receipts:
        try:
            result = json.loads(receipt["result"] or "{}")
        except (TypeError, ValueError):
            continue
        for index, order in enumerate(result.get("orders", [])):
            if not isinstance(order, dict):
                continue
            status = str(order.get("orderStatus") or order.get("status") or "").lower()
            order_id = str(order.get("orderId") or order.get("id") or f"{receipt['actor_id']}:{receipt['created_s']}:{index}")
            order_at = order.get("at")
            try:
                order_s = int(order_at) // 1000 if int(order_at) > 0 else int(receipt["created_s"])
            except (TypeError, ValueError, OverflowError):
                order_s = int(receipt["created_s"])
            if not start_s <= order_s < end_s:
                continue
            if status in {"queued", "pending", "awaiting_delivery", "pending_delivery", "partially_delivered"}:
                pending_orders.add(order_id)
            if status in {"failed", "error", "partially_failed", "refunded"}:
                failed_orders.add(order_id)
            if status == "refunded":
                refunded_orders.add(order_id)
            currency = order.get("currency")
            item_id = str(order.get("itemId") or "")
            quantity = order.get("qty", 1)
            try:
                quantity = max(0, min(2304, int(quantity)))
            except (TypeError, ValueError, OverflowError):
                quantity = 0
            amount = _amount(order.get("total"))
            if status != "delivered" or currency not in revenue or not item_id:
                continue
            sold_orders.add(order_id)
            buyers.add(str(order.get("playerId") or receipt["actor_id"]))
            item_total += quantity
            revenue[currency] += amount
            day = datetime.fromtimestamp(order_s, timezone.utc).date().isoformat()
            days.setdefault(day, {"at": day, "AR": 0, "CM": 0, "rubles": 0, "visits": 0})[currency] += amount
            key = (item_id, currency)
            summary = top.setdefault(key, {"id": item_id, "name": item_id, "qty": 0, "revenue": 0.0, "currency": currency, "orderIds": set()})
            summary["qty"] += quantity
            summary["revenue"] += amount
            summary["orderIds"].add(order_id)

    data = {
        "revenue": {key: round(value, 2) for key, value in revenue.items()},
        "rubles": 0,
        "paymentsAvailable": False,
        "orders": len(sold_orders),
        "buyers": len(buyers),
        "items": item_total,
        "visits": visits,
        "visitors": visitors,
        "engagement": {
            "uniqueVisitors": int(unique_row["count"] or 0) if unique_row else 0,
            "sessions": int(session_row["count"] or 0) if session_row else 0,
            "returning": int(returning_row["count"] or 0) if returning_row else 0,
        },
        "audience": audience,
        "sources": [{"name": row["name"], "count": int(row["count"])} for row in source_rows],
        "campaigns": [
            {
                "source": row["utm_source"],
                "medium": row["utm_medium"],
                "campaign": row["utm_campaign"],
                "count": int(row["count"]),
            }
            for row in utm_rows[:30]
        ],
        "consents": {
            "total": int(consent_row["total"] or 0) if consent_row else 0,
            "analytics": int(consent_row["analytics"] or 0) if consent_row else 0,
            "preferences": int(consent_row["preferences"] or 0) if consent_row else 0,
            "policyVersion": CONSENT_POLICY_VERSION,
            "recent": [
                {
                    "analytics": bool(int(row["analytics"])),
                    "preferences": bool(int(row["preferences"])),
                    "source": row["source"],
                    "at": int(row["created_s"]) * 1000,
                }
                for row in consent_recent
            ],
        },
        "accounts": {
            "total": int(account_row["total"] or 0) if account_row else 0,
            "new": int(account_row["new_users"] or 0) if account_row else 0,
            "active": int(account_row["active_users"] or 0) if account_row else 0,
            "linked": int(account_row["linked_users"] or 0) if account_row else 0,
        },
        "top": [
            {**{key: value for key, value in row.items() if key != "orderIds"}, "orders": len(row["orderIds"])}
            for row in sorted(top.values(), key=lambda row: (-row["revenue"], row["id"]))[:30]
        ],
        "pending": len(pending_orders),
        "failed": len(failed_orders),
        "refunded": len(refunded_orders),
        "days": [days[key] for key in sorted(days)],
        "pages": pages,
        "events": events,
        "from": start.isoformat(),
        "to": end.isoformat(),
    }
    return {"data": data}
