package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import me.copimine.client.RiftRamBossBarHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides only the server bar that belongs to the End Rift Guardian. */
@Mixin(BossHealthOverlay.class)
public abstract class EndRiftBossBarHudMixin {
    @Unique private BossEvent copimine$customBarRendered;

    /** Bar body; the calling method extracts its title separately. */
    @Inject(
            method = "extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void copimine$hideVanillaEndRiftBar(GuiGraphicsExtractor context, int x, int y,
                                                 BossEvent bossBar, CallbackInfo callback) {
        copimine$customBarRendered = null;
        if (RiftRamBossBarHud.render(context, x, y, bossBar)) {
            copimine$customBarRendered = bossBar;
            callback.cancel();
            return;
        }
        if (copimine$isEndRiftBossBar(bossBar)) {
            copimine$customBarRendered = bossBar;
            callback.cancel();
        }
    }

    /** Suppress only the title whose body we just replaced, preserving other bosses. */
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"))
    private Component copimine$customBarTitle(LerpingBossEvent bar) {
        boolean replaced = copimine$customBarRendered == bar;
        copimine$customBarRendered = null;
        return replaced ? Component.empty() : bar.getName();
    }

    /** Covers progress/notch bars, whose extraction uses the textured overload. */
    @Inject(
            method = "extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;I[Lnet/minecraft/resources/Identifier;[Lnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void copimine$hideVanillaEndRiftTexturedBar(GuiGraphicsExtractor context, int x, int y,
                                                         BossEvent bossBar, int progress,
                                                         Identifier[] identifiers,
                                                         Identifier[] notches,
                                                         CallbackInfo callback) {
        if (copimine$isEndRiftBossBar(bossBar)) {
            callback.cancel();
        }
    }

    @Unique
    private boolean copimine$isEndRiftBossBar(BossEvent bossBar) {
        if (bossBar == null || !ClientBridgeProtocol.endEventState().hasActiveBossBar()) {
            return false;
        }
        String title = bossBar.getName() == null ? "" : bossBar.getName().getString();
        // The server fallback keeps its historical display name, while the
        // client HUD uses the shorter title.  Hide either spelling so the
        // custom frame is never rendered together with the vanilla fallback.
        return title.startsWith("Хранитель Разлома")
                || title.startsWith("Страж Разлома");
    }
}
