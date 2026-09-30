package kr.knetsoft.knetraid.model;

import java.util.UUID;

public record RaidLogEntry(String id, String baseId, UUID actorUuid, RaidActionType actionType,
                            String world, int x, int y, int z, long occurredAt) {
}
