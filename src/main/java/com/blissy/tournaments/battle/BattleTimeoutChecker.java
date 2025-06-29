package com.blissy.tournaments.battle;

import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.config.TournamentsConfig;
import com.blissy.tournaments.data.Tournament;
import com.blissy.tournaments.data.TournamentMatch;
import com.blissy.tournaments.data.TournamentParticipant;
import com.blissy.tournaments.util.TeleportUtil;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Checks for battles that have been stuck in IN_PROGRESS state for too long
 * and resolves them by picking a winner based on some criteria
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class BattleTimeoutChecker {

    private static final int CHECK_INTERVAL = 1200; // Check every minute (20 ticks/s * 60s = 1200 ticks)
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        // Get battle timeout in seconds from config
        int timeoutSeconds = TournamentsConfig.COMMON.battleTimeoutSeconds.get();

        // Check all tournaments
        for (Tournament tournament : TournamentManager.getInstance().getAllTournaments().values()) {
            if (tournament.getStatus() != Tournament.TournamentStatus.IN_PROGRESS) {
                continue;
            }

            List<TournamentMatch> stuckMatches = new ArrayList<>();

            // Check for stuck matches
            for (TournamentMatch match : tournament.getCurrentMatches()) {
                if (match.getStatus() == TournamentMatch.MatchStatus.IN_PROGRESS &&
                        match.getStartedAt() != null) {

                    // Calculate how long the match has been running
                    Duration duration = Duration.between(match.getStartedAt(), Instant.now());

                    // If longer than timeout, mark as stuck
                    if (duration.getSeconds() > timeoutSeconds) {
                        stuckMatches.add(match);
                        Tournaments.LOGGER.warn("Match {} has been in progress for {} seconds (timeout: {})",
                                match.getDescription(), duration.getSeconds(), timeoutSeconds);
                    }
                }
            }

            // Resolve stuck matches
            for (TournamentMatch match : stuckMatches) {
                resolveStuckMatch(tournament, match);
            }
        }
    }

    /**
     * Resolve a stuck match by picking a winner
     */
    private static void resolveStuckMatch(Tournament tournament, TournamentMatch match) {
        Tournaments.LOGGER.info("Resolving stuck match: {} vs {}",
                match.getPlayer1Name(), match.getPlayer2Name());

        // Get player entities
        ServerPlayerEntity player1 = null;
        ServerPlayerEntity player2 = null;

        for (TournamentParticipant participant : tournament.getParticipants()) {
            if (participant.getPlayerId().equals(match.getPlayer1Id())) {
                player1 = participant.getPlayer();
            } else if (participant.getPlayerId().equals(match.getPlayer2Id())) {
                player2 = participant.getPlayer();
            }
        }

        // Determine a winner based on who is online
        UUID winnerId;
        UUID loserId;
        String winnerName;
        String loserName;

        if (player1 != null && player2 == null) {
            // Player 2 is offline, player 1 wins
            winnerId = match.getPlayer1Id();
            loserId = match.getPlayer2Id();
            winnerName = match.getPlayer1Name();
            loserName = match.getPlayer2Name();
            Tournaments.LOGGER.info("Player 1 ({}) wins because player 2 ({}) is offline",
                    winnerName, loserName);
        } else if (player1 == null && player2 != null) {
            // Player 1 is offline, player 2 wins
            winnerId = match.getPlayer2Id();
            loserId = match.getPlayer1Id();
            winnerName = match.getPlayer2Name();
            loserName = match.getPlayer1Name();
            Tournaments.LOGGER.info("Player 2 ({}) wins because player 1 ({}) is offline",
                    winnerName, loserName);
        } else if (player1 != null && player2 != null) {
            // Both online, pick player with lower UUID as arbitrary tiebreaker
            if (match.getPlayer1Id().compareTo(match.getPlayer2Id()) < 0) {
                winnerId = match.getPlayer1Id();
                loserId = match.getPlayer2Id();
                winnerName = match.getPlayer1Name();
                loserName = match.getPlayer2Name();
            } else {
                winnerId = match.getPlayer2Id();
                loserId = match.getPlayer1Id();
                winnerName = match.getPlayer2Name();
                loserName = match.getPlayer1Name();
            }
            Tournaments.LOGGER.info("Both players online, arbitrary winner: {}", winnerName);
        } else {
            // Both offline, pick player with lower UUID as arbitrary tiebreaker
            if (match.getPlayer1Id().compareTo(match.getPlayer2Id()) < 0) {
                winnerId = match.getPlayer1Id();
                loserId = match.getPlayer2Id();
                winnerName = match.getPlayer1Name();
                loserName = match.getPlayer2Name();
            } else {
                winnerId = match.getPlayer2Id();
                loserId = match.getPlayer1Id();
                winnerName = match.getPlayer2Name();
                loserName = match.getPlayer1Name();
            }
            Tournaments.LOGGER.info("Both players offline, arbitrary winner: {}", winnerName);
        }

        // Record the result
        boolean resultRecorded = tournament.recordMatchResult(winnerId, loserId);

        if (resultRecorded) {
            // Match completion is now fully handled in recordMatchResult, including:
            // - ELO updates
            // - Player elimination
            // - Teleportation
            // - Notification

            Tournaments.LOGGER.info("Successfully resolved stuck match: {} wins", winnerName);
        } else {
            Tournaments.LOGGER.error("Failed to record result for stuck match: {} vs {}",
                    match.getPlayer1Name(), match.getPlayer2Name());
        }
    }}