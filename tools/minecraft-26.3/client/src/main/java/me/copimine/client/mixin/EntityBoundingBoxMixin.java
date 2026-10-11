package me.copimine.client.mixin;

import me.copimine.client.EndRiftTentacleRenderer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the visual-only item carrier from blocking the tentacle's living hitbox. */
@Mixin(Entity.class)
public abstract class EntityBoundingBoxMixin {
    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void copimine$shrinkTentacleVisualPickBox(CallbackInfoReturnable<AABB> cir) {
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof Display display)
                || !EndRiftTentacleRenderer.isTentacleCarrier(display)) {
            return;
        }
        Vec3 position = entity.position();
        double half = 0.015D;
        cir.setReturnValue(new AABB(position.x - half, position.y - half, position.z - half,
                position.x + half, position.y + half, position.z + half));
    }
}
