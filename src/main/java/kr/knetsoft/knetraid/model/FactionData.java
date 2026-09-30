package kr.knetsoft.knetraid.model;

import java.util.UUID;

public final class FactionData {

    private final String id;
    private String name;
    private String tag;
    private UUID leaderUuid;
    private final long createdAt;

    public FactionData(String id, String name, String tag, UUID leaderUuid, long createdAt) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leaderUuid = leaderUuid;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
