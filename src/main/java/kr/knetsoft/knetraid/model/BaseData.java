package kr.knetsoft.knetraid.model;

import java.util.UUID;

public final class BaseData {

    private final String id;
    private final String name;
    private final UUID ownerUuid;
    private final String factionId;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final long registeredAt;
    private Long lastRaidAt;
    private final long ownerFirstJoinAt;
    private final String ownerNickname;

    public BaseData(String id, String name, UUID ownerUuid, String factionId, String world,
                     double x, double y, double z, long registeredAt, Long lastRaidAt,
                     long ownerFirstJoinAt, String ownerNickname) {
        this.id = id;
        this.name = name;
        this.ownerUuid = ownerUuid;
        this.factionId = factionId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.registeredAt = registeredAt;
        this.lastRaidAt = lastRaidAt;
        this.ownerFirstJoinAt = ownerFirstJoinAt;
        this.ownerNickname = ownerNickname;
    }

    public long getOwnerFirstJoinAt() {
        return ownerFirstJoinAt;
    }

    public String getOwnerNickname() {
        return ownerNickname;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getFactionId() {
        return factionId;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public Long getLastRaidAt() {
        return lastRaidAt;
    }

    public void setLastRaidAt(Long lastRaidAt) {
        this.lastRaidAt = lastRaidAt;
    }

    /**
     * 같은 월드 기준 이 기지 중심으로부터의 제곱거리. 매 이벤트마다 sqrt 연산을 피하기 위해 제곱값으로 비교한다.
     */
    public double distanceSquared(String otherWorld, double ox, double oy, double oz) {
        if (!world.equals(otherWorld)) {
            return Double.MAX_VALUE;
        }
        double dx = x - ox;
        double dy = y - oy;
        double dz = z - oz;
        return dx * dx + dy * dy + dz * dz;
    }
}
