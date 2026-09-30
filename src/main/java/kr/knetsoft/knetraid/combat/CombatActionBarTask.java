package kr.knetsoft.knetraid.combat;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.model.CombatEventType;
import kr.knetsoft.knetraid.model.CombatLogEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * 매초 전투 중인 플레이어에게 액션바로 남은 시간을 표시하고,
 * 자연 만료된 전투 상태를 정리하며 기록을 남긴다.
 */
public class CombatActionBarTask extends BukkitRunnable {

    private final KnetRaid plugin;

    public CombatActionBarTask(KnetRaid plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        FileConfiguration combatConfig = plugin.getConfigManager().getModuleConfig("combat.yml");
        boolean actionbarEnabled = combatConfig == null || combatConfig.getBoolean("combat.actionbar", true);

        if (actionbarEnabled && plugin.getConfigManager().isModuleEnabled("combat")) {
            for (UUID uuid : plugin.getCombatManager().activeCombatants()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    continue;
                }
                long remaining = plugin.getCombatManager().getRemainingSeconds(uuid);
                if (remaining <= 0) {
                    continue;
                }
                player.sendActionBar(Component.text("전투 중  ", NamedTextColor.RED)
                        .append(Component.text(remaining + "초", NamedTextColor.YELLOW)));
            }
        }

        for (UUID uuid : plugin.getCombatManager().pruneExpired()) {
            plugin.getAsyncTaskTracker().begin();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    plugin.getCombatLogRepository().insert(new CombatLogEntry(
                            UUID.randomUUID().toString(), uuid, null, CombatEventType.COMBAT_END, System.currentTimeMillis()));
                } catch (java.sql.SQLException exception) {
                    plugin.getLogger().severe("전투 기록을 저장하는 중 오류가 발생했습니다: " + exception.getMessage());
                } finally {
                    plugin.getAsyncTaskTracker().end();
                }
            });
        }
    }
}
