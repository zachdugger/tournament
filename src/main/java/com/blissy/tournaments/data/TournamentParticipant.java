package com.blissy.tournaments.data;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.management.PlayerList;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.UUID;

public class TournamentParticipant {
    private final UUID playerId;
    private final String playerName;
    private int wins;
    private int losses;

    public TournamentParticipant(ServerPlayerEntity player) {
        this.playerId = player.getUUID();
        this.playerName = player.getName().getString();
        this.wins = 0;
        this.losses = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getWins() {
        return wins;
    }

    public void incrementWins() {
        wins++;
    }

    public int getLosses() {
        return losses;
    }

    public void incrementLosses() {
        losses++;
    }

    public ServerPlayerEntity getPlayer() {
        PlayerList playerList = ServerLifecycleHooks.getCurrentServer().getPlayerList();
        return playerList.getPlayer(playerId);  // Changed from getPlayerByUUID to getPlayer
    }
}