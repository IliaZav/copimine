package me.copimine.client.mixin;

import me.copimine.client.ClientBridgeProtocol;
import net.minecraft.client.render.entity.EndermanEntityRenderer;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses event textures only for UUIDs explicitly bound by the End Rift server. */
@Mixin(EndermanEntityRenderer.class)
public abstract class EndermanEntityRendererMixin {
    private static Identifier texture(String name) {
        return Identifier.of("copimineclient", "textures/entity/" + name + ".png");
    }

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void copimine$guardianTexture(EndermanEntity entity, CallbackInfoReturnable<Identifier> cir) {
        if (entity == null) {
            return;
        }
        String visual = ClientBridgeProtocol.endEventVisualForEntity(entity.getUuid().toString());
        Identifier texture = switch (visual) {
            case "END_RIFT_GUARDIAN_V1" -> texture("end_rift_guardian");
            case "END_RIFT_ELITE_V1" -> texture("end_rift_elite");
            case "END_RIFT_ENDERMAN_V1" -> texture("end_rift_enderman");
            default -> null;
        };
        if (texture != null) {
            cir.setReturnValue(texture);
        }
    }
}
