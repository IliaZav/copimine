"""The echo copy must animate as a *real player*, not as a shaped stand-in.

The requirement in one sentence: fighting an echo should look like fighting a
player -- the owner's skin, only darkened, with glowing violet eyes, and every
vanilla action (walk, sprint, crouch, swing, bow draw, crossbow reload, shield
block, eat, hurt flash, death) playing through the game's own player animation
code.

That is deliberately *not* an authored animation set. A second set of clips would
drift from vanilla the moment Mojang retimes an arm swing. The shipped design is:

* ``EchoPlayerView`` is a detached ``RemotePlayer`` -- the real vanilla player
  entity class -- whose body fields are written from the server's semantic
  frame, so ``AvatarRenderer`` runs its normal player animations over it.
* ``EchoVanillaRenderer`` advances the vanilla animation clocks (gait, swing,
  use, hurt, death) from server receipts instead of ticking a second actor.
* ``EchoPlayerRendererMixin`` installs ``EchoEyesFeature`` on the real player
  renderer, and that layer paints the violet eye pass and the darkened body rig.

These sources import Minecraft classes and cannot be compiled without the client
jar, so every invariant here is read from the source text. Visual confirmation on
a real client is a separate, still-unmet step; this module only proves the
structure, and says so.
"""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
MIXIN = CLIENT / "mixin"


def view_source() -> str:
    return (CLIENT / "EchoPlayerView.java").read_text(encoding="utf-8")


def renderer_source() -> str:
    return (CLIENT / "EchoVanillaRenderer.java").read_text(encoding="utf-8")


def code_only(text: str) -> str:
    """Strip ``//`` and block comments so prose cannot satisfy a code check."""
    stripped = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return "\n".join(
        line.split("//", 1)[0] for line in stripped.splitlines()
    )


def test_the_copy_is_the_real_vanilla_player_class():
    """A ``RemotePlayer`` is what lets the normal player renderer animate it."""
    text = view_source()
    assert re.search(r"class EchoPlayerView extends RemotePlayer", text), (
        "the copy must extend the vanilla player entity class, otherwise "
        "AvatarRenderer cannot drive it with the player animation code"
    )
    # The view must never become a second simulation: super.tick() would move
    # it independently and could consume items it only mirrors. Checked against
    # code only -- the file legitimately warns about that call in a comment.
    assert not re.search(r"super\.tick\s*\(", code_only(text)), (
        "the detached view must not tick vanilla physics; it is projected, not simulated"
    )


def test_every_posture_the_player_uses_is_projected():
    """Pose, crouch, sprint, eye/body rotation and gait come from the frame."""
    text = view_source()
    required = {
        "crouching pose": r"setPose\(frame\.pose\(\) == EchoPresentationState\.Pose\.CROUCHING",
        "crouch input": r"setShiftKeyDown\(",
        "sprint": r"setSprinting\(frame\.sprinting\(\)\)",
        "body yaw": r"yBodyRot = carrier\.yBodyRot",
        "head yaw": r"yHeadRot = carrier\.yHeadRot",
        "swing/main arm": r"setMainArm\(carrier\.getMainArm\(\)\)",
        "gait clock": r"calculateEntityAnimation\(false\)",
    }
    missing = [name for name, pattern in required.items() if not re.search(pattern, text)]
    assert not missing, f"the copy does not project: {', '.join(missing)}"


def test_item_use_animation_is_projected_for_bow_crossbow_shield_and_food():
    """Vanilla draws a draw/reload/block/eat pose only from these accessors."""
    text = view_source()
    overrides = {
        "isUsingItem": r"@Override public boolean isUsingItem\(\)",
        "getUsedItemHand": r"@Override public InteractionHand getUsedItemHand\(\)",
        "getUseItem": r"@Override public ItemStack getUseItem\(\)",
        "getTicksUsingItem": r"@Override public int getTicksUsingItem\(\)",
        "getUseItemRemainingTicks": r"@Override public int getUseItemRemainingTicks\(\)",
    }
    missing = [name for name, pattern in overrides.items() if not re.search(pattern, text)]
    assert not missing, (
        "the copy must answer the use-item accessors vanilla animates from; "
        f"missing {', '.join(missing)}"
    )
    # A use that is over must not keep the arm raised.
    assert "deathTicks() == 0" in text, (
        "a dying copy must not keep an item-use animation running"
    )


def test_the_copy_wears_the_owners_real_skin():
    text = view_source()
    assert re.search(r"@Override public PlayerSkin getSkin\(\)", text), (
        "getSkin must be overridden to return the owner's skin, not a placeholder"
    )
    renderer = renderer_source()
    assert "DefaultPlayerSkin.get(semantic.frame().owner())" in renderer, (
        "the fallback skin must also be the owner's, so a slow fetch cannot "
        "show a stranger's body"
    )


def test_the_renderer_drives_vanilla_gait_swing_use_and_hurt_clocks():
    """These are the vanilla bookkeeping writes that make a mid-animation body."""
    text = renderer_source()
    required = {
        "gait": r"player\.walkAnimation\.update\(",
        "swing": r"player\.swing\(net\.minecraft\.world\.InteractionHand\.MAIN_HAND",
        "use start": r"player\.startUsingItem\(",
        "use stop": r"player\.stopUsingItem\(\)",
        "hurt flash": r"player\.hurtTime = HURT_TICKS",
        "hurt duration": r"player\.hurtDuration = HURT_TICKS",
    }
    missing = [name for name, pattern in required.items() if not re.search(pattern, text)]
    assert not missing, f"the renderer never advances: {', '.join(missing)}"


def test_the_renderer_renders_through_the_vanilla_player_renderer():
    text = renderer_source()
    assert "AvatarRenderer" in text, (
        "the copy must be submitted by the vanilla avatar renderer, not a bespoke one"
    )
    assert re.search(r"renderer\.extractRenderState\(cached\.player, echoState", text), (
        "the render state must be extracted by the vanilla renderer from the view"
    )
    assert re.search(r"renderer\.submit\(echoState, matrices, collector, camera\)", text), (
        "the copy must be submitted through the vanilla renderer submit path"
    )
    assert re.search(r"target\.deathTime = Math\.max\(source\.deathTime, deathTicks\)", text), (
        "the death collapse must be carried into the vanilla death animation"
    )


def test_a_real_player_is_never_replaced_by_the_echo_renderer():
    """The custom path only substitutes echo carriers, never ordinary players."""
    text = renderer_source()
    assert re.search(r"binding\.copimine\$isEcho\(\)\) return false;", text), (
        "the echo renderer must bail out on echoes (they are drawn by the vanilla "
        "path plus the eye layer) so a copy is never double-drawn"
    )


def test_the_eye_and_body_layers_are_installed_on_the_vanilla_player_renderer():
    text = (MIXIN / "EchoPlayerRendererMixin.java").read_text(encoding="utf-8")
    assert "@Mixin(AvatarRenderer.class)" in text, (
        "the layer must be mixed into the vanilla player renderer"
    )
    assert "addLayer(new EchoEyesFeature(this))" in text, (
        "EchoEyesFeature must actually be added, or the copy has no violet eyes "
        "and no darkening"
    )
    registered = (ROOT / "tools/minecraft-26.3/client/src/main/resources/"
                        "copimineclient-26.3.mixins.json").read_text(encoding="utf-8")
    assert '"EchoPlayerRendererMixin"' in registered, "the mixin must be registered"


def test_the_copy_appears_with_a_materialisation_ramp():
    """A copy grows into place instead of popping in at full size."""
    renderer = renderer_source()
    assert re.search(r"cached\.player\.markAppeared\(now\)", renderer), (
        "the first drawn frame must be recorded, or the arrival ramp never starts"
    )
    assert "EchoAppearanceClock.materialiseScale" in renderer, (
        "the body scale must come from the shared appearance clock"
    )
    assert re.search(r"matrices\.pushPose\(\)", renderer) and re.search(
        r"matrices\.popPose\(\)", renderer), (
        "the scale must be applied inside a pushed/popped pose"
    )
    view = view_source()
    assert "EchoAppearanceClock" in view, (
        "the view must own the appearance clock so a recycled actor re-materialises"
    )


def test_the_eyes_light_up_gradually_and_stay_dark_at_first():
    """The eyes fade in after the body; they are not lit on the first frame."""
    renderer = renderer_source()
    assert re.search(r"copimine\$echoEyeGlow\(cached\.player\.eyeGlow\(now\)\)", renderer), (
        "the renderer must publish the eye glow onto the render state"
    )
    eyes = (CLIENT / "EchoEyesFeature.java").read_text(encoding="utf-8")
    assert "copimine$echoEyeGlow()" in eyes, (
        "the eye layer must read the glow"
    )
    assert re.search(r"if \(glow <= 0\.02F\) return;", eyes), (
        "the eye pass must be skipped while the eyes are still dark"
    )
    assert re.search(r"Math\.round\(255\.0F \* Math\.min\(1\.0F, glow\)\)", eyes), (
        "the eye tint alpha must ramp with the glow"
    )
