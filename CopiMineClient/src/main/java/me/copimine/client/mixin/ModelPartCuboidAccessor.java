package me.copimine.client.mixin;

import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Runtime bridge for the public custom face quads used by the supplied mesh. */
@Mixin(ModelPart.Cuboid.class)
public interface ModelPartCuboidAccessor {
    @Mutable
    @Accessor("sides")
    void copimine$setSides(ModelPart.Quad[] sides);
}
