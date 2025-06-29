package com.blissy.tournaments.network;

import com.blissy.tournaments.data.Tournament;
import net.minecraft.network.PacketBuffer;

import java.util.*;

/**
 * Server-side packet representing tournament list data.
 * Since this is a server-side only mod, this packet exists only for
 * server-to-server communication and will never be sent to clients.
 */
public class TournamentListPacket {
    private final List<TournamentData> tournaments;

    /**
     * Create a packet from a collection of tournaments
     */
    public TournamentListPacket(Collection<Tournament> tournaments) {
        this.tournaments = new ArrayList<>();
        for (Tournament tournament : tournaments) {
            this.tournaments.add(new TournamentData(
                    tournament.getName(),
                    tournament.getParticipantCount(),
                    tournament.getMaxParticipants(),
                    tournament.getStatus().toString(),
                    tournament.getHostId().toString()
            ));
        }
    }

    /**
     * Create a packet from a list of tournament data
     */
    public TournamentListPacket(List<TournamentData> tournaments) {
        this.tournaments = tournaments;
    }

    /**
     * Encodes the packet data into the provided packet buffer
     * This is only used for server-side storage/communication and
     * will never be sent to clients.
     */
    public static void encode(TournamentListPacket packet, PacketBuffer buffer) {
        buffer.writeInt(packet.tournaments.size());
        for (TournamentData data : packet.tournaments) {
            buffer.writeUtf(data.name);  // Changed from writeString to writeUtf
            buffer.writeInt(data.participantCount);
            buffer.writeInt(data.maxParticipants);
            buffer.writeUtf(data.status);  // Changed from writeString to writeUtf
            buffer.writeUtf(data.hostId);  // Changed from writeString to writeUtf
        }
    }

    /**
     * Contains the data for a single tournament
     */
    public static class TournamentData {
        public final String name;
        public final int participantCount;
        public final int maxParticipants;
        public final String status;
        public final String hostId;

        public TournamentData(String name, int participantCount, int maxParticipants, String status, String hostId) {
            this.name = name;
            this.participantCount = participantCount;
            this.maxParticipants = maxParticipants;
            this.status = status;
            this.hostId = hostId;
        }
    }

    /**
     * Get the list of tournaments in this packet
     */
    public List<TournamentData> getTournaments() {
        return tournaments;
    }
}