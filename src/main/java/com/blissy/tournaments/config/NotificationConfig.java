package com.blissy.tournaments.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration for tournament notifications
 * Controls the appearance and timing of on-screen notifications
 */
public class NotificationConfig {
    public static class Notifications {
        // Display durations
        public final ForgeConfigSpec.IntValue titleDuration;
        public final ForgeConfigSpec.IntValue subtitleDuration;
        public final ForgeConfigSpec.IntValue actionBarDuration;

        // Fade timings
        public final ForgeConfigSpec.IntValue titleFadeIn;
        public final ForgeConfigSpec.IntValue titleFadeOut;

        // Style options
        public final ForgeConfigSpec.BooleanValue useBoldForTitles;
        public final ForgeConfigSpec.BooleanValue useItalicForSubtitles;

        // Notification types
        public final ForgeConfigSpec.BooleanValue useActionBarForMatches;
        public final ForgeConfigSpec.BooleanValue useMinimalCountdown;

        // Text symbols (can be used for aesthetic styling)
        public final ForgeConfigSpec.ConfigValue<String> notificationPrefix;
        public final ForgeConfigSpec.ConfigValue<String> notificationSuffix;

        public Notifications(ForgeConfigSpec.Builder builder) {
            builder.comment("Tournament Notification Settings")
                    .push("notifications");

            titleDuration = builder
                    .comment("Duration in ticks that title messages stay on screen (20 ticks = 1 second)")
                    .defineInRange("titleDuration", 60, 20, 200);

            subtitleDuration = builder
                    .comment("Duration in ticks that subtitle messages stay on screen")
                    .defineInRange("subtitleDuration", 60, 20, 200);

            actionBarDuration = builder
                    .comment("Duration in ticks that action bar messages stay on screen")
                    .defineInRange("actionBarDuration", 60, 20, 200);

            titleFadeIn = builder
                    .comment("Fade-in time for titles in ticks")
                    .defineInRange("titleFadeIn", 5, 0, 20);

            titleFadeOut = builder
                    .comment("Fade-out time for titles in ticks")
                    .defineInRange("titleFadeOut", 5, 0, 20);

            useBoldForTitles = builder
                    .comment("Whether to use bold formatting for titles")
                    .define("useBoldForTitles", false);

            useItalicForSubtitles = builder
                    .comment("Whether to use italic formatting for subtitles")
                    .define("useItalicForSubtitles", true);

            useActionBarForMatches = builder
                    .comment("Use action bar for match notifications instead of titles")
                    .define("useActionBarForMatches", true);

            useMinimalCountdown = builder
                    .comment("Use minimal styling for countdown timers")
                    .define("useMinimalCountdown", true);

            notificationPrefix = builder
                    .comment("Prefix symbol for important notifications (e.g. ★, ◆, ►)")
                    .define("notificationPrefix", "⚡");

            notificationSuffix = builder
                    .comment("Suffix symbol for important notifications (e.g. ★, ◆, ►)")
                    .define("notificationSuffix", "⚡");

            builder.pop();
        }
    }

    public static final ForgeConfigSpec SPEC;
    public static final Notifications NOTIFICATIONS;

    static {
        final Pair<Notifications, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder()
                .configure(Notifications::new);
        SPEC = specPair.getRight();
        NOTIFICATIONS = specPair.getLeft();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, "tournaments-notifications.toml");
    }
}