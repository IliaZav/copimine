package me.copimine.client;

import net.minecraft.world.item.component.CustomModelData;

/** Reads legacy integer model selectors from the 26.3 float-list component. */
public final class EndRiftItemModelData {
    private EndRiftItemModelData() { }

    public static int firstFloatAsExactInteger(CustomModelData data) {
        if (data == null) return -1;
        Float value = data.getFloat(0);
        if (value == null || !Float.isFinite(value) || value < 0.0F || value > Integer.MAX_VALUE) return -1;
        int integer = Math.round(value);
        return value == integer ? integer : -1;
    }
}
