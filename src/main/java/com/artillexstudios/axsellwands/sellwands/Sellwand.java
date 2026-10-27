package com.artillexstudios.axsellwands.sellwands;

import com.artillexstudios.axapi.config.Config;
import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section;
import com.artillexstudios.axapi.utils.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class Sellwand {
    private final String id;
    private final Config file;
    private final String name;
    private final float multiplier;
    private final int uses;
    private final long cooldown;
    private final long expireMillis;
    private final Section itemSection;
    private final List<TimedLine> timedLore;
    private final String timedName;
    private final HashSet<Material> disallowed = new HashSet<>();
    private final HashSet<Material> allowed = new HashSet<>();

    /**
     * A configured lore line that counts down, paired with the slot it renders into.
     */
    public record TimedLine(int index, String template) {}

    public Sellwand(String id, @NotNull Config file) {
        this.id = id;
        this.file = file;

        long configMillis = file.getLong("cooldown-miliseconds", -1);
        if (configMillis != -1) {
            file.getBackingDocument().remove("cooldown-miliseconds");
            file.set("cooldown-milliseconds", configMillis);
            file.save();
        }

        this.name = file.getString("name", "Sellwand");
        this.multiplier = file.getFloat("multiplier", 1f);
        this.uses = file.getInt("uses", -1);
        this.cooldown = file.getLong("cooldown-milliseconds", 0);

        long expireSeconds = file.getLong("expire-seconds", -1);
        this.expireMillis = expireSeconds == -1 ? -1 : expireSeconds * 1000L;

        this.itemSection = file.getSection("item");
        this.timedLore = timedLore(itemSection);
        this.timedName = timedName(itemSection);

        for (String str : file.getStringList("disallowed-containers")) {
            final Material material = Material.getMaterial(str);
            if (material == null) {
                Bukkit.getConsoleSender().sendMessage(StringUtils.formatToString("&#FF5500[AxSellwands] Material " + str + " does not exists, skipping!"));
                continue;
            }
            disallowed.add(material);
        }

        for (String str : file.getStringList("allowed-containers")) {
            final Material material = Material.getMaterial(str);
            if (material == null) {
                Bukkit.getConsoleSender().sendMessage(StringUtils.formatToString("&#FF5500[AxSellwands] Material " + str + " does not exists, skipping!"));
                continue;
            }
            allowed.add(material);
        }
    }

    public String getId() {
        return id;
    }

    public Config getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public float getMultiplier() {
        return multiplier;
    }

    public int getUses() {
        return uses;
    }

    public long getCooldown() {
        return cooldown;
    }

    public long getExpireMillis() {
        return expireMillis;
    }

    public Section getItemSection() {
        return itemSection;
    }

    /**
     * The configured lore lines carrying {@value SellwandRenderer#TIME_LEFT}, in the order they
     * render. Read on every item packet, so it is resolved once here rather than walked per call.
     */
    public List<TimedLine> getTimedLore() {
        return timedLore;
    }

    /**
     * The configured name when it carries {@value SellwandRenderer#TIME_LEFT}, otherwise null.
     */
    @Nullable
    public String getTimedName() {
        return timedName;
    }

    /**
     * @return whether the name or any lore line carries {@value SellwandRenderer#TIME_LEFT}
     */
    public boolean hasTimedDisplay() {
        return timedName != null || !timedLore.isEmpty();
    }

    private static List<TimedLine> timedLore(Section section) {
        if (section == null) {
            return List.of();
        }

        List<String> lines = section.getStringList("lore");
        List<TimedLine> timed = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(SellwandRenderer.TIME_LEFT)) {
                timed.add(new TimedLine(i, lines.get(i)));
            }
        }
        return List.copyOf(timed);
    }

    @Nullable
    private static String timedName(Section section) {
        if (section == null) {
            return null;
        }

        String name = section.getString("name");
        return name != null && name.contains(SellwandRenderer.TIME_LEFT) ? name : null;
    }

    public HashSet<Material> getDisallowed() {
        return disallowed;
    }

    public HashSet<Material> getAllowed() {
        return allowed;
    }
}
