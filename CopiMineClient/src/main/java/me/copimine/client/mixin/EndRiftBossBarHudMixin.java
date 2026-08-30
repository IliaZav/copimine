package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/** Hides only the server bar that belongs to the End Rift Guardian. */
@Mixin(BossBarHud.class)
public abstract class EndRiftBossBarHudMixin {
    /**
     * BossBarHud draws the title itself in render(), after delegating the
     * textured background to renderBossBar().  Filter only this event bar at
     * the collection boundary so other server BossBars remain visible.
     */
    @Redirect(
            method = "render(Lnet/minecraft/client/gui/DrawContext;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection<?> copimine$filterVanillaEndRiftBars(Map<?, ?> bars) {
        if (!ClientBridgeProtocol.endEventState().hasActiveBossBar()) {
            return bars.values();
        }
        Collection<Object> filtered = new ArrayList<>();
        for (Object value : bars.values()) {
            if (!(value instanceof BossBar bossBar) || !copimine$isEndRiftBossBar(bossBar)) {
                filtered.add(value);
            }
        }
        return filtered;
    }

    @Inject(
            method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void copimine$hideVanillaEndRiftBar(DrawContext context, int x, int y,
                                                 BossBar bossBar, CallbackInfo callback) {
        if (copimine$isEndRiftBossBar(bossBar)) {
            callback.cancel();
        }
    }

    /**
     * 1.21.1 routes the textured boss-bar path through this overload.  Keep
     * the four-argument hook above for mappings/builds that still use the
     * legacy wrapper, but cancel the actual draw overload as well so the
     * vanilla bar cannot appear underneath the custom HUD.
     */
    @Inject(
            method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;I[Lnet/minecraft/util/Identifier;[Lnet/minecraft/util/Identifier;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void copimine$hideVanillaEndRiftTexturedBar(DrawContext context, int x, int y,
                                                         BossBar bossBar, int progress,
                                                         Identifier[] identifiers,
                                                         Identifier[] notches,
                                                         CallbackInfo callback) {
        if (copimine$isEndRiftBossBar(bossBar)) {
            callback.cancel();
        }
    }

    @Unique
    private boolean copimine$isEndRiftBossBar(BossBar bossBar) {
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
