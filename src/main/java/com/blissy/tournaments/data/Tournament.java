package com.blissy.tournaments.data;

import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.battle.ScheduledBattleManager;
import com.blissy.tournaments.config.TournamentsConfig;
import com.blissy.tournaments.util.BroadcastUtil;
import com.blissy.tournaments.util.TeleportUtil;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class Tournament {
    private final String name;
    private final int maxParticipants;
    private final UUID hostId;
    private final Map<UUID, TournamentParticipant> participants;
    private final Set<UUID> eliminatedPlayers;
    private TournamentStatus status;
    private Instant createdAt;
    private Instant startedAt;
    private Instant endedAt;
    private Instant scheduledStartTime;
    private String battleFormat;  // Added for singles/doubles battles

    // Tournament bracket management
    private List<List<TournamentParticipant>> brackets;
    private List<TournamentMatch> matches;
    private int currentRound;

    public Tournament(String name, int maxParticipants, ServerPlayerEntity host) {
        this(name, maxParticipants, host, "SINGLES"); // Default to singles battles
    }

    public Tournament(String name, int maxParticipants, ServerPlayerEntity host, String battleFormat) {
        this.name = name;
        this.maxParticipants = Math.min(maxParticipants,
                TournamentsConfig.COMMON.maxParticipants.get());
        this.hostId = host.getUUID();
        this.participants = new HashMap<>();
        this.eliminatedPlayers = new HashSet<>(); // Initialize
        this.status = TournamentStatus.WAITING;
        this.createdAt = Instant.now();
        this.scheduledStartTime = null;
        this.currentRound = 0;
        this.brackets = new ArrayList<>();
        this.matches = new ArrayList<>();
        this.battleFormat = validateBattleFormat(battleFormat);

        // Add host as first participant
        addParticipant(host);
    }

    private String validateBattleFormat(String format) {
        if (format == null) {
            return "SINGLES";
        }

        // Normalize to uppercase for comparison
        format = format.toUpperCase();

        // Check for valid formats
        if ("SINGLES".equals(format) || "DOUBLES".equals(format)) {
            return format;
        }

        // Default to singles for invalid formats
        Tournaments.LOGGER.warn("Invalid battle format specified: {}. Defaulting to SINGLES.", format);
        return "SINGLES";
    }

    /**
     * Get the battle format for this tournament (SINGLES or DOUBLES)
     */
    public String getBattleFormat() {
        return battleFormat;
    }

    /**
     * Set the battle format for this tournament
     * @param format The format to set (SINGLES or DOUBLES)
     */
    public void setBattleFormat(String format) {
        this.battleFormat = validateBattleFormat(format);
    }

    /**
     * Add a new participant to the tournament
     */
    public boolean addParticipant(ServerPlayerEntity player) {
        // Check tournament status
        if (status != TournamentStatus.WAITING) {
            BroadcastUtil.sendTitle(player, "Tournament Already Started", TextFormatting.RED, 10, 70, 20);
            BroadcastUtil.sendSubtitle(player, "Cannot join now", TextFormatting.RED, 10, 70, 20);
            return false;
        }

        // Check participant limit
        if (participants.size() >= maxParticipants) {
            BroadcastUtil.sendTitle(player, "Tournament Full", TextFormatting.RED, 10, 70, 20);
            BroadcastUtil.sendSubtitle(player, "This tournament is full", TextFormatting.RED, 10, 70, 20);
            return false;
        }

        UUID playerId = player.getUUID();
        TournamentParticipant participant = new TournamentParticipant(player);
        participants.put(playerId, participant);

        // Broadcast join message
        broadcastActionBar(player.getName().getString() + " has joined the tournament!");

        // Show welcome message to the joining player
        BroadcastUtil.sendTitle(player, "Joined Tournament", TextFormatting.GREEN, 10, 70, 20);
        BroadcastUtil.sendSubtitle(player, name, TextFormatting.YELLOW, 10, 70, 20);

        return true;
    }

    /**
     * Remove a participant from the tournament
     */
    public boolean removeParticipant(ServerPlayerEntity player) {
        UUID playerId = player.getUUID();

        if (!participants.containsKey(playerId)) {
            return false;
        }

        // Check if tournament has started and teleport leaving player to exit
        if (status == TournamentStatus.IN_PROGRESS &&
                TournamentsConfig.COMMON.enableTeleports.get() &&
                !eliminatedPlayers.contains(playerId)) {
            // Player is leaving an active tournament they weren't eliminated from
            boolean success = TeleportUtil.teleportToExitPoint(player);
            if (!success) {
                Tournaments.LOGGER.warn("Failed to teleport leaving player {} to exit point",
                        player.getName().getString());
            }

            // Mark as eliminated
            eliminatedPlayers.add(playerId);
        }

        participants.remove(playerId);

        // Broadcast leave message
        broadcastActionBar(player.getName().getString() + " has left the tournament");

        // Send leave confirmation to player
        BroadcastUtil.sendTitle(player, "Left Tournament", TextFormatting.YELLOW, 10, 70, 20);
        BroadcastUtil.sendSubtitle(player, name, TextFormatting.YELLOW, 10, 70, 20);

        // If host leaves, assign new host
        if (playerId.equals(hostId) && !participants.isEmpty()) {
            UUID newHostId = participants.keySet().iterator().next();
            // Notify the new host
            ServerPlayerEntity newHost = participants.get(newHostId).getPlayer();
            if (newHost != null) {
                BroadcastUtil.sendTitle(newHost, "You Are Now Host", TextFormatting.GOLD, 10, 70, 20);
                BroadcastUtil.sendSubtitle(newHost, "Previous host left the tournament", TextFormatting.YELLOW, 10, 70, 20);
            }
        }

        // Check if only one participant remains and tournament is in progress
        if (status == TournamentStatus.IN_PROGRESS && participants.size() == 1) {
            // Get the last participant
            TournamentParticipant lastParticipant = participants.values().iterator().next();

            // Announce the last player as winner
            broadcastActionBar("Only one participant remains!");
            broadcastTitle("Tournament Winner", lastParticipant.getPlayerName());

            // End the tournament with this participant as winner
            end();
        }

        return true;
    }

    /**
     * Start the tournament with countdown
     */
    public void start() {
        // Check if we can start
        if (status != TournamentStatus.WAITING) {
            return;
        }

        if (participants.size() < 2) {
            broadcastTitle("Cannot Start Tournament", "Need at least 2 participants");
            return;
        }

        // Change status
        status = TournamentStatus.IN_PROGRESS;
        startedAt = Instant.now();

        // Generate initial tournament bracket
        generateBracket();

        // Announce tournament start with countdown
        broadcastTitle("Tournament Starting", battleFormat + " Format");

        // Run countdown for all participants
        for (TournamentParticipant participant : participants.values()) {
            ServerPlayerEntity player = participant.getPlayer();
            if (player != null && player.isAlive() && player.getServer() != null) {
                BroadcastUtil.runCountdown(player, "Starting in", 5, () -> {
                    // Teleport after countdown if teleports are enabled
                    if (TournamentsConfig.COMMON.enableTeleports.get()) {
                        boolean success = TeleportUtil.teleportToEntryPoint(player);
                        if (!success) {
                            Tournaments.LOGGER.warn("Failed to teleport player {} to tournament entry point",
                                    player.getName().getString());
                            BroadcastUtil.sendActionBar(player, "Failed to teleport to tournament arena. Contact an admin.", TextFormatting.RED);
                        }
                    }
                });
            }
        }

        // Schedule first round matches after 6 seconds (after countdown + teleport)
        ServerPlayerEntity anyPlayer = getAnyPlayer();
        if (anyPlayer != null && anyPlayer.getServer() != null) {
            anyPlayer.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(120, () -> {
                scheduleCurrentRoundMatches();
            }));
        } else {
            // Fallback if we can't get a player or server
            scheduleCurrentRoundMatches();
        }
    }

    /**
     * Get any online player in the tournament for server tasks
     */
    private ServerPlayerEntity getAnyPlayer() {
        for (TournamentParticipant participant : participants.values()) {
            ServerPlayerEntity player = participant.getPlayer();
            if (player != null) {
                return player;
            }
        }
        return null;
    }

    /**
     * Generate tournament bracket based on participants
     */
    private void generateBracket() {
        // Shuffle participants to randomize initial matchups
        List<TournamentParticipant> shuffledParticipants = new ArrayList<>(participants.values());
        Collections.shuffle(shuffledParticipants);

        // Create initial round
        List<TournamentParticipant> currentRoundBracket = new ArrayList<>(shuffledParticipants);
        brackets.add(currentRoundBracket);
        currentRound = 0;

        // Display tournament bracket
        broadcastTitle("Tournament Bracket", "Round 1 Matches");

        // Use a series of delayed action bar messages to show each match
        // This makes the bracket more visible by spacing out notifications
        for (int i = 0; i < currentRoundBracket.size(); i += 2) {
            final int matchIndex = i;
            final int delay = (i / 2) * 40; // 2 seconds between matches for readability

            // Use a delayed task to spread out the messages
            ServerPlayerEntity anyPlayer = getAnyPlayer();
            if (anyPlayer != null && anyPlayer.getServer() != null) {
                anyPlayer.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(
                        delay,
                        () -> {
                            if (matchIndex + 1 < currentRoundBracket.size()) {
                                // Regular match with two players
                                String player1 = currentRoundBracket.get(matchIndex).getPlayerName();
                                String player2 = currentRoundBracket.get(matchIndex + 1).getPlayerName();

                                // Send to all participants
                                for (TournamentParticipant participant : participants.values()) {
                                    ServerPlayerEntity player = participant.getPlayer();
                                    if (player != null) {
                                        // If this is the player's match, send a more prominent notification
                                        if (player.getName().getString().equals(player1) ||
                                                player.getName().getString().equals(player2)) {

                                            // Highlight their match with a title
                                            BroadcastUtil.sendTitle(player,
                                                    "Your Match: #" + ((matchIndex/2) + 1),
                                                    TextFormatting.GOLD, 10, 60, 10);

                                            String opponent = player.getName().getString().equals(player1) ? player2 : player1;
                                            BroadcastUtil.sendSubtitle(player,
                                                    "VS " + opponent,
                                                    TextFormatting.AQUA, 10, 60, 10);
                                        } else {
                                            // For other players, just show the match in action bar
                                            BroadcastUtil.sendActionBar(player,
                                                    "Match " + ((matchIndex/2) + 1) + ": " + player1 + " vs " + player2,
                                                    TextFormatting.GOLD);
                                        }
                                    }
                                }
                            } else {
                                // Bye match with only one player
                                String player = currentRoundBracket.get(matchIndex).getPlayerName();

                                for (TournamentParticipant participant : participants.values()) {
                                    ServerPlayerEntity playerEntity = participant.getPlayer();
                                    if (playerEntity != null) {
                                        if (playerEntity.getName().getString().equals(player)) {
                                            // Special notification for the player getting the bye
                                            BroadcastUtil.sendTitle(playerEntity,
                                                    "You Got a Bye!",
                                                    TextFormatting.GREEN, 10, 60, 10);

                                            BroadcastUtil.sendSubtitle(playerEntity,
                                                    "Automatically advance to next round",
                                                    TextFormatting.YELLOW, 10, 60, 10);
                                        } else {
                                            // For other players
                                            BroadcastUtil.sendActionBar(playerEntity,
                                                    "Match " + ((matchIndex/2) + 1) + ": " + player + " receives a bye",
                                                    TextFormatting.GOLD);
                                        }
                                    }
                                }
                            }
                        }
                ));
            }
        }
    }

    /**
     * Schedule matches for the current round with on-screen notifications
     */
    private void scheduleCurrentRoundMatches() {
        List<TournamentParticipant> currentRoundBracket = brackets.get(currentRound);

        // Clear previous round matches
        matches.clear();

        // Broadcast round start
        broadcastTitle("Round " + (currentRound + 1), "Matches are being scheduled");

        // Create matches for current round
        for (int i = 0; i < currentRoundBracket.size(); i += 2) {
            if (i + 1 < currentRoundBracket.size()) {
                TournamentParticipant player1 = currentRoundBracket.get(i);
                TournamentParticipant player2 = currentRoundBracket.get(i + 1);

                // Create match
                TournamentMatch match = new TournamentMatch(player1, player2);
                matches.add(match);

                // Get player entities
                ServerPlayerEntity p1 = player1.getPlayer();
                ServerPlayerEntity p2 = player2.getPlayer();

                if (p1 != null && p2 != null) {
                    // Announce match to all participants
                    final String matchDescription = match.getDescription();
                    broadcastActionBar("Match scheduled: " + matchDescription);

                    // Teleport players to their match positions
                    boolean p1Teleported = TeleportUtil.teleportToMatchPosition(p1, 1);
                    boolean p2Teleported = TeleportUtil.teleportToMatchPosition(p2, 2);

                    if (p1Teleported && p2Teleported) {
                        Tournaments.LOGGER.info("Teleported {} and {} to their match positions",
                                p1.getName().getString(), p2.getName().getString());
                    } else {
                        Tournaments.LOGGER.warn("Failed to teleport players to match positions");
                    }

                    // Special notification for match participants
                    BroadcastUtil.sendTitle(p1, "Match Started", TextFormatting.GOLD, 10, 60, 20);
                    BroadcastUtil.sendSubtitle(p1, "VS " + p2.getName().getString(), TextFormatting.YELLOW, 10, 60, 20);

                    BroadcastUtil.sendTitle(p2, "Match Started", TextFormatting.GOLD, 10, 60, 20);
                    BroadcastUtil.sendSubtitle(p2, "VS " + p1.getName().getString(), TextFormatting.YELLOW, 10, 60, 20);

                    // After 3 seconds, show the ready command instruction with enhanced visibility
                    if (p1.getServer() != null) {
                        p1.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(60, () -> {
                            // More prominent ready notification with longer duration
                            BroadcastUtil.sendTitle(p1, "Type /tournament ready", TextFormatting.GREEN, 10, 100, 20);
                            BroadcastUtil.sendSubtitle(p1, "to start your match against " + p2.getName().getString(),
                                    TextFormatting.YELLOW, 10, 100, 20);

                            BroadcastUtil.sendTitle(p2, "Type /tournament ready", TextFormatting.GREEN, 10, 100, 20);
                            BroadcastUtil.sendSubtitle(p2, "to start your match against " + p1.getName().getString(),
                                    TextFormatting.YELLOW, 10, 100, 20);

                            // Send periodic reminders if players haven't used the ready command
                            p1.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(200, () -> {
                                // Check if match is still scheduled (players haven't readied up)
                                if (match.getStatus() == TournamentMatch.MatchStatus.SCHEDULED) {
                                    BroadcastUtil.sendNotificationBar(p1, "Remember to type /tournament ready to begin!");
                                    BroadcastUtil.sendNotificationBar(p2, "Remember to type /tournament ready to begin!");
                                }
                            }));
                        }));
                    }
                } else {
                    broadcastActionBar("Could not schedule match: " + match.getDescription() +
                            " - one or both players offline");
                }
            } else {
                // Odd number of players, this player gets a bye to next round
                TournamentParticipant player = currentRoundBracket.get(i);
                broadcastActionBar(player.getPlayerName() + " advances to next round with a bye");

                // Notify the player who got a bye
                ServerPlayerEntity byePlayer = player.getPlayer();
                if (byePlayer != null) {
                    BroadcastUtil.sendTitle(byePlayer, "You Got a Bye", TextFormatting.GREEN, 10, 60, 20);
                    BroadcastUtil.sendSubtitle(byePlayer, "Advancing to next round", TextFormatting.YELLOW, 10, 60, 20);
                }
            }
        }
    }

    /**
     * Mark a player as eliminated
     */
    public void eliminatePlayer(UUID playerId) {
        if (!participants.containsKey(playerId)) {
            Tournaments.LOGGER.warn("Attempted to eliminate player {} who is not in tournament {}",
                    playerId, name);
            return;
        }

        // If already eliminated, don't do it again
        if (eliminatedPlayers.contains(playerId)) {
            Tournaments.LOGGER.debug("Player {} is already eliminated from tournament {}",
                    playerId, name);
            return;
        }

        // Add to eliminated players list
        eliminatedPlayers.add(playerId);
        Tournaments.LOGGER.info("Player {} eliminated from tournament {}", playerId, name);

        // Get the player
        TournamentParticipant participant = participants.get(playerId);
        ServerPlayerEntity player = participant.getPlayer();
        String playerName = participant.getPlayerName();

        // Teleport to exit point if available
        if (player != null && player.isAlive() && TournamentsConfig.COMMON.enableTeleports.get()) {
            Tournaments.LOGGER.info("Teleporting eliminated player {} to exit point", playerName);

            // Show elimination message
            BroadcastUtil.sendTitle(player, "Eliminated", TextFormatting.RED, 10, 70, 20);
            BroadcastUtil.sendSubtitle(player, "You are out of the tournament", TextFormatting.YELLOW, 10, 70, 20);

            boolean success = TeleportUtil.teleportToExitPoint(player);
            if (!success) {
                Tournaments.LOGGER.warn("Failed to teleport eliminated player {} to exit point", playerName);

                // Try again after a short delay
                if (player.getServer() != null) {
                    player.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(20, () -> {
                        Tournaments.LOGGER.info("Attempting delayed teleport for eliminated player {}", playerName);
                        boolean delayedSuccess = TeleportUtil.teleportToExitPoint(player);

                        if (!delayedSuccess) {
                            Tournaments.LOGGER.error("Delayed teleport for eliminated player {} also failed", playerName);
                            BroadcastUtil.sendActionBar(player, "You have been eliminated. Use /spawn to return to spawn.", TextFormatting.RED);
                        }
                    }));
                }
            }
        } else if (player != null) {
            // Notify even if we can't teleport
            BroadcastUtil.sendTitle(player, "Eliminated", TextFormatting.RED, 10, 70, 20);
            BroadcastUtil.sendSubtitle(player, "You are out of the tournament", TextFormatting.YELLOW, 10, 70, 20);
        }

        // Broadcast elimination message to all participants
        broadcastActionBar(playerName + " has been eliminated from the tournament");

        // Check if only one active player remains
        int activePlayersRemaining = participants.size() - eliminatedPlayers.size();
        if (activePlayersRemaining <= 1) {
            // Find the last active player
            TournamentParticipant lastActive = null;
            for (TournamentParticipant p : participants.values()) {
                if (!eliminatedPlayers.contains(p.getPlayerId())) {
                    lastActive = p;
                    break;
                }
            }

            // If we found the last active player, they win
            if (lastActive != null) {
                broadcastActionBar("Only one participant remains active!");
                broadcastTitle("Tournament Winner", lastActive.getPlayerName());

                // Show winning message to the winner
                ServerPlayerEntity winner = lastActive.getPlayer();
                if (winner != null) {
                    BroadcastUtil.sendTitle(winner, "Victory!", TextFormatting.GOLD, 10, 100, 20);
                    BroadcastUtil.sendSubtitle(winner, "You are the tournament champion", TextFormatting.YELLOW, 10, 100, 20);
                }

                end();
            }
        }
    }

    /**
     * Check if a player is eliminated
     */
    public boolean isPlayerEliminated(UUID playerId) {
        return eliminatedPlayers.contains(playerId);
    }

    /**
     * Get the number of eliminated players
     */
    public int getEliminatedPlayerCount() {
        return eliminatedPlayers.size();
    }

    /**
     * Record a match result
     * @param winnerUUID UUID of the winner
     * @param loserUUID UUID of the loser
     * @return True if result was recorded successfully
     */
    public boolean recordMatchResult(UUID winnerUUID, UUID loserUUID) {
        // Find the match
        TournamentMatch matchToUpdate = null;

        for (TournamentMatch match : matches) {
            if (match.hasPlayer(winnerUUID) && match.hasPlayer(loserUUID)) {
                if (match.getStatus() == TournamentMatch.MatchStatus.IN_PROGRESS) {
                    matchToUpdate = match;
                    break;
                } else if (match.getStatus() == TournamentMatch.MatchStatus.SCHEDULED) {
                    // Match was scheduled but never marked as in progress
                    // Let's set it to in progress first
                    match.start();
                    matchToUpdate = match;
                    break;
                }
            }
        }

        if (matchToUpdate == null) {
            Tournaments.LOGGER.warn("No active match found for players {} and {}",
                    participants.containsKey(winnerUUID) ? participants.get(winnerUUID).getPlayerName() : winnerUUID,
                    participants.containsKey(loserUUID) ? participants.get(loserUUID).getPlayerName() : loserUUID);

            // Create a debug log of all matches
            Tournaments.LOGGER.info("Current matches in tournament {}:", name);
            for (TournamentMatch m : matches) {
                Tournaments.LOGGER.info("Match: {} vs {} - Status: {}",
                        m.getPlayer1Name(), m.getPlayer2Name(), m.getStatus());
            }

            // If we couldn't find a match, create a new one
            if (participants.containsKey(winnerUUID) && participants.containsKey(loserUUID)) {
                TournamentParticipant winner = participants.get(winnerUUID);
                TournamentParticipant loser = participants.get(loserUUID);

                matchToUpdate = new TournamentMatch(winner, loser);
                matchToUpdate.start(); // Mark as started
                matches.add(matchToUpdate);

                Tournaments.LOGGER.info("Created new match for result recording: {} vs {}",
                        winner.getPlayerName(), loser.getPlayerName());
            } else {
                return false; // Can't create match if participants not found
            }
        }

        // Mark as completed with winner
        if (matchToUpdate.complete(winnerUUID)) {
            // Update participant stats
            if (participants.containsKey(winnerUUID)) {
                participants.get(winnerUUID).incrementWins();

                // Teleport winner back to entry point
                ServerPlayerEntity winner = participants.get(winnerUUID).getPlayer();
                if (winner != null && TournamentsConfig.COMMON.enableTeleports.get()) {
                    boolean teleported = TeleportUtil.teleportToEntryPoint(winner);
                    if (teleported) {
                        Tournaments.LOGGER.info("Teleported winner {} back to entry point",
                                winner.getName().getString());
                        // Victory message handled in match complete method
                    }
                }
            }

            if (participants.containsKey(loserUUID)) {
                participants.get(loserUUID).incrementLosses();

                // Eliminate the loser - handled separately to ensure proper teleporting
                eliminatePlayer(loserUUID);
            }

            // Broadcast the result using the new method
            String winnerName = participants.containsKey(winnerUUID) ?
                    participants.get(winnerUUID).getPlayerName() : "Unknown";
            String loserName = participants.containsKey(loserUUID) ?
                    participants.get(loserUUID).getPlayerName() : "Unknown";

            // CHANGED from broadcastMessage to broadcastMatchResult
            broadcastMatchResult(winnerName, loserName);

            // Check if round is complete
            checkRoundCompletion();

            return true;
        }

        return false;
    }

    /**
     * Check if current round is complete and advance if needed
     */
    private void checkRoundCompletion() {
        // Check if all matches are completed
        boolean allCompleted = true;
        for (TournamentMatch match : matches) {
            if (match.getStatus() != TournamentMatch.MatchStatus.COMPLETED &&
                    match.getStatus() != TournamentMatch.MatchStatus.CANCELLED) {
                allCompleted = false;
                break;
            }
        }

        if (allCompleted) {
            Tournaments.LOGGER.info("All matches in round {} completed, advancing to next round", currentRound + 1);
            advanceRound();
        } else {
            Tournaments.LOGGER.info("Some matches still in progress, not advancing round yet");
        }
    }

    public void advanceRound() {
        // Determine winners from current round
        List<TournamentParticipant> winners = new ArrayList<>();

        // Get winners from completed matches
        for (TournamentMatch match : matches) {
            if (match.getStatus() == TournamentMatch.MatchStatus.COMPLETED) {
                UUID winnerId = match.getWinnerId();
                if (winnerId != null && participants.containsKey(winnerId)) {
                    winners.add(participants.get(winnerId));
                }
            }
        }

        // Add byes (participants without matches)
        List<TournamentParticipant> currentRoundBracket = brackets.get(currentRound);
        for (TournamentParticipant participant : currentRoundBracket) {
            boolean hasMatch = false;
            for (TournamentMatch match : matches) {
                if (match.hasPlayer(participant.getPlayerId())) {
                    hasMatch = true;
                    break;
                }
            }

            if (!hasMatch) {
                // Participant had a bye, add to winners
                winners.add(participant);
            }
        }

        // If only one winner, end tournament
        if (winners.size() <= 1) {
            if (winners.size() == 1) {
                // Announce the winner
                TournamentParticipant winner = winners.get(0);
                broadcastTitle("Tournament Winner", winner.getPlayerName());

                // Show winning message to the winner
                ServerPlayerEntity winnerPlayer = winner.getPlayer();
                if (winnerPlayer != null) {
                    BroadcastUtil.sendTitle(winnerPlayer, "Victory!", TextFormatting.GOLD, 10, 100, 20);
                    BroadcastUtil.sendSubtitle(winnerPlayer, "You are the tournament champion", TextFormatting.YELLOW, 10, 100, 20);
                }
            }
            end();
            return;
        }

        // Add winners to next round
        brackets.add(winners);
        currentRound++;

        // Announce next round
        broadcastTitle("Round " + (currentRound + 1), winners.size() + " participants remaining");

        // Schedule next round matches with a short delay
        // Use a delayed task to give time for teleportation to complete
        ServerPlayerEntity anyPlayer = getAnyPlayer();
        if (anyPlayer != null && anyPlayer.getServer() != null) {
            anyPlayer.getServer().tell(
                    new net.minecraft.util.concurrent.TickDelayedTask(60, () -> {
                        scheduleCurrentRoundMatches();
                        Tournaments.LOGGER.info("Scheduled matches for round {}", currentRound + 1);
                    })
            );
        } else {
            // Fallback if we can't get a player or server
            scheduleCurrentRoundMatches();
            Tournaments.LOGGER.info("Scheduled matches for round {}", currentRound + 1);
        }
    }

    /**
     * End the tournament
     */
    public void end() {
        if (status == TournamentStatus.ENDED) {
            return;
        }

        status = TournamentStatus.ENDED;
        endedAt = Instant.now();

        // Determine and announce winner
        TournamentParticipant winner = determineOverallWinner();
        if (winner != null) {
            broadcastTitle("Tournament Ended", winner.getPlayerName() + " wins!");

            // Show victory message to winner
            ServerPlayerEntity playerEntity = winner.getPlayer();
            if (playerEntity != null) {
                // Give rewards if enabled
                if (TournamentsConfig.COMMON.enableRewards.get()) {
                    // Implement reward logic here
                    BroadcastUtil.sendActionBar(playerEntity, "You've received tournament rewards!", TextFormatting.GOLD);
                }
            }
        } else {
            broadcastTitle("Tournament Ended", "No clear winner");
        }
    }

    /**
     * Determine the overall tournament winner
     */
    private TournamentParticipant determineOverallWinner() {
        if (brackets.isEmpty()) {
            return null;
        }

        // First check if only one active player remains
        List<TournamentParticipant> activePlayers = new ArrayList<>();
        for (TournamentParticipant participant : participants.values()) {
            if (!eliminatedPlayers.contains(participant.getPlayerId())) {
                activePlayers.add(participant);
            }
        }

        if (activePlayers.size() == 1) {
            return activePlayers.get(0);
        }

        // Otherwise use last round bracket
        List<TournamentParticipant> finalRound = brackets.get(brackets.size() - 1);
        return finalRound.isEmpty() ? null : finalRound.get(0);
    }

    /**
     * Broadcast a message to all tournament participants as a title and subtitle
     * Uses improved styling for better readability
     */
    public void broadcastTitle(String title, String subtitle) {
        for (TournamentParticipant participant : participants.values()) {
            ServerPlayerEntity player = participant.getPlayer();
            if (player != null) {
                BroadcastUtil.sendTitle(player, title, TextFormatting.GOLD, 10, 60, 20);
                if (subtitle != null && !subtitle.isEmpty()) {
                    BroadcastUtil.sendSubtitle(player, subtitle, TextFormatting.YELLOW, 10, 60, 20);
                }
            }
        }
    }

    /**
     * Broadcast a message to all tournament participants as an actionbar message
     * This is the least intrusive notification format
     */
    public void broadcastActionBar(String message) {
        for (TournamentParticipant participant : participants.values()) {
            ServerPlayerEntity player = participant.getPlayer();
            if (player != null) {
                BroadcastUtil.sendActionBar(player, message, TextFormatting.GOLD);
            }
        }
    }

    /**
     * Broadcast an important notification to all participants
     * Uses the notification bar for more visibility
     */
    public void broadcastNotification(String message) {
        for (TournamentParticipant participant : participants.values()) {
            ServerPlayerEntity player = participant.getPlayer();
            if (player != null) {
                BroadcastUtil.sendNotificationBar(player, message);
            }
        }
    }

    /**
     * Broadcast a match result with styled formatting
     * Displays who won the match with appropriate styling
     */
    public void broadcastMatchResult(String winnerName, String loserName) {
        broadcastNotification(winnerName + " has defeated " + loserName);
        broadcastActionBar(winnerName + " advances to the next round!");
    }

    /**
     * Modified broadcast message - now uses ONLY action bar and title notifications
     * No chat messages will be sent
     */
    public void broadcastMessage(String message) {
        // Default to action bar notifications for all messages
        broadcastActionBar(message);
    }

    /**
     * Modified broadcast message - now uses ONLY action bar and title notifications
     * No chat messages will be sent regardless of alsoSendChat parameter
     */
    public void broadcastMessage(String message, boolean alsoSendChat) {
        // Ignore alsoSendChat parameter - we never send to chat now
        broadcastActionBar(message);
    }

    // Getters and utility methods
    public String getName() {
        return name;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public int getParticipantCount() {
        return participants.size();
    }

    public UUID getHostId() {
        return hostId;
    }

    public TournamentStatus getStatus() {
        return status;
    }

    public Collection<TournamentParticipant> getParticipants() {
        return participants.values();
    }

    public List<TournamentMatch> getCurrentMatches() {
        return new ArrayList<>(matches);
    }

    /**
     * Get the scheduled start time for the tournament
     */
    public Instant getScheduledStartTime() {
        return scheduledStartTime;
    }

    /**
     * Set the scheduled start time for the tournament
     */
    public void setScheduledStartTime(Instant time) {
        this.scheduledStartTime = time;
    }

    public enum TournamentStatus {
        WAITING,      // Tournament created, waiting for participants
        IN_PROGRESS,  // Tournament is currently running
        ENDED         // Tournament has concluded
    }
}