"""Проверка ClamAV для каждого файла, который приходит на сайт от людей.

Файл попадает в хранилище только со статусом clean: «не проверили» не равно
«безопасно». Демон clamd работает под другим пользователем и в каталог сайта
зайти не может, поэтому файл отправляется ему потоком (--stream).
"""

import asyncio
import subprocess
from pathlib import Path

from .security import fail

COMMAND = "clamdscan"
TIMEOUT = 120


def scan(path):
    """status: clean | infected | unavailable. Коды clamdscan: 0 чисто, 1 найдено, 2 ошибка."""
    try:
        result = subprocess.run([COMMAND, "--stream", "--no-summary", "--stdout", str(path)],
                                capture_output=True, text=True, timeout=TIMEOUT)
    except (OSError, subprocess.SubprocessError) as error:
        return "unavailable", str(error)[:200]
    lines = (result.stdout + result.stderr).strip().splitlines()
    detail = lines[0][:200] if lines else ""
    if result.returncode == 0:
        return "clean", "Проверено ClamAV"
    if result.returncode == 1:
        return "infected", detail
    return "unavailable", detail


async def store(path: Path, payload, database, user, target, reason="", infected_action="file.infected"):
    """Записывает файл и оставляет его только если антивирус сказал clean."""
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o750)
    path.write_bytes(payload)
    try:
        path.chmod(0o640)
    except OSError:
        pass
    status, detail = await asyncio.to_thread(scan, path)
    if status == "clean":
        return {"scan": status, "detail": detail}
    path.unlink(missing_ok=True)
    if status == "infected":
        database.audit(user or {"id": "system", "name": "system"}, infected_action, str(target), reason,
                       {"detail": detail, "bytes": len(payload)})
        fail(422, "file_infected", "Файл не прошёл проверку антивирусом и был удалён.")
    fail(503, "scan_unavailable", "Проверка антивирусом сейчас недоступна. Попробуйте позже.")
