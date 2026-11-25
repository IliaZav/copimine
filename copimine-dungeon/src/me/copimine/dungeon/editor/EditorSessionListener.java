package me.copimine.dungeon.editor;

import me.copimine.dungeon.domain.Cuboid;
import me.copimine.dungeon.domain.DungeonRoomDefinition;
import me.copimine.dungeon.domain.MobTemplate;
import me.copimine.dungeon.domain.ScalingMode;
import me.copimine.dungeon.domain.WaveSpec;
import me.copimine.dungeon.domain.EditorOverlay;
import me.copimine.dungeon.domain.GateStyle;
import me.copimine.dungeon.domain.UnlockMode;
import me.copimine.dungeon.domain.OverlayType;
import me.copimine.dungeon.store.DungeonDefinitionStore;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.util.BoundingBox;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Session-lifecycle guard (spec OV4.3): "dropping, unequipping, changing world,
 * revoke, death and logout immediately remove editor overlays and
 * subscriptions." Tearing the session down removes the overlay and any pending
 * selection so nothing leaks into the ordinary fight.
 */
public final class EditorSessionListener implements Listener {

    private final EditorSessionManager sessions;

    private final DungeonBuilderWand wand;
    private final DungeonDefinitionStore store;

    public EditorSessionListener(EditorSessionManager sessions) {
        this(sessions, null, null);
    }

    public EditorSessionListener(EditorSessionManager sessions, DungeonBuilderWand wand,
                                 DungeonDefinitionStore store) {
        this.sessions = sessions;
        this.wand = wand;
        this.store = store;
    }

    @EventHandler
    public void onWandClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (wand == null || event.getHand() != EquipmentSlot.HAND
                || (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK
                    && action != Action.RIGHT_CLICK_AIR)
                || !wand.isAuthentic(event.getItem(), player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (!mayEdit(player)) {
            sessions.close(player.getUniqueId());
            return;
        }
        EditorSession session = sessions.open(player.getUniqueId(), player.getName());
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            openToolMenu(player, session);
            return;
        }
        var block = event.getClickedBlock();
        if (block == null) return;
        String world = block.getWorld().getName();
        if (!world.equals(session.world())) {
            session.clearSelection();
            session.setWorld(world);
        }
        if (session.mode().readOnly()) {
            return;
        }
        boolean first = action == Action.LEFT_CLICK_BLOCK;
        if (first) session.setCorner1(block.getX(), block.getY(), block.getZ());
        else session.setCorner2(block.getX(), block.getY(), block.getZ());
        Cuboid selection = session.selection();
        player.sendActionBar(Component.text("Точка " + (first ? 1 : 2) + " · " + session.mode().label() + " · "
                + block.getX() + ", " + block.getY() + ", " + block.getZ()
                + (selection == null ? "" : " · " + dimensions(selection))));
    }

    private void openToolMenu(Player player, EditorSession session) {
        Menu menu = new Menu(player.getUniqueId(), 36, "Жезл строителя · " + session.mode().label());
        Material[] icons = {Material.STONE_BRICKS, Material.TARGET, Material.IRON_DOOR,
                Material.CHISELED_STONE_BRICKS, Material.SPAWNER, Material.CHEST, Material.CAMPFIRE,
                Material.FILLED_MAP, Material.ENDER_EYE};
        EditorMode[] modes = EditorMode.values();
        for (int i = 0; i < modes.length; i++) {
            EditorMode mode = modes[i];
            menu.button(i, icons[i], mode.label(), p -> {
                session.setMode(mode);
                p.closeInventory();
                p.sendActionBar(Component.text("Режим " + mode.label() + " · ЛКМ точка 1 · ПКМ точка 2"));
            });
        }
        menu.button(18, Material.COMPASS, "Выбрать комнату", p -> openRoomMenu(p, session, 0));
        menu.button(20, Material.GLOW_INK_SAC, session.overlayVisible() ? "Скрыть рамки" : "Показать рамки", p -> {
            session.setOverlayVisible(!session.overlayVisible());
            openToolMenu(p, session);
        });
        menu.button(22, Material.STRUCTURE_VOID, "Очистить выделение", p -> {
            session.clearSelection();
            p.closeInventory();
        });
        if (session.selectedRoomId() != null) {
            menu.button(23, Material.SPAWNER, "Волны и состав", p -> openWaves(p, session, 0));
            menu.button(25, Material.CHEST, "Добыча", p -> openRewards(p, session));
            menu.button(24, Material.BOOK, "Проверить " + session.selectedRoomId(), p -> {
                p.closeInventory();
                p.performCommand("cmdungeon room validate " + session.selectedRoomId());
            });
            if (player.hasPermission(EditorPermissions.ADMIN)) {
                menu.button(26, Material.WRITABLE_BOOK, "Опубликовать " + session.selectedRoomId(),
                        p -> openPublishMenu(p, session));
            }
        }
        var focused = focus(player, nearbyObjects(player, session), session.overlayRangeBlocks());
        if (focused != null && focused.type() != OverlayType.DRAFT) {
            menu.button(28, Material.SPYGLASS, "Настройки · " + focused.id(),
                    p -> openObjectMenu(p, session, focused));
        }
        menu.button(30, Material.TINTED_GLASS, "Дальность " + session.overlayRangeBlocks() + " блоков", p -> {
            int old = session.overlayRangeBlocks();
            session.setOverlayRangeBlocks(old >= 96 ? 16 : old + 16);
            openToolMenu(p, session);
        });
        menu.button(32, Material.COMPARATOR, session.overlayFilter() == null ? "Фильтр · все"
                : "Фильтр · " + session.overlayFilter().label(), p -> openOverlayFilter(p, session));
        if (session.selection() != null) {
            menu.button(34, Material.LIME_CONCRETE, "Применить выделение", p -> openSelectionMenu(p, session));
        }
        player.openInventory(menu.inventory);
    }

    private void openOverlayFilter(Player player, EditorSession session) {
        Menu menu = new Menu(player.getUniqueId(), 18, "Слои редактора");
        int slot = 0;
        for (OverlayType layer : OverlayType.values()) {
            menu.button(slot++, Material.GLASS, layer.label(), p -> {
                session.setOverlayFilter(layer);
                openToolMenu(p, session);
            });
        }
        menu.button(9, Material.SPYGLASS, "Показывать все слои", p -> {
            session.setOverlayFilter(null);
            openToolMenu(p, session);
        });
        menu.button(13, Material.ARROW, "Назад", p -> openToolMenu(p, session));
        player.openInventory(menu.inventory);
    }

    private void openSelectionMenu(Player player, EditorSession session) {
        Menu menu = new Menu(player.getUniqueId(), 18, "Выделение · " + dimensions(session.selection()));
        String roomId = session.selectedRoomId();
        menu.button(0, Material.BRICKS, "Создать комнату", p -> {
            String id = "room_" + UUID.randomUUID().toString().substring(0, 8);
            p.performCommand("cmdungeon create " + id);
            if (store.catalog().room(id) != null) session.selectRoom(id);
            openToolMenu(p, session);
        });
        if (roomId != null && store.catalog().room(roomId) != null) {
            menu.button(2, Material.BRICKS, "Границы · " + roomId,
                    p -> runAndReturn(p, session, "room bounds " + roomId));
            menu.button(4, Material.TARGET, "Триггер · " + roomId,
                    p -> runAndReturn(p, session, "room trigger " + roomId));
            menu.button(6, Material.IRON_DOOR, "Добавить шлюз", p ->
                    runAndReturn(p, session, "gate add gate_" + UUID.randomUUID().toString().substring(0, 8)));
            menu.button(10, Material.SPAWNER, "Создать зону спавна", p ->
                    runAndReturn(p, session, "spawn zone spawn_" + UUID.randomUUID().toString().substring(0, 8)));
            menu.button(8, Material.CHISELED_STONE_BRICKS, "Добавить секрет", p ->
                    runAndReturn(p, session, "secret add secret_" + UUID.randomUUID().toString().substring(0, 8)));
        }
        menu.button(13, Material.ARROW, "Назад", p -> openToolMenu(p, session));
        player.openInventory(menu.inventory);
    }

    private void openObjectMenu(Player player, EditorSession session, Inspect target) {
        session.selectRoom(target.room());
        String prefix = switch (target.type()) {
            case GATE -> "gate";
            case SECRET -> store.catalog().secret(target.id()) != null ? "secret" : "gate";
            default -> "";
        };
        Menu menu = new Menu(player.getUniqueId(), 36, target.type().label() + " · " + target.id());
        menu.button(0, Material.BOOK, target.details(), p -> p.sendMessage(target.details()));
        if (!prefix.isEmpty()) {
            int slot = 9;
            for (GateStyle style : GateStyle.values()) {
                if (prefix.equals("secret") && !style.isSecretFamily()) continue;
                menu.button(slot++, Material.PURPUR_BLOCK, "Стиль · " + style.token(),
                        p -> runAndReturn(p, session, prefix + " style " + target.id() + " " + style.token()));
            }
            slot = 18;
            for (UnlockMode mode : UnlockMode.values()) {
                menu.button(slot++, Material.TRIPWIRE_HOOK, "Открытие · " + mode.token(),
                        p -> runAndReturn(p, session, prefix + " unlock " + target.id() + " " + mode.token()));
            }
            if (session.selection() != null) menu.button(27, Material.TARGET, "Заменить объём выделением",
                    p -> runAndReturn(p, session, prefix + " bounds " + target.id()));
            if (prefix.equals("gate")) {
                menu.button(22, Material.IRON_DOOR, "Сделать входом",
                        p -> runAndReturn(p, session, "gate role " + target.id() + " entrance"));
                menu.button(23, Material.IRON_TRAPDOOR, "Сделать выходом",
                        p -> runAndReturn(p, session, "gate role " + target.id() + " exit"));
                menu.button(25, Material.COMPASS, "Направление", p -> openGateDirections(p, session, target.id()));
            }
        } else if (target.room() != null) {
            if (target.type() == OverlayType.SPAWN && session.selection() != null) {
                menu.button(18, Material.SPAWNER, "Заменить зону выделением",
                        p -> runAndReturn(p, session, "spawn bounds " + target.id()));
            }
            menu.button(9, Material.BOOK, "Проверить комнату",
                    p -> runAndReturn(p, session, "room validate " + target.room()));
            if (session.selection() != null) menu.button(13, Material.TARGET, "Применить выделение",
                    p -> openSelectionMenu(p, session));
        }
        menu.button(31, Material.ARROW, "Назад", p -> openToolMenu(p, session));
        player.openInventory(menu.inventory);
    }

    private void openWaves(Player player, EditorSession session, int page) {
        DungeonRoomDefinition room = store.catalog().rooms().get(session.selectedRoomId());
        if (room == null) {
            openToolMenu(player, session);
            return;
        }
        Menu menu = new Menu(player.getUniqueId(), 54, "Волны · " + room.id());
        int first = page * 36;
        for (int i = first; i < Math.min(first + 36, room.waves().size()); i++) {
            int waveIndex = i;
            WaveSpec wave = room.waves().get(i);
            menu.button(i - first, Material.SPAWNER, "Волна " + (i + 1) + " · " + wave.totalCount()
                    + " мобов · " + wave.delayTicks() + " тиков", p -> openWave(p, session, waveIndex));
        }
        menu.button(36, Material.LIME_CONCRETE, "Добавить волну", p -> {
            p.performCommand("cmdungeon wave add " + room.id());
            openWaves(p, session, Math.min(room.waves().size(), 63) / 36);
        });
        menu.button(38, Material.COMPARATOR, "Активных одновременно · " + room.maxConcurrentEnemies(), p ->
                openNumber(p, "Лимит активных врагов", room.maxConcurrentEnemies(), 1, 500, 5,
                        "mobs concurrent-cap " + room.id(), next -> openWaves(next, session, page)));
        int slot = 41;
        for (ScalingMode mode : ScalingMode.values()) {
            menu.button(slot++, Material.REDSTONE, (room.scalingMode() == mode ? "✓ " : "") + mode.token(), p -> {
                p.performCommand("cmdungeon mobs scaling " + room.id() + " " + mode.token());
                openWaves(p, session, page);
            });
        }
        if (page > 0) menu.button(45, Material.ARROW, "Предыдущая страница", p -> openWaves(p, session, page - 1));
        menu.button(49, Material.ARROW, "К жезлу", p -> openToolMenu(p, session));
        if (first + 36 < room.waves().size()) {
            menu.button(53, Material.ARROW, "Следующая страница", p -> openWaves(p, session, page + 1));
        }
        player.openInventory(menu.inventory);
    }

    private void openWave(Player player, EditorSession session, int index) {
        DungeonRoomDefinition room = store.catalog().rooms().get(session.selectedRoomId());
        if (room == null || index >= room.waves().size()) {
            openWaves(player, session, 0);
            return;
        }
        WaveSpec wave = room.waves().get(index);
        Menu menu = new Menu(player.getUniqueId(), 36, "Состав волны " + (index + 1));
        int slot = 0;
        for (MobTemplate template : MobTemplate.values()) {
            int count = wave.group().stream().filter(group -> group.template() == template)
                    .mapToInt(me.copimine.dungeon.domain.EnemyGroup::count).sum();
            boolean vanilla = template.name().startsWith("ORDINARY_");
            Material icon = Material.matchMaterial(template.baseType() + "_SPAWN_EGG");
            menu.button(slot++, icon == null ? Material.SPAWNER : icon,
                    (vanilla ? "Ванильный · " : "Ивентовый · ") + template.id() + " × " + count,
                    p -> openNumber(p, template.id(), count, 0, 200, 10,
                            "wave mob " + room.id() + " " + index + " " + template.id(),
                            next -> openWave(next, session, index)));
        }
        menu.button(27, Material.CLOCK, "Задержка · " + wave.delayTicks() + " тиков",
                p -> openNumber(p, "Задержка в тиках", wave.delayTicks(), 0, 72000, 20,
                        "wave delay " + room.id() + " " + index, next -> openWave(next, session, index)));
        menu.button(31, Material.ARROW, "К волнам", p -> openWaves(p, session, index / 36));
        player.openInventory(menu.inventory);
    }

    private void openNumber(Player player, String label, int value, int min, int max, int step,
                            String command, Consumer<Player> parent) {
        Menu menu = new Menu(player.getUniqueId(), 27, label);
        int[] deltas = {-step * 10, -step, -1, 1, step, step * 10};
        for (int i = 0; i < deltas.length; i++) {
            int delta = deltas[i];
            int adjusted = Math.clamp(value + delta, min, max);
            menu.button(i + 1, delta < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                    (delta > 0 ? "+" : "") + delta, p -> openNumber(p, label, adjusted, min, max, step, command, parent));
        }
        menu.button(13, Material.LIME_CONCRETE, "Сохранить · " + value, p -> {
            p.performCommand("cmdungeon " + command + " " + value);
            parent.accept(p);
        });
        menu.button(22, Material.ARROW, "Назад", parent);
        player.openInventory(menu.inventory);
    }

    private void openRewards(Player player, EditorSession session) {
        DungeonRoomDefinition room = store.catalog().rooms().get(session.selectedRoomId());
        if (room == null) {
            openToolMenu(player, session);
            return;
        }
        Menu menu = new Menu(player.getUniqueId(), 27, "Добыча · " + room.id());
        menu.button(0, Material.CHEST, "Таблица · " + room.reward().lootTableId(), p -> openLootTables(p, session, 0));
        menu.button(4, Material.ENDER_CHEST, "Способ · " + room.reward().mode().token(), p ->
                runAndReturn(p, session, "reward mode " + room.id() + " personal"));
        menu.button(8, Material.PAPER, "Личная выдача · инвентарь или почта",
                p -> p.sendMessage("Таблицу датапака можно указать командой /cmdungeon reward loot "
                        + room.id() + " namespace:path"));
        menu.button(22, Material.ARROW, "Назад", p -> openToolMenu(p, session));
        player.openInventory(menu.inventory);
    }

    private void openLootTables(Player player, EditorSession session, int page) {
        String room = session.selectedRoomId();
        var tables = java.util.Arrays.stream(org.bukkit.loot.LootTables.values())
                .map(org.bukkit.loot.LootTables::getKey)
                .filter(key -> key.getKey().startsWith("chests/") && Bukkit.getLootTable(key) != null)
                .sorted(Comparator.comparing(org.bukkit.NamespacedKey::toString)).toList();
        Menu menu = new Menu(player.getUniqueId(), 54, "Таблицы добычи");
        int first = page * 45;
        for (int i = first; i < Math.min(first + 45, tables.size()); i++) {
            String table = tables.get(i).toString();
            menu.button(i - first, Material.CHEST, table, p -> {
                p.performCommand("cmdungeon reward loot " + room + " " + table);
                openRewards(p, session);
            });
        }
        if (page > 0) menu.button(45, Material.ARROW, "Предыдущая", p -> openLootTables(p, session, page - 1));
        menu.button(49, Material.ARROW, "Назад", p -> openRewards(p, session));
        if (first + 45 < tables.size()) {
            menu.button(53, Material.ARROW, "Следующая", p -> openLootTables(p, session, page + 1));
        }
        player.openInventory(menu.inventory);
    }

    private void openGateDirections(Player player, EditorSession session, String id) {
        Menu menu = new Menu(player.getUniqueId(), 18, "Направление · " + id);
        String[] directions = {"auto", "north", "south", "east", "west", "up", "down"};
        for (int i = 0; i < directions.length; i++) {
            String direction = directions[i];
            menu.button(i, Material.COMPASS, direction,
                    p -> runAndReturn(p, session, "gate direction " + id + " " + direction));
        }
        menu.button(13, Material.ARROW, "Назад", p -> openToolMenu(p, session));
        player.openInventory(menu.inventory);
    }

    private void runAndReturn(Player player, EditorSession session, String command) {
        player.performCommand("cmdungeon " + command);
        openToolMenu(player, session);
    }

    private void openRoomMenu(Player player, EditorSession session, int page) {
        var rooms = store.catalog().rooms().values().stream()
                .filter(room -> room.world().equals(player.getWorld().getName())).toList();
        Menu menu = new Menu(player.getUniqueId(), 54, "Комнаты · " + player.getWorld().getName());
        int first = page * 45;
        for (int i = 0; i < 45 && first + i < rooms.size(); i++) {
            var room = rooms.get(first + i);
            menu.button(i, room.id().equals(session.selectedRoomId()) ? Material.LODESTONE : Material.BRICKS,
                    room.displayName() + " · " + room.id(), p -> {
                        session.selectRoom(room.id());
                        session.setOverlayVisible(true);
                        p.closeInventory();
                        p.sendActionBar(Component.text("Выбрана комната " + room.displayName()));
                    });
        }
        if (page > 0) menu.button(45, Material.ARROW, "Предыдущая страница",
                p -> openRoomMenu(p, session, page - 1));
        menu.button(49, Material.BLAZE_ROD, "К настройкам жезла", p -> openToolMenu(p, session));
        if (first + 45 < rooms.size()) menu.button(53, Material.ARROW, "Следующая страница",
                p -> openRoomMenu(p, session, page + 1));
        player.openInventory(menu.inventory);
    }

    private void openPublishMenu(Player player, EditorSession session) {
        String roomId = session.selectedRoomId();
        Menu menu = new Menu(player.getUniqueId(), 9, "Публикация · " + roomId);
        menu.button(2, Material.LIME_CONCRETE, "Опубликовать " + roomId, p -> {
            p.closeInventory();
            p.performCommand("cmdungeon room publish " + roomId);
        });
        menu.button(6, Material.RED_CONCRETE, "Вернуться", p -> openToolMenu(p, session));
        player.openInventory(menu.inventory);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(menu.viewer)
                || !mayEdit(player) || !wand.isAuthentic(player.getInventory().getItemInMainHand(), menu.viewer)
                || !sessions.hasSession(menu.viewer)) return;
        Consumer<Player> action = menu.actions.get(event.getRawSlot());
        if (action != null) Bukkit.getScheduler().runTask(wand.plugin(), () -> {
            if (player.isOnline() && mayEdit(player) && sessions.hasSession(menu.viewer)
                    && player.getOpenInventory().getTopInventory().getHolder() == menu
                    && wand.isAuthentic(player.getInventory().getItemInMainHand(), menu.viewer)) action.accept(player);
        });
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    private static final class Menu implements InventoryHolder {
        private final UUID viewer;
        private final Inventory inventory;
        private final Map<Integer, Consumer<Player>> actions = new LinkedHashMap<>();

        private Menu(UUID viewer, int rows, String name) {
            this.viewer = viewer;
            this.inventory = Bukkit.createInventory(this, rows, Component.text(name));
        }

        private void button(int slot, Material material, String text, Consumer<Player> action) {
            ItemStack item = new ItemStack(material);
            var meta = item.getItemMeta();
            meta.displayName(Component.text(text));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            actions.put(slot, action);
        }

        @Override public Inventory getInventory() {
            return inventory;
        }
    }

    private record Inspect(OverlayType type, String id, String room, Cuboid bounds, String details, boolean selected) { }

    public void drawOverlays() {
        if (wand == null || store == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!mayEdit(player) || !wand.isAuthentic(player.getInventory().getItemInMainHand(), id)) {
                if (sessions.hasSession(id)) sessions.close(id);
                continue;
            }
            EditorSession session = sessions.open(id, player.getName());
            if (!session.overlayVisible()) continue;
            Location eye = player.getEyeLocation();
            double rangeSquared = (double) session.overlayRangeBlocks() * session.overlayRangeBlocks();
            List<Inspect> objects = nearbyObjects(player, session);
            Inspect focused = focus(player, objects, session.overlayRangeBlocks());
            if (focused != null) {
                player.sendActionBar(Component.text(focused.type().label() + " · " + focused.id()
                        + " · " + dimensions(focused.bounds()) + " · " + focused.details(),
                        net.kyori.adventure.text.format.TextColor.color(focused.type().color())));
            }
            var entries = objects.stream()
                    .sorted(Comparator.comparing((Inspect entry) -> entry != focused && !entry.selected())
                            .thenComparingDouble(entry -> distanceToBoundsSquared(eye, entry.bounds())))
                    .limit(16)
                    .map(entry -> new EditorOverlay.Entry(entry.type(), entry.bounds(), entry.selected() || entry == focused))
                    .toList();
            for (var sample : EditorOverlay.sampleAll(entries, 150)) {
                Location at = new Location(player.getWorld(), sample.x() + 0.5, sample.y() + 0.5, sample.z() + 0.5);
                if (at.distanceSquared(eye) <= rangeSquared) {
                    player.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(sample.color()), 0.8f));
                }
            }
        }
    }

    private List<Inspect> nearbyObjects(Player player, EditorSession session) {
        String world = player.getWorld().getName();
        Location eye = player.getEyeLocation();
        double range = session.overlayRangeBlocks();
        var entries = new ArrayList<Inspect>();
        var catalog = store.catalog();
        if (world.equals(session.world()) && session.selection() != null)
            entries.add(new Inspect(OverlayType.DRAFT, "выделение", session.selectedRoomId(),
                    session.selection(), session.mode().label(), true));
        for (var room : catalog.rooms().values()) {
            if (!world.equals(room.world())) continue;
            boolean selected = room.id().equals(session.selectedRoomId());
            entries.add(new Inspect(OverlayType.ROOM, room.id(), room.id(), room.bounds(),
                    "волны " + room.waves().size() + " · лимит " + room.maxConcurrentEnemies(), selected));
            if (room.trigger() != null) entries.add(new Inspect(OverlayType.TRIGGER, room.id(), room.id(), room.trigger(),
                    "вход в бой · " + room.displayName(), selected));
        }
        for (var gate : catalog.gates().values()) {
            if (!world.equals(gate.world())) continue;
            entries.add(new Inspect(gate.style().isSecretFamily() ? OverlayType.SECRET : OverlayType.GATE,
                    gate.id(), gate.roomId(), gate.bounds(), (gate.initialOpen() ? "вход" : "выход")
                    + " · " + gate.style().token() + " · " + gate.unlock().token() + " · " + gate.direction(), false));
        }
        for (var secret : catalog.secrets().values()) {
            if (!world.equals(secret.world())) continue;
            entries.add(new Inspect(OverlayType.SECRET, secret.id(), secret.roomId(), secret.bounds(),
                    secret.unlock().token() + " · " + secret.style().token(), false));
        }
        for (var anchor : catalog.spawns().values()) {
            if (!world.equals(anchor.world())) continue;
            var room = catalog.room(anchor.roomId());
            boolean reward = room != null && anchor.id().equals(room.reward().anchorId());
            entries.add(new Inspect(reward ? OverlayType.REWARD : OverlayType.SPAWN, anchor.id(), anchor.roomId(),
                    anchor.bounds(),
                    "комната " + anchor.roomId(), false));
        }
        return entries.stream().filter(entry -> entry.bounds() != null
                && (session.overlayFilter() == null || session.overlayFilter() == entry.type())
                && distanceToBoundsSquared(eye, entry.bounds()) <= range * range).toList();
    }

    private static Inspect focus(Player player, List<Inspect> entries, int range) {
        var eye = player.getEyeLocation();
        var start = eye.toVector();
        var facing = eye.getDirection();
        Inspect best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Inspect entry : entries) {
            Cuboid c = entry.bounds();
            var box = new BoundingBox(c.minX(), c.minY(), c.minZ(), c.maxX() + 1D, c.maxY() + 1D, c.maxZ() + 1D);
            var hit = box.rayTrace(start, facing, range);
            if (hit == null) continue;
            // Small interactive objects win ties against the enclosing room and its trigger.
            double weight = entry.type() == OverlayType.ROOM ? 1.6 : entry.type() == OverlayType.TRIGGER ? .7
                    : entry.type() == OverlayType.DRAFT ? 2.2 : 0;
            double distance = start.distance(hit.getHitPosition()) + weight;
            if (distance < bestScore) {
                best = entry;
                bestScore = distance;
            }
        }
        return best;
    }

    private static String dimensions(Cuboid c) {
        return (c.maxX() - c.minX() + 1) + "×" + (c.maxY() - c.minY() + 1) + "×" + (c.maxZ() - c.minZ() + 1);
    }

    private static double distanceToBoundsSquared(Location eye, Cuboid bounds) {
        double x = Math.max(bounds.minX() - eye.getX(), Math.max(0, eye.getX() - bounds.maxX() - 1.0D));
        double y = Math.max(bounds.minY() - eye.getY(), Math.max(0, eye.getY() - bounds.maxY() - 1.0D));
        double z = Math.max(bounds.minZ() - eye.getZ(), Math.max(0, eye.getZ() - bounds.maxZ() - 1.0D));
        return x * x + y * y + z * z;
    }

    private static boolean mayEdit(Player player) {
        return player.hasPermission(EditorPermissions.BUILDER) || player.hasPermission(EditorPermissions.ADMIN);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        if (wand != null && !wand.isAuthentic(event.getPlayer().getInventory().getItem(event.getNewSlot()),
                event.getPlayer().getUniqueId())) {
            sessions.close(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (wand != null && !wand.isAuthentic(event.getMainHandItem(), event.getPlayer().getUniqueId())) {
            sessions.close(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.close(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        sessions.close(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        sessions.close(player.getUniqueId());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (wand != null && wand.isAuthentic(event.getItemDrop().getItemStack(),
                event.getPlayer().getUniqueId())) {
            sessions.close(event.getPlayer().getUniqueId());
        }
    }
}
