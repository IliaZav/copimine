package me.copimine.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the entity lookup without forcing a full client-world entity scan. */
@Mixin(ClientLevel.class)
public interface ClientWorldAccessor {
    @Invoker("getEntities")
    LevelEntityGetter<Entity> copimine$getEntities();
}
