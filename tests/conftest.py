"""Общая обвязка тестов.

Локально демона ClamAV нет, поэтому по умолчанию считаем файлы чистыми.
Тесты самого антивируса подменяют scan явно.
"""

import pytest

from backend import antivirus


@pytest.fixture(autouse=True)
def clean_antivirus(monkeypatch):
    monkeypatch.setattr(antivirus, "scan", lambda path: ("clean", "Проверено ClamAV (тест)"))
