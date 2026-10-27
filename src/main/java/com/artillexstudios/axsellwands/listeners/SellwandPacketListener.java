package com.artillexstudios.axsellwands.listeners;

import com.artillexstudios.axsellwands.sellwands.Sellwand;
import com.artillexstudios.axsellwands.sellwands.SellwandRenderer;
import com.artillexstudios.axsellwands.sellwands.SellwandState;
import com.artillexstudios.axsellwands.sellwands.Sellwands;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTNumber;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SellwandPacketListener extends PacketListenerAbstract {

    public SellwandPacketListener() {
        super(PacketListenerPriority.NORMAL);
    }

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }

    /**
     * Resends single slots of the player's own inventory, each passing back through this listener
     * with a newer countdown. Unlike resending the whole inventory, slots left out keep the stack
     * the client already has, which is what keeps a held wand from replaying its equip animation.
     *
     * @param slots inventory indices, hotbar first, as {@link PlayerInventory#getItem(int)} takes them
     * @return false on servers older than 1.21.2, which have no per-slot inventory packet, so the
     * caller has to resend the whole inventory instead
     */
    public static boolean resend(Player player, List<Integer> slots) {
        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21_2)) {
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        for (int slot : slots) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerSetPlayerInventory(slot, SpigotConversionUtil.fromBukkitItemStack(inventory.getItem(slot))));
        }
        return true;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled() || !Sellwands.hasTimedDisplay()) {
            return;
        }

        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Server.SET_SLOT) {
            if (refresh(new WrapperPlayServerSetSlot(event).getItem())) {
                event.markForReEncode(true);
            }
        } else if (type == PacketType.Play.Server.WINDOW_ITEMS) {
            if (refreshWindow(new WrapperPlayServerWindowItems(event))) {
                event.markForReEncode(true);
            }
        } else if (type == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            if (refresh(new WrapperPlayServerSetPlayerInventory(event).getStack())) {
                event.markForReEncode(true);
            }
        } else if (type == PacketType.Play.Server.SET_CURSOR_ITEM) {
            if (refresh(new WrapperPlayServerSetCursorItem(event).getStack())) {
                event.markForReEncode(true);
            }
        }
    }

    private static boolean refreshWindow(WrapperPlayServerWindowItems wrapper) {
        boolean modified = false;

        List<ItemStack> items = wrapper.getItems();
        if (items != null) {
            for (ItemStack item : items) {
                if (refresh(item)) {
                    modified = true;
                }
            }
        }

        Optional<ItemStack> carried = wrapper.getCarriedItem();
        if (carried.isPresent() && refresh(carried.get())) {
            modified = true;
        }

        return modified;
    }

    private static boolean refresh(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }

        NBTCompound data = item.getComponentOr(ComponentTypes.CUSTOM_DATA, null);
        if (data == null) {
            return false;
        }

        NBTString type = data.getStringTagOrNull("axsellwands-type");
        NBTNumber expiresAt = data.getNumberTagOrNull("axsellwands-expires-at");
        if (type == null || expiresAt == null || expiresAt.getAsLong() == SellwandRenderer.NO_EXPIRY) {
            return false;
        }

        Sellwand sellwand = Sellwands.getSellwands().get(type.getValue());
        if (sellwand == null || !sellwand.hasTimedDisplay()) {
            return false;
        }

        Map<String, String> replacements = SellwandRenderer.placeholders(sellwand, state(data, type.getValue(), expiresAt.getAsLong()));
        List<Component> lore = renderedLore(item, sellwand, replacements);
        Component name = renderedName(item, sellwand, replacements);
        if (lore == null && name == null) {
            return false;
        }

        if (lore != null) {
            item.setComponent(ComponentTypes.LORE, new ItemLore(lore));
        }
        if (name != null) {
            item.setComponent(ComponentTypes.CUSTOM_NAME, name);
        }
        return true;
    }

    /**
     * Reads the state the placeholders need from the packet's own tags, the same defaults
     * {@link SellwandRenderer#read} applies. Converting the stack to a Bukkit item to read it
     * would encode and decode the whole item per packet. The owner and last use are left out, as
     * no placeholder shows them.
     */
    private static SellwandState state(NBTCompound data, String type, long expiresAt) {
        return new SellwandState(type, null, data.getNumberTagValueOrDefault("axsellwands-multiplier", 1f).floatValue(), 0L, data.getNumberTagValueOrDefault("axsellwands-uses", -1).intValue(), data.getNumberTagValueOrDefault("axsellwands-max-uses", -1).intValue(), data.getNumberTagValueOrDefault("axsellwands-sold-amount", 0).intValue(), data.getNumberTagValueOrDefault("axsellwands-sold-price", 0D).doubleValue(), expiresAt);
    }

    /**
     * The item's lore with each marked line rebuilt against the current clock, or null when it
     * carries no line this plugin marked.
     */
    @Nullable
    private static List<Component> renderedLore(ItemStack item, Sellwand sellwand, Map<String, String> replacements) {
        List<Sellwand.TimedLine> timed = sellwand.getTimedLore();
        if (timed.isEmpty()) {
            return null;
        }

        List<Component> lines = new ArrayList<>(item.getComponentOr(ComponentTypes.LORE, ItemLore.EMPTY).getLines());
        int done = 0;
        for (int i = 0; i < lines.size() && done < timed.size(); i++) {
            if (!SellwandRenderer.TIME_MARKER.equals(lines.get(i).insertion())) {
                continue;
            }
            lines.set(i, SellwandRenderer.renderTimedLine(timed.get(done++).template(), replacements));
        }

        return done == 0 ? null : lines;
    }

    @Nullable
    private static Component renderedName(ItemStack item, Sellwand sellwand, Map<String, String> replacements) {
        String template = sellwand.getTimedName();
        if (template == null) {
            return null;
        }

        Component name = item.getComponentOr(ComponentTypes.CUSTOM_NAME, null);
        if (name == null || !SellwandRenderer.TIME_MARKER.equals(name.insertion())) {
            return null;
        }

        return SellwandRenderer.renderTimedLine(template, replacements);
    }
}
