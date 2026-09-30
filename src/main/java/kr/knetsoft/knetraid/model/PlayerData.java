package kr.knetsoft.knetraid.model;

import java.util.UUID;

/**
 * knetraid_players 테이블 한 행에 대응하는 플레이어 기본 데이터.
 * 닉네임이 바뀌어도 uuid를 기준 식별자로 사용하므로 데이터가 유실되지 않는다.
 */
public final class PlayerData {

    private final UUID uuid;
    private String nickname;
    private final long firstJoinAt;
    private long lastJoinAt;
    private Long lastQuitAt;

    public PlayerData(UUID uuid, String nickname, long firstJoinAt, long lastJoinAt, Long lastQuitAt) {
        this.uuid = uuid;
        this.nickname = nickname;
        this.firstJoinAt = firstJoinAt;
        this.lastJoinAt = lastJoinAt;
        this.lastQuitAt = lastQuitAt;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public long getFirstJoinAt() {
        return firstJoinAt;
    }

    public long getLastJoinAt() {
        return lastJoinAt;
    }

    public void setLastJoinAt(long lastJoinAt) {
        this.lastJoinAt = lastJoinAt;
    }

    public Long getLastQuitAt() {
        return lastQuitAt;
    }

    public void setLastQuitAt(Long lastQuitAt) {
        this.lastQuitAt = lastQuitAt;
    }
}
