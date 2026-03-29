package com.ethicalfinder;

import com.ethicalfinder.commands.EFCommand;
import com.ethicalfinder.listeners.InventoryListener;
import com.ethicalfinder.managers.HistoryManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class EthicalFinder extends JavaPlugin {

    private HistoryManager historyManager;

    @Override
    public void onEnable() {
        // Save default config if not present
        saveDefaultConfig();

        // Boot managers
        historyManager = new HistoryManager(this);
        historyManager.startWipeTask();

        // Register listener
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);

        // Register commands (/ef and /ethicalfinder both work)
        EFCommand cmd = new EFCommand(this);
        Objects.requireNonNull(getCommand("ef")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("ef")).setTabCompleter(cmd);
        Objects.requireNonNull(getCommand("antidupe")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("antidupe")).setTabCompleter(cmd);
        Objects.requireNonNull(getCommand("ad")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("ad")).setTabCompleter(cmd);
        Objects.requireNonNull(getCommand("ethicalfinder")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("ethicalfinder")).setTabCompleter(cmd);

        getLogger().info("EthicalFinder is now active — watching for dupes.");
    }

    @Override
    public void onDisable() {
        historyManager.stopWipeTask();
        getLogger().info("EthicalFinder disabled.");
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    /** Reloads config and restarts the wipe task with the new interval. */
    public void reloadPluginConfig() {
        reloadConfig();
        historyManager.stopWipeTask();
        historyManager.startWipeTask();
        getLogger().info("Config reloaded.");
    }
}
