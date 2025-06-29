package com.blissy.tournaments.handlers;

import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.data.RecurringTournament;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

/**
 * Handler for checking and creating recurring tournaments
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class RecurringTournamentHandler {

    private static final int CHECK_INTERVAL = 1200; // Check every minute (20 ticks * 60s)
    private static int tickCounter = 0;

    /**
     * Check for recurring tournaments every minute
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        // Don't run if no players are online
        if (ServerLifecycleHooks.getCurrentServer() == null ||
                ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerCount() == 0) {
            return;
        }

        try {
            Tournaments.LOGGER.debug("Checking recurring tournaments...");
            RecurringTournament.checkAllRecurringTournaments();
        } catch (Exception e) {
            Tournaments.LOGGER.error("Error during recurring tournament check: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if a player has permission to create recurring tournaments
     */
    public static boolean canCreateRecurringTournament(ServerPlayerEntity player) {
        // Permission level 2 or higher can create recurring tournaments
        return player != null && player.hasPermissions(2);
    }

    /**
     * Check if a player has permission to create normal tournaments
     * Added checks for player.getPersistentData() to avoid NullPointerExceptions
     */
    public static boolean canCreatePlayerTournament(ServerPlayerEntity player) {
        // Players with "tournaments.create" permission can create tournaments
        // This is a simpler check than the full permission system
        if (player == null) {
            return false;
        }

        if (player.hasPermissions(2)) {
            return true;
        }

        if (player.getPersistentData() != null &&
                player.getPersistentData().contains("tournaments.create")) {
            return player.getPersistentData().getBoolean("tournaments.create");
        }

        return false;
    }

    /**
     * Set player tournament creation permission
     */
    public static void setPlayerTournamentPermission(ServerPlayerEntity player, boolean canCreate) {
        if (player != null && player.getPersistentData() != null) {
            player.getPersistentData().putBoolean("tournaments.create", canCreate);

            // Notify player of permission change
            player.sendMessage(
                    new net.minecraft.util.text.StringTextComponent(
                            canCreate ? "You now have permission to create tournaments" : "Your tournament creation permission has been revoked")
                            .withStyle(canCreate ?
                                    net.minecraft.util.text.TextFormatting.GREEN :
                                    net.minecraft.util.text.TextFormatting.RED),
                    player.getUUID()
            );

            Tournaments.LOGGER.info("Tournament creation permission for {} set to {}",
                    player.getName().getString(), canCreate);
        }
    }
}