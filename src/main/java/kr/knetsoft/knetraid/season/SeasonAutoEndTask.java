package kr.knetsoft.knetraid.season;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.model.SeasonData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.Optional;

/**
 * season.yml의 duration-days를 초과한 활성 시즌을 주기적으로 확인해 자동 종료한다.
 * duration-days가 0 이하이면 자동 종료를 사용하지 않는다(관리자가 직접 종료).
 */
public class SeasonAutoEndTask extends BukkitRunnable {

    private final KnetRaid plugin;

    public SeasonAutoEndTask(KnetRaid plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfigManager().isModuleEnabled("season")) {
            return;
        }
        FileConfiguration seasonConfig = plugin.getConfigManager().getModuleConfig("season.yml");
        if (seasonConfig == null || !seasonConfig.getBoolean("season.enabled", true)) {
            return;
        }
        int durationDays = seasonConfig.getInt("season.duration-days", 30);
        if (durationDays <= 0) {
            return;
        }

        plugin.getAsyncTaskTracker().begin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Optional<SeasonData> active = plugin.getSeasonRepository().findActiveSeason();
                if (active.isEmpty()) {
                    return;
                }
                long durationMillis = durationDays * 86_400_000L;
                if (System.currentTimeMillis() - active.get().startedAt() >= durationMillis) {
                    plugin.getSeasonEndService().endSeason(active.get());
                }
            } catch (SQLException exception) {
                plugin.getLogger().severe("시즌 자동 종료 확인 중 오류가 발생했습니다: " + exception.getMessage());
            } finally {
                plugin.getAsyncTaskTracker().end();
            }
        });
    }
}
