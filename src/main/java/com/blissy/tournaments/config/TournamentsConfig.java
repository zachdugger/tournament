package com.blissy.tournaments.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

public class TournamentsConfig {
    public static class Common {
        public final ForgeConfigSpec.IntValue maxParticipants;
        public final ForgeConfigSpec.BooleanValue enableRewards;
        public final ForgeConfigSpec.IntValue battleTimeoutSeconds;
        public final ForgeConfigSpec.BooleanValue enableTeleports;

        // Entry point teleport coordinates
        public final ForgeConfigSpec.DoubleValue entryX;
        public final ForgeConfigSpec.DoubleValue entryY;
        public final ForgeConfigSpec.DoubleValue entryZ;
        public final ForgeConfigSpec.ConfigValue<String> entryDimension;

        // Exit point teleport coordinates
        public final ForgeConfigSpec.DoubleValue exitX;
        public final ForgeConfigSpec.DoubleValue exitY;
        public final ForgeConfigSpec.DoubleValue exitZ;
        public final ForgeConfigSpec.ConfigValue<String> exitDimension;

        // Match position 1 teleport coordinates
        public final ForgeConfigSpec.DoubleValue matchPos1X;
        public final ForgeConfigSpec.DoubleValue matchPos1Y;
        public final ForgeConfigSpec.DoubleValue matchPos1Z;
        public final ForgeConfigSpec.ConfigValue<String> matchPos1Dimension;

        // Match position 2 teleport coordinates
        public final ForgeConfigSpec.DoubleValue matchPos2X;
        public final ForgeConfigSpec.DoubleValue matchPos2Y;
        public final ForgeConfigSpec.DoubleValue matchPos2Z;
        public final ForgeConfigSpec.ConfigValue<String> matchPos2Dimension;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Tournament Settings")
                    .push("tournaments");

            maxParticipants = builder
                    .comment("Maximum number of participants allowed in a tournament")
                    .defineInRange("maxParticipants", 32, 2, 128);

            enableRewards = builder
                    .comment("Whether tournament winners receive rewards")
                    .define("enableRewards", true);

            battleTimeoutSeconds = builder
                    .comment("Time in seconds before a battle is considered timed out")
                    .defineInRange("battleTimeoutSeconds", 300, 60, 3600);

            enableTeleports = builder
                    .comment("Whether tournament teleportation is enabled")
                    .define("enableTeleports", true);

            builder.comment("Teleport Settings")
                    .push("teleports");

            entryX = builder
                    .comment("X coordinate for the tournament entry point (spectator area)")
                    .defineInRange("entryX", 0.0, -30000000.0, 30000000.0);

            entryY = builder
                    .comment("Y coordinate for the tournament entry point (spectator area)")
                    .defineInRange("entryY", 64.0, 0.0, 256.0);

            entryZ = builder
                    .comment("Z coordinate for the tournament entry point (spectator area)")
                    .defineInRange("entryZ", 0.0, -30000000.0, 30000000.0);

            entryDimension = builder
                    .comment("Dimension for the tournament entry point (e.g., minecraft:overworld)")
                    .define("entryDimension", "minecraft:overworld");

            exitX = builder
                    .comment("X coordinate for the tournament exit point")
                    .defineInRange("exitX", 0.0, -30000000.0, 30000000.0);

            exitY = builder
                    .comment("Y coordinate for the tournament exit point")
                    .defineInRange("exitY", 64.0, 0.0, 256.0);

            exitZ = builder
                    .comment("Z coordinate for the tournament exit point")
                    .defineInRange("exitZ", 0.0, -30000000.0, 30000000.0);

            exitDimension = builder
                    .comment("Dimension for the tournament exit point (e.g., minecraft:overworld)")
                    .define("exitDimension", "minecraft:overworld");

            // Define match position 1 coordinates
            matchPos1X = builder
                    .comment("X coordinate for the first match position (where player 1 stands to battle)")
                    .defineInRange("matchPos1X", 0.0, -30000000.0, 30000000.0);

            matchPos1Y = builder
                    .comment("Y coordinate for the first match position (where player 1 stands to battle)")
                    .defineInRange("matchPos1Y", 64.0, 0.0, 256.0);

            matchPos1Z = builder
                    .comment("Z coordinate for the first match position (where player 1 stands to battle)")
                    .defineInRange("matchPos1Z", 0.0, -30000000.0, 30000000.0);

            matchPos1Dimension = builder
                    .comment("Dimension for the first match position (e.g., minecraft:overworld)")
                    .define("matchPos1Dimension", "minecraft:overworld");

            // Define match position 2 coordinates
            matchPos2X = builder
                    .comment("X coordinate for the second match position (where player 2 stands to battle)")
                    .defineInRange("matchPos2X", 0.0, -30000000.0, 30000000.0);

            matchPos2Y = builder
                    .comment("Y coordinate for the second match position (where player 2 stands to battle)")
                    .defineInRange("matchPos2Y", 64.0, 0.0, 256.0);

            matchPos2Z = builder
                    .comment("Z coordinate for the second match position (where player 2 stands to battle)")
                    .defineInRange("matchPos2Z", 0.0, -30000000.0, 30000000.0);

            matchPos2Dimension = builder
                    .comment("Dimension for the second match position (e.g., minecraft:overworld)")
                    .define("matchPos2Dimension", "minecraft:overworld");

            builder.pop(); // Pop teleports

            builder.pop(); // Pop tournaments
        }
    }

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        final Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder()
                .configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    }
}