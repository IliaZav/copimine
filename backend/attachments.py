"""Файлы и фото к обращениям игроков.

Файл сначала проверяется ClamAV и только потом попадает в хранилище: если
clamd недоступен, загрузка отклоняется — «не проверили» не равно «безопасно».
Загрузка идёт base64 в JSON, как в mapart: без лишних зависимостей.
"""
import asyncio
import base64
import binascii
import hashlib
import re
import secrets
import time
from pathlib import Path

from fastapi import APIRouter, Request
from fastapi.responses import FileResponse

from .security import current_user, fail, throttle
from . import antivirus

router = APIRouter()

MAX_BYTES = 8 * 1024 * 1024
MAX_FILES = 5
# Разрешённые типы: фото, PDF, логи/текст и архивы.
ALLOWED = {
    "image/png": ".png", "image/jpeg": ".jpg", "image/webp": ".webp", "image/gif": ".gif",
    "text/plain": ".txt", "text/csv": ".csv", "application/json": ".json",
    "application/pdf": ".pdf", "application/zip": ".zip",
    "application/x-zip-compressed": ".zip", "application/gzip": ".gz",
    "application/x-gzip": ".gz", "application/x-tar": ".tar",
}
# Сигнатура в первых байтах: заявленному MIME верить нельзя.
MAGIC = {
    b"\x89PNG\r\n\x1a\n": {".png"}, b"\xff\xd8\xff": {".jpg"}, b"GIF87a": {".gif"}, b"GIF89a": {".gif"},
    b"%PDF-": {".pdf"}, b"PK\x03\x04": {".zip"}, b"\x1f\x8b": {".gz"},
}
BINARY = {".png", ".jpg", ".gif", ".pdf", ".zip", ".gz"}
NAME = re.compile(r"[^A-Za-z0-9._ -]+")
FILE_ID = re.compile(r"[a-f0-9]{32}")


def _root(request):
    root = (Path(request.app.state.settings.private) / "ticket-files").resolve()
    root.mkdir(parents=True, exist_ok=True)
    return root


def _clean_name(value):
    return NAME.sub("_", str(value or "file")).strip(" ._")[:80] or "file"


def _scan(path):
    """status: clean | infected | unavailable. Коды clamdscan: 0 чисто, 1 найдено, 2 ошибка."""
    return antivirus.scan(path)


def _check_signature(data, suffix):
    for magic, suffixes in MAGIC.items():
        if data.startswith(magic):
            if suffix not in suffixes:
                fail(400, "file_type_mismatch", "Содержимое файла не совпадает с его типом.")
            return
    if suffix in BINARY:
        fail(400, "file_type_mismatch", "Содержимое файла не совпадает с его типом.")


@router.post("/api/v6/tickets/{identifier}/files")
async def upload_ticket_file(identifier: str, request: Request):
    user = current_user(request)
    throttle(request.app.state.database, "ticket-file:" + user["id"], 30, 3600)
    data = await request.json()
    mime = str(data.get("mime", "")).split(";")[0].strip().lower()
    suffix = ALLOWED.get(mime)
    if not suffix:
        fail(415, "file_type", "Можно приложить фото, PDF, текстовый файл или архив до 8 МБ.")
    encoded = data.get("data")
    if not isinstance(encoded, str) or not encoded:
        fail(400, "file_required", "Прикрепите файл.")
    if len(encoded) > MAX_BYTES * 2:
        fail(413, "file_too_large", "Файл больше 8 МБ.")
    try:
        payload = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError):
        fail(400, "invalid_file", "Не удалось прочитать файл.")
    if not payload:
        fail(400, "file_required", "Файл пустой.")
    if len(payload) > MAX_BYTES:
        fail(413, "file_too_large", "Файл больше 8 МБ.")
    _check_signature(payload, suffix)

    database = request.app.state.database
    with database.transaction() as db:
        ticket = db.execute("SELECT id,status FROM cm_site_tickets WHERE id=%s AND user_id=%s",
                            (identifier, user["id"])).fetchone()
        if not ticket:
            fail(404, "not_found", "Обращение не найдено.")
        if ticket["status"] == "closed":
            fail(409, "ticket_closed", "Обращение закрыто. Создай новое, если вопрос остался.")
        count = db.execute("SELECT count(*) AS n FROM cm_site_ticket_files WHERE ticket_id=%s",
                           (identifier,)).fetchone()["n"]
    if int(count) >= MAX_FILES:
        fail(409, "too_many_files", f"К обращению можно приложить не больше {MAX_FILES} файлов.")

    file_id = secrets.token_hex(16)
    target = _root(request) / (file_id + suffix)
    target.write_bytes(payload)
    status, detail = await asyncio.to_thread(_scan, target)
    if status != "clean":
        target.unlink(missing_ok=True)
        if status == "infected":
            database.audit(user, "ticket.file.infected", identifier, details={"name": data.get("name"), "detail": detail})
            fail(422, "file_infected", "Файл не прошёл проверку антивирусом и был удалён.")
        fail(503, "scan_unavailable", "Проверка антивирусом сейчас недоступна. Попробуйте позже или напишите без файла.")

    name = _clean_name(data.get("name"))
    now = int(time.time())
    with database.transaction() as db:
        db.execute(
            "INSERT INTO cm_site_ticket_files(id,ticket_id,uploader_id,uploader_name,name,mime,bytes,sha256,scan,scan_detail,created_s)"
            " VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (file_id, identifier, user["id"], user["display_name"] or user["name"], name, mime,
             len(payload), hashlib.sha256(payload).hexdigest(), status, detail, now),
        )
        db.execute("UPDATE cm_site_tickets SET updated_s=%s WHERE id=%s", (now, identifier))
    database.audit(user, "ticket.file", identifier, details={"name": name, "bytes": len(payload), "scan": status})
    return {"ok": True, "file": {"id": file_id, "name": name, "mime": mime, "bytes": len(payload), "at": now}}


@router.get("/api/v6/tickets/{identifier}/files")
def list_ticket_files(identifier: str, request: Request):
    user = current_user(request)
    manage = "tickets.manage" in (user.get("permissions") or []) or user.get("role") in {"owner", "admin", "moderator", "junior_admin", "helper"}
    with request.app.state.database.transaction() as db:
        ticket = db.execute("SELECT id FROM cm_site_tickets WHERE id=%s AND user_id=%s",
                            (identifier, user["id"])).fetchone()
        if not ticket and not manage:
            fail(404, "not_found", "Обращение не найдено.")
        rows = db.execute(
            "SELECT id,name,mime,bytes,uploader_name,created_s FROM cm_site_ticket_files WHERE ticket_id=%s ORDER BY created_s,id",
            (identifier,)).fetchall()
    return {"files": [
        {"id": r["id"], "name": r["name"], "mime": r["mime"], "bytes": r["bytes"], "by": r["uploader_name"], "at": r["created_s"]}
        for r in rows]}


@router.get("/ticket-file/{file_id}")
def download_ticket_file(file_id: str, request: Request):
    user = current_user(request)
    if not FILE_ID.fullmatch(file_id):
        fail(404, "not_found", "Файл не найден.")
    manage = "tickets.manage" in (user.get("permissions") or []) or user.get("role") in {"owner", "admin", "moderator", "junior_admin", "helper"}
    with request.app.state.database.transaction() as db:
        row = db.execute(
            "SELECT f.*,t.user_id FROM cm_site_ticket_files f JOIN cm_site_tickets t ON t.id=f.ticket_id WHERE f.id=%s",
            (file_id,)).fetchone()
    if not row or (row["user_id"] != user["id"] and not manage):
        fail(404, "not_found", "Файл не найден.")
    path = _root(request) / (row["id"] + ALLOWED.get(row["mime"], ""))
    if not path.is_file():
        fail(404, "not_found", "Файл не найден.")
    return FileResponse(path, media_type=row["mime"], filename=row["name"],
                        headers={"Cache-Control": "private, no-store", "X-Content-Type-Options": "nosniff"})