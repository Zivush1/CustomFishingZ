package me.zivush.customfishingz;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class Placeholders extends PlaceholderExpansion {
    private final CustomFishingZ plugin;

    public Placeholders(CustomFishingZ plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "customfishingz";
    }

    @Override
    public String getAuthor() {
        return "Zivush";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        StatisticsManager stats = plugin.getStatisticsManager();

        if (identifier.equals("fish_caught")) {
            return String.valueOf(stats.getServerFishCaught());
        }

        if (identifier.equals("fish_escaped")) {
            return String.valueOf(stats.getServerFishEscaped());
        }

        if (identifier.equals("fish_played")) {
            return String.valueOf(stats.getServerFishPlayed());
        }

        if (identifier.startsWith("won_reward_")) {
            String rewardName = identifier.substring("won_reward_".length());
            return String.valueOf(stats.getServerRewardWon(rewardName));
        }

        if (player == null) {
            return "";
        }

        if (identifier.equals("player_fish_caught")) {
            return String.valueOf(stats.getPlayerFishCaught(player));
        }

        if (identifier.equals("player_fish_escaped")) {
            return String.valueOf(stats.getPlayerFishEscaped(player));
        }

        if (identifier.equals("player_fish_played")) {
            return String.valueOf(stats.getPlayerFishPlayed(player));
        }

        if (identifier.startsWith("player_won_reward_")) {
            String rewardName = identifier.substring("player_won_reward_".length());
            return String.valueOf(stats.getPlayerRewardWon(player, rewardName));
        }

        return null;
    }
}