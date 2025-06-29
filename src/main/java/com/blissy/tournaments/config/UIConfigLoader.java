package com.blissy.tournaments.config;

import com.blissy.tournaments.Tournaments;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and provides access to the UI configuration
 */
public class UIConfigLoader {
    private static final ResourceLocation CONFIG_LOCATION = new ResourceLocation("tournaments", "ui_config.json");
    private static JsonObject config;
    private static final Map<String, Item> ITEM_MAP = new HashMap<>();
    private static final Map<String, TextFormatting> COLOR_MAP = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configFile;

    static {
        // Initialize item map
        ITEM_MAP.put("minecraft:nether_star", Items.NETHER_STAR);
        ITEM_MAP.put("minecraft:golden_apple", Items.GOLDEN_APPLE);
        ITEM_MAP.put("minecraft:redstone", Items.REDSTONE);
        ITEM_MAP.put("minecraft:emerald", Items.EMERALD);
        ITEM_MAP.put("minecraft:beacon", Items.BEACON);
        ITEM_MAP.put("minecraft:arrow", Items.ARROW);
        ITEM_MAP.put("minecraft:golden_helmet", Items.GOLDEN_HELMET);
        ITEM_MAP.put("minecraft:paper", Items.PAPER);
        ITEM_MAP.put("minecraft:book", Items.BOOK);
        ITEM_MAP.put("minecraft:writable_book", Items.WRITABLE_BOOK);
        ITEM_MAP.put("minecraft:diamond_sword", Items.DIAMOND_SWORD);
        ITEM_MAP.put("minecraft:clock", Items.CLOCK);
        ITEM_MAP.put("minecraft:barrier", Items.BARRIER);
        ITEM_MAP.put("minecraft:gold_ingot", Items.GOLD_INGOT);
        ITEM_MAP.put("minecraft:player_head", Items.PLAYER_HEAD);
        ITEM_MAP.put("minecraft:iron_sword", Items.IRON_SWORD);
        ITEM_MAP.put("minecraft:name_tag", Items.NAME_TAG);
        ITEM_MAP.put("minecraft:bookshelf", Items.BOOKSHELF);
        ITEM_MAP.put("minecraft:comparator", Items.COMPARATOR);

        // Glass panes for borders
        ITEM_MAP.put("minecraft:black_stained_glass_pane", Items.BLACK_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:gray_stained_glass_pane", Items.GRAY_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:white_stained_glass_pane", Items.WHITE_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:blue_stained_glass_pane", Items.BLUE_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:light_blue_stained_glass_pane", Items.LIGHT_BLUE_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:cyan_stained_glass_pane", Items.CYAN_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:green_stained_glass_pane", Items.GREEN_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:lime_stained_glass_pane", Items.LIME_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:yellow_stained_glass_pane", Items.YELLOW_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:orange_stained_glass_pane", Items.ORANGE_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:red_stained_glass_pane", Items.RED_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:pink_stained_glass_pane", Items.PINK_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:purple_stained_glass_pane", Items.PURPLE_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:magenta_stained_glass_pane", Items.MAGENTA_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:brown_stained_glass_pane", Items.BROWN_STAINED_GLASS_PANE);
        ITEM_MAP.put("minecraft:light_gray_stained_glass_pane", Items.LIGHT_GRAY_STAINED_GLASS_PANE);

        // Initialize color map
        COLOR_MAP.put("black", TextFormatting.BLACK);
        COLOR_MAP.put("dark_blue", TextFormatting.DARK_BLUE);
        COLOR_MAP.put("dark_green", TextFormatting.DARK_GREEN);
        COLOR_MAP.put("dark_aqua", TextFormatting.DARK_AQUA);
        COLOR_MAP.put("dark_red", TextFormatting.DARK_RED);
        COLOR_MAP.put("dark_purple", TextFormatting.DARK_PURPLE);
        COLOR_MAP.put("gold", TextFormatting.GOLD);
        COLOR_MAP.put("gray", TextFormatting.GRAY);
        COLOR_MAP.put("dark_gray", TextFormatting.DARK_GRAY);
        COLOR_MAP.put("blue", TextFormatting.BLUE);
        COLOR_MAP.put("green", TextFormatting.GREEN);
        COLOR_MAP.put("aqua", TextFormatting.AQUA);
        COLOR_MAP.put("red", TextFormatting.RED);
        COLOR_MAP.put("light_purple", TextFormatting.LIGHT_PURPLE);
        COLOR_MAP.put("yellow", TextFormatting.YELLOW);
        COLOR_MAP.put("white", TextFormatting.WHITE);
    }

    /**
     * Load the UI configuration
     */
    public static void loadConfig(IResourceManager resourceManager) {
        try {
            // Try to load from config folder first
            configFile = Paths.get("config", "tournaments", "ui_config.json");

            // Ensure parent directories exist
            Files.createDirectories(configFile.getParent());

            // Check if config file exists
            if (Files.exists(configFile)) {
                try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                    config = GSON.fromJson(reader, JsonObject.class);
                    Tournaments.LOGGER.info("Tournament UI configuration loaded from file: {}", configFile);
                    return;
                } catch (IOException e) {
                    Tournaments.LOGGER.warn("Error reading UI config file, falling back to defaults", e);
                }
            }

            // If not found in config folder, try resource manager
            if (resourceManager != null) {
                try {
                    IResource resource = resourceManager.getResource(CONFIG_LOCATION);
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                        config = GSON.fromJson(reader, JsonObject.class);
                        Tournaments.LOGGER.info("Tournament UI configuration loaded from resources");
                        // Save to config folder for editing
                        saveConfig();
                        return;
                    }
                } catch (IOException e) {
                    Tournaments.LOGGER.warn("Could not load tournament UI configuration from resources", e);
                }
            }

            // If we get here, use default config
            Tournaments.LOGGER.warn("Using default UI configuration");
            config = createDefaultConfig();
            saveConfig();

        } catch (Exception e) {
            Tournaments.LOGGER.error("Error loading tournament UI configuration", e);
            // Create a default empty config
            config = createDefaultConfig();
            try {
                saveConfig();
            } catch (IOException saveError) {
                Tournaments.LOGGER.error("Failed to save default config", saveError);
            }
        }
    }

    /**
     * Save the current configuration to the config folder
     */
    public static void saveConfig() throws IOException {
        if (config == null) {
            config = createDefaultConfig();
        }

        if (configFile == null) {
            configFile = Paths.get("config", "tournaments", "ui_config.json");
            Files.createDirectories(configFile.getParent());
        }

        try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
            Tournaments.LOGGER.info("Tournament UI configuration saved to: {}", configFile);
        }
    }

    /**
     * Create default configuration when file can't be loaded
     */
    private static JsonObject createDefaultConfig() {
        // Basic default config
        JsonObject defaultConfig = new JsonObject();

        // Main screen
        JsonObject mainScreen = new JsonObject();
        mainScreen.addProperty("title", "Tournament Hub");
        mainScreen.addProperty("title_item", "minecraft:nether_star");

        // Slots
        JsonObject mainSlots = new JsonObject();
        mainSlots.addProperty("title_slot", 4);
        mainSlots.addProperty("join_button_slot", 2);
        mainSlots.addProperty("leave_button_slot", 2);
        mainSlots.addProperty("create_button_slot", 8);
        mainSlots.addProperty("start_button_slot", 6);
        mainSlots.addProperty("tournaments_start_slot", 18);
        mainScreen.add("slots", mainSlots);

        // Items
        JsonObject mainItems = new JsonObject();

        // Join button
        JsonObject joinButton = new JsonObject();
        joinButton.addProperty("item", "minecraft:golden_apple");
        joinButton.addProperty("name", "Join Tournament");
        joinButton.addProperty("color", "yellow");
        joinButton.addProperty("action", "join");
        mainItems.add("join_button", joinButton);

        // Leave button
        JsonObject leaveButton = new JsonObject();
        leaveButton.addProperty("item", "minecraft:redstone");
        leaveButton.addProperty("name", "Leave Tournament");
        leaveButton.addProperty("color", "red");
        leaveButton.addProperty("action", "leave");
        mainItems.add("leave_button", leaveButton);

        // Create button
        JsonObject createButton = new JsonObject();
        createButton.addProperty("item", "minecraft:emerald");
        createButton.addProperty("name", "Create Tournament");
        createButton.addProperty("color", "green");
        createButton.addProperty("action", "create");
        mainItems.add("create_button", createButton);

        // Start button
        JsonObject startButton = new JsonObject();
        startButton.addProperty("item", "minecraft:beacon");
        startButton.addProperty("name", "Start Tournament");
        startButton.addProperty("color", "aqua");
        startButton.addProperty("action", "start");
        mainItems.add("start_button", startButton);

        // Back button
        JsonObject backButton = new JsonObject();
        backButton.addProperty("item", "minecraft:arrow");
        backButton.addProperty("name", "Back");
        backButton.addProperty("color", "gray");
        backButton.addProperty("action", "back");
        mainItems.add("back_button", backButton);

        mainScreen.add("items", mainItems);

        // Tournament items
        JsonObject tournamentItems = new JsonObject();

        // Player tournament
        JsonObject playerTournament = new JsonObject();
        playerTournament.addProperty("item", "minecraft:golden_helmet");
        tournamentItems.add("player_tournament", playerTournament);

        // Waiting tournament
        JsonObject waitingTournament = new JsonObject();
        waitingTournament.addProperty("item", "minecraft:paper");
        tournamentItems.add("waiting", waitingTournament);

        // In progress tournament
        JsonObject inProgressTournament = new JsonObject();
        inProgressTournament.addProperty("item", "minecraft:book");
        tournamentItems.add("in_progress", inProgressTournament);

        // Ended tournament
        JsonObject endedTournament = new JsonObject();
        endedTournament.addProperty("item", "minecraft:writable_book");
        tournamentItems.add("ended", endedTournament);

        mainScreen.add("tournament_items", tournamentItems);

        // Add borders
        JsonObject borders = new JsonObject();

        // Outer border
        JsonObject outerBorder = new JsonObject();
        outerBorder.addProperty("item", "minecraft:black_stained_glass_pane");
        outerBorder.addProperty("name", " ");

        JsonArray outerSlots = new JsonArray();
        for (int i : new int[]{0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 45, 46, 47, 48, 49, 50, 51, 52, 53}) {
            outerSlots.add(i);
        }
        outerBorder.add("slots", outerSlots);
        borders.add("outer_border", outerBorder);

        // Inner border
        JsonObject innerBorder = new JsonObject();
        innerBorder.addProperty("item", "minecraft:gray_stained_glass_pane");
        innerBorder.addProperty("name", " ");

        JsonArray innerSlots = new JsonArray();
        for (int i : new int[]{10, 16, 19, 25, 28, 34, 37, 43}) {
            innerSlots.add(i);
        }
        innerBorder.add("slots", innerSlots);
        borders.add("inner_border", innerBorder);

        mainScreen.add("borders", borders);

        // Add main screen to config
        defaultConfig.add("main_screen", mainScreen);

        // Add matches screen
        JsonObject matchesScreen = new JsonObject();
        matchesScreen.addProperty("title", "Tournament Matches");
        matchesScreen.addProperty("title_item", "minecraft:diamond_sword");

        // Matches screen slots
        JsonObject matchesSlots = new JsonObject();
        matchesSlots.addProperty("title_slot", 4);
        matchesSlots.addProperty("back_button_slot", 0);
        matchesSlots.addProperty("matches_start_slot", 18);
        matchesScreen.add("slots", matchesSlots);

        // Matches screen items
        JsonObject matchesItems = new JsonObject();

        // Scheduled match item
        JsonObject scheduledMatch = new JsonObject();
        scheduledMatch.addProperty("item", "minecraft:clock");
        scheduledMatch.addProperty("name", "Scheduled Match");
        scheduledMatch.addProperty("color", "aqua");
        matchesItems.add("scheduled_match", scheduledMatch);

        // In progress match item
        JsonObject inProgressMatch = new JsonObject();
        inProgressMatch.addProperty("item", "minecraft:diamond_sword");
        inProgressMatch.addProperty("name", "In Progress Match");
        inProgressMatch.addProperty("color", "gold");
        matchesItems.add("in_progress_match", inProgressMatch);

        // Completed match item
        JsonObject completedMatch = new JsonObject();
        completedMatch.addProperty("item", "minecraft:emerald");
        completedMatch.addProperty("name", "Completed Match");
        completedMatch.addProperty("color", "green");
        matchesItems.add("completed_match", completedMatch);

        // Cancelled match item
        JsonObject cancelledMatch = new JsonObject();
        cancelledMatch.addProperty("item", "minecraft:barrier");
        cancelledMatch.addProperty("name", "Cancelled Match");
        cancelledMatch.addProperty("color", "red");
        matchesItems.add("cancelled_match", cancelledMatch);

        matchesScreen.add("items", matchesItems);

        // Add matches screen borders
        JsonObject matchesBorders = new JsonObject();

        // Outer border
        JsonObject matchesOuterBorder = new JsonObject();
        matchesOuterBorder.addProperty("item", "minecraft:blue_stained_glass_pane");
        matchesOuterBorder.addProperty("name", " ");

        JsonArray matchesOuterSlots = new JsonArray();
        for (int i : new int[]{0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 45, 46, 47, 48, 49, 50, 51, 52, 53}) {
            matchesOuterSlots.add(i);
        }
        matchesOuterBorder.add("slots", matchesOuterSlots);
        matchesBorders.add("outer_border", matchesOuterBorder);

        // Inner border
        JsonObject matchesInnerBorder = new JsonObject();
        matchesInnerBorder.addProperty("item", "minecraft:light_blue_stained_glass_pane");
        matchesInnerBorder.addProperty("name", " ");

        JsonArray matchesInnerSlots = new JsonArray();
        for (int i : new int[]{10, 16, 27, 35, 36, 44}) {
            matchesInnerSlots.add(i);
        }
        matchesInnerBorder.add("slots", matchesInnerSlots);
        matchesBorders.add("inner_border", matchesInnerBorder);

        matchesScreen.add("borders", matchesBorders);

        defaultConfig.add("matches_screen", matchesScreen);

        // Add creation screen
        JsonObject creationScreen = new JsonObject();
        creationScreen.addProperty("title", "Create Tournament");
        creationScreen.addProperty("title_item", "minecraft:writable_book");

        // Creation screen slots
        JsonObject creationSlots = new JsonObject();
        creationSlots.addProperty("title_slot", 4);
        creationSlots.addProperty("back_button_slot", 0);
        creationScreen.add("slots", creationSlots);

        // Creation screen items
        JsonObject creationItems = new JsonObject();

        // Tournament name field
        JsonObject nameField = new JsonObject();
        nameField.addProperty("item", "minecraft:name_tag");
        nameField.addProperty("name", "Tournament Name");
        nameField.addProperty("color", "yellow");
        nameField.addProperty("action", "setName");
        creationItems.add("name_field", nameField);

        // Add min level field
        JsonObject minLevelField = new JsonObject();
        minLevelField.addProperty("item", "minecraft:iron_sword");
        minLevelField.addProperty("name", "Min Pokemon Level");
        minLevelField.addProperty("color", "aqua");
        minLevelField.addProperty("action", "setMinLevel");
        creationItems.add("min_level_field", minLevelField);

        // Add max level field
        JsonObject maxLevelField = new JsonObject();
        maxLevelField.addProperty("item", "minecraft:diamond_sword");
        maxLevelField.addProperty("name", "Max Pokemon Level");
        maxLevelField.addProperty("color", "blue");
        maxLevelField.addProperty("action", "setMaxLevel");
        creationItems.add("max_level_field", maxLevelField);

        // Add format field
        JsonObject formatField = new JsonObject();
        formatField.addProperty("item", "minecraft:bookshelf");
        formatField.addProperty("name", "Tournament Format");
        formatField.addProperty("color", "light_purple");
        formatField.addProperty("action", "setFormat");
        creationItems.add("format_field", formatField);

        // Add entry fee field
        JsonObject entryFeeField = new JsonObject();
        entryFeeField.addProperty("item", "minecraft:gold_ingot");
        entryFeeField.addProperty("name", "Entry Fee");
        entryFeeField.addProperty("color", "gold");
        entryFeeField.addProperty("action", "setEntryFee");
        creationItems.add("entry_fee_field", entryFeeField);

        // Add max participants field
        JsonObject maxPlayersField = new JsonObject();
        maxPlayersField.addProperty("item", "minecraft:player_head");
        maxPlayersField.addProperty("name", "Tournament Size");
        maxPlayersField.addProperty("color", "green");
        maxPlayersField.addProperty("action", "setMaxParticipants");
        creationItems.add("max_participants_field", maxPlayersField);

        // Add create button
        JsonObject createItem = new JsonObject();
        createItem.addProperty("item", "minecraft:emerald");
        createItem.addProperty("name", "Create Tournament");
        createItem.addProperty("color", "green");
        createItem.addProperty("action", "createTournament");
        creationItems.add("create_button", createItem);

        creationScreen.add("items", creationItems);

        // Add creation screen border
        JsonObject creationBorders = new JsonObject();
        JsonObject creationOuterBorder = new JsonObject();
        creationOuterBorder.addProperty("item", "minecraft:green_stained_glass_pane");
        creationOuterBorder.addProperty("name", " ");

        JsonArray creationBorderSlots = new JsonArray();
        for (int i : new int[]{0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 50, 51, 52, 53}) {
            creationBorderSlots.add(i);
        }
        creationOuterBorder.add("slots", creationBorderSlots);
        creationBorders.add("outer_border", creationOuterBorder);

        creationScreen.add("borders", creationBorders);

        defaultConfig.add("creation_screen", creationScreen);

        return defaultConfig;
    }

    /**
     * Get the main screen configuration
     */
    public static JsonObject getMainScreenConfig() {
        if (config == null) {
            config = createDefaultConfig();
            try {
                saveConfig();
            } catch (IOException e) {
                Tournaments.LOGGER.error("Failed to save default main screen config", e);
            }
        }
        return config.has("main_screen") ? config.getAsJsonObject("main_screen") : new JsonObject();
    }

    /**
     * Get the matches screen configuration
     */
    public static JsonObject getMatchesScreenConfig() {
        if (config == null) {
            config = createDefaultConfig();
            try {
                saveConfig();
            } catch (IOException e) {
                Tournaments.LOGGER.error("Failed to save default matches screen config", e);
            }
        }
        return config.has("matches_screen") ? config.getAsJsonObject("matches_screen") : new JsonObject();
    }

    /**
     * Get the creation screen configuration
     */
    public static JsonObject getCreationScreenConfig() {
        if (config == null) {
            config = createDefaultConfig();
            try {
                saveConfig();
            } catch (IOException e) {
                Tournaments.LOGGER.error("Failed to save default creation screen config", e);
            }
        }
        return config.has("creation_screen") ? config.getAsJsonObject("creation_screen") : new JsonObject();
    }

    /**
     * Get an item from the config string
     */
    public static Item getItem(String itemId) {
        return ITEM_MAP.getOrDefault(itemId, Items.BARRIER);
    }

    /**
     * Get a color from the config string
     */
    public static TextFormatting getColor(String colorName) {
        return COLOR_MAP.getOrDefault(colorName, TextFormatting.WHITE);
    }

    /**
     * Get a slot number from the config
     */
    public static int getSlot(JsonObject screenConfig, String slotName) {
        if (screenConfig == null || !screenConfig.has("slots")) {
            // Default values for common slots
            if ("title_slot".equals(slotName)) return 4;
            if ("tournaments_start_slot".equals(slotName)) return 18;
            if ("back_button_slot".equals(slotName)) return 0;
            return 0;
        }

        JsonObject slots = screenConfig.getAsJsonObject("slots");
        return slots.has(slotName) ? slots.get(slotName).getAsInt() : 0;
    }

    /**
     * Get an item description from the config
     */
    public static ItemConfig getItemConfig(JsonObject screenConfig, String itemName) {
        if (screenConfig == null || !screenConfig.has("items")) {
            // Return default for common items
            if ("join_button".equals(itemName)) {
                return new ItemConfig(Items.GOLDEN_APPLE, "Join Tournament", TextFormatting.YELLOW, "join");
            }
            if ("leave_button".equals(itemName)) {
                return new ItemConfig(Items.REDSTONE, "Leave Tournament", TextFormatting.RED, "leave");
            }
            if ("create_button".equals(itemName)) {
                return new ItemConfig(Items.EMERALD, "Create Tournament", TextFormatting.GREEN, "create");
            }
            if ("start_button".equals(itemName)) {
                return new ItemConfig(Items.BEACON, "Start Tournament", TextFormatting.AQUA, "start");
            }
            if ("back_button".equals(itemName)) {
                return new ItemConfig(Items.ARROW, "Back", TextFormatting.GRAY, "back");
            }

            return new ItemConfig(Items.BARRIER, itemName, TextFormatting.WHITE, "none");
        }

        JsonObject items = screenConfig.getAsJsonObject("items");
        if (items.has(itemName)) {
            JsonObject itemConfig = items.getAsJsonObject(itemName);
            Item item = getItem(itemConfig.get("item").getAsString());
            String name = itemConfig.has("name") ? itemConfig.get("name").getAsString() : itemName;
            TextFormatting color = itemConfig.has("color") ?
                    getColor(itemConfig.get("color").getAsString()) : TextFormatting.WHITE;
            String action = itemConfig.has("action") ?
                    itemConfig.get("action").getAsString() : "none";

            return new ItemConfig(item, name, color, action);
        }
        return new ItemConfig(Items.BARRIER, itemName, TextFormatting.WHITE, "none");
    }

    /**
     * Helper class for item configuration
     */
    public static class ItemConfig {
        private final Item item;
        private final String name;
        private final TextFormatting color;
        private final String action;

        public ItemConfig(Item item, String name, TextFormatting color, String action) {
            this.item = item;
            this.name = name;
            this.color = color;
            this.action = action;
        }

        public Item getItem() { return item; }
        public String getName() { return name; }
        public TextFormatting getColor() { return color; }
        public String getAction() { return action; }
    }

    /**
     * Get border configurations for a screen
     * @param screenConfig The screen JSON configuration
     * @return Map of border names to border configurations
     */
    public static Map<String, BorderConfig> getBorderConfigs(JsonObject screenConfig) {
        Map<String, BorderConfig> borders = new HashMap<>();

        if (screenConfig == null || !screenConfig.has("borders")) {
            return borders;
        }

        JsonObject bordersObject = screenConfig.getAsJsonObject("borders");
        for (Map.Entry<String, JsonElement> entry : bordersObject.entrySet()) {
            String borderName = entry.getKey();
            JsonObject borderObject = entry.getValue().getAsJsonObject();

            // Get item ID
            String itemId = borderObject.has("item") ?
                    borderObject.get("item").getAsString() :
                    "minecraft:black_stained_glass_pane";

            // Get custom name (empty for no name)
            String name = borderObject.has("name") ?
                    borderObject.get("name").getAsString() :
                    " ";

            // Get color if specified
            TextFormatting color = TextFormatting.WHITE;
            if (borderObject.has("color")) {
                color = getColor(borderObject.get("color").getAsString());
            }

            // Get slots
            List<Integer> slots = new ArrayList<>();
            if (borderObject.has("slots") && borderObject.get("slots").isJsonArray()) {
                JsonArray slotsArray = borderObject.getAsJsonArray("slots");
                for (JsonElement element : slotsArray) {
                    slots.add(element.getAsInt());
                }
            }

            BorderConfig config = new BorderConfig(
                    UIConfigLoader.getItem(itemId),
                    name,
                    color,
                    slots
            );

            borders.put(borderName, config);
        }

        return borders;
    }

    /**
     * Helper class for border configuration
     */
    public static class BorderConfig {
        private final Item item;
        private final String name;
        private final TextFormatting color;
        private final List<Integer> slots;

        public BorderConfig(Item item, String name, TextFormatting color, List<Integer> slots) {
            this.item = item;
            this.name = name;
            this.color = color;
            this.slots = slots;
        }

        public Item getItem() { return item; }
        public String getName() { return name; }
        public TextFormatting getColor() { return color; }
        public List<Integer> getSlots() { return slots; }
    }

    /**
     * Apply borders to an inventory based on screen configuration
     * @param inventory The inventory to apply borders to
     * @param screenConfig The screen JSON configuration
     */
    public static void applyBorders(Inventory inventory, JsonObject screenConfig) {
        if (screenConfig == null) {
            return;
        }

        Map<String, BorderConfig> borders = getBorderConfigs(screenConfig);

        for (BorderConfig border : borders.values()) {
            for (int slot : border.getSlots()) {
                if (slot >= 0 && slot < inventory.getContainerSize()) {
                    ItemStack borderItem = new ItemStack(border.getItem());

                    // Set custom name if not empty
                    if (!border.getName().isEmpty() && !border.getName().equals(" ")) {
                        borderItem.setHoverName(new StringTextComponent(border.getName())
                                .withStyle(border.getColor()));
                    } else if (border.getName().equals(" ")) {
                        // Special case: single space means blank name
                        borderItem.setHoverName(new StringTextComponent(" "));
                    }

                    // Ensure it doesn't interfere with other items
                    CompoundNBT tag = borderItem.getOrCreateTag();
                    tag.putBoolean("BorderItem", true);

                    // Only set if slot is empty
                    if (inventory.getItem(slot).isEmpty()) {
                        inventory.setItem(slot, borderItem);
                    }
                }
            }
        }
    }
}