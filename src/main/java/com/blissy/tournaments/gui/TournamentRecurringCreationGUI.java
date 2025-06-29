package com.blissy.tournaments.gui;

import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.config.UIConfigLoader;
import com.blissy.tournaments.data.RecurringTournament;
import com.blissy.tournaments.handlers.RecurringTournamentHandler;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for recurring tournament creation
 */
public class TournamentRecurringCreationGUI {

    // Default values
    private static final int DEFAULT_MIN_LEVEL = 100;
    private static final int DEFAULT_MAX_LEVEL = 100;
    private static final int DEFAULT_MAX_PARTICIPANTS = 8;
    private static final String DEFAULT_FORMAT = "SINGLE_ELIMINATION";
    private static final double DEFAULT_ENTRY_FEE = 0.0;
    private static final double DEFAULT_RECURRENCE_HOURS = 24.0; // Default: once per day

    /**
     * Open the recurring tournament creation GUI
     */
    public static void openCreationGUI(ServerPlayerEntity player) {
        // Check if player has permission
        if (!RecurringTournamentHandler.canCreateRecurringTournament(player)) {
            player.sendMessage(
                    new StringTextComponent("You don't have permission to create recurring tournaments")
                            .withStyle(TextFormatting.RED),
                    player.getUUID());
            return;
        }

        // Get title from config
        JsonObject config = UIConfigLoader.getCreationScreenConfig();
        String title = "Create Recurring Tournament";

        // Use container factory to create GUI
        ContainerFactory.openTournamentGui(player, title, (inventory, p) -> {
            populateCreationGUI(inventory, p);
        });
    }

    /**
     * Populate the recurring tournament creation GUI
     */
    private static void populateCreationGUI(Inventory inventory, ServerPlayerEntity player) {
        JsonObject config = UIConfigLoader.getCreationScreenConfig();

        // Apply borders first
        UIConfigLoader.applyBorders(inventory, config);

        // Add back button
        ItemStack backButton = new ItemStack(Items.ARROW);
        backButton.setHoverName(new StringTextComponent("Back").withStyle(TextFormatting.GRAY));
        CompoundNBT backTag = backButton.getOrCreateTag();
        backTag.putString("GuiAction", "back");
        inventory.setItem(UIConfigLoader.getSlot(config, "back_button_slot"), backButton);

        // Add tournament ID field
        ItemStack idItem = new ItemStack(Items.NAME_TAG);
        idItem.setHoverName(new StringTextComponent("Recurring Tournament ID")
                .withStyle(TextFormatting.RED));
        List<ITextComponent> idLore = new ArrayList<>();
        idLore.add(new StringTextComponent("Click to set recurring tournament ID")
                .withStyle(TextFormatting.GRAY));
        idLore.add(new StringTextComponent("This is a unique identifier for this recurring tournament")
                .withStyle(TextFormatting.GRAY));
        idLore.add(new StringTextComponent("Required")
                .withStyle(TextFormatting.RED));
        TournamentGuiHandler.setItemLore(idItem, idLore);

        CompoundNBT idTag = idItem.getOrCreateTag();
        idTag.putString("GuiAction", "setRecurringId");
        inventory.setItem(20, idItem);

        // Add tournament template name field
        ItemStack nameItem = new ItemStack(Items.NAME_TAG);
        nameItem.setHoverName(new StringTextComponent("Tournament Template Name")
                .withStyle(TextFormatting.YELLOW));
        List<ITextComponent> nameLore = new ArrayList<>();
        nameLore.add(new StringTextComponent("Click to set tournament template name")
                .withStyle(TextFormatting.GRAY));
        nameLore.add(new StringTextComponent("This is the base name for each tournament instance")
                .withStyle(TextFormatting.GRAY));
        nameLore.add(new StringTextComponent("Required")
                .withStyle(TextFormatting.RED));
        TournamentGuiHandler.setItemLore(nameItem, nameLore);

        CompoundNBT nameTag = nameItem.getOrCreateTag();
        nameTag.putString("GuiAction", "setRecurringTemplateName");
        inventory.setItem(22, nameItem);

        // Add tournament size field
        ItemStack sizeItem = new ItemStack(Items.PLAYER_HEAD);
        sizeItem.setHoverName(new StringTextComponent("Tournament Size")
                .withStyle(TextFormatting.GREEN));
        List<ITextComponent> sizeLore = new ArrayList<>();
        sizeLore.add(new StringTextComponent("Click to set tournament size")
                .withStyle(TextFormatting.GRAY));
        sizeLore.add(new StringTextComponent("Default: " + DEFAULT_MAX_PARTICIPANTS + " players")
                .withStyle(TextFormatting.GRAY));
        TournamentGuiHandler.setItemLore(sizeItem, sizeLore);

        CompoundNBT sizeTag = sizeItem.getOrCreateTag();
        sizeTag.putString("GuiAction", "setRecurringMaxParticipants");
        inventory.setItem(24, sizeItem);

        // Add min level field
        ItemStack minLevelItem = new ItemStack(Items.IRON_SWORD);
        minLevelItem.setHoverName(new StringTextComponent("Min Pokemon Level")
                .withStyle(TextFormatting.AQUA));
        List<ITextComponent> minLevelLore = new ArrayList<>();
        minLevelLore.add(new StringTextComponent("Click to set minimum level")
                .withStyle(TextFormatting.GRAY));
        minLevelLore.add(new StringTextComponent("Default: " + DEFAULT_MIN_LEVEL)
                .withStyle(TextFormatting.GRAY));
        TournamentGuiHandler.setItemLore(minLevelItem, minLevelLore);

        CompoundNBT minLevelTag = minLevelItem.getOrCreateTag();
        minLevelTag.putString("GuiAction", "setRecurringMinLevel");
        inventory.setItem(29, minLevelItem);

        // Add max level field
        ItemStack maxLevelItem = new ItemStack(Items.DIAMOND_SWORD);
        maxLevelItem.setHoverName(new StringTextComponent("Max Pokemon Level")
                .withStyle(TextFormatting.BLUE));
        List<ITextComponent> maxLevelLore = new ArrayList<>();
        maxLevelLore.add(new StringTextComponent("Click to set maximum level")
                .withStyle(TextFormatting.GRAY));
        maxLevelLore.add(new StringTextComponent("Default: " + DEFAULT_MAX_LEVEL)
                .withStyle(TextFormatting.GRAY));
        TournamentGuiHandler.setItemLore(maxLevelItem, maxLevelLore);

        CompoundNBT maxLevelTag = maxLevelItem.getOrCreateTag();
        maxLevelTag.putString("GuiAction", "setRecurringMaxLevel");
        inventory.setItem(31, maxLevelItem);

        // Add format field
        ItemStack formatItem = new ItemStack(Items.BOOKSHELF);
        formatItem.setHoverName(new StringTextComponent("Tournament Format")
                .withStyle(TextFormatting.LIGHT_PURPLE));
        List<ITextComponent> formatLore = new ArrayList<>();
        formatLore.add(new StringTextComponent("Click to set format")
                .withStyle(TextFormatting.GRAY));
        formatLore.add(new StringTextComponent("Default: " + DEFAULT_FORMAT)
                .withStyle(TextFormatting.GRAY));
        TournamentGuiHandler.setItemLore(formatItem, formatLore);

        CompoundNBT formatTag = formatItem.getOrCreateTag();
        formatTag.putString("GuiAction", "setRecurringFormat");
        inventory.setItem(33, formatItem);

        // Add entry fee field
        ItemStack feeItem = new ItemStack(Items.GOLD_INGOT);
        feeItem.setHoverName(new StringTextComponent("Entry Fee")
                .withStyle(TextFormatting.GOLD));
        List<ITextComponent> feeLore = new ArrayList<>();
        feeLore.add(new StringTextComponent("Click to set entry fee")
                .withStyle(TextFormatting.GRAY));
        feeLore.add(new StringTextComponent("Default: " + DEFAULT_ENTRY_FEE)
                .withStyle(TextFormatting.GRAY));
        TournamentGuiHandler.setItemLore(feeItem, feeLore);

        CompoundNBT feeTag = feeItem.getOrCreateTag();
        feeTag.putString("GuiAction", "setRecurringEntryFee");
        inventory.setItem(38, feeItem);

        // Add recurrence interval field
        ItemStack intervalItem = new ItemStack(Items.CLOCK);
        intervalItem.setHoverName(new StringTextComponent("Recurrence Interval (Hours)")
                .withStyle(TextFormatting.LIGHT_PURPLE));
        List<ITextComponent> intervalLore = new ArrayList<>();
        intervalLore.add(new StringTextComponent("Click to set how often this tournament repeats")
                .withStyle(TextFormatting.GRAY));
        intervalLore.add(new StringTextComponent("Default: " + DEFAULT_RECURRENCE_HOURS + " hours")
                .withStyle(TextFormatting.GRAY));
        intervalLore.add(new StringTextComponent("Enter time in hours (e.g. 24 for daily, 168 for weekly)")
                .withStyle(TextFormatting.GRAY));
        TournamentGuiHandler.setItemLore(intervalItem, intervalLore);

        CompoundNBT intervalTag = intervalItem.getOrCreateTag();
        intervalTag.putString("GuiAction", "setRecurringInterval");
        inventory.setItem(40, intervalItem);

        // Add create button
        ItemStack createItem = new ItemStack(Items.EMERALD);
        createItem.setHoverName(new StringTextComponent("Create Recurring Tournament")
                .withStyle(TextFormatting.GREEN, TextFormatting.BOLD));
        List<ITextComponent> createLore = new ArrayList<>();
        createLore.add(new StringTextComponent("Click to create recurring tournament")
                .withStyle(TextFormatting.GRAY));
        TournamentGuiHandler.setItemLore(createItem, createLore);

        CompoundNBT createTag = createItem.getOrCreateTag();
        createTag.putString("GuiAction", "createRecurringTournament");
        inventory.setItem(49, createItem);

        // Add current settings display - show what has been set so far
        ItemStack currentSettings = new ItemStack(Items.PAPER);
        currentSettings.setHoverName(new StringTextComponent("Current Settings")
                .withStyle(TextFormatting.WHITE, TextFormatting.BOLD));

        List<ITextComponent> settingsLore = new ArrayList<>();

        // Get current settings
        String id = getRecurringCreationSetting(player, "recurringId");
        String templateName = getRecurringCreationSetting(player, "templateName");
        String minLevelStr = getRecurringCreationSetting(player, "minLevel");
        String maxLevelStr = getRecurringCreationSetting(player, "maxLevel");
        String maxParticipantsStr = getRecurringCreationSetting(player, "maxParticipants");
        String format = getRecurringCreationSetting(player, "format");
        String entryFeeStr = getRecurringCreationSetting(player, "entryFee");
        String recurrenceIntervalStr = getRecurringCreationSetting(player, "recurrenceInterval");

        // Add them to lore if set
        settingsLore.add(new StringTextComponent("Recurring ID: " + (id != null ? id : "Not set"))
                .withStyle(id != null ? TextFormatting.GREEN : TextFormatting.RED));

        settingsLore.add(new StringTextComponent("Template Name: " + (templateName != null ? templateName : "Not set"))
                .withStyle(templateName != null ? TextFormatting.GREEN : TextFormatting.RED));

        settingsLore.add(new StringTextComponent("Min Level: " + (minLevelStr != null ? minLevelStr : DEFAULT_MIN_LEVEL))
                .withStyle(TextFormatting.AQUA));

        settingsLore.add(new StringTextComponent("Max Level: " + (maxLevelStr != null ? maxLevelStr : DEFAULT_MAX_LEVEL))
                .withStyle(TextFormatting.BLUE));

        settingsLore.add(new StringTextComponent("Size: " + (maxParticipantsStr != null ? maxParticipantsStr : DEFAULT_MAX_PARTICIPANTS) + " players")
                .withStyle(TextFormatting.GREEN));

        settingsLore.add(new StringTextComponent("Format: " + (format != null ? format : DEFAULT_FORMAT))
                .withStyle(TextFormatting.LIGHT_PURPLE));

        settingsLore.add(new StringTextComponent("Entry Fee: " + (entryFeeStr != null ? entryFeeStr : DEFAULT_ENTRY_FEE))
                .withStyle(TextFormatting.GOLD));

        settingsLore.add(new StringTextComponent("Recurrence: Every " +
                (recurrenceIntervalStr != null ? formatHours(Double.parseDouble(recurrenceIntervalStr)) : formatHours(DEFAULT_RECURRENCE_HOURS)))
                .withStyle(TextFormatting.YELLOW));

        TournamentGuiHandler.setItemLore(currentSettings, settingsLore);
        inventory.setItem(13, currentSettings);
    }

    /**
     * Create a recurring tournament with the specified or default values
     */
    public static void createRecurringTournament(ServerPlayerEntity player) {
        try {
            // Get all settings from player data
            String id = getRecurringCreationSetting(player, "recurringId");
            String templateName = getRecurringCreationSetting(player, "templateName");
            String minLevelStr = getRecurringCreationSetting(player, "minLevel");
            String maxLevelStr = getRecurringCreationSetting(player, "maxLevel");
            String maxParticipantsStr = getRecurringCreationSetting(player, "maxParticipants");
            String format = getRecurringCreationSetting(player, "format");
            String entryFeeStr = getRecurringCreationSetting(player, "entryFee");
            String recurrenceIntervalStr = getRecurringCreationSetting(player, "recurrenceInterval");

            Tournaments.LOGGER.info("Creating recurring tournament with settings: id={}, template={}, minLevel={}, maxLevel={}, maxParticipants={}, format={}, entryFee={}, interval={}",
                    id, templateName, minLevelStr, maxLevelStr, maxParticipantsStr, format, entryFeeStr, recurrenceIntervalStr);

            // Check required fields
            if (id == null || id.trim().isEmpty()) {
                player.sendMessage(
                        new StringTextComponent("Recurring Tournament ID cannot be empty")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                openCreationGUI(player);
                return;
            }

            if (templateName == null || templateName.trim().isEmpty()) {
                player.sendMessage(
                        new StringTextComponent("Tournament Template Name cannot be empty")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                openCreationGUI(player);
                return;
            }

            // Check if ID already exists
            if (RecurringTournament.getRecurringTournament(id) != null) {
                player.sendMessage(
                        new StringTextComponent("A recurring tournament with this ID already exists")
                                .withStyle(TextFormatting.RED),
                        player.getUUID());
                openCreationGUI(player);
                return;
            }

            // Parse values with defaults
            int minLevel = minLevelStr != null ? Integer.parseInt(minLevelStr) : DEFAULT_MIN_LEVEL;
            int maxLevel = maxLevelStr != null ? Integer.parseInt(maxLevelStr) : DEFAULT_MAX_LEVEL;
            int maxParticipants = maxParticipantsStr != null ? Integer.parseInt(maxParticipantsStr) : DEFAULT_MAX_PARTICIPANTS;
            String tournamentFormat = format != null ? format : DEFAULT_FORMAT;
            double entryFee = entryFeeStr != null ? Double.parseDouble(entryFeeStr) : DEFAULT_ENTRY_FEE;
            double recurrenceHours = recurrenceIntervalStr != null ? Double.parseDouble(recurrenceIntervalStr) : DEFAULT_RECURRENCE_HOURS;

            // Create the recurring tournament
            RecurringTournament tournament = new RecurringTournament(
                    id, templateName, minLevel, maxLevel, maxParticipants,
                    tournamentFormat, entryFee, recurrenceHours, player.getUUID());

            RecurringTournament.addRecurringTournament(tournament);

            // Notify the player
            player.sendMessage(
                    new StringTextComponent("Recurring tournament created successfully: " + id)
                            .withStyle(TextFormatting.GREEN),
                    player.getUUID());

            player.sendMessage(
                    new StringTextComponent("It will create a new tournament instance every " + formatHours(recurrenceHours))
                            .withStyle(TextFormatting.YELLOW),
                    player.getUUID());

            // Create first instance immediately with a better name
            String instanceName = templateName + "_" + System.currentTimeMillis();

            // Create the tournament instance
            TournamentManager manager = TournamentManager.getInstance();
            if (manager.createTournament(instanceName, maxParticipants, player)) {
                // Set tournament settings
                manager.setTournamentSettings(instanceName, minLevel, maxLevel, format);

                // Store entry fee and recurring info in tournament settings
                CompoundNBT extraSettings = new CompoundNBT();
                extraSettings.putDouble("entryFee", entryFee);
                extraSettings.putBoolean("isRecurring", true);
                extraSettings.putString("recurringId", id);
                manager.setTournamentExtraSettings(instanceName, extraSettings);

                player.sendMessage(
                        new StringTextComponent("First tournament instance created: " + instanceName)
                                .withStyle(TextFormatting.GREEN),
                        player.getUUID());

                player.sendMessage(
                        new StringTextComponent("Type /tournament join " + instanceName + " to join")
                                .withStyle(TextFormatting.YELLOW),
                        player.getUUID());
            }

            // Try to create first instance immediately
            Tournaments.LOGGER.info("Attempting to create first instance of recurring tournament {}", id);
            tournament.checkAndCreateTournament();

            // Open the main GUI to see the new recurring tournament
            TournamentMainGUI.openMainGui(player);
        } catch (Exception e) {
            Tournaments.LOGGER.error("Error creating recurring tournament", e);
            player.sendMessage(
                    new StringTextComponent("Error creating recurring tournament: " + e.getMessage())
                            .withStyle(TextFormatting.RED),
                    player.getUUID());

            // Reopen the creation GUI
            openCreationGUI(player);
        }
    }

    /**
     * Store recurring creation settings in a temporary NBT tag
     */
    public static void storeRecurringCreationSetting(ServerPlayerEntity player, String key, String value) {
        CompoundNBT playerData = player.getPersistentData();
        CompoundNBT settings;

        if (playerData.contains("RecurringSettings")) {
            settings = playerData.getCompound("RecurringSettings");
        } else {
            settings = new CompoundNBT();
            playerData.put("RecurringSettings", settings);
        }

        settings.putString(key, value);
        Tournaments.LOGGER.debug("Stored recurring tournament setting: {}={}", key, value);
    }

    /**
     * Get recurring creation setting from persistent player data
     */
    public static String getRecurringCreationSetting(ServerPlayerEntity player, String key) {
        CompoundNBT playerData = player.getPersistentData();

        if (!playerData.contains("RecurringSettings")) {
            return null;
        }

        CompoundNBT settings = playerData.getCompound("RecurringSettings");
        return settings.contains(key) ? settings.getString(key) : null;
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
}