package me.zivush.customfishingz;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Commands implements CommandExecutor, TabCompleter {

    private final CustomFishingZ plugin;

    public Commands(CustomFishingZ plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "wand":
                return handleWandCommand(sender);
            case "setpool":
                return handleSetPoolCommand(sender, args);
            case "reload":
                return handleReloadCommand(sender);
            case "reward":
                return handleRewardCommand(sender, args);
            default:
                return false;
        }
    }

    private boolean handleWandCommand(CommandSender sender) {
        if (!sender.hasPermission("customfishingz.wand")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission for this command!");
        } else if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
        } else {
            Player player = (Player) sender;
            ItemStack wand = new ItemStack(Material.BLAZE_ROD);
            ItemMeta meta = wand.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "Fishing Pool Wand");
            wand.setItemMeta(meta);
            player.getInventory().addItem(wand);
            player.sendMessage(ChatColor.GREEN + "You have been given the Fishing Pool Wand!");
        }
        return true;
    }

    private boolean handleSetPoolCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customfishingz.setpool")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission for this command!");
        } else if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
        } else if (args.length != 2) {
            sender.sendMessage("Usage: /customfishingz setpool <name>");
        } else {
            Player player = (Player) sender;
            CustomFishingZ.Selection selection = plugin.getSelection(player);
            if (selection == null || selection.getPos1() == null || selection.getPos2() == null) {
                sender.sendMessage(ChatColor.RED + "Please select both positions using the wand first!");
            } else {
                plugin.setPool(args[1], selection.getPos1(), selection.getPos2());
                sender.sendMessage(ChatColor.GREEN + "Fishing pool '" + args[1] + "' set successfully!");
                plugin.clearSelection(player);
            }
        }
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("customfishingz.reload")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission for this command!");
        } else {
            plugin.loadConfig();
            sender.sendMessage(ChatColor.GREEN + "Config reloaded successfully!");
        }
        return true;
    }

    private boolean handleRewardCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customfishingz.reward")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission for this command!");
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /customfishingz reward <pool_name> <create|set|add|remove|delete> <reward_name> [args]");
            return true;
        }

        String poolName = args[1];
        String action = args[2].toLowerCase();
        String rewardName = args[3];

        switch (action) {
            case "create":
                return plugin.createReward(sender, poolName, rewardName);
            case "set":
                if (args.length < 6) {
                    sender.sendMessage(ChatColor.RED + "Usage: /customfishingz reward <pool_name> set <reward_name> <chance|message> <value>");
                    return true;
                }
                String setType = args[4].toLowerCase();
                String setValue = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
                return plugin.setReward(sender, poolName, rewardName, setType, setValue);
            case "add":
                if (args.length < 5) {
                    sender.sendMessage(ChatColor.RED + "Usage: /customfishingz reward <pool_name> add <reward_name> <command>");
                    return true;
                }
                String addCommand = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                return plugin.addRewardCommand(sender, poolName, rewardName, addCommand);
            case "remove":
                if (args.length < 5) {
                    sender.sendMessage(ChatColor.RED + "Usage: /customfishingz reward <pool_name> remove <reward_name> <command>");
                    return true;
                }
                String removeCommand = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                return plugin.removeRewardCommand(sender, poolName, rewardName, removeCommand);
            case "delete":
                return plugin.deleteReward(sender, poolName, rewardName);
            default:
                sender.sendMessage(ChatColor.RED + "Invalid action. Use create, set, add, remove, or delete.");
                return true;
        }
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("wand", "setpool", "reload", "reward"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reward")) {
            completions.addAll(plugin.getPoolNames());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reward")) {
            completions.addAll(Arrays.asList("create", "set", "add", "remove", "delete"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("reward") && !args[2].equalsIgnoreCase("create")) {
            completions.addAll(plugin.getRewardNames(args[1]));
        } else if (args.length == 5 && args[0].equalsIgnoreCase("reward") && args[2].equalsIgnoreCase("set")) {
            completions.addAll(Arrays.asList("chance", "message"));
        }
        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
