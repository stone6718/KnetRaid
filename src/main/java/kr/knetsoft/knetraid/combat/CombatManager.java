package kr.knetsoft.knetraid.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 전투 상태를 uuid 기준으로 메모리에서 관리한다.
 * 만료된 항목은 조회 시점에 지연 제거되며, pruneExpired()로 주기적으로도 정리해
 * 아무도 조회하지 않는 항목이 계속 쌓이지 않도록 한다(메모리 누수 방지).
 */
public class CombatManager {

    private record CombatState(UUID opponentUuid, long expiresAt) {
    }

    private final Map<UUID, CombatState> combatants = new ConcurrentHashMap<>();

    /**
     * 전투 상태로 진입시키거나 지속시간을 갱신한다.
     *
     * @return 이전에 전투 상태가 아니었다면(=새로 진입) true
     */
    public boolean tagCombat(UUID player, UUID opponent, long durationMillis) {
        boolean wasInCombat = isInCombat(player);
        combatants.put(player, new CombatState(opponent, System.currentTimeMillis() + durationMillis));
        return !wasInCombat;
    }

    public boolean isInCombat(UUID player) {
        CombatState state = combatants.get(player);
        if (state == null) {
            return false;
        }
        if (System.currentTimeMillis() >= state.expiresAt()) {
            combatants.remove(player);
            return false;
        }
        return true;
    }

    public long getRemainingSeconds(UUID player) {
        CombatState state = combatants.get(player);
        if (state == null) {
            return 0;
        }
        long remaining = state.expiresAt() - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    public Optional<UUID> getOpponent(UUID player) {
        CombatState state = combatants.get(player);
        return state == null ? Optional.empty() : Optional.of(state.opponentUuid());
    }

    public void clear(UUID player) {
        combatants.remove(player);
    }

    public Set<UUID> activeCombatants() {
        return combatants.keySet();
    }

    /**
     * 만료된 전투 상태를 정리한다.
     *
     * @return 이번 호출로 제거된(=자연 만료된) 플레이어 uuid 목록
     */
    public List<UUID> pruneExpired() {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();
        combatants.forEach((uuid, state) -> {
            if (now >= state.expiresAt()) {
                expired.add(uuid);
            }
        });
        expired.forEach(combatants::remove);
        return expired;
    }
}
