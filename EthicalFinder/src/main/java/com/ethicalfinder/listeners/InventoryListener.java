package com.ethicalfinder.listeners;

import com.ethicalfinder.EthicalFinder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
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

    // -------------------------------------------------------------------------
    // On Open — take a snapshot of both inventories before anything changes
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // The player's own crafting/survival inventory doesn't count as a "storage" GUI
        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) return;

        UUID uuid = player.getUniqueId();

        // Snapshot the player's current held items (all 36 slots + off-hand)
        playerSnapshots.put(uuid, countItems(player.getInventory().getContents()));

        // Snapshot the opened container (chest, furnace, hopper, custom GUI, etc.)
        containerSnapshots.put(uuid, countItems(event.getInventory().getContents()));

        // Resolve a readable GUI name: prefer the view title, fall back to inventory type
        String title = event.getView().getTitle();
        String guiName = (title != null && !title.isBlank())
                ? ChatColor.stripColor(title)
                : formatEnum(type.name());
        openedGuiName.put(uuid, guiName);
    }

    // -------------------------------------------------------------------------
    // On Close — compare current state against snapshot to spot item gains
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        // Only proceed if we actually snapshotted this session
        if (!playerSnapshots.containsKey(uuid)) return;

        Map<Material, Integer> originalPlayer    = playerSnapshots.remove(uuid);
        Map<Material, Integer> originalContainer = containerSnapshots.remove(uuid);
        String guiName                           = openedGuiName.remove(uuid);

        // What the player is holding right now
        Map<Material, Integer> currentPlayer = countItems(player.getInventory().getContents());

        // --- Detection logic ---
        // The maximum legitimate amount a player can hold of any item after closing
        // a GUI is:  what they had before  +  what was in the container.
        // Anything above that is an unexplained gain (potential dupe).
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Counts every non-air item in an array of stacks, grouped by Material.
     */
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

    /**
     * Sends a formatted dupe alert to console and all players with the alert permission.
     *
     * Format: "Ethical Finder" -> <User> got duplicate of <item> while in <gui> x<count>
     */
    private void flagDupe(Player player, Material material, String guiName, int dupeCount) {
        String itemName = formatEnum(material.name());

        // Console-friendly plain text
        String plainAlert = String.format(
                "[EthicalFinder] DUPE: %s got duplicate of %s while in \"%s\" x%d",
                player.getName(), itemName, guiName, dupeCount
        );

        // Chat-formatted message matching requested style
        String chatAlert = ChatColor.DARK_RED + "["
                + ChatColor.GOLD + "Ethical Finder"
                + ChatColor.DARK_RED + "] "
                + ChatColor.YELLOW + "-> "
                + ChatColor.WHITE  + player.getName()
                + ChatColor.YELLOW + " got duplicate of "
                + ChatColor.AQUA   + itemName
                + ChatColor.YELLOW + " while in "
                + ChatColor.GREEN  + guiName
                + ChatColor.YELLOW + " x"
                + ChatColor.RED    + dupeCount;

        // Log to console
        plugin.getLogger().warning(plainAlert);

        // Alert ops and anyone with the permission node
        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.isOp() || p.hasPermission("ethicalfinder.alerts"))
                .forEach(p -> p.sendMessage(chatAlert));
    }

    /**
     * Converts an enum-style name like DIAMOND_SWORD into "Diamond Sword".
     */
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
