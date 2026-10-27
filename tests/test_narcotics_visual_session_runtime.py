"""Exercise the real visual session service at its bridge and scheduler boundaries."""

from pathlib import Path
import os
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "copimine-narcotics/src/me/copimine/visualruntime/VisualRuntimeService.java"
COMPLETION = SERVICE.with_name("VisualSessionCompletionPolicy.java")


def java_tool(name):
    java_home = os.environ.get("JAVA_HOME")
    executable = name + (".exe" if os.name == "nt" else "")
    if java_home:
        candidate = Path(java_home) / "bin" / executable
        if candidate.is_file():
            return str(candidate)
    found = shutil.which(name)
    assert found, f"{name} is required for the Narcotics visual runtime test"
    return found


def test_visual_session_updates_presentation_when_overdose_changes(tmp_path):
    # Reverting the production change to refresh only on deadline extension must
    # fail: the bridge would retain the normal presentation for the same ID.
    sources = {
        "net/kyori/adventure/key/Key.java": "package net.kyori.adventure.key; public class Key {}",
        "net/kyori/adventure/title/Title.java": "package net.kyori.adventure.title; public class Title {}",
        "org/bukkit/Particle.java": "package org.bukkit; public enum Particle { ASH,HAPPY_VILLAGER,TRIAL_SPAWNER_DETECTION_OMINOUS,ITEM_SLIME,PORTAL,END_ROD,SPLASH,SQUID_INK,WITCH,SMOKE }",
        "org/bukkit/Location.java": "package org.bukkit; public class Location { public Location add(double x,double y,double z){return this;} }",
        "org/bukkit/potion/PotionEffectType.java": "package org.bukkit.potion; public enum PotionEffectType { DARKNESS,NAUSEA }",
        "org/bukkit/potion/PotionEffect.java": "package org.bukkit.potion; public record PotionEffect(PotionEffectType type,int ticks,int strength,boolean ambient,boolean particles,boolean icon) {}",
        "org/bukkit/entity/Player.java": r'''
package org.bukkit.entity;
import java.util.*; import org.bukkit.*; import org.bukkit.potion.*;
public class Player {
  public final UUID id=UUID.randomUUID(); public int lastParticleCount; public int particles;
  public final List<PotionEffect> effects=new ArrayList<>();
  public UUID getUniqueId(){return id;} public boolean isOnline(){return true;}
  public Location getLocation(){return new Location();}
  public void spawnParticle(Particle p,Location l,int count,double x,double y,double z,double extra){lastParticleCount=count;particles++;}
  public boolean addPotionEffect(PotionEffect effect){effects.add(effect);return true;}
}
''',
        "me/copimine/narcotics/config/NarcoticsConfigService.java": r'''
package me.copimine.narcotics.config;
public class NarcoticsConfigService {
  public boolean visualsForceDisabled(){return false;} public boolean clientBridgeEnabled(){return true;}
  public boolean allowServerParticleFallback(){return true;} public boolean fallbackToParticles(){return true;}
}
''',
        "me/copimine/clientbridge/ClientCapabilityState.java": "package me.copimine.clientbridge; public class ClientCapabilityState { public boolean clientShaderLike(){return true;} public boolean trueIrisShader(){return true;} }",
        "me/copimine/clientbridge/ClientVisualCommand.java": r'''
package me.copimine.clientbridge;
import java.util.UUID;
public class ClientVisualCommand {
  public interface FallbackHandler {void onFallback(UUID id,String effect,int seconds,float intensity,String source,String reason);}
  public interface FinishHandler {void onFinished(UUID id,String effect,String source,String reason);}
}
''',
        "me/copimine/clientbridge/CopiMineClientBridge.java": r'''
package me.copimine.clientbridge;
import java.util.*; import org.bukkit.entity.Player; import me.copimine.narcotics.config.NarcoticsConfigService;
public class CopiMineClientBridge {
  public boolean available=true; public final Visuals visuals=new Visuals();
  public boolean enabled(){return true;} public void reload(NarcoticsConfigService config){}
  public Visuals visuals(){return visuals;} public Capabilities capabilities(){return new Capabilities();}
  public String routeHint(Player p,String effect){return "test";}
  public static class Capabilities {
    public ClientCapabilityState state(Player p){return null;} public boolean supportsEffect(Player p,String id){return true;}
  }
  public record Sent(String kind,String effect,String pack,int seconds,float intensity,
      ClientVisualCommand.FallbackHandler fallback,ClientVisualCommand.FinishHandler finished) {}
  public class Visuals {
    public final List<Sent> sent=new ArrayList<>();
    public int clears; public boolean rejectNextRefresh;
    public boolean canUse(Player p,String effect){return available;}
    public long sendVisualStart(Player p,String e,String pack,int s,float intensity,int fi,int fo,String source,
        ClientVisualCommand.FallbackHandler fallback,ClientVisualCommand.FinishHandler finished){
      sent.add(new Sent("start",e,pack,s,intensity,fallback,finished));return sent.size();
    }
    public long sendVisualRefresh(Player p,String e,String pack,int s,float intensity,int fi,int fo,String source,
        ClientVisualCommand.FallbackHandler fallback,ClientVisualCommand.FinishHandler finished){
      if(rejectNextRefresh){rejectNextRefresh=false;return -1;}
      sent.add(new Sent("refresh",e,pack,s,intensity,fallback,finished));return sent.size();
    }
    public void clearVisuals(Player p){clears++;}
  }
}
''',
        "me/copimine/narcotics/CopiMineNarcotics.java": r'''
package me.copimine.narcotics;
import java.io.File; import java.util.*; import java.util.logging.Logger; import org.bukkit.entity.Player;
public class CopiMineNarcotics {
  public final Server server=new Server(); public Server getServer(){return server;}
  public File getDataFolder(){return new File(".");} public Logger getLogger(){return Logger.getAnonymousLogger();}
  public static class Server {
    public final Scheduler scheduler=new Scheduler(); public Player player;
    public Scheduler getScheduler(){return scheduler;} public Player getPlayer(UUID id){return player!=null && player.id.equals(id)?player:null;}
  }
  public static class Scheduler {
    public int next; public final Map<Integer,Runnable> tasks=new HashMap<>();
    public void cancelTask(int id){tasks.remove(id);}
    public Task runTaskLater(CopiMineNarcotics p,Runnable action,long ticks){int id=++next;tasks.put(id,action);return new Task(id);}
    public void runTask(CopiMineNarcotics p,Runnable action){action.run();}
  }
  public record Task(int id) {public int getTaskId(){return id;}}
}
''',
        "VisualSessionProbe.java": r'''
import java.util.*;
import me.copimine.clientbridge.CopiMineClientBridge;
import me.copimine.clientbridge.CopiMineClientBridge.Sent;
import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.visualruntime.VisualRuntimeService;
import org.bukkit.entity.Player;
public class VisualSessionProbe {
  static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
  public static void main(String[] args) throws Exception {
    var plugin=new CopiMineNarcotics(); var player=new Player();plugin.server.player=player;
    var bridge=new CopiMineClientBridge(); var service=new VisualRuntimeService(plugin,new NarcoticsConfigService(),bridge);
    service.apply(player,"CHAOS",120,false); Sent normal=bridge.visuals.sent.get(0);
    int clears=bridge.visuals.clears;
    service.apply(player,"chaos",30,false);
    require(bridge.visuals.sent.size()==1,"identical shorter request must not reload the client");
    service.apply(player,"CHAOS",30,true);
    require(bridge.visuals.sent.size()==2,"normal to overdose must send a visual refresh even without extending expiry");
    Sent overdose=bridge.visuals.sent.get(1);
    require(overdose.kind().equals("refresh") && overdose.intensity()==1.0F,"overdose must update the existing client presentation");
    require(Set.of("crucify.zip","cursed_metamorphopsia.zip","ctr_vcr.zip","nms_1_6.zip").contains(overdose.pack()),"overdose uses the approved dark shader set");
    require(overdose.seconds()>30 && overdose.seconds()<=120,"overdose transition preserves the longer original expiry");
    require(bridge.visuals.clears==clears,"transition must not clear the existing client shader session");
    normal.finished().onFinished(player.id,"CHAOS","old","late");
    require(service.hasSession(player.id),"stale normal completion must not clear the overdose session");
    service.apply(player,"CHAOS",30,false);
    require(bridge.visuals.sent.size()==3 && bridge.visuals.sent.get(2).intensity()==0.85F,"overdose to normal must refresh without extension");
    overdose.fallback().onFallback(player.id,"CHAOS",30,1.0F,"old","late");
    overdose.finished().onFinished(player.id,"CHAOS","old","late");
    require(service.sessionSummary(player.id).contains("CLIENT_MOD_VISUAL"),"stale overdose callbacks cannot replace or clear the newer session");
    service.clear(player);
    require(!service.hasSession(player.id) && bridge.visuals.clears==clears+1,"admin clear ends the current session");
    bridge.visuals.sent.get(2).finished().onFinished(player.id,"CHAOS","old","late");
    require(!service.hasSession(player.id),"late finish cannot recreate an explicitly cleared session");

    service.apply(player,"DESATURATE",30,false);
    Sent original=bridge.visuals.sent.get(3);
    Runnable oldCleanup=plugin.server.scheduler.tasks.values().iterator().next();
    service.apply(player,"DESATURATE",120,false);
    Sent extended=bridge.visuals.sent.get(4);
    require(extended.kind().equals("refresh") && extended.intensity()==0.85F && extended.seconds()>30,
        "same presentation with a later deadline refreshes its client timer");
    original.finished().onFinished(player.id,"DESATURATE","old","late");
    require(service.hasSession(player.id),"old completion cannot clear the extended session");
    service.apply(player,"WOBBLE",30,false);
    require(bridge.visuals.sent.get(5).kind().equals("start") && service.sessionSummary(player.id).startsWith("WOBBLE"),
        "different effect replaces the session through a new start");
    extended.finished().onFinished(player.id,"DESATURATE","old","late");
    oldCleanup.run();
    require(service.sessionSummary(player.id).startsWith("WOBBLE"),"stale completion or cleanup cannot clear a newer effect");
    service.clear(player);

    service.apply(player,"WOBBLE",1,false);
    Runnable expiry=plugin.server.scheduler.tasks.values().iterator().next();
    Thread.sleep(1200L);
    expiry.run();
    require(!service.hasSession(player.id),"expired session is cleared by its scheduled cleanup");

    bridge.available=false;
    service.apply(player,"DESATURATE",120,false);
    require(player.lastParticleCount==12 && player.effects.isEmpty(),"normal fallback is applied once");
    service.apply(player,"DESATURATE",30,true);
    require(player.lastParticleCount==24 && player.effects.size()==1,"changed overdose presentation reaches fallback too");

    var failingPlugin=new CopiMineNarcotics(); var failingPlayer=new Player(); failingPlugin.server.player=failingPlayer;
    var failingBridge=new CopiMineClientBridge();
    var failingService=new VisualRuntimeService(failingPlugin,new NarcoticsConfigService(),failingBridge);
    failingService.apply(failingPlayer,"CHAOS",120,false);
    int failingClears=failingBridge.visuals.clears;
    failingBridge.visuals.rejectNextRefresh=true;
    failingService.apply(failingPlayer,"CHAOS",30,true);
    require(failingBridge.visuals.clears==failingClears+1,
        "failed refresh must request cleanup of the prior authenticated client visual before server fallback");
    require(failingService.sessionSummary(failingPlayer.id).contains("SERVER_PARTICLE_FALLBACK"),
        "failed refresh switches to the server fallback");

    var callbackPlugin=new CopiMineNarcotics(); var callbackPlayer=new Player(); callbackPlugin.server.player=callbackPlayer;
    var callbackBridge=new CopiMineClientBridge();
    var callbackService=new VisualRuntimeService(callbackPlugin,new NarcoticsConfigService(),callbackBridge);
    callbackService.apply(callbackPlayer,"CHAOS",120,false);
    callbackService.apply(callbackPlayer,"CHAOS",30,true);
    Sent pending=callbackBridge.visuals.sent.get(1);
    int callbackClears=callbackBridge.visuals.clears;
    pending.fallback().onFallback(callbackPlayer.id,"CHAOS",30,1.0F,"test","not-acknowledged");
    require(callbackBridge.visuals.clears==callbackClears+1,
        "asynchronous fallback must request cleanup of the prior authenticated client visual");
    require(callbackService.sessionSummary(callbackPlayer.id).contains("SERVER_PARTICLE_FALLBACK"),
        "asynchronous fallback owns the remaining server session");
    System.out.println("VISUAL_SESSION_TRANSITIONS_PASS");
  }
}
''',
    }
    generated = []
    for name, content in sources.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        generated.append(path)
    build = subprocess.run([java_tool("javac"), "-proc:none", "-encoding", "UTF-8", "-d", str(tmp_path),
                            str(SERVICE), str(COMPLETION), *map(str, generated)], capture_output=True, text=True)
    assert build.returncode == 0, build.stdout + build.stderr
    result = subprocess.run([java_tool("java"), "-cp", str(tmp_path), "VisualSessionProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    assert "VISUAL_SESSION_TRANSITIONS_PASS" in result.stdout
