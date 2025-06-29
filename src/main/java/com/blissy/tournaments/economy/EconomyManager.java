package com.blissy.tournaments.economy;

import com.blissy.tournaments.Tournaments;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.lang.reflect.Method;

/**
 * Manager for economy integration with Essentials or other economy plugins
 * Uses reflection to avoid hard dependencies
 */
public class EconomyManager {
    private static boolean economyAvailable = false;
    private static Object economyPlugin = null;
    private static Method getUserMethod = null;
    private static Method getMoneyMethod = null;
    private static Method takeMoneyMethod = null;
    private static Method giveMoneyMethod = null;

    /**
     * Initialize the economy manager and attempt to hook into available economy plugins
     */
    public static void initialize() {
        // Try to hook into EssentialsX first
        if (tryEssentialsHook()) {
            Tournaments.LOGGER.info("Successfully hooked into Essentials economy");
            return;
        }

        // Try other economy plugins if needed
        // if (tryVaultHook()) return;

        Tournaments.LOGGER.warn("No supported economy plugin found. Economy features will be disabled.");
    }

    /**
     * Attempt to hook into EssentialsX economy
     * @return true if successful
     */
    private static boolean tryEssentialsHook() {
        try {
            // Look for Essentials main class
            Class<?> essentialsClass = Class.forName("com.earth2me.essentials.Essentials");
            Class<?> essentialsUserClass = Class.forName("com.earth2me.essentials.User");

            // Try to get plugin instance (works in Bukkit environment)
            Method getPluginMethod = null;
            Object pluginManager = null;

            try {
                Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
                getPluginMethod = bukkitClass.getMethod("getPluginManager");
                pluginManager = getPluginMethod.invoke(null);
                Method getPluginMethod2 = pluginManager.getClass().getMethod("getPlugin", String.class);
                economyPlugin = getPluginMethod2.invoke(pluginManager, "Essentials");
            } catch (Exception e) {
                Tournaments.LOGGER.debug("Bukkit method for getting Essentials failed, trying alternative methods");

                // Alternative methods could be attempted here
                return false;
            }

            if (economyPlugin != null) {
                // Get methods needed for basic economy operations
                getUserMethod = essentialsClass.getMethod("getUser", java.util.UUID.class);
                getMoneyMethod = essentialsUserClass.getMethod("getMoney");
                takeMoneyMethod = essentialsUserClass.getMethod("takeMoney", double.class);
                giveMoneyMethod = essentialsUserClass.getMethod("giveMoney", double.class);

                economyAvailable = true;
                return true;
            }
        } catch (ClassNotFoundException e) {
            Tournaments.LOGGER.debug("Essentials classes not found");
        } catch (Exception e) {
            Tournaments.LOGGER.warn("Error hooking into Essentials economy", e);
        }

        return false;
    }

    /**
     * Check if a player has enough money
     * @param player The player to check
     * @param amount The amount of money
     * @return True if the player has enough money
     */
    public static boolean hasBalance(ServerPlayerEntity player, double amount) {
        if (!economyAvailable || player == null) {
            return true; // If economy isn't available, assume players can afford it
        }

        try {
            // Get player balance using reflection
            double balance = getPlayerBalance(player);
            return balance >= amount;
        } catch (Exception e) {
            Tournaments.LOGGER.error("Error checking player balance", e);
            return false;
        }
    }

    /**
     * Withdraw money from a player
     * @param player The player to withdraw from
     * @param amount The amount to withdraw
     * @return True if withdrawal was successful
     */
    public static boolean withdrawBalance(ServerPlayerEntity player, double amount) {
        if (!economyAvailable || player == null) {
            return true; // If economy isn't available, assume withdrawal succeeds
        }

        try {
            double balance = getPlayerBalance(player);

            if (balance >= amount) {
                // Withdraw money using reflection
                takePlayerMoney(player, amount);
                return true;
            }

            return false;
        } catch (Exception e) {
            Tournaments.LOGGER.error("Error withdrawing from player balance", e);
            return false;
        }
    }

    /**
     * Deposit money to a player
     * @param player The player to deposit to
     * @param amount The amount to deposit
     * @return True if deposit was successful
     */
    public static boolean depositBalance(ServerPlayerEntity player, double amount) {
        if (!economyAvailable || player == null) {
            return true; // If economy isn't available, assume deposit succeeds
        }

        try {
            // Deposit money using reflection
            givePlayerMoney(player, amount);
            return true;
        } catch (Exception e) {
            Tournaments.LOGGER.error("Error depositing to player balance", e);
            return false;
        }
    }

    /**
     * Get a player's balance using reflection
     */
    private static double getPlayerBalance(ServerPlayerEntity player) throws Exception {
        // Implementation depends on which economy plugin we're using
        if (economyPlugin != null && getUserMethod != null && getMoneyMethod != null) {
            // Get Essentials User object
            Object user = getUserMethod.invoke(economyPlugin, player.getUUID());

            // Get balance
            return (double) getMoneyMethod.invoke(user);
        }

        return 0.0;
    }

    /**
     * Take money from a player using reflection
     */
    private static void takePlayerMoney(ServerPlayerEntity player, double amount) throws Exception {
        // Implementation depends on which economy plugin we're using
        if (economyPlugin != null && getUserMethod != null && takeMoneyMethod != null) {
            // Get Essentials User object
            Object user = getUserMethod.invoke(economyPlugin, player.getUUID());

            // Take money
            takeMoneyMethod.invoke(user, amount);
        }
    }

    /**
     * Give money to a player using reflection
     */
    private static void givePlayerMoney(ServerPlayerEntity player, double amount) throws Exception {
        // Implementation depends on which economy plugin we're using
        if (economyPlugin != null && getUserMethod != null && giveMoneyMethod != null) {
            // Get Essentials User object
            Object user = getUserMethod.invoke(economyPlugin, player.getUUID());

            // Give money
            giveMoneyMethod.invoke(user, amount);
        }
    }

    /**
     * Check if economy is available
     * @return True if economy integration is available
     */
    public static boolean isEconomyAvailable() {
        return economyAvailable;
    }
}