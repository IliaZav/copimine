package me.copimine.client.mixin;

import me.copimine.client.EndRiftTentacleRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the visual-only item carrier from blocking the tentacle's living hitbox. */
@Mixin(Entity.class)
public abstract class EntityBoundingBoxMixin {
    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void copimine$shrinkTentacleVisualPickBox(CallbackInfoReturnable<Box> cir) {
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof DisplayEntity display)
                || !EndRiftTentacleRenderer.isTentacleCarrier(display)) {
            return;
        }
        Vec3d position = entity.getPos();
        double half = 0.015D;
        cir.setReturnValue(new Box(position.x - half, position.y - half, position.z - half,
                position.x + half, position.y + half, position.z + half));
    }
}
