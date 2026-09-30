package kr.knetsoft.knetraid.model;

import java.util.UUID;

public final class FactionMember {

    private final UUID playerUuid;
    private final String factionId;
    private FactionRole role;
    private final long joinedAt;

    public FactionMember(UUID playerUuid, String factionId, FactionRole role, long joinedAt) {
        this.playerUuid = playerUuid;
        this.factionId = factionId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getFactionId() {
        return factionId;
    }

    public FactionRole getRole() {
        return role;
    }

    public void setRole(FactionRole role) {
        this.role = role;
    }

    public long getJoinedAt() {
        return joinedAt;
    }
}
