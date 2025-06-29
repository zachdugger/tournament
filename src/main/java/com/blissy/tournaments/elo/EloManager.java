package com.blissy.tournaments.elo;

import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.config.EloConfig;
import com.blissy.tournaments.data.EloPlayer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class EloManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ELO_FILE = "config/tournaments/elo_data.json";

    private final Map<UUID, EloPlayer> playerMap = new HashMap<>();

    public void recordMatch(UUID winnerId, UUID loserId) {
        EloPlayer winner = getOrCreatePlayer(winnerId);
        EloPlayer loser = getOrCreatePlayer(loserId);

        int winnerElo = winner.getElo();
        int loserElo = loser.getElo();

        double winnerExpected = 1 / (1 + Math.pow(10, (loserElo - winnerElo) / 400.0));
        double loserExpected = 1 / (1 + Math.pow(10, (winnerElo - loserElo) / 400.0));

        int winnerGain = (int) Math.round(EloConfig.K_FACTOR.get() * (1 - winnerExpected));
        int loserLoss = (int) Math.round(EloConfig.K_FACTOR.get() * loserExpected);

        winner.addWin();
        winner.addElo(winnerGain);

        loser.addLoss();
        loser.subtractElo(loserLoss);

        ServerPlayerEntity winnerPlayer = winner.getPlayer();
        ServerPlayerEntity loserPlayer = loser.getPlayer();

        if (winnerPlayer != null) {
            winnerPlayer.sendMessage(
                    new StringTextComponent("You gained " + winnerGain + " ELO points for winning!")
                            .withStyle(TextFormatting.GREEN),
                    winnerPlayer.getUUID());
        }

        if (loserPlayer != null) {
            loserPlayer.sendMessage(
                    new StringTextComponent("You lost " + loserLoss + " ELO points.")
                            .withStyle(TextFormatting.RED),
                    loserPlayer.getUUID());
        }

        save();
    }

    public void resetRankings() {
        // Determine top players before reset
        List<EloPlayer> topPlayers = getTopPlayers(EloConfig.REWARD_SLOTS.get());

        // Reset all players to default ELO
        for (EloPlayer player : playerMap.values()) {
            player.reset();
        }

        // Give rewards based on final standings
        giveRewards(topPlayers);

        save();
    }

    private void giveRewards(List<EloPlayer> topPlayers) {
        // Get rewards map when needed, not from a constant
        Map<String, String> rewards = EloConfig.getRewards();

        for (int i = 0; i < topPlayers.size(); i++) {
            EloPlayer player = topPlayers.get(i);
            String rewardKey = "reward_" + (i + 1);

            if (rewards.containsKey(rewardKey)) {
                String rewardCommand = rewards.get(rewardKey);
                rewardCommand = rewardCommand.replace("{player}", player.getPlayerName());

                Tournaments.LOGGER.info("Giving reward '" + rewardCommand + "' to player " + player.getPlayerName());

                ServerPlayerEntity playerEntity = player.getPlayer();
                if (playerEntity != null) {
                    playerEntity.getServer().getCommands().performCommand(
                            playerEntity.getServer().createCommandSourceStack(), rewardCommand);

                    playerEntity.sendMessage(
                            new StringTextComponent("You received a reward for placing #" + (i+1) + " on the leaderboard!")
                                    .withStyle(TextFormatting.GOLD),
                            playerEntity.getUUID());
                }
            }
        }
    }

    public List<EloPlayer> getTopPlayers(int count) {
        List<EloPlayer> players = new ArrayList<>(playerMap.values());
        players.sort(Comparator.comparingInt(EloPlayer::getElo).reversed());

        return players.subList(0, Math.min(count, players.size()));
    }

    public EloPlayer getPlayer(UUID playerId) {
        return playerMap.get(playerId);
    }

    // Made public to allow access from other classes
    public EloPlayer getOrCreatePlayer(UUID playerId) {
        return playerMap.computeIfAbsent(playerId, id -> new EloPlayer(id));
    }

    public void load() {
        File file = new File(ELO_FILE);

        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                EloPlayer[] players = GSON.fromJson(reader, EloPlayer[].class);
                if (players != null) {
                    Arrays.stream(players).forEach(player -> playerMap.put(player.getPlayerId(), player));
                }
            } catch (IOException e) {
                Tournaments.LOGGER.error("Failed to load ELO data", e);
            }
        }
    }

    public void save() {
        File file = new File(ELO_FILE);

        // Ensure directory exists
        File dir = file.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(playerMap.values(), writer);
        } catch (IOException e) {
            Tournaments.LOGGER.error("Failed to save ELO data", e);
        }
    }
}