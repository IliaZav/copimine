import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "copimine-narcotics" / "config.yml"
BRIDGE = ROOT / "copimine-narcotics" / "src" / "me" / "copimine" / "clientbridge" / "CopiMineClientBridge.java"
CAPABILITIES = ROOT / "copimine-narcotics" / "src" / "me" / "copimine" / "clientbridge" / "ClientCapabilityService.java"
STATE = ROOT / "copimine-narcotics" / "src" / "me" / "copimine" / "clientbridge" / "ClientCapabilityState.java"
SERVER_PAYLOAD = ROOT / "copimine-narcotics" / "src" / "me" / "copimine" / "clientbridge" / "ClientBridgePayloads.java"
CLIENT_PAYLOAD = ROOT / "CopiMineClient" / "src" / "main" / "java" / "me" / "copimine" / "client" / "BridgePayload.java"
CLIENT_PROTOCOL = ROOT / "CopiMineClient" / "src" / "main" / "java" / "me" / "copimine" / "client" / "ClientBridgeProtocol.java"
VISUALS = ROOT / "copimine-narcotics" / "src" / "me" / "copimine" / "clientbridge" / "ClientVisualEffectService.java"


class ClientBridgeModpackRetirementContractTest(unittest.TestCase):
    def test_modpack_gate_and_mod_inventory_are_not_part_of_runtime(self):
        config = CONFIG.read_text(encoding="utf-8")
        bridge = BRIDGE.read_text(encoding="utf-8")
        capabilities = CAPABILITIES.read_text(encoding="utf-8")
        state = STATE.read_text(encoding="utf-8")

        for marker in (
            "require_client_mod",
            "kick_if_missing_client",
            "required_mod_ids",
            "enforceClientModpack",
            "onAuthCommandBeforeModpack",
            "missingRequiredMods",
            "hasRequiredModpack",
            "ClientModpackPolicy",
            "installedModIds",
        ):
            self.assertNotIn(marker, config + bridge + capabilities + state)

    def test_client_bridge_payload_keeps_v2_wire_shape_without_mod_ids(self):
        server = SERVER_PAYLOAD.read_text(encoding="utf-8")
        client = CLIENT_PAYLOAD.read_text(encoding="utf-8")
        protocol = CLIENT_PROTOCOL.read_text(encoding="utf-8")

        self.assertIn("PROTOCOL_VERSION = 2", server)
        self.assertIn("PROTOCOL_VERSION = 2", protocol)
        self.assertNotIn("installedModIds", server + client + protocol)
        self.assertNotIn("FabricLoader.getInstance().getAllMods", protocol)

    def test_clients_without_a_bridge_session_receive_no_custom_payload(self):
        visuals = VISUALS.read_text(encoding="utf-8")
        self.assertIn("if (state != null)", visuals)
        self.assertIn("if (state != null && player.isOnline())", visuals)


if __name__ == "__main__":
    unittest.main()
