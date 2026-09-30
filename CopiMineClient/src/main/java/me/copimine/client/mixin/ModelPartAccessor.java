package me.copimine.client.mixin;

import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Runtime bridge for replacing generated cuboid UVs with imported face UVs. */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("cuboids")
    List<ModelPart.Cuboid> copimine$getCuboids();

    @Mutable
    @Accessor("cuboids")
    void copimine$setCuboids(List<ModelPart.Cuboid> cuboids);
}
