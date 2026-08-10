from __future__ import annotations

import threading
from pathlib import Path
from typing import Callable, Optional


class FileMtimeMemo:
    """Serialize and memoize a file-backed boolean until the file changes."""

    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._signature: Optional[tuple[int, int]] = None
        self._value = False

    def get(self, path: Path, loader: Callable[[], bool]) -> bool:
        try:
            stat = path.stat()
            signature = (stat.st_mtime_ns, stat.st_size)
        except OSError:
            signature = None

        with self._lock:
            if signature is not None and signature == self._signature:
                return self._value
            value = bool(loader())
            self._signature = signature
            self._value = value
            return value
