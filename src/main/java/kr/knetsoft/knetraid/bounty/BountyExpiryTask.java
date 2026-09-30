package kr.knetsoft.knetraid.bounty;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.economy.EconomyFormat;
import kr.knetsoft.knetraid.economy.EconomyResult;
import kr.knetsoft.knetraid.model.BountyData;
import kr.knetsoft.knetraid.model.CurrencyReason;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 만료 시각이 지난 ACTIVE 현상금을 주기적으로 찾아 EXPIRED로 표시하고,
 * 서버가 발행한 현상금이 아니라면 이슈어에게 환불한다.
 */
public class BountyExpiryTask extends BukkitRunnable {

    private final KnetRaid plugin;

    public BountyExpiryTask(KnetRaid plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfigManager().isModuleEnabled("bounty")) {
            return;
        }
        plugin.getAsyncTaskTracker().begin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<BountyData> expired = plugin.getBountyRepository().listExpiredActive(System.currentTimeMillis());
                if (expired.isEmpty()) {
                    return;
                }

                List<String> ids = new ArrayList<>();
                for (BountyData bounty : expired) {
                    ids.add(bounty.id());
                }
                plugin.getBountyRepository().markExpired(ids);

                FileConfiguration economyConfig = plugin.getConfigManager().getModuleConfig("economy.yml");
                int decimalPlaces = economyConfig != null ? economyConfig.getInt("economy.decimal-places", 2) : 2;

                for (BountyData bounty : expired) {
                    if (SystemIssuer.isSystem(bounty.issuerUuid())) {
                        continue;
                    }
                    EconomyResult result = plugin.getEconomyProvider().deposit(bounty.issuerUuid(), bounty.amount());
                    if (!result.success()) {
                        plugin.getLogger().severe("만료된 현상금 환불에 실패했습니다 (issuer: " + bounty.issuerUuid()
                                + ", amount: " + bounty.amount() + "): " + result.errorMessage());
                        continue;
                    }
                    plugin.getCurrencyTransactionRepository().log(bounty.issuerUuid(), bounty.amount(), CurrencyReason.BOUNTY_REFUND);

                    Player issuerPlayer = Bukkit.getPlayer(bounty.issuerUuid());
                    if (issuerPlayer != null) {
                        String amountText = EconomyFormat.format(bounty.amount(), decimalPlaces);
                        Bukkit.getScheduler().runTask(plugin, () ->
                                issuerPlayer.sendMessage(plugin.getMessageManager().get("bounty.expired-refund", "amount", amountText)));
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().severe("현상금 만료 처리 중 오류가 발생했습니다: " + exception.getMessage());
            } finally {
                plugin.getAsyncTaskTracker().end();
            }
        });
    }
}
