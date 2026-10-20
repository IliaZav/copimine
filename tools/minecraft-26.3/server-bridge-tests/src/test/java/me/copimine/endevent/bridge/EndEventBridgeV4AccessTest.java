package me.copimine.endevent.bridge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

final class EndEventBridgeV4AccessTest {
    @Test
    void optionalBridgeTickIsDefaultAndSafeToCallWithoutSenderImplementation() throws Exception {
        var tick = EndEventBridgeV4Access.class.getMethod("tick");
        assertTrue(tick.isDefault());

        EndEventBridgeV4Access access = (EndEventBridgeV4Access) Proxy.newProxyInstance(
                EndEventBridgeV4Access.class.getClassLoader(),
                new Class<?>[] { EndEventBridgeV4Access.class },
                (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    throw new UnsupportedOperationException("Unexpected bridge call: " + method.getName());
                });

        assertDoesNotThrow(access::tick);
    }
}
