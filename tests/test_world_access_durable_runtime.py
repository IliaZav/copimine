"""Execute WorldCore's actual access-service and durable configuration methods."""
from pathlib import Path
import subprocess

import pytest
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def world_access_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("world-access-durable")
    source = (ROOT / "copimine-world-core/src/me/copimine/worldcore/CopiMineWorldCore.java").read_text(encoding="utf-8")
    signatures = ["OperationResult setWorldState(", "class WorldAccessServiceImpl",
                  "boolean handleBorderCommand(", "boolean handleCommand("]
    for signature in ("OperationResult persistWorldAccessChange(", "void saveWorldAccessConfiguration(",
                      "String worldAccessReceiptPath(", "boolean reconcileWorldAccessConfiguration("):
        if signature in source:
            signatures.append(signature)
    methods = "\n".join(extract(source, signature) for signature in signatures)
    java = r'''
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.logging.Logger;
public class WorldAccessDurabilityProbe {
    File dataFolder;
    final YamlConfiguration config=new YamlConfiguration();
    WorldAccess netherAccess,endAccess;
    String pendingWorldAccessConfiguration;
    int evacuations,reloadRequests;
    WorldLimit overworldLimit=new WorldLimit(10000,true);
    final WorldAccessService service=new WorldAccessServiceImpl();
    WorldAccessDurabilityProbe(File folder) throws Exception {
        dataFolder=folder;
        if(new File(folder,"config.yml").isFile())config.load(new File(folder,"config.yml"));
        else {
            Files.createDirectories(folder.toPath());
            config.set("world_access.end.enabled",false);
            config.set("world_access.nether.enabled",false);
            config.set("unrelated.setting","preserved");
            config.save(new File(folder,"config.yml"));
        }
        reloadLocalConfig();
    }
    YamlConfiguration getConfig(){return config;}
    File getDataFolder(){return dataFolder;}
    Logger getLogger(){return Logger.getLogger("world-access-durable");}
    void reloadLocalConfig(){
        endAccess=new WorldAccess(config.getBoolean("world_access.end.enabled"));
        netherAccess=new WorldAccess(config.getBoolean("world_access.nether.enabled"));
    }
    void saveConfig(){
        // Verified pinned JavaPlugin.saveConfig catches IOException and returns normally.
        try {config.save(new File(dataFolder,"config.yml"));}catch(IOException ignored){}
    }
    void reloadConfig(){
        reloadRequests++;
        try{config.load(new File(dataFolder,"config.yml"));}catch(IOException ignored){}
    }
    static class CommandSender {void sendMessage(String message){}}
    record WorldLimit(int radius,boolean enabled){}
    int parseInt(String value,int fallback){try{return Integer.parseInt(value);}catch(NumberFormatException error){return fallback;}}
    int playersOutside(int radius){return 0;}
    OperationResult applyOverworldBorder(){return new OperationResult(true,"border applied");}
    void enforceWorldAccessAndBorders(){}
    boolean handleWorldToggleCommand(CommandSender sender,String[] args){throw new AssertionError("unexpected toggle branch");}
    void sendHelp(CommandSender sender){}
    String color(String text){return text;}
    void evacuatePlayers(WorldAccess access,String message){evacuations++;}
    boolean matchesAccessWorld(WorldAccess access,World world){return access!=null;}
    record WorldAccess(boolean enabled){}
    record OperationResult(boolean success,String message){}
    record WorldAccessResult(boolean success,boolean changed,String code,String message){}
    static class World{}
    interface WorldAccessService {
        WorldAccessResult setEndEnabled(boolean enabled,String actor,String key);
        boolean isEndEnabled();boolean isEndWorld(World world);
    }
    // Minimal configuration adapter. Production owns commit/replay/rollback and
    // uses real Files/FileChannel operations; this adapter only serializes values.
    static class ConfigurationSection {
        final Set<String> keys;
        ConfigurationSection(Set<String> keys){this.keys=keys;}
        Set<String> getKeys(boolean deep){return keys;}
    }
    static class YamlConfiguration {
        final Map<String,Object> values=new TreeMap<>();
        void set(String key,Object value){if(value==null)values.remove(key);else values.put(key,value);}
        boolean contains(String key){return values.containsKey(key)||values.keySet().stream().anyMatch(k->k.startsWith(key+"."));}
        boolean isBoolean(String key){return values.get(key) instanceof Boolean;}
        boolean getBoolean(String key){return Boolean.TRUE.equals(values.get(key));}
        boolean getBoolean(String key,boolean fallback){return values.get(key) instanceof Boolean b?b:fallback;}
        String getString(String key){Object value=values.get(key);return value==null?null:value.toString();}
        String getString(String key,String fallback){String value=getString(key);return value==null?fallback:value;}
        ConfigurationSection getConfigurationSection(String prefix){
            Set<String> keys=new HashSet<>();
            for(String key:values.keySet())if(key.startsWith(prefix+"."))keys.add(key.substring(prefix.length()+1).split("\\.",2)[0]);
            return keys.isEmpty()?null:new ConfigurationSection(keys);
        }
        String saveToString(){
            StringBuilder text=new StringBuilder();
            values.forEach((key,value)->text.append(Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8)))
                .append(':').append(value instanceof Boolean?'b':'s').append(':')
                .append(Base64.getEncoder().encodeToString(value.toString().getBytes(StandardCharsets.UTF_8))).append('\n'));
            return text.toString();
        }
        void loadFromString(String text)throws IOException{
            values.clear();
            for(String line:text.split("\\R")){if(line.isBlank())continue;String[] fields=line.split(":",3);
                if(fields.length!=3)throw new IOException("invalid configuration");
                String key=new String(Base64.getDecoder().decode(fields[0]),StandardCharsets.UTF_8);
                String value=new String(Base64.getDecoder().decode(fields[2]),StandardCharsets.UTF_8);
                values.put(key,fields[1].equals("b")?Boolean.valueOf(value):value);
            }
        }
        void save(File file)throws IOException{Files.writeString(file.toPath(),saveToString(),StandardCharsets.UTF_8);}
        void load(File file)throws IOException{loadFromString(Files.readString(file.toPath(),StandardCharsets.UTF_8));}
    }

    // Inject only read-back faults after an actual atomic move. Every file write,
    // move and force uses the real filesystem; the failure is at the IO boundary.
    static class Files {
        static boolean failReadAfterNextMove,persistentReadFailure;
        static Path unreadableOnce;
        static int atomicMoves;
        static Path createDirectories(Path p)throws IOException{return java.nio.file.Files.createDirectories(p);}
        static Path writeString(Path p,CharSequence v)throws IOException{return java.nio.file.Files.writeString(p,v);}
        static Path writeString(Path p,CharSequence v,java.nio.charset.Charset c)throws IOException{return java.nio.file.Files.writeString(p,v,c);}
        static Path createTempFile(Path p,String prefix,String suffix)throws IOException{return java.nio.file.Files.createTempFile(p,prefix,suffix);}
        static boolean isSymbolicLink(Path p){return java.nio.file.Files.isSymbolicLink(p);}
        static Path move(Path from,Path to,CopyOption...options)throws IOException{
            Path result=java.nio.file.Files.move(from,to,options);atomicMoves++;
            if(failReadAfterNextMove){unreadableOnce=to;failReadAfterNextMove=false;}
            return result;
        }
        static String readString(Path p,java.nio.charset.Charset c)throws IOException{
            if(p.equals(unreadableOnce)){
                if(!persistentReadFailure)unreadableOnce=null;
                throw new IOException("injected read-back failure after successful atomic move");
            }
            return java.nio.file.Files.readString(p,c);
        }
        static boolean deleteIfExists(Path p)throws IOException{return java.nio.file.Files.deleteIfExists(p);}
        static java.util.stream.Stream<Path> list(Path p)throws IOException{return java.nio.file.Files.list(p);}
    }

    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void noTemporaryFiles(File root)throws IOException{
        try(var paths=Files.list(root.toPath())){require(paths.noneMatch(p->p.getFileName().toString().endsWith(".tmp")),"temporary files leaked");}
    }
    public static void main(String[] args)throws Exception{
        String mode=args[0];File root=new File(args[1]);
        var p=new WorldAccessDurabilityProbe(root);
        if(mode.equals("post-publication-border-write")||mode.equals("post-publication-reload")
                ||mode.equals("admin-border-after-recovery")){
            Files.failReadAfterNextMove=true;
            Files.persistentReadFailure=!mode.equals("admin-border-after-recovery");
            require(!p.service.setEndEnabled(true,"event","victory-1").success(),"inject publication verification failure");
            String published=java.nio.file.Files.readString(new File(root,"config.yml").toPath(),StandardCharsets.UTF_8);
            CommandSender sender=new CommandSender();
            if(mode.equals("post-publication-reload"))p.handleCommand(sender,new String[]{"reload"});
            else p.handleBorderCommand(sender,new String[]{"border","set","5000"});
            var disk=new YamlConfiguration();
            disk.loadFromString(java.nio.file.Files.readString(new File(root,"config.yml").toPath(),StandardCharsets.UTF_8));
            require(disk.contains(p.worldAccessReceiptPath("victory-1")),"admin config path erased a published receipt");
            if(mode.equals("admin-border-after-recovery")){
                require("5000".equals(disk.getString("world_limits.overworld.radius"))&&p.service.isEndEnabled(),
                    "verified recovery must permit ordinary border changes and retain End access");
            }else{
                require(published.equals(disk.saveToString()),"unverified publication must block unrelated config writes");
                require(p.reloadRequests==0,"reload must not bypass unresolved publication recovery");
            }
        }else if(mode.equals("post-publication-readback-failure")||mode.equals("unresolved-readback-blocks-change")){
            Files.failReadAfterNextMove=true;
            Files.persistentReadFailure=mode.equals("unresolved-readback-blocks-change");
            var uncertain=p.service.setEndEnabled(true,"event","victory-1");
            require(!uncertain.success(),"unverified publication must not report success");
            var disk=new YamlConfiguration();
            disk.loadFromString(java.nio.file.Files.readString(new File(root,"config.yml").toPath(),StandardCharsets.UTF_8));
            String receipt=p.worldAccessReceiptPath("victory-1");
            require(disk.getBoolean("world_access.end.enabled")&&disk.contains(receipt),
                "fault must occur after the real atomic move published state and receipt");
            int movesAfterPublication=Files.atomicMoves;
            var close=p.service.setEndEnabled(false,"admin","close-1");
            disk.loadFromString(java.nio.file.Files.readString(new File(root,"config.yml").toPath(),StandardCharsets.UTF_8));
            require(disk.contains(receipt),"later operation erased an already-published receipt using stale memory");
            if(Files.persistentReadFailure){
                require(!close.success()&&Files.atomicMoves==movesAfterPublication
                    &&disk.getBoolean("world_access.end.enabled"),
                    "unresolved read-back must block later mutation until verified recovery");
            }else if(close.success()){
                require(!disk.getBoolean("world_access.end.enabled"),"successful later close must be durable");
                var replay=p.service.setEndEnabled(true,"event","victory-1");
                require(!replay.success()&&!p.service.isEndEnabled(),"stale published victory reopened a later admin close");
            }else{
                require(Files.atomicMoves==movesAfterPublication&&disk.getBoolean("world_access.end.enabled"),
                    "failed-closed later operation must preserve published disk state");
            }
        }else if(mode.equals("failed-open")){
            File blocked=new File(root,"not-a-directory");Files.writeString(blocked.toPath(),"sentinel");p.dataFolder=blocked;
            var result=p.service.setEndEnabled(true,"event","victory-1");
            require(!result.success(),"failed disk write must never report OPENED");
            require(!p.service.isEndEnabled()&&!p.config.getBoolean("world_access.end.enabled"),"failed write leaked live state");
            require(p.evacuations==0,"failed open triggered effects");
        }else if(mode.equals("failed-close")){
            require(p.service.setEndEnabled(true,"event","victory-1").success(),"prepare open");
            File blocked=new File(root,"not-a-directory");Files.writeString(blocked.toPath(),"sentinel");p.dataFolder=blocked;
            var result=p.service.setEndEnabled(false,"admin","close-1");
            require(!result.success()&&p.service.isEndEnabled(),"failed close must preserve existing access");
            require(p.evacuations==0,"failed close evacuated players");
        }else if(mode.equals("open-restart")){
            var result=p.service.setEndEnabled(true,"event","victory-1");
            require(result.success()&&result.changed(),"open succeeds once");
            p=new WorldAccessDurabilityProbe(root);
            require(p.service.isEndEnabled(),"cold restart lost open state");
            require("preserved".equals(p.config.getString("unrelated.setting")),"unrelated config lost");
            result=p.service.setEndEnabled(true,"event","victory-1");
            require(result.success()&&!result.changed(),"same request not idempotent");
        }else if(mode.equals("replay-after-close")||mode.equals("replay-after-close-restart")){
            require(p.service.setEndEnabled(true,"event","victory-1").success(),"open");
            require(p.service.setEndEnabled(false,"admin","close-1").success(),"close");
            if(mode.endsWith("restart"))p=new WorldAccessDurabilityProbe(root);
            var result=p.service.setEndEnabled(true,"event","victory-1");
            require(!result.success()&&!result.changed(),"superseded request must be rejected");
            require(!p.service.isEndEnabled(),"stale event reopened admin-closed End");
        }else if(mode.equals("conflicting-key")){
            require(p.service.setEndEnabled(true,"event","same-key").success(),"open");
            var result=p.service.setEndEnabled(false,"event","same-key");
            require(!result.success()&&p.service.isEndEnabled(),"changed-payload replay applied");
        }else if(mode.equals("already-open-receipt")){
            require(p.setWorldState(false,true).success(),"admin open");
            require(p.service.setEndEnabled(true,"event","victory-1").success(),"record same-state request");
            require(p.setWorldState(false,false).success(),"admin close");
            p=new WorldAccessDurabilityProbe(root);
            require(!p.service.setEndEnabled(true,"event","victory-1").success()&&!p.service.isEndEnabled(),"already-open request lacked durable receipt");
        }else if(mode.equals("new-key-after-close")){
            p.service.setEndEnabled(true,"event","victory-1");p.service.setEndEnabled(false,"admin","close-1");
            var result=p.service.setEndEnabled(true,"admin","open-2");
            require(result.success()&&result.changed()&&p.service.isEndEnabled(),"new authorized operation blocked");
        }else if(mode.equals("blank-key")){
            require(!p.service.setEndEnabled(true,"event"," ").success()&&!p.service.isEndEnabled(),"blank key accepted");
        }else throw new AssertionError(mode);
        noTemporaryFiles(root);
        System.out.println("PASS "+mode);
    }
''' + methods + "\n}\n"
    path = out / "WorldAccessDurabilityProbe.java"
    path.write_text(java, encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(out), str(path)], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=40)
    assert compiled.returncode == 0, compiled.stderr
    return out


@pytest.mark.parametrize("scenario", ["failed-open", "failed-close", "open-restart", "replay-after-close",
    "replay-after-close-restart", "conflicting-key", "already-open-receipt", "new-key-after-close", "blank-key",
    "post-publication-readback-failure", "unresolved-readback-blocks-change",
    "post-publication-border-write", "post-publication-reload", "admin-border-after-recovery"])
def test_world_access_is_durable_and_replay_safe(world_access_probe, tmp_path, scenario):
    result = subprocess.run(["java", "-cp", str(world_access_probe), "WorldAccessDurabilityProbe", scenario, str(tmp_path)], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20)
    assert result.returncode == 0, result.stdout + result.stderr
