package com.ethicalfinder;

import com.ethicalfinder.listeners.InventoryListener;
import org.bukkit.plugin.java.JavaPlugin;

public class EthicalFinder extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("EthicalFinder is now active — watching for dupes.");
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("EthicalFinder disabled.");
    }
}
