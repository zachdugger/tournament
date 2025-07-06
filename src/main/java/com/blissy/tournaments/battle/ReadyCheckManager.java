package com.blissy.tournaments.battle;

import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.data.Tournament;
import com.blissy.tournaments.data.TournamentMatch;
import com.blissy.tournaments.util.BroadcastUtil;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.TextFormatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages ready check state for tournament battles
 */
public class ReadyCheckManager {
    // Map to track which players are ready for their matches
    private static final Map<UUID, Boolean> readyPlayers = new HashMap<>();

    /**
     * Mark a player as ready for their current match
     * @param player The player marking as ready
     * @param tournament The tournament they are in
     * @return True if both players are now ready, false otherwise
     */
    public static boolean markPlayerReady(ServerPlayerEntity player, Tournament tournament) {
        if (player == null || tournament == null) {
            return false;
        }

        UUID playerId = player.getUUID();

        // Find the player's current match
        TournamentMatch match = findPlayerCurrentMatch(playerId, tournament);
        if (match == null) {
            BroadcastUtil.sendTitle(player, "No Active Match", TextFormatting.RED, 15, 100, 25);
            return false;
        }

        // Mark this player as ready
        readyPlayers.put(playerId, true);

        // Get opponent
        UUID opponentId = match.getOpponent(playerId);
        ServerPlayerEntity opponent = null;

        // Get the opponent player entity
        if (tournament.getParticipants() != null) {
            for (var participant : tournament.getParticipants()) {
                if (participant.getPlayerId().equals(opponentId)) {
                    opponent = participant.getPlayer();
                    break;
                }
            }
        }

        // Notify players
        BroadcastUtil.sendTitle(player, "Ready!", TextFormatting.GREEN, 15, 100, 25);

        // Check if opponent is also ready
        if (readyPlayers.getOrDefault(opponentId, false)) {
            // Both players are ready, start the battle
            if (opponent != null) {
                // Send countdown to both players
                BroadcastUtil.runCountdown(player, "Battle Starting", 3, null);
                BroadcastUtil.runCountdown(opponent, "Battle Starting", 3, null);

                // Mark match as in progress
                match.start();

                // Create final copies of the variables for use in the lambda
                final ServerPlayerEntity finalPlayer = player;
                final ServerPlayerEntity finalOpponent = opponent;
                final TournamentMatch finalMatch = match;

                // Initiate the battle after countdown (3.5 sec delay)
                if (player.getServer() != null) {
                    player.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(
                            70, // 3.5 seconds (after 3s countdown)
                            () -> {
                                // Initiate the battle with the final variables
                                com.blissy.tournaments.compat.PixelmonHandler.createTournamentBattle(finalPlayer, finalOpponent);

                                // Clear ready status for this match
                                clearReadyStatus(finalMatch);
                            }
                    ));
                } else {
                    // Fallback if server is null (shouldn't happen)
                    com.blissy.tournaments.compat.PixelmonHandler.createTournamentBattle(player, opponent);
                    clearReadyStatus(match);
                }

                return true;
            } else {
                BroadcastUtil.sendTitle(player, "Opponent Offline", TextFormatting.RED, 15, 100, 25);
                BroadcastUtil.sendSubtitle(player, "Please wait for them to reconnect", TextFormatting.RED, 15, 100, 25);
                return false;
            }
        } else {
            // Opponent not ready yet
            BroadcastUtil.sendSubtitle(player, "Waiting for opponent...", TextFormatting.YELLOW, 15, 100, 25);

            if (opponent != null) {
                BroadcastUtil.sendTitle(opponent, player.getName().getString() + " is Ready", TextFormatting.YELLOW, 15, 100, 25);
                BroadcastUtil.sendSubtitle(opponent, "Type /tournament ready when you're ready", TextFormatting.YELLOW, 15, 100, 25);
            }

            return false;
        }
    }

    /**
     * Find the current match for a player
     */
    private static TournamentMatch findPlayerCurrentMatch(UUID playerId, Tournament tournament) {
        if (tournament.getStatus() != Tournament.TournamentStatus.IN_PROGRESS) {
            return null;
        }

        for (TournamentMatch match : tournament.getCurrentMatches()) {
            if (match.hasPlayer(playerId) && match.getStatus() == TournamentMatch.MatchStatus.SCHEDULED) {
                return match;
            }
        }

        return null;
    }

    /**
     * Clear ready status for a match when it starts or ends
     */
    public static void clearReadyStatus(TournamentMatch match) {
        if (match == null) return;

        readyPlayers.remove(match.getPlayer1Id());
        readyPlayers.remove(match.getPlayer2Id());
    }

    /**
     * Check if a player can use the ready command
     */
    public static boolean canPlayerUseReadyCommand(ServerPlayerEntity player, Tournament tournament) {
        if (player == null || tournament == null) {
            return false;
        }

        return findPlayerCurrentMatch(player.getUUID(), tournament) != null;
    }
}