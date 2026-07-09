package com.artillexstudios.axsellwands.listeners;

import com.artillexstudios.axsellwands.sellwands.SellwandRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps the %time-left% lore of expiring sellwands current at the moments a player can read it.
 *
 * <p>The timed sweep in {@link WandExpiryListener} covers wands carried by online players. This
 * covers the two cases that sweep cannot: a wand sitting inside a container the player just
 * opened, and a wand the player just selected on their hotbar.
 */
public class WandLoreListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        if (!WandExpiryListener.updateLore()) return;

        for (ItemStack item : event.getInventory().getContents()) {
            SellwandRenderer.refreshExpiring(item);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemHeld(@NotNull PlayerItemHeldEvent event) {
        if (!WandExpiryListener.updateLore()) return;

        Player player = event.getPlayer();
        if (SellwandRenderer.refreshExpiring(player.getInventory().getItem(event.getNewSlot()))) {
            player.updateInventory();
        }
    }
}
