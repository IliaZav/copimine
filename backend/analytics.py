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
RETENTION_SECONDS = 400 * 86400


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

    database = request.app.state.database
    now = int(time.time())
    with database.transaction() as db:
        db.execute("DELETE FROM cm_site_analytics_events WHERE created_s < %s", (now - RETENTION_SECONDS,))
        for event_id, event_type, page, visitor_hash, item_id, quantity in stored:
            db.execute(
                "INSERT INTO cm_site_analytics_events(id,event_type,page,visitor_hash,item_id,quantity,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s)",
                (event_id, event_type, page, visitor_hash, item_id, quantity, now),
            )
    return {"accepted": len(stored)}


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
