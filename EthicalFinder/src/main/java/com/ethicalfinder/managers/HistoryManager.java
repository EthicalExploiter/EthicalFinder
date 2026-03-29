package com.ethicalfinder.managers;

import com.ethicalfinder.EthicalFinder;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryManager {

    // ── Data class ────────────────────────────────────────────────────────────

    public static class DupeEntry {
        public final String playerName;
        public final String itemName;
        public final String guiName;
        public final int    dupeCount;
        public final long   timestamp; // epoch millis

        public DupeEntry(String playerName, String itemName, String guiName, int dupeCount) {
            this.playerName = playerName;
            this.itemName   = itemName;
            this.guiName    = guiName;
            this.dupeCount  = dupeCount;
            this.timestamp  = System.currentTimeMillis();
        }

        private static final DateTimeFormatter FMT = DateTimeFormatter
                .ofPattern("MM/dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());

        public String formattedTime() {
            return FMT.format(Instant.ofEpochMilli(timestamp));
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final EthicalFinder plugin;
    private final List<DupeEntry> history = Collections.synchronizedList(new ArrayList<>());
    private BukkitTask wipeTask;

    public HistoryManager(EthicalFinder plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void addEntry(String playerName, String itemName, String guiName, int dupeCount) {
        history.add(new DupeEntry(playerName, itemName, guiName, dupeCount));
    }

    public List<DupeEntry> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void clearHistory() {
        history.clear();
    }

    public int size() {
        return history.size();
    }

    // ── Scheduling ────────────────────────────────────────────────────────────

    /** Starts (or restarts) the auto-wipe task based on current config. */
    public void startWipeTask() {
        stopWipeTask();

        int hours = plugin.getConfig().getInt("history-wipe-hours", 24);
        if (hours <= 0) return; // disabled

        long ticks = hours * 72000L; // 20 ticks/s * 3600 s/hr
        wipeTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int count = history.size();
            history.clear();
            plugin.getLogger().info("Auto-wiped " + count + " history entries after " + hours + "h.");
        }, ticks, ticks);
    }

    public void stopWipeTask() {
        if (wipeTask != null && !wipeTask.isCancelled()) {
            wipeTask.cancel();
            wipeTask = null;
        }
    }
}
