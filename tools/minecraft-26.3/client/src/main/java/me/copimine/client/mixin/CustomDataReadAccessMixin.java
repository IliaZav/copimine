package me.copimine.client.mixin;

import me.copimine.client.ArtifactCustomDataReadAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes only immutable string reads; the component's mutable tag never escapes. */
@Mixin(CustomData.class)
public abstract class CustomDataReadAccessMixin implements ArtifactCustomDataReadAccess {
    @Shadow @Final private CompoundTag tag;

    @Override
    public String getTopLevelString(String key) {
        return tag.getStringOr(key, "");
    }

    @Override
    public String getNestedString(String compoundKey, String key) {
        Tag nestedTag = tag.get(compoundKey);
        return nestedTag instanceof CompoundTag nested ? nested.getStringOr(key, "") : "";
    }
}
