package kr.knetsoft.knetraid.model;

import java.util.UUID;

public record BountyData(String id, UUID targetUuid, UUID issuerUuid, double amount, BountyStatus status,
                          long createdAt, Long expiresAt, UUID claimedByUuid, Long claimedAt) {
}
