"""Счётчик попаданий на страницу-пасхалку (418).

Кнопки нигде нет: попасть можно только зная адрес. Запись хранит всё, что сайт
и так знает о запросе, чтобы потом было видно, кто додумался.
"""

import hashlib
import re
import time
import uuid

from fastapi import APIRouter, Request

from .security import COOKIE, current_user, digest, fail, throttle

router = APIRouter()

# Сколько попаданий храним: этого хватает, чтобы увидеть гостей, а таблица не растёт бесконечно.
KEEP = 5000
TEXT = re.compile(r"[\x00-\x1f\x7f]")


def _clean(value, maximum):
    return TEXT.sub("", str(value or ""))[:maximum]


def _visitor(request):
    """Middleware ищет сеанс только под /api/, а нам нужен и обычный адрес страницы."""
    user = getattr(request.state, "user", None)
    if user:
        return user
    token = request.cookies.get(COOKIE)
    if not token:
        return None
    try:
        with request.app.state.database.transaction() as db:
            row = db.execute("SELECT u.* FROM cm_site_sessions s JOIN cm_site_users u ON u.id=s.user_id"
                             " WHERE s.token_hash=%s AND s.revoked_s=0 AND s.expires_s>%s AND u.enabled=1",
                             (digest(token), int(time.time()))).fetchone()
    except Exception:  # noqa: BLE001
        return None
    return dict(row) if row else None


def record(request, status=418):
    """Пишет попадание. Ошибка записи не должна ломать саму страницу."""
    database = request.app.state.database
    user = _visitor(request)
    ip = request.client.host if request.client else ""
    try:
        with database.transaction() as db:
            db.execute(
                "INSERT INTO cm_site_easter_eggs(id,visitor_hash,user_id,user_name,ip,user_agent,referer,path,status,created_s)"
                " VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                (uuid.uuid4().hex, hashlib.sha256((ip + "|" + request.headers.get("user-agent", "")).encode()).hexdigest()[:32],
                 (user or {}).get("id", ""), (user or {}).get("name", "")[:64], ip[:64],
                 _clean(request.headers.get("user-agent"), 300), _clean(request.headers.get("referer"), 300),
                 _clean(request.url.path, 200), status, int(time.time())),
            )
            db.execute(
                "DELETE FROM cm_site_easter_eggs WHERE id NOT IN (SELECT id FROM cm_site_easter_eggs"
                " ORDER BY created_s DESC, id DESC LIMIT %s)", (KEEP,),
            )
    except Exception:  # noqa: BLE001 — журнал не важнее самой страницы
        pass


@router.get("/api/v6/admin/easter")
def read_easter_eggs(request: Request):
    current_user(request, "logs.view")
    try:
        limit = min(max(int(request.query_params.get("limit", "200")), 1), 1000)
    except ValueError:
        limit = 200
    with request.app.state.database.transaction() as db:
        rows = db.execute(
            "SELECT id,user_id,user_name,ip,user_agent,referer,path,status,created_s"
            " FROM cm_site_easter_eggs ORDER BY created_s DESC, id DESC LIMIT %s", (limit,),
        ).fetchall()
        total = db.execute("SELECT count(*) AS n FROM cm_site_easter_eggs").fetchone()["n"]
    guests = sum(1 for row in rows if not row["user_id"])
    return {"total": int(total), "guests": guests, "entries": [
        {"id": row["id"], "user_id": row["user_id"] or "", "user": row["user_name"] or "Гость",
         "ip": row["ip"], "agent": row["user_agent"], "referer": row["referer"],
         "path": row["path"], "status": row["status"], "at": row["created_s"] * 1000}
        for row in rows]}


@router.delete("/api/v6/admin/easter")
def clear_easter_eggs(request: Request):
    user = current_user(request, "logs.view", fresh=True)
    database = request.app.state.database
    with database.transaction() as db:
        db.execute("DELETE FROM cm_site_easter_eggs")
    database.audit(user, "easter.cleared", "checkpoint")
    return {"ok": True}


def guard(request):
    """Троттлинг: без него бот заполнит журнал за минуту."""
    throttle(request.app.state.database, "easter:" + (request.client.host if request.client else ""), 60, 3600)


__all__ = ["router", "record", "guard", "fail"]
