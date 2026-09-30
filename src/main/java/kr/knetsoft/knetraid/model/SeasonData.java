package kr.knetsoft.knetraid.model;

public record SeasonData(String id, String name, long startedAt, Long endedAt) {

    public boolean isActive() {
        return endedAt == null;
    }
}
