from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "copimine-narcotics/src/me/copimine/clientbridge"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_gate_is_required_with_tested_fifteen_second_grace_and_emergency_switch() -> None:
    config = read("copimine-narcotics/config.yml")
    assert "client_gate:" in config
    assert "required: true" in config
    assert "ready_timeout_seconds: 15" in config
    service = read("copimine-narcotics/src/me/copimine/narcotics/config/NarcoticsConfigService.java")
    assert 'root.getBoolean("client_gate.required", true)' in service
    assert 'root.getInt("client_gate.ready_timeout_seconds", 15)' in service
    assert "setClientGateRequired" in service


def test_existing_bridge_owns_both_channels_and_never_checks_mod_inventory() -> None:
    bridge = read("copimine-narcotics/src/me/copimine/clientbridge/CopiMineClientBridge.java")
    payloads = read("copimine-narcotics/src/me/copimine/clientbridge/ClientReadyPayloads.java")
    assert "registerIncomingPluginChannel(plugin, ClientReadyPayloads.READY_CHANNEL, this)" in bridge
    assert "registerOutgoingPluginChannel(plugin, ClientReadyPayloads.ACK_CHANNEL)" in bridge
    assert "CLIENT_READY_ACK_SENT" in bridge
    assert "CLIENT_GATE_ACCEPT" in bridge
    assert "CLIENT_GATE_REJECT reason=" in bridge
    assert "CLIENT_READY_TIMEOUT" in bridge
    assert "STALE_JOIN_ATTEMPT_IGNORED" in bridge
    assert "cancelAdmissionTimeout" in bridge
    assert "modList" not in bridge and "jarHashes" not in bridge and "launch-ticket" not in bridge
    assert "MAX_PAYLOAD_BYTES = 256" in payloads
    assert "MAX_CLIENT_VERSION_LENGTH = 64" in payloads


def test_state_machine_has_protocol_only_decision_and_reconnect_race_guard() -> None:
    admission = read("copimine-narcotics/src/me/copimine/clientbridge/ClientReadyAdmission.java")
    assert "REQUIRED_PROTOCOL = 3" in admission
    assert "DEFAULT_TIMEOUT_MILLIS = 15_000L" in admission
    assert "joinAttemptId" in admission
    assert "DUPLICATE_ACCEPTED" in admission
    assert "PROTOCOL_MISMATCH" in admission
    assert "CLIENT_READY_AFTER_TIMEOUT" in admission
    assert "STALE_JOIN_ATTEMPT" in admission
    assert "readyPacketCount" in admission
    assert "nonce" not in admission.lower()
    assert "hardware" not in admission.lower()


def test_ready_diagnostics_are_structured_without_raw_payload_logging() -> None:
    bridge = read("copimine-narcotics/src/me/copimine/clientbridge/CopiMineClientBridge.java")
    assert "CLIENT_GATE_INTERNAL_ERROR" in bridge
    assert "error.getClass().getSimpleName()" in bridge
    assert "message=" not in bridge
    assert "payload hex" not in bridge.lower()


def test_gate_timeout_rechecks_when_scheduler_fires_before_wall_clock_deadline() -> None:
    bridge = read("copimine-narcotics/src/me/copimine/clientbridge/CopiMineClientBridge.java")

    assert "Decision.NOT_DUE" in bridge
    assert "CLIENT_GATE_TIMEOUT_RECHECK" in bridge
    assert "scheduleAdmissionTimeout" in bridge


def test_local_gate_matrix_covers_accept_reject_and_minimal_valid_client_profiles() -> None:
    matrix = read("scripts/run_copimine_client_gate_matrix.ps1")
    for scenario in (
        "managed-plus-extra",
        "no-copimine-client",
        "no-mods",
        "copimine-client-plus-fabric-api",
    ):
        assert scenario in matrix
    assert "CLIENT_GATE_MATRIX=PASS" in matrix
    assert "fabric-api-0.116.11+1.21.1.jar" in matrix
    assert "The matrix may only mutate an instance below" in matrix


def test_gate_matrices_do_not_pin_a_removed_dated_install_fixture() -> None:
    for relative in (
        "scripts/run_copimine_client_gate_matrix.ps1",
        "scripts/run_copimine_third_party_gate_matrix.ps1",
    ):
        matrix = read(relative)
        assert "[string]$InstanceRoot = ''" in matrix
        assert "Get-ChildItem -LiteralPath $resolvedValidationRoot -Directory -Filter 'msi-install-*'" in matrix
        assert "msi-install-1.0.3-20260823" not in matrix
