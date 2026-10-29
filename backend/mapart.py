import asyncio
import base64
import binascii
import io
import re
import secrets
import time
from pathlib import Path
from urllib.parse import urlparse

from fastapi import APIRouter, Request
from fastapi.responses import FileResponse
from PIL import Image, ImageOps, UnidentifiedImageError

from .security import current_user, digest, fail, throttle
from . import antivirus

router = APIRouter()

_MAX_BYTES = 4 * 1024 * 1024
_FILENAME = re.compile(r"[a-f0-9]{32}\.png")
_MC_NAME = re.compile(r"[A-Za-z0-9_]{1,16}\Z")


def _root(request):
    root = (Path(request.app.state.settings.private) / "mapart-assets").resolve()
    root.mkdir(parents=True, exist_ok=True)
    return root


def _png(encoded):
    if not isinstance(encoded, str) or not encoded or len(encoded) > 5_600_000:
        fail(413, "mapart_image_too_large", "Картинка должна быть не больше 4 МБ.")
    try:
        data = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError):
        fail(400, "invalid_mapart_image", "Не удалось прочитать файл изображения.")
    if not data or len(data) > _MAX_BYTES:
        fail(413, "mapart_image_too_large", "Картинка должна быть не больше 4 МБ.")
    try:
        with Image.open(io.BytesIO(data)) as source:
            if source.width < 1 or source.height < 1 or source.width > 8000 or source.height > 8000 or source.width * source.height > 16_000_000:
                fail(400, "invalid_mapart_image", "Поддерживаются изображения до 8 000 × 8 000 пикселей.")
            source.verify()
        with Image.open(io.BytesIO(data)) as source:
            source = ImageOps.exif_transpose(source)
            source.load()
            source.thumbnail((2048, 2048), Image.Resampling.LANCZOS)
            if source.mode not in {"RGB", "RGBA"}:
                source = source.convert("RGBA" if "A" in source.getbands() else "RGB")
            output = io.BytesIO()
            source.save(output, format="PNG", optimize=True)
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
        fail(400, "invalid_mapart_image", "Файл повреждён или не является поддерживаемым изображением.")
    png = output.getvalue()
    if not png or len(png) > _MAX_BYTES:
        fail(413, "mapart_image_too_large", "После обработки картинка превышает 4 МБ.")
    return png


def _upload_position(game, player):
    """Best-effort upload-time position. Placement happens later, so this is only a hint."""
    try:
        listing = game.rcon("list")
        if player.lower() not in listing.lower():
            return None
        raw = game.rcon(f"execute as {player} at @s run data get entity @s Pos")
        values = [float(value) for value in re.findall(r"-?\d+(?:\.\d+)?", raw.split(":", 1)[-1])][:3]
        if len(values) != 3:
            return None
        dimension = game.rcon(f"execute as {player} at @s run data get entity @s Dimension")
        match = re.search(r"minecraft:([a-z_]+)", dimension)
        world = {"the_nether": "nether", "the_end": "end"}.get(match.group(1) if match else "", "overworld")
        x, y, z = (round(value) for value in values)
        if not all(-30_000_000 <= value <= 30_000_000 for value in (x, y, z)):
            return None
        return world, x, y, z
    except Exception:
        return None


async def _position(request, user):
    if not user:
        return None
    player = str(user.get("minecraft_name") or "").strip()
    game = getattr(request.app.state, "game", None)
    if not player or not _MC_NAME.fullmatch(player) or not game:
        return None
    try:
        return await asyncio.wait_for(asyncio.to_thread(_upload_position, game, player), timeout=4)
    except Exception:
        return None


def _remote(value):
    if not isinstance(value, str) or not 1 <= len(value) <= 2048:
        return ""
    try:
        parsed = urlparse(value)
    except ValueError:
        return ""
    if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password:
        return ""
    return value


def _metadata(data, user):
    name = re.sub(r"[^A-Za-z0-9_-]+", "-", str(data.get("name") or "").strip()).strip("-")[:32] or "copimine-art"
    try:
        width = int(data.get("width") or 0)
        height = int(data.get("height") or 0)
    except (TypeError, ValueError):
        fail(400, "invalid_mapart_size", "Размер картины указан неверно.")
    if not 1 <= width <= 10 or not 1 <= height <= 10 or width * height > 100:
        fail(400, "invalid_mapart_size", "Размер картины — от 1 × 1 до 10 × 10 карт.")
    account_name = ""
    if user:
        account_name = str(user.get("minecraft_name") or user.get("display_name") or user.get("name") or "").strip()
    author = account_name or str(data.get("author") or "").strip()[:32]
    if not account_name and author and not re.fullmatch(r"[A-Za-z0-9_]{3,32}", author):
        fail(400, "invalid_mapart_author", "Ник — 3–32 символа: латиница, цифры и подчёркивание.")
    return name, width, height, str(user.get("id") if user else ""), author or "Гость"


@router.post("/api/v6/mapart/image")
async def upload_mapart_image(request: Request):
    throttle(request.app.state.database, "mapart:" + digest(str(getattr(request.state, "anonymous", ""))), 20, 3600)
    throttle(request.app.state.database, "mapart-ip:" + digest(str(request.client.host if request.client else "")), 40, 3600)
    data = await request.json()
    user = getattr(request.state, "user", None)
    name, width, height, author_id, author_name = _metadata(data, user)
    position = await _position(request, user)
    world, x, y, z = position or ("", None, None, None)
    root = _root(request)
    filename = ""
    remote = _remote(data.get("remote"))
    if data.get("image"):
        png = _png(data.get("image"))
        filename = secrets.token_hex(16) + ".png"
        # Картинка анонимная, поэтому антивирус проверяет её до попадания в хранилище.
        await antivirus.store(root / filename, png, request.app.state.database, user, "mapart:" + filename,
                              "Загружена картинка для картины", "mapart.infected")
        for old in sorted(root.glob("*.png"), key=lambda path: path.stat().st_mtime, reverse=True)[200:]:
            old.unlink(missing_ok=True)
        url = request.app.state.settings.public_url + "/mapart/image/" + filename
    elif remote:
        url = remote
    else:
        fail(400, "invalid_mapart_image", "Выбери картинку или вставь прямую ссылку.")
    command = f"/imageframe create {name} {url} {width} {height}"
    created = int(time.time())
    with request.app.state.database.transaction() as db:
        db.execute(
            "INSERT INTO cm_site_mapart_uploads(id,image,url,name,width,height,command,author_id,author_name,world,x,y,z,created_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (secrets.token_hex(12), filename, url, name, width, height, command, author_id, author_name, world, x, y, z, created),
        )
    return {"url": url, "name": name, "command": command, "created": created,
            "location": {"world": world, "x": x, "y": y, "z": z} if x is not None else None}


@router.get("/api/v6/admin/mapart")
def admin_mapart(request: Request):
    current_user(request, "content.manage")
    try:
        limit = min(max(int(request.query_params.get("limit", "100")), 1), 200)
    except ValueError:
        limit = 100
    with request.app.state.database.transaction() as db:
        rows = db.execute(
            "SELECT id,image,url,name,width,height,command,author_id,author_name,world,x,y,z,created_s FROM cm_site_mapart_uploads ORDER BY created_s DESC LIMIT %s",
            (limit,),
        ).fetchall()
    return {"items": [dict(row) for row in rows]}


@router.get("/mapart/image/{filename}")
def mapart_image(filename: str, request: Request):
    if not _FILENAME.fullmatch(filename):
        fail(404, "mapart_image_not_found", "Картинка не найдена.")
    path = _root(request) / filename
    if not path.is_file():
        fail(404, "mapart_image_not_found", "Картинка не найдена.")
    return FileResponse(path, media_type="image/png", headers={"Cache-Control": "public, max-age=31536000, immutable"})
