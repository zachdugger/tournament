package com.blissy.tournaments.handlers;

import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.config.TournamentsConfig;
import com.blissy.tournaments.data.Tournament;
import com.blissy.tournaments.util.TeleportUtil;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Handler for player-related tournament events
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class TournamentPlayerHandler {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        UUID playerId = player.getUUID();

        // Check if teleports are enabled
        if (!TournamentsConfig.COMMON.enableTeleports.get()) {
            return;
        }

        // Get all tournaments
        for (Tournament tournament : TournamentManager.getInstance().getAllTournaments().values()) {
            // If player is eliminated from this tournament, teleport them to exit
            if (tournament.getStatus() == Tournament.TournamentStatus.IN_PROGRESS &&
                    tournament.isPlayerEliminated(playerId)) {

                // Delay teleport by 20 ticks (1 second) to ensure player is fully loaded
                player.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(20, () -> {
                    Tournaments.LOGGER.info("Teleporting rejoined player {} to tournament exit point",
                            player.getName().getString());

                    player.sendMessage(
                            new StringTextComponent("You were eliminated from tournament: " + tournament.getName() +
                                    ". Teleporting to exit...")
                                    .withStyle(TextFormatting.YELLOW),
                            player.getUUID());

                    boolean success = TeleportUtil.teleportToExitPoint(player);
                    if (!success) {
                        Tournaments.LOGGER.warn("Failed to teleport rejoined player {} to exit point",
                                player.getName().getString());
                    }
                }));

                // Only process the first tournament the player is eliminated from
                break;
            }
        }
    }
}