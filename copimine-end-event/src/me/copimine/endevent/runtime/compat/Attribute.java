package me.copimine.endevent.runtime.compat;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/** Typed registry aliases across the 1.21.1 and 26.3 attribute-key rename.
 * Values and callers retain the same health, damage, knockback and scale semantics.
 * Resolve only after Bukkit has installed its registries.
 */
public final class Attribute {
    public static final org.bukkit.attribute.Attribute GENERIC_MAX_HEALTH = required("max_health");
    public static final org.bukkit.attribute.Attribute GENERIC_ATTACK_DAMAGE = required("attack_damage");
    public static final org.bukkit.attribute.Attribute GENERIC_ATTACK_KNOCKBACK = required("attack_knockback");
    public static final org.bukkit.attribute.Attribute GENERIC_SCALE = required("scale");

    private Attribute() { }

    static org.bukkit.attribute.Attribute required(String key) {
        // 1.21.1 exposes a plain enum and can run recovery policy tests without
        // a live registry provider. Newer Paper exposes registry-backed values.
        if (org.bukkit.attribute.Attribute.class.isEnum()) {
            try {
                return (org.bukkit.attribute.Attribute) org.bukkit.attribute.Attribute.class
                        .getField("GENERIC_" + key.toUpperCase(java.util.Locale.ROOT)).get(null);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Required legacy attribute is unavailable: " + key, error);
            }
        }
        org.bukkit.attribute.Attribute value = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        if (value == null) value = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + key));
        if (value == null) throw new IllegalStateException("Required Minecraft attribute is unavailable: " + key);
        return value;
    }
}
