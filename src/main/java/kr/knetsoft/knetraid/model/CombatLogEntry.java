package kr.knetsoft.knetraid.model;

import java.util.UUID;

public record CombatLogEntry(String id, UUID playerUuid, UUID opponentUuid, CombatEventType eventType, long occurredAt) {
}
