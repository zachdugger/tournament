package com.blissy.tournaments.debug;

import com.blissy.tournaments.Tournaments;
import com.pixelmonmod.pixelmon.api.events.battles.BattleEndEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleStartedEvent;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Diagnostic class to debug Pixelmon battle events
 * Add this class to your project to get detailed logging on battle events
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class BattleEventDiagnostics {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAnyBattleStart(BattleStartedEvent.Post event) {
        try {
            // Log that we received the event
            Tournaments.LOGGER.info("=============== DIAGNOSTIC: BATTLE START EVENT DETECTED ===============");
            Tournaments.LOGGER.info("Battle controller: {}", event.getBattleController().battleIndex);

            // Log team one participants
            Tournaments.LOGGER.info("Team One Participants:");
            int i = 0;
            for (BattleParticipant participant : event.getTeamOne()) {
                if (participant instanceof PlayerParticipant) {
                    PlayerParticipant playerParticipant = (PlayerParticipant) participant;
                    Tournaments.LOGGER.info("Player {}: {}", i, playerParticipant.player.getName().getString());

                    // Log player's Pokémon from storage, since we can't access allPokemon directly
                    ServerPlayerEntity player = playerParticipant.player;
                    Pokemon[] pokemon = StorageProxy.getParty(player.getUUID()).getAll();
                    Tournaments.LOGGER.info("  Pokemon count: {}", pokemon.length);
                    for (int j = 0; j < pokemon.length; j++) {
                        Pokemon p = pokemon[j];
                        if (p != null) {
                            Tournaments.LOGGER.info("  Pokemon {}: {}, Level {}, Fainted: {}",
                                    j, p.getDisplayName(), p.getPokemonLevel(), p.isFainted());
                        }
                    }
                } else {
                    Tournaments.LOGGER.info("Participant {}: {}", i, participant.getClass().getSimpleName());
                }
                i++;
            }

            // Log team two participants
            Tournaments.LOGGER.info("Team Two Participants:");
            i = 0;
            for (BattleParticipant participant : event.getTeamTwo()) {
                if (participant instanceof PlayerParticipant) {
                    PlayerParticipant playerParticipant = (PlayerParticipant) participant;
                    Tournaments.LOGGER.info("Player {}: {}", i, playerParticipant.player.getName().getString());

                    // Log player's Pokémon from storage
                    ServerPlayerEntity player = playerParticipant.player;
                    Pokemon[] pokemon = StorageProxy.getParty(player.getUUID()).getAll();
                    Tournaments.LOGGER.info("  Pokemon count: {}", pokemon.length);
                    for (int j = 0; j < pokemon.length; j++) {
                        Pokemon p = pokemon[j];
                        if (p != null) {
                            Tournaments.LOGGER.info("  Pokemon {}: {}, Level {}, Fainted: {}",
                                    j, p.getDisplayName(), p.getPokemonLevel(), p.isFainted());
                        }
                    }
                } else {
                    Tournaments.LOGGER.info("Participant {}: {}", i, participant.getClass().getSimpleName());
                }
                i++;
            }

            Tournaments.LOGGER.info("===============================================================");
        } catch (Exception e) {
            Tournaments.LOGGER.error("Error in diagnostic start event handler", e);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAnyBattleEnd(BattleEndEvent event) {
        try {
            // Log that we received the event
            Tournaments.LOGGER.info("=============== DIAGNOSTIC: BATTLE END EVENT DETECTED ===============");
            Tournaments.LOGGER.info("Battle controller: {}", event.getBattleController().battleIndex);
            Tournaments.LOGGER.info("Battle end cause: {}", event.getCause());
            Tournaments.LOGGER.info("Is abnormal: {}", event.isAbnormal());

            // Log the results map size
            Tournaments.LOGGER.info("Results map size: {}", event.getResults().size());

            // Log each battle participant and their result
            for (BattleParticipant participant : event.getResults().keySet()) {
                if (participant instanceof PlayerParticipant) {
                    PlayerParticipant playerParticipant = (PlayerParticipant) participant;
                    Tournaments.LOGGER.info("Player: {}, Result: {}, IsDefeated: {}",
                            playerParticipant.player.getName().getString(),
                            event.getResults().get(participant),
                            participant.isDefeated);

                    // Log player's current Pokémon state from storage
                    ServerPlayerEntity player = playerParticipant.player;
                    Pokemon[] pokemon = StorageProxy.getParty(player.getUUID()).getAll();
                    Tournaments.LOGGER.info("Current party Pokemon for {}", player.getName().getString());
                    for (int i = 0; i < pokemon.length; i++) {
                        Pokemon p = pokemon[i];
                        if (p != null) {
                            Tournaments.LOGGER.info("  Pokemon {}: {}, Level {}, Fainted: {}",
                                    i, p.getDisplayName(), p.getPokemonLevel(), p.isFainted());
                        }
                    }
                } else {
                    Tournaments.LOGGER.info("Non-player participant: {}, Result: {}, IsDefeated: {}",
                            participant.getClass().getSimpleName(),
                            event.getResults().get(participant),
                            participant.isDefeated);
                }
            }

            // Log players in battle
            Tournaments.LOGGER.info("Players in battle: {}", event.getPlayers().size());
            for (int i = 0; i < event.getPlayers().size(); i++) {
                Tournaments.LOGGER.info("Player {}: {}", i,
                        event.getPlayers().get(i).getName().getString());
            }

            Tournaments.LOGGER.info("===============================================================");
        } catch (Exception e) {
            Tournaments.LOGGER.error("Error in diagnostic event handler", e);
        }
    }
}