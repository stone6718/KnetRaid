package kr.knetsoft.knetraid.raid;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.model.BaseData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Optional;

/**
 * 주기적으로 온라인 플레이어와 등록된 기지 사이의 거리를 검사해
 * 소유자가 아닌 플레이어가 alert-radius 안에 들어오면 기지 소유자/온라인 세력원에게 알린다.
 * BaseManager의 메모리 캐시만 사용하므로 DB 접근이 없다.
 */
public class IntrusionAlertTask extends BukkitRunnable {

    private final KnetRaid plugin;

    public IntrusionAlertTask(KnetRaid plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfigManager().isModuleEnabled("raid")) {
            return;
        }
        FileConfiguration raidConfig = plugin.getConfigManager().getModuleConfig("raid.yml");
        if (raidConfig == null) {
            return;
        }
        double alertRadius = raidConfig.getDouble("raid.alert-radius", 30);
        double radiusSquared = alertRadius * alertRadius;
        long cooldownMillis = raidConfig.getLong("raid.alert-cooldown-seconds", 60) * 1000L;

        for (Player player : Bukkit.getOnlinePlayers()) {
            List<BaseData> candidates = plugin.getBaseManager().inWorld(player.getWorld().getName());
            if (candidates.isEmpty()) {
                continue;
            }
            for (BaseData base : candidates) {
                if (player.getUniqueId().equals(base.getOwnerUuid())) {
                    continue;
                }
                double distanceSquared = base.distanceSquared(player.getWorld().getName(),
                        player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ());
                if (distanceSquared > radiusSquared) {
                    continue;
                }
                Optional<String> intruderFaction = plugin.getFactionManager().getCachedFactionId(player.getUniqueId());
                if (base.getFactionId() != null && intruderFaction.isPresent() && intruderFaction.get().equals(base.getFactionId())) {
                    continue; // 같은 세력원은 침입 알림 대상에서 제외
                }
                if (!plugin.getBaseManager().tryConsumeAlertCooldown(base.getId(), cooldownMillis)) {
                    continue;
                }
                notifyIntrusion(base, player);
            }
        }
    }

    private void notifyIntrusion(BaseData base, Player intruder) {
        String message = plugin.getMessageManager().get("raid.intrusion-alert",
                "base", base.getName(), "player", intruder.getName());

        Player owner = Bukkit.getPlayer(base.getOwnerUuid());
        if (owner != null) {
            owner.sendMessage(message);
        }
        if (base.getFactionId() != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(owner)) {
                    continue;
                }
                Optional<String> onlineFaction = plugin.getFactionManager().getCachedFactionId(online.getUniqueId());
                if (onlineFaction.isPresent() && onlineFaction.get().equals(base.getFactionId())) {
                    online.sendMessage(message);
                }
            }
        }
    }
}
