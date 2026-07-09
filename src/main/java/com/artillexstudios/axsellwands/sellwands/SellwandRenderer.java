package com.artillexstudios.axsellwands.sellwands;

import com.artillexstudios.axapi.items.NBTWrapper;
import com.artillexstudios.axapi.utils.ItemBuilder;
import com.artillexstudios.axsellwands.utils.NumberUtils;
import com.artillexstudios.axsellwands.utils.TimeUtils;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
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

    private SellwandRenderer() {
    }

    /**
     * Reads a sellwand's stored state, or returns null when the item is not a sellwand.
     */
    @Nullable
    public static SellwandState read(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return null;

        NBTWrapper wrapper = new NBTWrapper(item);
        String type = wrapper.getString("axsellwands-type");
        if (type == null) return null;

        return new SellwandState(
                type,
                wrapper.getUUID("axsellwands-uuid"),
                wrapper.getFloatOr("axsellwands-multiplier", 1f),
                wrapper.getLongOr("axsellwands-lastused", 0L),
                wrapper.getIntOr("axsellwands-uses", -1),
                wrapper.getIntOr("axsellwands-max-uses", -1),
                wrapper.getIntOr("axsellwands-sold-amount", 0),
                wrapper.getDoubleOr("axsellwands-sold-price", 0D),
                wrapper.getLongOr("axsellwands-expires-at", NO_EXPIRY)
        );
    }

    /**
     * Rebuilds the item's name and lore from {@code state}, then stores that state back onto it.
     */
    public static void render(ItemStack item, Sellwand sellwand, SellwandState state) {
        item.setItemMeta(ItemBuilder.create(sellwand.getItemSection(), placeholders(sellwand, state)).get().getItemMeta());
        write(item, state);
    }

    /**
     * Re-renders a wand that counts down, so its %time-left% lore reflects the current time.
     * Wands without an expiry are skipped, as their lore does not change on its own.
     * Returns true when the item was re-rendered.
     */
    public static boolean refreshExpiring(@Nullable ItemStack item) {
        SellwandState state = read(item);
        if (state == null || state.expiresAt() == NO_EXPIRY) return false;

        Sellwand sellwand = Sellwands.getSellwands().get(state.type());
        if (sellwand == null) return false;

        render(item, sellwand, state);
        return true;
    }

    /**
     * The replacement map applied to a sellwand's configured name and lore.
     */
    public static Map<String, String> placeholders(Sellwand sellwand, SellwandState state) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%multiplier%", "" + state.multiplier());
        replacements.put("%uses%", "" + (state.uses() == -1 ? LANG.getString("unlimited", "∞") : state.uses()));
        replacements.put("%max-uses%", "" + (state.maxUses() == -1 ? LANG.getString("unlimited", "∞") : state.maxUses()));
        replacements.put("%sold-amount%", "" + state.soldAmount());
        replacements.put("%sold-price%", NumberUtils.formatNumber(state.soldPrice()));
        replacements.put("%max-time%", sellwand.getExpireMillis() == NO_EXPIRY
                ? TimeUtils.never()
                : TimeUtils.format(sellwand.getExpireMillis()));
        replacements.put("%time-left%", state.expiresAt() == NO_EXPIRY
                ? TimeUtils.never()
                : TimeUtils.format(state.expiresAt() - System.currentTimeMillis()));
        return replacements;
    }

    private static void write(ItemStack item, SellwandState state) {
        NBTWrapper wrapper = new NBTWrapper(item);
        wrapper.set("axsellwands-type", state.type());
        wrapper.set("axsellwands-multiplier", state.multiplier());
        wrapper.set("axsellwands-lastused", state.lastUsed());
        wrapper.set("axsellwands-uses", state.uses());
        wrapper.set("axsellwands-max-uses", state.maxUses());
        wrapper.set("axsellwands-sold-amount", state.soldAmount());
        wrapper.set("axsellwands-sold-price", state.soldPrice());
        if (state.uuid() != null) wrapper.set("axsellwands-uuid", state.uuid());
        if (state.expiresAt() != NO_EXPIRY) wrapper.set("axsellwands-expires-at", state.expiresAt());
        wrapper.build();
    }
}
