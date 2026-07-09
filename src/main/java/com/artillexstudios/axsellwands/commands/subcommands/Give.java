package com.artillexstudios.axsellwands.commands.subcommands;

import com.artillexstudios.axapi.utils.ContainerUtils;
import com.artillexstudios.axapi.utils.ItemBuilder;
import com.artillexstudios.axsellwands.sellwands.Sellwand;
import com.artillexstudios.axsellwands.sellwands.SellwandRenderer;
import com.artillexstudios.axsellwands.sellwands.SellwandState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.artillexstudios.axsellwands.AxSellwands.CONFIG;
import static com.artillexstudios.axsellwands.AxSellwands.MESSAGEUTILS;

public enum Give {
    INSTANCE;

    public void execute(CommandSender sender, Player player, @NotNull Sellwand sellwand, @Nullable Integer amount) {
        float multiplier = sellwand.getMultiplier();
        int uses = sellwand.getUses();
        long expiresAt = sellwand.getExpireMillis() == SellwandRenderer.NO_EXPIRY
                ? SellwandRenderer.NO_EXPIRY
                : System.currentTimeMillis() + sellwand.getExpireMillis();

        SellwandState state = new SellwandState(sellwand.getId(), null, multiplier, 0L, uses, uses, 0, 0D, expiresAt);
        Map<String, String> replacements = SellwandRenderer.placeholders(sellwand, state);

        ItemStack it = ItemBuilder.create(sellwand.getItemSection(), replacements).get();

        int am = 1;
        if (amount != null) am = amount;

        for (int i = 0; i < am; i++) {
            UUID uuid = CONFIG.getInt("stacking-mode", 0) != 2 ? UUID.randomUUID() : null;
            ItemStack copy = it.clone();
            SellwandRenderer.render(copy, sellwand, new SellwandState(sellwand.getId(), uuid, multiplier, 0L, uses, uses, 0, 0D, expiresAt));
            ContainerUtils.INSTANCE.addOrDrop(player.getInventory(), List.of(copy), player.getLocation());
        }

        replacements.put("%amount%", "" + am);
        replacements.put("%sellwand%", sellwand.getName());
        replacements.put("%player%", player.getName());

        MESSAGEUTILS.sendLang(sender, "sellwand-give", replacements);
        MESSAGEUTILS.sendLang(player, "sellwand-got", replacements);
    }
}
