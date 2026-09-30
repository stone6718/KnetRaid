package kr.knetsoft.knetraid.faction;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 세력 시스템에서 DB에 영속되지 않는 런타임 상태(초대, 세력 채팅 모드, 해체 확인)를 관리한다.
 * 서버 재시작 시 초기화되어도 문제없는 휘발성 상태만 다룬다.
 */
public class FactionManager {

    private static final long INVITE_EXPIRE_MS = 5 * 60 * 1000L;
    private static final long DISBAND_CONFIRM_WINDOW_MS = 30 * 1000L;

    private final Map<UUID, List<FactionInvite>> pendingInvites = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> factionChatEnabled = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingDisbandConfirmations = new ConcurrentHashMap<>();
    /** 온라인 플레이어의 현재 세력 id 캐시. 세력 채팅 라우팅에서 DB 조회 없이 즉시 참조하기 위함이다. */
    private final Map<UUID, String> onlineFactionCache = new ConcurrentHashMap<>();

    public void cacheFactionId(UUID playerUuid, String factionId) {
        if (factionId == null) {
            onlineFactionCache.remove(playerUuid);
        } else {
            onlineFactionCache.put(playerUuid, factionId);
        }
    }

    public Optional<String> getCachedFactionId(UUID playerUuid) {
        return Optional.ofNullable(onlineFactionCache.get(playerUuid));
    }

    public void addInvite(UUID inviteeUuid, FactionInvite invite) {
        pendingInvites.computeIfAbsent(inviteeUuid, key -> new CopyOnWriteArrayList<>());
        List<FactionInvite> invites = pendingInvites.get(inviteeUuid);
        invites.removeIf(existing -> existing.factionId().equals(invite.factionId()));
        invites.add(invite);
    }

    public List<String> listInviteFactionNames(UUID inviteeUuid) {
        List<FactionInvite> invites = pendingInvites.get(inviteeUuid);
        if (invites == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        return invites.stream()
                .filter(invite -> now - invite.invitedAt() <= INVITE_EXPIRE_MS)
                .map(FactionInvite::factionName)
                .toList();
    }

    public FactionInvite takeInvite(UUID inviteeUuid, String factionName) {
        List<FactionInvite> invites = pendingInvites.get(inviteeUuid);
        if (invites == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        invites.removeIf(invite -> now - invite.invitedAt() > INVITE_EXPIRE_MS);

        FactionInvite found = null;
        for (FactionInvite invite : invites) {
            if (invite.factionName().equalsIgnoreCase(factionName)) {
                found = invite;
                break;
            }
        }
        if (found != null) {
            invites.remove(found);
        }
        return found;
    }

    public boolean toggleFactionChat(UUID playerUuid) {
        boolean next = !factionChatEnabled.getOrDefault(playerUuid, false);
        factionChatEnabled.put(playerUuid, next);
        return next;
    }

    public boolean isFactionChatEnabled(UUID playerUuid) {
        return factionChatEnabled.getOrDefault(playerUuid, false);
    }

    public void clearFactionChat(UUID playerUuid) {
        factionChatEnabled.remove(playerUuid);
    }

    /**
     * 해체 요청을 기록한다. 확인 창(30초) 내에 다시 호출되면 true(확정)를 반환하고,
     * 그렇지 않으면 새 확인 창을 시작하며 false를 반환한다.
     */
    public boolean confirmDisband(UUID leaderUuid) {
        long now = System.currentTimeMillis();
        Long requestedAt = pendingDisbandConfirmations.get(leaderUuid);
        if (requestedAt != null && now - requestedAt <= DISBAND_CONFIRM_WINDOW_MS) {
            pendingDisbandConfirmations.remove(leaderUuid);
            return true;
        }
        pendingDisbandConfirmations.put(leaderUuid, now);
        return false;
    }

    public void clearDisbandConfirmation(UUID leaderUuid) {
        pendingDisbandConfirmations.remove(leaderUuid);
    }

    public void clearPlayer(UUID playerUuid) {
        pendingInvites.remove(playerUuid);
        factionChatEnabled.remove(playerUuid);
        pendingDisbandConfirmations.remove(playerUuid);
        onlineFactionCache.remove(playerUuid);
    }
}
