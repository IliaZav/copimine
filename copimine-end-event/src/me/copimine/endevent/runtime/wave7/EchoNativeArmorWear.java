package me.copimine.endevent.runtime.wave7;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.LivingEntity;

/** Pinned 1.21.1 bridge to the equipment path Player uses and carrier mobs omit. */
public final class EchoNativeArmorWear {
    private final Object carrier, bypassesArmor, damagesHelmet, armorSlots, helmetSlot;
    private final Class<?> craftSource;
    private final Method sourceHandle, tagged, hurtEquipment;

    public EchoNativeArmorWear(LivingEntity entity) {
        try {
            ClassLoader loader = entity.getClass().getClassLoader();
            Class<?> living = Class.forName("net.minecraft.world.entity.LivingEntity", false, loader);
            Class<?> source = Class.forName("net.minecraft.world.damagesource.DamageSource", false, loader);
            Class<?> slot = Class.forName("net.minecraft.world.entity.EquipmentSlot", false, loader);
            Class<?> tag = Class.forName("net.minecraft.tags.TagKey", false, loader);
            Class<?> tags = Class.forName("net.minecraft.tags.DamageTypeTags", false, loader);
            craftSource = Class.forName("org.bukkit.craftbukkit.damage.CraftDamageSource", false, loader);
            carrier = entity.getClass().getMethod("getHandle").invoke(entity);
            if (!living.isInstance(carrier)) throw new IllegalArgumentException("Unsupported native Echo carrier");
            sourceHandle = craftSource.getMethod("getHandle");
            tagged = source.getMethod("is", tag);
            bypassesArmor = tags.getField("BYPASSES_ARMOR").get(null);
            damagesHelmet = tags.getField("DAMAGES_HELMET").get(null);
            armorSlots = Array.newInstance(slot, 4);
            String[] names = {"FEET", "LEGS", "CHEST", "HEAD"};
            for (int i = 0; i < names.length; i++) Array.set(armorSlots, i, slot.getField(names[i]).get(null));
            helmetSlot = Array.newInstance(slot, 1);
            Array.set(helmetSlot, 0, slot.getField("HEAD").get(null));
            hurtEquipment = living.getDeclaredMethod("doHurtEquipment", source, float.class, armorSlots.getClass());
            if (!hurtEquipment.trySetAccessible()) throw new IllegalStateException("Native Echo equipment path is inaccessible");
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("Pinned native Echo armor adapter is unavailable", error);
        }
    }

    /** No health write, second damage event, or rewritten Unbreaking/fire/armor rules. */
    public void wear(DamageSource source, double originalAmount, double armorAmount) {
        if (!craftSource.isInstance(source) || !bounded(originalAmount) || !bounded(armorAmount))
            throw new IllegalArgumentException("Invalid native Echo armor observation");
        try {
            Object nativeSource = sourceHandle.invoke(source);
            // Matches LivingEntity's accepted-damage dispatch. Native equipment
            // handles actual ArmorItem, canBeHurtBy, Unbreaking and break effects.
            if ((Boolean) tagged.invoke(nativeSource, damagesHelmet))
                hurtEquipment.invoke(carrier, nativeSource, (float) originalAmount, helmetSlot);
            if (!(Boolean) tagged.invoke(nativeSource, bypassesArmor))
                hurtEquipment.invoke(carrier, nativeSource, (float) armorAmount, armorSlots);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Native Echo armor observation failed", error);
        }
    }

    private static boolean bounded(double amount) {
        return Double.isFinite(amount) && amount >= 0 && amount <= 1_000_000;
    }
}
