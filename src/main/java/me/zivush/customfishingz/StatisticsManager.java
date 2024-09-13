package me.zivush.customfishingz;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatisticsManager {
    private final Plugin plugin;
    private final File databaseFile;
    private FileConfiguration database;
    private final boolean storeStats;

    private final Map<UUID, PlayerStats> playerStats;
    private ServerStats serverStats;

    public StatisticsManager(Plugin plugin, boolean storeStats) {
        this.plugin = plugin;
        this.storeStats = storeStats;
        this.databaseFile = new File(plugin.getDataFolder(), "database.yml");
        this.playerStats = new HashMap<>();
        this.serverStats = new ServerStats();

        if (storeStats) {
            loadDatabase();
        }
    }

    private void loadDatabase() {
        if (!databaseFile.exists()) {
            plugin.saveResource("database.yml", false);
        }
        database = YamlConfiguration.loadConfiguration(databaseFile);
        loadServerStats();
        loadPlayerStats();
    }

    private void loadServerStats() {
        serverStats.fishCaught = database.getInt("server.fishCaught", 0);
        serverStats.fishEscaped = database.getInt("server.fishEscaped", 0);
        serverStats.fishPlayed = database.getInt("server.fishPlayed", 0);
        serverStats.rewardsWon = database.getConfigurationSection("server.rewardsWon") != null ?
                database.getConfigurationSection("server.rewardsWon").getValues(false) : new HashMap<>();
    }

    private void loadPlayerStats() {
        if (database.getConfigurationSection("players") != null) {
            for (String uuidString : database.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                PlayerStats stats = new PlayerStats();
                stats.fishCaught = database.getInt("players." + uuidString + ".fishCaught", 0);
                stats.fishEscaped = database.getInt("players." + uuidString + ".fishEscaped", 0);
                stats.fishPlayed = database.getInt("players." + uuidString + ".fishPlayed", 0);
                stats.rewardsWon = database.getConfigurationSection("players." + uuidString + ".rewardsWon") != null ?
                        database.getConfigurationSection("players." + uuidString + ".rewardsWon").getValues(false) : new HashMap<>();
                playerStats.put(uuid, stats);
            }
        }
    }

    public void saveDatabase() {
        if (!storeStats) return;

        database.set("server.fishCaught", serverStats.fishCaught);
        database.set("server.fishEscaped", serverStats.fishEscaped);
        database.set("server.fishPlayed", serverStats.fishPlayed);
        for (Map.Entry<String, Object> entry : serverStats.rewardsWon.entrySet()) {
            database.set("server.rewardsWon." + entry.getKey(), entry.getValue());
        }

        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            String uuidString = entry.getKey().toString();
            PlayerStats stats = entry.getValue();
            database.set("players." + uuidString + ".fishCaught", stats.fishCaught);
            database.set("players." + uuidString + ".fishEscaped", stats.fishEscaped);
            database.set("players." + uuidString + ".fishPlayed", stats.fishPlayed);
            for (Map.Entry<String, Object> rewardEntry : stats.rewardsWon.entrySet()) {
                database.set("players." + uuidString + ".rewardsWon." + rewardEntry.getKey(), rewardEntry.getValue());
            }
        }

        try {
            database.save(databaseFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save database.yml!");
            e.printStackTrace();
        }
    }

    public void incrementFishCaught(Player player) {
        if (!storeStats) return;
        serverStats.fishCaught++;
        getPlayerStats(player).fishCaught++;
    }

    public void incrementFishEscaped(Player player) {
        if (!storeStats) return;
        serverStats.fishEscaped++;
        getPlayerStats(player).fishEscaped++;
    }

    public void incrementFishPlayed(Player player) {
        if (!storeStats) return;
        serverStats.fishPlayed++;
        getPlayerStats(player).fishPlayed++;
    }

    public void incrementRewardWon(Player player, String rewardName) {
        if (!storeStats) return;
        serverStats.rewardsWon.merge(rewardName, 1, (a, b) -> (Integer)a + (Integer)b);
        getPlayerStats(player).rewardsWon.merge(rewardName, 1, (a, b) -> (Integer)a + (Integer)b);
    }

    private PlayerStats getPlayerStats(Player player) {
        return playerStats.computeIfAbsent(player.getUniqueId(), k -> new PlayerStats());
    }

    public int getServerFishCaught() {
        return serverStats.fishCaught;
    }

    public int getServerFishEscaped() {
        return serverStats.fishEscaped;
    }

    public int getServerFishPlayed() {
        return serverStats.fishPlayed;
    }

    public int getServerRewardWon(String rewardName) {
        return (Integer) serverStats.rewardsWon.getOrDefault(rewardName, 0);
    }

    public int getPlayerFishCaught(Player player) {
        return getPlayerStats(player).fishCaught;
    }

    public int getPlayerFishEscaped(Player player) {
        return getPlayerStats(player).fishEscaped;
    }

    public int getPlayerFishPlayed(Player player) {
        return getPlayerStats(player).fishPlayed;
    }

    public int getPlayerRewardWon(Player player, String rewardName) {
        return (Integer) getPlayerStats(player).rewardsWon.getOrDefault(rewardName, 0);
    }

    private static class ServerStats {
        int fishCaught;
        int fishEscaped;
        int fishPlayed;
        Map<String, Object> rewardsWon = new HashMap<>();
    }

    private static class PlayerStats {
        int fishCaught;
        int fishEscaped;
        int fishPlayed;
        Map<String, Object> rewardsWon = new HashMap<>();
    }
}