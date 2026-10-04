"""Execute the production packet boundary in Paper's disabled-plugin state."""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def packet_probe(tmp_path_factory):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    start = source.index("    private void sendClientPacket(Player player, String type, String instanceId, long durationMillis,\n"
                         "                                  String subjectId, String visualId, int health, int maxHealth,\n"
                         "                                  float progress, String clearPolicy, String status)")
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    directory = tmp_path_factory.mktemp("disabled-packet-cleanup")
    fixture = directory / "DisabledPacketCleanup.java"
    fixture.write_text('''
import java.util.logging.Level;
public class DisabledPacketCleanup {
  boolean enabled = true, checkpointSaved; int packets;
  long generation = 5; String eventId = "attempt";
  record Config(String bridgeChannel) { }
  Config config = new Config("copimine:bridge");
  boolean isEnabled() { return enabled; }
  class Player {
    boolean isOnline() { return true; }
    void sendPluginMessage(DisabledPacketCleanup plugin, String channel, byte[] data) {
      if (!plugin.isEnabled()) throw new IllegalArgumentException("Plugin must be enabled to send messages");
      if (data.length == 0) throw new AssertionError("encoded packet required");
      packets++;
    }
  }
  void recordPluginMessage() { }
  java.util.logging.Logger getLogger() { return java.util.logging.Logger.getAnonymousLogger(); }
  public static void main(String[] args) {
    var p = new DisabledPacketCleanup();
    p.enabled = Boolean.parseBoolean(args[0]);
    if (Boolean.parseBoolean(args[1])) p.config = null;
    p.sendClientPacket(p.new Player(), "CLEAR", "binding", 0, "entity", "visual", 0, 0, 0, "CLEAR", "");
    p.checkpointSaved = true;
    if (!p.checkpointSaved || p.packets != Integer.parseInt(args[2]))
      throw new AssertionError("cleanup must reach its checkpoint; only enabled plugins send packets");
  }
''' + source[start:end] + "\n}", encoding="utf-8")
    result = subprocess.run(["javac", "-d", str(directory), str(fixture)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("enabled,missing_config,packets", [(True, False, 1), (False, False, 0), (False, True, 0)])
def test_cleanup_packet_boundary(packet_probe, enabled, missing_config, packets):
    result = subprocess.run(["java", "-cp", str(packet_probe), "DisabledPacketCleanup",
                             str(enabled).lower(), str(missing_config).lower(), str(packets)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
