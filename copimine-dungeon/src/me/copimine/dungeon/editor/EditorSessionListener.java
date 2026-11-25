package me.copimine.dungeon.editor;

import me.copimine.dungeon.domain.Cuboid;
import me.copimine.dungeon.domain.EditorOverlay;
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
        player.sendMessage("Точка " + (first ? 1 : 2) + " (" + session.mode().label() + ") "
                + block.getX() + ", " + block.getY() + ", " + block.getZ());
    }

    private void openToolMenu(Player player, EditorSession session) {
        Menu menu = new Menu(player.getUniqueId(), 27, "Жезл строителя · " + session.mode().label());
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
            menu.button(24, Material.BOOK, "Проверить " + session.selectedRoomId(), p -> {
                p.closeInventory();
                p.performCommand("cmdungeon room validate " + session.selectedRoomId());
            });
            if (player.hasPermission(EditorPermissions.ADMIN)) {
                menu.button(26, Material.WRITABLE_BOOK, "Опубликовать " + session.selectedRoomId(),
                        p -> openPublishMenu(p, session));
            }
        }
        player.openInventory(menu.inventory);
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

    public void drawOverlays() {
        if (wand == null || store == null) {
            return;
        }
        for (EditorSession session : sessions.sessions()) {
            Player player = Bukkit.getPlayer(session.actorId());
            if (player == null || !mayEdit(player)
                    || !wand.isAuthentic(player.getInventory().getItemInMainHand(), session.actorId())) {
                sessions.close(session.actorId());
                continue;
            }
            if (!session.overlayVisible()) {
                continue;
            }
            Location eye = player.getEyeLocation();
            double rangeSquared = (double) session.overlayRangeBlocks() * session.overlayRangeBlocks();
            ArrayList<EditorOverlay.Entry> entries = new ArrayList<>();
            if (player.getWorld().getName().equals(session.world())) {
                addOverlay(entries, session, eye, rangeSquared, OverlayType.DRAFT, session.selection(), true);
            }
            var catalog = store.catalog();
            var room = catalog.room(session.selectedRoomId());
            if (room != null && player.getWorld().getName().equals(room.world())) {
                addOverlay(entries, session, eye, rangeSquared, OverlayType.ROOM, room.bounds(), true);
                addOverlay(entries, session, eye, rangeSquared, OverlayType.TRIGGER, room.trigger(), true);
                for (String id : room.gateIds()) {
                    var gate = catalog.gate(id);
                    if (gate != null && gate.world().equals(room.world())) {
                        OverlayType type = gate.style().isSecretFamily() ? OverlayType.SECRET : OverlayType.GATE;
                        addOverlay(entries, session, eye, rangeSquared, type, gate.bounds(), false);
                    }
                }
                for (String id : room.secretIds()) {
                    var secret = catalog.secret(id);
                    if (secret != null && secret.world().equals(room.world())) {
                        addOverlay(entries, session, eye, rangeSquared, OverlayType.SECRET, secret.bounds(), false);
                    }
                }
                for (String id : room.spawnAnchorIds()) {
                    var anchor = catalog.spawn(id);
                    if (anchor != null && anchor.world().equals(room.world())) {
                        addOverlay(entries, session, eye, rangeSquared, id.equals(room.reward().anchorId())
                                ? OverlayType.REWARD : OverlayType.SPAWN,
                                new Cuboid(anchor.x(), anchor.y(), anchor.z(),
                                        anchor.x(), anchor.y(), anchor.z()), false);
                    }
                }
            }
            for (var sample : EditorOverlay.sampleAll(entries, 120)) {
                Location at = new Location(player.getWorld(), sample.x() + 0.5, sample.y() + 0.5, sample.z() + 0.5);
                if (at.distanceSquared(eye) <= rangeSquared) {
                    player.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(sample.color()), 0.8f));
                }
            }
        }
    }

    private static void addOverlay(ArrayList<EditorOverlay.Entry> entries, EditorSession session,
                                   Location eye, double rangeSquared,
                                   OverlayType type, Cuboid bounds, boolean selected) {
        if (bounds != null && distanceToBoundsSquared(eye, bounds) <= rangeSquared && entries.size() < 16
                && (session.overlayFilter() == null || session.overlayFilter() == type)) {
            entries.add(new EditorOverlay.Entry(type, bounds, selected));
        }
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
