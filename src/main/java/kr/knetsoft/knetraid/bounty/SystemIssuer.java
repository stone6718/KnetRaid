package kr.knetsoft.knetraid.bounty;

import java.util.UUID;

/**
 * 관리자(/약탈관리 현상금 등록)가 건 현상금은 특정 플레이어가 아니라 서버가 발행한 것이므로,
 * NOT NULL인 issuer_uuid 컬럼에 이 고정 UUID(전부 0)를 사용해 "서버 발행"을 표시한다.
 * 이 UUID로는 실제 플레이어가 접속할 수 없으므로 안전한 구분자로 사용할 수 있다.
 */
public final class SystemIssuer {

    public static final UUID UUID_VALUE = new UUID(0L, 0L);

    private SystemIssuer() {
    }

    public static boolean isSystem(UUID uuid) {
        return UUID_VALUE.equals(uuid);
    }
}
