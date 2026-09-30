package kr.knetsoft.knetraid.season;

import java.util.Optional;

/**
 * 현재 진행 중인 시즌의 id를 메모리에 캐시한다.
 * 킬/약탈/현상금 이벤트가 매번 DB를 조회하지 않고 이 캐시로 활성 시즌 여부를 확인한다.
 */
public class SeasonManager {

    private volatile String activeSeasonId;
    private volatile String activeSeasonName;

    public void setActiveSeason(String seasonId, String seasonName) {
        this.activeSeasonId = seasonId;
        this.activeSeasonName = seasonName;
    }

    public void clearActiveSeason() {
        this.activeSeasonId = null;
        this.activeSeasonName = null;
    }

    public Optional<String> getActiveSeasonId() {
        return Optional.ofNullable(activeSeasonId);
    }

    public Optional<String> getActiveSeasonName() {
        return Optional.ofNullable(activeSeasonName);
    }
}
