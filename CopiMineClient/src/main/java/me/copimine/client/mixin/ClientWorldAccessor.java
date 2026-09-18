package me.copimine.client.mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.world.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the UUID lookup without forcing a full client-world entity scan. */
@Mixin(ClientWorld.class)
public interface ClientWorldAccessor {
    @Invoker("getEntityLookup")
    EntityLookup<Entity> copimine$getEntityLookup();
}
