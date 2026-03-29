package com.ethicalfinder.listeners;

import com.ethicalfinder.EthicalFinder;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InventoryListener implements Listener {

    private final EthicalFinder plugin;

    // Snapshot of player's own inventory at the moment they opened a GUI
    private final Map<UUID, Map<Material, Integer>> playerSnapshots = new HashMap<>();

    // Snapshot of the container/GUI the player opened
    private final Map<UUID, Map<Material, Integer>> containerSnapshots = new HashMap<>();

    // Human-readable name of the GUI that was opened
    private final Map<UUID, String> openedGuiName = new HashMap<>();

    public InventoryListener(EthicalFinder plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // On Open — gate-check player, then snapshot both inventories
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // Skip player's own crafting / creative screen
        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) return;

        // ── Config-driven ignores ─────────────────────────────────────────────
        if (shouldIgnorePlayer(player)) return;

        // Resolve a readable GUI name (strip colour codes)
        String title   = event.getView().getTitle();
        String guiName = (title != null && !title.isBlank())
                ? ChatColor.stripColor(title)
                : formatEnum(type.name());

        // ── Excluded GUIs ─────────────────────────────────────────────────────
        if (isExcludedGui(guiName)) return;

        UUID uuid = player.getUniqueId();
        playerSnapshots.put(uuid, countItems(player.getInventory().getContents()));
        containerSnapshots.put(uuid, countItems(event.getInventory().getContents()));
        openedGuiName.put(uuid, guiName);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // On Close — compare against snapshot; flag any unexplained item gains
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        if (!playerSnapshots.containsKey(uuid)) return;

        Map<Material, Integer> originalPlayer    = playerSnapshots.remove(uuid);
        Map<Material, Integer> originalContainer = containerSnapshots.remove(uuid);
        String guiName                           = openedGuiName.remove(uuid);

        // Re-check ignore conditions at close time (e.g. switched to creative mid-session)
        if (shouldIgnorePlayer(player)) return;

        Map<Material, Integer> currentPlayer = countItems(player.getInventory().getContents());

        // Max legitimate = what they had + what was already in the GUI
        for (Map.Entry<Material, Integer> entry : currentPlayer.entrySet()) {
            Material mat      = entry.getKey();
            int current       = entry.getValue();
            int hadBefore     = originalPlayer.getOrDefault(mat, 0);
            int wasInGui      = originalContainer.getOrDefault(mat, 0);
            int maxLegitimate = hadBefore + wasInGui;

            if (current > maxLegitimate) {
                int dupeCount = current - maxLegitimate;
                flagDupe(player, mat, guiName, dupeCount);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Config helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns true if this player should be silently skipped based on config. */
    private boolean shouldIgnorePlayer(Player player) {
        if (plugin.getConfig().getBoolean("ignore-ops", true) && player.isOp()) return true;
        if (plugin.getConfig().getBoolean("ignore-creative", true)
                && player.getGameMode() == GameMode.CREATIVE) return true;
        return false;
    }

    /**
     * Returns true if the GUI title matches any entry in the excluded-guis list.
     * Comparison is case-insensitive and uses partial (contains) matching so that
     * something like "My Chest" still matches if "chest" is in the list.
     */
    private boolean isExcludedGui(String guiName) {
        List<String> excluded = plugin.getConfig().getStringList("excluded-guis");
        String lower = guiName.toLowerCase();
        for (String ex : excluded) {
            if (lower.contains(ex.toLowerCase())) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Detection output
    // ─────────────────────────────────────────────────────────────────────────

    private void flagDupe(Player player, Material material, String guiName, int dupeCount) {
        String itemName = formatEnum(material.name());

        // Plain log for console
        String plainAlert = String.format(
                "[EthicalFinder] DUPE ALERT: %s got duplicate of %s while in \"%s\" x%d",
                player.getName(), itemName, guiName, dupeCount
        );

        // Coloured chat alert
        String chatAlert = ChatColor.DARK_RED + "["
                + ChatColor.GOLD   + "Ethical Finder"
                + ChatColor.DARK_RED + "] "
                + ChatColor.YELLOW + "-> "
                + ChatColor.WHITE  + player.getName()
                + ChatColor.YELLOW + " got duplicate of "
                + ChatColor.AQUA   + itemName
                + ChatColor.YELLOW + " while in "
                + ChatColor.GREEN  + guiName
                + ChatColor.YELLOW + " x"
                + ChatColor.RED    + dupeCount;

        plugin.getLogger().warning(plainAlert);

        // Save to history
        plugin.getHistoryManager().addEntry(player.getName(), itemName, guiName, dupeCount);

        // Broadcast to ops and permission holders
        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.isOp() || p.hasPermission("ethicalfinder.alerts"))
                .forEach(p -> p.sendMessage(chatAlert));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private Map<Material, Integer> countItems(ItemStack[] contents) {
        Map<Material, Integer> counts = new HashMap<>();
        if (contents == null) return counts;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() != Material.AIR) {
                counts.merge(stack.getType(), stack.getAmount(), Integer::sum);
            }
        }
        return counts;
    }

    /** Converts DIAMOND_SWORD → "Diamond Sword" */
    private String formatEnum(String raw) {
        String[] words = raw.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
