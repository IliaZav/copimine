"""Управление наркотиками и медиа карточек товаров.

Правила повторяют CopiMineNarcotics (NarcoticsConfigService): всё, что не прошло
проверку здесь, до сервера не доходит вообще. Запись в config.yml плагина сюда
не входит — сайт готовит проверенный документ и выгрузку.
"""

import base64
import binascii
import hashlib
import hmac
import io
import json
import re
import time
import uuid
from pathlib import Path

from fastapi import APIRouter, Request
from fastapi.responses import FileResponse
from PIL import Image, ImageOps, UnidentifiedImageError

from .security import current_user, fail

router = APIRouter()

SEED_KEY = "narcotics_config"
AUDIT_KEY = "narcotics.updated"

ITEM_ID = re.compile(r"^[a-z0-9][a-z0-9_]{0,63}$")
TEXTURE_KEY = re.compile(r"^narcotic_[a-z0-9_]{1,56}$")
MATERIAL_KEY = re.compile(r"^MATERIAL:([A-Z0-9_]+)$")
POTION_KEY = re.compile(r"^POTION:([A-Z0-9_]+)$")
HTML = re.compile(r"<\s*/?\s*[a-z!][^>]*>|javascript\s*:|data\s*:\s*text/html", re.I)
COLOURS = re.compile(r"[&§][0-9a-fk-or]")

# Псевдонимы из NarcoticsConfigService.canonicalEffectId.
ALIASES = {"CONFUSION": "NAUSEA", "HARM": "INSTANT_DAMAGE", "SLOW_DIGGING": "MINING_FATIGUE"}
EFFECTS = {
    "SPEED", "SLOWNESS", "HASTE", "MINING_FATIGUE", "STRENGTH", "INSTANT_HEALTH", "INSTANT_DAMAGE",
    "JUMP_BOOST", "NAUSEA", "REGENERATION", "RESISTANCE", "FIRE_RESISTANCE", "WATER_BREATHING",
    "INVISIBILITY", "BLINDNESS", "NIGHT_VISION", "HUNGER", "WEAKNESS", "POISON", "WITHER",
    "HEALTH_BOOST", "ABSORPTION", "SATURATION", "GLOWING", "LEVITATION", "LUCK", "UNLUCK",
    "SLOW_FALLING", "CONDUIT_POWER", "DOLPHINS_GRACE", "BAD_OMEN", "HERO_OF_THE_VILLAGE",
    "DARKNESS", "HARM", "CONFUSION", "SLOW_DIGGING", "WEAVING", "OOZING", "WIND_CHARGED",
    "INFESTED", "TRIAL_OMEN", "RAID_OMEN",
}
VISUALS = ["DESATURATE", "COLOR_CONVOLVE", "SCAN_PINCUSHION", "GREEN_NOISE", "INVERT", "WOBBLE", "BLOBS", "PENCIL", "CHAOS"]

MAX_ENCODED = 12 * 1024 * 1024
MAX_IMAGE_BYTES = 6 * 1024 * 1024
MAX_VIDEO_BYTES = 8 * 1024 * 1024
IMAGE_FORMATS = {"PNG": "png", "JPEG": "jpg", "WEBP": "webp", "GIF": "gif"}
VIDEO_MAGIC = {b"ftyp": "mp4", b"\x1aE\xdf\xa3": "webm"}
MEDIA_NAME = re.compile(r"^[a-f0-9]{24}\.(?:webp|mp4|webm)$")


# --------------------------------------------------------------------------- материалы

_MATERIALS = {"stamp": None, "ids": frozenset()}


def _material_ids(request):
    """Реальный каталог предметов Minecraft, который видит и админка."""
    path = request.app.state.settings.frontend / "data" / "materials-v6.json"
    try:
        stamp = path.stat().st_mtime_ns
    except OSError:
        fail(503, "materials_unavailable", "Каталог предметов не загружен. Повтори позже.")
    if _MATERIALS["stamp"] != stamp:
        try:
            rows = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            fail(503, "materials_unavailable", "Каталог предметов повреждён.")
        _MATERIALS["ids"] = frozenset(
            str(row.get("id", "")).upper() for row in rows
            if isinstance(row, dict) and row.get("giveable") is not False and row.get("id")
        )
        _MATERIALS["stamp"] = stamp
    return _MATERIALS["ids"]


# --------------------------------------------------------------------------- валидация

def _text(value, label, maximum, *, required=True):
    if not isinstance(value, str):
        fail(400, "invalid_narcotic", f"Поле «{label}» должно быть текстом.")
    value = COLOURS.sub("", value).strip()
    if (required and not value) or len(value) > maximum or "\x00" in value or HTML.search(value):
        fail(400, "invalid_narcotic", f"Проверьте поле «{label}»: до {maximum} символов без HTML.")
    return value


def _integer(value, label, minimum, maximum):
    if type(value) is not int or not minimum <= value <= maximum:
        fail(400, "invalid_narcotic", f"Поле «{label}»: целое число от {minimum} до {maximum}.")
    return value


def _effect_id(value):
    name = str(value or "").strip().upper()
    if not name or name not in EFFECTS or name == "WEAVING":
        fail(400, "invalid_narcotic", "Неизвестный эффект зелья: " + (name or "пусто") + ".")
    return ALIASES.get(name, name)


def _effects(rows, label):
    if not isinstance(rows, list) or len(rows) > 16:
        fail(400, "invalid_narcotic", f"«{label}»: список эффектов, не больше 16.")
    result = []
    for row in rows:
        if not isinstance(row, dict) or set(row) - {"type", "amplifier", "duration_seconds"}:
            fail(400, "invalid_narcotic", f"«{label}»: у эффекта только type, amplifier и duration_seconds.")
        result.append({
            "type": _effect_id(row.get("type")),
            "amplifier": _integer(row.get("amplifier"), f"{label}: amplifier", 0, 255),
            "duration_seconds": _integer(row.get("duration_seconds"), f"{label}: duration_seconds", 1, 86400),
        })
    return result


def _ingredient(row, materials):
    if not isinstance(row, dict) or set(row) - {"key", "count"}:
        fail(400, "invalid_narcotic", "Ингредиент — объект с полями key и count.")
    key = str(row.get("key", "")).strip().upper()
    count = _integer(row.get("count"), "Количество ингредиента", 1, 64)
    material = MATERIAL_KEY.fullmatch(key)
    if material:
        if material.group(1) not in materials or material.group(1) == "AIR":
            fail(400, "invalid_narcotic", "Предмета «" + material.group(1) + "» нет в каталоге Minecraft.")
        return {"key": key, "count": count}
    potion = POTION_KEY.fullmatch(key)
    if potion:
        return {"key": "POTION:" + _effect_id(potion.group(1)), "count": count}
    fail(400, "invalid_narcotic", "Ингредиент указывается как MATERIAL:ПРЕДМЕТ или POTION:ЭФФЕКТ.")


def _signature(recipe):
    keys = sorted(part["key"] for part in recipe for _ in range(part["count"]))
    return "|".join(keys)


def _item(item_id, raw, materials):
    if not ITEM_ID.fullmatch(item_id):
        fail(400, "invalid_narcotic", "ID предмета: латиница, цифры и подчёркивания.")
    if not isinstance(raw, dict):
        fail(400, "invalid_narcotic", "Карточка наркотика повреждена.")
    material = str(raw.get("material", "")).strip().upper()
    if material not in materials or material == "AIR":
        fail(400, "invalid_narcotic", "Основной материал «" + material + "» не из каталога Minecraft.")
    fallback = str(raw.get("fallback_material", "") or "").strip().upper()
    if fallback and (fallback not in materials or fallback == "AIR"):
        fail(400, "invalid_narcotic", "Запасной материал «" + fallback + "» не из каталога Minecraft.")
    recipe = raw.get("recipe", [])
    if not isinstance(recipe, list) or len(recipe) > 9:
        fail(400, "invalid_narcotic", "Рецепт — список до 9 ингредиентов.")
    parsed = [_ingredient(row, materials) for row in recipe]
    # Варка в котле не запускается меньше чем с тремя ингредиентами.
    if item_id != "zhuzevo" and len(parsed) < 3:
        fail(400, "invalid_narcotic", "В рецепте «" + item_id + "» должно быть минимум 3 ингредиента.")
    if item_id == "zhuzevo" and parsed:
        fail(400, "invalid_narcotic", "У «zhuzevo» рецепт всегда пустой: это эффект передозировки.")
    visual = str(raw.get("visual_effect", "")).strip().upper()
    if visual not in VISUALS:
        fail(400, "invalid_narcotic", "Визуальный эффект выбирается из списка плагина.")
    texture_key = str(raw.get("texture_key", "")).strip()
    if not TEXTURE_KEY.fullmatch(texture_key):
        fail(400, "invalid_narcotic", "Ключ текстуры: narcotic_<id> строчными буквами.")
    return {
        "display_name": _text(raw.get("display_name", ""), "название", 64),
        "material": material,
        "fallback_material": fallback,
        "texture_key": texture_key,
        "custom_model_data": _integer(raw.get("custom_model_data"), "custom_model_data", 1, 2_000_000_000),
        "overdose_weight": _integer(raw.get("overdose_weight"), "overdose_weight", 0, 1000),
        "visual_effect": visual,
        "recipe": parsed,
        "normal_effects": _effects(raw.get("normal_effects", []), "обычные эффекты"),
        "overdose_effects": _effects(raw.get("overdose_effects", []), "эффекты передозировки"),
    }


def validate(document, materials):
    if not isinstance(document, dict) or not isinstance(document.get("items"), dict):
        fail(400, "invalid_narcotic", "Документ наркотиков повреждён.")
    items = document["items"]
    if not items:
        fail(400, "invalid_narcotic", "Оставь хотя бы один наркотик: пустой раздел плагин не примет.")
    if len(items) > 64:
        fail(400, "invalid_narcotic", "Не больше 64 наркотиков.")
    parsed, signatures, models = {}, {}, set()
    for item_id, raw in items.items():
        clean = _item(str(item_id).strip().lower(), raw, materials)
        if clean["recipe"]:
            signature = _signature(clean["recipe"])
            if signature in signatures:
                fail(400, "invalid_narcotic", "Такой рецепт уже есть у «" + signatures[signature] + "»: наборы не должны повторяться.")
            signatures[signature] = item_id
        model = (clean["material"], clean["custom_model_data"])
        if model in models:
            fail(400, "invalid_narcotic", "custom_model_data " + str(model[1]) + " уже занят для " + clean["material"] + ".")
        models.add(model)
        parsed[item_id] = clean
    return {"version": 1, "effects": VISUALS, "items": parsed}


def _version(value):
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:24]


def _seed(request):
    path = Path(request.app.state.settings.private) / "narcotics-config.json"
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        fail(503, "narcotics_seed_missing", "Эталонный конфиг наркотиков не найден на сервере.")
    if not isinstance(document, dict) or not isinstance(document.get("items"), dict):
        fail(503, "narcotics_seed_missing", "Эталонный конфиг наркотиков повреждён.")
    document.setdefault("effects", VISUALS)
    return document


def _load(request):
    """Текущий документ: правки из базы поверх эталона плагина."""
    database = request.app.state.database
    with database.transaction() as db:
        row = db.execute("SELECT value,updated_s,actor FROM cm_site_settings WHERE key=%s", (SEED_KEY,)).fetchone()
    if not row:
        return {"document": _seed(request), "updated_s": 0, "actor": ""}
    try:
        document = json.loads(row["value"])
    except ValueError:
        document = _seed(request)
    if not isinstance(document, dict) or not isinstance(document.get("items"), dict):
        document = _seed(request)
    document.setdefault("effects", VISUALS)
    return {"document": document, "updated_s": int(row["updated_s"] or 0), "actor": str(row["actor"] or "")}


# --------------------------------------------------------------------------- текстуры

def _texture_dir(request):
    root = (Path(request.app.state.settings.private) / "narcotics-textures").resolve()
    root.mkdir(parents=True, exist_ok=True, mode=0o750)
    return root


def _decode_image(encoded):
    if not isinstance(encoded, str) or not encoded or len(encoded) > MAX_ENCODED:
        fail(413, "image_too_large", "Изображение не больше 6 МБ.")
    try:
        data = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError):
        fail(400, "invalid_image", "Не удалось прочитать файл изображения.")
    if not data or len(data) > MAX_IMAGE_BYTES:
        fail(413, "image_too_large", "Изображение не больше 6 МБ.")
    return data


# --------------------------------------------------------------------------- маршруты

@router.get("/api/v6/admin/narcotics")
def read_narcotics(request: Request):
    current_user(request, "catalog.manage")
    state = _load(request)
    # Пикер показывает только канонические имена: псевдонимы и WEAVING плагин всё равно отвергнет.
    effects = sorted(name for name in EFFECTS if name != "WEAVING" and ALIASES.get(name, name) == name)
    return {**state, "version": _version(state["document"]), "visuals": VISUALS, "effects": effects,
            "updated": state["updated_s"]}


@router.put("/api/v6/admin/narcotics")
async def save_narcotics(request: Request):
    user = current_user(request, "catalog.manage", fresh=True)
    data = await request.json()
    if set(data) - {"document", "version", "reason"}:
        fail(400, "invalid_narcotic", "В запросе лишние поля.")
    reason = str(data.get("reason", "")).strip()
    if not 3 <= len(reason) <= 240:
        fail(400, "reason_required", "Укажи причину изменения: от 3 до 240 символов.")
    expected = data.get("version")
    if not isinstance(expected, str) or len(expected) != 24:
        fail(400, "narcotics_version_required", "Не указана версия документа.")
    materials = _material_ids(request)
    document = validate(data.get("document"), materials)
    encoded = json.dumps(document, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    if len(encoded.encode("utf-8")) > 200 * 1024:
        fail(413, "narcotics_too_large", "Документ наркотиков слишком большой.")
    database = request.app.state.database
    now = int(time.time())
    with database.transaction() as db:
        if not db.sqlite:
            db.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", ("copimine-narcotics",))
        row = db.execute("SELECT value FROM cm_site_settings WHERE key=%s", (SEED_KEY,)).fetchone()
        current = json.loads(row["value"]) if row else _seed(request)
        if not isinstance(current, dict) or not isinstance(current.get("items"), dict):
            current = _seed(request)
        current.setdefault("effects", VISUALS)
        if not hmac.compare_digest(_version(current), expected):
            fail(409, "narcotics_changed", "Документ изменился. Обнови страницу и повтори.")
        db.execute(
            "INSERT INTO cm_site_settings(key,value,updated_s,actor) VALUES(%s,%s,%s,%s) "
            "ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_s=excluded.updated_s,actor=excluded.actor",
            (SEED_KEY, encoded, now, user["name"]),
        )
        db.execute(
            "INSERT INTO cm_site_audit(id,actor_id,actor_name,action,target,reason,details,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)",
            (uuid.uuid4().hex, user["id"], user["name"], AUDIT_KEY, "narcotics", reason,
             json.dumps({"items": len(document["items"]), "bytes": len(encoded)}, ensure_ascii=False), now),
        )
    return {"ok": True, "version": _version(document), "document": document}


@router.get("/api/v6/admin/narcotics/export")
def export_narcotics(request: Request):
    """Готовый фрагмент config.yml для плагина — без записи на игровой сервер."""
    current_user(request, "catalog.manage")
    try:
        import yaml
    except ImportError:
        fail(503, "yaml_unavailable", "Экспорт YAML недоступен на сервере.")
    document = _load(request)["document"]
    items = {}
    for item_id, raw in document["items"].items():
        items[item_id] = {
            "display_name": raw["display_name"],
            "material": raw["material"],
            **({"fallback_material": raw["fallback_material"]} if raw.get("fallback_material") else {}),
            "texture_key": raw["texture_key"],
            "custom_model_data": raw["custom_model_data"],
            "overdose_weight": raw["overdose_weight"],
            "visual_effect": raw["visual_effect"],
            "recipe": [part["key"].replace("MATERIAL:", "material:").replace("POTION:", "potion:") if part["count"] == 1
                       else {"key": part["key"], "count": part["count"]} for part in raw["recipe"]],
            "normal_effects": [_effect_line(row) for row in raw["normal_effects"]],
            "overdose_effects": [_effect_line(row) for row in raw["overdose_effects"]],
        }
    fragment = {"textures": {"mode": "CUSTOM"}, "items": items}
    return {"yaml": yaml.safe_dump(fragment, allow_unicode=True, sort_keys=False, width=120)}


def _effect_line(row):
    return "type:%s,amplifier:%d,duration_seconds:%d" % (row["type"], row["amplifier"], row["duration_seconds"])


@router.post("/api/v6/admin/narcotics/{item_id}/texture")
async def upload_narcotic_texture(request: Request, item_id: str):
    user = current_user(request, "catalog.manage", fresh=True)
    if not ITEM_ID.fullmatch(item_id):
        fail(400, "invalid_narcotic", "Неизвестный ID наркотика.")
    data = await request.json()
    if set(data) - {"image", "reason"}:
        fail(400, "invalid_narcotic", "В запросе лишние поля.")
    reason = str(data.get("reason", "")).strip()
    if not 3 <= len(reason) <= 240:
        fail(400, "reason_required", "Укажи причину изменения: от 3 до 240 символов.")
    source = _decode_image(data.get("image"))
    root = _texture_dir(request)
    try:
        with Image.open(io.BytesIO(source)) as image:
            if image.format not in IMAGE_FORMATS:
                fail(400, "invalid_image", "Поддерживаются PNG, JPEG, WebP и GIF.")
            image.seek(0)
            image = ImageOps.exif_transpose(image).convert("RGBA")
            # Игра берёт 16×16, в админке показываем увеличенное превью.
            pack = image.resize((16, 16), Image.LANCZOS)
            preview = image.resize((128, 128), Image.LANCZOS)
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
        fail(400, "invalid_image", "Файл повреждён или это не изображение.")
    png, webp = io.BytesIO(), io.BytesIO()
    pack.save(png, format="PNG", optimize=True)
    preview.save(webp, format="WEBP", quality=90, method=4)
    (root / (item_id + ".png")).write_bytes(png.getvalue())
    (root / (item_id + "-preview.webp")).write_bytes(webp.getvalue())
    now = int(time.time())
    database = request.app.state.database
    with database.transaction() as db:
        db.execute(
            "INSERT INTO cm_site_audit(id,actor_id,actor_name,action,target,reason,details,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)",
            (uuid.uuid4().hex, user["id"], user["name"], "narcotics.texture", "narcotic:" + item_id, reason,
             json.dumps({"bytes": len(png.getvalue())}, ensure_ascii=False), now),
        )
    return {"ok": True, "url": "/api/v6/admin/narcotics/" + item_id + "/texture", "at": now}


@router.get("/api/v6/admin/narcotics/{item_id}/texture")
def read_narcotic_texture(request: Request, item_id: str, preview: int = 1):
    current_user(request, "catalog.manage")
    if not ITEM_ID.fullmatch(item_id):
        fail(404, "texture_not_found", "Текстура не найдена.")
    root = _texture_dir(request)
    name = item_id + ("-preview.webp" if preview else ".png")
    path = root / name
    if not path.is_file():
        # Пока своя текстура не загружена, показываем иконку материала из каталога.
        item = _load(request)["document"]["items"].get(item_id) or {}
        fallback = Path(request.app.state.settings.frontend) / "assets" / "materials" / (str(item.get("material", "")).lower() + ".png")
        if not fallback.is_file():
            fail(404, "texture_not_found", "Текстура ещё не загружена.")
        return FileResponse(fallback, media_type="image/png", headers={"Cache-Control": "private, max-age=60", "X-Content-Type-Options": "nosniff"})
    media = "image/webp" if preview else "image/png"
    return FileResponse(path, media_type=media, headers={"Cache-Control": "private, max-age=60", "X-Content-Type-Options": "nosniff"})


# --------------------------------------------------------------------------- медиа товаров

def _media_dir(request):
    root = (Path(request.app.state.settings.private) / "catalog-media").resolve()
    root.mkdir(parents=True, exist_ok=True, mode=0o750)
    return root


@router.post("/api/v6/admin/catalog/{identifier}/media")
async def upload_catalog_media(request: Request, identifier: str):
    """Фото или видео для карточки товара. Файл переживает правку текста."""
    user = current_user(request, "catalog.manage", fresh=True)
    data = await request.json()
    if set(data) - {"kind", "data", "reason"}:
        fail(400, "invalid_media", "В запросе лишние поля.")
    reason = str(data.get("reason", "")).strip()
    if not 3 <= len(reason) <= 240:
        fail(400, "reason_required", "Укажи причину изменения: от 3 до 240 символов.")
    kind = data.get("kind")
    if kind not in {"image", "video"}:
        fail(400, "invalid_media", "Тип файла: image или video.")
    encoded = data.get("data")
    if not isinstance(encoded, str) or not encoded or len(encoded) > MAX_ENCODED:
        fail(413, "media_too_large", "Файл не больше 8 МБ.")
    try:
        payload = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError):
        fail(400, "invalid_media", "Не удалось прочитать файл.")
    if not payload:
        fail(400, "invalid_media", "Файл пустой.")
    root = _media_dir(request)
    if kind == "image":
        if len(payload) > MAX_IMAGE_BYTES:
            fail(413, "media_too_large", "Изображение не больше 6 МБ.")
        try:
            with Image.open(io.BytesIO(payload)) as image:
                if image.format not in IMAGE_FORMATS:
                    fail(400, "invalid_media", "Поддерживаются PNG, JPEG, WebP и GIF.")
                image.seek(0)
                image = ImageOps.exif_transpose(image)
                image.load()
                has_alpha = image.mode in ("RGBA", "LA", "PA") or "transparency" in image.info
                image = image.convert("RGBA" if has_alpha else "RGB")
                if max(image.size) > 1400:
                    image.thumbnail((1400, 1400), Image.LANCZOS)
                output = io.BytesIO()
                image.save(output, format="WEBP", quality=86, method=4)
                payload = output.getvalue()
        except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
            fail(400, "invalid_media", "Файл повреждён или не является изображением.")
        extension = "webp"
    else:
        if len(payload) > MAX_VIDEO_BYTES:
            fail(413, "media_too_large", "Видео не больше 8 МБ.")
        head = payload[4:8] if payload[4:8] == b"ftyp" else payload[:4]
        extension = VIDEO_MAGIC.get(head)
        if not extension:
            fail(400, "invalid_media", "Видео должно быть MP4 или WebM — сервер не перекодирует файлы.")
    name = uuid.uuid4().hex[:24] + "." + extension
    path = root / name
    with path.open("xb") as output:
        output.write(payload)
    try:
        path.chmod(0o640)
    except OSError:
        pass
    now = int(time.time())
    database = request.app.state.database
    with database.transaction() as db:
        db.execute(
            "INSERT INTO cm_site_audit(id,actor_id,actor_name,action,target,reason,details,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)",
            (uuid.uuid4().hex, user["id"], user["name"], "catalog.media", identifier, reason,
             json.dumps({"kind": kind, "bytes": len(payload)}, ensure_ascii=False), now),
        )
    return {"ok": True, "url": "/api/v6/media/" + name, "kind": kind, "bytes": len(payload)}


@router.get("/api/v6/media/{filename}")
def public_media(request: Request, filename: str):
    if not MEDIA_NAME.fullmatch(filename):
        fail(404, "media_not_found", "Файл не найден.")
    root = _media_dir(request)
    path = (root / filename).resolve()
    if path.parent != root or not path.is_file():
        fail(404, "media_not_found", "Файл не найден.")
    media = "video/mp4" if filename.endswith(".mp4") else "video/webm" if filename.endswith(".webm") else "image/webp"
    return FileResponse(path, media_type=media, headers={
        "Cache-Control": "public, max-age=31536000, immutable", "X-Content-Type-Options": "nosniff",
    })
