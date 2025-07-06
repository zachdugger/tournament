package com.blissy.tournaments.util;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.STitlePacket;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.HoverEvent;

/**
 * Enhanced utility for tournament notifications
 * Displays all tournament information on-screen instead of in chat
 */
public class BroadcastUtil {

    /**
     * Send a compact title message (centered screen text with improved styling)
     * Extended duration to ensure 3-5 second visibility
     */
    public static void sendTitle(ServerPlayerEntity player, String message, TextFormatting color, int fadeIn, int stay, int fadeOut) {
        if (player == null) return;

        // Create styled text with shadow effect to improve readability
        Style style = Style.EMPTY
                .withColor(color)
                .withBold(false); // Keep text compact by avoiding bold

        ITextComponent text = new StringTextComponent(message).setStyle(style);
        player.connection.send(new STitlePacket(STitlePacket.Type.TITLE, text, fadeIn, stay, fadeOut));
    }

    /**
     * Send a compact subtitle message (smaller text below title)
     * Extended duration to ensure 3-5 second visibility
     */
    public static void sendSubtitle(ServerPlayerEntity player, String message, TextFormatting color, int fadeIn, int stay, int fadeOut) {
        if (player == null) return;

        // Create styled text with slightly different formatting than title
        Style style = Style.EMPTY
                .withColor(color)
                .withItalic(true); // Italic for subtitles helps distinguish from title

        ITextComponent text = new StringTextComponent(message).setStyle(style);
        player.connection.send(new STitlePacket(STitlePacket.Type.SUBTITLE, text, fadeIn, stay, fadeOut));
    }

    /**
     * Send an actionbar message (text above hotbar)
     * Extended duration to ensure 3-5 second visibility
     */
    public static void sendActionBar(ServerPlayerEntity player, String message, TextFormatting color) {
        if (player == null) return;

        // Create styled text with improved readability
        Style style = Style.EMPTY.withColor(color);
        ITextComponent text = new StringTextComponent(message).setStyle(style);

        // Extended duration for better visibility (5 seconds total)
        player.connection.send(new STitlePacket(STitlePacket.Type.ACTIONBAR, text, 10, 100, 10));
    }

    /**
     * Send a notification actionbar message (for important updates)
     * Uses brighter colors and longer duration (5 seconds)
     */
    public static void sendNotificationBar(ServerPlayerEntity player, String message) {
        if (player == null) return;

        // Create bright gold text with emphasis
        Style style = Style.EMPTY
                .withColor(TextFormatting.GOLD)
                .withBold(true);

        ITextComponent text = new StringTextComponent("⚡ " + message + " ⚡").setStyle(style);
        // Extended duration for important notifications (6 seconds total)
        player.connection.send(new STitlePacket(STitlePacket.Type.ACTIONBAR, text, 15, 120, 25));
    }

    /**
     * Send a mini title - smaller and less intrusive than a full title
     * Good for status updates, extended to 4 seconds
     */
    public static void sendMiniTitle(ServerPlayerEntity player, String message, TextFormatting color) {
        if (player == null) return;

        // Extended duration for mini titles (4 seconds total)
        sendTitle(player, message, color, 10, 80, 10);
    }

    /**
     * Clear all titles from player's screen
     */
    public static void clearTitles(ServerPlayerEntity player) {
        if (player == null) return;

        player.connection.send(new STitlePacket(STitlePacket.Type.CLEAR, null));
    }

    /**
     * Run countdown sequence for player
     * Uses compact styling for less screen space with extended visibility
     */
    public static void runCountdown(ServerPlayerEntity player, String message, int seconds, Runnable onComplete) {
        if (player == null) return;

        // Clear any existing titles
        clearTitles(player);

        // Start with the message - extended duration for better visibility
        sendTitle(player, message, TextFormatting.GOLD, 10, 80, 10);

        // Set up the countdown with timed tasks
        for (int i = seconds; i > 0; i--) {
            final int count = i;
            player.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(
                    (seconds - i) * 20, // 20 ticks = 1 second
                    () -> {
                        // Show countdown number with extended duration
                        int stay = Math.max(15, count * 6); // Minimum 15 ticks, scale with count
                        sendTitle(player, String.valueOf(count), TextFormatting.RED, 0, stay, 10);

                        if (count == 1 && onComplete != null) {
                            // Execute completion task after 1 second
                            player.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(
                                    20, onComplete
                            ));
                        }
                    }
            ));
        }
    }
}