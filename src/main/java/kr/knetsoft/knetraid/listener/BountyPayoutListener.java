package kr.knetsoft.knetraid.listener;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.economy.EconomyFormat;
import kr.knetsoft.knetraid.economy.EconomyResult;
import kr.knetsoft.knetraid.model.BountyData;
import kr.knetsoft.knetraid.model.CurrencyReason;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.net.InetAddress;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 플레이어 처치 시 대상에게 걸린 활성 현상금을 합산해 킬러에게 지급한다.
 * 자기 자신 처치, 세력원 처치, 동일 IP 처치, 반복 처치 쿨타임은 각각 설정으로 제한할 수 있다.
 */
public class BountyPayoutListener implements Listener {

    private final KnetRaid plugin;

    public BountyPayoutListener(KnetRaid plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfigManager().isModuleEnabled("bounty")) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        FileConfiguration bountyConfig = plugin.getConfigManager().getModuleConfig("bounty.yml");
        if (bountyConfig == null || !bountyConfig.getBoolean("bounty.enabled", true)) {
            return;
        }

        UUID killerUuid = killer.getUniqueId();
        UUID victimUuid = victim.getUniqueId();

        if (!bountyConfig.getBoolean("bounty.faction-member-reward-allowed", false)) {
            Optional<String> killerFaction = plugin.getFactionManager().getCachedFactionId(killerUuid);
            Optional<String> victimFaction = plugin.getFactionManager().getCachedFactionId(victimUuid);
            if (killerFaction.isPresent() && killerFaction.equals(victimFaction)) {
                return;
            }
        }

        if (!bountyConfig.getBoolean("bounty.same-ip-reward-allowed", false)) {
            InetAddress killerIp = killer.getAddress() != null ? killer.getAddress().getAddress() : null;
            InetAddress victimIp = victim.getAddress() != null ? victim.getAddress().getAddress() : null;
            if (killerIp != null && killerIp.equals(victimIp)) {
                return;
            }
        }

        long cooldownMillis = bountyConfig.getLong("bounty.repeat-kill-cooldown-minutes", 30) * 60_000L;
        if (!plugin.getBountyManager().tryConsumeCooldown(killerUuid, victimUuid, cooldownMillis)) {
            return;
        }

        int decimalPlaces = getDecimalPlaces();

        plugin.getAsyncTaskTracker().begin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<BountyData> claimed = plugin.getBountyRepository().claimActiveForTarget(victimUuid, killerUuid, System.currentTimeMillis());
                if (claimed.isEmpty()) {
                    return;
                }
                double total = claimed.stream().mapToDouble(BountyData::amount).sum();
                if (total <= 0) {
                    return;
                }

                EconomyResult result = plugin.getEconomyProvider().deposit(killerUuid, total);
                if (!result.success()) {
                    plugin.getLogger().severe("현상금 지급 실패 (대상: " + killer.getName() + ", 금액: " + total + "): "
                            + result.errorMessage());
                    return;
                }
                plugin.getCurrencyTransactionRepository().log(killerUuid, total, CurrencyReason.BOUNTY_REWARD);

                if (plugin.getConfigManager().isModuleEnabled("season")) {
                    Optional<String> activeSeasonId = plugin.getSeasonManager().getActiveSeasonId();
                    if (activeSeasonId.isPresent()) {
                        plugin.getSeasonRepository().addBountyEarned(activeSeasonId.get(), killerUuid, total);
                    }
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    String amountText = EconomyFormat.format(total, decimalPlaces);
                    killer.sendMessage(plugin.getMessageManager().get("bounty.reward-received",
                            "amount", amountText, "player", victim.getName()));
                    String broadcastText = plugin.getMessageManager().get("bounty.reward-broadcast",
                            "killer", killer.getName(), "target", victim.getName(), "amount", amountText);
                    Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(broadcastText));
                });
            } catch (SQLException exception) {
                plugin.getLogger().severe("현상금 지급 처리 중 오류가 발생했습니다: " + exception.getMessage());
            } finally {
                plugin.getAsyncTaskTracker().end();
            }
        });
    }

    private int getDecimalPlaces() {
        FileConfiguration economyConfig = plugin.getConfigManager().getModuleConfig("economy.yml");
        return economyConfig != null ? economyConfig.getInt("economy.decimal-places", 2) : 2;
    }
}
