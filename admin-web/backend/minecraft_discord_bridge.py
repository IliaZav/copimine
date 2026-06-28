import json
import os
import re
import time
import urllib.request
from pathlib import Path

from backend.envfile import parse_env_file, resolve_env_file

APP_ROOT = Path("/opt/copimine/admin-web")
ENV_FILE = resolve_env_file(APP_ROOT / ".env")
MC_LOG = Path(os.getenv("MC_LOG_FILE", "/opt/copimine/minecraft/server/logs/latest.log"))
STATE_FILE = APP_ROOT / "data" / "minecraft_discord_bridge.offset"
DEDUPE_FILE = APP_ROOT / "data" / "minecraft_discord_bridge.dedupe.json"

def load_env():
    return parse_env_file(ENV_FILE)

env = load_env()
TOKEN = env.get("DISCORD_BOT_TOKEN", "")
APPLICATIONS_CHANNEL_ID = env.get("DISCORD_APPLICATIONS_CHANNEL_ID", "")
REPORTS_CHANNEL_ID = env.get("DISCORD_REPORTS_CHANNEL_ID", "")
DISCORD_BRIDGE_DEDUPE_SECONDS = int(env.get("DISCORD_BRIDGE_DEDUPE_SECONDS", "90") or "90")
BRIDGE_DEDUPE_V2 = "structured-embed-dedupe"

if not TOKEN:
    raise SystemExit("DISCORD_BOT_TOKEN is missing in .env")

if not APPLICATIONS_CHANNEL_ID:
    raise SystemExit("DISCORD_APPLICATIONS_CHANNEL_ID is missing in .env")

if not REPORTS_CHANNEL_ID:
    raise SystemExit("DISCORD_REPORTS_CHANNEL_ID is missing in .env")

COMMAND_RE = re.compile(
    r"\[.*?/INFO\]:\s+(?P<player>[A-Za-z0-9_]{2,16})\s+issued server command:\s+/(?P<cmd>\S+)\s*(?P<text>.*)",
    re.I,
)

CHAT_RE = re.compile(
    r"\[.*?/INFO\]:\s+<(?P<player>[A-Za-z0-9_]{2,16})>\s+(?P<text>.*)",
    re.I,
)

APP_WORDS = ("заяв", "zayav", "application", "apply", "anketa", "анкета")
REPORT_WORDS = ("жалоб", "репорт", "report", "problem", "проблем", "ticket", "тикет")

def clean_minecraft(text: str) -> str:
    text = re.sub(r"§.", "", text)
    return text.strip()

def classify(cmd: str, text: str):
    hay = f"{cmd} {text}".lower()
    if any(w in hay for w in REPORT_WORDS):
        return "report"
    if any(w in hay for w in APP_WORDS):
        return "application"
    return None

def load_dedupe():
    try:
        raw = json.loads(DEDUPE_FILE.read_text(encoding="utf-8"))
        return raw if isinstance(raw, dict) else {}
    except Exception:
        return {}

def save_dedupe(state: dict):
    DEDUPE_FILE.parent.mkdir(parents=True, exist_ok=True)
    cutoff = int(time.time()) - max(30, DISCORD_BRIDGE_DEDUPE_SECONDS * 4)
    compact = {k: v for k, v in state.items() if int(v or 0) >= cutoff}
    DEDUPE_FILE.write_text(json.dumps(compact, ensure_ascii=False, indent=2), encoding="utf-8")

def should_skip_duplicate(key: str) -> bool:
    state = load_dedupe()
    now = int(time.time())
    last = int(state.get(key, 0) or 0)
    if last and now - last < DISCORD_BRIDGE_DEDUPE_SECONDS:
        return True
    state[key] = now
    save_dedupe(state)
    return False

def build_bridge_embed_payload(kind: str, player: str, title: str, description: str, source: str):
    color = 0x57F287 if kind == "application" else 0xED4245 if kind == "report" else 0x5865F2
    return {
        "embeds": [{
            "title": title[:256],
            "description": description[:3900],
            "color": color,
            "fields": [
                {"name": "Игрок", "value": f"`{player or 'unknown'}`", "inline": True},
                {"name": "Источник", "value": source[:256], "inline": True},
                {"name": "Дедупликация", "value": f"{DISCORD_BRIDGE_DEDUPE_SECONDS}s • {BRIDGE_DEDUPE_V2}", "inline": True},
            ],
            "footer": {"text": "CopiMine Minecraft → Discord bridge"},
        }]
    }

def send_discord(channel_id: str, title: str, description: str, kind: str = "event", player: str = "", source: str = "minecraft"):
    url = f"https://discord.com/api/v10/channels/{channel_id}/messages"
    payload = build_bridge_embed_payload(kind, player, title, description, source)
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": f"Bot {TOKEN}",
            "Content-Type": "application/json",
            "User-Agent": "CopiMine-Minecraft-Discord-Bridge",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=25) as r:
        r.read()

def get_offset():
    try:
        return int(STATE_FILE.read_text().strip())
    except Exception:
        return MC_LOG.stat().st_size if MC_LOG.exists() else 0

def save_offset(offset: int):
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(str(offset), encoding="utf-8")

def process_line(line: str):
    line = clean_minecraft(line)

    m = COMMAND_RE.search(line)
    if m:
        player = m.group("player")
        cmd = clean_minecraft(m.group("cmd"))
        text = clean_minecraft(m.group("text"))
        kind = classify(cmd, text)

        if kind == "application":
            if should_skip_duplicate(f"{kind}:{player}:/{cmd}:{text[:240]}"):
                return
            send_discord(
                APPLICATIONS_CHANNEL_ID,
                "Новая заявка из Minecraft",
                f"**Игрок:** `{player}`\n**Команда:** `/{cmd}`\n**Текст:**\n{text or 'Без текста'}",
                kind,
                player,
                f"/{cmd}",
            )
            return

        if kind == "report":
            if should_skip_duplicate(f"{kind}:{player}:/{cmd}:{text[:240]}"):
                return
            send_discord(
                REPORTS_CHANNEL_ID,
                "Новая жалоба / репорт из Minecraft",
                f"**Игрок:** `{player}`\n**Команда:** `/{cmd}`\n**Текст:**\n{text or 'Без текста'}",
                kind,
                player,
                f"/{cmd}",
            )
            return

    m = CHAT_RE.search(line)
    if m:
        player = m.group("player")
        text = clean_minecraft(m.group("text"))
        kind = classify("", text)

        if kind == "application":
            if should_skip_duplicate(f"{kind}:{player}:chat:{text[:240]}"):
                return
            send_discord(
                APPLICATIONS_CHANNEL_ID,
                "Заявка из игрового чата",
                f"**Игрок:** `{player}`\n**Сообщение:**\n{text}",
                kind,
                player,
                "chat",
            )
            return

        if kind == "report":
            if should_skip_duplicate(f"{kind}:{player}:chat:{text[:240]}"):
                return
            send_discord(
                REPORTS_CHANNEL_ID,
                "Жалоба / репорт из игрового чата",
                f"**Игрок:** `{player}`\n**Сообщение:**\n{text}",
                kind,
                player,
                "chat",
            )
            return

def main():
    print(f"Bridge started. Watching: {MC_LOG}", flush=True)
    offset = get_offset()

    while True:
        try:
            if not MC_LOG.exists():
                time.sleep(3)
                continue

            size = MC_LOG.stat().st_size
            if size < offset:
                offset = 0

            with MC_LOG.open("r", encoding="utf-8", errors="ignore") as f:
                f.seek(offset)
                for line in f:
                    try:
                        process_line(line)
                    except Exception as e:
                        print(f"Failed to process line: {type(e).__name__}: {e}", flush=True)
                offset = f.tell()
                save_offset(offset)

        except Exception as e:
            print(f"Bridge loop error: {type(e).__name__}: {e}", flush=True)

        time.sleep(2)

if __name__ == "__main__":
    main()
