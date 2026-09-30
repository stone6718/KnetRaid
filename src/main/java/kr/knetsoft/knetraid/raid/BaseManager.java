package kr.knetsoft.knetraid.raid;

import kr.knetsoft.knetraid.model.BaseData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 등록된 모든 기지를 메모리에 캐시한다. 블록/컨테이너 보호 및 침입 감지는
 * 매 이벤트마다 DB를 조회하지 않고 이 캐시만 사용한다.
 * 서버 시작 시 전체 로드 후, 기지 생성/삭제 시점에만 캐시를 갱신한다.
 */
public class BaseManager {

    private final List<BaseData> bases = new CopyOnWriteArrayList<>();
    private final Map<String, Long> lastAlertAt = new ConcurrentHashMap<>();

    public void setAll(List<BaseData> loaded) {
        bases.clear();
        bases.addAll(loaded);
    }

    public void add(BaseData base) {
        bases.add(base);
    }

    public void remove(String baseId) {
        bases.removeIf(base -> base.getId().equals(baseId));
        lastAlertAt.remove(baseId);
    }

    public List<BaseData> all() {
        return bases;
    }

    /**
     * 같은 월드에 등록된 기지만 반환한다. 매 이벤트마다 다른 월드의 기지까지
     * 전부 검사하지 않도록 1차로 걸러내기 위함이다.
     */
    public List<BaseData> inWorld(String world) {
        return bases.stream().filter(base -> base.getWorld().equals(world)).toList();
    }

    /**
     * 해당 기지에 대해 알림 쿨타임(초)이 지났으면 true를 반환하고 즉시 쿨타임을 갱신한다.
     */
    public boolean tryConsumeAlertCooldown(String baseId, long cooldownMillis) {
        long now = System.currentTimeMillis();
        Long last = lastAlertAt.get(baseId);
        if (last != null && now - last < cooldownMillis) {
            return false;
        }
        lastAlertAt.put(baseId, now);
        return true;
    }
}
