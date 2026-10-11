package me.copimine.client;

import net.minecraft.world.item.ItemStack;

/** Keeps the armor item visible across 26.3's deferred equipment-layer texture lookup. */
public final class ArmorStackTracker {
    private static final ThreadLocal<String> CURRENT_TEXTURE = new ThreadLocal<>();

    private ArmorStackTracker() {}

    public static void remember(ItemStack stack) {
        CURRENT_TEXTURE.set(ArtifactArmorTexturePolicy.textureFor(stack));
    }

    public static String currentTexture() {
        return CURRENT_TEXTURE.get();
    }

    public static void clear() {
        CURRENT_TEXTURE.remove();
    }
}
