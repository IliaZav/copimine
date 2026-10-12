package me.copimine.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.client.model.geom.ModelPart;

/** Runtime bridge for replacing generated cuboid UVs with imported face UVs. */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("cubes")
    List<ModelPart.Cube> copimine$getCuboids();

    @Mutable
    @Accessor("cubes")
    void copimine$setCuboids(List<ModelPart.Cube> cuboids);
}
