package kr.knetsoft.knetraid.teleport;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 랜덤 텔레포트의 쿨타임과 대기(warmup) 상태를 메모리에서 관리한다.
 */
public class TeleportManager {

    private record PendingWarmup(Location startLocation, BukkitTask task) {
    }

    private final Map<UUID, Long> lastTeleportAt = new ConcurrentHashMap<>();
    private final Map<UUID, PendingWarmup> pendingWarmups = new ConcurrentHashMap<>();

    public boolean isOnCooldown(UUID uuid, long cooldownMillis) {
        Long last = lastTeleportAt.get(uuid);
        return last != null && System.currentTimeMillis() - last < cooldownMillis;
    }

    public long getRemainingCooldownSeconds(UUID uuid, long cooldownMillis) {
        Long last = lastTeleportAt.get(uuid);
        if (last == null) {
            return 0;
        }
        long remaining = cooldownMillis - (System.currentTimeMillis() - last);
        return Math.max(0, remaining / 1000);
    }

    public void recordTeleport(UUID uuid) {
        lastTeleportAt.put(uuid, System.currentTimeMillis());
    }

    public boolean hasPendingWarmup(UUID uuid) {
        return pendingWarmups.containsKey(uuid);
    }

    public Optional<Location> getWarmupStartLocation(UUID uuid) {
        PendingWarmup warmup = pendingWarmups.get(uuid);
        return warmup == null ? Optional.empty() : Optional.of(warmup.startLocation());
    }

    public void startWarmup(UUID uuid, Location startLocation, BukkitTask task) {
        pendingWarmups.put(uuid, new PendingWarmup(startLocation, task));
    }

    /**
     * 대기가 자연스럽게 끝나 순간이동을 실행하는 시점에 호출한다(취소가 아니라 완료 처리).
     */
    public void completeWarmup(UUID uuid) {
        pendingWarmups.remove(uuid);
    }

    /**
     * 이동/피격 등으로 대기를 취소할 때 호출한다. 예약된 작업도 함께 취소한다.
     */
    public void cancelWarmup(UUID uuid) {
        PendingWarmup warmup = pendingWarmups.remove(uuid);
        if (warmup != null) {
            warmup.task().cancel();
        }
    }
}
