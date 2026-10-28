"""Server monitoring, release control and login history for the admin panel.

Expensive probes (RCON, /proc, journal, log tail) run only in the worker, at most
once per SAMPLE_SECONDS, and the panel renders whatever the last sample stored.
"""
import hashlib
import json
import re
import shutil
import subprocess
import time
import uuid
import zipfile
from pathlib import Path

from fastapi import APIRouter, Request

from .domain import safe_reason
from .security import current_user, fail

router = APIRouter()
GAME_UNIT = "copimine-game.service"
# Замер по умолчанию раз в 3 секунды; интервал настраивается в панели мониторинга.
SAMPLE_SECONDS = 3
SAMPLE_MIN, SAMPLE_MAX = 2, 300
INTERVAL_KEY = "monitor_interval"
RETENTION_DAYS = 7
LOGIN_RETENTION_DAYS = 30
REQUIRED_PLUGINS = ("CopiMineEconomyCore", "CopiMineElectionCore", "CopiMineUltimateAdminPlus", "CopiMineArtifacts", "CopiMineWorldCore", "CopiMineNarcotics")
TAG = re.compile(r"\[([^\[\]]{1,48})\]")
INVENTORY_CACHE = {"at": 0.0, "data": None}
# Предыдущее значение счётчика CPU процесса игры: разница за интервал даёт нагрузку.
PROCESS_CPU = {"value": None, "at": 0.0}
# Тяжёлые чтения (журнал, systemctl, статистика базы) обновляем не каждый замер.
HEAVY_CACHE = {"at": 0.0, "host": {}, "database": {}, "errors": {}}
HEAVY_SECONDS = 15
# Журнал просадок кэшируется на полминуты: запрос по суткам тяжёлый.
INCIDENT_CACHE = {"at": 0.0, "database": None, "data": []}
INCIDENT_FLOOR = 18.0
INCIDENT_MSPT = 40.0


def _number(value):
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _run(args, timeout=20):
    try:
        return subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)
    except (OSError, subprocess.SubprocessError):
        return None


def _server(settings):
    return settings.game_root / "minecraft" / "server"


def _releases_root(settings):
    return settings.map_root.parent / "releases"


def sample_interval(database):
    """Интервал замера из настроек, по умолчанию 3 секунды."""
    try:
        value = int(database.setting(INTERVAL_KEY, SAMPLE_SECONDS))
    except (TypeError, ValueError):
        return SAMPLE_SECONDS
    return min(SAMPLE_MAX, max(SAMPLE_MIN, value))


def process_cpu(unit=GAME_UNIT):
    """Загрузка процесса игры в процентах одного ядра, по счётчику из systemd."""
    result = _run(["systemctl", "show", unit, "-p", "CPUUsageNSec"])
    if not result or result.returncode:
        return None
    raw = result.stdout.strip().partition("=")[2]
    value = _number(raw)
    if value is None:
        return None
    now = time.monotonic()
    previous = PROCESS_CPU.get("value")
    PROCESS_CPU["value"], elapsed = value, now - PROCESS_CPU["at"]
    PROCESS_CPU["at"] = now
    if previous is None or elapsed <= 0:
        return None
    return round((value - previous) / (elapsed * 1e9) * 100, 1)


def host_stats():
    out = {}
    try:
        mem = {}
        for line in Path("/proc/meminfo").read_text(encoding="utf-8").splitlines():
            key, _, value = line.partition(":")
            mem[key] = int(value.split()[0]) * 1024
        total, available = mem.get("MemTotal"), mem.get("MemAvailable")
        if total and available is not None:
            out["memoryTotal"] = total
            out["memoryUsed"] = total - available
            out["memoryPercent"] = round((total - available) * 100 / total, 1)
        out["load"] = [float(x) for x in Path("/proc/loadavg").read_text(encoding="utf-8").split()[:3]]
        out["cpuCount"] = len(os_cpu()) or None
        out["uptimeSeconds"] = int(float(Path("/proc/uptime").read_text(encoding="utf-8").split()[0]))
    except (OSError, ValueError, IndexError):
        pass
    try:
        usage = shutil.disk_usage("/")
        out["disk"] = {"total": usage.total, "free": usage.free, "percent": round(usage.used * 100 / usage.total, 1)}
    except OSError:
        pass
    return out


def os_cpu():
    try:
        return list(range(len(__import__("os").sched_getaffinity(0))))
    except (AttributeError, OSError):
        return []


def service_state(unit=GAME_UNIT):
    result = _run(["systemctl", "show", unit, "-p", "NRestarts", "-p", "MainPID", "-p", "ActiveEnterTimestamp", "-p", "SubState", "-p", "MemoryCurrent", "-p", "ActiveState"])
    if not result or result.returncode:
        return {"available": False}
    fields = {}
    for line in result.stdout.splitlines():
        key, _, value = line.partition("=")
        fields[key] = value.strip()
    return {
        "available": True,
        "activeState": fields.get("ActiveState") or "",
        "subState": fields.get("SubState") or "",
        "memoryBytes": _number(fields.get("MemoryCurrent")),
        "restarts": _number(fields.get("NRestarts")) or 0,
        "startedAt": fields.get("ActiveEnterTimestamp") or "",
    }


def database_stats(database):
    if database.sqlite:
        return {"engine": "sqlite-test"}
    try:
        with database.transaction() as db:
            row = db.execute(
                "SELECT count(*) AS connections, coalesce(max(extract(epoch FROM now()-backend_start)),0) AS oldest FROM pg_stat_activity WHERE datname=current_database()"
            ).fetchone()
            size = db.execute("SELECT pg_database_size(current_database()) AS bytes").fetchone()
            limit = db.execute("SELECT setting FROM pg_settings WHERE name='max_connections'").fetchone()
        return {
            "engine": "postgresql",
            "ok": True,
            "connections": int(row["connections"]),
            "oldestSeconds": int(row["oldest"]),
            "bytes": int(size["bytes"]),
            "maxConnections": int(limit["setting"]),
        }
    except Exception:
        return {"engine": "postgresql", "ok": False}


def log_errors(settings, tail=4000):
    path = _server(settings) / "logs" / "latest.log"
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()[-tail:]
    except OSError:
        return {"available": False}
    by_source, samples = {}, []
    for line in lines:
        if " WARN" not in line and " ERROR" not in line and "Exception" not in line:
            continue
        source = "server"
        for tag in TAG.findall(line):
            if "thread" not in tag.lower() and "async" not in tag.lower() and ":" not in tag:
                source = tag.split("/")[0]
                break
        by_source[source] = by_source.get(source, 0) + 1
        if len(samples) < 8:
            samples.append(line.strip()[:300])
    return {"available": True, "count": sum(by_source.values()), "bySource": by_source, "samples": samples, "tail": tail}


def plugin_meta(jar):
    try:
        with zipfile.ZipFile(jar) as archive:
            text = archive.read("plugin.yml").decode("utf-8", "replace")
    except (OSError, KeyError, zipfile.BadZipFile):
        return {}
    meta = {}
    for line in text.splitlines()[:40]:
        if line.startswith(("name:", "version:", "api-version:")):
            key, _, value = line.partition(":")
            meta[key.strip()] = value.strip().strip("'\"")
    return meta


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def plugin_inventory(settings, force=False):
    """Installed jars with version + hash. Cached: 14 jars is ~16 MB of hashing."""
    if not force and INVENTORY_CACHE["data"] is not None and time.time() - INVENTORY_CACHE["at"] < 60:
        return INVENTORY_CACHE["data"]
    directory = _server(settings) / "plugins"
    plugins = []
    try:
        jars = sorted(directory.glob("*.jar"))
    except OSError:
        jars = []
    for jar in jars:
        meta = plugin_meta(jar)
        try:
            size, digest = jar.stat().st_size, sha256_file(jar)
        except OSError:
            continue
        plugins.append({
            "file": jar.name,
            "name": meta.get("name") or jar.stem,
            "version": meta.get("version") or "",
            "apiVersion": meta.get("api-version") or "",
            "size": size,
            "sha256": digest,
        })
    INVENTORY_CACHE.update(at=time.time(), data=plugins)
    return plugins


def staging_inventory(settings):
    directory = _releases_root(settings) / "staging"
    items = []
    try:
        jars = sorted(directory.glob("*.jar"))
    except OSError:
        jars = []
    for jar in jars:
        meta = plugin_meta(jar)
        checksum_file = jar.with_name(jar.name + ".sha256")
        try:
            expected = checksum_file.read_text(encoding="utf-8").split()[0].lower()
        except (OSError, IndexError):
            expected = ""
        try:
            size, digest = jar.stat().st_size, sha256_file(jar)
        except OSError:
            continue
        items.append({
            "file": jar.name,
            "name": meta.get("name") or jar.stem,
            "version": meta.get("version") or "",
            "apiVersion": meta.get("api-version") or "",
            "size": size,
            "sha256": digest,
            "expected": expected,
            "verified": bool(expected) and expected == digest,
        })
    return items


def sample(database, settings, game, now=None):
    """One moderate-frequency probe. Stores a row and prunes old ones."""
    now = int(time.time()) if now is None else now
    try:
        health = game.health(detailed=True) or {}
    except Exception:
        health = {}
    worlds = [{"name": w.get("name"), "dimension": w.get("dimension"), "chunks": w.get("loadedChunks")} for w in health.get("worlds") or []]
    # Журнал, systemctl и статистику Postgres читаем раз в HEAVY_SECONDS:
    # раз в три секунды это лишняя нагрузка на сервер.
    mono = time.monotonic()
    if mono - HEAVY_CACHE["at"] >= HEAVY_SECONDS:
        HEAVY_CACHE["host"] = {"process": service_state(), "system": host_stats()}
        HEAVY_CACHE["database"] = database_stats(database)
        HEAVY_CACHE["errors"] = log_errors(settings)
        HEAVY_CACHE["at"] = mono
    host = dict(HEAVY_CACHE["host"])
    if host.get("process"):
        host["process"] = {**host["process"], "cpuPercent": process_cpu()}
    row = {
        "id": uuid.uuid4().hex,
        "sampled_s": now,
        "online": 1 if health.get("online") else 0,
        "players": int(health.get("online") or 0),
        "tps": health.get("tps"),
        "mspt": health.get("mspt"),
        "cpu": (host.get("process") or {}).get("cpuPercent"),
        "plugins": len(health.get("plugins") or []),
        "worlds": json.dumps(worlds),
        "host": json.dumps(host),
        "database": json.dumps(HEAVY_CACHE["database"]),
        "errors": json.dumps(HEAVY_CACHE["errors"]),
        "source": "bridge" if health.get("tps") is not None else "unavailable",
    }
    with database.transaction() as db:
        db.execute(
            "INSERT INTO cm_site_server_metrics (id,sampled_s,online,players,tps,mspt,cpu,plugins,worlds,host,database,errors,source) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            tuple(row.values()),
        )
        db.execute("DELETE FROM cm_site_server_metrics WHERE sampled_s < %s", (now - RETENTION_DAYS * 86400,))
        db.execute("DELETE FROM cm_site_login_events WHERE created_s < %s", (now - LOGIN_RETENTION_DAYS * 86400,))
    return row


def _decode(row, keys=("worlds", "host", "database", "errors", "target", "backup", "check_result", "apply_result")):
    out = dict(row)
    for key in keys:
        if key in out and isinstance(out[key], str):
            try:
                out[key] = json.loads(out[key])
            except ValueError:
                out[key] = None
    return out


def incidents(rows, tps_floor=18.0, mspt_ceiling=40.0):
    """Просадки из истории: начало, длительность, минимум TPS и что было рядом.

    rows идут от новых к старым.
    """
    out, run = [], None
    for row in reversed(rows):  # от старых к новым, чтобы склеить непрерывные просадки
        sample_host = row.get("host") or {}
        system, process = sample_host.get("system") or {}, sample_host.get("process") or {}
        tps, mspt = row.get("tps"), row.get("mspt")
        bad = (tps is not None and tps < tps_floor) or (mspt is not None and mspt > mspt_ceiling)
        if bad and run is None:
            run = {"from": row["sampled_s"], "to": row["sampled_s"], "tps": tps, "mspt": mspt, "samples": 1,
                   "players": row.get("players") or 0, "cpu": process.get("cpuPercent"),
                   "load": (system.get("load") or [None])[0], "memoryPercent": system.get("memoryPercent")}
        elif bad:
            run["to"] = row["sampled_s"]
            run["samples"] += 1
            if tps is not None and (run["tps"] is None or tps < run["tps"]):
                run["tps"] = tps
            if mspt is not None and (run["mspt"] is None or mspt > run["mspt"]):
                run["mspt"] = mspt
            run["players"] = max(run["players"], row.get("players") or 0)
            run["cpu"] = process.get("cpuPercent") or run["cpu"]
            run["load"] = (system.get("load") or [None])[0] or run["load"]
        elif run is not None:
            out.append(run)
            run = None
    if run is not None:
        out.append(run)
    return out[-20:]


@router.get("/api/v6/admin/monitor")
def monitor(request: Request):
    current_user(request, "server.monitor")
    database = request.app.state.database
    interval = sample_interval(database)
    with database.transaction() as db:
        rows = [dict(r) for r in db.execute("SELECT * FROM cm_site_server_metrics ORDER BY sampled_s DESC LIMIT 1").fetchall()]
        if not rows:
            return {"sample": None, "history": [], "incidents": [], "intervalSeconds": interval, "stale": None}
        latest = _decode(rows[0])
        # График за два часа: замер раз в три секунды дал бы тысячи точек,
        # поэтому в базу уходит группировка по минутам.
        history = [
            {"at": int(r["at"]), "tps": _round(r["lo"], 1), "high": _round(r["hi"], 1), "mspt": _round(r["mspt"], 1),
             "players": int(r["players"] or 0), "cpu": _round(r["cpu"], 1)}
            for r in reversed(db.execute(
                "SELECT (sampled_s / 60) * 60 AS at, min(tps) AS lo, max(tps) AS hi, max(mspt) AS mspt, "
                "max(players) AS players, max(cpu) AS cpu FROM cm_site_server_metrics "
                "WHERE sampled_s >= %s GROUP BY 1 ORDER BY 1 DESC LIMIT 140",
                (int(time.time()) - 7200,)).fetchall())
        ]
    return {
        "sample": latest,
        "history": history,
        "incidents": incident_list(database),
        "intervalSeconds": interval,
        "stale": max(0, int(time.time()) - latest["sampled_s"]),
        "requiredPlugins": list(REQUIRED_PLUGINS),
    }


def _round(value, digits=1):
    return None if value is None else round(float(value), digits)


def incident_list(database, hours=24):
    """Просадки за сутки, сгруппированные по минутам. Кэш на полминуты: запрос тяжёлый."""
    now = time.monotonic()
    if INCIDENT_CACHE["database"] is database and now - INCIDENT_CACHE["at"] < 30:
        return INCIDENT_CACHE["data"]
    with database.transaction() as db:
        rows = [dict(r) for r in db.execute(
            "SELECT (sampled_s / 60) * 60 AS at, min(tps) AS tps, max(mspt) AS mspt, max(players) AS players, "
            "max(cpu) AS cpu, max(online) AS online FROM cm_site_server_metrics "
            "WHERE sampled_s >= %s GROUP BY 1 HAVING min(tps) < %s OR max(mspt) > %s ORDER BY 1 LIMIT 600",
            (int(time.time()) - hours * 3600, INCIDENT_FLOOR, INCIDENT_MSPT)).fetchall()]
    data = coalesce(rows)
    INCIDENT_CACHE.update(at=now, database=database, data=data)
    return data


def coalesce(rows):
    """Склеивает соседние плохие минуты в одну запись: просадка длиной 5 минут — это одна строка."""
    out, run = [], None
    for row in rows:
        if run and row["at"] - run["to"] <= 60:
            run["to"] = row["at"]
            run["samples"] += 1
            if row["tps"] is not None and (run["tps"] is None or row["tps"] < run["tps"]):
                run["tps"] = row["tps"]
            if row["mspt"] is not None and (run["mspt"] is None or row["mspt"] > run["mspt"]):
                run["mspt"] = row["mspt"]
            run["players"] = max(run["players"] or 0, row["players"] or 0)
            run["cpu"] = max(run["cpu"] or 0, row["cpu"] or 0) or None
            continue
        if run:
            out.append(run)
        run = {"from": int(row["at"]), "to": int(row["at"]), "samples": 1,
               "tps": _round(row["tps"], 1), "mspt": _round(row["mspt"], 0),
               "players": row["players"], "cpu": _round(row["cpu"], 0)}
    if run:
        out.append(run)
    return out[-20:]


@router.post("/api/v6/admin/monitor/interval")
async def set_monitor_interval(request: Request):
    """Интервал замера. Право то же, что на просмотр мониторинга."""
    user = current_user(request, "server.monitor")
    data = await request.json()
    try:
        value = int(data.get("seconds"))
    except (TypeError, ValueError):
        fail(400, "invalid_interval", "Укажите интервал в секундах.")
    if not SAMPLE_MIN <= value <= SAMPLE_MAX:
        fail(400, "invalid_interval", f"Интервал — от {SAMPLE_MIN} до {SAMPLE_MAX} секунд.")
    database = request.app.state.database
    database.set_setting(INTERVAL_KEY, value, user["name"])
    database.audit(user, "monitor.interval", str(value), reason=str(data.get("reason") or "")[:240])
    return {"ok": True, "intervalSeconds": value}


@router.get("/api/v6/admin/releases")
def releases(request: Request):
    current_user(request, "release.manage")
    database, settings = request.app.state.database, request.app.state.settings
    with database.transaction() as db:
        rows = [_decode(r) for r in db.execute("SELECT * FROM cm_site_releases ORDER BY created_s DESC LIMIT 50").fetchall()]
    try:
        health = request.app.state.game.health()
    except Exception:
        health = {}
    return {
        "installed": {
            "commit": database.setting("release.commit", ""),
            "commitSource": database.setting("release.commit_source", ""),
            "ci": database.setting("release.ci", None),
            "paper": health.get("paper") or "",
            "minecraft": health.get("minecraft") or "",
            "plugins": plugin_inventory(settings),
        },
        "staging": staging_inventory(settings),
        "stagingPath": str(_releases_root(settings) / "staging"),
        "releases": rows,
        "requiredPlugins": list(REQUIRED_PLUGINS),
        "installCommand": "/opt/copimine-site/deploy/cm-release install ",
    }


@router.post("/api/v6/admin/releases/report")
async def report_release_state(request: Request):
    """Records what the deploy tool or CI reported, so the panel is not guessing."""
    user = current_user(request, "release.manage", fresh=True)
    data = await request.json()
    database = request.app.state.database
    commit = str(data.get("commit", "")).strip()[:64]
    if commit and not re.fullmatch(r"[0-9a-fA-F]{7,40}", commit):
        fail(400, "invalid_commit", "Коммит указывается 7–40 шестнадцатеричными символами.")
    ci = data.get("ci")
    if ci is not None:
        if not isinstance(ci, dict) or str(ci.get("status", "")) not in {"pass", "fail", "running", "skipped"}:
            fail(400, "invalid_ci", "Результат CI: pass, fail, running или skipped.")
        ci = {"status": str(ci["status"]), "url": str(ci.get("url", ""))[:500], "at": int(ci.get("at") or time.time() * 1000)}
    if commit:
        database.set_setting("release.commit", commit, user["id"])
        database.set_setting("release.commit_source", str(data.get("source", "report"))[:40], user["id"])
    if ci is not None:
        database.set_setting("release.ci", ci, user["id"])
    database.audit(user, "release.reported", commit or "ci", "", {"commit": commit, "ci": ci})
    return {"ok": True}


@router.post("/api/v6/admin/releases")
async def create_release(request: Request):
    user = current_user(request, "release.manage", fresh=True)
    data = await request.json()
    reason = safe_reason(data.get("reason"))
    database, settings = request.app.state.database, request.app.state.settings
    version = str(data.get("version", "")).strip()[:40] or time.strftime("%Y%m%d-%H%M")
    if not re.fullmatch(r"[0-9A-Za-z._-]{3,40}", version):
        fail(400, "invalid_version", "Версия: 3–40 символов, латиница, цифры, точка, дефис.")
    release_id = uuid.uuid4().hex[:12]
    target = {
        "plugins": staging_inventory(settings),
        "installed": [{"file": p["file"], "sha256": p["sha256"]} for p in plugin_inventory(settings)],
        "commit": database.setting("release.commit", ""),
        "unit": GAME_UNIT,
    }
    directory = _releases_root(settings) / release_id
    try:
        (directory / "staged").mkdir(parents=True, exist_ok=True)
        for item in target["plugins"]:
            shutil.copy2(_releases_root(settings) / "staging" / item["file"], directory / "staged" / item["file"])
        (directory / "release.json").write_text(json.dumps({"id": release_id, "version": version, **target}, ensure_ascii=False, indent=1), encoding="utf-8")
    except OSError:
        fail(503, "release_storage", "Не удалось подготовить каталог релиза.")
    now = int(time.time())
    with database.transaction() as db:
        db.execute(
            "INSERT INTO cm_site_releases (id,version,commit_sha,target,actor_id,actor_name,reason,status,backup,check_result,apply_result,created_s,updated_s) VALUES (%s,%s,%s,%s,%s,%s,%s,'prepared','{}','{}','{}',%s,%s)",
            (release_id, version, target["commit"], json.dumps(target, ensure_ascii=False), user["id"], user["name"], reason, now, now),
        )
    database.audit(user, "release.prepared", release_id, reason, {"version": version, "plugins": [p["file"] for p in target["plugins"]]})
    return {"ok": True, "id": release_id, "version": version, "plugins": len(target["plugins"])}


def _release(database, release_id):
    with database.transaction() as db:
        row = db.execute("SELECT * FROM cm_site_releases WHERE id=%s", (release_id,)).fetchone()
    if not row:
        fail(404, "not_found", "Релиз не найден.")
    return _decode(row)


def _save(database, release_id, **fields):
    sets = ", ".join(key + "=%s" for key in fields)
    with database.transaction() as db:
        db.execute("UPDATE cm_site_releases SET " + sets + ", updated_s=%s WHERE id=%s", (*fields.values(), int(time.time()), release_id))


@router.post("/api/v6/admin/releases/{release_id}/backup")
def backup_release(request: Request, release_id: str):
    """pg_dump + a copy of every installed jar. This is what rollback restores."""
    user = current_user(request, "release.manage", fresh=True)
    database, settings = request.app.state.database, request.app.state.settings
    release = _release(database, release_id)
    directory = _releases_root(settings) / release_id
    jars = directory / "backup" / "jars"
    try:
        jars.mkdir(parents=True, exist_ok=True)
        plugins = _server(settings) / "plugins"
        sums = []
        for item in plugin_inventory(settings, force=True):
            shutil.copy2(plugins / item["file"], jars / item["file"])
            sums.append(item["sha256"] + "  " + item["file"])
        (directory / "backup" / "SHA256SUMS").write_text("\n".join(sums) + "\n", encoding="utf-8")
    except OSError:
        fail(503, "backup_failed", "Не удалось скопировать плагины в резервную копию.")
    database_file = ""
    if not database.sqlite:
        database_file = "database.dump"
        result = _run(["pg_dump", settings.database_url, "--no-owner", "--no-acl", "-Fc", "-f", str(directory / "backup" / database_file)], timeout=300)
        if not result or result.returncode:
            database_file = ""
    info = {"at": int(time.time()), "jars": len(sums), "database": database_file, "path": str(directory / "backup")}
    _save(database, release_id, backup=json.dumps(info), status="backed_up")
    database.audit(user, "release.backup", release_id, release["reason"], info)
    return {"ok": True, "backup": info}


@router.post("/api/v6/admin/releases/{release_id}/check")
def check_release(request: Request, release_id: str):
    user = current_user(request, "release.manage", fresh=True)
    database, settings = request.app.state.database, request.app.state.settings
    release = _release(database, release_id)
    target = release["target"] or {}
    installed = {p["name"]: p for p in plugin_inventory(settings, force=True)}
    items = []

    def add(code, level, message):
        items.append({"code": code, "level": level, "message": message})

    staged = list(target.get("plugins") or [])
    if not staged:
        add("no_staged_plugins", "warn", "В релизе нет новых плагинов: установка только перезапустит сервер.")
    for item in staged:
        path = _releases_root(settings) / release_id / "staged" / item["file"]
        try:
            actual = sha256_file(path)
        except OSError:
            add("missing_jar", "fail", "Файл " + item["file"] + " отсутствует в подготовленном каталоге.")
            continue
        if actual != item["sha256"]:
            add("hash_mismatch", "fail", "Контрольная сумма " + item["file"] + " не совпадает с подготовленной.")
        if item.get("expected") and item["expected"] != actual:
            add("operator_hash_mismatch", "fail", "Файл " + item["file"] + " не совпадает с .sha256, который положил администратор сервера.")
        current = installed.get(item["name"])
        if current and current["file"] != item["file"]:
            add("renamed_jar", "fail", "Плагин " + item["name"] + " уже стоит как " + current["file"] + ". Переименуй новый файл так же.")
        elif current:
            add("replaces", "pass", item["name"] + ": " + (current["version"] or "?") + " → " + (item["version"] or "?"))
    names = {p["name"] for p in staged} | set(installed)
    missing = [name for name in REQUIRED_PLUGINS if name not in names]
    if missing:
        add("missing_required", "fail", "Не хватает обязательных плагинов: " + ", ".join(missing))
    else:
        add("required_present", "pass", "Все обязательные плагины на месте.")
    try:
        health = request.app.state.game.health()
    except Exception:
        health = {}
    minecraft = str(health.get("minecraft") or "")
    for item in staged:
        api = str(item.get("apiVersion") or "")
        if api and minecraft and api.split(".")[0] != minecraft.split(".")[0]:
            add("api_version", "warn", item["name"] + ": API " + api + " при сервере " + minecraft + ".")
    if not release["backup"].get("jars"):
        add("no_backup", "fail", "Сначала создай резервную копию: без неё откат невозможен.")
    else:
        add("backup_ready", "pass", "Резервная копия: " + str(release["backup"].get("jars")) + " плагинов.")
    try:
        free = shutil.disk_usage(str(_releases_root(settings))).free
        if free < 2 * 1024**3:
            add("disk", "fail", "Меньше 2 ГБ свободного места.")
        else:
            add("disk", "pass", "Свободно " + str(round(free / 1024**3)) + " ГБ.")
    except OSError:
        pass
    result = {"at": int(time.time()), "items": items, "ok": not any(i["level"] == "fail" for i in items)}
    _save(database, release_id, check_result=json.dumps(result), status="checked" if result["ok"] else "blocked")
    database.audit(user, "release.checked", release_id, release["reason"], {"ok": result["ok"], "failures": [i["code"] for i in items if i["level"] == "fail"]})
    return {"ok": result["ok"], "check": result}


@router.post("/api/v6/admin/releases/{release_id}/apply")
async def apply_release(request: Request, release_id: str):
    """Arms a verified install. The jar swap itself runs as root, outside the web app.

    shortcut: the site user cannot write plugins/, so the actual swap needs
    `sudo /opt/copimine-site/deploy/cm-release install <id>`. Revisit if the app
    ever gets a root-owned helper unit.
    """
    user = current_user(request, "release.manage", fresh=True)
    data = await request.json()
    if str(data.get("confirm", "")).strip().upper() != "УСТАНОВИТЬ":
        fail(400, "confirm_required", "Введи слово УСТАНОВИТЬ, чтобы подтвердить установку.")
    reason = safe_reason(data.get("reason"))
    database, settings = request.app.state.database, request.app.state.settings
    release = _release(database, release_id)
    if release["status"] not in {"checked"} or not (release["check_result"] or {}).get("ok"):
        fail(409, "check_required", "Проверка совместимости не пройдена. Установка не подготовлена.")
    if not (release["backup"] or {}).get("jars"):
        fail(409, "backup_required", "Нет резервной копии.")
    directory = _releases_root(settings) / release_id
    request_body = {"id": release_id, "version": release["version"], "plugins": release["target"]["plugins"], "unit": GAME_UNIT, "actor": user["name"], "armedAt": int(time.time())}
    try:
        (directory / "install.json").write_text(json.dumps(request_body, ensure_ascii=False, indent=1), encoding="utf-8")
    except OSError:
        fail(503, "release_storage", "Не удалось записать задание на установку.")
    _save(database, release_id, status="armed")
    database.audit(user, "release.armed", release_id, reason, {"version": release["version"]})
    return {"ok": True, "status": "armed", "command": "sudo /opt/copimine-site/deploy/cm-release install " + release_id}


@router.post("/api/v6/admin/releases/{release_id}/rollback")
async def rollback_release(request: Request, release_id: str):
    user = current_user(request, "release.manage", fresh=True)
    data = await request.json()
    if str(data.get("confirm", "")).strip().upper() != "ОТКАТИТЬ":
        fail(400, "confirm_required", "Введи слово ОТКАТИТЬ, чтобы подтвердить откат.")
    reason = safe_reason(data.get("reason"))
    database, settings = request.app.state.database, request.app.state.settings
    release = _release(database, release_id)
    backup = release["backup"] or {}
    if not backup.get("jars"):
        fail(409, "backup_required", "У этого релиза нет резервной копии для отката.")
    database.audit(user, "release.rollback_armed", release_id, reason, {"version": release["version"]})
    _save(database, release_id, status="rollback_armed")
    return {"ok": True, "command": "sudo /opt/copimine-site/deploy/cm-release rollback " + release_id}


@router.get("/api/v6/admin/accounts/{identifier}/logins")
def login_history(request: Request, identifier: str):
    current_user(request, "players.view")
    database = request.app.state.database
    with database.transaction() as db:
        user = db.execute("SELECT id,name FROM cm_site_users WHERE id=%s OR name_norm=%s", (identifier, identifier.lower())).fetchone()
    if not user:
        fail(404, "not_found", "Аккаунт не найден.")
    with database.transaction() as db:
        rows = [dict(r) for r in db.execute(
            "SELECT id,result,ip,user_agent,created_s FROM cm_site_login_events WHERE user_id=%s OR name=%s ORDER BY created_s DESC LIMIT 100",
            (user["id"], user["name"]),
        ).fetchall()]
    return {"account": user["name"], "entries": [{"id": r["id"], "at": r["created_s"] * 1000, "result": r["result"], "ip": r["ip"], "userAgent": r["user_agent"]} for r in rows]}