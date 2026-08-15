from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "CopiMineClient"


def _properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, value = line.split("=", 1)
        values[key] = value
    return values


def test_shared_ready_protocol_contract_is_stable() -> None:
    values = _properties(ROOT / "protocol" / "client-ready.properties")
    assert values == {
        "protocolVersion": "3",
        "readyChannel": "copimine:client_ready",
        "ackChannel": "copimine:client_ready_ack",
    }
    ready_source = (CLIENT / "src/main/java/me/copimine/client/ClientReadyProtocol.java").read_text(encoding="utf-8")
    assert re.search(r"PROTOCOL_VERSION\s*=\s*3\b", ready_source)
    assert 'READY_CHANNEL = "copimine:client_ready"' in ready_source
    assert 'ACK_CHANNEL = "copimine:client_ready_ack"' in ready_source


def test_ready_payload_does_not_report_launcher_or_mod_inventory() -> None:
    ready = (CLIENT / "src/main/java/me/copimine/client/ClientReadyPayload.java").read_text(encoding="utf-8")
    ack = (CLIENT / "src/main/java/me/copimine/client/ClientReadyAckPayload.java").read_text(encoding="utf-8")
    assert re.search(r"record\s+ClientReadyPayload\s*\(\s*int\s+protocolVersion\s*,\s*String\s+clientVersion\s*\)", ready)
    assert "modList" not in ready and "launcher" not in ready.lower() and "device" not in ready.lower()
    assert "heartbeat" not in ready.lower() and "hardware" not in ready.lower() and "nonce" not in ready.lower()
    assert "launcher" not in ack.lower() and "device" not in ack.lower() and "modList" not in ack


def test_fabric_metadata_uses_project_version_and_exact_loader() -> None:
    properties = _properties(CLIENT / "gradle.properties")
    metadata = json.loads((CLIENT / "src/main/resources/fabric.mod.json").read_text(encoding="utf-8"))
    assert properties["mod_version"] == "0.1.0"
    assert metadata["version"] == "${version}"
    assert metadata["depends"]["minecraft"] == "1.21.1"
    assert metadata["depends"]["fabricloader"] == "=0.19.3"
    assert "CLIENT_VERSION" not in (CLIENT / "src/main/java/me/copimine/client/CopiMineClient.java").read_text(encoding="utf-8")


def test_retry_contract_is_finite_and_stops_on_ack() -> None:
    retry = (CLIENT / "src/main/java/me/copimine/client/ClientReadyRetryState.java").read_text(encoding="utf-8")
    assert re.search(r"MAX_ATTEMPTS\s*=\s*6\b", retry)
    assert re.search(r"DEADLINE_MILLIS\s*=\s*15_000L", retry)
    assert "acknowledge" in retry
    assert "ScheduledExecutorService" not in retry
