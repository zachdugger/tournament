package com.blissy.tournaments.events;

import com.blissy.tournaments.data.Tournament;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.Event;

public class TournamentEvent extends Event {
    private final Tournament tournament;

    public TournamentEvent(Tournament tournament) {
        this.tournament = tournament;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public static class Created extends TournamentEvent {
        public Created(Tournament tournament) {
            super(tournament);
        }
    }

    public static class Started extends TournamentEvent {
        public Started(Tournament tournament) {
            super(tournament);
        }
    }

    public static class Ended extends TournamentEvent {
        public Ended(Tournament tournament) {
            super(tournament);
        }
    }

    public static class PlayerJoined extends TournamentEvent {
        private final ServerPlayerEntity player;

        public PlayerJoined(Tournament tournament, ServerPlayerEntity player) {
            super(tournament);
            this.player = player;
        }

        public ServerPlayerEntity getPlayer() {
            return player;
        }
    }

    public static class PlayerLeft extends TournamentEvent {
        private final ServerPlayerEntity player;

        public PlayerLeft(Tournament tournament, ServerPlayerEntity player) {
            super(tournament);
            this.player = player;
        }

        public ServerPlayerEntity getPlayer() {
            return player;
        }
    }
}