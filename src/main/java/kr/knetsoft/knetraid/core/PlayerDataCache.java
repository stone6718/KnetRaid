package kr.knetsoft.knetraid.core;

import kr.knetsoft.knetraid.model.PlayerData;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 온라인 플레이어의 PlayerData를 메모리에 캐시한다.
 * DB 왕복 없이 명령어/이벤트에서 즉시 조회할 수 있도록 하기 위함이며,
 * 퇴장 시 제거되므로 오프라인 플레이어까지 무한정 쌓이지 않는다.
 */
public class PlayerDataCache {

    private final ConcurrentMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public void put(UUID uuid, PlayerData data) {
        cache.put(uuid, data);
    }

    public Optional<PlayerData> get(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public void remove(UUID uuid) {
        cache.remove(uuid);
    }

    public int size() {
        return cache.size();
    }
}
