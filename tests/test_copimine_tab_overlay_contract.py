from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_26 = ROOT / "tools/minecraft-26.3/client"
CLIENT_LEGACY = ROOT / "CopiMineClient"

MIXIN_26 = CLIENT_26 / "src/main/java/me/copimine/client/mixin/PlayerTabOverlayMixin.java"
ACCESSOR_26 = CLIENT_26 / "src/main/java/me/copimine/client/mixin/PlayerTabOverlayAccessor.java"
MIXIN_CONFIG_26 = CLIENT_26 / "src/main/resources/copimineclient-26.3.mixins.json"

MIXIN_LEGACY = CLIENT_LEGACY / "src/main/java/me/copimine/client/mixin/PlayerListHudMixin.java"
ACCESSOR_LEGACY = CLIENT_LEGACY / "src/main/java/me/copimine/client/mixin/PlayerListHudAccessor.java"
MIXIN_CONFIG_LEGACY = CLIENT_LEGACY / "src/main/resources/copimineclient.mixins.json"

RENDER_26 = (
    "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
    "ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V"
)
RENDER_LEGACY = (
    "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;"
    "Lnet/minecraft/scoreboard/ScoreboardObjective;)V"
)


def test_26_3_player_list_mixin_targets_the_real_render_entry():
    source = MIXIN_26.read_text(encoding="utf-8")

    assert "@Mixin(PlayerTabOverlay.class)" in source
    assert RENDER_26 in source
    assert 'at = @At("HEAD")' in source
    assert "cancellable = true" in source
    # The accessor is the only sanctioned way to reach the private player list.
    assert ACCESSOR_26.is_file()
    accessor = ACCESSOR_26.read_text(encoding="utf-8")
    assert '@Invoker("getPlayerInfos")' in accessor
    assert "copimine$getPlayerInfos()" in accessor


def test_26_3_player_list_mixin_is_registered():
    config = json.loads(MIXIN_CONFIG_26.read_text(encoding="utf-8"))

    assert "PlayerTabOverlayMixin" in config["client"]
    assert "PlayerTabOverlayAccessor" in config["client"]


def test_26_3_player_list_mixin_uses_display_and_profile_indirection():
    source = MIXIN_26.read_text(encoding="utf-8")

    # Names must come from the class's own formatted list, never a hand-rolled player walk.
    assert "copimine$getPlayerInfos()" in source
    assert "getNameForDisplay(" in source
    assert "getOnlinePlayers()" not in source
    assert "copimine$plainText(" in source
    assert "isDrawnLine(" in source


def test_player_list_mixins_survive_missing_header_and_footer():
    source = MIXIN_26.read_text(encoding="utf-8")

    # A pack may send only one of the two sections, so the text renderer guards null itself
    # and the handler only cancels the vanilla list when there is something to replace it with.
    assert "if (text == null)" in source
    assert "if (header == null && footer == null)" in source