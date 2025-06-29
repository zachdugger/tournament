package com.blissy.tournaments.gui;

import com.blissy.tournaments.TournamentManager;
import com.blissy.tournaments.Tournaments;
import com.blissy.tournaments.config.UIConfigLoader;
import com.blissy.tournaments.data.Tournament;
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
 * GUI for tournament creation
 */
public class TournamentCreationGUI {

    // Default values
    private static final int DEFAULT_MIN_LEVEL = 100;
    private static final int DEFAULT_MAX_LEVEL = 100;
    private static final int DEFAULT_MAX_PARTICIPANTS = 8; // Changed default to 8
    private static final String DEFAULT_FORMAT = "SINGLE_ELIMINATION";
    private static final double DEFAULT_ENTRY_FEE = 0.0;
    private static final double DEFAULT_START_DELAY = 0.0; // Default: start immediately

    /**
     * Open the tournament creation GUI
     */
    public static void openCreationGUI(ServerPlayerEntity player) {
        // Check if player has permission
        boolean isAdmin = player.hasPermissions(2);
        boolean canCreate = RecurringTournamentHandler.canCreatePlayerTournament(player);

        if (!canCreate) {
            player.sendMessage(
                    new StringTextComponent("You don't have permission to create tournaments")
                            .withStyle(TextFormatting.RED),
                    player.getUUID());
            return;
        }

        // Get title from config
        JsonObject config = UIConfigLoader.getCreationScreenConfig();
        String title = config.has("title") ? config.get("title").getAsString() : "Create Tournament";

        // Use container factory to create GUI
        ContainerFactory.openTournamentGui(player, title, (inventory, p) -> {
            populateCreationGUI(inventory, p, isAdmin);
        });
    }

    /**
     * Populate the creation GUI
     */
    private static void populateCreationGUI(Inventory inventory, ServerPlayerEntity player, boolean isAdmin) {
        JsonObject config = UIConfigLoader.getCreationScreenConfig();

        // Apply borders first
        UIConfigLoader.applyBorders(inventory, config);

        // Add back button
        ItemStack backButton = new ItemStack(Items.ARROW);
        backButton.setHoverName(new StringTextComponent("Back").withStyle(TextFormatting.GRAY));
        CompoundNBT backTag = backButton.getOrCreateTag();
        backTag.putString("GuiAction", "back");
        inventory.setItem(UIConfigLoader.getSlot(config, "back_button_slot"), backButton);

        // Add tournament name field
        ItemStack nameItem = new ItemStack(Items.NAME_TAG);
        nameItem.setHoverName(new StringTextComponent("Tournament Name")
                .withStyle(TextFormatting.YELLOW));
        List<ITextComponent> nameLore = new ArrayList<>();
        nameLore.add(new StringTextComponent("Click to set tournament name")
                .withStyle(TextFormatting.GRAY));
        nameLore.add(new StringTextComponent("Required")
                .withStyle(TextFormatting.RED));
        TournamentGuiHandler.setItemLore(nameItem, nameLore);

        CompoundNBT nameTag = nameItem.getOrCreateTag();
        nameTag.putString("GuiAction", "setName");
        inventory.setItem(20, nameItem);

        // For normal players, only show essential settings
        // For admins, show all settings

        // Add tournament size field - only for admins
        if (isAdmin) {
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
            sizeTag.putString("GuiAction", "setMaxParticipants");
            inventory.setItem(22, sizeItem);
        }

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
        minLevelTag.putString("GuiAction", "setMinLevel");
        inventory.setItem(isAdmin ? 24 : 22, minLevelItem);

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
        maxLevelTag.putString("GuiAction", "setMaxLevel");
        inventory.setItem(isAdmin ? 31 : 24, maxLevelItem);

        // Add format field - only for admins
        if (isAdmin) {
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
            formatTag.putString("GuiAction", "setFormat");
            inventory.setItem(33, formatItem);
        }

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
        feeTag.putString("GuiAction", "setEntryFee");
        inventory.setItem(isAdmin ? 40 : 31, feeItem);

        // Add start delay field
        ItemStack delayItem = new ItemStack(Items.CLOCK);
        delayItem.setHoverName(new StringTextComponent("Start Delay")
                .withStyle(TextFormatting.LIGHT_PURPLE));
        List<ITextComponent> delayLore = new ArrayList<>();
        delayLore.add(new StringTextComponent("Click to set delay before tournament starts")
                .withStyle(TextFormatting.GRAY));
        delayLore.add(new StringTextComponent("Default: Start immediately")
                .withStyle(TextFormatting.GRAY));
        delayLore.add(new StringTextComponent("Enter time in hours (e.g. 0.5 for 30 minutes)")
                .withStyle(TextFormatting.GRAY));
        TournamentGuiHandler.setItemLore(delayItem, delayLore);

        CompoundNBT delayTag = delayItem.getOrCreateTag();
        delayTag.putString("GuiAction", "setStartDelay");
        inventory.setItem(isAdmin ? 42 : 33, delayItem);


        // Add create button
        ItemStack createItem = new ItemStack(Items.EMERALD);
        createItem.setHoverName(new StringTextComponent("Create Tournament")
                .withStyle(TextFormatting.GREEN, TextFormatting.BOLD));
        List<ITextComponent> createLore = new ArrayList<>();
        createLore.add(new StringTextComponent("Click to create tournament")
                .withStyle(TextFormatting.GRAY));
        TournamentGuiHandler.setItemLore(createItem, createLore);

        CompoundNBT createTag = createItem.getOrCreateTag();
        createTag.putString("GuiAction", "createTournament");
        inventory.setItem(49, createItem);

        // Add current settings display - show what has been set so far
        ItemStack currentSettings = new ItemStack(Items.PAPER);
        currentSettings.setHoverName(new StringTextComponent("Current Settings")
                .withStyle(TextFormatting.WHITE, TextFormatting.BOLD));

        List<ITextComponent> settingsLore = new ArrayList<>();

        // Get current settings
        String name = getCreationSetting(player, "name");
        String minLevelStr = getCreationSetting(player, "minLevel");
        String maxLevelStr = getCreationSetting(player, "maxLevel");
        String maxParticipantsStr = getCreationSetting(player, "maxParticipants");
        String format = getCreationSetting(player, "format");
        String entryFeeStr = getCreationSetting(player, "entryFee");
        String startDelayStr = getCreationSetting(player, "startDelay");

        // Add them to lore if set
        settingsLore.add(new StringTextComponent("Name: " + (name != null ? name : "Not set"))
                .withStyle(name != null ? TextFormatting.GREEN : TextFormatting.RED));

        settingsLore.add(new StringTextComponent("Min Level: " + (minLevelStr != null ? minLevelStr : DEFAULT_MIN_LEVEL))
                .withStyle(TextFormatting.AQUA));

        settingsLore.add(new StringTextComponent("Max Level: " + (maxLevelStr != null ? maxLevelStr : DEFAULT_MAX_LEVEL))
                .withStyle(TextFormatting.BLUE));

        if (isAdmin || maxParticipantsStr != null) {
            settingsLore.add(new StringTextComponent("Size: " + (maxParticipantsStr != null ? maxParticipantsStr : DEFAULT_MAX_PARTICIPANTS) + " players")
                    .withStyle(TextFormatting.GREEN));
        }

        if (isAdmin || format != null) {
            settingsLore.add(new StringTextComponent("Format: " + (format != null ? format : DEFAULT_FORMAT))
                    .withStyle(TextFormatting.LIGHT_PURPLE));
        }

        settingsLore.add(new StringTextComponent("Entry Fee: " + (entryFeeStr != null ? entryFeeStr : DEFAULT_ENTRY_FEE))
                .withStyle(TextFormatting.GOLD));

        String delayDisplay = "Manual start";
        if (startDelayStr != null) {
            double delay = Double.parseDouble(startDelayStr);
            if (delay > 0) {
                if (delay < 1) {
                    delayDisplay = (int)(delay * 60) + " minutes";
                } else {
                    delayDisplay = String.format("%.1f hours", delay);
                }
            }
        }

        settingsLore.add(new StringTextComponent("Start Delay: " + delayDisplay)
                .withStyle(TextFormatting.YELLOW));

        TournamentGuiHandler.setItemLore(currentSettings, settingsLore);
        inventory.setItem(13, currentSettings);
    }

    /**
     * Create a tournament with the specified or default values
     */
    public static void createTournament(ServerPlayerEntity player, String name, Integer minLevel, Integer maxLevel,
                                        Integer maxParticipants, String format) {
        createTournament(player, name, minLevel, maxLevel, maxParticipants, format, null, null);
    }

    /**
     * Create a tournament with the specified or default values, including entry fee
     */
    public static void createTournament(ServerPlayerEntity player, String name, Integer minLevel, Integer maxLevel,
                                        Integer maxParticipants, String format, Double entryFee) {
        createTournament(player, name, minLevel, maxLevel, maxParticipants, format, entryFee, null);
    }

    /**
     * Create a tournament with all specified values
     */
    public static void createTournament(ServerPlayerEntity player, String name, Integer minLevel, Integer maxLevel,
                                        Integer maxParticipants, String format, Double entryFee, Double startDelay) {
        // Use defaults for any null values
        name = name != null ? name : "Tournament_" + System.currentTimeMillis();
        minLevel = minLevel != null ? minLevel : DEFAULT_MIN_LEVEL;
        maxLevel = maxLevel != null ? maxLevel : DEFAULT_MAX_LEVEL;
        maxParticipants = maxParticipants != null ? maxParticipants : DEFAULT_MAX_PARTICIPANTS;
        format = format != null ? format : DEFAULT_FORMAT;
        entryFee = entryFee != null ? entryFee : DEFAULT_ENTRY_FEE;
        startDelay = startDelay != null ? startDelay : DEFAULT_START_DELAY;

        // Validate name is not empty
        if (name.trim().isEmpty()) {
            player.sendMessage(
                    new StringTextComponent("Tournament name cannot be empty")
                            .withStyle(TextFormatting.RED),
                    player.getUUID());
            return;
        }

        // Create the tournament
        TournamentManager manager = TournamentManager.getInstance();
        // Remove the battleFormat parameter since it's not expected by the method
        if (manager.createTournament(name, maxParticipants, player)) {
            // Set tournament settings
            manager.setTournamentSettings(name, minLevel, maxLevel, format);

            // Store entry fee and start delay in tournament settings
            CompoundNBT extraSettings = new CompoundNBT();
            extraSettings.putDouble("entryFee", entryFee);
            extraSettings.putDouble("startDelay", startDelay);
            manager.setTournamentExtraSettings(name, extraSettings);

            // Set the scheduled start time if applicable
            if (startDelay > 0) {
                manager.setTournamentScheduledStart(name, startDelay);
            }

            String startInfo = startDelay > 0 ?
                    " | Starts in: " + formatTime(startDelay) :
                    " | Starts: When manually started";

            player.sendMessage(
                    new StringTextComponent("Tournament '" + name + "' created successfully!")
                            .withStyle(TextFormatting.GREEN),
                    player.getUUID());

            player.sendMessage(
                    new StringTextComponent("Settings: Lvl " + minLevel + "-" + maxLevel +
                            " | Format: " + format +
                            " | Max Players: " + maxParticipants +
                            " | Entry Fee: " + entryFee +
                            startInfo)
                            .withStyle(TextFormatting.YELLOW),
                    player.getUUID());

            // Open the main GUI to see the new tournament
            TournamentMainGUI.openMainGui(player);
        } else {
            player.sendMessage(
                    new StringTextComponent("Failed to create tournament. Name may already be in use.")
                            .withStyle(TextFormatting.RED),
                    player.getUUID());
        }
    }

    /**
     * Store creation settings in a temporary NBT tag
     * This is a simple way to store settings during creation
     */
    public static void storeCreationSetting(ServerPlayerEntity player, String key, String value) {
        CompoundNBT playerData = player.getPersistentData();
        CompoundNBT tournamentSettings;

        if (playerData.contains("TournamentSettings")) {
            tournamentSettings = playerData.getCompound("TournamentSettings");
        } else {
            tournamentSettings = new CompoundNBT();
            playerData.put("TournamentSettings", tournamentSettings);
        }

        tournamentSettings.putString(key, value);
        Tournaments.LOGGER.debug("Stored tournament setting: {}={}", key, value);
    }

    /**
     * Get creation setting from persistent player data
     */
    public static String getCreationSetting(ServerPlayerEntity player, String key) {
        CompoundNBT playerData = player.getPersistentData();

        if (!playerData.contains("TournamentSettings")) {
            return null;
        }

        CompoundNBT tournamentSettings = playerData.getCompound("TournamentSettings");
        return tournamentSettings.contains(key) ? tournamentSettings.getString(key) : null;
    }

    /**
     * Helper method to format time nicely
     */
    private static String formatTime(double hours) {
        if (hours >= 1) {
            return String.format("%.1f hours", hours);
        } else {
            return String.format("%.0f minutes", hours * 60);
        }
    }
}