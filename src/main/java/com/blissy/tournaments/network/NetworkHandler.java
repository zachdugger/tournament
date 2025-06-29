package com.blissy.tournaments.network;

import com.blissy.tournaments.Tournaments;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-side only network handler
 * In a server-side only mod, we don't need to send packets to clients
 */
public class NetworkHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void register() {
        LOGGER.info("Initializing server-side only tournament system");
        // No network packets needed for server-side only mod
    }

    /**
     * For server-side communication between different parts of the mod
     */
    public static void notifyTournamentUpdate(String tournamentName) {
        // Use server-side events instead of packets
        LOGGER.debug("Tournament updated: {}", tournamentName);
    }
}