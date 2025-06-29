package com.blissy.tournaments.network;

import com.blissy.tournaments.data.Tournament;
import net.minecraft.network.PacketBuffer;

public class TournamentDataPacket {
    private final String tournamentName;
    private final int participantCount;
    private final int maxParticipants;
    private final String status;

    public TournamentDataPacket(Tournament tournament) {
        this.tournamentName = tournament.getName();
        this.participantCount = tournament.getParticipantCount();
        this.maxParticipants = tournament.getMaxParticipants();
        this.status = tournament.getStatus().toString();
    }

    public static void encode(TournamentDataPacket packet, PacketBuffer buffer) {
        buffer.writeUtf(packet.tournamentName);  // Changed from writeInt to writeUtf
        buffer.writeInt(packet.participantCount);
        buffer.writeInt(packet.maxParticipants);
        buffer.writeUtf(packet.status);  // Changed from writeInt to writeUtf
    }

    // Client-side decode and handle methods not needed for server-only mod
}