package com.artillexstudios.axsellwands.sellwands;

import com.artillexstudios.axapi.config.Config;
import com.artillexstudios.axsellwands.AxSellwands;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Sellwands {
    private static final Map<String, Sellwand> sellwands = new ConcurrentHashMap<>();
    private static volatile boolean timedDisplay;

    public static void reload() {
        sellwands.clear();

        final File path = new File(AxSellwands.getInstance().getDataFolder(), "sellwands");
        if (path.exists()) {
            for (File file : path.listFiles()) {
                if (!file.getName().endsWith(".yml") && !file.getName().endsWith(".yaml")) continue;
                final String id = file.getName().replace(".yml", "").replace("%yaml%", "");

                final Config cfg = new Config(file);
                final Sellwand sellwand = new Sellwand(id, cfg);

                sellwands.put(id, sellwand);
            }
        }

        timedDisplay = sellwands.values().stream().anyMatch(Sellwand::hasTimedDisplay);
    }

    /**
     * @return whether any loaded sellwand counts down in its name or lore, so packets that could
     * only show sellwands without one are passed through without being decoded
     */
    public static boolean hasTimedDisplay() {
        return timedDisplay;
    }

    /**
     * The loaded sellwands, read from packet threads as well as server threads, so the map is
     * concurrent. A reader during a reload sees either definition, never a broken map.
     */
    public static Map<String, Sellwand> getSellwands() {
        return sellwands;
    }
}
