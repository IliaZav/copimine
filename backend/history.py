"""Chat and command history for one player, read from CoreProtect.

CoreProtect already records every chat line and command (`player-messages`,
`player-commands` in its config) in its own DuckDB file. The game server keeps
that file locked, so the only safe read is a copy of `database.duckdb` plus its
write-ahead log; the copy is cached for a minute so a paging session does not
copy the database on every page.

The file is written by a newer DuckDB than any pip build that runs on the game
host's CPU, but `duckdb==1.4.5` matches it, so no Java helper is needed. When
the module is missing or the file cannot be read, the endpoint answers
`history_unavailable` and the UI says so instead of showing an empty list.
"""
import csv
import io
import re
import shutil
import tempfile
import time
from pathlib import Path

from fastapi import APIRouter, Request, Response

from .security import current_user, fail

router = APIRouter()
KINDS = ("chat", "command")
PAGE_MAX, EXPORT_MAX = 500, 50000
# Копия базы живёт минуту: пейджинг не должен копировать файл на каждую страницу.
COPY_TTL = 60.0
COPY = {"at": 0.0, "dir": None}
NICK = re.compile(r"[A-Za-z0-9_]{1,32}")


def _source(settings):
    return Path(settings.game_root) / "minecraft" / "server" / "plugins" / "CoreProtect" / "database.duckdb"


def _copy(settings):
    """Private snapshot of the live database; the game keeps the original locked."""
    source = _source(settings)
    if not source.is_file():
        fail(503, "history_unavailable", "CoreProtect ещё не создал базу истории.")
    if COPY["dir"] and time.time() - COPY["at"] < COPY_TTL:
        return COPY["dir"]
    if COPY["dir"]:
        shutil.rmtree(COPY["dir"], ignore_errors=True)
    target = Path(tempfile.mkdtemp(prefix="cp-history-"))
    shutil.copy2(source, target / "database.duckdb")
    wal = Path(str(source) + ".wal")
    if wal.is_file():
        shutil.copy2(wal, target / "database.duckdb.wal")
    COPY.update(at=time.time(), dir=target)
    return target


def _connect(settings):
    try:
        import duckdb
    except ImportError:
        fail(503, "history_unavailable", "Модуль чтения истории не установлен на сервере.")
    directory = _copy(settings)
    try:
        return duckdb.connect(str(directory / "database.duckdb"), read_only=True)
    except Exception:
        # Битый или недописанный снимок не должен отдавать прошлую копию молча.
        COPY.update(at=0.0, dir=None)
        fail(503, "history_unavailable", "История недоступна: базу CoreProtect не удалось открыть.")


def player_history(settings, player, kind="chat", limit=100, offset=0):
    """One page of a player's messages, newest first."""
    if kind not in KINDS:
        fail(400, "invalid_kind", "Выберите чат или команды.")
    con = _connect(settings)
    try:
        table = "co_chat" if kind == "chat" else "co_command"
        count = con.execute(
            "SELECT count(*) FROM " + table + " c JOIN co_user u ON u.rowid=c.user"
            " WHERE lower(u.user)=lower(?)", (player,)).fetchone()[0]
        rows = con.execute(
            "SELECT c.time,c.message,c.x,c.y,c.z,w.world FROM " + table + " c"
            " JOIN co_user u ON u.rowid=c.user LEFT JOIN co_world w ON w.rowid=c.wid"
            " WHERE lower(u.user)=lower(?) ORDER BY c.time DESC,c.rowid DESC LIMIT ? OFFSET ?",
            (player, limit, offset)).fetchall()
    finally:
        con.close()
    return {
        "entries": [{"at": r[0], "message": r[1], "x": r[2], "y": r[3], "z": r[4], "world": r[5]} for r in rows],
        "total": count,
        "hasMore": offset + len(rows) < count,
        # Снимок сделан минуту назад: подпись в интерфейсе не должна врать про «сейчас».
        "snapshotAt": int(COPY["at"]),
    }


def history_csv(settings, player, kind="chat"):
    data = player_history(settings, player, kind, EXPORT_MAX, 0)
    out = io.StringIO()
    writer = csv.writer(out, lineterminator="\r\n")
    writer.writerow(["time", "player", "kind", "world", "x", "y", "z", "message"])
    for row in data["entries"]:
        writer.writerow([time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(row["at"])), player, kind,
                         row["world"] or "", row["x"], row["y"], row["z"],
                         # Ведущие =,+,- Excel принимает за формулу: экранируем.
                         "'" + row["message"] if str(row["message"])[:1] in "=+-@" else row["message"]])
    # BOM: без него Excel открывает кириллицу в cp1251.
    return "\ufeff" + out.getvalue()


def _name(request, identifier):
    """Minecraft nick for a history lookup, from the account when the id is known."""
    identifier = str(identifier or "").strip()
    if not identifier:
        fail(400, "invalid_player", "Выберите игрока.")
    with request.app.state.database.transaction() as db:
        row = db.execute(
            "SELECT minecraft_name,name FROM cm_site_users WHERE id=%s OR name_norm=%s OR lower(minecraft_name)=%s",
            (identifier, identifier.lower(), identifier.lower())).fetchone()
    if row:
        return row["minecraft_name"] or row["name"]
    if not NICK.fullmatch(identifier):
        fail(400, "invalid_player", "Проверьте игровой ник.")
    return identifier


def _paging(request):
    try:
        limit = int(request.query_params.get("limit", "100"))
        offset = int(request.query_params.get("offset", "0"))
    except ValueError:
        fail(400, "invalid_paging", "Проверьте размер страницы.")
    if offset < 0:
        fail(400, "invalid_paging", "Проверьте размер страницы.")
    return min(max(limit, 1), PAGE_MAX), offset


def _kind(request):
    kind = request.query_params.get("kind", "chat")
    if kind not in KINDS:
        fail(400, "invalid_kind", "Выберите чат или команды.")
    return kind


@router.get("/api/v6/admin/players/{identifier}/messages")
def player_messages(identifier: str, request: Request):
    current_user(request, "logs.view")
    player = _name(request, identifier)
    limit, offset = _paging(request)
    return {"player": player, **player_history(request.app.state.settings, player, _kind(request), limit, offset)}


@router.get("/api/v6/admin/players/{identifier}/messages/export")
def export_messages(identifier: str, request: Request):
    user = current_user(request, "logs.view")
    player = _name(request, identifier)
    kind = _kind(request)
    text = history_csv(request.app.state.settings, player, kind)
    request.app.state.database.audit(user, "player.messages_export", player, "", {"kind": kind})
    name = re.sub(r"[^A-Za-z0-9_-]", "_", player)
    return Response(text, media_type="text/csv; charset=utf-8", headers={
        "Content-Disposition": 'attachment; filename="copimine-' + name + "-" + kind + '.csv"',
        "Cache-Control": "private, no-store",
    })