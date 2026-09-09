from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
AUTH = (
    ROOT
    / "minecraft/server/plugins/AuthEffects/src/main/java/me/serverrp/autheffects/AuthEffectsPlugin.java"
).read_text(encoding="utf-8")


def test_auth_commands_keep_captcha_available_before_login() -> None:
    assert 'case "login", "l", "register", "reg", "changepassword", "cp", "captcha" -> true;' in AUTH


def test_login_event_state_wins_over_a_transient_authme_api_lag() -> None:
    assert "if (authenticated.contains(uuid))" in AUTH
    assert "authApiUsesPlayerArgument" in AUTH
    assert 'getMethod("isAuthenticated", Player.class)' in AUTH
    assert 'getMethod("isAuthenticated", String.class)' in AUTH


def test_auth_cleanup_does_not_capture_or_remove_unrelated_slowness() -> None:
    assert "existing != null && !isAuthLockEffect(existing)" in AUTH
    assert "boolean tracked = ownSlowness.remove(uuid)" in AUTH
    assert "if (!tracked && !isAuthLockEffect(current))" in AUTH
    assert "trackedAuthLock || staleAuthLock" in AUTH
    assert "effect.hasIcon()" in AUTH
