package me.copimine.client.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Runtime bridge for the public custom face quads used by the supplied mesh. */
@Mixin(ModelPart.Cube.class)
public interface ModelPartCuboidAccessor {
    @Accessor("polygons")
    ModelPart.Polygon[] copimine$getSides();

    @Mutable
    @Accessor("polygons")
    void copimine$setSides(ModelPart.Polygon[] sides);
}
