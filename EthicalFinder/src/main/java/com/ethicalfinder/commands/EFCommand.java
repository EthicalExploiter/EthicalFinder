package com.ethicalfinder.commands;

import com.ethicalfinder.EthicalFinder;
import com.ethicalfinder.managers.HistoryManager;
import com.ethicalfinder.managers.HistoryManager.DupeEntry;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class EFCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX =
            ChatColor.DARK_RED + "[" + ChatColor.GOLD + "EthicalFinder" + ChatColor.DARK_RED + "] " + ChatColor.RESET;

    private static final String NO_PERM =
            PREFIX + ChatColor.RED + "You don't have permission to use this command.";

    private final EthicalFinder plugin;

    public EFCommand(EthicalFinder plugin) {
        this.plugin = plugin;
    }

    // ── Command dispatch ──────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "history" -> {
                if (!sender.hasPermission("ethicalfinder.history")) { sender.sendMessage(NO_PERM); return true; }
                int page = 1;
                if (args.length >= 2) {
                    try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
                }
                sendHistory(sender, page);
            }

            case "clearhistory", "clear" -> {
                if (!sender.hasPermission("ethicalfinder.clearhistory")) { sender.sendMessage(NO_PERM); return true; }
                int count = plugin.getHistoryManager().size();
                plugin.getHistoryManager().clearHistory();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Cleared " + count + " history entries.");
            }

            case "reload" -> {
                if (!sender.hasPermission("ethicalfinder.reload")) { sender.sendMessage(NO_PERM); return true; }
                plugin.reloadPluginConfig();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuration reloaded successfully.");
            }

            default -> sendHelp(sender, label);
        }

        return true;
    }


    private void sendHistory(CommandSender sender, int page) {
        HistoryManager hm   = plugin.getHistoryManager();
        List<DupeEntry> all = hm.getHistory();
        int pageSize        = plugin.getConfig().getInt("history-page-size", 8);

        if (all.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "No dupe events recorded yet.");
            return;
        }

        // Newest entries first
        List<DupeEntry> reversed = new ArrayList<>(all);
        java.util.Collections.reverse(reversed);

        int totalPages = (int) Math.ceil((double) reversed.size() / pageSize);
        page = Math.max(1, Math.min(page, totalPages));

        int start = (page - 1) * pageSize;
        int end   = Math.min(start + pageSize, reversed.size());

        // Header
        sender.sendMessage(ChatColor.DARK_RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage(PREFIX + ChatColor.GOLD + "Dupe History " +
                ChatColor.DARK_GRAY + "(Page " + page + "/" + totalPages + ")" +
                ChatColor.DARK_GRAY + "  Total: " + reversed.size());
        sender.sendMessage(ChatColor.DARK_RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Entries
        for (int i = start; i < end; i++) {
            DupeEntry e = reversed.get(i);
            sender.sendMessage(
                    ChatColor.DARK_GRAY + "[" + e.formattedTime() + "] " +
                    ChatColor.WHITE  + e.playerName +
                    ChatColor.YELLOW + " duped " +
                    ChatColor.AQUA   + e.itemName +
                    ChatColor.YELLOW + " in " +
                    ChatColor.GREEN  + e.guiName +
                    ChatColor.YELLOW + " x" +
                    ChatColor.RED    + e.dupeCount
            );
        }

        // Footer / navigation hint
        if (totalPages > 1) {
            sender.sendMessage(ChatColor.DARK_GRAY + "Use /" + "ef history <page> to navigate.");
        }
        sender.sendMessage(ChatColor.DARK_RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.DARK_RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage(PREFIX + ChatColor.GOLD + "Commands");
        sender.sendMessage(ChatColor.DARK_RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        cmd(sender, label, "history [page]",  "View dupe history");
        cmd(sender, label, "clearhistory",    "Clear all history entries");
        cmd(sender, label, "reload",          "Reload the config");
        sender.sendMessage(ChatColor.DARK_RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void cmd(CommandSender sender, String label, String sub, String desc) {
        sender.sendMessage(
                ChatColor.GOLD + "/" + label + " " + sub +
                ChatColor.DARK_GRAY + " — " +
                ChatColor.GRAY + desc
        );
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("history", "clearhistory", "reload")) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("history")) {
            // Suggest page numbers
            int total = plugin.getHistoryManager().size();
            int pageSize = plugin.getConfig().getInt("history-page-size", 8);
            int pages = Math.max(1, (int) Math.ceil((double) total / pageSize));
            for (int i = 1; i <= pages; i++) completions.add(String.valueOf(i));
        }
        return completions;
    }
}
