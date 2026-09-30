package kr.knetsoft.knetraid.listener;

import kr.knetsoft.knetraid.KnetRaid;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.sql.SQLException;
import java.util.Optional;

/**
 * 진행 중인 시즌이 있을 때 처치/사망 수를 집계한다.
 * 현상금 지급 여부와 무관하게 항상 기록되며, 별도의 리스너로 분리해 관심사를 나눈다.
 */
public class SeasonStatListener implements Listener {

    private final KnetRaid plugin;

    public SeasonStatListener(KnetRaid plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfigManager().isModuleEnabled("season")) {
            return;
        }
        Optional<String> seasonId = plugin.getSeasonManager().getActiveSeasonId();
        if (seasonId.isEmpty()) {
            return;
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        plugin.getAsyncTaskTracker().begin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getSeasonRepository().addDeath(seasonId.get(), victim.getUniqueId());
                if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
                    plugin.getSeasonRepository().addKill(seasonId.get(), killer.getUniqueId());
                }
            } catch (SQLException exception) {
                plugin.getLogger().severe("시즌 처치/사망 기록 중 오류가 발생했습니다: " + exception.getMessage());
            } finally {
                plugin.getAsyncTaskTracker().end();
            }
        });
    }
}
