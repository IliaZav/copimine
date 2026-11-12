package me.copimine.endevent.presentation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

/** Modern item presentation without linking the legacy release build to new API types. */
public final class ItemPresentationComponents {
    private static final Method ITEM_MODEL = optional(ItemMeta.class, "setItemModel", NamespacedKey.class);
    private static final Method READ_DATA = optional(ItemMeta.class, "getCustomModelDataComponent");
    private static final Method WRITE_FLOATS = READ_DATA == null ? null
            : required(READ_DATA.getReturnType(), "setFloats", List.class);
    private static final Method WRITE_DATA = READ_DATA == null ? null
            : required(ItemMeta.class, "setCustomModelDataComponent", READ_DATA.getReturnType());

    private ItemPresentationComponents() { }

    /** Legacy packs retain the numeric custom-model-data identity already set by the caller. */
    public static boolean setItemModel(ItemMeta meta, NamespacedKey model) {
        if (ITEM_MODEL == null) return false;
        invoke(ITEM_MODEL, meta, model);
        return true;
    }

    /** A missing transport must cancel the rig cast before it can authorize contact. */
    public static void setFloats(ItemMeta meta, List<Float> floats) {
        if (READ_DATA == null) throw new UnsupportedOperationException("ITEM_FLOAT_COMPONENT_UNAVAILABLE");
        List<Float> frozen = List.copyOf(floats);
        if (frozen.stream().anyMatch(value -> !Float.isFinite(value)))
            throw new IllegalArgumentException("NONFINITE_ITEM_FLOAT_COMPONENT");
        Object component = invoke(READ_DATA, meta);
        if (component == null) throw new IllegalStateException("ITEM_FLOAT_COMPONENT_MISSING");
        invoke(WRITE_FLOATS, component, frozen);
        invoke(WRITE_DATA, meta, component);
    }

    private static Method optional(Class<?> type, String name, Class<?>... parameters) {
        try { return type.getMethod(name, parameters); }
        catch (NoSuchMethodException absent) { return null; }
    }

    private static Method required(Class<?> type, String name, Class<?>... parameters) {
        Method method = optional(type, name, parameters);
        if (method == null) throw new IllegalStateException("INCOMPLETE_ITEM_COMPONENT_API: " + name);
        return method;
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try { return method.invoke(target, arguments); }
        catch (IllegalAccessException | InvocationTargetException unavailable) {
            Throwable cause = unavailable instanceof InvocationTargetException invocation
                    ? invocation.getCause() : unavailable;
            throw new IllegalStateException("ITEM_COMPONENT_WRITE_FAILED: " + method.getName(), cause);
        }
    }
}
