package me.copimine.client;

import me.copimine.client.mixin.EndermanEyesFeatureRendererMixin;
import net.minecraft.entity.Entity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EndermanEyesFeatureRendererMixinDescriptorTest {
    @Test
    void eyesCallbackKeepsTheTargetEntityDescriptorWithoutMixinTypeSpecialization() {
        Class<?> mixinType = EndermanEyesFeatureRendererMixin.class;
        assertEquals(0, mixinType.getTypeParameters().length,
                "a generic Mixin parameter is specialized to LivingEntity and no longer matches the Entity target");

        Method handler = Arrays.stream(mixinType.getDeclaredMethods())
                .filter(method -> method.getName().equals("copimine$hideVanillaGuardianEyes"))
                .findFirst()
                .orElseThrow();
        assertEquals(Entity.class, handler.getParameterTypes()[3],
                "the injection handler must keep the erased EyesFeatureRenderer Entity argument");
    }
}
