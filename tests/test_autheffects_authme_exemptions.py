"""Exercise the actual AuthEffects admission method with the pinned AuthMe API."""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]


def test_provider_exemption_allows_local_targets_and_revocation_locks_them_again(tmp_path):
    source = (ROOT / "minecraft/server/plugins/AuthEffects/src/main/java/me/serverrp/autheffects/AuthEffectsPlugin.java").read_text(encoding="utf-8")
    admission = extract(source, "boolean isAuthenticated(Player player)")
    probe = tmp_path / "AuthExemptionProbe.java"
    probe.write_text('''
import java.util.*;
import java.util.logging.Logger;
import java.lang.reflect.Method;
public class AuthExemptionProbe {
    public static class Player {
        private final UUID id = UUID.randomUUID();
        UUID getUniqueId() { return id; }
        String getName() { return "EndRiftTarget1"; }
    }
    public static class Api {
        boolean login, exempt, failExemption;
        public boolean isAuthenticated(Player player) { return login; }
        public boolean isUnrestricted(Player player) {
            if (failExemption) throw new IllegalStateException("provider unavailable");
            return exempt;
        }
    }
    public static class LegacyApi {
        public boolean isAuthenticated(String name) { return name.equals("EndRiftTarget1"); }
    }
    private final Set<UUID> authenticated = new HashSet<>();
    private Method authApiIsAuthenticated, authApiIsUnrestricted;
    private Object authApi;
    private boolean authApiAvailable, authApiUsesPlayerArgument;
    private Logger getLogger() { return Logger.getAnonymousLogger(); }
''' + admission + '''
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        var gate = new AuthExemptionProbe();
        var player = new Player();
        var api = new Api();
        gate.authApi = api;
        gate.authApiAvailable = true;
        gate.authApiUsesPlayerArgument = true;
        gate.authApiIsAuthenticated = Api.class.getMethod("isAuthenticated", Player.class);
        gate.authApiIsUnrestricted = Api.class.getMethod("isUnrestricted", Player.class);
        require(!gate.isAuthenticated(player), "ordinary unauthenticated player must stay locked");
        api.exempt = true;
        require(gate.isAuthenticated(player), "AuthMe-approved unrestricted NPC must be allowed");
        require(gate.authenticated.isEmpty(), "provider exemptions must not become cached logins");
        api.exempt = false;
        require(!gate.isAuthenticated(player), "revoked provider exemption must lock on next check");
        api.failExemption = true;
        require(!gate.isAuthenticated(player), "failed provider lookup must fail closed");
        api.login = true;
        require(gate.isAuthenticated(player), "optional lookup failure must not block a genuine login");
        gate.authenticated.clear();
        api.login = false;
        api.failExemption = false;
        gate.authApiIsUnrestricted = null;
        require(!gate.isAuthenticated(player), "missing optional API must not authorize");
        api.login = true;
        require(gate.isAuthenticated(player), "genuine provider login must still pass");
        require(gate.authenticated.contains(player.getUniqueId()), "genuine login is session-cached");
        require(!gate.isAuthenticated(null), "null player must be denied");
        var legacy = new AuthExemptionProbe();
        legacy.authApi = new LegacyApi();
        legacy.authApiAvailable = true;
        legacy.authApiIsAuthenticated = LegacyApi.class.getMethod("isAuthenticated", String.class);
        require(legacy.isAuthenticated(new Player()), "legacy String login API remains supported");
        var unavailable = new AuthExemptionProbe();
        unavailable.authApi = api;
        unavailable.authApiIsUnrestricted = gate.authApiIsUnrestricted;
        require(!unavailable.isAuthenticated(new Player()), "unavailable provider must not authorize");
    }
}
''', encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(probe)], check=True, capture_output=True, text=True)
    result = subprocess.run(["java", "-cp", str(tmp_path), "AuthExemptionProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
