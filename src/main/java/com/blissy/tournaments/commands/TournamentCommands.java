package com.blissy.tournaments.commands;

import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.battle.ReadyCheckManager;
import com.blissy.tournaments.compat.PixelmonHandler;
import com.blissy.tournaments.config.TournamentsConfig;
import com.blissy.tournaments.config.UIConfigLoader;
import com.blissy.tournaments.data.RecurringTournament;
import com.blissy.tournaments.data.Tournament;
import com.blissy.tournaments.data.TournamentMatch;
import com.blissy.tournaments.data.TournamentParticipant;
import com.blissy.tournaments.gui.TournamentCreationGUI;
import com.blissy.tournaments.gui.TournamentMainGUI;
import com.blissy.tournaments.gui.TournamentRecurringCreationGUI;
import com.blissy.tournaments.handlers.RecurringTournamentHandler;
import com.blissy.tournaments.elo.EloManager;
import com.blissy.tournaments.util.BroadcastUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class TournamentCommands {

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSource> dispatcher = event.getDispatcher();

        // Register the /tournament command with subcommands
        dispatcher.register(
                Commands.literal("tournament")
                        .executes(context -> {
                            // Open the tournament main GUI
                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                            TournamentMainGUI.openMainGui(player);
                            return 1;
                        })
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    String tournamentName = StringArgumentType.getString(context, "name");

                                    if (TournamentManager.getInstance().joinTournament(tournamentName, player)) {
                                        // Tournament join success is already handled in TournamentManager.joinTournament
                                    } else {
                                        // CHANGED FROM CHAT TO TITLE
                                        BroadcastUtil.sendTitle(player, "Cannot Join", TextFormatting.RED, 10, 60, 20);
                                        BroadcastUtil.sendSubtitle(player, "Tournament: " + tournamentName, TextFormatting.RED, 10, 60, 20);
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("leave")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    if (TournamentManager.getInstance().leaveTournament(player)) {
                                        // Leave message already handled in TournamentManager.leaveTournament
                                    } else {
                                        // CHANGED FROM CHAT TO TITLE
                                        BroadcastUtil.sendTitle(player, "Not In Tournament", TextFormatting.RED, 10, 60, 20);
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    Map<String, Tournament> tournaments = TournamentManager.getInstance().getAllTournaments();

                                    if (tournaments.isEmpty()) {
                                        // CHANGED FROM CHAT TO TITLE
                                        BroadcastUtil.sendTitle(player, "No Tournaments", TextFormatting.YELLOW, 10, 60, 20);
                                        BroadcastUtil.sendSubtitle(player, "No active tournaments found", TextFormatting.YELLOW, 10, 60, 20);
                                    } else {
                                        // Show tournament count in title
                                        BroadcastUtil.sendTitle(player, "Active Tournaments", TextFormatting.GOLD, 10, 60, 20);
                                        BroadcastUtil.sendSubtitle(player, tournaments.size() + " tournaments running", TextFormatting.YELLOW, 10, 60, 20);

                                        // Show first tournament in action bar
                                        // For multiple tournaments, use delayed tasks to show each one
                                        int delay = 0;
                                        for (Tournament tournament : tournaments.values()) {
                                            final Tournament t = tournament;
                                            player.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(
                                                    delay,
                                                    () -> {
                                                        TextFormatting color;
                                                        switch (t.getStatus()) {
                                                            case WAITING: color = TextFormatting.GREEN; break;
                                                            case IN_PROGRESS: color = TextFormatting.GOLD; break;
                                                            case ENDED: color = TextFormatting.RED; break;
                                                            default: color = TextFormatting.WHITE;
                                                        }
                                                        BroadcastUtil.sendActionBar(player,
                                                                t.getName() + " (" + t.getParticipantCount() + "/" +
                                                                        t.getMaxParticipants() + ") [" + t.getStatus() + "]", color);
                                                    }
                                            ));
                                            delay += 40; // 2 second delay between tournaments
                                        }
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("listrecurring")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    List<RecurringTournament> recurringTournaments = RecurringTournament.getAllRecurringTournaments();

                                    if (recurringTournaments.isEmpty()) {
                                        player.sendMessage(
                                                new StringTextComponent("No recurring tournaments configured")
                                                        .withStyle(TextFormatting.YELLOW),
                                                player.getUUID());
                                    } else {
                                        player.sendMessage(
                                                new StringTextComponent("Recurring Tournaments:")
                                                        .withStyle(TextFormatting.GOLD),
                                                player.getUUID());

                                        for (RecurringTournament tournament : recurringTournaments) {
                                            player.sendMessage(
                                                    new StringTextComponent("- " + tournament.getName() +
                                                            " (Template: " + tournament.getTemplateName() +
                                                            ", Recurs every " + formatHours(tournament.getRecurrenceHours()) + ")")
                                                            .withStyle(TextFormatting.AQUA),
                                                    player.getUUID());
                                        }
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("ready")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    // Get player's tournament
                                    Tournament tournament = TournamentManager.getInstance().getPlayerTournament(player);
                                    if (tournament == null) {
                                        player.sendMessage(
                                                new StringTextComponent("You are not in a tournament")
                                                        .withStyle(TextFormatting.RED),
                                                player.getUUID());
                                        return 0;
                                    }

                                    // Check if player can use ready command
                                    if (!ReadyCheckManager.canPlayerUseReadyCommand(player, tournament)) {
                                        player.sendMessage(
                                                new StringTextComponent("You don't have an active match scheduled")
                                                        .withStyle(TextFormatting.RED),
                                                player.getUUID());
                                        return 0;
                                    }

                                    // Mark player as ready
                                    ReadyCheckManager.markPlayerReady(player, tournament);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("create")
                                .requires(source -> {
                                    try {
                                        return RecurringTournamentHandler.canCreatePlayerTournament(source.getPlayerOrException());
                                    } catch (CommandSyntaxException e) {
                                        return false;
                                    }
                                })
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    TournamentCreationGUI.openCreationGUI(player);
                                    return 1;
                                })
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            String name = StringArgumentType.getString(context, "name");

                                            // Use default values
                                            TournamentCreationGUI.createTournament(player, name, 100, 100, 8, "SINGLE_ELIMINATION");
                                            return 1;
                                        })
                                        .then(Commands.argument("size", IntegerArgumentType.integer(2, 64))
                                                .executes(context -> {
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                    String name = StringArgumentType.getString(context, "name");
                                                    int size = IntegerArgumentType.getInteger(context, "size");

                                                    // Use default values for other parameters
                                                    TournamentCreationGUI.createTournament(player, name, 100, 100, size, "SINGLE_ELIMINATION");
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("createrecurring")
                                .requires(source -> {
                                    try {
                                        return RecurringTournamentHandler.canCreateRecurringTournament(source.getPlayerOrException());
                                    } catch (CommandSyntaxException e) {
                                        return false;
                                    }
                                })
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    TournamentRecurringCreationGUI.openCreationGUI(player);
                                    return 1;
                                })
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .then(Commands.argument("templateName", StringArgumentType.string())
                                                .then(Commands.argument("interval", DoubleArgumentType.doubleArg(0.1))
                                                        .executes(context -> {
                                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                            String id = StringArgumentType.getString(context, "id");
                                                            String templateName = StringArgumentType.getString(context, "templateName");
                                                            double interval = DoubleArgumentType.getDouble(context, "interval");

                                                            // Store settings in player data
                                                            TournamentRecurringCreationGUI.storeRecurringCreationSetting(player, "recurringId", id);
                                                            TournamentRecurringCreationGUI.storeRecurringCreationSetting(player, "templateName", templateName);
                                                            TournamentRecurringCreationGUI.storeRecurringCreationSetting(player, "recurrenceInterval", String.valueOf(interval));

                                                            // Create with default values for other parameters
                                                            TournamentRecurringCreationGUI.createRecurringTournament(player);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("delete")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            String tournamentName = StringArgumentType.getString(context, "name");

                                            // Get the tournament
                                            Tournament tournament = TournamentManager.getInstance().getTournament(tournamentName);
                                            if (tournament == null) {
                                                BroadcastUtil.sendTitle(player, "Tournament Not Found", TextFormatting.RED, 15, 100, 25);
                                                BroadcastUtil.sendSubtitle(player, tournamentName, TextFormatting.RED, 15, 100, 25);
                                                return 0;
                                            }

                                            // Delete the tournament
                                            if (TournamentManager.getInstance().deleteTournament(tournamentName)) {
                                                player.sendMessage(
                                                        new StringTextComponent("Tournament deleted: " + tournamentName)
                                                                .withStyle(TextFormatting.GREEN),
                                                        player.getUUID());
                                            } else {
                                                player.sendMessage(
                                                        new StringTextComponent("Failed to delete tournament: " + tournamentName)
                                                                .withStyle(TextFormatting.RED),
                                                        player.getUUID());
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("deleterecurring")
                                .requires(source -> {
                                    try {
                                        return RecurringTournamentHandler.canCreateRecurringTournament(source.getPlayerOrException());
                                    } catch (CommandSyntaxException e) {
                                        return false;
                                    }
                                })
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            String id = StringArgumentType.getString(context, "id");

                                            // Check if the recurring tournament exists
                                            RecurringTournament recurringTournament = RecurringTournament.getRecurringTournament(id);
                                            if (recurringTournament == null) {
                                                player.sendMessage(
                                                        new StringTextComponent("Recurring tournament not found: " + id)
                                                                .withStyle(TextFormatting.RED),
                                                        player.getUUID());
                                                return 0;
                                            }

                                            // Delete the recurring tournament
                                            if (RecurringTournament.deleteRecurringTournament(id)) {
                                                player.sendMessage(
                                                        new StringTextComponent("Recurring tournament deleted: " + id)
                                                                .withStyle(TextFormatting.GREEN),
                                                        player.getUUID());
                                            } else {
                                                player.sendMessage(
                                                        new StringTextComponent("Failed to delete recurring tournament: " + id)
                                                                .withStyle(TextFormatting.RED),
                                                        player.getUUID());
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("start")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    Tournament playerTournament = TournamentManager.getInstance().getPlayerTournament(player);

                                    if (playerTournament != null &&
                                            player.getUUID().equals(playerTournament.getHostId()) &&
                                            playerTournament.getStatus() == Tournament.TournamentStatus.WAITING) {

                                        playerTournament.start();
                                        // CHANGED FROM CHAT TO NOTIFICATION
                                        BroadcastUtil.sendNotificationBar(player, "Tournament Started!");
                                    } else {
                                        // CHANGED FROM CHAT TO TITLE
                                        BroadcastUtil.sendTitle(player, "Cannot Start", TextFormatting.RED, 10, 60, 20);
                                        BroadcastUtil.sendSubtitle(player, "You cannot start this tournament", TextFormatting.RED, 10, 60, 20);
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("debug")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    // Log current tournaments
                                    player.sendMessage(
                                            new StringTextComponent("Debugging tournaments...")
                                                    .withStyle(TextFormatting.YELLOW),
                                            player.getUUID());

                                    Map<String, Tournament> tournaments = TournamentManager.getInstance().getAllTournaments();
                                    player.sendMessage(
                                            new StringTextComponent("Active tournaments: " + tournaments.size())
                                                    .withStyle(TextFormatting.YELLOW),
                                            player.getUUID());

                                    for (Tournament tournament : tournaments.values()) {
                                        player.sendMessage(
                                                new StringTextComponent("- " + tournament.getName() +
                                                        " (" + tournament.getParticipantCount() + " players, status: " +
                                                        tournament.getStatus() + ")")
                                                        .withStyle(TextFormatting.YELLOW),
                                                player.getUUID());

                                        // List participants
                                        player.sendMessage(
                                                new StringTextComponent("  Participants:")
                                                        .withStyle(TextFormatting.YELLOW),
                                                player.getUUID());

                                        for (TournamentParticipant participant : tournament.getParticipants()) {
                                            player.sendMessage(
                                                    new StringTextComponent("    - " + participant.getPlayerName() +
                                                            " (eliminated: " + tournament.isPlayerEliminated(participant.getPlayerId()) + ")")
                                                            .withStyle(TextFormatting.YELLOW),
                                                    player.getUUID());
                                        }

                                        // List matches
                                        player.sendMessage(
                                                new StringTextComponent("  Matches:")
                                                        .withStyle(TextFormatting.YELLOW),
                                                player.getUUID());

                                        for (TournamentMatch match : tournament.getCurrentMatches()) {
                                            player.sendMessage(
                                                    new StringTextComponent("    - " + match.getPlayer1Name() + " vs " +
                                                            match.getPlayer2Name() + " (status: " + match.getStatus() + ")")
                                                            .withStyle(TextFormatting.YELLOW),
                                                    player.getUUID());
                                        }
                                    }

                                    // Debug recurring tournaments
                                    player.sendMessage(
                                            new StringTextComponent("Debugging recurring tournaments...")
                                                    .withStyle(TextFormatting.YELLOW),
                                            player.getUUID());

                                    List<RecurringTournament> recurringTournaments = RecurringTournament.getAllRecurringTournaments();
                                    player.sendMessage(
                                            new StringTextComponent("Recurring tournaments: " + recurringTournaments.size())
                                                    .withStyle(TextFormatting.YELLOW),
                                            player.getUUID());

                                    for (RecurringTournament tournament : recurringTournaments) {
                                        player.sendMessage(
                                                new StringTextComponent("- " + tournament.getName() +
                                                        " (Template: " + tournament.getTemplateName() +
                                                        ", Recurrence: " + formatHours(tournament.getRecurrenceHours()) + ")")
                                                        .withStyle(TextFormatting.YELLOW),
                                                player.getUUID());

                                        player.sendMessage(
                                                new StringTextComponent("  Next Occurrence: " + formatTimeUntil(tournament.getNextScheduled()))
                                                        .withStyle(TextFormatting.YELLOW),
                                                player.getUUID());
                                    }

                                    // Debug active battles
                                    player.sendMessage(
                                            new StringTextComponent("Debugging active tournament battles...")
                                                    .withStyle(TextFormatting.YELLOW),
                                            player.getUUID());

                                    PixelmonHandler.debugTournamentBattles();

                                    return 1;
                                })
                        )
                        .then(Commands.literal("forcebattleresult")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .then(Commands.argument("tournamentName", StringArgumentType.string())
                                        .then(Commands.argument("winner", StringArgumentType.string())
                                                .then(Commands.argument("loser", StringArgumentType.string())
                                                        .executes(context -> {
                                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                            String tournamentName = StringArgumentType.getString(context, "tournamentName");
                                                            String winnerName = StringArgumentType.getString(context, "winner");
                                                            String loserName = StringArgumentType.getString(context, "loser");

                                                            Tournament tournament = TournamentManager.getInstance().getTournament(tournamentName);
                                                            if (tournament == null) {
                                                                player.sendMessage(
                                                                        new StringTextComponent("Tournament not found: " + tournamentName)
                                                                                .withStyle(TextFormatting.RED),
                                                                        player.getUUID());
                                                                return 0;
                                                            }

                                                            // Find player UUIDs by name
                                                            UUID winnerId = null;
                                                            UUID loserId = null;

                                                            for (TournamentParticipant participant : tournament.getParticipants()) {
                                                                if (participant.getPlayerName().equalsIgnoreCase(winnerName)) {
                                                                    winnerId = participant.getPlayerId();
                                                                } else if (participant.getPlayerName().equalsIgnoreCase(loserName)) {
                                                                    loserId = participant.getPlayerId();
                                                                }
                                                            }

                                                            if (winnerId == null || loserId == null) {
                                                                player.sendMessage(
                                                                        new StringTextComponent("Could not find players in tournament: " +
                                                                                winnerName + " and/or " + loserName)
                                                                                .withStyle(TextFormatting.RED),
                                                                        player.getUUID());
                                                                return 0;
                                                            }

                                                            // Force record the match result
                                                            boolean resultRecorded = tournament.recordMatchResult(winnerId, loserId);

                                                            if (resultRecorded) {
                                                                // Update ELO ratings
                                                                Tournaments.ELO_MANAGER.recordMatch(winnerId, loserId);

                                                                // Force eliminate the loser
                                                                tournament.eliminatePlayer(loserId);

                                                                player.sendMessage(
                                                                        new StringTextComponent("Force recorded match result: " +
                                                                                winnerName + " defeats " + loserName)
                                                                                .withStyle(TextFormatting.GREEN),
                                                                        player.getUUID());

                                                                tournament.broadcastMessage(winnerName + " has defeated " +
                                                                        loserName + " and advances to the next round!");

                                                                return 1;
                                                            } else {
                                                                player.sendMessage(
                                                                        new StringTextComponent("Failed to record match result")
                                                                                .withStyle(TextFormatting.RED),
                                                                        player.getUUID());
                                                                return 0;
                                                            }
                                                        })
                                                )))
                        )
                        .then(Commands.literal("reloadconfig")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    try {
                                        // Reload the config
                                        UIConfigLoader.loadConfig(null);
                                        UIConfigLoader.saveConfig();

                                        player.sendMessage(
                                                new StringTextComponent("Tournament UI configuration reloaded successfully")
                                                        .withStyle(TextFormatting.GREEN),
                                                player.getUUID());

                                        Tournaments.LOGGER.info("UI config reloaded by {}", player.getName().getString());
                                    } catch (Exception e) {
                                        Tournaments.LOGGER.error("Failed to reload UI config", e);
                                        player.sendMessage(
                                                new StringTextComponent("Error reloading UI configuration: " + e.getMessage())
                                                        .withStyle(TextFormatting.RED),
                                                player.getUUID());
                                    }

                                    return 1;
                                })
                        )
                        .then(Commands.literal("setentry")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    // Get player's current position
                                    double x = player.getX();
                                    double y = player.getY();
                                    double z = player.getZ();
                                    String dimension = player.level.dimension().location().toString();

                                    // Set config values (temporarily)
                                    TournamentsConfig.COMMON.entryX.set(x);
                                    TournamentsConfig.COMMON.entryY.set(y);
                                    TournamentsConfig.COMMON.entryZ.set(z);
                                    TournamentsConfig.COMMON.entryDimension.set(dimension);

                                    // Store the values in a server NBT data file
                                    storeLocationInServerData("tournament_entry", x, y, z, dimension);

                                    player.sendMessage(
                                            new StringTextComponent("Tournament entry point set to your current location")
                                                    .withStyle(TextFormatting.GREEN),
                                            player.getUUID());

                                    return 1;
                                })
                        )
                        .then(Commands.literal("setexit")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    // Get player's current position
                                    double x = player.getX();
                                    double y = player.getY();
                                    double z = player.getZ();
                                    String dimension = player.level.dimension().location().toString();

                                    // Set config values (temporarily)
                                    TournamentsConfig.COMMON.exitX.set(x);
                                    TournamentsConfig.COMMON.exitY.set(y);
                                    TournamentsConfig.COMMON.exitZ.set(z);
                                    TournamentsConfig.COMMON.exitDimension.set(dimension);

                                    // Store the values in a server NBT data file
                                    storeLocationInServerData("tournament_exit", x, y, z, dimension);

                                    player.sendMessage(
                                            new StringTextComponent("Tournament exit point set to your current location")
                                                    .withStyle(TextFormatting.GREEN),
                                            player.getUUID());

                                    return 1;
                                })
                        )
                        .then(Commands.literal("setmatchtp1")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    // Get player's current position
                                    double x = player.getX();
                                    double y = player.getY();
                                    double z = player.getZ();
                                    String dimension = player.level.dimension().location().toString();

                                    // Store the values in a server NBT data file
                                    storeLocationInServerData("tournament_match_pos1", x, y, z, dimension);

                                    player.sendMessage(
                                            new StringTextComponent("Tournament match position 1 set to your current location")
                                                    .withStyle(TextFormatting.GREEN),
                                            player.getUUID());

                                    return 1;
                                })
                        )
                        .then(Commands.literal("setmatchtp2")
                                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();

                                    // Get player's current position
                                    double x = player.getX();
                                    double y = player.getY();
                                    double z = player.getZ();
                                    String dimension = player.level.dimension().location().toString();

                                    // Store the values in a server NBT data file
                                    storeLocationInServerData("tournament_match_pos2", x, y, z, dimension);

                                    player.sendMessage(
                                            new StringTextComponent("Tournament match position 2 set to your current location")
                                                    .withStyle(TextFormatting.GREEN),
                                            player.getUUID());

                                    return 1;
                                })
                        )
                        .then(Commands.literal("resetelo")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    Tournaments.ELO_MANAGER.resetRankings();
                                    context.getSource().sendSuccess(
                                            new StringTextComponent("ELO rankings have been reset"),
                                            true);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("forcechecktournaments")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    RecurringTournament.checkAllRecurringTournaments();
                                    player.sendMessage(
                                            new StringTextComponent("Forced recurring tournament check completed")
                                                    .withStyle(TextFormatting.GREEN),
                                            player.getUUID());
                                    return 1;
                                })
                        )
                        .then(Commands.literal("givecreatepermission")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayerEntity admin = context.getSource().getPlayerOrException();
                                            String playerName = StringArgumentType.getString(context, "player");

                                            // Find the target player
                                            ServerPlayerEntity targetPlayer = null;
                                            for (ServerPlayerEntity p : context.getSource().getServer().getPlayerList().getPlayers()) {
                                                if (p.getName().getString().equalsIgnoreCase(playerName)) {
                                                    targetPlayer = p;
                                                    break;
                                                }
                                            }

                                            if (targetPlayer == null) {
                                                admin.sendMessage(
                                                        new StringTextComponent("Player not found: " + playerName)
                                                                .withStyle(TextFormatting.RED),
                                                        admin.getUUID());
                                                return 0;
                                            }

                                            // Give permission
                                            RecurringTournamentHandler.setPlayerTournamentPermission(targetPlayer, true);

                                            admin.sendMessage(
                                                    new StringTextComponent("Tournament creation permission granted to " + playerName)
                                                            .withStyle(TextFormatting.GREEN),
                                                    admin.getUUID());

                                            targetPlayer.sendMessage(
                                                    new StringTextComponent("You have been granted permission to create tournaments")
                                                            .withStyle(TextFormatting.GREEN),
                                                    targetPlayer.getUUID());

                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("revokecreatepermission")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayerEntity admin = context.getSource().getPlayerOrException();
                                            String playerName = StringArgumentType.getString(context, "player");

                                            // Find the target player
                                            ServerPlayerEntity targetPlayer = null;
                                            for (ServerPlayerEntity p : context.getSource().getServer().getPlayerList().getPlayers()) {
                                                if (p.getName().getString().equalsIgnoreCase(playerName)) {
                                                    targetPlayer = p;
                                                    break;
                                                }
                                            }

                                            if (targetPlayer == null) {
                                                admin.sendMessage(
                                                        new StringTextComponent("Player not found: " + playerName)
                                                                .withStyle(TextFormatting.RED),
                                                        admin.getUUID());
                                                return 0;
                                            }

                                            // Revoke permission
                                            RecurringTournamentHandler.setPlayerTournamentPermission(targetPlayer, false);

                                            admin.sendMessage(
                                                    new StringTextComponent("Tournament creation permission revoked from " + playerName)
                                                            .withStyle(TextFormatting.GREEN),
                                                    admin.getUUID());

                                            targetPlayer.sendMessage(
                                                    new StringTextComponent("Your permission to create tournaments has been revoked")
                                                            .withStyle(TextFormatting.RED),
                                                    targetPlayer.getUUID());

                                            return 1;
                                        })
                                )
                        )

        );

    }

    /**
     * Store a location in the server's NBT data
     */
    private static void storeLocationInServerData(String id, double x, double y, double z, String dimension) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;

            // Get or create the data directory
            File dataDir = new File(server.getServerDirectory(), "data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            // Create data file
            File locationFile = new File(dataDir, "tournaments_locations.dat");

            // Create or load NBT data
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

            // Add or update location data
            CompoundNBT locationData = new CompoundNBT();
            locationData.putDouble("x", x);
            locationData.putDouble("y", y);
            locationData.putDouble("z", z);
            locationData.putString("dimension", dimension);

            root.put(id, locationData);

            // Save the data
            try (FileOutputStream stream = new FileOutputStream(locationFile)) {
                CompressedStreamTools.writeCompressed(root, stream);
                Tournaments.LOGGER.info("Saved tournament location: {} at ({}, {}, {}) in {}",
                        id, x, y, z, dimension);
            } catch (Exception e) {
                Tournaments.LOGGER.error("Failed to save locations file", e);
            }
        } catch (Exception e) {
            Tournaments.LOGGER.error("Failed to store location data", e);
        }
    }

    /**
     * Format hours in a user-friendly way
     */
    private static String formatHours(double hours) {
        if (hours == 24) {
            return "24 hours (daily)";
        } else if (hours == 168) {
            return "168 hours (weekly)";
        } else if (hours == 720) {
            return "720 hours (monthly)";
        } else if (hours < 1) {
            return (int)(hours * 60) + " minutes";
        } else if (hours == Math.floor(hours)) {
            return (int)hours + " hours";
        } else {
            return String.format("%.1f hours", hours);
        }
    }

    /**
     * Format time until a future instant
     */
    private static String formatTimeUntil(java.time.Instant target) {
        if (target == null) {
            return "Never";
        }

        java.time.Instant now = java.time.Instant.now();
        if (now.isAfter(target)) {
            return "Now";
        }

        java.time.Duration duration = java.time.Duration.between(now, target);
        long seconds = duration.getSeconds();

        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutes";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            if (minutes == 0) {
                return hours + " hours";
            } else {
                return hours + " hours, " + minutes + " minutes";
            }
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            if (hours == 0) {
                return days + " days";
            } else {
                return days + " days, " + hours + " hours";
            }
        }
    }
}