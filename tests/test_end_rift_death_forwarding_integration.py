"""Execute production ClearLag adapter registration, delegation and teardown."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_delayed_emitter_guard_is_scoped_idempotent_and_fully_reversible(tmp_path):
    files = {
        "org/bukkit/event/Listener.java": "package org.bukkit.event; public interface Listener {}",
        "org/bukkit/event/Event.java": "package org.bukkit.event; public class Event {}",
        "org/bukkit/event/EventException.java": "package org.bukkit.event; public class EventException extends Exception {}",
        "org/bukkit/event/EventPriority.java": "package org.bukkit.event; public enum EventPriority { LOW, NORMAL, HIGH, HIGHEST, MONITOR }",
        "org/bukkit/event/EventHandler.java": "package org.bukkit.event; public @interface EventHandler {}",
        "org/bukkit/plugin/Plugin.java": "package org.bukkit.plugin; public interface Plugin {String getName();boolean isEnabled();}",
        "org/bukkit/plugin/EventExecutor.java": "package org.bukkit.plugin; import org.bukkit.event.*; public interface EventExecutor {void execute(Listener listener,Event event) throws EventException;}",
        "org/bukkit/plugin/RegisteredListener.java": r'''
package org.bukkit.plugin;
import org.bukkit.event.*;
public class RegisteredListener {
    final Listener listener;final EventExecutor executor;final EventPriority priority;final Plugin plugin;final boolean ignore;
    public RegisteredListener(Listener l,EventExecutor e,EventPriority p,Plugin provider,boolean i){listener=l;executor=e;priority=p;plugin=provider;ignore=i;}
    public Listener getListener(){return listener;}public Plugin getPlugin(){return plugin;}
    public EventPriority getPriority(){return priority;}public boolean isIgnoringCancelled(){return ignore;}
    public void callEvent(Event event)throws EventException{executor.execute(listener,event);}
}
''',
        "org/bukkit/event/HandlerList.java": r'''
package org.bukkit.event;
import java.util.*;import org.bukkit.plugin.RegisteredListener;
public class HandlerList {
    public boolean failNext;
    final List<RegisteredListener> handlers=new ArrayList<>();
    public RegisteredListener[] getRegisteredListeners(){return handlers.toArray(RegisteredListener[]::new);}
    public void unregister(RegisteredListener listener){handlers.remove(listener);}
    public void register(RegisteredListener listener){if(failNext){failNext=false;throw new IllegalStateException("registration failure");}if(handlers.contains(listener))throw new IllegalStateException("duplicate");handlers.add(listener);}
}
''',
        "org/bukkit/event/entity/PlayerDeathEvent.java": r'''
package org.bukkit.event.entity;
import org.bukkit.event.*;
public class PlayerDeathEvent extends Event {
    static final HandlerList handlers=new HandlerList();public boolean cancelled;
    public static HandlerList getHandlerList(){return handlers;}
}
''',
        "org/bukkit/event/server/PluginEnableEvent.java": "package org.bukkit.event.server; import org.bukkit.plugin.Plugin; public record PluginEnableEvent(Plugin plugin){public Plugin getPlugin(){return plugin;}}",
        "org/bukkit/event/server/PluginDisableEvent.java": "package org.bukkit.event.server; import org.bukkit.plugin.Plugin; public record PluginDisableEvent(Plugin plugin){public Plugin getPlugin(){return plugin;}}",
        "com/fernsehheft/clearlag/ClearLag.java": "package com.fernsehheft.clearlag; public class ClearLag implements org.bukkit.event.Listener {}",
        "me/copimine/endevent/runtime/EventDeathProtectionListener.java": r'''
package me.copimine.endevent.runtime;
import org.bukkit.event.entity.PlayerDeathEvent;
public class EventDeathProtectionListener {
    public int guardedCalls;
    public void withRetainedDropsHidden(PlayerDeathEvent event,Runnable callback){guardedCalls++;if(!event.cancelled)callback.run();}
}
''',
        "ForwardingIntegrationProbe.java": r'''
import org.bukkit.event.*;import org.bukkit.event.entity.*;import org.bukkit.event.server.*;
import org.bukkit.plugin.*;import java.util.logging.Logger;
import me.copimine.endevent.runtime.*;
public class ForwardingIntegrationProbe {
    static class Provider implements Plugin {String name;boolean enabled=true;Provider(String n){name=n;}public String getName(){return name;}public boolean isEnabled(){return enabled;}}
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static int delegateCalls;
    static EventException delegateFailure;
    public static void main(String[] args)throws Exception {
        var handlers=PlayerDeathEvent.getHandlerList();var plugin=new Provider("ClearLag");
        var listener=new com.fernsehheft.clearlag.ClearLag();
        var original=new RegisteredListener(listener,(l,e)->{delegateCalls++;if(delegateFailure!=null)throw delegateFailure;},EventPriority.HIGH,plugin,false);
        var other=new RegisteredListener(new Listener(){},(l,e)->{},EventPriority.HIGH,plugin,false);
        handlers.register(original);handlers.register(other);
        var protection=new EventDeathProtectionListener();
        var integration=new DeathDropForwardingIntegration(protection,Logger.getAnonymousLogger());
        handlers.failNext=true;
        try{integration.install();throw new AssertionError("expected failed registration");}
        catch(IllegalStateException expected){require(expected.getMessage().equals("registration failure"),"preserve registration error");}
        require(java.util.Arrays.asList(handlers.getRegisteredListeners()).contains(original),"registration failure restores original callback before retry");
        integration.install();integration.install();
        RegisteredListener guarded=null;
        for(var registered:handlers.getRegisteredListeners())if(registered.getListener()==listener)guarded=registered;
        require(guarded!=null && guarded!=original && handlers.getRegisteredListeners().length==2,"wrap exactly the known HIGH emitter once");
        require(guarded.getPlugin()==plugin && guarded.getPriority()==EventPriority.HIGH && !guarded.isIgnoringCancelled(),"preserve original plugin and priority metadata");
        guarded.callEvent(new PlayerDeathEvent());
        require(protection.guardedCalls==1 && delegateCalls==1,"actual registered callback passes through the inventory-view guard");
        guarded.callEvent(new Event());
        require(protection.guardedCalls==1 && delegateCalls==2,"unrelated event is delegated without a death view");
        var cancelled=new PlayerDeathEvent();cancelled.cancelled=true;guarded.callEvent(cancelled);
        require(delegateCalls==2,"cancelled death cannot schedule the known delayed emitter");
        delegateFailure=new EventException();
        try{guarded.callEvent(new PlayerDeathEvent());throw new AssertionError("missing original failure");}
        catch(EventException failure){require(failure==delegateFailure,"preserve the exact registered delegate exception");}
        delegateFailure=null;integration.close();integration.close();integration.install();
        require(java.util.Arrays.asList(handlers.getRegisteredListeners()).contains(original) && handlers.getRegisteredListeners().length==2,"owner disable restores exact original registration with no orphan wrapper");

        var next=new DeathDropForwardingIntegration(protection,Logger.getAnonymousLogger());next.install();
        plugin.enabled=false;next.onPluginDisable(new PluginDisableEvent(plugin));
        require(handlers.getRegisteredListeners().length==1,"forwarder disable removes adapter without resurrecting the disabled registration");
        plugin.enabled=true;handlers.register(original);next.onPluginEnable(new PluginEnableEvent(plugin));
        require(handlers.getRegisteredListeners().length==2 && !java.util.Arrays.asList(handlers.getRegisteredListeners()).contains(original),"forwarder re-enable registers one fresh guard");
        next.close();
        require(java.util.Arrays.asList(handlers.getRegisteredListeners()).contains(original),"second owner teardown restores the re-enabled provider");
    }
}
''',
    }
    for relative, text in files.items():
        path = tmp_path / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    production = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/DeathDropForwardingIntegration.java"
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(production), *map(str, tmp_path.rglob("*.java"))], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "ForwardingIntegrationProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
