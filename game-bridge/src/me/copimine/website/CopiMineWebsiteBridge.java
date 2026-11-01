package me.copimine.website;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Подписанный мост между сайтом и игровым сервером.
 *
 * Сайт кладёт в {@code plugins/CopiMineWebsiteBridge/requests} JSON-файл вида
 * {@code {"payload": "...", "signature": "hmac-sha256"}} и вызывает команду
 * {@code cmwebbridge <id>} через RCON. Плагин проверяет подпись, выполняет операцию
 * и пишет результат в {@code responses/<id>.json}. Хэш payload делает повтор
 * той же операции безопасным: ответ берётся из файла, действие не повторяется.
 *
 * Команды исполняются только из консоли и только в главном потоке — иначе Bukkit
 * меняет состояние мира не из того потока и падает.
 */
public final class CopiMineWebsiteBridge extends JavaPlugin implements CommandExecutor, Listener {

    private final Gson gson = new Gson();
    private final Set<UUID> frozen = new HashSet<>();
    private String secret;
    private Path requests;
    private Path responses;
    private volatile boolean flushing;
    private volatile long flushedAt;
    private volatile String flushError = "";

    /** Миры, которые сайт вправе трогать картой. Остальные наружу не отдаём. */
    private static final Set<String> MAPS = Set.of(
            "public_overworld", "public_nether", "public_end",
            "admin_overworld", "admin_nether", "admin_end");

    /** Сколько времени запрос остаётся действительным. */
    private static final long REQUEST_TTL_MS = 120_000L;
    private static final long REQUEST_SKEW_MS = 5_000L;

    /** Максимальный уровень эффекта, который разрешено выдать через сайт. */
    private static final int MAX_AMPLIFIER = 4;

    @Override
    public void onEnable() {
        this.secret = System.getenv("PLUGIN_API_KEY");
        if (this.secret != null && this.secret.length() >= 32) {
            try {
                this.requests = this.getDataFolder().toPath().resolve("requests");
                this.responses = this.getDataFolder().toPath().resolve("responses");
                Files.createDirectories(this.requests);
                Files.createDirectories(this.responses);
                Objects.requireNonNull(this.getCommand("cmwebbridge")).setExecutor(this);
                this.getServer().getPluginManager().registerEvents(this, this);
            } catch (Exception cause) {
                throw new IllegalStateException("Cannot initialize signed website bridge", cause);
            }
        } else {
            this.getLogger().severe("PLUGIN_API_KEY is missing; disabling bridge");
            this.getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !(sender instanceof RemoteConsoleCommandSender)) {
            return true;
        }
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Website mutations require main thread");
        }
        JsonObject answer;
        try {
            if (args.length != 1) {
                throw new Rejected("invalid_request", "Некорректный запрос.");
            }
            answer = args[0].equals("health") ? this.ok(this.health()) : this.signed(args[0]);
        } catch (Rejected rejected) {
            answer = this.error(rejected.code, rejected.getMessage());
        } catch (Exception failure) {
            this.getLogger().warning("Website bridge failed: " + failure.getClass().getSimpleName());
            answer = this.error("game_operation_failed",
                    "Игровая операция не подтверждена. Проверьте состояние перед повтором.");
        }
        sender.sendMessage("CMWEB:" + Base64.getEncoder()
                .encodeToString(this.gson.toJson(answer).getBytes(StandardCharsets.UTF_8)));
        return true;
    }

    private JsonObject signed(String id) throws Exception {
        if (!id.matches("[a-f0-9]{32}")) {
            throw new Rejected("invalid_request", "Некорректный идентификатор запроса.");
        }
        Path request = this.requests.resolve(id + ".json");
        Path response = this.responses.resolve(id + ".json");
        if (Files.isSymbolicLink(request) || !Files.isRegularFile(request) || Files.size(request) > 65536L) {
            throw new Rejected("invalid_request", "Запрос недоступен.");
        }

        JsonObject envelope = JsonParser.parseString(Files.readString(request)).getAsJsonObject();
        String payload = envelope.get("payload").getAsString();
        String signature = envelope.get("signature").getAsString();

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(this.secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(signature),
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)))) {
            throw new Rejected("signature", "Подпись игрового запроса не прошла проверку.");
        }

        String hash = sha(payload.getBytes(StandardCharsets.UTF_8));

        // Ответ уже есть — возвращаем его, не выполняя действие второй раз.
        if (Files.isRegularFile(response) && !Files.isSymbolicLink(response)) {
            JsonObject stored = JsonParser.parseString(Files.readString(response)).getAsJsonObject();
            if (!stored.get("hash").getAsString().equals(hash)) {
                throw new Rejected("idempotency_conflict", "Запрос изменился.");
            }
            if (!stored.has("result")) {
                throw new Rejected("operation_uncertain",
                        "Операция была прервана. Проверьте фактическое состояние на сервере.");
            }
            return stored.getAsJsonObject("result");
        }

        JsonObject body = JsonParser.parseString(payload).getAsJsonObject();
        long age = System.currentTimeMillis() - body.get("createdAt").getAsLong();
        if (!id.equals(body.get("id").getAsString()) || age < -REQUEST_SKEW_MS || age > REQUEST_TTL_MS) {
            throw new Rejected("request_expired", "Срок игрового запроса истёк.");
        }

        JsonObject stored = new JsonObject();
        stored.addProperty("hash", hash);
        this.write(response, stored);

        JsonObject result;
        try {
            result = this.ok(this.execute(body.get("operation").getAsString(),
                    body.has("params") && body.get("params").isJsonObject()
                            ? body.getAsJsonObject("params") : new JsonObject()));
        } catch (Rejected rejected) {
            result = this.error(rejected.code, rejected.getMessage());
        }

        stored.add("result", result);
        this.write(response, stored);
        return result;
    }

    private JsonObject execute(String operation, JsonObject params) throws Exception {
        switch (operation) {
            case "health":
                return this.health();
            case "map.health":
                return this.mapHealth();
            case "map.control":
                return this.mapControl(params);
            case "map.update":
                return this.mapUpdate();
            case "map.flush":
                return this.mapFlush();
            case "link.send":
                return this.linkSend(params);
            case "inventory.snapshot":
                return this.snapshot(this.player(params));
            case "inventory.remove":
                return this.inventoryRemove(params);
            default:
                return this.action(operation, params);
        }
    }

    private JsonObject mapControl(JsonObject params) throws Exception {
        String mode = string(params, "mode");
        if (!Set.of("start", "stop").contains(mode)) {
            throw new Rejected("invalid_map_control", "Неизвестное управление картой.");
        }
        this.bluePlugin();
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bluemap " + mode)) {
            throw new Rejected("map_command_failed", "BlueMap отклонил команду.");
        }
        return this.accepted();
    }

    private JsonObject mapUpdate() throws Exception {
        this.bluePlugin();
        JsonObject health = this.mapHealth();
        if (!health.get("available").getAsBoolean()) {
            throw new Rejected("bluemap_unavailable", "BlueMap ещё загружается.");
        }
        for (JsonElement element : health.getAsJsonArray("maps")) {
            String name = element.getAsString();
            if (MAPS.contains(name) && !Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bluemap update " + name)) {
                throw new Rejected("map_command_failed", "Обновление карты не принято.");
            }
        }
        return this.accepted();
    }

    private JsonObject mapFlush() throws Exception {
        Object bluemap = this.bluePlugin();
        if (this.flushing) {
            throw new Rejected("map_flushing", "Сохранение карты уже выполняется.");
        }
        if (this.mapHealth().get("rendering").getAsBoolean()) {
            throw new Rejected("map_not_paused", "Сначала дождитесь остановки рендера.");
        }
        this.flushing = true;
        this.flushError = "";
        CompletableFuture.runAsync(() -> {
            try {
                bluemap.getClass().getMethod("save").invoke(bluemap);
                this.flushedAt = System.currentTimeMillis();
            } catch (Exception failure) {
                this.flushError = failure.getClass().getSimpleName();
            } finally {
                this.flushing = false;
            }
        });
        return this.accepted();
    }

    private JsonObject linkSend(JsonObject params) throws Exception {
        Player player = this.player(params);
        boolean authenticated = Bukkit.getOnlineMode();
        Plugin authme = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authme != null && authme.isEnabled()) {
            Class<?> api = authme.getClass().getClassLoader().loadClass("fr.xephi.authme.api.v3.AuthMeApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            authenticated = (Boolean) api.getMethod("isAuthenticated", Player.class).invoke(instance, player);
        }
        if (!authenticated) {
            throw new Rejected("minecraft_auth_required",
                    "Сначала войдите в Minecraft через AuthMe, затем запросите код снова.");
        }
        String code = string(params, "code");
        if (!code.matches("[A-Za-z0-9]{6,12}")) {
            throw new Rejected("invalid_code", "Некорректный код привязки.");
        }
        player.sendMessage("§aCopiMine: §fКод привязки сайта: §e" + code
                + "§f. Не сообщайте его другим игрокам. Срок — 5 минут.");
        JsonObject result = new JsonObject();
        result.addProperty("name", player.getName());
        result.addProperty("uuid", player.getUniqueId().toString());
        result.addProperty("delivered", true);
        return result;
    }

    private JsonObject inventoryRemove(JsonObject params) throws Exception {
        Player player = this.player(params);
        String kind = string(params, "kind");
        Inventory inventory = this.inventory(player, kind);
        if (!string(params, "expectedVersion").equals(this.fingerprint(inventory, kind))) {
            throw new Rejected("stale_inventory",
                    "Инвентарь изменился. Обновите снимок и повторите действие.");
        }
        int slot = integer(params, "slot");
        int quantity = integer(params, "quantity");
        if (slot < 0 || slot >= inventory.getSize() || quantity < 1 || quantity > 99) {
            throw new Rejected("invalid_slot", "Проверьте слот и количество.");
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.getType().isAir() || stack.getAmount() < quantity) {
            throw new Rejected("stale_inventory", "Предмет или количество изменились.");
        }
        if (stack.getAmount() == quantity) {
            inventory.setItem(slot, null);
        } else {
            ItemStack remainder = stack.clone();
            remainder.setAmount(stack.getAmount() - quantity);
            inventory.setItem(slot, remainder);
        }
        player.updateInventory();
        player.saveData();
        JsonObject result = this.snapshot(player);
        result.addProperty("removed", quantity);
        return result;
    }

    // ---------------------------------------------------------------- действия

    private JsonObject action(String operation, JsonObject params) throws Exception {
        String reason = params.has("reason") ? string(params, "reason") : "Действие администратора сайта";
        if (reason.length() > 500) {
            throw new Rejected("invalid_reason", "Причина слишком длинная.");
        }
        JsonObject args = params.has("params") && params.get("params").isJsonObject()
                ? params.getAsJsonObject("params") : params;
        String name = operation.startsWith("action.") ? operation.substring(7) : operation;

        switch (name) {
            case "kick":
                this.player(params).kickPlayer(reason);
                break;
            case "ban":
                this.ban(params, reason, args);
                break;
            case "ban.ip":
                this.banIp(params, reason);
                break;
            case "unban":
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(target(params));
                break;
            case "mute":
                this.mute(params, reason);
                break;
            case "warn":
                this.player(params).sendMessage("§eCopiMine: §fпредупреждение. §7" + reason);
                break;
            case "jail":
                this.jail(params, reason);
                break;
            case "unjail":
                this.unjail(params);
                break;

            case "heal":
                this.heal(params);
                break;
            case "feed":
                this.feed(params);
                break;
            case "kill":
                this.player(params).setHealth(0.0);
                break;
            case "extinguish":
                this.player(params).setFireTicks(0);
                break;
            case "effect.clear":
                this.clearEffects(this.player(params));
                break;
            case "fly":
                this.fly(params, args);
                break;
            case "god":
                this.player(params).setInvulnerable(bool(args, "enabled"));
                break;
            case "gamemode":
                this.player(params).setGameMode(
                        GameMode.valueOf(string(args, "mode").toUpperCase(Locale.ROOT)));
                break;

            case "freeze":
                this.frozen.add(this.player(params).getUniqueId());
                break;
            case "unfreeze":
                this.frozen.remove(this.player(params).getUniqueId());
                break;

            case "teleport":
                this.teleport(params, args);
                break;
            case "tp.spawn":
                this.player(params).teleport(this.world(args).getSpawnLocation());
                break;
            case "tp.bed":
                this.toBed(params);
                break;
            case "tp.up10":
                this.player(params).teleport(this.player(params).getLocation().add(0, 10, 0));
                break;
            case "tp.random":
                this.toRandom(params);
                break;

            case "give":
                this.give(params, args);
                break;
            case "inventory.clear":
                this.player(params).getInventory().clear();
                break;
            case "inventory.armor-off":
                ((PlayerInventory) this.player(params).getInventory()).setArmorContents(new ItemStack[4]);
                break;
            case "inventory.repair-hand":
                this.repairHand(this.player(params));
                break;

            case "fun.title":
                this.player(params).sendTitle(string(args, "title"), string(args, "subtitle"), 10, 60, 20);
                break;
            case "fun.lightning":
                this.player(params).getWorld().strikeLightningEffect(this.player(params).getLocation());
                break;
            case "fun.launch":
                this.player(params).setVelocity(new Vector(0, 1.4, 0));
                break;
            case "fun.knockback":
                this.player(params).setVelocity(new Vector(0.9, 0.4, 0.9));
                break;
            case "effects.burn5":
                this.player(params).setFireTicks(100);
                break;
            case "fun.blind":
                this.addEffect(params, PotionEffectType.BLINDNESS, 200, 0);
                break;
            case "effects.slow10":
                this.addEffect(params, PotionEffectType.SLOWNESS, 200, 2);
                break;
            case "effects.god10":
                this.addEffect(params, PotionEffectType.RESISTANCE, 200, MAX_AMPLIFIER);
                break;
            case "fun.pumpkin":
                this.player(params).getInventory().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                break;

            case "time.day":
                this.world(args).setTime(1000L);
                break;
            case "time.night":
                this.world(args).setTime(13000L);
                break;
            case "weather.clear":
                this.world(args).setStorm(false);
                break;
            case "weather.rain":
                this.world(args).setStorm(true);
                break;
            case "save":
                for (World world : Bukkit.getWorlds()) {
                    world.save();
                }
                break;

            case "whitelist.add":
            case "whitelist.remove": {
                String nick = target(params);
                if (!nick.matches("[A-Za-z0-9_]{1,32}")) {
                    throw new Rejected("invalid_player", "Некорректный ник.");
                }
                Bukkit.getOfflinePlayer(nick).setWhitelisted(name.endsWith("add"));
                break;
            }

            default:
                throw new Rejected("unsupported_action", "Это действие не подключено к серверу.");
        }

        JsonObject result = new JsonObject();
        result.addProperty("executed", true);
        result.addProperty("action", name);
        result.addProperty("completedAt", System.currentTimeMillis());
        return result;
    }

    private void heal(JsonObject params) throws Rejected {
        Player player = this.player(params);
        player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue());
    }

    private void feed(JsonObject params) throws Rejected {
        Player player = this.player(params);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
    }

    private void clearEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void addEffect(JsonObject params, PotionEffectType type, int ticks, int amplifier)
            throws Rejected {
        this.player(params).addPotionEffect(
                new PotionEffect(type, ticks, Math.min(amplifier, MAX_AMPLIFIER), false, true, true));
    }

    private void fly(JsonObject params, JsonObject args) throws Rejected {
        Player player = this.player(params);
        boolean enabled = bool(args, "enabled");
        player.setAllowFlight(enabled);
        if (!enabled) {
            player.setFlying(false);
        }
    }

    private void jail(JsonObject params, String reason) throws Rejected {
        Player player = this.player(params);
        player.sendMessage("§cCopiMine: §fвы помещены в тюрьму. §7" + reason);
        // Обездвиживаем: сильное замедление держит игрока на месте, не ломая выход/вход.
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE,
                MAX_AMPLIFIER * 60, false, false, false));
    }

    private void unjail(JsonObject params) throws Rejected {
        Player player = this.player(params);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.sendMessage("§aCopiMine: §fвы освобождены из тюрьмы.");
    }

    private void toBed(JsonObject params) throws Rejected {
        Player player = this.player(params);
        Location bed = player.getRespawnLocation();
        if (bed == null) {
            throw new Rejected("invalid_parameter", "У игрока нет точки возрождения.");
        }
        player.teleport(bed);
    }

    private void toRandom(JsonObject params) throws Rejected {
        Player player = this.player(params);
        Location spot = player.getLocation().clone()
                .add((Math.random() - 0.5) * 32, 0, (Math.random() - 0.5) * 32);
        World world = spot.getWorld();
        // Не телепортируем в невыгруженный чанк и в пустоту под миром.
        if (!world.isChunkGenerated(spot.getBlockX() >> 4, spot.getBlockZ() >> 4)) {
            throw new Rejected("ungenerated_chunk", "Рядом нет загруженного места для перемещения.");
        }
        spot.setY(Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, spot.getY())));
        player.teleport(spot);
    }

    private void ban(JsonObject params, String reason, JsonObject args) throws Rejected {
        String nick = target(params);
        if (!nick.matches("[A-Za-z0-9_]{1,32}")) {
            throw new Rejected("invalid_player", "Некорректный ник.");
        }
        Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                .addBan(nick, reason, expiry(args), "CopiMine website");
        Player online = Bukkit.getPlayerExact(nick);
        if (online != null) {
            online.kickPlayer(reason);
        }
    }

    private void banIp(JsonObject params, String reason) throws Rejected {
        Player player = this.player(params);
        String address = params.has("ip") ? string(params, "ip")
                : player.getAddress() == null ? "" : player.getAddress().getAddress().getHostAddress();
        if (!address.matches("[0-9a-fA-F:.]{3,45}")) {
            throw new Rejected("invalid_parameter", "Не удалось определить IP игрока.");
        }
        Bukkit.getBanList(org.bukkit.BanList.Type.IP)
                .addBan(address, reason, (Date) null, "CopiMine website");
        player.kickPlayer(reason);
    }

    private void mute(JsonObject params, String reason) throws Rejected {
        Player player = this.player(params);
        player.sendMessage("§cCopiMine: §fвам ограничен чат. §7" + reason);
        // Ограничение чата ведёт штатная команда, если её реализует другой плагин.
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mute " + player.getName() + " " + reason)) {
            this.getLogger().info("Chat restriction requested for " + player.getName());
        }
    }

    private void teleport(JsonObject params, JsonObject args) throws Rejected {
        Player player = this.player(params);
        World world = this.world(args);
        double x = args.get("x").getAsDouble();
        double y = args.get("y").getAsDouble();
        double z = args.get("z").getAsDouble();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 2.999998E7 || Math.abs(z) > 2.999998E7
                || y < world.getMinHeight() || y > world.getMaxHeight()) {
            throw new Rejected("invalid_coordinates", "Некорректные координаты.");
        }
        if (!world.isChunkGenerated((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
            throw new Rejected("ungenerated_chunk",
                    "Телепортация в несуществующий чанк через сайт запрещена.");
        }
        player.teleport(new Location(world, x, y, z));
    }

    private void give(JsonObject params, JsonObject args) throws Rejected {
        Player player = this.player(params);
        String id = string(args, "itemId");
        Material material = Material.matchMaterial(id);
        if (material == null || material.isAir()) {
            throw new Rejected("invalid_parameter", "Неизвестный предмет: " + id);
        }
        int quantity = args.has("quantity") ? Math.max(1, Math.min(64, integer(args, "quantity"))) : 1;
        ItemStack stack = new ItemStack(material, quantity);
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.updateInventory();
    }

    private void repairHand(Player player) throws Rejected {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || !(hand.getItemMeta() instanceof Damageable damageable)) {
            throw new Rejected("invalid_parameter", "В руке нет предмета с прочностью.");
        }
        damageable.setDamage(0);
        hand.setItemMeta((ItemMeta) damageable);
        player.updateInventory();
    }

    private World world(JsonObject args) throws Rejected {
        World world = Bukkit.getWorld(string(args, "world"));
        if (world == null) {
            throw new Rejected("world_unavailable", "Мир не загружен.");
        }
        return world;
    }

    private Date expiry(JsonObject args) {
        if (args.has("expiresAt") && args.get("expiresAt").isJsonPrimitive()) {
            long millis = args.get("expiresAt").getAsLong();
            if (millis > System.currentTimeMillis()) {
                return new Date(millis);
            }
        }
        return null;
    }

    // -------------------------------------------------------------- состояние

    private JsonObject health() {
        JsonObject health = new JsonObject();
        health.addProperty("ready", true);
        health.addProperty("minecraft", Bukkit.getMinecraftVersion());
        health.addProperty("paper", Bukkit.getVersion());

        JsonArray plugins = new JsonArray();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.isEnabled()) {
                plugins.add(plugin.getName());
            }
        }
        health.add("plugins", plugins);

        JsonArray players = new JsonArray();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!this.hidden(player)) {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", player.getName());
                entry.addProperty("uuid", player.getUniqueId().toString());
                players.add(entry);
            }
        }
        health.add("players", players);
        health.addProperty("online", Bukkit.getOnlinePlayers().size());
        health.addProperty("tps", Bukkit.getTPS()[0]);
        health.addProperty("mspt", Bukkit.getAverageTickTime());

        JsonArray worlds = new JsonArray();
        for (World world : Bukkit.getWorlds()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", world.getName());
            entry.addProperty("dimension", world.getEnvironment().name());
            entry.addProperty("loadedChunks", world.getLoadedChunks().length);
            worlds.add(entry);
        }
        health.add("worlds", worlds);
        return health;
    }

    private boolean hidden(Player player) {
        return player.getGameMode() == GameMode.SPECTATOR
                || player.getMetadata("vanished").stream().anyMatch(meta -> meta.asBoolean())
                || player.isInvisible();
    }

    private Object bluePlugin() throws Exception {
        Plugin bluemap = Bukkit.getPluginManager().getPlugin("BlueMap");
        if (bluemap == null || !bluemap.isEnabled()) {
            throw new Rejected("bluemap_unavailable", "BlueMap не подключён.");
        }
        Object api = bluemap.getClass().getMethod("getPlugin").invoke(bluemap);
        if (api == null) {
            throw new Rejected("bluemap_unavailable", "BlueMap ещё загружается.");
        }
        return api;
    }

    private JsonObject mapHealth() throws Exception {
        Object api = this.bluePlugin();
        Object core = api.getClass().getMethod("getBlueMap").invoke(api);
        JsonObject health = new JsonObject();
        health.addProperty("available", core != null);
        health.addProperty("flushing", this.flushing);
        health.addProperty("flushedAt", this.flushedAt);
        health.addProperty("flushError", this.flushError);
        if (core == null) {
            health.addProperty("rendering", false);
            health.addProperty("queued", 0);
            health.add("maps", new JsonArray());
            return health;
        }
        Object manager = api.getClass().getMethod("getRenderManager").invoke(api);
        health.addProperty("rendering", (Boolean) manager.getClass().getMethod("isRunning").invoke(manager));
        health.addProperty("queued", (Integer) manager.getClass()
                .getMethod("getScheduledRenderTaskCount").invoke(manager));
        Object current = manager.getClass().getMethod("getCurrentRenderTask").invoke(manager);
        health.addProperty("busy", current != null);
        if (current != null) {
            Class<?> task = manager.getClass().getClassLoader()
                    .loadClass("de.bluecolored.bluemap.common.rendermanager.RenderTask");
            health.addProperty("progress",
                    ((Number) task.getMethod("estimateProgress").invoke(current)).doubleValue());
        }
        Map<?, ?> maps = (Map<?, ?>) core.getClass().getMethod("getMaps").invoke(core);
        JsonArray names = new JsonArray();
        for (Object key : maps.keySet()) {
            if (MAPS.contains(key.toString())) {
                names.add(key.toString());
            }
        }
        health.add("maps", names);
        return health;
    }

    private JsonObject snapshot(Player player) throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("id", UUID.randomUUID().toString());
        snapshot.addProperty("player", player.getName());
        snapshot.addProperty("uuid", player.getUniqueId().toString());
        snapshot.addProperty("source", "live");
        snapshot.addProperty("capturedAt", System.currentTimeMillis());
        snapshot.add("inventory", this.slots(player.getInventory(), 41));
        snapshot.add("enderChest", this.slots(player.getEnderChest(), 27));
        snapshot.addProperty("inventoryVersion", this.fingerprint(player.getInventory(), "inventory"));
        snapshot.addProperty("enderVersion", this.fingerprint(player.getEnderChest(), "ender"));
        return snapshot;
    }

    private JsonArray slots(Inventory inventory, int count) {
        JsonArray slots = new JsonArray();
        for (int index = 0; index < count; index++) {
            ItemStack stack = inventory.getItem(index);
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", index);
            if (stack != null && !stack.getType().isAir()) {
                entry.addProperty("material", stack.getType().name());
                entry.addProperty("amount", stack.getAmount());
                entry.addProperty("serialized",
                        Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
                if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
                    entry.addProperty("name", stack.getItemMeta().getDisplayName());
                }
            } else {
                entry.addProperty("material", "AIR");
                entry.addProperty("amount", 0);
            }
            slots.add(entry);
        }
        return slots;
    }

    private String fingerprint(Inventory inventory, String kind) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(kind.getBytes(StandardCharsets.UTF_8));
        for (int index = 0; index < inventory.getSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            byte[] bytes = stack == null ? new byte[0] : stack.serializeAsBytes();
            digest.update(ByteBuffer.allocate(8).putInt(index).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private Inventory inventory(Player player, String kind) throws Rejected {
        if (kind.equals("inventory")) {
            return player.getInventory();
        }
        if (kind.equals("ender")) {
            return player.getEnderChest();
        }
        throw new Rejected("invalid_container", "Неизвестный контейнер.");
    }

    private Player player(JsonObject params) throws Rejected {
        if (params.has("uuid")) {
            try {
                Player player = Bukkit.getPlayer(UUID.fromString(string(params, "uuid")));
                if (player != null) {
                    return player;
                }
            } catch (IllegalArgumentException ignored) {
                // Не UUID — ищем по нику ниже.
            }
        }
        String nick = params.has("player") ? string(params, "player")
                : params.has("name") ? string(params, "name") : target(params);
        if (!nick.matches("[A-Za-z0-9_]{1,32}")) {
            throw new Rejected("invalid_player", "Некорректный ник.");
        }
        Player player = Bukkit.getPlayerExact(nick);
        if (player == null) {
            throw new Rejected("player_offline", "Игрок должен быть на сервере для этой операции.");
        }
        return player;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (this.frozen.contains(event.getPlayer().getUniqueId()) && event.hasChangedPosition()) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ вспомогательное

    private JsonObject ok(JsonObject data) {
        JsonObject answer = new JsonObject();
        answer.addProperty("ok", true);
        answer.add("data", data);
        return answer;
    }

    private JsonObject error(String code, String message) {
        JsonObject answer = new JsonObject();
        answer.addProperty("ok", false);
        answer.addProperty("code", code);
        answer.addProperty("message", message);
        return answer;
    }

    private JsonObject accepted() {
        JsonObject result = new JsonObject();
        result.addProperty("accepted", true);
        return result;
    }

    private void write(Path path, JsonObject value) throws Exception {
        Path temporary = path.resolveSibling("." + path.getFileName() + "." + System.nanoTime());
        Files.writeString(temporary, this.gson.toJson(value), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
    }

    private static String sha(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String target(JsonObject params) throws Rejected {
        return string(params, params.has("player") ? "player" : "target");
    }

    private static String string(JsonObject params, String key) throws Rejected {
        if (!params.has(key) || !params.get(key).isJsonPrimitive()) {
            throw new Rejected("invalid_parameter", "Не заполнен обязательный параметр.");
        }
        String value = params.get(key).getAsString();
        if (value.isEmpty() || value.length() > 256) {
            throw new Rejected("invalid_parameter", "Некорректное значение параметра.");
        }
        return value;
    }

    private static int integer(JsonObject params, String key) throws Rejected {
        try {
            return params.get(key).getAsInt();
        } catch (RuntimeException failure) {
            throw new Rejected("invalid_parameter", "Ожидалось целое число.");
        }
    }

    private static boolean bool(JsonObject params, String key) {
        return params.has(key) && params.get(key).isJsonPrimitive()
                && params.get(key).getAsBoolean();
    }

    /** Отказ операции: код и сообщение уходят на сайт как есть. */
    static final class Rejected extends Exception {
        final String code;

        Rejected(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
