package com.artillexstudios.axsellwands;

import com.artillexstudios.axapi.AxPlugin;
import com.artillexstudios.axapi.config.Config;
import com.artillexstudios.axapi.libs.boostedyaml.dvs.versioning.BasicVersioning;
import com.artillexstudios.axapi.libs.boostedyaml.settings.dumper.DumperSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.general.GeneralSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.loader.LoaderSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.updater.UpdaterSettings;
import com.artillexstudios.axapi.metrics.AxMetrics;
import com.artillexstudios.axapi.utils.MessageUtils;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axapi.utils.featureflags.FeatureFlags;
import com.artillexstudios.axsellwands.commands.CommandManager;
import com.artillexstudios.axsellwands.hooks.HookManager;
import com.artillexstudios.axsellwands.listeners.*;
import com.artillexstudios.axsellwands.sellwands.Sellwands;
import com.artillexstudios.axsellwands.utils.FileUtils;
import com.artillexstudios.axsellwands.utils.NumberUtils;
import com.artillexstudios.axsellwands.utils.TimeUtils;
import com.artillexstudios.axsellwands.utils.UpdateNotifier;
import com.github.retrooper.packetevents.PacketEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;

import java.io.File;

public final class AxSellwands extends AxPlugin {
    public static Config CONFIG;
    public static Config LANG;
    public static Config HOOKS;
    public static MessageUtils MESSAGEUTILS;
    private static AxPlugin instance;
    //    private static ThreadedQueue<Runnable> threadedQueue;
    private static AxMetrics metrics;
    private static volatile SellwandPacketListener packetListener;

//    public static ThreadedQueue<Runnable> getThreadedQueue() {
//        return threadedQueue;
//    }

    public static AxPlugin getInstance() {
        return instance;
    }

    /**
     * @return whether PacketEvents is hooked, and so whether resending an item shows a newer
     * countdown. Callers check this before touching {@link SellwandPacketListener}, which cannot
     * load without PacketEvents.
     */
    public static boolean hasDisplayRefresh() {
        return packetListener != null;
    }

    public void enable() {
        instance = this;

        new Metrics(this, 21332);

        CONFIG = new Config(new File(getDataFolder(), "config.yml"), getResource("config.yml"), GeneralSettings.builder().setUseDefaults(false).build(), LoaderSettings.builder().setAutoUpdate(true).build(), DumperSettings.DEFAULT, UpdaterSettings.builder().setKeepAll(true).setVersioning(new BasicVersioning("version")).build());
        LANG = new Config(new File(getDataFolder(), "lang.yml"), getResource("lang.yml"), GeneralSettings.builder().setUseDefaults(false).build(), LoaderSettings.builder().setAutoUpdate(true).build(), DumperSettings.DEFAULT, UpdaterSettings.builder().setKeepAll(true).setVersioning(new BasicVersioning("version")).build());
        HOOKS = new Config(new File(getDataFolder(), "hooks.yml"), getResource("hooks.yml"), GeneralSettings.builder().setUseDefaults(false).build(), LoaderSettings.builder().setAutoUpdate(true).build(), DumperSettings.DEFAULT, UpdaterSettings.builder().setKeepAll(true).setVersioning(new BasicVersioning("version")).build());

        MESSAGEUTILS = new MessageUtils(LANG.getBackingDocument(), "prefix", CONFIG.getBackingDocument());

//        threadedQueue = new ThreadedQueue<>("AxSellwands-Datastore-thread");

        HookManager.setupHooks();
        NumberUtils.reload();
        TimeUtils.reload();
        CommandManager.load();

        if (FileUtils.PLUGIN_DIRECTORY.resolve("sellwands/").toFile().mkdirs()) {
            FileUtils.copyFromResource("sellwands");
        }

        Sellwands.reload();
        getServer().getPluginManager().registerEvents(new SellwandUseListener(), this);
        getServer().getPluginManager().registerEvents(new CraftListener(), this);
        getServer().getPluginManager().registerEvents(new InventoryClickListener(), this);
        getServer().getPluginManager().registerEvents(new WandExpiryListener(), this);
        WandExpiryListener.init();

        registerDisplayRefresh();

        Bukkit.getConsoleSender().sendMessage(StringUtils.formatToString("&#FF5500[AxSellwands] Loaded plugin!"));

        metrics = new AxMetrics(this, 15);
        metrics.start();

        UpdateNotifier.init(CONFIG, LANG);
        if (CONFIG.getBoolean("update-notifier.enabled", true)) new UpdateNotifier();
    }

    public void disable() {
        SellwandPacketListener listener = packetListener;
        if (listener != null) {
            packetListener = null;
            if (PacketEvents.getAPI() != null) {
                listener.unregister();
            }
        }
        if (metrics != null) metrics.cancel();
    }

    /**
     * Hooks PacketEvents so expiring sellwands count down in whatever menu shows them. Without it
     * a wand still expires on time and still renders when it is given, used or stacked, but the
     * countdown on a wand sitting in a menu stands still. The alternative, rewriting the item
     * wherever it is found, is what used to break other plugins' menus, so there is no fallback.
     */
    private void registerDisplayRefresh() {
        if (!getServer().getPluginManager().isPluginEnabled("packetevents") || PacketEvents.getAPI() == null) {
            getLogger().warning("PacketEvents is not installed, so expiring sellwand lore will not count down while it is on screen.");
            return;
        }

        try {
            SellwandPacketListener listener = new SellwandPacketListener();
            listener.register();
            packetListener = listener;
        } catch (LinkageError | RuntimeException ex) {
            packetListener = null;
            getLogger().warning("Could not hook PacketEvents, expiring sellwand lore will not count down while it is on screen: " + ex.getMessage());
        }
    }

    public void updateFlags() {
        FeatureFlags.USE_LEGACY_HEX_FORMATTER.set(true);
        FeatureFlags.PACKET_ENTITY_TRACKER_ENABLED.set(true);
        FeatureFlags.HOLOGRAM_UPDATE_TICKS.set(20L);
        FeatureFlags.PACKET_ENTITY_TRACKER_THREADS.set(1);
    }
}