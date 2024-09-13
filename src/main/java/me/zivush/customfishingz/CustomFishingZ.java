package me.zivush.customfishingz;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.PluginManager;

import java.util.*;

public class CustomFishingZ extends JavaPlugin implements Listener {

    private Map<String, FishingPool> fishingPools;
    private Map<UUID, Selection> playerSelections;
    private Map<Player, FishingGame> activeGames;
    private Location poolLocation;
    private int poolRadius;
    private Map<String, Reward> rewards;
    private String fishEscapeMessage;
    private String fishCaughtMessage;
    private boolean randomSpeed;
    private int fixedSpeed;
    private int minSpeed;
    private int maxSpeed;
    private String barTexture;
    private StatisticsManager statisticsManager;
    private boolean usePlaceholders;
    private String gameDisplayType;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Commands commands = new Commands(this);
        getCommand("customfishingz").setExecutor(commands);
        getCommand("customfishingz").setTabCompleter(commands);
        activeGames = new HashMap<>();
        playerSelections = new HashMap<>();

        boolean storeStats = getConfig().getBoolean("store_statistics", true);
        statisticsManager = new StatisticsManager(this, storeStats);

        PluginManager pluginManager = getServer().getPluginManager();
        usePlaceholders = pluginManager.getPlugin("PlaceholderAPI") != null;
        if (usePlaceholders) {
            new Placeholders(this).register();
        }
    }

    @Override
    public void onDisable() {
        for (FishingGame game : activeGames.values()) {
            game.end();
        }
        if (statisticsManager != null) {
            statisticsManager.saveDatabase();
        }
    }

    public void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();

        fishingPools = new HashMap<>();
        ConfigurationSection poolsSection = config.getConfigurationSection("pools");
        gameDisplayType = getConfig().getString("game_display.type", "subtitle").toLowerCase();
        if (poolsSection != null) {
            for (String poolName : poolsSection.getKeys(false)) {
                ConfigurationSection poolSection = poolsSection.getConfigurationSection(poolName);
                if (poolSection != null) {
                    Location pos1 = locationFromString(poolSection.getString("pos1"));
                    Location pos2 = locationFromString(poolSection.getString("pos2"));
                    Map<String, Reward> rewards = loadRewards(poolSection.getConfigurationSection("rewards"));
                    fishingPools.put(poolName, new FishingPool(pos1, pos2, rewards));
                }
            }
        }

        fishEscapeMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.fish_escape"));
        fishCaughtMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.fish_caught"));

        randomSpeed = config.getBoolean("settings.random_speed", false);
        fixedSpeed = config.getInt("settings.speed", 1);
        minSpeed = config.getInt("settings.min_speed", 1);
        maxSpeed = config.getInt("settings.max_speed", 4);

        barTexture = config.getString("settings.bar_texture", "█");
    }
    private Map<String, Reward> loadRewards(ConfigurationSection rewardsSection) {
        Map<String, Reward> rewards = new HashMap<>();
        if (rewardsSection != null) {
            for (String key : rewardsSection.getKeys(false)) {
                ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(key);
                if (rewardSection != null) {
                    rewards.put(key, new Reward(
                            rewardSection.getString("message", "You won a reward!").replace('&', '§'),
                            rewardSection.getStringList("commands"),
                            rewardSection.getDouble("chance", 0.1)
                    ));
                }
            }
        }
        return rewards;
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            Player player = event.getPlayer();
            Location hookLocation = event.getHook().getLocation();
            FishingPool pool = getPoolAtLocation(hookLocation);
            if (pool != null) {
                getLogger().info("Fish caught in pool");
                event.setCancelled(true);
                handleFishingRodDurability(player);
                Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> startFishingGame(player, pool), 3L);
            }
        }
    }
    private void handleFishingRodDurability(Player player) {
        ItemStack fishingRod = player.getInventory().getItemInMainHand();
        if (fishingRod.getType() == Material.FISHING_ROD) {
            int unbreakingLevel = fishingRod.getEnchantmentLevel(Enchantment.DURABILITY);
            boolean shouldTakeDamage = true;

            if (unbreakingLevel > 0) {
                Random random = new Random();
                int chance = 100 / (unbreakingLevel + 1);
                shouldTakeDamage = random.nextInt(100) < chance;
            }

            if (shouldTakeDamage) {
                fishingRod.setDurability((short) (fishingRod.getDurability() + 1));
            }
        }
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.BLAZE_ROD && item.hasItemMeta() &&
                item.getItemMeta().getDisplayName().equals(ChatColor.GOLD + "Fishing Pool Wand")) {
            event.setCancelled(true);
            Selection selection = playerSelections.computeIfAbsent(player.getUniqueId(), k -> new Selection());

            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                selection.setPos1(event.getClickedBlock().getLocation());
                player.sendMessage(ChatColor.GREEN + "Position 1 set.");
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                selection.setPos2(event.getClickedBlock().getLocation());
                player.sendMessage(ChatColor.GREEN + "Position 2 set.");
            }
        }

        FishingGame game = activeGames.get(player);
        if (game != null) {
            game.onInteract();
        }
    }
    private FishingPool getPoolAtLocation(Location location) {
        for (FishingPool pool : fishingPools.values()) {
            if (pool.isInPool(location)) {
                return pool;
            }
        }
        return null;
    }

    private void startFishingGame(Player player, FishingPool pool) {
        if (activeGames.containsKey(player)) {
            activeGames.get(player).end();
        }
        FishingGame game = new FishingGame(player, pool);
        activeGames.put(player, game);
        game.start();
    }


    private void giveReward(Player player, FishingPool pool) {
        Map<String, Reward> rewards = pool.getRewards();
        if (rewards == null || rewards.isEmpty()) {
            getLogger().warning("No rewards found for pool");
            return;
        }

        double random = Math.random();
        double cumulativeChance = 0;

        for (Map.Entry<String, Reward> entry : rewards.entrySet()) {
            String rewardName = entry.getKey();
            Reward reward = entry.getValue();
            cumulativeChance += reward.getChance();
            if (random <= cumulativeChance) {
                player.sendMessage(reward.getMessage());
                for (String command : reward.getCommands()) {
                    command = command.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
                statisticsManager.incrementRewardWon(player, rewardName);
                return;
            }
        }
    }

    public static class FishingPool {
        private final Location pos1;
        private final Location pos2;
        private final Map<String, Reward> rewards;

        public FishingPool(Location pos1, Location pos2, Map<String, Reward> rewards) {
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.rewards = rewards != null ? rewards : new HashMap<>();
        }

        public boolean isInPool(Location location) {
            World world = pos1.getWorld();
            if (world == null || !world.equals(location.getWorld())) {
                return false;
            }

            double minX = Math.min(pos1.getX(), pos2.getX());
            double minY = Math.min(pos1.getY(), pos2.getY());
            double minZ = Math.min(pos1.getZ(), pos2.getZ());
            double maxX = Math.max(pos1.getX(), pos2.getX());
            double maxY = Math.max(pos1.getY(), pos2.getY());
            double maxZ = Math.max(pos1.getZ(), pos2.getZ());

            return location.getX() >= minX && location.getX() <= maxX &&
                    location.getY() >= minY && location.getY() <= maxY &&
                    location.getZ() >= minZ && location.getZ() <= maxZ;
        }

        public Map<String, Reward> getRewards() {
            return rewards;
        }
    }

    public void setPool(String name, Location pos1, Location pos2) {
        FishingPool pool = new FishingPool(pos1, pos2, new HashMap<>());
        fishingPools.put(name, pool);

        FileConfiguration config = getConfig();
        String path = "pools." + name + ".";
        config.set(path + "pos1", locationToString(pos1));
        config.set(path + "pos2", locationToString(pos2));
        saveConfig();
    }

    private class FishingGame {
        private final Player player;
        private BossBar bossBar;
        private BukkitRunnable gameTask;
        private int position;
        private int count;
        private boolean increasing;
        private int gameSpeed;
        private final FishingPool pool;

        public FishingGame(Player player, FishingPool pool) {
            this.player = player;
            this.pool = pool;
            if (gameDisplayType.equals("bossbar")) {
                this.bossBar = Bukkit.createBossBar(getBarText(), BarColor.GREEN, BarStyle.SOLID);
                this.bossBar.addPlayer(player);
            }

            if (randomSpeed) {
                Random random = new Random();
                gameSpeed = random.nextInt(maxSpeed - minSpeed + 1) + minSpeed;
            } else {
                gameSpeed = fixedSpeed;
            }
        }

        public void start() {
            position = 0;
            increasing = true;
            gameTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (count >= 80) {
                        end();
                        if (fishEscapeMessage != null && !fishEscapeMessage.isEmpty()) {
                            player.sendMessage(fishEscapeMessage);
                        }
                        player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, 1.0f, 1.0f);
                    }
                    if (increasing) {
                        position++;
                        count++;
                        if (position >= 20) {
                            increasing = false;
                        }
                    } else {
                        position--;
                        count++;
                        if (position <= 0) {
                            increasing = true;
                        }
                    }
                    updateBar();
                }
            };
            statisticsManager.incrementFishPlayed(player);
            gameTask.runTaskTimer(CustomFishingZ.this, 0, gameSpeed);
        }

        public void onInteract() {
            if ((position == 9 || position == 10 || position == 11)) {
                end();
                giveReward(player, pool);
                if (fishCaughtMessage != null && !fishCaughtMessage.isEmpty()) {
                    player.sendMessage(fishCaughtMessage);
                }
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                statisticsManager.incrementFishCaught(player);
            } else {
                end();
                if (fishEscapeMessage != null && !fishEscapeMessage.isEmpty()) {
                    player.sendMessage(fishEscapeMessage);
                }
                player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, 1.0f, 1.0f);
                statisticsManager.incrementFishEscaped(player);
            }
        }

        public void end() {
            if (gameTask != null) {
                gameTask.cancel();
            }
            player.getWorld().getEntities().stream()
                    .filter(entity -> entity instanceof org.bukkit.entity.FishHook)
                    .map(entity -> (org.bukkit.entity.FishHook) entity)
                    .filter(fishHook -> fishHook.getShooter() instanceof Player)
                    .filter(fishHook -> fishHook.getShooter().equals(player))
                    .forEach(org.bukkit.entity.FishHook::remove);

            if (bossBar != null) {
                bossBar.removeAll();
            }
            if (gameDisplayType.equals("subtitle")) {
                player.sendTitle("", "", 0, 1, 0); // Clear the subtitle
            }
            activeGames.remove(player);
        }

        private void updateBar() {
            String barText = getBarText();
            switch (gameDisplayType) {
                case "actionbar":
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(barText));
                    break;
                case "bossbar":
                    if (bossBar != null) {
                        bossBar.setTitle(barText);
                    }
                    break;
                case "subtitle":
                    player.sendTitle("", barText, 0, 20, 0);
                    break;
            }
        }

        private String getBarText() {
            StringBuilder barText = new StringBuilder("§7");
            for (int i = 0; i < 21; i++) {
                if (i == position) {
                    barText.append("§a").append(barTexture);
                } else if (i == 9 || i == 10 || i == 11) {
                    barText.append("§2").append(barTexture);
                } else {
                    barText.append("§7").append(barTexture);
                }
            }
            return barText.toString();
        }
    }
    public static class Selection {
        private Location pos1;
        private Location pos2;

        public void setPos1(Location pos1) {
            this.pos1 = pos1;
        }

        public void setPos2(Location pos2) {
            this.pos2 = pos2;
        }

        public Location getPos1() {
            return pos1;
        }

        public Location getPos2() {
            return pos2;
        }
    }

    private static class Reward {
        private String message;
        private List<String> commands;
        private double chance;

        public Reward(String message, List<String> commands, double chance) {
            this.message = message;
            this.commands = commands;
            this.chance = chance;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getCommands() {
            return commands;
        }

        public double getChance() {
            return chance;
        }
        public void setMessage(String message) {
            this.message = message;
        }

        public void setCommands(List<String> commands) {
            this.commands = commands;
        }

        public void setChance(double chance) {
            this.chance = chance;
        }
    }
    public Selection getSelection(Player player) {
        return playerSelections.get(player.getUniqueId());
    }

    public void clearSelection(Player player) {
        playerSelections.remove(player.getUniqueId());
    }

    private String locationToString(Location loc) {
        return String.format("%.2f,%.2f,%.2f,%s", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
    }

    private Location locationFromString(String str) {
        String[] parts = str.split(",");
        World world = Bukkit.getWorld(parts[3]);
        return new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
    }
    public boolean createReward(CommandSender sender, String poolName, String rewardName) {
        if (!fishingPools.containsKey(poolName)) {
            sender.sendMessage(ChatColor.RED + "Pool '" + poolName + "' does not exist.");
            return false;
        }

        FishingPool pool = fishingPools.get(poolName);
        if (pool.getRewards().containsKey(rewardName)) {
            sender.sendMessage(ChatColor.RED + "Reward '" + rewardName + "' already exists in this pool.");
            return false;
        }

        // Create a new reward with default values
        Reward newReward = new Reward("You won a reward!", new ArrayList<>(), 0.1);
        pool.getRewards().put(rewardName, newReward);

        // Update config
        FileConfiguration config = getConfig();
        String path = "pools." + poolName + ".rewards." + rewardName;
        config.set(path + ".message", newReward.getMessage());
        config.set(path + ".commands", newReward.getCommands());
        config.set(path + ".chance", newReward.getChance());
        saveConfig();

        sender.sendMessage(ChatColor.GREEN + "Reward '" + rewardName + "' created successfully with default values.");
        sender.sendMessage(ChatColor.YELLOW + "Use '/customfishingz reward " + poolName + " set " + rewardName + "' to set chance and message.");
        sender.sendMessage(ChatColor.YELLOW + "Use '/customfishingz reward " + poolName + " add " + rewardName + "' to add commands.");
        return true;
    }

    public boolean setReward(CommandSender sender, String poolName, String rewardName, String setType, String setValue) {
        if (!fishingPools.containsKey(poolName)) {
            sender.sendMessage(ChatColor.RED + "Pool '" + poolName + "' does not exist.");
            return false;
        }

        FishingPool pool = fishingPools.get(poolName);
        if (!pool.getRewards().containsKey(rewardName)) {
            sender.sendMessage(ChatColor.RED + "Reward '" + rewardName + "' does not exist in this pool.");
            return false;
        }

        Reward reward = pool.getRewards().get(rewardName);
        String path = "pools." + poolName + ".rewards." + rewardName;

        switch (setType.toLowerCase()) {
            case "chance":
                try {
                    double chance = Double.parseDouble(setValue);
                    reward.setChance(chance);
                    getConfig().set(path + ".chance", chance);
                    sender.sendMessage(ChatColor.GREEN + "Chance set to " + chance + " for reward '" + rewardName + "'.");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid chance value. Please enter a number.");
                    return false;
                }
                break;
            case "message":
                reward.setMessage(ChatColor.translateAlternateColorCodes('&', setValue));
                getConfig().set(path + ".message", setValue);
                sender.sendMessage(ChatColor.GREEN + "Message set for reward '" + rewardName + "'.");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Invalid set type. Use 'chance' or 'message'.");
                return false;
        }

        saveConfig();
        return true;
    }

    public boolean addRewardCommand(CommandSender sender, String poolName, String rewardName, String command) {
        if (!fishingPools.containsKey(poolName)) {
            sender.sendMessage(ChatColor.RED + "Pool '" + poolName + "' does not exist.");
            return false;
        }

        FishingPool pool = fishingPools.get(poolName);
        if (!pool.getRewards().containsKey(rewardName)) {
            sender.sendMessage(ChatColor.RED + "Reward '" + rewardName + "' does not exist in this pool.");
            return false;
        }

        Reward reward = pool.getRewards().get(rewardName);
        reward.getCommands().add(command);

        // Update config
        String path = "pools." + poolName + ".rewards." + rewardName + ".commands";
        getConfig().set(path, reward.getCommands());
        saveConfig();

        sender.sendMessage(ChatColor.GREEN + "Command added to reward '" + rewardName + "'.");
        return true;
    }
    public boolean removeRewardCommand(CommandSender sender, String poolName, String rewardName, String command) {
        if (!fishingPools.containsKey(poolName)) {
            sender.sendMessage(ChatColor.RED + "Pool '" + poolName + "' does not exist.");
            return false;
        }

        FishingPool pool = fishingPools.get(poolName);
        if (!pool.getRewards().containsKey(rewardName)) {
            sender.sendMessage(ChatColor.RED + "Reward '" + rewardName + "' does not exist in this pool.");
            return false;
        }

        Reward reward = pool.getRewards().get(rewardName);
        if (!reward.getCommands().remove(command)) {
            sender.sendMessage(ChatColor.RED + "Command not found in reward '" + rewardName + "'.");
            return false;
        }

        // Update config
        String path = "pools." + poolName + ".rewards." + rewardName + ".commands";
        getConfig().set(path, reward.getCommands());
        saveConfig();

        sender.sendMessage(ChatColor.GREEN + "Command removed from reward '" + rewardName + "'.");
        return true;
    }
    public boolean deleteReward(CommandSender sender, String poolName, String rewardName) {
        if (!fishingPools.containsKey(poolName)) {
            sender.sendMessage(ChatColor.RED + "Pool '" + poolName + "' does not exist.");
            return false;
        }

        FishingPool pool = fishingPools.get(poolName);
        if (!pool.getRewards().containsKey(rewardName)) {
            sender.sendMessage(ChatColor.RED + "Reward '" + rewardName + "' does not exist in this pool.");
            return false;
        }

        pool.getRewards().remove(rewardName);

        // Update config
        String path = "pools." + poolName + ".rewards." + rewardName;
        getConfig().set(path, null);
        saveConfig();

        sender.sendMessage(ChatColor.GREEN + "Reward '" + rewardName + "' has been deleted from pool '" + poolName + "'.");
        return true;
    }
    public List<String> getRewardNames(String poolName) {
        FishingPool pool = fishingPools.get(poolName);
        if (pool == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(pool.getRewards().keySet());
    }

    public List<String> getPoolNames() {
        return new ArrayList<>(fishingPools.keySet());
    }
    public StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

    public boolean isUsePlaceholders() {
        return usePlaceholders;
    }

}
