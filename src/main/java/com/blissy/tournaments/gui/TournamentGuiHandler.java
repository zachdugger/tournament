package com.blissy.tournaments.gui;

import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.handlers.RecurringTournamentHandler;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import com.blissy.tournaments.data.Tournament;

/**
 * Handler for managing tournament GUI containers
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "tournaments")
public class TournamentGuiHandler {

    private static boolean processingAction = false;

    /**
     * Handle container open events
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) {
            return;
        }

        Container container = event.getContainer();
        if (container instanceof ContainerFactory.ClickInterceptingContainer) {
            Tournaments.LOGGER.debug("Tournament container opened with ID: {}", container.containerId);
        }
    }

    /**
     * Handle container close events
     */
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) {
            return;
        }

        Container container = event.getContainer();
        if (container instanceof ContainerFactory.ClickInterceptingContainer) {
            Tournaments.LOGGER.debug("Tournament container closed with ID: {}", container.containerId);
        }
    }

    /**
     * Set item lore (description) for an ItemStack
     * @param stack ItemStack to modify
     * @param lore List of text components for lore
     */
    public static void setItemLore(ItemStack stack, List<ITextComponent> lore) {
        CompoundNBT displayTag = stack.getOrCreateTagElement("display");
        ListNBT loreList = new ListNBT();

        for (ITextComponent component : lore) {
            String json = ITextComponent.Serializer.toJson(component);
            loreList.add(StringNBT.valueOf(json));
        }

        displayTag.put("Lore", loreList);
    }

    /**
     * Process an item for GUI actions
     */
    public static void processItemClick(ServerPlayerEntity player, ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("GuiAction")) {
            CompoundNBT tag = stack.getTag();
            String action = tag.getString("GuiAction");
            String tournamentName = tag.contains("TournamentName") ?
                    tag.getString("TournamentName") : null;
            String recurringId = tag.contains("RecurringTournamentId") ?
                    tag.getString("RecurringTournamentId") : null;

            Tournaments.LOGGER.debug("Processing GUI action: {} for tournament: {}", action, tournamentName != null ? tournamentName : "none");

            // Special case for create action to bypass the recursion issues
            if ("create".equals(action)) {
                player.closeContainer();
                Tournaments.LOGGER.info("GUI FLOW DEBUG: Direct handling of create action");

                boolean canCreatePlayer = RecurringTournamentHandler.canCreatePlayerTournament(player);
                if (canCreatePlayer) {
                    TournamentCreationGUI.openCreationGUI(player);
                    return;
                }
            }

            // Handle creation-related GUI actions directly
            if (action.equals("setName")) {
                // Have player type name in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the tournament name in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.YELLOW),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForTournamentName", true);
                return;
            } else if (action.equals("setMinLevel")) {
                // Have player type min level in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the minimum Pokemon level (1-100) in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.AQUA),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForMinLevel", true);
                return;
            } else if (action.equals("setMaxLevel")) {
                // Have player type max level in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the maximum Pokemon level (1-100) in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.BLUE),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForMaxLevel", true);
                return;
            } else if (action.equals("setMaxParticipants")) {
                // Have player type max participants in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the tournament size (4-64) in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.GREEN),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForMaxParticipants", true);
                return;
            } else if (action.equals("setFormat")) {
                // Have player type format in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the tournament format (SINGLE_ELIMINATION, DOUBLE_ELIMINATION, ROUND_ROBIN) in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.LIGHT_PURPLE),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForFormat", true);
                return;
            } else if (action.equals("setEntryFee")) {
                // Have player type entry fee in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the entry fee amount in chat (type '0' for no fee, or 'cancel' to cancel):")
                                .withStyle(TextFormatting.GOLD),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForEntryFee", true);
                return;
            } else if (action.equals("setStartDelay")) {
                // Have player type start delay in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please enter the time (in hours) before the tournament begins (e.g., 0.5 for 30 minutes, or 0 for manual start):")
                                .withStyle(TextFormatting.LIGHT_PURPLE),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForStartDelay", true);
                return;
            } else if (action.equals("createTournament")) {
                // Create tournament with specified or default values
                String name = TournamentCreationGUI.getCreationSetting(player, "name");
                String minLevelStr = TournamentCreationGUI.getCreationSetting(player, "minLevel");
                String maxLevelStr = TournamentCreationGUI.getCreationSetting(player, "maxLevel");
                String maxParticipantsStr = TournamentCreationGUI.getCreationSetting(player, "maxParticipants");
                String format = TournamentCreationGUI.getCreationSetting(player, "format");
                String entryFeeStr = TournamentCreationGUI.getCreationSetting(player, "entryFee");
                String startDelayStr = TournamentCreationGUI.getCreationSetting(player, "startDelay");

                // Parse values (use null for defaults)
                Integer minLevel = minLevelStr != null ? Integer.parseInt(minLevelStr) : null;
                Integer maxLevel = maxLevelStr != null ? Integer.parseInt(maxLevelStr) : null;
                Integer maxParticipants = maxParticipantsStr != null ? Integer.parseInt(maxParticipantsStr) : null;
                Double entryFee = entryFeeStr != null ? Double.parseDouble(entryFeeStr) : null;
                Double startDelay = startDelayStr != null ? Double.parseDouble(startDelayStr) : null;

                // Create the tournament
                player.closeContainer();
                TournamentCreationGUI.createTournament(player, name, minLevel, maxLevel, maxParticipants, format, entryFee, startDelay);
                return;
            } else if (action.equals("openRecurringCreation")) {
                // Open recurring tournament creation GUI
                player.closeContainer();
                if (RecurringTournamentHandler.canCreateRecurringTournament(player)) {
                    TournamentRecurringCreationGUI.openCreationGUI(player);
                } else {
                    player.sendMessage(
                            new StringTextComponent("You don't have permission to create recurring tournaments")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                    TournamentCreationGUI.openCreationGUI(player);
                }
                return;
            } else if (action.equals("createRecurringTournament")) {
                // Create recurring tournament with specified values
                player.closeContainer();

                // Call the method to create a recurring tournament
                TournamentRecurringCreationGUI.createRecurringTournament(player);
                return;
            } else if (action.equals("setRecurringId")) {
                // Have player type recurring tournament ID in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the recurring tournament ID in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForRecurringId", true);
                return;
            } else if (action.equals("setRecurringTemplateName")) {
                // Have player type template name in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the tournament template name in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.YELLOW),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForRecurringTemplateName", true);
                return;
            } else if (action.equals("setRecurringMinLevel")) {
                // Have player type min level in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the minimum Pokemon level (1-100) in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.AQUA),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForRecurringMinLevel", true);
                return;
            } else if (action.equals("setRecurringMaxLevel")) {
                // Have player type max level in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the maximum Pokemon level (1-100) in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.BLUE),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForRecurringMaxLevel", true);
                return;
            } else if (action.equals("setRecurringMaxParticipants")) {
                // Have player type max participants in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the tournament size (4-64) in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.GREEN),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForRecurringMaxParticipants", true);
                return;
            } else if (action.equals("setRecurringFormat")) {
                // Have player type format in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the tournament format (SINGLE_ELIMINATION, DOUBLE_ELIMINATION, ROUND_ROBIN) in chat (type 'cancel' to cancel):")
                                .withStyle(TextFormatting.LIGHT_PURPLE),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForRecurringFormat", true);
                return;
            } else if (action.equals("setRecurringEntryFee")) {
                // Have player type entry fee in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please type the entry fee amount in chat (type '0' for no fee, or 'cancel' to cancel):")
                                .withStyle(TextFormatting.GOLD),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForRecurringEntryFee", true);
                return;
            } else if (action.equals("setRecurringInterval")) {
                // Have player type recurrence interval in chat
                player.closeContainer();
                player.sendMessage(
                        new StringTextComponent("Please enter the recurrence interval in hours (e.g., 24 for daily, 168 for weekly):")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                player.getPersistentData().putBoolean("WaitingForRecurringInterval", true);
                return;
            }

            // Process other actions through the main GUI handler
            if (recurringId != null) {
                TournamentMainGUI.processGuiAction(player, action, tournamentName, recurringId, null);
            } else {
                TournamentMainGUI.processGuiAction(player, action, tournamentName);
            }
        }
    }

    /**
     * Helper method to check if a player can start a tournament
     */
    public static boolean canPlayerStartTournament(ServerPlayerEntity player, Tournament tournament) {
        return tournament != null &&
                tournament.getStatus() == Tournament.TournamentStatus.WAITING &&
                player.getUUID().equals(tournament.getHostId());
    }

    /**
     * Add a start button to tournament UI views for hosts
     */
    public static void addStartButtonIfHost(Inventory inventory, Tournament tournament,
                                            ServerPlayerEntity player, int slot) {
        if (canPlayerStartTournament(player, tournament)) {
            ItemStack startButton = new ItemStack(Items.BEACON);
            startButton.setHoverName(new StringTextComponent("Start Tournament")
                    .withStyle(TextFormatting.GREEN, TextFormatting.BOLD));

            List<ITextComponent> startLore = new ArrayList<>();
            startLore.add(new StringTextComponent("Click to start this tournament now!")
                    .withStyle(TextFormatting.YELLOW));
            setItemLore(startButton, startLore);

            CompoundNBT startTag = startButton.getOrCreateTag();
            startTag.putString("GuiAction", "start");
            startTag.putString("TournamentName", tournament.getName());

            // Place the start button
            inventory.setItem(slot, startButton);
            Tournaments.LOGGER.info("Start button added for tournament {} in slot {}",
                    tournament.getName(), slot);
        }
    }

    /**
     * Process GUI actions - with full parameters
     */
    public static void processGuiAction(ServerPlayerEntity player, String action, String tournamentName,
                                        String recurringId, net.minecraft.inventory.container.ClickType clickType) {
        // Prevent recursion
        if (processingAction) {
            Tournaments.LOGGER.warn("Preventing recursive GUI action processing");
            return;
        }

        processingAction = true;

        try {
            com.blissy.tournaments.TournamentManager manager = com.blissy.tournaments.TournamentManager.getInstance();

            // Close container first to prevent issues
            player.closeContainer();

            Tournaments.LOGGER.info("GUI FLOW DEBUG: Processing action '{}'", action);

            // Special handling for create action
            if ("create".equals(action)) {
                Tournaments.LOGGER.info("GUI FLOW DEBUG: Create tournament action detected");
                // Check if we have permission for recurring tournaments
                boolean canCreateRecurring = RecurringTournamentHandler.canCreateRecurringTournament(player);
                boolean canCreatePlayer = RecurringTournamentHandler.canCreatePlayerTournament(player);

                if (canCreateRecurring) {
                    // Ask if they want to create a regular or recurring tournament
                    player.sendMessage(
                            new StringTextComponent("Do you want to create a regular or recurring tournament?")
                                    .withStyle(TextFormatting.YELLOW),
                            player.getUUID());

                    player.sendMessage(
                            new StringTextComponent("Type '/tournament create' for a regular tournament or '/tournament createrecurring' for a recurring tournament")
                                    .withStyle(TextFormatting.YELLOW),
                            player.getUUID());
                    return;
                } else if (canCreatePlayer) {
                    // Open regular tournament creation GUI
                    Tournaments.LOGGER.info("GUI FLOW DEBUG: Opening TournamentCreationGUI");
                    TournamentCreationGUI.openCreationGUI(player);
                    return;
                } else {
                    player.sendMessage(
                            new StringTextComponent("You don't have permission to create tournaments")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                    // Reopen main GUI
                    TournamentMainGUI.openMainGui(player);
                    return;
                }
            }

            // New action for opening recurring tournament creation
            if ("openRecurringCreation".equals(action)) {
                if (RecurringTournamentHandler.canCreateRecurringTournament(player)) {
                    TournamentRecurringCreationGUI.openCreationGUI(player);
                } else {
                    player.sendMessage(
                            new StringTextComponent("You don't have permission to create recurring tournaments")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                    TournamentCreationGUI.openCreationGUI(player);
                }
                return;
            }

            // Add handling for createRecurringTournament action
            if ("createRecurringTournament".equals(action)) {
                if (RecurringTournamentHandler.canCreateRecurringTournament(player)) {
                    // Create the recurring tournament
                    TournamentRecurringCreationGUI.createRecurringTournament(player);
                } else {
                    player.sendMessage(
                            new StringTextComponent("You don't have permission to create recurring tournaments")
                                    .withStyle(TextFormatting.RED),
                            player.getUUID());
                    TournamentMainGUI.openMainGui(player);
                }
                return;
            }

            switch (action) {
                case "join":
                    if (tournamentName != null) {
                        if (manager.joinTournament(tournamentName, player)) {
                            player.sendMessage(
                                    new StringTextComponent("Successfully joined tournament: " + tournamentName)
                                            .withStyle(TextFormatting.GREEN),
                                    player.getUUID());
                        }
                    }
                    // Reopen main GUI after join action
                    TournamentMainGUI.openMainGui(player);
                    break;

                case "leave":
                    if (manager.leaveTournament(player)) {
                        player.sendMessage(
                                new StringTextComponent("Successfully left tournament")
                                        .withStyle(TextFormatting.GREEN),
                                player.getUUID());
                    } else {
                        player.sendMessage(
                                new StringTextComponent("You are not in a tournament")
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                    }
                    // Reopen main GUI after leave action
                    TournamentMainGUI.openMainGui(player);
                    break;

                case "start":
                    com.blissy.tournaments.data.Tournament playerTournament = manager.getPlayerTournament(player);
                    if (playerTournament != null &&
                            player.getUUID().equals(playerTournament.getHostId()) &&
                            playerTournament.getStatus() == com.blissy.tournaments.data.Tournament.TournamentStatus.WAITING) {

                        playerTournament.start();
                        player.sendMessage(
                                new StringTextComponent("Tournament started!")
                                        .withStyle(TextFormatting.GREEN),
                                player.getUUID());
                    } else {
                        player.sendMessage(
                                new StringTextComponent("You cannot start this tournament")
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                    }
                    // Reopen main GUI after start action
                    TournamentMainGUI.openMainGui(player);
                    break;

                case "matches":
                    if (tournamentName != null) {
                        TournamentMainGUI.openMatchesGui(player, tournamentName);
                        // Do NOT reopen main GUI here
                    } else {
                        // If no tournament name provided, go back to main GUI
                        TournamentMainGUI.openMainGui(player);
                    }
                    break;

                case "view_recurring":
                    if (recurringId != null) {
                        // Get the recurring tournament details
                        com.blissy.tournaments.data.RecurringTournament tournament = com.blissy.tournaments.data.RecurringTournament.getRecurringTournament(recurringId);

                        if (tournament != null) {
                            // Display tournament details in chat
                            player.sendMessage(
                                    new StringTextComponent("=== Recurring Tournament: " + tournament.getName() + " ===")
                                            .withStyle(TextFormatting.GOLD),
                                    player.getUUID());

                            player.sendMessage(
                                    new StringTextComponent("Template Name: " + tournament.getTemplateName())
                                            .withStyle(TextFormatting.YELLOW),
                                    player.getUUID());

                            player.sendMessage(
                                    new StringTextComponent("Level Range: " + tournament.getMinLevel() + "-" + tournament.getMaxLevel())
                                            .withStyle(TextFormatting.AQUA),
                                    player.getUUID());

                            player.sendMessage(
                                    new StringTextComponent("Format: " + tournament.getFormat())
                                            .withStyle(TextFormatting.LIGHT_PURPLE),
                                    player.getUUID());

                            player.sendMessage(
                                    new StringTextComponent("Recurrence: Every " + formatHours(tournament.getRecurrenceHours()))
                                            .withStyle(TextFormatting.GREEN),
                                    player.getUUID());

                            player.sendMessage(
                                    new StringTextComponent("Next Occurrence: " + formatTimeUntil(tournament.getNextScheduled()))
                                            .withStyle(TextFormatting.GREEN),
                                    player.getUUID());

                            // Check if right-clicked and has permission to delete
                            if (clickType == net.minecraft.inventory.container.ClickType.values()[1] && RecurringTournamentHandler.canCreateRecurringTournament(player)) {
                                player.sendMessage(
                                        new StringTextComponent("To delete this recurring tournament, type: /tournament deleterecurring " + tournament.getName())
                                                .withStyle(TextFormatting.RED),
                                        player.getUUID());
                            }
                        } else {
                            player.sendMessage(
                                    new StringTextComponent("Recurring tournament not found: " + recurringId)
                                            .withStyle(TextFormatting.RED),
                                    player.getUUID());
                        }
                    }

                    // Reopen the GUI they were on before
                    if ("show_all_recurring".equals(action)) {
                        TournamentMainGUI.openRecurringTournamentsGui(player);
                    } else {
                        TournamentMainGUI.openMainGui(player);
                    }
                    break;

                case "back":
                    // Go back to main GUI
                    TournamentMainGUI.openMainGui(player);
                    break;

                case "show_all_recurring":
                    // Show all recurring tournaments
                    TournamentMainGUI.openRecurringTournamentsGui(player);
                    break;

                case "show_all_player":
                    // Show all player tournaments
                    TournamentMainGUI.openPlayerTournamentsGui(player);
                    break;

                case "create_recurring":
                    if (RecurringTournamentHandler.canCreateRecurringTournament(player)) {
                        // Open recurring tournament creation GUI
                        TournamentRecurringCreationGUI.openCreationGUI(player);
                    } else {
                        player.sendMessage(
                                new StringTextComponent("You don't have permission to create recurring tournaments")
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                        // Reopen main GUI
                        TournamentMainGUI.openMainGui(player);
                    }
                    break;

                case "create_player":
                    if (RecurringTournamentHandler.canCreatePlayerTournament(player)) {
                        // Open regular tournament creation GUI
                        TournamentCreationGUI.openCreationGUI(player);
                    } else {
                        player.sendMessage(
                                new StringTextComponent("You don't have permission to create tournaments")
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                        // Reopen main GUI
                        TournamentMainGUI.openMainGui(player);
                    }
                    break;

                case "reloadconfig":
                    if (player.hasPermissions(2)) {
                        try {
                            // Reload the config
                            com.blissy.tournaments.config.UIConfigLoader.loadConfig(null);
                            com.blissy.tournaments.config.UIConfigLoader.saveConfig();

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
                        // Reopen main GUI
                        TournamentMainGUI.openMainGui(player);
                    }
                    break;

                default:
                    if ("none".equals(action)) {
                        // Just reopen the main GUI for "none" action
                        TournamentMainGUI.openMainGui(player);
                    } else {
                        // Unknown action, just reopen main GUI
                        player.sendMessage(
                                new StringTextComponent("Unknown action: " + action)
                                        .withStyle(TextFormatting.RED),
                                player.getUUID());
                        TournamentMainGUI.openMainGui(player);
                    }
                    break;
            }
        } finally {
            // Always reset the processing flag to prevent deadlocks
            processingAction = false;
        }
    }

    /**
     * Process GUI actions - simpler version that forwards to full version
     */
    public static void processGuiAction(ServerPlayerEntity player, String action, String tournamentName) {
        // Handle create action specially to avoid problems
        if ("create".equals(action)) {
            player.closeContainer();
            Tournaments.LOGGER.info("GUI FLOW DEBUG: Direct create action in 3-param version");

            boolean canCreatePlayer = RecurringTournamentHandler.canCreatePlayerTournament(player);
            if (canCreatePlayer) {
                TournamentCreationGUI.openCreationGUI(player);
                return;
            }
        }

        // For other actions, call the 5-param version (which now has recursion protection)
        processGuiAction(player, action, tournamentName, null, null);
    }


    /**
     * Format time in hours nicely (e.g., "2 hours" or "30 minutes")
     */
    private static String formatHours(double hours) {
        if (hours >= 1) {
            if (hours == Math.floor(hours)) {
                return (int) hours + " hours";
            } else {
                return String.format("%.1f hours", hours);
            }
        } else {
            return (int) (hours * 60) + " minutes";
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