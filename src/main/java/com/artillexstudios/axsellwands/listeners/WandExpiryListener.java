package com.artillexstudios.axsellwands.listeners;

import com.artillexstudios.axapi.items.NBTWrapper;
import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axsellwands.utils.bucket.Bucket;
import com.artillexstudios.axsellwands.utils.bucket.BucketFactory;
import com.artillexstudios.axsellwands.utils.bucket.BucketPartition;
import com.artillexstudios.axsellwands.utils.bucket.PartitioningStrategies;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

import static com.artillexstudios.axsellwands.AxSellwands.CONFIG;
import static com.artillexstudios.axsellwands.AxSellwands.MESSAGEUTILS;

/**
 * Checks online players for expired sellwands. Instead of scanning every online
 * player's inventory on every tick, players are split into partitions and only
 * one partition is scanned per tick, cycling round-robin - spreading the cost
 * out over time instead of spiking it once per interval.
 */
public class WandExpiryListener implements Listener {
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
            if (player == null) return;
            Scheduler.get().run(player, task -> sweep(player), () -> {
            });
        });
    }

    public static void sweep(Player player) {
        boolean removedAny = false;

        for (ItemStack item : player.getInventory().getContents()) {
            if (removeIfExpired(item)) removedAny = true;
        }

        if (removeIfExpired(player.getInventory().getItemInOffHand())) removedAny = true;

        if (removedAny) {
            MESSAGEUTILS.sendLang(player, "sellwand-expired");
        }
    }

    private static boolean removeIfExpired(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        NBTWrapper wrapper = new NBTWrapper(item);
        if (!wrapper.contains("axsellwands-type")) return false;

        Long expiresAt = wrapper.getLong("axsellwands-expires-at");
        if (expiresAt == null || System.currentTimeMillis() < expiresAt) return false;

        item.setAmount(0);
        return true;
    }
}
