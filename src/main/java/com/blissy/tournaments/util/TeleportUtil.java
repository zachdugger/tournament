package com.blissy.tournaments.util;

import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.config.TournamentsConfig;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.UUID;

/**
 * Utility class for teleporting players in tournaments
 */
public class TeleportUtil {

    /**
     * Teleport a player to the tournament entry point
     */
    public static boolean teleportToEntryPoint(ServerPlayerEntity player) {
        if (player == null || !player.isAlive()) {
            return false;
        }

        // Check if teleports are enabled
        if (!TournamentsConfig.COMMON.enableTeleports.get()) {
            player.sendMessage(
                    new StringTextComponent("Tournament teleportation is disabled")
                            .withStyle(TextFormatting.RED),
                    player.getUUID());
            return false;
        }

        // Try to load from saved data first, then config
        TeleportLocation entryLocation = loadLocationFromServerData("tournament_entry");

        if (entryLocation == null) {
            // Use config values as fallback
            entryLocation = new TeleportLocation(
                    TournamentsConfig.COMMON.entryX.get(),
                    TournamentsConfig.COMMON.entryY.get(),
                    TournamentsConfig.COMMON.entryZ.get(),
                    TournamentsConfig.COMMON.entryDimension.get()
            );
        }

        // Teleport the player
        return teleportPlayer(player, entryLocation);
    }

    /**
     * Teleport a player to the tournament exit point
     */
    public static boolean teleportToExitPoint(ServerPlayerEntity player) {
        if (player == null || !player.isAlive()) {
            return false;
        }

        // Check if teleports are enabled
        if (!TournamentsConfig.COMMON.enableTeleports.get()) {
            player.sendMessage(
                    new StringTextComponent("Tournament teleportation is disabled")
                            .withStyle(TextFormatting.RED),
                    player.getUUID());
            return false;
        }

        // Try to load from saved data first, then config
        TeleportLocation exitLocation = loadLocationFromServerData("tournament_exit");

        if (exitLocation == null) {
            // Use config values as fallback
            exitLocation = new TeleportLocation(
                    TournamentsConfig.COMMON.exitX.get(),
                    TournamentsConfig.COMMON.exitY.get(),
                    TournamentsConfig.COMMON.exitZ.get(),
                    TournamentsConfig.COMMON.exitDimension.get()
            );
        }

        // Teleport the player
        return teleportPlayer(player, exitLocation);
    }

    /**
     * Teleport a player to a tournament match position
     * @param player The player to teleport
     * @param position The position (1 or 2)
     * @return true if teleportation was successful
     */
    public static boolean teleportToMatchPosition(ServerPlayerEntity player, int position) {
        if (player == null || !player.isAlive()) {
            return false;
        }

        // Check if teleports are enabled
        if (!TournamentsConfig.COMMON.enableTeleports.get()) {
            player.sendMessage(
                    new StringTextComponent("Tournament teleportation is disabled")
                            .withStyle(TextFormatting.RED),
                    player.getUUID());
            return false;
        }

        // Try to load from saved data first, then config
        String positionKey = "tournament_match_pos" + position;
        TeleportLocation matchLocation = loadLocationFromServerData(positionKey);

        if (matchLocation == null) {
            // Try legacy key format
            matchLocation = loadLocationFromServerData("match_position_" + position);
        }

        if (matchLocation == null) {
            // Use entry location offset as fallback
            double entryX = TournamentsConfig.COMMON.entryX.get();
            double entryY = TournamentsConfig.COMMON.entryY.get();
            double entryZ = TournamentsConfig.COMMON.entryZ.get();
            String entryDimension = TournamentsConfig.COMMON.entryDimension.get();

            // Position 1 is at entry coordinates, position 2 is offset
            if (position == 1) {
                matchLocation = new TeleportLocation(entryX, entryY, entryZ, entryDimension);
            } else {
                // Position 2 is 10 blocks in the X direction from position 1
                matchLocation = new TeleportLocation(entryX + 10, entryY, entryZ, entryDimension);
            }

            Tournaments.LOGGER.warn("No saved match position {}. Using fallback location at ({}, {}, {})",
                    position, matchLocation.x, matchLocation.y, matchLocation.z);
        }

        // Teleport the player - don't send a message here, it's included in the match announcement
        return teleportPlayer(player, matchLocation);
    }

    /**
     * Ensure that match positions are set up
     * This creates default positions if they don't exist yet
     */
    public static void ensureMatchPositionsExist() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Get data file
        File dataDir = new File(server.getServerDirectory(), "data");
        File locationFile = new File(dataDir, "tournaments_locations.dat");

        // Create directory if needed
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        // Create or load existing data
        CompoundNBT root = new CompoundNBT();
        if (locationFile.exists()) {
            try (FileInputStream stream = new FileInputStream(locationFile)) {
                root = CompressedStreamTools.readCompressed(stream);
            } catch (Exception e) {
                Tournaments.LOGGER.error("Failed to read locations file", e);
                // Create new if failed to read
                root = new CompoundNBT();
            }
        }

        boolean needsUpdate = false;

        // Check if match positions exist
        if (!root.contains("match_position_1")) {
            // Create match position 1
            double entryX = TournamentsConfig.COMMON.entryX.get();
            double entryY = TournamentsConfig.COMMON.entryY.get();
            double entryZ = TournamentsConfig.COMMON.entryZ.get();
            String entryDimension = TournamentsConfig.COMMON.entryDimension.get();

            CompoundNBT position1 = new CompoundNBT();
            position1.putDouble("x", entryX);
            position1.putDouble("y", entryY);
            position1.putDouble("z", entryZ);
            position1.putString("dimension", entryDimension);

            root.put("match_position_1", position1);
            needsUpdate = true;

            Tournaments.LOGGER.info("Created default match position 1 at ({}, {}, {}) in {}",
                    entryX, entryY, entryZ, entryDimension);
        }

        if (!root.contains("match_position_2")) {
            // Create match position 2
            double entryX = TournamentsConfig.COMMON.entryX.get();
            double entryY = TournamentsConfig.COMMON.entryY.get();
            double entryZ = TournamentsConfig.COMMON.entryZ.get();
            String entryDimension = TournamentsConfig.COMMON.entryDimension.get();

            CompoundNBT position2 = new CompoundNBT();
            position2.putDouble("x", entryX + 10);  // Position 2 is 10 blocks away
            position2.putDouble("y", entryY);
            position2.putDouble("z", entryZ);
            position2.putString("dimension", entryDimension);

            root.put("match_position_2", position2);
            needsUpdate = true;

            Tournaments.LOGGER.info("Created default match position 2 at ({}, {}, {}) in {}",
                    entryX + 10, entryY, entryZ, entryDimension);
        }

        // Save the data if needed
        if (needsUpdate) {
            try (FileOutputStream stream = new FileOutputStream(locationFile)) {
                CompressedStreamTools.writeCompressed(root, stream);
                Tournaments.LOGGER.info("Saved tournament location data with match positions");
            } catch (Exception e) {
                Tournaments.LOGGER.error("Failed to save locations file", e);
            }
        }
    }

    /**
     * Teleport a player to the specified location
     */
    private static boolean teleportPlayer(ServerPlayerEntity player, TeleportLocation location) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        try {
            // Parse dimension
            ResourceLocation dimensionRL = new ResourceLocation(location.dimension);
            RegistryKey<World> dimensionKey = RegistryKey.create(Registry.DIMENSION_REGISTRY, dimensionRL);
            ServerWorld targetWorld = server.getLevel(dimensionKey);

            if (targetWorld == null) {
                Tournaments.LOGGER.error("Failed to find dimension: {}", location.dimension);
                return false;
            }

            // Check if player is already in the right dimension
            boolean sameDimension = player.level.dimension().equals(dimensionKey);

            // Prepare teleport position
            Vector3d position = new Vector3d(location.x, location.y, location.z);
            float yRot = player.yRot;
            float xRot = player.xRot;

            // Store the teleport location in player data
            UUID playerId = player.getUUID();
            player.getPersistentData().putDouble("LastX", player.getX());
            player.getPersistentData().putDouble("LastY", player.getY());
            player.getPersistentData().putDouble("LastZ", player.getZ());
            player.getPersistentData().putString("LastDimension", player.level.dimension().location().toString());

            // Teleport to target position
            if (sameDimension) {
                // Same dimension teleport
                player.teleportTo(location.x, location.y, location.z);
            } else {
                // Cross-dimension teleport
                player.teleportTo(targetWorld,
                        location.x, location.y, location.z,
                        yRot, xRot);
            }

            return true;
        } catch (Exception e) {
            Tournaments.LOGGER.error("Error teleporting player", e);
            return false;
        }
    }

    /**
     * Load a location from the server data file
     */
    private static TeleportLocation loadLocationFromServerData(String id) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;

            // Get data file
            File dataDir = new File(server.getServerDirectory(), "data");
            File locationFile = new File(dataDir, "tournaments_locations.dat");

            // If file doesn't exist, return null
            if (!locationFile.exists()) {
                return null;
            }

            // Load NBT data
            CompoundNBT root;
            try (FileInputStream stream = new FileInputStream(locationFile)) {
                root = CompressedStreamTools.readCompressed(stream);
            }

            // Get location data
            if (root.contains(id)) {
                CompoundNBT locationData = root.getCompound(id);
                double x = locationData.getDouble("x");
                double y = locationData.getDouble("y");
                double z = locationData.getDouble("z");
                String dimension = locationData.getString("dimension");

                return new TeleportLocation(x, y, z, dimension);
            }

            return null;
        } catch (Exception e) {
            Tournaments.LOGGER.error("Failed to load location data", e);
            return null;
        }
    }

    /**
     * Simple class to represent a teleport location
     */
    private static class TeleportLocation {
        final double x;
        final double y;
        final double z;
        final String dimension;

        public TeleportLocation(double x, double y, double z, String dimension) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
        }
    }
}