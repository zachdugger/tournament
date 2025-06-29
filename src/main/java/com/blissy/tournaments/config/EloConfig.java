package com.blissy.tournaments.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

public class EloConfig {
    public static final ForgeConfigSpec SPEC;
    public static final K_FACTOR K_FACTOR;
    public static final ForgeConfigSpec.IntValue REWARD_SLOTS;
    // Change this to store the config values instead of the actual values
    private static final Map<String, ForgeConfigSpec.ConfigValue<String>> REWARD_CONFIG_VALUES = new HashMap<>();

    static {
        final Pair<K_FACTOR, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder()
                .configure(K_FACTOR::new);

        SPEC = specPair.getRight();
        K_FACTOR = specPair.getLeft();

        // Create a builder for additional config
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("elo");

        // Define reward slots
        REWARD_SLOTS = builder
                .comment("Number of top players to reward")
                .defineInRange("reward_slots", 3, 1, 10);

        // Define rewards
        for (int i = 1; i <= 6; i++) {
            String defaultReward = "give {player} pixelmon:master_ball " + i;
            String rewardKey = "reward_" + i;

            // Store ConfigValue objects instead of calling get()
            ForgeConfigSpec.ConfigValue<String> rewardValue = builder
                    .comment("Reward command for place " + i)
                    .define(rewardKey, defaultReward);

            // Store the ConfigValue instead of its value
            REWARD_CONFIG_VALUES.put(rewardKey, rewardValue);
        }

        builder.pop();
    }

    // Add this method to get rewards when needed
    public static Map<String, String> getRewards() {
        Map<String, String> rewards = new HashMap<>();
        for (Map.Entry<String, ForgeConfigSpec.ConfigValue<String>> entry : REWARD_CONFIG_VALUES.entrySet()) {
            rewards.put(entry.getKey(), entry.getValue().get());
        }
        return rewards;
    }

    public static class K_FACTOR {
        public static ForgeConfigSpec.IntValue FACTOR;

        // Keep the original constructor that takes a Builder
        K_FACTOR(ForgeConfigSpec.Builder builder) {
            builder.comment("ELO Settings")
                    .push("elo");

            FACTOR = builder
                    .comment("K-Factor used in ELO calculation. Higher = more points gained/lost per match.")
                    .defineInRange("k_factor", 32, 1, 200);

            builder.pop();
        }

        public static int get() {
            return FACTOR.get();
        }
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "tournaments-elo.toml");
    }
}