package com.blissy.tournaments;

import com.blissy.tournaments.config.EloConfig;
import com.blissy.tournaments.config.NotificationConfig;
import com.blissy.tournaments.config.TournamentsConfig;
import com.blissy.tournaments.config.UIConfigLoader;
import com.blissy.tournaments.data.RecurringTournament;
import com.blissy.tournaments.data.SkinCacheHandler;
import com.blissy.tournaments.elo.EloManager;
import com.blissy.tournaments.handlers.RecurringTournamentHandler;
import com.blissy.tournaments.handlers.TournamentPlayerHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;

@Mod("tournaments")
public class Tournaments {

    public static final Logger LOGGER = LogManager.getLogger();
    public static Tournaments instance;
    public static final EloManager ELO_MANAGER = new EloManager();

    public Tournaments() {
        instance = this;

        // Register to the mod event bus for initialization events
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::setup);

        // Register to the forge event bus for game events
        MinecraftForge.EVENT_BUS.register(this);

        // Register command classes explicitly
        MinecraftForge.EVENT_BUS.register(com.blissy.tournaments.commands.TournamentCommands.class);
        MinecraftForge.EVENT_BUS.register(com.blissy.tournaments.gui.TournamentMainGUI.class);
        MinecraftForge.EVENT_BUS.register(com.blissy.tournaments.gui.TournamentGuiHandler.class);
        MinecraftForge.EVENT_BUS.register(com.blissy.tournaments.handlers.ChatHandler.class);
        MinecraftForge.EVENT_BUS.register(com.blissy.tournaments.handlers.TournamentPlayerHandler.class);

        // CRITICAL FIX: Make sure battle handlers are registered
        MinecraftForge.EVENT_BUS.register(com.blissy.tournaments.compat.PixelmonHandler.class);
        MinecraftForge.EVENT_BUS.register(com.blissy.tournaments.battle.BattleTimeoutChecker.class);
        MinecraftForge.EVENT_BUS.register(com.blissy.tournaments.battle.ScheduledBattleManager.class);

        // Register recurring tournament handler
        MinecraftForge.EVENT_BUS.register(com.blissy.tournaments.handlers.RecurringTournamentHandler.class);

        // Register configs
        TournamentsConfig.register();
        EloConfig.register();
        // Register notification config
        NotificationConfig.register();
        LOGGER.info("Tournaments mod initializing");

        // Initialize UI config with defaults immediately
        UIConfigLoader.loadConfig(null);

        try {
            // Make sure config gets saved to disk
            UIConfigLoader.saveConfig();
        } catch (IOException e) {
            LOGGER.error("Failed to save UI config", e);
        }

        // Debug log registration status
        LOGGER.info("Tournament event handlers registered:");
        LOGGER.info("- PixelmonHandler: Registered for battle events");
        LOGGER.info("- BattleTimeoutChecker: Registered for timeout detection");
        LOGGER.info("- ScheduledBattleManager: Registered for battle scheduling");
        LOGGER.info("- RecurringTournamentHandler: Registered for recurring tournaments");
        LOGGER.info("- SkinCacheHandler: Ready for skin caching");
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Tournaments setup");

        // Initialize tournament manager
        TournamentManager.getInstance().initialize();

        // Load ELO data
        ELO_MANAGER.load();

        // Load recurring tournaments
        RecurringTournament.loadRecurringTournaments();

        // Log Pixelmon integration
        LOGGER.info("Successfully initialized Pixelmon integration");
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        MinecraftServer server = event.getServer();

        LOGGER.info("Tournament mod initialized on server start");

        // Load skin cache after server is ready
        SkinCacheHandler.loadCache();

        // Make sure match positions are set up
        LOGGER.info("Checking match position teleport locations...");
        try {
            com.blissy.tournaments.util.TeleportUtil.ensureMatchPositionsExist();
        } catch (Exception e) {
            LOGGER.error("Error setting up match positions", e);
        }

        // Reload configs to ensure they're in the server's config directory
        UIConfigLoader.loadConfig(null);
        try {
            UIConfigLoader.saveConfig();
            LOGGER.info("Tournament UI config saved to server's config directory");
        } catch (IOException e) {
            LOGGER.error("Failed to save UI config in server directory", e);
        }

        // Load ELO data again on server start
        ELO_MANAGER.load();

        // Load recurring tournaments
        RecurringTournament.loadRecurringTournaments();
        LOGGER.info("Loaded recurring tournaments");

        // Log data integrity stats on startup
        try {
            // Log skin cache stats
            Map<String, Object> cacheStats = SkinCacheHandler.getCacheStats();
            LOGGER.info("Skin cache stats: {} cached profiles, {} with skins, cache file exists: {}",
                    cacheStats.get("cachedProfiles"), cacheStats.get("profilesWithSkins"),
                    cacheStats.get("cacheFileExists"));

        } catch (Exception e) {
            LOGGER.warn("Could not perform skin cache integrity check", e);
        }
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        LOGGER.info("Server stopping. Ensuring all tournament data and skin cache is saved...");

        try {
            // Force save any pending data
            ELO_MANAGER.save();
            SkinCacheHandler.forceSave();

            // Save recurring tournaments
            RecurringTournament.saveRecurringTournaments();

            // Log final stats
            Map<String, Object> cacheStats = SkinCacheHandler.getCacheStats();
            LOGGER.info("Final data save completed. Cached profiles: {}",
                    cacheStats.get("cachedProfiles"));
            LOGGER.info("Saved recurring tournaments");

        } catch (Exception e) {
            LOGGER.error("Error during shutdown data save - some data may be lost!", e);
        }

        LOGGER.info("Tournament system shutdown complete.");
    }

    @SubscribeEvent
    public void onResourceReload(AddReloadListenerEvent event) {
        // Reload UI config on resource reload
        LOGGER.info("Resource reload detected, reloading tournament UI configuration");
        UIConfigLoader.loadConfig(null);
        try {
            UIConfigLoader.saveConfig();
        } catch (IOException e) {
            LOGGER.error("Failed to save UI config during resource reload", e);
        }
    }
}