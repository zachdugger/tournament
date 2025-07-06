package com.blissy.tournaments.compat;

import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.data.Tournament;
import com.blissy.tournaments.data.TournamentMatch;
import com.blissy.tournaments.data.TournamentParticipant;
import com.pixelmonmod.pixelmon.api.events.battles.BattleStartedEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleEndEvent;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.api.battles.BattleResults;
import com.pixelmonmod.pixelmon.api.battles.BattleEndCause;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class PixelmonHandler {

    // Track tournament battles by battleIndex
    private static final Map<Integer, BattleInfo> tournamentBattles = new HashMap<>();

    // Counter for match checking frequency (every 20 ticks = 1 second)
    private static int tickCounter = 0;
    private static final int CHECK_FREQUENCY = 20;

    private static class BattleInfo {
        public final String tournamentName;
        public final UUID player1Id;
        public final UUID player2Id;
        public long startTime;

        public BattleInfo(String tournamentName, UUID player1Id, UUID player2Id) {
            this.tournamentName = tournamentName;
            this.player1Id = player1Id;
            this.player2Id = player2Id;
            this.startTime = System.currentTimeMillis();
        }
    }

    /**
     * Debug method to log all active tournament battles
     */
    public static void debugTournamentBattles() {
        Tournaments.LOGGER.info("=== ACTIVE TOURNAMENT BATTLES ===");
        for (Map.Entry<Integer, BattleInfo> entry : tournamentBattles.entrySet()) {
            Tournaments.LOGGER.info("Battle #{}: Tournament={}, Player1={}, Player2={}",
                    entry.getKey(), entry.getValue().tournamentName,
                    entry.getValue().player1Id, entry.getValue().player2Id);
        }
        Tournaments.LOGGER.info("================================");
    }

    /**
     * Check all tournament matches for stuck IN_PROGRESS status and resolve them
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < CHECK_FREQUENCY) return;
        tickCounter = 0;

        // Process all tournaments to find IN_PROGRESS matches
        TournamentManager manager = TournamentManager.getInstance();

        for (Tournament tournament : manager.getAllTournaments().values()) {
            // Skip tournaments that aren't in progress
            if (tournament.getStatus() != Tournament.TournamentStatus.IN_PROGRESS) {
                continue;
            }

            Tournaments.LOGGER.info("Checking matches for tournament: {}", tournament.getName());

            // Check each match
            for (TournamentMatch match : tournament.getCurrentMatches()) {
                // Focus on IN_PROGRESS matches
                if (match.getStatus() == TournamentMatch.MatchStatus.IN_PROGRESS) {
                    Tournaments.LOGGER.info("Found IN_PROGRESS match: {} vs {}",
                            match.getPlayer1Name(), match.getPlayer2Name());

                    checkAndResolveMatch(tournament, match);
                }
            }

            // Check tournament completion directly too
            checkTournamentCompletion(tournament);
        }
    }

    /**
     * Force-check if a tournament should end (for cases where automatic detection fails)
     */
    private static void checkTournamentCompletion(Tournament tournament) {
        // Count active (non-eliminated) players
        int activePlayerCount = 0;
        UUID lastActivePlayer = null;

        for (TournamentParticipant participant : tournament.getParticipants()) {
            if (!tournament.isPlayerEliminated(participant.getPlayerId())) {
                activePlayerCount++;
                lastActivePlayer = participant.getPlayerId();
            }
        }

        Tournaments.LOGGER.info("Tournament {} has {} active players remaining",
                tournament.getName(), activePlayerCount);

        // If only one player remains, end the tournament
        if (activePlayerCount == 1 && lastActivePlayer != null) {
            // Get the winner's name
            String winnerName = "Unknown";
            for (TournamentParticipant participant : tournament.getParticipants()) {
                if (participant.getPlayerId().equals(lastActivePlayer)) {
                    winnerName = participant.getPlayerName();
                    break;
                }
            }

            Tournaments.LOGGER.info("Ending tournament {} with winner: {}",
                    tournament.getName(), winnerName);

            // Broadcast the winner
            tournament.broadcastMessage("★ Tournament Winner: " + winnerName + "! ★");

            // End the tournament
            tournament.end();
        }
    }

    /**
     * Check a match's players to see if a battle result can be determined
     */
    private static void checkAndResolveMatch(Tournament tournament, TournamentMatch match) {
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

        Tournaments.LOGGER.info("Checking Pokemon status for match: {} vs {}",
                match.getPlayer1Name(), match.getPlayer2Name());

        // Check if we can determine a winner based on fainted Pokémon
        UUID winnerId = null;
        UUID loserId = null;

        boolean player1AllFainted = true;
        boolean player2AllFainted = true;

        // Check player 1's Pokémon
        if (player1 != null) {
            Tournaments.LOGGER.info("Checking Pokemon for {}", match.getPlayer1Name());
            Pokemon[] player1Pokemon = StorageProxy.getParty(player1.getUUID()).getAll();

            for (Pokemon pokemon : player1Pokemon) {
                if (pokemon != null) {
                    boolean isFainted = pokemon.isFainted();
                    Tournaments.LOGGER.info("  Pokemon: {} - Fainted: {}",
                            pokemon.getDisplayName(), isFainted);

                    if (!isFainted) {
                        player1AllFainted = false;
                    }
                }
            }

            Tournaments.LOGGER.info("Player1 all Pokemon fainted: {}", player1AllFainted);
        }

        // Check player 2's Pokémon
        if (player2 != null) {
            Tournaments.LOGGER.info("Checking Pokemon for {}", match.getPlayer2Name());
            Pokemon[] player2Pokemon = StorageProxy.getParty(player2.getUUID()).getAll();

            for (Pokemon pokemon : player2Pokemon) {
                if (pokemon != null) {
                    boolean isFainted = pokemon.isFainted();
                    Tournaments.LOGGER.info("  Pokemon: {} - Fainted: {}",
                            pokemon.getDisplayName(), isFainted);

                    if (!isFainted) {
                        player2AllFainted = false;
                    }
                }
            }

            Tournaments.LOGGER.info("Player2 all Pokemon fainted: {}", player2AllFainted);
        }

        // Determine winner based on fainted status
        if (player1AllFainted && !player2AllFainted) {
            // Player 1 lost, Player 2 won
            winnerId = match.getPlayer2Id();
            loserId = match.getPlayer1Id();
            Tournaments.LOGGER.info("WINNER DETERMINED: {} (opponent's Pokemon all fainted)", match.getPlayer2Name());
        } else if (!player1AllFainted && player2AllFainted) {
            // Player 2 lost, Player 1 won
            winnerId = match.getPlayer1Id();
            loserId = match.getPlayer2Id();
            Tournaments.LOGGER.info("WINNER DETERMINED: {} (opponent's Pokemon all fainted)", match.getPlayer1Name());
        } else if (player1 != null && player2 == null) {
            // Player 2 is offline, player 1 wins
            winnerId = match.getPlayer1Id();
            loserId = match.getPlayer2Id();
            Tournaments.LOGGER.info("Player 1 wins because player 2 is offline");
        } else if (player1 == null && player2 != null) {
            // Player 1 is offline, player 2 wins
            winnerId = match.getPlayer2Id();
            loserId = match.getPlayer1Id();
            Tournaments.LOGGER.info("Player 2 wins because player 1 is offline");
        } else {
            // If we can't determine a winner yet, don't resolve the match
            Tournaments.LOGGER.info("Cannot determine winner yet - no player's team is fully fainted");
            return;
        }

        // Process the result
        processMatchResult(tournament, match, winnerId, loserId);

        // Check tournament completion after processing the match
        checkTournamentCompletion(tournament);
    }

    /**
     * Process a tournament match result
     */
    private static void processMatchResult(Tournament tournament, TournamentMatch match, UUID winnerId, UUID loserId) {
        // Get the names for display
        String winnerName = winnerId.equals(match.getPlayer1Id()) ? match.getPlayer1Name() : match.getPlayer2Name();
        String loserName = loserId.equals(match.getPlayer1Id()) ? match.getPlayer1Name() : match.getPlayer2Name();

        Tournaments.LOGGER.info("Processing match result: {} defeats {}", winnerName, loserName);

        // Record the result
        boolean resultRecorded = tournament.recordMatchResult(winnerId, loserId);
        if (!resultRecorded) {
            Tournaments.LOGGER.error("Failed to record match result");
            tournament.broadcastMessage("Error recording match result. Please contact an administrator.");
            return;
        }

        // Update ELO ratings
        Tournaments.ELO_MANAGER.recordMatch(winnerId, loserId);

        // Find players
        ServerPlayerEntity winner = null;
        ServerPlayerEntity loser = null;

        for (TournamentParticipant participant : tournament.getParticipants()) {
            if (participant.getPlayerId().equals(winnerId)) {
                winner = participant.getPlayer();
            } else if (participant.getPlayerId().equals(loserId)) {
                loser = participant.getPlayer();
            }
        }

        // Eliminate the loser
        tournament.eliminatePlayer(loserId);

        // Send notifications to players
        if (winner != null) {
            winner.sendMessage(
                    new StringTextComponent("You won the tournament match against " + loserName + "!")
                            .withStyle(TextFormatting.GREEN),
                    winner.getUUID());
        }

        if (loser != null) {
            loser.sendMessage(
                    new StringTextComponent("You lost the tournament match against " + winnerName + ". You have been eliminated.")
                            .withStyle(TextFormatting.RED),
                    loser.getUUID());
        }

        // Broadcast the result
        tournament.broadcastMessage(winnerName + " has defeated " + loserName + " and advances to the next round!");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBattleStart(BattleStartedEvent.Post event) {
        try {
            // Get the battle controller
            BattleController bc = event.getBattleController();

            // Debug log that the event was triggered
            Tournaments.LOGGER.info("Battle started event triggered. Battle controller: {}", bc.battleIndex);

            // Extract players from both teams
            List<ServerPlayerEntity> players = new ArrayList<>();
            Map<UUID, Integer> playerTeams = new HashMap<>();

            // Check team one
            for (BattleParticipant participant : event.getTeamOne()) {
                if (participant instanceof PlayerParticipant) {
                    PlayerParticipant playerParticipant = (PlayerParticipant) participant;
                    players.add(playerParticipant.player);
                    playerTeams.put(playerParticipant.player.getUUID(), 1);

                    // Log the player found
                    Tournaments.LOGGER.info("Team 1 player detected: {}", playerParticipant.player.getName().getString());
                }
            }

            // Check team two
            for (BattleParticipant participant : event.getTeamTwo()) {
                if (participant instanceof PlayerParticipant) {
                    PlayerParticipant playerParticipant = (PlayerParticipant) participant;
                    players.add(playerParticipant.player);
                    playerTeams.put(playerParticipant.player.getUUID(), 2);

                    // Log the player found
                    Tournaments.LOGGER.info("Team 2 player detected: {}", playerParticipant.player.getName().getString());
                }
            }

            // If we don't have exactly 2 players, this isn't a 1v1 player battle
            if (players.size() != 2) {
                Tournaments.LOGGER.info("Not a 1v1 player battle. Player count: {}", players.size());
                return;
            }

            ServerPlayerEntity player1 = players.get(0);
            ServerPlayerEntity player2 = players.get(1);

            TournamentManager manager = TournamentManager.getInstance();
            Tournament tournament1 = manager.getPlayerTournament(player1);
            Tournament tournament2 = manager.getPlayerTournament(player2);

            // Log tournament check
            Tournaments.LOGGER.info("Player 1 tournament: {}, Player 2 tournament: {}",
                    tournament1 != null ? tournament1.getName() : "none",
                    tournament2 != null ? tournament2.getName() : "none");

            // Both players must be in the same tournament
            if (tournament1 != null && tournament1 == tournament2) {
                // Find match between these players
                TournamentMatch match = null;
                for (TournamentMatch m : tournament1.getCurrentMatches()) {
                    if ((m.getPlayer1Id().equals(player1.getUUID()) && m.getPlayer2Id().equals(player2.getUUID())) ||
                            (m.getPlayer1Id().equals(player2.getUUID()) && m.getPlayer2Id().equals(player1.getUUID()))) {
                        match = m;
                        break;
                    }
                }

                // Make sure match is marked as IN_PROGRESS
                if (match != null && match.getStatus() == TournamentMatch.MatchStatus.SCHEDULED) {
                    match.start();
                    Tournaments.LOGGER.info("Match status updated to IN_PROGRESS");
                }

                // This is a tournament battle
                Tournaments.LOGGER.info("TOURNAMENT BATTLE DETECTED: Adding to tracked battles with index {}",
                        bc.battleIndex);

                tournamentBattles.put(bc.battleIndex,
                        new BattleInfo(tournament1.getName(), player1.getUUID(), player2.getUUID()));

                // Notify players
                player1.sendMessage(
                        new StringTextComponent("Tournament battle started against " + player2.getName().getString())
                                .withStyle(TextFormatting.GOLD),
                        player1.getUUID());

                player2.sendMessage(
                        new StringTextComponent("Tournament battle started against " + player1.getName().getString())
                                .withStyle(TextFormatting.GOLD),
                        player2.getUUID());

                Tournaments.LOGGER.info("Tournament battle started: {} vs {}",
                        player1.getName().getString(), player2.getName().getString());

                // Debug all tournament battles
                debugTournamentBattles();
            } else {
                Tournaments.LOGGER.info("Not a tournament battle - players not in same tournament");
            }
        } catch (Exception e) {
            Tournaments.LOGGER.error("Exception in onBattleStart", e);
        }
    }

    @SubscribeEvent
    public static void onBattleEnd(BattleEndEvent event) {
        try {
            // Get the battle controller
            BattleController bc = event.getBattleController();

            // Debug log that the event was triggered
            Tournaments.LOGGER.info("Battle end event triggered. Battle controller: {}", bc.battleIndex);

            // Check if this was a tournament battle
            BattleInfo battleInfo = tournamentBattles.get(bc.battleIndex);
            if (battleInfo == null) {
                Tournaments.LOGGER.info("Battle {} was not a tracked tournament battle", bc.battleIndex);
                return;
            }

            // This was a tournament battle, check for forfeit/flee
            BattleEndCause endCause = event.getCause();
            boolean wasForfeit = false;
            UUID forfeitPlayerId = null;

            Tournaments.LOGGER.info("Tournament battle ended. Cause: {}, Abnormal: {}", endCause, event.isAbnormal());

            // Check if it was a forfeit/flee
            if (endCause == BattleEndCause.FORFEIT || endCause == BattleEndCause.FLEE) {
                wasForfeit = true;
                Tournaments.LOGGER.info("Battle ended due to forfeit/flee");

                // Try to determine who forfeited by checking battle results
                Map<BattleParticipant, BattleResults> results = event.getResults();

                for (Map.Entry<BattleParticipant, BattleResults> entry : results.entrySet()) {
                    BattleParticipant participant = entry.getKey();
                    BattleResults result = entry.getValue();

                    if (participant instanceof PlayerParticipant) {
                        PlayerParticipant playerParticipant = (PlayerParticipant) participant;
                        UUID playerId = playerParticipant.player.getUUID();

                        // Check if this player forfeited (has a loss result)
                        if (result == BattleResults.DEFEAT || result == BattleResults.FLEE) {
                            forfeitPlayerId = playerId;
                            Tournaments.LOGGER.info("Player {} forfeited the battle", playerParticipant.player.getName().getString());
                            break;
                        }
                    }
                }
            }

            // Handle forfeit case
            if (wasForfeit && forfeitPlayerId != null) {
                handleForfeit(battleInfo, forfeitPlayerId);
            } else {
                // Normal battle end - let the tick handler process it
                Tournaments.LOGGER.info("Normal battle end, will be processed by tick handler");
            }

            // Remove from tracking
            tournamentBattles.remove(bc.battleIndex);
            Tournaments.LOGGER.info("Removed battle {} from tracking", bc.battleIndex);

        } catch (Exception e) {
            Tournaments.LOGGER.error("Error in battle end event", e);
        }
    }

    /**
     * Handle a forfeit in a tournament battle
     */
    private static void handleForfeit(BattleInfo battleInfo, UUID forfeitPlayerId) {
        try {
            TournamentManager manager = TournamentManager.getInstance();
            Tournament tournament = manager.getTournament(battleInfo.tournamentName);

            if (tournament == null) {
                Tournaments.LOGGER.error("Tournament {} not found when handling forfeit", battleInfo.tournamentName);
                return;
            }

            // Determine winner and loser
            UUID winnerId, loserId;
            if (forfeitPlayerId.equals(battleInfo.player1Id)) {
                // Player 1 forfeited, Player 2 wins
                winnerId = battleInfo.player2Id;
                loserId = battleInfo.player1Id;
            } else {
                // Player 2 forfeited, Player 1 wins
                winnerId = battleInfo.player1Id;
                loserId = battleInfo.player2Id;
            }

            // Get player names
            String winnerName = "Unknown";
            String loserName = "Unknown";

            for (TournamentParticipant participant : tournament.getParticipants()) {
                if (participant.getPlayerId().equals(winnerId)) {
                    winnerName = participant.getPlayerName();
                } else if (participant.getPlayerId().equals(loserId)) {
                    loserName = participant.getPlayerName();
                }
            }

            Tournaments.LOGGER.info("Forfeit detected: {} forfeited to {}", loserName, winnerName);

            // Record the match result
            boolean resultRecorded = tournament.recordMatchResult(winnerId, loserId);

            if (resultRecorded) {
                // Update ELO ratings
                Tournaments.ELO_MANAGER.recordMatch(winnerId, loserId);

                // Notify players
                ServerPlayerEntity winner = null;
                ServerPlayerEntity loser = null;

                for (TournamentParticipant participant : tournament.getParticipants()) {
                    if (participant.getPlayerId().equals(winnerId)) {
                        winner = participant.getPlayer();
                    } else if (participant.getPlayerId().equals(loserId)) {
                        loser = participant.getPlayer();
                    }
                }

                if (winner != null) {
                    winner.sendMessage(
                            new StringTextComponent("You won the tournament match! " + loserName + " forfeited.")
                                    .withStyle(TextFormatting.GREEN),
                            winner.getUUID());
                }

                if (loser != null) {
                    loser.sendMessage(
                            new StringTextComponent("You forfeited the tournament match against " + winnerName + ". You have been eliminated.")
                                    .withStyle(TextFormatting.RED),
                            loser.getUUID());
                }

                // Broadcast the forfeit result
                tournament.broadcastMessage(loserName + " forfeited to " + winnerName + " and has been eliminated!");

                Tournaments.LOGGER.info("Successfully processed forfeit: {} advances, {} eliminated", winnerName, loserName);
            } else {
                Tournaments.LOGGER.error("Failed to record forfeit result");
            }

        } catch (Exception e) {
            Tournaments.LOGGER.error("Error handling forfeit", e);
        }
    }

    /**
     * Validate that a player's Pokémon meet tournament requirements
     */
    public static boolean validatePlayerPokemon(ServerPlayerEntity player, TournamentManager.TournamentSettings settings) {
        try {
            // Null check for parameters
            if (player == null || settings == null) {
                Tournaments.LOGGER.error("Null parameters in validatePlayerPokemon: player={}, settings={}",
                        player != null, settings != null);
                return false;
            }

            // Get the player's Pokémon
            Pokemon[] playerPokemon = StorageProxy.getParty(player.getUUID()).getAll();

            // Check if the player has any Pokémon
            if (playerPokemon == null || playerPokemon.length == 0) {
                player.sendMessage(
                        new StringTextComponent("You don't have any Pokemon in your party")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                return false;
            }

            // Count valid Pokémon
            int validCount = 0;

            // Check each Pokémon
            for (Pokemon p : playerPokemon) {
                // Skip null Pokémon
                if (p == null) {
                    continue;
                }

                // Check level
                if (settings.isValidPokemonLevel(p.getPokemonLevel())) {
                    validCount++;
                } else {
                    player.sendMessage(
                            new StringTextComponent("Your Pokemon " + p.getDisplayName() +
                                    " (Level " + p.getPokemonLevel() +
                                    ") does not meet tournament level requirements " +
                                    "(" + settings.getMinLevel() + "-" + settings.getMaxLevel() + ")")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                }
            }

            // Player needs at least one valid Pokémon to join
            if (validCount == 0) {
                player.sendMessage(
                        new StringTextComponent("None of your Pokemon meet the tournament level requirements " +
                                "(" + settings.getMinLevel() + "-" + settings.getMaxLevel() + ")")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                return false;
            }

            return true;
        } catch (Exception e) {
            Tournaments.LOGGER.error("Error validating player Pokemon", e);
            player.sendMessage(
                    new StringTextComponent("Error checking your Pokemon. Please try again.")
                            .withStyle(TextFormatting.RED),
                    player.getUUID());
            return false;
        }
    }

    /**
     * Create a battle between two tournament participants
     */
    public static void createTournamentBattle(ServerPlayerEntity player1, ServerPlayerEntity player2) {
        createTournamentBattle(player1, player2, null);
    }

    /**
     * Create a battle between two tournament participants with specified format
     */
    public static void createTournamentBattle(ServerPlayerEntity player1, ServerPlayerEntity player2, String battleFormat) {
        try {
            // Null check for players
            if (player1 == null || player2 == null) {
                Tournaments.LOGGER.error("Cannot create tournament battle with null players");
                return;
            }

            // Verify both players have valid Pokemon
            Pokemon[] p1AllPokemon = StorageProxy.getParty(player1.getUUID()).getAll();
            Pokemon[] p2AllPokemon = StorageProxy.getParty(player2.getUUID()).getAll();

            if (p1AllPokemon == null || p2AllPokemon == null) {
                Tournaments.LOGGER.error("Failed to get Pokemon for players");
                return;
            }

            if (p1AllPokemon.length == 0 || p2AllPokemon.length == 0) {
                player1.sendMessage(
                        new StringTextComponent("Cannot start battle - one or both players have no Pokemon")
                                .withStyle(TextFormatting.RED),
                        player1.getUUID());

                player2.sendMessage(
                        new StringTextComponent("Cannot start battle - one or both players have no Pokemon")
                                .withStyle(TextFormatting.RED),
                        player2.getUUID());

                Tournaments.LOGGER.error("Cannot start battle - one or both players have no Pokemon");
                return;
            }

            // Get the correct battle format from tournament settings
            Tournament tournament = TournamentManager.getInstance().getPlayerTournament(player1);
            String format = "SINGLES"; // Default format

            if (tournament != null) {
                // Check tournament settings for battle format
                TournamentManager.TournamentSettings settings =
                        TournamentManager.getInstance().getTournamentSettings(tournament.getName());

                if (settings != null && settings.getFormat() != null) {
                    String tournamentFormat = settings.getFormat();

                    // Check if format contains battle type information
                    if (tournamentFormat.contains("SINGLES")) {
                        format = "SINGLES";
                    } else if (tournamentFormat.contains("DOUBLES")) {
                        format = "DOUBLES";
                    }

                    Tournaments.LOGGER.info("Using battle format: {}", format);
                }

                // Verify Pokemon levels
                if (settings != null) {
                    boolean p1Valid = verifyPokemonLevels(p1AllPokemon, settings, player1);
                    boolean p2Valid = verifyPokemonLevels(p2AllPokemon, settings, player2);

                    if (!p1Valid || !p2Valid) {
                        return; // Players were notified in the verify method
                    }
                }
            }

            // Notify players
            player1.sendMessage(
                    new StringTextComponent("Starting " + format + " battle against " + player2.getName().getString())
                            .withStyle(TextFormatting.AQUA),
                    player1.getUUID());

            player2.sendMessage(
                    new StringTextComponent("Starting " + format + " battle against " + player1.getName().getString())
                            .withStyle(TextFormatting.AQUA),
                    player2.getUUID());

            // Create the battle with proper format
            if (format.equals("SINGLES")) {
                // Singles format - select only first Pokémon from each player
                Pokemon[] p1SinglePokemon = new Pokemon[1];
                Pokemon[] p2SinglePokemon = new Pokemon[1];

                // Find first non-fainted Pokémon for each player
                for (Pokemon p : p1AllPokemon) {
                    if (p != null && !p.isFainted()) {
                        p1SinglePokemon[0] = p;
                        break;
                    }
                }

                for (Pokemon p : p2AllPokemon) {
                    if (p != null && !p.isFainted()) {
                        p2SinglePokemon[0] = p;
                        break;
                    }
                }

                // Create participants with single Pokémon
                PlayerParticipant p1 = new PlayerParticipant(player1, p1SinglePokemon);
                PlayerParticipant p2 = new PlayerParticipant(player2, p2SinglePokemon);

                // Start 1v1 battle
                BattleController bc = BattleRegistry.startBattle(p1, p2);
                Tournaments.LOGGER.info("Started SINGLES (1v1) battle");

                if (bc != null) {
                    Tournaments.LOGGER.info("Singles battle created with index: {}", bc.battleIndex);
                }
            }
            else if (format.equals("DOUBLES")) {
                // Doubles format - select first two Pokémon from each player
                Pokemon[] p1DoublePokemon = new Pokemon[2];
                Pokemon[] p2DoublePokemon = new Pokemon[2];

                // Find first two non-fainted Pokémon for each player
                int count1 = 0;
                for (Pokemon p : p1AllPokemon) {
                    if (p != null && !p.isFainted() && count1 < 2) {
                        p1DoublePokemon[count1] = p;
                        count1++;
                    }
                }

                int count2 = 0;
                for (Pokemon p : p2AllPokemon) {
                    if (p != null && !p.isFainted() && count2 < 2) {
                        p2DoublePokemon[count2] = p;
                        count2++;
                    }
                }

                // Create participants with double Pokémon
                PlayerParticipant p1 = new PlayerParticipant(player1, p1DoublePokemon);
                PlayerParticipant p2 = new PlayerParticipant(player2, p2DoublePokemon);

                // Start 2v2 battle
                BattleController bc = BattleRegistry.startBattle(p1, p2);
                Tournaments.LOGGER.info("Started DOUBLES (2v2) battle");

                if (bc != null) {
                    Tournaments.LOGGER.info("Doubles battle created with index: {}", bc.battleIndex);
                }
            }
            else {
                // Default behavior - use full teams
                PlayerParticipant p1 = new PlayerParticipant(player1, p1AllPokemon);
                PlayerParticipant p2 = new PlayerParticipant(player2, p2AllPokemon);

                BattleController bc = BattleRegistry.startBattle(p1, p2);
                Tournaments.LOGGER.info("Started FULL TEAM battle (no specific format)");

                if (bc != null) {
                    Tournaments.LOGGER.info("Full team battle created with index: {}", bc.battleIndex);
                }
            }

        } catch (Exception e) {
            Tournaments.LOGGER.error("Error starting tournament battle", e);

            // Notify players of error
            if (player1 != null) {
                player1.sendMessage(
                        new StringTextComponent("Error starting battle. Please try again.")
                                .withStyle(TextFormatting.RED),
                        player1.getUUID());
            }

            if (player2 != null) {
                player2.sendMessage(
                        new StringTextComponent("Error starting battle. Please try again.")
                                .withStyle(TextFormatting.RED),
                        player2.getUUID());
            }
        }
    }

    /**
     * Verify that all Pokémon in a player's party meet tournament level requirements
     */
    private static boolean verifyPokemonLevels(Pokemon[] pokemon,
                                               TournamentManager.TournamentSettings settings,
                                               ServerPlayerEntity player) {
        // Check for null parameters
        if (pokemon == null || settings == null || player == null) {
            Tournaments.LOGGER.error("Null parameters in verifyPokemonLevels: pokemon={}, settings={}, player={}",
                    pokemon != null, settings != null, player != null);
            return false;
        }

        for (Pokemon p : pokemon) {
            // Skip null Pokemon
            if (p == null) {
                continue;
            }

            if (!settings.isValidPokemonLevel(p.getPokemonLevel())) {
                player.sendMessage(
                        new StringTextComponent("Your Pokemon " + p.getDisplayName() +
                                " (Level " + p.getPokemonLevel() +
                                ") does not meet tournament level requirements " +
                                "(" + settings.getMinLevel() + "-" + settings.getMaxLevel() + ")")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                return false;
            }
        }
        return true;
    }
}