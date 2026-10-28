package me.copimine.client.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Exposes the vanilla ordering and 80-row cap instead of rebuilding the player list by hand. */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Invoker("getPlayerInfos")
    List<PlayerInfo> copimine$getPlayerInfos();
}