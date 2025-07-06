package com.blissy.tournaments.data;

import com.blissy.tournaments.Tournaments;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Handles caching of player skins for the tournament system
 * Stores complete GameProfile data including skin textures for tournament participants
 */
public class SkinCacheHandler {
    private static final String DATA_FOLDER = "config/tournaments/";
    private static final String SKIN_CACHE_FILE = DATA_FOLDER + "skin_cache.json";
    private static final String SKIN_BACKUP_FILE = DATA_FOLDER + "skin_cache_backup.json";
    private static final String SKIN_TEMP_FILE = DATA_FOLDER + "skin_cache_temp.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Thread-safe access control
    private static final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();

    // Cache stored profiles
    private static Map<UUID, CachedProfile> profileCache = new HashMap<>();
    private static boolean hasUnsavedChanges = false;
    private static long lastSaveTime = 0;
    private static final long MIN_SAVE_INTERVAL_MS = 10000; // 10 seconds minimum between saves

    /**
     * Represents a cached player profile with skin data
     */
    public static class CachedProfile {
        private String name;
        private UUID uuid;
        private Map<String, CachedProperty> properties;
        private long cachedAt;
        private String version = "1.1.0";

        public CachedProfile() {
            // Default constructor for GSON
        }

        public CachedProfile(GameProfile profile) {
            this.name = profile.getName();
            this.uuid = profile.getId();
            this.cachedAt = System.currentTimeMillis();
            this.properties = new HashMap<>();

            // Store all properties (including skin textures)
            for (Map.Entry<String, Collection<Property>> entry : profile.getProperties().asMap().entrySet()) {
                for (Property property : entry.getValue()) {
                    this.properties.put(entry.getKey(), new CachedProperty(property));
                }
            }
        }

        public String getName() { return name; }
        public UUID getUuid() { return uuid; }
        public long getCachedAt() { return cachedAt; }
        public String getVersion() { return version; }

        /**
         * Convert back to GameProfile
         */
        public GameProfile toGameProfile() {
            GameProfile profile = new GameProfile(uuid, name);

            // Restore properties
            if (properties != null) {
                for (Map.Entry<String, CachedProperty> entry : properties.entrySet()) {
                    CachedProperty cachedProp = entry.getValue();
                    Property property = new Property(cachedProp.getName(), cachedProp.getValue(), cachedProp.getSignature());
                    profile.getProperties().put(entry.getKey(), property);
                }
            }

            return profile;
        }

        public boolean isValid() {
            return name != null && uuid != null && cachedAt > 0 && version != null;
        }
    }

    /**
     * Represents a cached property (like skin texture)
     */
    public static class CachedProperty {
        private String name;
        private String value;
        private String signature;

        public CachedProperty() {
            // Default constructor for GSON
        }

        public CachedProperty(Property property) {
            this.name = property.getName();
            this.value = property.getValue();
            this.signature = property.getSignature();
        }

        public String getName() { return name; }
        public String getValue() { return value; }
        public String getSignature() { return signature; }
    }

    /**
     * Load cached skin data from disk
     */
    public static void loadCache() {
        cacheLock.writeLock().lock();
        try {
            File dataFolder = new File(DATA_FOLDER);
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File cacheFile = new File(SKIN_CACHE_FILE);
            boolean loadedSuccessfully = false;

            // Try to load the main file first
            if (cacheFile.exists()) {
                try {
                    loadedSuccessfully = attemptToLoadFile(cacheFile, "main skin cache file");
                } catch (Exception e) {
                    Tournaments.LOGGER.error("Failed to load main skin cache file", e);
                }
            }

            // If main file failed, try backup
            if (!loadedSuccessfully) {
                File backupFile = new File(SKIN_BACKUP_FILE);
                if (backupFile.exists()) {
                    try {
                        loadedSuccessfully = attemptToLoadFile(backupFile, "backup skin cache file");
                        if (loadedSuccessfully) {
                            Tournaments.LOGGER.info("Successfully recovered skin cache from backup file");
                        }
                    } catch (Exception e) {
                        Tournaments.LOGGER.error("Failed to load backup skin cache file", e);
                    }
                }
            }

            // If both failed, start fresh
            if (!loadedSuccessfully) {
                Tournaments.LOGGER.info("No valid skin cache file found. Starting fresh.");
                profileCache = new HashMap<>();
            }

            // Validate and clean loaded data
            validateAndCleanCache();

            Tournaments.LOGGER.info("Loaded skin cache for {} players", profileCache.size());

        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Attempt to load cache from a specific file
     */
    private static boolean attemptToLoadFile(File file, String description) {
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<UUID, CachedProfile>>(){}.getType();
            Map<UUID, CachedProfile> loadedCache = GSON.fromJson(reader, type);

            if (loadedCache != null) {
                profileCache = loadedCache;
                Tournaments.LOGGER.info("Loaded skin cache for {} players from {}.", profileCache.size(), description);
                return true;
            } else {
                Tournaments.LOGGER.warn("Skin cache file {} exists but contains no data.", description);
                return false;
            }
        } catch (Exception e) {
            Tournaments.LOGGER.error("Failed to load skin cache from {}", description, e);
            return false;
        }
    }

    /**
     * Validate and clean loaded cache data
     */
    private static void validateAndCleanCache() {
        cacheLock.writeLock().lock();
        try {
            Iterator<Map.Entry<UUID, CachedProfile>> iterator = profileCache.entrySet().iterator();
            int cleaned = 0;

            while (iterator.hasNext()) {
                Map.Entry<UUID, CachedProfile> entry = iterator.next();
                CachedProfile profile = entry.getValue();

                if (!profile.isValid()) {
                    iterator.remove();
                    cleaned++;
                    Tournaments.LOGGER.warn("Removed invalid cached profile for UUID: {}", entry.getKey());
                }
            }

            if (cleaned > 0) {
                Tournaments.LOGGER.info("Cleaned {} invalid skin cache entries", cleaned);
                markCacheChanged();
            }

        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Save skin cache to disk (with rate limiting)
     */
    public static void saveCache() {
        long currentTime = System.currentTimeMillis();

        // Rate limit saves
        if (!hasUnsavedChanges || (currentTime - lastSaveTime) < MIN_SAVE_INTERVAL_MS) {
            return;
        }

        saveCacheImmediately();
    }

    /**
     * Save skin cache to disk immediately
     */
    public static void saveCacheImmediately() {
        cacheLock.readLock().lock();
        try {
            File dataFolder = new File(DATA_FOLDER);
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            // Create backup before saving
            createBackup();

            // Use atomic save: write to temp file, then move
            File tempFile = new File(SKIN_TEMP_FILE);
            File cacheFile = new File(SKIN_CACHE_FILE);

            try (FileWriter writer = new FileWriter(tempFile)) {
                GSON.toJson(profileCache, writer);
                writer.flush();
            }

            // Atomic move
            Files.move(tempFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            lastSaveTime = System.currentTimeMillis();
            hasUnsavedChanges = false;

            Tournaments.LOGGER.debug("Saved skin cache for {} players.", profileCache.size());

        } catch (IOException e) {
            Tournaments.LOGGER.error("Failed to save skin cache", e);
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Create a backup of the current cache file
     */
    private static void createBackup() {
        File cacheFile = new File(SKIN_CACHE_FILE);
        File backupFile = new File(SKIN_BACKUP_FILE);

        if (cacheFile.exists()) {
            try {
                Files.copy(cacheFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Tournaments.LOGGER.warn("Failed to create skin cache backup file", e);
            }
        }
    }

    /**
     * Mark that cache data has changed and needs to be saved
     */
    private static void markCacheChanged() {
        hasUnsavedChanges = true;
    }

    /**
     * Cache a player's profile if they don't already have one cached
     */
    public static void cachePlayerProfile(ServerPlayerEntity player) {
        cachePlayerProfile(player.getGameProfile());
    }

    /**
     * Cache a GameProfile
     */
    public static void cachePlayerProfile(GameProfile profile) {
        if (profile == null || profile.getId() == null) {
            return;
        }

        cacheLock.writeLock().lock();
        try {
            UUID uuid = profile.getId();

            // Check if we already have a recent cache entry
            CachedProfile existing = profileCache.get(uuid);
            if (existing != null) {
                // Update if the profile has more properties or is newer
                GameProfile existingProfile = existing.toGameProfile();
                if (profile.getProperties().size() > existingProfile.getProperties().size()) {
                    Tournaments.LOGGER.debug("Updating cached profile for {} with more complete data", profile.getName());
                    profileCache.put(uuid, new CachedProfile(profile));
                    markCacheChanged();
                }
                return;
            }

            // Cache the new profile
            profileCache.put(uuid, new CachedProfile(profile));
            markCacheChanged();

            Tournaments.LOGGER.info("Cached skin profile for player: {} ({})", profile.getName(), uuid);

            // Save with rate limiting
            saveCache();

        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Get a cached profile for a player
     */
    public static GameProfile getCachedProfile(UUID uuid) {
        cacheLock.readLock().lock();
        try {
            CachedProfile cached = profileCache.get(uuid);
            if (cached != null && cached.isValid()) {
                return cached.toGameProfile();
            }
            return null;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Update cache for tournament participants
     * This should be called when players join tournaments
     */
    public static void updateTournamentParticipantsCache(Collection<TournamentParticipant> participants) {
        MinecraftServer server = net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        int cached = 0;
        for (TournamentParticipant participant : participants) {
            UUID uuid = participant.getPlayerId();

            // Check if player is online and cache their current profile
            ServerPlayerEntity onlinePlayer = server.getPlayerList().getPlayer(uuid);
            if (onlinePlayer != null) {
                cachePlayerProfile(onlinePlayer.getGameProfile());
                cached++;
            } else {
                // Try to get from server's profile cache if not in our cache
                if (getCachedProfile(uuid) == null) {
                    GameProfile serverProfile = server.getProfileCache().get(uuid);
                    if (serverProfile != null && serverProfile.getProperties().size() > 0) {
                        cachePlayerProfile(serverProfile);
                        cached++;
                    }
                }
            }
        }

        if (cached > 0) {
            Tournaments.LOGGER.debug("Updated skin cache for {} tournament participants", cached);
        }
    }

    /**
     * Get the best available profile for a UUID (cached or server)
     */
    public static GameProfile getBestProfile(UUID uuid, MinecraftServer server) {
        // First try online player
        if (server != null) {
            ServerPlayerEntity onlinePlayer = server.getPlayerList().getPlayer(uuid);
            if (onlinePlayer != null) {
                GameProfile profile = onlinePlayer.getGameProfile();
                // Cache it for future use
                cachePlayerProfile(profile);
                return profile;
            }
        }

        // Try our cache
        GameProfile cachedProfile = getCachedProfile(uuid);
        if (cachedProfile != null) {
            return cachedProfile;
        }

        // Fallback to server's profile cache
        if (server != null) {
            return server.getProfileCache().get(uuid);
        }

        return null;
    }

    /**
     * Force save all pending cache data
     */
    public static void forceSave() {
        if (hasUnsavedChanges) {
            Tournaments.LOGGER.info("Force saving skin cache...");
            saveCacheImmediately();
        }
    }

    /**
     * Get cache statistics
     */
    public static Map<String, Object> getCacheStats() {
        cacheLock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("cachedProfiles", profileCache.size());
            stats.put("hasUnsavedChanges", hasUnsavedChanges);
            stats.put("lastSaveTime", lastSaveTime);
            stats.put("cacheFileExists", new File(SKIN_CACHE_FILE).exists());
            stats.put("backupFileExists", new File(SKIN_BACKUP_FILE).exists());

            // Count profiles with skin data
            long profilesWithSkins = profileCache.values().stream()
                    .filter(profile -> profile.properties != null && profile.properties.containsKey("textures"))
                    .count();
            stats.put("profilesWithSkins", profilesWithSkins);

            return stats;
        } finally {
            cacheLock.readLock().unlock();
        }
    }
}