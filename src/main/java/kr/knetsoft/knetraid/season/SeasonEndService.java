package kr.knetsoft.knetraid.season;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.economy.EconomyFormat;
import kr.knetsoft.knetraid.economy.EconomyResult;
import kr.knetsoft.knetraid.model.CurrencyReason;
import kr.knetsoft.knetraid.model.PlayerData;
import kr.knetsoft.knetraid.model.SeasonData;
import kr.knetsoft.knetraid.model.SeasonStatEntry;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 시즌 종료 시 공통으로 수행하는 작업: 종료 시각 기록, 상위권 보상 지급, 결과 안내.
 * DB/경제 작업을 포함하므로 반드시 비동기 스레드에서 호출해야 한다.
 */
public class SeasonEndService {

    private final KnetRaid plugin;

    public SeasonEndService(KnetRaid plugin) {
        this.plugin = plugin;
    }

    public void endSeason(SeasonData season) throws SQLException {
        long now = System.currentTimeMillis();
        plugin.getSeasonRepository().endSeason(season.id(), now);

        List<SeasonStatEntry> topKills = plugin.getSeasonRepository().topByKills(season.id(), 3);
        FileConfiguration seasonConfig = plugin.getConfigManager().getModuleConfig("season.yml");
        List<Integer> bonuses = seasonConfig != null
                ? seasonConfig.getIntegerList("season.rewards.top-kills-bonus")
                : List.of();
        int decimalPlaces = getDecimalPlaces();

        List<String> resultLines = new ArrayList<>();
        for (int i = 0; i < topKills.size(); i++) {
            SeasonStatEntry entry = topKills.get(i);
            String nickname = plugin.getPlayerRepository().find(entry.playerUuid())
                    .map(PlayerData::getNickname)
                    .orElse(entry.playerUuid().toString());
            double bonus = i < bonuses.size() ? bonuses.get(i) : 0;

            if (bonus > 0 && plugin.getEconomyProvider().isAvailable()) {
                EconomyResult result = plugin.getEconomyProvider().deposit(entry.playerUuid(), bonus);
                if (result.success()) {
                    plugin.getCurrencyTransactionRepository().log(entry.playerUuid(), bonus, CurrencyReason.ADMIN_ADJUST);
                } else {
                    plugin.getLogger().severe("시즌 종료 보상 지급 실패 (player: " + entry.playerUuid() + "): " + result.errorMessage());
                }
            }

            resultLines.add(plugin.getMessageManager().get("season.end-rank-line",
                    "rank", i + 1, "player", nickname, "kills", entry.kills(),
                    "bonus", bonus > 0 ? EconomyFormat.format(bonus, decimalPlaces) : plugin.getMessageManager().get("faction.none")));
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getSeasonManager().clearActiveSeason();
            String header = plugin.getMessageManager().get("season.end-broadcast-header", "name", season.name());
            Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(header));
            for (String line : resultLines) {
                Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(line));
            }
        });
    }

    private int getDecimalPlaces() {
        FileConfiguration economyConfig = plugin.getConfigManager().getModuleConfig("economy.yml");
        return economyConfig != null ? economyConfig.getInt("economy.decimal-places", 2) : 2;
    }
}
