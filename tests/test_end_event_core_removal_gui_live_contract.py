"""Static contract for the live operator-GUI Core removal regression."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BOT = ROOT / "tests/LocalEndRiftCoreRemovalGuiBot.js"
RUNNER = ROOT / "tests/RunEndRiftCoreRemovalGuiLive.ps1"


def test_gui_removal_probe_uses_a_real_player_packet_and_confirm_slot() -> None:
    source = BOT.read_text(encoding="utf-8")
    for marker in (
        "auth: 'offline'",
        "version: '1.21.1'",
        "windowOpen",
        "bot.attack(coreDisplay)",
        "CORE_REMOVAL_GUI_DIG_FALLBACK",
        "item_display",
        "block_dig",
        "status: 0",
        "status: 2",
        "clickWindow(11, 0, 0)",
        "CORE_REMOVAL_GUI_OPEN",
        "CORE_REMOVAL_GUI_CLICK",
        "lookAt(coreDisplay.position, false)",
    ):
        assert marker in source


def test_core_overlay_damage_is_position_validated_without_stale_role_tag_gate() -> None:
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    handler = source[source.index("public void onCoreOverlayDamage"):
                     source.index("public void onOwnedDisplayDamage")]
    assert "sameCoreOverlayBlock(display, core)" in handler
    assert "EVENT_KIND_CORE.equals(readString(display, keyKind))" not in handler


def test_core_overlay_accepts_the_interaction_event_but_cancels_damage_in_the_handler() -> None:
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    core_spawn = source[source.index("private void spawnCoreOverlay"):
                         source.index("private void spawnRuneOverlay")]
    assert "entity.setInvulnerable(false);" in core_spawn
    assert "event.setCancelled(true);" in source[source.index("public void onCoreOverlayDamage"):
                                                    source.index("public void onOwnedDisplayDamage")]


def test_core_overlay_has_a_left_swing_fallback_for_non_attackable_display_entities() -> None:
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    assert "public void onCoreOverlayAnimation(PlayerAnimationEvent event)" in source
    handler = source[source.index("public void onCoreOverlayAnimation"):
                     source.index("public void onOwnedDisplayDamage")]
    assert "player.getTargetBlockExact(8)" in handler
    assert "sameCore(target)" in handler
    assert "openCoreRemovalConfirm(player)" in handler


def test_gui_removal_runner_is_local_only_and_asserts_post_confirm_state() -> None:
    source = RUNNER.read_text(encoding="utf-8")
    for marker in (
        "environment:\\s*local",
        "codex/end-rift-event",
        "server-port=25566",
        "rcon\\.port=25576",
        "op $BotName",
        "LocalEndRiftCoreRemovalGuiBot.js",
        "CORE_REMOVAL_GUI_OPEN",
        "CORE_REMOVAL_GUI_CLICK",
        "state=.*UNCONFIGURED",
        "coreOverlay=false",
        "runes=0/0",
        "block-data",
        "item_display",
        "block_display",
        "text_display",
        "finally",
        "cmend core remove confirm",
    ):
        assert marker in source
