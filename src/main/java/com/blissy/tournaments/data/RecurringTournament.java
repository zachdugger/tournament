package com.blissy.tournaments.data;

import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.Tournaments;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a recurring tournament that will be automatically recreated at specified intervals
 */
public class RecurringTournament {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String RECURRING_FILE = "config/tournaments/recurring_tournaments.json";
    private static final Map<String, RecurringTournament> recurringTournaments = new HashMap<>();

    private final String name;
    private final String templateName;
    private final int minLevel;
    private final int maxLevel;
    private final int maxParticipants;
    private final String format;
    private final double entryFee;
    private final double recurrenceHours;
    private final UUID creatorId;
    private Instant lastCreated;
    private Instant nextScheduled;

    public RecurringTournament(String name, String templateName, int minLevel, int maxLevel, int maxParticipants,
                               String format, double entryFee, double recurrenceHours, UUID creatorId) {
        this.name = name;
        this.templateName = templateName;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.maxParticipants = maxParticipants;
        this.format = format;
        this.entryFee = entryFee;
        this.recurrenceHours = recurrenceHours;
        this.creatorId = creatorId;
        this.lastCreated = Instant.now();
        this.nextScheduled = calculateNextScheduled();
    }

    /**
     * Calculate the next scheduled time for this recurring tournament
     */
    private Instant calculateNextScheduled() {
        return lastCreated.plus(Duration.ofMillis((long)(recurrenceHours * 60 * 60 * 1000)));
    }

    /**
     * Create a new instance of this tournament if it's time
     * @return true if a new tournament was created
     */
    public boolean checkAndCreateTournament() {
        Instant now = Instant.now();
        if (now.isAfter(nextScheduled)) {
            // It's time to create a new tournament
            createNewInstance();
            lastCreated = now;
            nextScheduled = calculateNextScheduled();
            saveRecurringTournaments();
            return true;
        }
        return false;
    }

    /**
     * Create a new instance of this tournament
     */
    private void createNewInstance() {
        TournamentManager manager = TournamentManager.getInstance();

        // Get creator player if online
        ServerPlayerEntity creator = null;
        try {
            creator = net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer()
                    .getPlayerList().getPlayer(creatorId);
        } catch (Exception e) {
            Tournaments.LOGGER.warn("Creator for recurring tournament {} not found", name);
        }

        // If creator is not online, use a random online player with admin permissions
        if (creator == null) {
            List<ServerPlayerEntity> admins = new ArrayList<>();
            for (ServerPlayerEntity player : net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer()
                    .getPlayerList().getPlayers()) {
                if (player.hasPermissions(2)) {
                    admins.add(player);
                }
            }

            if (!admins.isEmpty()) {
                creator = admins.get(0);
            } else {
                Tournaments.LOGGER.error("Cannot create recurring tournament {} - no admin players online", name);
                return;
            }
        }

        // Generate a unique name with timestamp
        String uniqueName = templateName + "_" + System.currentTimeMillis();

        // Create the tournament
        if (manager.createTournament(uniqueName, maxParticipants, creator)) {
            // Set tournament settings
            manager.setTournamentSettings(uniqueName, minLevel, maxLevel, format);

            // Store entry fee in tournament settings
            CompoundNBT extraSettings = new CompoundNBT();
            extraSettings.putDouble("entryFee", entryFee);
            extraSettings.putBoolean("isRecurring", true);
            extraSettings.putString("recurringId", name);
            manager.setTournamentExtraSettings(uniqueName, extraSettings);

            // Set a scheduled start time - 10 minutes after creation
            manager.setTournamentScheduledStart(uniqueName, 0.16); // 0.16 hours = about 10 minutes

            Tournaments.LOGGER.info("Created recurring tournament instance: {} (template: {}) - scheduled to start in 10 minutes",
                    uniqueName, name);

            // Broadcast message to all online players - single consolidated message
            net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().forEach(
                    player -> player.sendMessage(
                            new net.minecraft.util.text.StringTextComponent("New tournament started: " + uniqueName + " (starts in 10 minutes, type /tournament join " + uniqueName + " to join)")
                                    .withStyle(net.minecraft.util.text.TextFormatting.GREEN),
                            player.getUUID()
                    )
            );
        } else {
            Tournaments.LOGGER.error("Failed to create recurring tournament instance: {}", name);
        }
    }

    /**
     * Add a new recurring tournament
     */
    public static void addRecurringTournament(RecurringTournament tournament) {
        recurringTournaments.put(tournament.name, tournament);
        saveRecurringTournaments();
    }

    /**
     * Get a recurring tournament by name
     */
    public static RecurringTournament getRecurringTournament(String name) {
        return recurringTournaments.get(name);
    }

    /**
     * Delete a recurring tournament
     */
    public static boolean deleteRecurringTournament(String name) {
        if (recurringTournaments.containsKey(name)) {
            recurringTournaments.remove(name);
            saveRecurringTournaments();
            return true;
        }
        return false;
    }

    /**
     * Get all recurring tournaments
     */
    public static List<RecurringTournament> getAllRecurringTournaments() {
        return new ArrayList<>(recurringTournaments.values());
    }

    /**
     * Check all recurring tournaments and create new instances if needed
     */
    public static void checkAllRecurringTournaments() {
        for (RecurringTournament tournament : new ArrayList<>(recurringTournaments.values())) {
            try {
                tournament.checkAndCreateTournament();
            } catch (Exception e) {
                Tournaments.LOGGER.error("Error checking recurring tournament {}: {}",
                        tournament.getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Save recurring tournaments to file
     */
    public static void saveRecurringTournaments() {
        try {
            File file = new File(RECURRING_FILE);
            File dir = file.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(recurringTournaments.values(), writer);
                Tournaments.LOGGER.info("Saved {} recurring tournaments to {}",
                        recurringTournaments.size(), RECURRING_FILE);
            }
        } catch (IOException e) {
            Tournaments.LOGGER.error("Failed to save recurring tournaments", e);
        }
    }

    /**
     * Load recurring tournaments from file
     */
    public static void loadRecurringTournaments() {
        File file = new File(RECURRING_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<RecurringTournament>>() {}.getType();
                List<RecurringTournament> tournaments = GSON.fromJson(reader, listType);

                recurringTournaments.clear();

                if (tournaments != null) {
                    for (RecurringTournament tournament : tournaments) {
                        recurringTournaments.put(tournament.name, tournament);
                    }
                    Tournaments.LOGGER.info("Loaded {} recurring tournaments", recurringTournaments.size());
                } else {
                    Tournaments.LOGGER.info("No recurring tournaments found in the save file");
                }
            } catch (IOException e) {
                Tournaments.LOGGER.error("Failed to load recurring tournaments", e);
            } catch (Exception e) {
                Tournaments.LOGGER.error("Error parsing recurring tournaments file", e);
            }
        } else {
            Tournaments.LOGGER.info("No recurring tournaments file found at {}", RECURRING_FILE);
        }
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getTemplateName() {
        return templateName;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public String getFormat() {
        return format;
    }

    public double getEntryFee() {
        return entryFee;
    }

    public double getRecurrenceHours() {
        return recurrenceHours;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public Instant getLastCreated() {
        return lastCreated;
    }

    public Instant getNextScheduled() {
        return nextScheduled;
    }
}