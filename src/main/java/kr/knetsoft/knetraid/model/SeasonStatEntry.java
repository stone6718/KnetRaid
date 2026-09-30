package kr.knetsoft.knetraid.model;

import java.util.UUID;

public record SeasonStatEntry(UUID playerUuid, int kills, int deaths, int raids, double bountyEarned) {
}
