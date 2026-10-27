package com.artillexstudios.axsellwands.sellwands;

import com.artillexstudios.axapi.items.NBTWrapper;
import com.artillexstudios.axapi.items.WrappedItemStack;
import com.artillexstudios.axapi.items.component.DataComponents;
import com.artillexstudios.axapi.items.component.type.ItemLore;
import com.artillexstudios.axapi.utils.ItemBuilder;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axsellwands.utils.NumberUtils;
import com.artillexstudios.axsellwands.utils.TimeUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.artillexstudios.axsellwands.AxSellwands.LANG;

/**
 * Renders a sellwand's name and lore from its configured item section.
 *
 * <p>Writing an item's meta replaces its {@code custom_data} component, so every render has to
 * put the whole {@link SellwandState} back afterwards. Routing all renders through here keeps
 * callers from silently dropping a tag they did not know about.
 */
public final class SellwandRenderer {
    public static final long NO_EXPIRY = -1L;
    public static final String TIME_LEFT = "%time-left%";
    public static final String TIME_MARKER = "axsellwands:time-left";
    private static final int LORE_VERSION = 1;

    private SellwandRenderer() {}

    /**
     * Whether an item's name and lore were written by an older version of this renderer, and so
     * have to be rebuilt once for the current one to read them. Wands handed out before
     * {@value #TIME_LEFT} moved to the packet layer have a countdown baked into their lore that
     * nothing would ever advance again.
     */
    public static boolean outdated(NBTWrapper wrapper) {
        return wrapper.getIntOr("axsellwands-lore-version", 0) < LORE_VERSION;
    }

    /**
     * Reads a sellwand's stored state, or returns null when the item is not a sellwand.
     */
    @Nullable
    public static SellwandState read(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return read(new NBTWrapper(item));
    }

    /**
     * Reads a sellwand's stored state from tags already wrapped, or returns null when the tags
     * are not a sellwand's.
     */
    @Nullable
    public static SellwandState read(NBTWrapper wrapper) {
        String type = wrapper.getString("axsellwands-type");
        if (type == null) {
            return null;
        }

        return new SellwandState(type, wrapper.getUUID("axsellwands-uuid"), wrapper.getFloatOr("axsellwands-multiplier", 1f), wrapper.getLongOr("axsellwands-lastused", 0L), wrapper.getIntOr("axsellwands-uses", -1), wrapper.getIntOr("axsellwands-max-uses", -1), wrapper.getIntOr("axsellwands-sold-amount", 0), wrapper.getDoubleOr("axsellwands-sold-price", 0D), wrapper.getLongOr("axsellwands-expires-at", NO_EXPIRY));
    }

    /**
     * Rebuilds the item's name and lore from {@code state}, then stores that state back onto it.
     */
    public static void render(ItemStack item, Sellwand sellwand, SellwandState state) {
        item.setItemMeta(ItemBuilder.create(sellwand.getItemSection(), placeholders(sellwand, state)).get().getItemMeta());
        WrappedItemStack wrapped = WrappedItemStack.wrap(item);
        if (sellwand.hasTimedDisplay()) {
            markTimedLines(wrapped, sellwand);
        }
        write(new NBTWrapper(wrapped), state);
    }

    /**
     * Tags the lines that count down with an insertion marker, so
     * {@code SellwandPacketListener} can rebuild those lines alone on each outgoing packet. An
     * insertion carries no appearance and survives the item being copied, which is what lets a
     * wand sitting in another plugin's menu keep a live countdown without that plugin's item ever
     * being written to.
     */
    private static void markTimedLines(WrappedItemStack wrapped, Sellwand sellwand) {
        ItemLore lore = wrapped.get(DataComponents.lore());
        if (lore != null && !sellwand.getTimedLore().isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.lines());
            for (Sellwand.TimedLine timed : sellwand.getTimedLore()) {
                if (timed.index() >= lines.size()) {
                    continue;
                }
                lines.set(timed.index(), lines.get(timed.index()).insertion(TIME_MARKER));
            }
            wrapped.set(DataComponents.lore(), new ItemLore(lines));
        }

        Component name = sellwand.getTimedName() == null ? null : wrapped.get(DataComponents.customName());
        if (name != null) {
            wrapped.set(DataComponents.customName(), name.insertion(TIME_MARKER));
        }
    }

    /**
     * The replacement map applied to a sellwand's configured name and lore, and to the messages
     * that quote them.
     */
    public static Map<String, String> placeholders(Sellwand sellwand, SellwandState state) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%multiplier%", "" + state.multiplier());
        replacements.put("%uses%", "" + (state.uses() == -1 ? LANG.getString("unlimited", "∞") : state.uses()));
        replacements.put("%max-uses%", "" + (state.maxUses() == -1 ? LANG.getString("unlimited", "∞") : state.maxUses()));
        replacements.put("%sold-amount%", "" + state.soldAmount());
        replacements.put("%sold-price%", NumberUtils.formatNumber(state.soldPrice()));
        replacements.put("%max-time%", sellwand.getExpireMillis() == NO_EXPIRY ? TimeUtils.never() : TimeUtils.format(sellwand.getExpireMillis()));
        replacements.put(TIME_LEFT, timeLeft(state.expiresAt()));
        return replacements;
    }

    /**
     * Renders one configured line against {@code replacements} and re-marks it, so a line rebuilt
     * on its way to a client is indistinguishable from the one {@link #render} wrote, apart from
     * the time it reads.
     */
    public static Component renderTimedLine(String template, Map<String, String> replacements) {
        return StringUtils.format(template, replacements).insertion(TIME_MARKER);
    }

    /**
     * The text {@value #TIME_LEFT} resolves to for a wand expiring at {@code expiresAt}.
     */
    public static String timeLeft(long expiresAt) {
        return expiresAt == NO_EXPIRY ? TimeUtils.never() : TimeUtils.format(expiresAt - System.currentTimeMillis());
    }

    private static void write(NBTWrapper wrapper, SellwandState state) {
        wrapper.set("axsellwands-type", state.type());
        wrapper.set("axsellwands-multiplier", state.multiplier());
        wrapper.set("axsellwands-lastused", state.lastUsed());
        wrapper.set("axsellwands-uses", state.uses());
        wrapper.set("axsellwands-max-uses", state.maxUses());
        wrapper.set("axsellwands-sold-amount", state.soldAmount());
        wrapper.set("axsellwands-sold-price", state.soldPrice());
        wrapper.set("axsellwands-lore-version", LORE_VERSION);
        if (state.uuid() != null) {
            wrapper.set("axsellwands-uuid", state.uuid());
        }
        if (state.expiresAt() != NO_EXPIRY) {
            wrapper.set("axsellwands-expires-at", state.expiresAt());
        }
        wrapper.build();
    }
}
