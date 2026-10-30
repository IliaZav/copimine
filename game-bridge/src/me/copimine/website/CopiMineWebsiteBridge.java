package me.copimine.website;

import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Narrow, signed website operations. Every player mutation runs on the server thread. */
public final class CopiMineWebsiteBridge extends JavaPlugin implements CommandExecutor, Listener {
    private final Gson gson = new Gson();
    private final Set<UUID> frozen = new HashSet<>();
    private String secret;
    private Path requests, responses;
    private volatile boolean flushing;
    private volatile long flushedAt;
    private volatile String flushError = "";
    private static final Set<String> MAPS = Set.of("public_overworld", "public_nether", "public_end", "admin_overworld", "admin_nether", "admin_end");

    @Override public void onEnable() {
        secret = System.getenv("PLUGIN_API_KEY");
        if (secret == null || secret.length() < 32) { getLogger().severe("PLUGIN_API_KEY is missing; disabling bridge"); getServer().getPluginManager().disablePlugin(this); return; }
        try {
            requests = getDataFolder().toPath().resolve("requests"); responses = getDataFolder().toPath().resolve("responses");
            Files.createDirectories(requests); Files.createDirectories(responses);
            Objects.requireNonNull(getCommand("cmwebbridge")).setExecutor(this);
            getServer().getPluginManager().registerEvents(this, this);
        } catch (Exception e) { throw new IllegalStateException("Cannot initialize signed website bridge", e); }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender)) return true;
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Website mutations require main thread");
        JsonObject result;
        try {
            if (args.length != 1) throw new Rejected("invalid_request", "Некорректный запрос.");
            if (args[0].equals("health")) result = ok(health());
            else result = signed(args[0]);
        } catch (Rejected e) { result = error(e.code, e.getMessage()); }
        catch (Exception e) { getLogger().warning("Website bridge failed: " + e.getClass().getSimpleName()); result = error("game_operation_failed", "Игровая операция не подтверждена. Проверьте состояние перед повтором."); }
        sender.sendMessage("CMWEB:" + Base64.getEncoder().encodeToString(gson.toJson(result).getBytes(StandardCharsets.UTF_8)));
        return true;
    }

    private JsonObject signed(String id) throws Exception {
        if (!id.matches("[a-f0-9]{32}")) throw new Rejected("invalid_request", "Некорректный идентификатор запроса.");
        Path path = requests.resolve(id + ".json"), receipt = responses.resolve(id + ".json");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > 65536) throw new Rejected("invalid_request", "Запрос недоступен.");
        JsonObject envelope = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        String payload = envelope.get("payload").getAsString(), signature = envelope.get("signature").getAsString();
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(signature), mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)))) throw new Rejected("signature", "Подпись игрового запроса не прошла проверку.");
        String hash = sha(payload.getBytes(StandardCharsets.UTF_8));
        if (Files.isRegularFile(receipt) && !Files.isSymbolicLink(receipt)) {
            JsonObject saved = JsonParser.parseString(Files.readString(receipt)).getAsJsonObject();
            if (!saved.get("hash").getAsString().equals(hash)) throw new Rejected("idempotency_conflict", "Запрос изменился.");
            if (!saved.has("result")) throw new Rejected("operation_uncertain", "Операция была прервана. Проверьте фактическое состояние на сервере.");
            return saved.getAsJsonObject("result");
        }
        JsonObject body = JsonParser.parseString(payload).getAsJsonObject();
        long age = System.currentTimeMillis() - body.get("createdAt").getAsLong();
        if (!id.equals(body.get("id").getAsString()) || age < -5000 || age > 120000) throw new Rejected("request_expired", "Срок игрового запроса истёк.");
        // A crash after the mutation cannot silently replay a destructive action.
        JsonObject saved = new JsonObject(); saved.addProperty("hash", hash); write(receipt, saved);
        JsonObject result;
        try { result = ok(execute(body.get("operation").getAsString(), body.getAsJsonObject("params"))); }
        catch (Rejected e) { result = error(e.code, e.getMessage()); }
        saved.add("result", result); write(receipt, saved);
        return result;
    }

    private JsonObject execute(String operation, JsonObject p) throws Exception {
        if (operation.equals("health")) return health();
        if (operation.equals("map.health")) return mapHealth();
        if (operation.equals("map.control")) {
            String mode = string(p,"mode");
            if (!Set.of("start","stop").contains(mode)) throw new Rejected("invalid_map_control","Неизвестное управление картой.");
            bluePlugin();
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bluemap " + mode)) throw new Rejected("map_command_failed","BlueMap отклонил команду.");
            JsonObject r = new JsonObject(); r.addProperty("accepted",true);return r;
        }
        if (operation.equals("map.update")) {
            bluePlugin();
            JsonObject state=mapHealth();
            if (!state.get("available").getAsBoolean()) throw new Rejected("bluemap_unavailable","BlueMap ещё загружается.");
            for (JsonElement map : state.getAsJsonArray("maps")) {
                String id=map.getAsString();
                if (MAPS.contains(id) && !Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bluemap update " + id)) throw new Rejected("map_command_failed","Обновление карты не принято.");
            }
            JsonObject r = new JsonObject(); r.addProperty("accepted",true);return r;
        }
        if (operation.equals("map.flush")) {
            Object plugin=bluePlugin();
            if (flushing) throw new Rejected("map_flushing","Сохранение карты уже выполняется.");
            if (mapHealth().get("rendering").getAsBoolean()) throw new Rejected("map_not_paused","Сначала дождитесь остановки рендера.");
            flushing=true; flushError="";
            CompletableFuture.runAsync(() -> {
                try { plugin.getClass().getMethod("save").invoke(plugin); flushedAt=System.currentTimeMillis(); }
                catch(Exception e) { flushError=e.getClass().getSimpleName(); }
                finally { flushing=false; }
            });
            JsonObject r = new JsonObject();r.addProperty("accepted",true);return r;
        }
        if (operation.equals("link.send")) {
            Player player = player(p);
            Plugin auth = Bukkit.getPluginManager().getPlugin("AuthMe");
            boolean authenticated = Bukkit.getOnlineMode();
            if (auth != null && auth.isEnabled()) {
                Class<?> api = auth.getClass().getClassLoader().loadClass("fr.xephi.authme.api.v3.AuthMeApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                authenticated = (boolean) api.getMethod("isAuthenticated",Player.class).invoke(instance,player);
            }
            if (!authenticated) throw new Rejected("minecraft_auth_required","Сначала войдите в Minecraft через AuthMe, затем запросите код снова.");
            String code=string(p,"code");
            if (!code.matches("[A-Za-z0-9]{6,12}")) throw new Rejected("invalid_code","Некорректный код привязки.");
            player.sendMessage("§aCopiMine: §fКод привязки сайта: §e"+code+"§f. Не сообщайте его другим игрокам. Срок — 5 минут.");
            JsonObject r=new JsonObject();r.addProperty("name",player.getName());r.addProperty("uuid",player.getUniqueId().toString());r.addProperty("delivered",true);return r;
        }
        if (operation.equals("inventory.snapshot")) return snapshot(player(p));
        if (operation.equals("inventory.remove")) {
            Player player=player(p);String kind=string(p,"kind");Inventory inv=inventory(player,kind);
            String expected=string(p,"expectedVersion");
            if (!expected.equals(fingerprint(inv,kind))) throw new Rejected("stale_inventory","Инвентарь изменился. Обновите снимок и повторите действие.");
            int slot=integer(p,"slot"),quantity=integer(p,"quantity");
            if(slot<0 || slot>=inv.getSize() || quantity<1 || quantity>99) throw new Rejected("invalid_slot","Проверьте слот и количество.");
            ItemStack item=inv.getItem(slot);
            if(item==null || item.getType().isAir() || item.getAmount()<quantity) throw new Rejected("stale_inventory","Предмет или количество изменились.");
            if(item.getAmount()==quantity) inv.setItem(slot,null);
            else { ItemStack next=item.clone();next.setAmount(item.getAmount()-quantity);inv.setItem(slot,next); }
            player.updateInventory();player.saveData();
            JsonObject r=snapshot(player);r.addProperty("removed",quantity);return r;
        }
        return action(operation,p);
    }

    private JsonObject health() {
        JsonObject r=new JsonObject();r.addProperty("ready",true);r.addProperty("minecraft",Bukkit.getMinecraftVersion());r.addProperty("paper",Bukkit.getVersion());
        JsonArray plugins=new JsonArray();for(Plugin plugin:Bukkit.getPluginManager().getPlugins())if(plugin.isEnabled())plugins.add(plugin.getName());r.add("plugins",plugins);
        JsonArray players=new JsonArray();for(Player player:Bukkit.getOnlinePlayers()) {
            if (hidden(player)) continue;
            JsonObject row=new JsonObject();row.addProperty("name",player.getName());row.addProperty("uuid",player.getUniqueId().toString());players.add(row);
        } r.add("players",players);r.addProperty("online",Bukkit.getOnlinePlayers().size());
        r.addProperty("tps",Bukkit.getTPS()[0]);r.addProperty("mspt",Bukkit.getAverageTickTime());
        JsonArray worlds=new JsonArray();for(World w:Bukkit.getWorlds()){JsonObject x=new JsonObject();x.addProperty("name",w.getName());x.addProperty("dimension",w.getEnvironment().name());x.addProperty("loadedChunks",w.getLoadedChunks().length);worlds.add(x);}r.add("worlds",worlds);return r;
    }

    private boolean hidden(Player p) {
        return p.getGameMode()==GameMode.SPECTATOR || p.getMetadata("vanished").stream().anyMatch(v->v.asBoolean()) || p.isInvisible();
    }

    private Object bluePlugin() throws Exception {
        Plugin blue=Bukkit.getPluginManager().getPlugin("BlueMap");
        if(blue==null || !blue.isEnabled())throw new Rejected("bluemap_unavailable","BlueMap не подключён.");
        Object plugin=blue.getClass().getMethod("getPlugin").invoke(blue);
        if(plugin==null)throw new Rejected("bluemap_unavailable","BlueMap ещё загружается.");
        return plugin;
    }

    private JsonObject mapHealth() throws Exception {
        Object plugin=bluePlugin();Object blue=plugin.getClass().getMethod("getBlueMap").invoke(plugin);
        JsonObject r=new JsonObject();r.addProperty("available",blue!=null);r.addProperty("flushing",flushing);r.addProperty("flushedAt",flushedAt);r.addProperty("flushError",flushError);
        if(blue==null){r.addProperty("rendering",false);r.addProperty("queued",0);r.add("maps",new JsonArray());return r;}
        Object manager=plugin.getClass().getMethod("getRenderManager").invoke(plugin);
        r.addProperty("rendering",(boolean)manager.getClass().getMethod("isRunning").invoke(manager));
        r.addProperty("queued",(int)manager.getClass().getMethod("getScheduledRenderTaskCount").invoke(manager));
        Object task=manager.getClass().getMethod("getCurrentRenderTask").invoke(manager);
        r.addProperty("busy",task!=null);
        if(task!=null) {
            Class<?> contract=manager.getClass().getClassLoader().loadClass("de.bluecolored.bluemap.common.rendermanager.RenderTask");
            r.addProperty("progress",((Number)contract.getMethod("estimateProgress").invoke(task)).doubleValue());
        }
        Map<?,?> maps=(Map<?,?>)blue.getClass().getMethod("getMaps").invoke(blue);JsonArray ids=new JsonArray();
        for(Object id:maps.keySet()) if(MAPS.contains(id.toString()))ids.add(id.toString());r.add("maps",ids);return r;
    }

    private JsonObject snapshot(Player p) throws Exception {
        JsonObject r=new JsonObject();r.addProperty("id",UUID.randomUUID().toString());r.addProperty("player",p.getName());r.addProperty("uuid",p.getUniqueId().toString());r.addProperty("source","live");r.addProperty("capturedAt",System.currentTimeMillis());
        r.add("inventory",slots(p.getInventory(),41));r.add("enderChest",slots(p.getEnderChest(),27));r.addProperty("inventoryVersion",fingerprint(p.getInventory(),"inventory"));r.addProperty("enderVersion",fingerprint(p.getEnderChest(),"ender"));return r;
    }
    private JsonArray slots(Inventory inventory,int size) {
        JsonArray rows=new JsonArray();for(int i=0;i<size;i++){
            ItemStack item=inventory.getItem(i);JsonObject row=new JsonObject();row.addProperty("slot",i);
            if(item!=null&&!item.getType().isAir()) { row.addProperty("material",item.getType().name());row.addProperty("amount",item.getAmount());row.addProperty("serialized",Base64.getEncoder().encodeToString(item.serializeAsBytes()));
                if(item.hasItemMeta() && item.getItemMeta().hasDisplayName())row.addProperty("name",item.getItemMeta().getDisplayName()); }
            else {row.addProperty("material","AIR");row.addProperty("amount",0);}rows.add(row);
        }return rows;
    }
    private String fingerprint(Inventory inventory,String kind) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(kind.getBytes(StandardCharsets.UTF_8));
        for(int i=0;i<inventory.getSize();i++) { ItemStack item=inventory.getItem(i);byte[] data=item==null?new byte[0]:item.serializeAsBytes();digest.update(java.nio.ByteBuffer.allocate(8).putInt(i).putInt(data.length).array());digest.update(data); }return HexFormat.of().formatHex(digest.digest());
    }
    private Inventory inventory(Player p,String kind) throws Rejected { if(kind.equals("inventory"))return p.getInventory();if(kind.equals("ender"))return p.getEnderChest();throw new Rejected("invalid_container","Неизвестный контейнер."); }
    private Player player(JsonObject p) throws Rejected {
        if(p.has("uuid")) {
            try {Player x=Bukkit.getPlayer(UUID.fromString(string(p,"uuid")));if(x!=null)return x;}
            catch(IllegalArgumentException ignored) { }
        }
        String name=p.has("player")?string(p,"player"):p.has("name")?string(p,"name"):target(p);if(!name.matches("[A-Za-z0-9_]{1,32}"))throw new Rejected("invalid_player","Некорректный ник.");
        Player player=Bukkit.getPlayerExact(name);if(player==null)throw new Rejected("player_offline","Игрок должен быть на сервере для этой операции.");return player;
    }
    private JsonObject action(String op,JsonObject p) throws Exception {
        String reason=p.has("reason")?string(p,"reason"):"Действие администратора сайта";
        if(reason.length()>500)throw new Rejected("invalid_reason","Причина слишком длинная.");
        JsonObject params=p.has("params")?p.getAsJsonObject("params"):p;
        if(op.startsWith("action."))op=op.substring(7);
        switch(op) {
            case "kick" -> player(p).kickPlayer(reason);
            case "heal" -> {Player x=player(p);x.setHealth(Objects.requireNonNull(x.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).getValue());}
            case "feed" -> {Player x=player(p);x.setFoodLevel(20);x.setSaturation(20);}
            case "kill" -> player(p).setHealth(0);
            case "gamemode" -> player(p).setGameMode(GameMode.valueOf(string(params,"mode").toUpperCase(Locale.ROOT)));
            case "freeze" -> frozen.add(player(p).getUniqueId());
            case "unfreeze" -> frozen.remove(player(p).getUniqueId());
            case "teleport" -> {Player x=player(p);World w=Bukkit.getWorld(string(params,"world"));if(w==null)throw new Rejected("world_unavailable","Мир не загружен.");double a=params.get("x").getAsDouble(),b=params.get("y").getAsDouble(),c=params.get("z").getAsDouble();if(!Double.isFinite(a)||!Double.isFinite(b)||!Double.isFinite(c)||Math.abs(a)>29999980||Math.abs(c)>29999980||b<w.getMinHeight()||b>w.getMaxHeight())throw new Rejected("invalid_coordinates","Некорректные координаты.");if(!w.isChunkGenerated(((int)Math.floor(a))>>4,((int)Math.floor(c))>>4))throw new Rejected("ungenerated_chunk","Телепортация в несуществующий чанк через сайт запрещена.");x.teleport(new Location(w,a,b,c));}
            case "ban" -> {String name=target(p);if(!name.matches("[A-Za-z0-9_]{1,32}"))throw new Rejected("invalid_player","Некорректный ник.");Bukkit.getBanList(BanList.Type.NAME).addBan(name,reason,(Date)null,"CopiMine website");Player x=Bukkit.getPlayerExact(name);if(x!=null)x.kickPlayer(reason);}
            case "unban" -> Bukkit.getBanList(BanList.Type.NAME).pardon(target(p));
            case "whitelist.add", "whitelist.remove" -> {String name=target(p);if(!name.matches("[A-Za-z0-9_]{1,32}"))throw new Rejected("invalid_player","Некорректный ник.");Bukkit.getOfflinePlayer(name).setWhitelisted(op.endsWith("add"));}
            case "time.day", "time.night", "weather.clear", "weather.rain" -> {World w=Bukkit.getWorld(string(params,"world"));if(w==null)throw new Rejected("world_unavailable","Мир не загружен.");switch(op){case "time.day"->w.setTime(1000);case "time.night"->w.setTime(13000);case "weather.clear"->w.setStorm(false);case "weather.rain"->w.setStorm(true);}}
            case "save" -> {for(World w:Bukkit.getWorlds())w.save();}
            default -> throw new Rejected("unsupported_action","Это действие не подключено к серверу.");
        }
        JsonObject r=new JsonObject();r.addProperty("executed",true);r.addProperty("action",op);r.addProperty("completedAt",System.currentTimeMillis());return r;
    }
    @EventHandler(ignoreCancelled=true) public void onMove(PlayerMoveEvent event) {if(frozen.contains(event.getPlayer().getUniqueId())&&event.hasChangedPosition())event.setCancelled(true);}
    private static String target(JsonObject p)throws Rejected{return string(p,p.has("player")?"player":"target");}
    private static String string(JsonObject p,String k)throws Rejected {if(!p.has(k)||!p.get(k).isJsonPrimitive())throw new Rejected("invalid_parameter","Не заполнен обязательный параметр.");return p.get(k).getAsString();}
    private static int integer(JsonObject p,String k)throws Rejected {try{return Integer.parseInt(string(p,k));}catch(NumberFormatException e){throw new Rejected("invalid_parameter","Ожидалось целое число.");}}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private JsonObject ok(JsonObject data){JsonObject r=new JsonObject();r.addProperty("ok",true);r.add("data",data);return r;}
    private JsonObject error(String code,String message){JsonObject r=new JsonObject();r.addProperty("ok",false);r.addProperty("code",code);r.addProperty("message",message);return r;}
    private void write(Path path,JsonObject data)throws Exception {Path temp=path.resolveSibling("."+path.getFileName()+"."+UUID.randomUUID());try{Files.writeString(temp,gson.toJson(data),StandardOpenOption.CREATE_NEW);Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
    private static final class Rejected extends Exception {final String code;Rejected(String code,String message){super(message);this.code=code;}}
}
