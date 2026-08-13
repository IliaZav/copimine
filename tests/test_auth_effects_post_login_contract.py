import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = next(
    (ROOT / "minecraft" / "server" / "plugins" / "AuthEffects" / "src" / "main" / "java").rglob("AuthEffectsPlugin.java")
)


class AuthEffectsPostLoginContractTest(unittest.TestCase):
    def test_event_authenticated_state_survives_a_transient_authme_api_false(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn("if (authenticated.contains(uuid))", source)
        self.assertIn("return true;", source)

    def test_successful_login_removes_stale_auth_lock_after_runtime_state_loss(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn("boolean tracked = ownSlowness.remove(uuid)", source)
        self.assertIn("isAuthLockEffect(current)", source)
        self.assertIn("tracked || isAuthLockEffect(current)", source)
        self.assertIn("effect.hasIcon()", source)
        self.assertIn("!effect.hasParticles()", source)
        self.assertIn("player.removePotionEffect(PotionEffectType.SLOWNESS)", source)

    def test_authme_api_uses_the_player_signature_and_keeps_legacy_fallback(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn('getMethod("isAuthenticated", Player.class)', source)
        self.assertIn("authApiIsAuthenticated.invoke(authApi, player)", source)
        self.assertIn('getMethod("isAuthenticated", String.class)', source)


if __name__ == "__main__":
    unittest.main()
