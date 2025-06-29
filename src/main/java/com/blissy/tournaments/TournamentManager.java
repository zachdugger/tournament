package com.blissy.tournaments;

import com.blissy.tournaments.data.Tournament;
import com.blissy.tournaments.data.TournamentMatch;
import com.blissy.tournaments.events.TournamentEvent;
import com.blissy.tournaments.util.BroadcastUtil;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class TournamentManager {
    // Singleton instance
    private static TournamentManager instance;

    // Stores all active tournaments
    private final Map<String, Tournament> tournaments;

    // Tracks which tournament a player is currently in
    private final Map<UUID, String> playerTournaments;

    // Stores tournament-specific settings
    private final Map<String, TournamentSettings> tournamentSettings;

    // Stores additional tournament settings like entry fee
    private final Map<String, CompoundNBT> tournamentExtraSettings;

    /**
     * Private constructor for singleton pattern
     */
    private TournamentManager() {
        tournaments = new HashMap<>();
        playerTournaments = new HashMap<>();
        tournamentSettings = new HashMap<>();
        tournamentExtraSettings = new HashMap<>();
    }

    /**
     * Get the singleton instance of TournamentManager
     * @return TournamentManager instance
     */
    public static TournamentManager getInstance() {
        if (instance == null) {
            instance = new TournamentManager();
        }
        return instance;
    }

    /**
     * Initialize the tournament manager
     * Called when the mod is loaded
     */
    public void initialize() {
        // Future: Load saved tournaments from persistent storage
        Tournaments.LOGGER.info("Initializing Tournament Manager");

        // Start a task to check for scheduled tournament starts
        startScheduledTournamentChecker();
    }

    /**
     * Shutdown and cleanup tournament manager
     * Called when the mod is unloaded
     */
    public void shutdown() {
        // End all active tournaments
        tournaments.values().forEach(Tournament::end);

        // Future: Save tournament data to persistent storage
        tournaments.clear();
        playerTournaments.clear();
        tournamentSettings.clear();
        tournamentExtraSettings.clear();

        Tournaments.LOGGER.info("Tournament Manager shut down");
    }

    /**
     * Create a new tournament
     * @param name Unique tournament name
     * @param maxParticipants Maximum number of participants
     * @param host Tournament host
     * @return True if tournament was created successfully
     */
    public boolean createTournament(String name, int maxParticipants, ServerPlayerEntity host) {
        // Check if tournament name already exists
        if (tournaments.containsKey(name)) {
            return false;
        }

        // Create new tournament
        Tournament tournament = new Tournament(name, maxParticipants, host);
        tournaments.put(name, tournament);

        // Fire creation event
        MinecraftForge.EVENT_BUS.post(new TournamentEvent.Created(tournament));

        Tournaments.LOGGER.info("Tournament created: {} by {}", name, host.getName().getString());

        return true;
    }

    /**
     * Join an existing tournament
     * @param tournamentName Name of the tournament
     * @param player Player attempting to join
     * @return True if join was successful
     */
    public boolean joinTournament(String tournamentName, ServerPlayerEntity player) {
        // Check tournament exists
        Tournament tournament = tournaments.get(tournamentName);
        if (tournament == null) {
            BroadcastUtil.sendTitle(player, "Tournament Not Found", TextFormatting.RED, 10, 70, 20);
            BroadcastUtil.sendSubtitle(player, tournamentName, TextFormatting.RED, 10, 70, 20);
            return false;
        }

        // Check if player is already in a tournament
        if (playerTournaments.containsKey(player.getUUID())) {
            BroadcastUtil.sendTitle(player, "Already In Tournament", TextFormatting.RED, 10, 70, 20);
            BroadcastUtil.sendSubtitle(player, "Leave your current tournament first", TextFormatting.RED, 10, 70, 20);
            return false;
        }

        // Check if tournament is waiting for players
        if (tournament.getStatus() != Tournament.TournamentStatus.WAITING) {
            BroadcastUtil.sendTitle(player, "Tournament Already Started", TextFormatting.RED, 10, 70, 20);
            BroadcastUtil.sendSubtitle(player, "Cannot join now", TextFormatting.RED, 10, 70, 20);
            return false;
        }

        // Get tournament settings to validate Pokémon
        TournamentSettings settings = getTournamentSettings(tournamentName);

        // Validate player's Pokémon meet level requirements
        if (!com.blissy.tournaments.compat.PixelmonHandler.validatePlayerPokemon(player, settings)) {
            // The validation method already sends appropriate messages to the player
            return false;
        }

        // Check if tournament has an entry fee
        CompoundNBT extraSettings = tournamentExtraSettings.get(tournamentName);
        if (extraSettings != null && extraSettings.contains("entryFee")) {
            double entryFee = extraSettings.getDouble("entryFee");

            // If there's an entry fee, check if player can afford it
            if (entryFee > 0) {
                // Use economy manager to check and withdraw funds
                try {
                    Class<?> economyManagerClass = Class.forName("com.blissy.tournaments.economy.EconomyManager");
                    boolean hasBalance = (boolean) economyManagerClass
                            .getMethod("hasBalance", ServerPlayerEntity.class, double.class)
                            .invoke(null, player, entryFee);

                    if (!hasBalance) {
                        BroadcastUtil.sendTitle(player, "Insufficient Funds", TextFormatting.RED, 10, 70, 20);
                        BroadcastUtil.sendSubtitle(player, "Entry fee: " + entryFee, TextFormatting.RED, 10, 70, 20);
                        return false;
                    }

                    boolean withdrawn = (boolean) economyManagerClass
                            .getMethod("withdrawBalance", ServerPlayerEntity.class, double.class)
                            .invoke(null, player, entryFee);

                    if (!withdrawn) {
                        BroadcastUtil.sendTitle(player, "Payment Error", TextFormatting.RED, 10, 70, 20);
                        BroadcastUtil.sendSubtitle(player, "Failed to process entry fee", TextFormatting.RED, 10, 70, 20);
                        return false;
                    }

                    BroadcastUtil.sendActionBar(player, "Paid " + entryFee + " to enter tournament", TextFormatting.GREEN);
                } catch (Exception e) {
                    // If economy plugin is not available, log warning but still allow join
                    Tournaments.LOGGER.warn("Economy plugin not available. Entry fee not processed.");
                }
            }
        }

        // Attempt to add participant
        if (tournament.addParticipant(player)) {
            playerTournaments.put(player.getUUID(), tournamentName);

            // Fire join event
            MinecraftForge.EVENT_BUS.post(new TournamentEvent.PlayerJoined(tournament, player));

            Tournaments.LOGGER.info("Player {} joined tournament {}",
                    player.getName().getString(), tournamentName);

            // Show title to player that they've joined (CHANGED FROM CHAT TO TITLE)
            BroadcastUtil.sendTitle(player, "Joined Tournament", TextFormatting.GREEN, 10, 70, 20);
            BroadcastUtil.sendSubtitle(player, tournamentName, TextFormatting.GREEN, 10, 70, 20);

            return true;
        }

        return false;
    }

    /**
     * Leave the current tournament
     * @param player Player leaving the tournament
     * @return True if leave was successful
     */
    public boolean leaveTournament(ServerPlayerEntity player) {
        UUID playerId = player.getUUID();

        // Check if player is in a tournament
        if (!playerTournaments.containsKey(playerId)) {
            return false;
        }

        String tournamentName = playerTournaments.get(playerId);
        Tournament tournament = tournaments.get(tournamentName);

        if (tournament != null && tournament.removeParticipant(player)) {
            playerTournaments.remove(playerId);

            // Fire leave event
            MinecraftForge.EVENT_BUS.post(new TournamentEvent.PlayerLeft(tournament, player));

            Tournaments.LOGGER.info("Player {} left tournament {}",
                    player.getName().getString(), tournamentName);

            // CHANGED FROM CHAT TO TITLE
            BroadcastUtil.sendTitle(player, "Left Tournament", TextFormatting.YELLOW, 10, 70, 20);
            BroadcastUtil.sendSubtitle(player, tournamentName, TextFormatting.YELLOW, 10, 70, 20);


            return true;
        }

        return false;
    }

    /**
     * Delete a tournament by name
     * @param tournamentName Name of the tournament to delete
     * @return True if tournament was deleted successfully
     */
    public boolean deleteTournament(String tournamentName) {
        // Check if tournament exists
        Tournament tournament = tournaments.get(tournamentName);
        if (tournament == null) {
            return false;
        }

        // End the tournament
        tournament.end();

        // Remove all participants from the tournament
        List<UUID> playersToRemove = new ArrayList<>();
        for (UUID playerId : playerTournaments.keySet()) {
            if (tournamentName.equals(playerTournaments.get(playerId))) {
                playersToRemove.add(playerId);
            }
        }

        // Remove players from the tournament mapping
        for (UUID playerId : playersToRemove) {
            playerTournaments.remove(playerId);
        }

        // Remove tournament data
        tournaments.remove(tournamentName);
        tournamentSettings.remove(tournamentName);
        tournamentExtraSettings.remove(tournamentName);

        Tournaments.LOGGER.info("Tournament deleted: {}", tournamentName);

        return true;
    }

    /**
     * Record a match result
     * @param tournamentName Name of the tournament
     * @param winnerUUID UUID of the winner
     * @param loserUUID UUID of the loser
     * @return True if result was recorded successfully
     */
    public boolean recordMatchResult(String tournamentName, UUID winnerUUID, UUID loserUUID) {
        Tournament tournament = tournaments.get(tournamentName);
        if (tournament == null) {
            return false;
        }

        return tournament.recordMatchResult(winnerUUID, loserUUID);
    }

    /**
     * Set tournament-specific settings
     * @param tournamentName Name of the tournament
     * @param minLevel Minimum Pokemon level
     * @param maxLevel Maximum Pokemon level
     * @param format Tournament format
     */
    public void setTournamentSettings(String tournamentName, int minLevel, int maxLevel, String format) {
        tournamentSettings.put(tournamentName,
                new TournamentSettings(minLevel, maxLevel, format));
    }

    /**
     * Set additional tournament settings
     * @param tournamentName Name of the tournament
     * @param extraSettings Additional settings as NBT compound
     */
    public void setTournamentExtraSettings(String tournamentName, CompoundNBT extraSettings) {
        tournamentExtraSettings.put(tournamentName, extraSettings);
    }

    /**
     * Get additional tournament settings
     * @param tournamentName Name of the tournament
     * @return Extra settings or null if not found
     */
    public CompoundNBT getTournamentExtraSettings(String tournamentName) {
        return tournamentExtraSettings.getOrDefault(tournamentName, new CompoundNBT());
    }

    /**
     * Get tournament settings
     * @param tournamentName Name of the tournament
     * @return Tournament settings or default settings
     */
    public TournamentSettings getTournamentSettings(String tournamentName) {
        return tournamentSettings.getOrDefault(tournamentName,
                new TournamentSettings(1, 100, "SINGLE_ELIMINATION"));
    }

    /**
     * Get a specific tournament
     * @param name Tournament name
     * @return Tournament or null if not found
     */
    public Tournament getTournament(String name) {
        return tournaments.get(name);
    }

    /**
     * Get the tournament a player is currently in
     * @param player Player to check
     * @return Tournament or null if not in a tournament
     */
    public Tournament getPlayerTournament(ServerPlayerEntity player) {
        if (player == null) return null;

        String tournamentName = playerTournaments.get(player.getUUID());
        return tournamentName != null ? tournaments.get(tournamentName) : null;
    }

    /**
     * Get all active tournaments
     * @return Map of tournament names to tournaments
     */
    public Map<String, Tournament> getAllTournaments() {
        return new HashMap<>(tournaments);
    }

    /**
     * Find tournaments matching specific criteria
     * @param minPlayers Minimum number of participants
     * @param maxPlayers Maximum number of participants
     * @param status Tournament status to filter
     * @return List of matching tournaments
     */
    public List<Tournament> findTournaments(
            Integer minPlayers,
            Integer maxPlayers,
            Tournament.TournamentStatus status
    ) {
        return tournaments.values().stream()
                .filter(tournament ->
                        (minPlayers == null || tournament.getParticipantCount() >= minPlayers) &&
                                (maxPlayers == null || tournament.getParticipantCount() <= maxPlayers) &&
                                (status == null || tournament.getStatus() == status)
                )
                .collect(Collectors.toList());
    }

    /**
     * Find tournaments with matches in progress
     * @return List of tournaments with active matches
     */
    public List<Tournament> findTournamentsWithActiveMatches() {
        return tournaments.values().stream()
                .filter(tournament -> tournament.getStatus() == Tournament.TournamentStatus.IN_PROGRESS)
                .collect(Collectors.toList());
    }

    /**
     * Start the scheduled tournament checker
     */
    private void startScheduledTournamentChecker() {
        // Create scheduled task to run every 30 seconds to check for tournaments that need to start
        Thread checkerThread = new Thread(() -> {
            Tournaments.LOGGER.info("Starting scheduled tournament checker thread");
            while (true) {
                try {
                    // Sleep for 30 seconds between checks
                    Thread.sleep(30000);

                    // Check for tournaments that are scheduled to start
                    Instant now = Instant.now();
                    for (Tournament tournament : tournaments.values()) {
                        if (tournament.getStatus() == Tournament.TournamentStatus.WAITING &&
                                tournament.getScheduledStartTime() != null &&
                                !now.isBefore(tournament.getScheduledStartTime())) {

                            // Start the tournament
                            Tournaments.LOGGER.info("Starting scheduled tournament: {}", tournament.getName());
                            tournament.start();
                        }
                    }
                } catch (InterruptedException e) {
                    Tournaments.LOGGER.error("Tournament scheduler interrupted", e);
                    break;
                } catch (Exception e) {
                    Tournaments.LOGGER.error("Error in tournament scheduler", e);
                    // Continue running despite errors
                }
            }
        });

        // Set as daemon thread so it doesn't prevent server shutdown
        checkerThread.setDaemon(true);
        checkerThread.start();
    }

    /**
     * Set the scheduled start time for a tournament
     */
    public void setTournamentScheduledStart(String tournamentName, double hoursDelay) {
        Tournament tournament = tournaments.get(tournamentName);
        if (tournament != null && tournament.getStatus() == Tournament.TournamentStatus.WAITING) {
            if (hoursDelay <= 0) {
                // No scheduled start
                tournament.setScheduledStartTime(null);
            } else {
                // Calculate the scheduled start time
                long millisDelay = (long)(hoursDelay * 60 * 60 * 1000);
                Instant startTime = Instant.now().plusMillis(millisDelay);
                tournament.setScheduledStartTime(startTime);

                // Log the scheduled start
                Tournaments.LOGGER.info("Tournament {} scheduled to start at {}",
                        tournamentName, startTime);

                // Notify participants
                tournament.broadcastMessage("Tournament scheduled to start in " +
                        (hoursDelay < 1 ?
                                (int)(hoursDelay * 60) + " minutes" :
                                String.format("%.1f hours", hoursDelay)));
            }
        }
    }

    /**
     * Tournament settings inner class
     */
    public static class TournamentSettings {
        private final int minLevel;
        private final int maxLevel;
        private final String format;

        public TournamentSettings(int minLevel, int maxLevel, String format) {
            this.minLevel = Math.max(1, Math.min(minLevel, 100));
            this.maxLevel = Math.max(1, Math.min(maxLevel, 100));
            this.format = format != null ? format : "SINGLE_ELIMINATION";
        }

        public int getMinLevel() {
            return minLevel;
        }

        public int getMaxLevel() {
            return maxLevel;
        }

        public String getFormat() {
            return format;
        }

        /**
         * Check if a Pokemon meets the level requirements
         * @param pokemonLevel Pokemon's level
         * @return True if the Pokemon is within the level range
         */
        public boolean isValidPokemonLevel(int pokemonLevel) {
            return pokemonLevel >= minLevel && pokemonLevel <= maxLevel;
        }

    }
}