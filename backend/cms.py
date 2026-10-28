"""Versioned website content editing with a public-only projection."""

import base64
import binascii
import copy
import hashlib
import hmac
import io
import json
import os
import re
import time
import uuid
from pathlib import Path

from fastapi import APIRouter, Request
from fastapi.responses import FileResponse
from PIL import Image, ImageOps, UnidentifiedImageError

from .security import current_user, fail

router = APIRouter()
SECTIONS = {"pages", "gallery", "faq"}
_ID = re.compile(r"^[a-z0-9][a-z0-9_-]{0,63}$")
_SLUG = re.compile(r"^[a-z0-9][a-z0-9-]{1,39}$")
_HTML = re.compile(r"<\s*/?\s*[a-z!][^>]*>|javascript\s*:|data\s*:\s*text/html|on(?:error|load|click)\s*=", re.I)
_ASSET = re.compile(r"^assets/[A-Za-z0-9_./-]+\.(?:png|jpe?g|webp|avif|gif)$", re.I)
_GALLERY_ASSET = re.compile(r"^/api/v6/gallery/[a-f0-9]{24}\.(?:png|jpe?g|webp|gif)$", re.I)
_GALLERY_FILENAME = re.compile(r"^[a-f0-9]{24}\.(?:png|jpe?g|webp|gif)$", re.I)
_GALLERY_MIMES = {"PNG": ("png", "image/png"), "JPEG": ("jpg", "image/jpeg"), "WEBP": ("webp", "image/webp"), "GIF": ("gif", "image/gif")}
_MAX_GALLERY_BYTES = 4 * 1024 * 1024
_SETTING_FIELDS = {"siteTitle", "headline", "description", "serverIp", "gameVersion", "discordInvite"}
_DOMAIN = re.compile(r"^(?=.{3,253}$)[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?(?::[0-9]{1,5})?$")
_DISCORD_INVITE = re.compile(r"^https://(?:discord\.gg/[A-Za-z0-9_-]+|discord\.com/invite/[A-Za-z0-9_-]+)$")


def _seed(request):
    path = request.app.state.settings.frontend / "data" / "content-v6-public.json"
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        return {}
    gallery_path = Path(request.app.state.settings.private) / "gallery-v73.json"
    if gallery_path.is_file():
        defaults = json.loads(gallery_path.read_text(encoding="utf-8"))
        if isinstance(defaults, list):
            gallery = value.get("gallery")
            if not isinstance(gallery, list):
                gallery = []
                value["gallery"] = gallery
            known_ids = {str(row.get("id", "")) for row in gallery if isinstance(row, dict)}
            for row in defaults:
                identifier = str(row.get("id", "")) if isinstance(row, dict) else ""
                if identifier and identifier not in known_ids:
                    gallery.append(row)
                    known_ids.add(identifier)
    return value


def _value(db, seed, section):
    row = db.execute("SELECT value FROM cm_site_settings WHERE key=%s", (section,)).fetchone()
    value = json.loads(row["value"]) if row else seed.get(section, [])
    return value if isinstance(value, list) else []


def _site_config(db, seed):
    row = db.execute("SELECT value FROM cm_site_settings WHERE key=%s", ("public_config",)).fetchone()
    saved = json.loads(row["value"]) if row else {}
    defaults = seed.get("config", {})
    result = {key: value for key, value in defaults.items() if key in _SETTING_FIELDS}
    if not saved:
        result["headline"] = "CopiMine —\nMinecraft-сервер."
        result["description"] = "Строй и играй вместе с другими. Банк, выборы и магазин — на сайте."
    result.update({key: value for key, value in saved.items() if key in _SETTING_FIELDS})
    return result


def _gallery_rows(db, seed):
    rows = copy.deepcopy(_value(db, seed, "gallery"))
    known_ids = {str(row.get("id", "")) for row in rows if isinstance(row, dict)}
    for row in seed.get("gallery", []):
        if not isinstance(row, dict):
            continue
        identifier = str(row.get("id", ""))
        if identifier and identifier not in known_ids:
            rows.append(copy.deepcopy(row))
            known_ids.add(identifier)
    rows.sort(key=lambda x: (int(x.get("position", 0)), str(x.get("id", ""))))
    return rows


def _gallery_file(settings, filename):
    if not _GALLERY_FILENAME.fullmatch(filename):
        return None
    root = (Path(settings.private) / "gallery-assets").resolve()
    path = (root / filename).resolve()
    return path if path.parent == root else None


def _gallery_bytes(encoded):
    if not isinstance(encoded, str) or not encoded or len(encoded) > 5_600_000:
        fail(413, "gallery_image_too_large", "Изображение должно быть не больше 4 МБ.")
    try:
        data = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError):
        fail(400, "invalid_gallery_image", "Не удалось прочитать файл изображения.")
    if not data or len(data) > _MAX_GALLERY_BYTES:
        fail(413, "gallery_image_too_large", "Изображение должно быть не больше 4 МБ.")
    try:
        with Image.open(io.BytesIO(data)) as image:
            image_format = image.format
            kind = _GALLERY_MIMES.get(image_format or "")
            if not kind or image.width < 1 or image.height < 1 or image.width > 8000 or image.height > 8000 or image.width * image.height > 16_000_000:
                fail(400, "invalid_gallery_image", "Поддерживаются PNG, JPEG, WebP и GIF размером до 8 000 × 8 000 пикселей.")
            image.verify()
        with Image.open(io.BytesIO(data)) as image:
            image.seek(0)
            image = ImageOps.exif_transpose(image)
            image.load()
            has_alpha = image.mode in ("RGBA", "LA", "PA") or "transparency" in image.info
            image = image.convert("RGBA" if has_alpha else "RGB")
            output = io.BytesIO()
            image.save(output, format="WEBP", quality=88, method=4)
            data = output.getvalue()
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
        fail(400, "invalid_gallery_image", "Файл повреждён или не является поддерживаемым изображением.")
    if not data or len(data) > _MAX_GALLERY_BYTES:
        fail(413, "gallery_image_too_large", "После обработки изображение превышает 4 МБ.")
    return data, "webp", "image/webp"


def public_config(request):
    database = request.app.state.database
    seed = _seed(request)
    with database.transaction() as db:
        return _site_config(db, seed)


def _version(value):
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:24]


def _safe_text(value, label, maximum, *, required=True):
    if not isinstance(value, str):
        fail(400, "invalid_content", f"Поле «{label}» должно быть текстом.")
    value = value.strip()
    if (required and not value) or len(value) > maximum or "\x00" in value or _HTML.search(value):
        fail(400, "invalid_content", f"Проверьте поле «{label}»: HTML и активные ссылки запрещены.")
    return value


def _validate_item(section, item, current):
    if not isinstance(item, dict):
        fail(400, "invalid_content", "Не удалось прочитать карточку содержимого.")
    identifier = str(item.get("id", "")).strip()
    if not _ID.fullmatch(identifier):
        fail(400, "invalid_content", "Укажите короткий латинский ID без пробелов.")
    if section == "pages":
        slug = str(item.get("slug", "")).strip()
        if not _SLUG.fullmatch(slug):
            fail(400, "invalid_content", "Адрес страницы: 2–40 латинских букв, цифр или дефисов.")
        draft_source = item.get("draft")
        legacy_published = item.get("published") if isinstance(item.get("published"), dict) else None
        if draft_source is None:
            draft_source = legacy_published or (current or {}).get("draft")
        if not isinstance(draft_source, dict):
            fail(400, "invalid_content", "У страницы нет текста черновика.")
        draft = {
            "title": _safe_text(draft_source.get("title", ""), "заголовок", 100),
            "body": _safe_text(draft_source.get("body", ""), "текст страницы", 16000),
            "description": _safe_text(draft_source.get("description", ""), "описание", 220, required=False),
            "nav": draft_source.get("nav") is True,
        }
        revision = int((current or {}).get("revision", 0)) + 1
        publishing = item.get("publish") is True
        previous_publication = (current or {}).get("published") is True or isinstance((current or {}).get("published"), dict)
        published = previous_publication if current else isinstance(item.get("published"), dict)
        stored_publication = (current or {}).get("publishedContent")
        if stored_publication is None:
            stored_publication = (current or {}).get("published") if isinstance((current or {}).get("published"), dict) else legacy_published
        published_content = copy.deepcopy(stored_publication)
        if publishing:
            published = True
            published_content = copy.deepcopy(draft)
        if item.get("unpublish") is True:
            published = False
        return {
            **(current or {}), "id": identifier, "slug": slug, "draft": draft,
            "published": published, "publishedContent": published_content,
            "revision": revision,
            "publishedRevision": revision if publishing else (current or {}).get("publishedRevision", revision if legacy_published else 0),
            "publishedAt": int(time.time() * 1000) if publishing else (current or {}).get("publishedAt", 0),
        }
    if section == "faq":
        return {
            **(current or {}), "id": identifier,
            "question": _safe_text(item.get("question", ""), "вопрос", 150),
            "answer": _safe_text(item.get("answer", ""), "ответ", 1800),
            "published": item.get("published") is True,
        }
    src = str(item.get("src", ""))
    if not ((_ASSET.fullmatch(src) and ".." not in src.split("/")) or _GALLERY_ASSET.fullmatch(src)):
        fail(400, "invalid_content", "Выберите изображение из галереи сайта.")
    try:
        position = int(item.get("position", 0))
    except (TypeError, ValueError):
        fail(400, "invalid_content", "Порядок изображения должен быть числом.")
    if not 0 <= position <= 1000:
        fail(400, "invalid_content", "Порядок изображения вне допустимого диапазона.")
    return {
        **(current or {}), "id": identifier, "src": src,
        "title": _safe_text(item.get("title", ""), "название", 80),
        "caption": _safe_text(item.get("caption", ""), "подпись", 180, required=False),
        "alt": _safe_text(item.get("alt", ""), "описание изображения", 200),
        "position": position, "published": item.get("published") is True,
    }


def public_sections(request):
    """Return only content approved for visitors; drafts never enter site state."""
    database = request.app.state.database
    seed = _seed(request)
    with database.transaction() as db:
        sections = {
            name: _gallery_rows(db, seed) if name == "gallery" else _value(db, seed, name)
            for name in SECTIONS
        }
    pages = []
    for row in sections["pages"]:
        if not isinstance(row, dict) or row.get("archived") is True:
            continue
        published = row.get("publishedContent")
        if published is None and isinstance(row.get("published"), dict):
            published = row["published"]
        if not published or (row.get("published") is False and row.get("publishedContent") is not None):
            continue
        pages.append({
            "id": row.get("id", ""), "slug": row.get("slug", ""),
            "published": copy.deepcopy(published),
            "publishedRevision": row.get("publishedRevision", row.get("revision", 1)),
            "publishedAt": row.get("publishedAt", 0),
        })
    faq = [x for x in sections["faq"] if isinstance(x, dict) and x.get("published") is True and x.get("archived") is not True]
    gallery = [x for x in sections["gallery"] if isinstance(x, dict) and x.get("published") is True and x.get("archived") is not True]
    gallery.sort(key=lambda x: (int(x.get("position", 0)), str(x.get("id", ""))))
    return {"pages": pages, "gallery": gallery, "faq": faq}


@router.get("/api/v6/admin/content")
def get_content(request: Request):
    current_user(request, "content.manage")
    seed = _seed(request)
    with request.app.state.database.transaction() as db:
        values = {
            name: _gallery_rows(db, seed) if name == "gallery" else _value(db, seed, name)
            for name in SECTIONS
        }
    return {"data": values, "versions": {name: _version(value) for name, value in values.items()}}


@router.post("/api/v6/admin/content/gallery/image")
async def upload_gallery_image(request: Request):
    user = current_user(request, "content.manage", fresh=True)
    data = await request.json()
    reason = _safe_text(data.get("reason", ""), "причина изменения", 500)
    expected = data.get("expectedVersion")
    if not isinstance(expected, str) or len(expected) != 24:
        fail(400, "content_version_required", "Обнови редактор перед загрузкой.")
    image_data, extension, _ = _gallery_bytes(data.get("image"))
    item_id = uuid.uuid4().hex[:24]
    filename = f"{item_id}.{extension}"
    src = f"/api/v6/gallery/{filename}"
    item = _validate_item("gallery", {
        "id": item_id, "src": src, "title": data.get("title", ""),
        "caption": data.get("caption", ""), "alt": data.get("alt", ""),
        "position": data.get("position", 0), "published": data.get("published") is True,
    }, None)
    path = _gallery_file(request.app.state.settings, filename)
    if path is None:
        fail(500, "gallery_storage_unavailable", "Не удалось подготовить хранилище изображений.")
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o750)
    created = False
    try:
        with path.open("xb") as output:
            created = True
            output.write(image_data)
        try:
            os.chmod(path, 0o640)
        except OSError:
            pass
        database = request.app.state.database
        seed = _seed(request)
        now = int(time.time())
        with database.transaction() as db:
            if not db.sqlite:
                db.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", ("copimine-cms:gallery",))
            rows = _gallery_rows(db, seed)
            if not hmac.compare_digest(_version(rows), expected):
                fail(409, "content_changed", "Галерея уже изменилась. Обнови редактор и повтори загрузку.")
            rows.append(item)
            rows.sort(key=lambda x: (int(x.get("position", 0)), str(x.get("id", ""))))
            encoded = json.dumps(rows, ensure_ascii=False, separators=(",", ":"))
            if len(encoded.encode("utf-8")) > 110 * 1024:
                fail(413, "content_too_large", "Галерея превышает допустимый размер.")
            db.execute(
                "INSERT INTO cm_site_settings(key,value,updated_s,actor) VALUES(%s,%s,%s,%s) "
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_s=excluded.updated_s,actor=excluded.actor",
                ("gallery", encoded, now, user["name"]),
            )
            db.execute(
                "INSERT INTO cm_site_audit(id,actor_id,actor_name,action,target,reason,details,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)",
                (uuid.uuid4().hex, user["id"], user["name"], "content.gallery.uploaded", "gallery:" + item_id, reason,
                 json.dumps({"published": item["published"], "bytes": len(image_data)}, ensure_ascii=False), now),
            )
    except Exception:
        if created:
            path.unlink(missing_ok=True)
        raise
    return {"ok": True, "item": item, "version": _version(rows)}


@router.get("/api/v6/admin/content/gallery/image/{item_id}")
def admin_gallery_image(request: Request, item_id: str):
    current_user(request, "content.manage")
    seed = _seed(request)
    with request.app.state.database.transaction() as db:
        item = next((x for x in _gallery_rows(db, seed) if isinstance(x, dict) and x.get("id") == item_id), None)
    if not item or not isinstance(item.get("src"), str) or not _GALLERY_ASSET.fullmatch(item["src"]):
        fail(404, "gallery_image_not_found", "Изображение галереи не найдено.")
    filename = item["src"].rsplit("/", 1)[-1]
    path = _gallery_file(request.app.state.settings, filename)
    if not path or not path.is_file():
        fail(404, "gallery_image_not_found", "Файл изображения не найден.")
    extension = filename.rsplit(".", 1)[1].lower()
    media_type = {"png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg", "webp": "image/webp", "gif": "image/gif"}[extension]
    return FileResponse(path, media_type=media_type, headers={"Cache-Control": "private, no-store", "X-Content-Type-Options": "nosniff"})


@router.get("/api/v6/gallery/{filename}")
def public_gallery_image(request: Request, filename: str):
    path = _gallery_file(request.app.state.settings, filename)
    if not path:
        fail(404, "gallery_image_not_found", "Изображение не опубликовано.")
    seed = _seed(request)
    with request.app.state.database.transaction() as db:
        published = any(isinstance(x, dict) and x.get("published") is True and x.get("src") == "/api/v6/gallery/" + filename for x in _gallery_rows(db, seed))
    if not published or not path.is_file():
        fail(404, "gallery_image_not_found", "Изображение не опубликовано.")
    extension = filename.rsplit(".", 1)[1].lower()
    media_type = {"png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg", "webp": "image/webp", "gif": "image/gif"}[extension]
    return FileResponse(path, media_type=media_type, headers={"Cache-Control": "public, max-age=60, must-revalidate", "X-Content-Type-Options": "nosniff"})


@router.patch("/api/v6/admin/content/{section}")
async def update_content_section(request: Request, section: str):
    user = current_user(request, "content.manage", fresh=True)
    if section not in SECTIONS:
        fail(404, "content_section_not_found", "Раздел содержимого не найден.")
    data = await request.json()
    reason = _safe_text(data.get("reason", ""), "причина изменения", 500)
    expected = data.get("expectedVersion")
    if not isinstance(expected, str) or len(expected) != 24:
        fail(400, "content_version_required", "Обнови редактор перед сохранением.")
    database = request.app.state.database
    seed = _seed(request)
    now = int(time.time())
    with database.transaction() as db:
        if not db.sqlite:
            db.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", ("copimine-cms:" + section,))
        rows = _gallery_rows(db, seed) if section == "gallery" else _value(db, seed, section)
        if not hmac.compare_digest(_version(rows), expected):
            fail(409, "content_changed", "Содержимое уже изменилось. Обнови редактор и повтори правку.")
        item = data.get("item")
        if not isinstance(item, dict):
            fail(400, "invalid_content", "Карточка содержимого отсутствует.")
        identifier = str(item.get("id", "")).strip()
        index = next((i for i, old in enumerate(rows) if isinstance(old, dict) and old.get("id") == identifier), None)
        current = rows[index] if index is not None else None
        clean = _validate_item(section, item, current)
        if section == "pages" and any(x.get("slug") == clean["slug"] and x.get("id") != clean["id"] for x in rows if isinstance(x, dict)):
            fail(409, "page_slug_exists", "Этот адрес уже занят другой страницей.")
        if index is None:
            rows.append(clean)
        else:
            rows[index] = clean
        if section == "gallery":
            rows.sort(key=lambda x: (int(x.get("position", 0)), str(x.get("id", ""))))
        encoded = json.dumps(rows, ensure_ascii=False, separators=(",", ":"))
        if len(encoded.encode("utf-8")) > 110 * 1024:
            fail(413, "content_too_large", "Раздел превышает допустимый размер.")
        db.execute(
            "INSERT INTO cm_site_settings(key,value,updated_s,actor) VALUES(%s,%s,%s,%s) "
            "ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_s=excluded.updated_s,actor=excluded.actor",
            (section, encoded, now, user["name"]),
        )
        db.execute(
            "INSERT INTO cm_site_audit(id,actor_id,actor_name,action,target,reason,details,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)",
            (uuid.uuid4().hex, user["id"], user["name"], "content.updated", section + ":" + identifier, reason,
             json.dumps({"section": section, "published": clean.get("published")}, ensure_ascii=False), now),
        )
    return {"ok": True, "item": clean, "version": _version(rows)}


@router.get("/api/v6/admin/site-settings")
def get_site_settings(request: Request):
    current_user(request, "settings.manage")
    seed = _seed(request)
    with request.app.state.database.transaction() as db:
        values = _site_config(db, seed)
    return {"data": values, "version": _version(values)}


@router.patch("/api/v6/admin/site-settings")
async def update_site_settings(request: Request):
    user = current_user(request, "settings.manage", fresh=True)
    data = await request.json()
    values = data.get("values")
    if not isinstance(values, dict) or not values or set(values) - _SETTING_FIELDS:
        fail(400, "invalid_site_settings", "В форме есть неподдерживаемые параметры сайта.")
    reason = _safe_text(data.get("reason", ""), "причина изменения", 500)
    expected = data.get("expectedVersion")
    if not isinstance(expected, str) or len(expected) != 24:
        fail(400, "settings_version_required", "Обнови форму перед сохранением.")
    seed = _seed(request)
    now = int(time.time())
    with request.app.state.database.transaction() as db:
        if not db.sqlite:
            db.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", ("copimine-site-settings",))
        current = _site_config(db, seed)
        if not hmac.compare_digest(_version(current), expected):
            fail(409, "site_settings_changed", "Настройки уже изменились. Обнови форму и повтори правку.")
        clean = dict(current)
        for key, value in values.items():
            if key == "serverIp":
                value = _safe_text(value, "адрес сервера", 253)
                if not _DOMAIN.fullmatch(value) or value.count(":") > 1:
                    fail(400, "invalid_site_settings", "Укажи домен или IP сервера без https://.")
            elif key == "discordInvite":
                value = _safe_text(value, "ссылка Discord", 180, required=False)
                if value and not _DISCORD_INVITE.fullmatch(value):
                    fail(400, "invalid_site_settings", "Нужна ссылка-приглашение discord.gg или discord.com/invite.")
            else:
                value = _safe_text(value, key, {"siteTitle": 50, "headline": 80, "description": 220, "gameVersion": 24}[key])
            clean[key] = value
        db.execute(
            "INSERT INTO cm_site_settings(key,value,updated_s,actor) VALUES(%s,%s,%s,%s) "
            "ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_s=excluded.updated_s,actor=excluded.actor",
            ("public_config", json.dumps(clean, ensure_ascii=False, separators=(",", ":")), now, user["name"]),
        )
        db.execute(
            "INSERT INTO cm_site_audit(id,actor_id,actor_name,action,target,reason,details,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)",
            (uuid.uuid4().hex, user["id"], user["name"], "site.settings.updated", "public_config", reason,
             json.dumps({"fields": sorted(values)}, ensure_ascii=False), now),
        )
    return {"ok": True, "data": clean, "version": _version(clean)}
