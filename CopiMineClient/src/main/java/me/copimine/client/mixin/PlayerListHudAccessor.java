package me.copimine.client.mixin;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Exposes the vanilla ordering and row cap instead of rebuilding the player list by hand. */
@Mixin(PlayerListHud.class)
public interface PlayerListHudAccessor {
    @Invoker("collectPlayerEntries")
    List<PlayerListEntry> copimine$collectPlayerEntries();
}