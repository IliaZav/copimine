package ac.grim.grimac.utils.lists;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.PacketEvents;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.manager.server.ServerVersion;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.protocol.item.ItemStack;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.protocol.player.GameMode;
import ac.grim.grimac.utils.inventory.Inventory;
import ac.grim.grimac.utils.inventory.InventoryStorage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rebuilt against the live GrimAC 2.3.74-40684fb JAR. The upstream implementation
 * treats Creative inventory predictions as a normal inventory and later calls
 * updateInventory(), which discards valid server-side Creative changes. This keeps
 * upstream behavior for every other game mode and turns off only that correction
 * while the player is in Creative.
 *
 * Upstream source: https://github.com/GrimAnticheat/Grim/tree/40684fb
 * License: GPL-3.0-or-later (the same license as GrimAC).
 */
public class CorrectingPlayerInventoryStorage extends InventoryStorage {
    private static final Set<String> SUPPORTED_INVENTORIES = new HashSet<>(Arrays.asList(
            "CHEST", "DISPENSER", "DROPPER", "PLAYER", "ENDER_CHEST", "SHULKER_BOX", "BARREL", "CRAFTING", "CREATIVE"
    ));

    private final GrimPlayer player;
    private final Map<Integer, Integer> serverIsCurrentlyProcessingThesePredictions = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> pendingFinalizedSlot = new ConcurrentHashMap<>();

    public CorrectingPlayerInventoryStorage(GrimPlayer player, int size) {
        super(size);
        this.player = player;
    }

    public void handleClientClaimedSlotSet(int slotID) {
        if (slotID >= 0 && slotID <= Inventory.ITEMS_END) {
            pendingFinalizedSlot.put(slotID, GrimAPI.INSTANCE.getTickManager().currentTick + 5);
        }
    }

    public void handleServerCorrectSlot(int slotID) {
        if (slotID >= 0 && slotID <= Inventory.ITEMS_END) {
            serverIsCurrentlyProcessingThesePredictions.put(slotID, player.lastTransactionSent.get());
        }
    }

    @Override
    public void setItem(int item, ItemStack stack) {
        int finalTransaction = serverIsCurrentlyProcessingThesePredictions.getOrDefault(item, -1);
        if (finalTransaction == -1 || player.lastTransactionReceived.get() >= finalTransaction) {
            pendingFinalizedSlot.put(item, GrimAPI.INSTANCE.getTickManager().currentTick + 5);
            serverIsCurrentlyProcessingThesePredictions.remove(item);
        }
        super.setItem(item, stack);

        if (item == player.inventory.inventory.getSelected() + Inventory.HOTBAR_OFFSET) {
            player.attackCooldown.updateHeldItem();
        }
    }

    private void checkThatBukkitIsSynced(int slot) {
        if (player.platformPlayer == null || !player.inventory.isPacketInventoryActive) {
            return;
        }

        int bukkitSlot = getBukkitSlot(slot);
        if (bukkitSlot == -1) {
            return;
        }

        ItemStack existing = getItem(slot);
        ItemStack serverSide = player.platformPlayer.getInventory().getStack(bukkitSlot, slot);
        if (existing.getType() != serverSide.getType() || existing.getAmount() != serverSide.getAmount()) {
            GrimAPI.INSTANCE.getScheduler().getEntityScheduler().execute(
                    player.platformPlayer,
                    GrimAPI.INSTANCE.getGrimPlugin(),
                    () -> player.platformPlayer.updateInventory(),
                    null,
                    0
            );
            setItem(slot, serverSide);
        }
    }

    public static int getBukkitSlot(int packetSlot) {
        if (packetSlot <= 4) {
            return -1;
        }
        if (packetSlot <= 8) {
            return (7 - packetSlot) + 36;
        }
        if (packetSlot <= 35) {
            return packetSlot;
        }
        if (packetSlot <= 44) {
            return packetSlot - 36;
        }
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9) && packetSlot == 45) {
            return 40;
        }
        return -1;
    }

    public void tickWithBukkit() {
        if (player.platformPlayer == null) {
            return;
        }

        // Creative inventory actions are server-authoritative. Do not let the
        // generic five-tick prediction reconciler issue updateInventory() there.
        if (player.platformPlayer.getGameMode() == GameMode.CREATIVE) {
            pendingFinalizedSlot.clear();
            serverIsCurrentlyProcessingThesePredictions.clear();
            return;
        }

        // Loop all slot changes the client has predicted and check that the server has accepted them.
        int tickID = GrimAPI.INSTANCE.getTickManager().currentTick;
        for (Iterator<Map.Entry<Integer, Integer>> iterator = pendingFinalizedSlot.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            if (entry.getValue() <= tickID) {
                checkThatBukkitIsSynced(entry.getKey());
                iterator.remove();
            }
        }

        if (player.inventory.needResend) {
            GrimAPI.INSTANCE.getScheduler().getEntityScheduler().execute(
                    player.platformPlayer,
                    GrimAPI.INSTANCE.getGrimPlugin(),
                    () -> {
                        if (!player.inventory.needResend) {
                            return;
                        }
                        if (SUPPORTED_INVENTORIES.contains(player.platformPlayer.getInventory().getOpenInventoryKey().toUpperCase(Locale.ROOT))) {
                            player.inventory.needResend = false;
                            player.platformPlayer.updateInventory();
                        }
                    },
                    null,
                    0
            );
        }

        if (tickID % 5 == 0) {
            int slotToCheck = (tickID / 5) % getSize();
            if (!pendingFinalizedSlot.containsKey(slotToCheck)
                    && !serverIsCurrentlyProcessingThesePredictions.containsKey(slotToCheck)) {
                checkThatBukkitIsSynced(slotToCheck);
            }
        }
    }
}
