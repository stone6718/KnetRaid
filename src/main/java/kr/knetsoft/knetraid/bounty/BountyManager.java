package kr.knetsoft.knetraid.bounty;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 동일한 킬러-피해자 조합의 반복 처치로 현상금을 농사짓는 것을 막기 위한
 * 쿨타임을 메모리에서 관리한다(부계정 악용 방지).
 */
public class BountyManager {

    private final Map<String, Long> lastPayoutAt = new ConcurrentHashMap<>();

    /**
     * 쿨타임이 지났으면(또는 처음이면) 지급을 허용하고 즉시 쿨타임을 갱신한다.
     *
     * @return 지급 가능 여부
     */
    public boolean tryConsumeCooldown(UUID killer, UUID victim, long cooldownMillis) {
        if (cooldownMillis <= 0) {
            return true;
        }
        String key = killer + ":" + victim;
        long now = System.currentTimeMillis();
        Long last = lastPayoutAt.get(key);
        if (last != null && now - last < cooldownMillis) {
            return false;
        }
        lastPayoutAt.put(key, now);
        return true;
    }
}
