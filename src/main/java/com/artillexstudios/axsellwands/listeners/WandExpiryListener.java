package com.artillexstudios.axsellwands.listeners;

import com.artillexstudios.axapi.items.NBTWrapper;
import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axsellwands.AxSellwands;
import com.artillexstudios.axsellwands.sellwands.Sellwand;
import com.artillexstudios.axsellwands.sellwands.SellwandRenderer;
import com.artillexstudios.axsellwands.sellwands.SellwandState;
import com.artillexstudios.axsellwands.sellwands.Sellwands;
import com.artillexstudios.axsellwands.utils.bucket.Bucket;
import com.artillexstudios.axsellwands.utils.bucket.BucketFactory;
import com.artillexstudios.axsellwands.utils.bucket.BucketPartition;
import com.artillexstudios.axsellwands.utils.bucket.PartitioningStrategies;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.artillexstudios.axsellwands.AxSellwands.CONFIG;
import static com.artillexstudios.axsellwands.AxSellwands.MESSAGEUTILS;

/**
 * Removes expired sellwands from online players, and resends the inventory of players holding one
 * that is still ticking down so {@link SellwandPacketListener} renders a newer countdown. Instead
 * of scanning every online player's inventory on every tick, players are split into partitions and
 * only one partition is scanned per tick, cycling round-robin, spreading the cost out over time
 * instead of spiking it once per interval.
 *
 * <p>A countdown a player is already looking at therefore advances once per
 * {@code partitions * interval-seconds}. Every other view of it is current, as opening a menu or
 * moving the item sends the item anew.
 */
public class WandExpiryListener implements Listener {
    private static final int STORAGE_SLOTS = 36;
    private static Bucket<UUID> tickBuckets;

    public static void init() {
        int partitions = Math.max(1, CONFIG.getInt("wand-expiry.partitions", 20));
        tickBuckets = BucketFactory.newConcurrentBucket(partitions, PartitioningStrategies.lowestSize());

        for (Player player : Bukkit.getOnlinePlayers()) {
            tickBuckets.add(player.getUniqueId());
        }

        long period = Math.max(1, CONFIG.getInt("wand-expiry.interval-seconds", 5)) * 20L;
        Scheduler.get().runTimer(task -> tick(), period, period);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        tickBuckets.add(player.getUniqueId());
        sweep(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tickBuckets.remove(event.getPlayer().getUniqueId());
    }

    private static void tick() {
        BucketPartition<UUID> partition = tickBuckets.asCycle().next();
        partition.each(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                return;
            }
            Scheduler.get().run(player, task -> sweep(player), () -> {
            });
        });
    }

    /**
     * Whether the countdown of an expiring sellwand should keep ticking in menus the player is
     * already looking at. Read per call so a config reload takes effect without a restart.
     */
    public static boolean updateLore() {
        return CONFIG.getBoolean("wand-expiry.update-lore", true);
    }

    /**
     * Removes the player's expired wands, rebuilds outdated ones, and refreshes the countdowns
     * they can see. {@code getContents()} already covers the armor and off-hand slots.
     *
     * <p>With a menu open the whole view is resent. Without one only the wand slots are, leaving
     * out the main hand and off-hand: the client replays the equip animation whenever a held
     * stack's lore changes, so a held wand would bob on every refresh. A wand picks up the newer
     * countdown from the last refresh it sat out of hand for.
     */
    public static void sweep(Player player) {
        long now = System.currentTimeMillis();
        boolean removedAny = false;
        boolean rebuilt = false;
        boolean expiring = false;
        List<Integer> outOfHand = new ArrayList<>();

        PlayerInventory inventory = player.getInventory();
        int held = inventory.getHeldItemSlot();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }

            NBTWrapper wrapper = new NBTWrapper(item);
            if (!wrapper.contains("axsellwands-type")) {
                continue;
            }

            Long expiresAt = wrapper.getLong("axsellwands-expires-at");
            if (expiresAt != null && now >= expiresAt) {
                item.setAmount(0);
                removedAny = true;
                continue;
            }

            if (expiresAt != null) {
                expiring = true;
                if (slot < STORAGE_SLOTS && slot != held) {
                    outOfHand.add(slot);
                }
            }
            if (rebuildIfOutdated(item, wrapper)) {
                rebuilt = true;
            }
        }

        if (removedAny) {
            MESSAGEUTILS.sendLang(player, "sellwand-expired");
        }

        if (removedAny || rebuilt) {
            player.updateInventory();
            return;
        }

        if (!updateLore() || !AxSellwands.hasDisplayRefresh()) {
            return;
        }

        InventoryType view = player.getOpenInventory().getType();
        if (view != InventoryType.CRAFTING && view != InventoryType.CREATIVE) {
            if (expiring || menuShowsExpiring(player)) {
                player.updateInventory();
            }
            return;
        }

        if (!outOfHand.isEmpty() && !SellwandPacketListener.resend(player, outOfHand)) {
            player.updateInventory();
        }
    }

    /**
     * Rebuilds a wand handed out by an older version, whose lore holds a countdown frozen at the
     * moment it was last written. Only the player's own items are rebuilt, once each, as writing an
     * item's meta drops anything another plugin put on it.
     */
    private static boolean rebuildIfOutdated(ItemStack item, NBTWrapper wrapper) {
        if (!SellwandRenderer.outdated(wrapper)) {
            return false;
        }

        SellwandState state = SellwandRenderer.read(wrapper);
        if (state == null) {
            return false;
        }

        Sellwand sellwand = Sellwands.getSellwands().get(state.type());
        if (sellwand == null) {
            return false;
        }

        SellwandRenderer.render(item, sellwand, state);
        return true;
    }

    /**
     * Whether the menu the player has open holds a wand that expires. The menu may belong to
     * another plugin, so it is only read, never written.
     */
    private static boolean menuShowsExpiring(Player player) {
        for (ItemStack item : player.getOpenInventory().getTopInventory().getContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            NBTWrapper wrapper = new NBTWrapper(item);
            if (wrapper.contains("axsellwands-type") && wrapper.getLong("axsellwands-expires-at") != null) {
                return true;
            }
        }

        return false;
    }
}
