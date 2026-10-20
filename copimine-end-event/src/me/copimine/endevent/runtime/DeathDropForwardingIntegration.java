package me.copimine.endevent.runtime;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.RegisteredListener;

/** Scoped compatibility with the installed ClearLag HIGH delayed drop emitter. */
public final class DeathDropForwardingIntegration implements Listener, AutoCloseable {
    private final EventDeathProtectionListener protection;
    private final Logger logger;
    private final Map<RegisteredListener, RegisteredListener> replacements = new IdentityHashMap<>();
    private boolean closed;

    public DeathDropForwardingIntegration(EventDeathProtectionListener protection, Logger logger) {
        this.protection = protection;
        this.logger = logger;
    }

    public void install() {
        if (closed) return;
        HandlerList handlers = PlayerDeathEvent.getHandlerList();
        for (RegisteredListener original : handlers.getRegisteredListeners()) {
            if (replacements.containsKey(original) || !matches(original)) continue;
            RegisteredListener guarded = new RegisteredListener(original.getListener(), (listener, event) -> {
                if (!(event instanceof PlayerDeathEvent death)) {
                    original.callEvent(event);
                    return;
                }
                EventException[] failure = new EventException[1];
                protection.withRetainedDropsHidden(death, () -> {
                    try {
                        original.callEvent(event);
                    } catch (EventException error) {
                        failure[0] = error;
                    }
                });
                if (failure[0] != null) throw failure[0];
            }, original.getPriority(), original.getPlugin(), original.isIgnoringCancelled());
            handlers.unregister(original);
            try {
                handlers.register(guarded);
            } catch (RuntimeException | Error failure) {
                try { handlers.register(original); }
                catch (RuntimeException | Error restoreFailure) { failure.addSuppressed(restoreFailure); }
                throw failure;
            }
            replacements.put(guarded, original);
            logger.info("END_RIFT_DEATH_FORWARDER_GUARD_INSTALLED plugin=ClearLag priority=HIGH");
        }
    }

    private static boolean matches(RegisteredListener listener) {
        return listener.getPlugin().isEnabled()
                && "ClearLag".equals(listener.getPlugin().getName())
                && "com.fernsehheft.clearlag.ClearLag".equals(listener.getListener().getClass().getName())
                && listener.getPriority() == EventPriority.HIGH;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("ClearLag".equals(event.getPlugin().getName())) install();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (!"ClearLag".equals(event.getPlugin().getName())) return;
        HandlerList handlers = PlayerDeathEvent.getHandlerList();
        replacements.entrySet().removeIf(entry -> {
            if (entry.getValue().getPlugin() != event.getPlugin()) return false;
            handlers.unregister(entry.getKey());
            return true;
        });
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        HandlerList handlers = PlayerDeathEvent.getHandlerList();
        for (var entry : replacements.entrySet()) {
            boolean present = false;
            for (RegisteredListener registered : handlers.getRegisteredListeners()) {
                if (registered == entry.getKey()) { present = true; break; }
            }
            handlers.unregister(entry.getKey());
            if (present && entry.getValue().getPlugin().isEnabled()) handlers.register(entry.getValue());
        }
        replacements.clear();
    }
}
