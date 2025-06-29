package com.blissy.tournaments.data;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.UUID;

public class EloPlayer {
    private final UUID playerId;
    private String playerName;
    private int elo;
    private int wins;
    private int losses;

    public EloPlayer(UUID playerId) {
        this.playerId = playerId;
        this.playerName = getPlayerName(playerId);
        this.elo = 1000;
        this.wins = 0;
        this.losses = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getElo() {
        return elo;
    }

    public void addElo(int points) {
        elo += points;
    }

    public void subtractElo(int points) {
        elo = Math.max(elo - points, 0);
    }

    public int getWins() {
        return wins;
    }

    public void addWin() {
        wins++;
    }

    public int getLosses() {
        return losses;
    }

    public void addLoss() {
        losses++;
    }

    public void reset() {
        elo = 1000;
    }

    public ServerPlayerEntity getPlayer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getPlayerList().getPlayer(playerId) : null;
    }

    private static String getPlayerName(UUID playerId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayerEntity player = server != null ? server.getPlayerList().getPlayer(playerId) : null;
        return player != null ? player.getName().getString() : "Unknown";
    }
}