package kr.knetsoft.knetraid.model;

import java.util.UUID;

public record BountyRankEntry(UUID targetUuid, double total, int contributorCount) {
}
