package com.blissy.tournaments.battle;

import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.compat.PixelmonHandler;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages scheduled battles between tournament participants
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class ScheduledBattleManager {

    private static class ScheduledBattle {
        private final ServerPlayerEntity player1;
        private final ServerPlayerEntity player2;
        private int ticksRemaining;

        public ScheduledBattle(ServerPlayerEntity player1, ServerPlayerEntity player2, int ticks) {
            this.player1 = player1;
            this.player2 = player2;
            this.ticksRemaining = ticks;
        }

        public boolean tick() {
            ticksRemaining--;
            return ticksRemaining <= 0;
        }

        public void start() {
            // Ensure both players are still online and valid
            if (player1 != null && player2 != null &&
                    player1.isAlive() && player2.isAlive()) {
                PixelmonHandler.createTournamentBattle(player1, player2);
            } else {
                Tournaments.LOGGER.warn("Scheduled battle could not start - one or both players unavailable");
            }
        }
    }

    private static final List<ScheduledBattle> scheduledBattles = new ArrayList<>();

    /**
     * Schedule a battle between two players
     * @param player1 First player
     * @param player2 Second player
     * @param tickDelay Delay in ticks before starting the battle
     */
    public static void scheduleBattle(ServerPlayerEntity player1, ServerPlayerEntity player2, int tickDelay) {
        scheduledBattles.add(new ScheduledBattle(player1, player2, tickDelay));
        Tournaments.LOGGER.info("Scheduled battle between {} and {} in {} ticks",
                player1.getName().getString(), player2.getName().getString(), tickDelay);
    }

    /**
     * Process scheduled battles on server tick
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Check for battles ready to start
        Iterator<ScheduledBattle> iterator = scheduledBattles.iterator();
        while (iterator.hasNext()) {
            ScheduledBattle battle = iterator.next();
            if (battle.tick()) {
                battle.start();
                iterator.remove();
            }
        }
    }
}