package kr.knetsoft.knetraid.listener;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.model.FactionData;
import kr.knetsoft.knetraid.model.PlayerData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * 플레이어 접속/퇴장 시 knetraid_players 레코드를 비동기로 갱신하고
 * 온라인 캐시(PlayerDataCache)를 유지한다. Bedrock(Floodgate) 플레이어라면
 * 접속 메시지에 표시 접두사를 붙인다.
 */
public class PlayerConnectionListener implements Listener {

    private final KnetRaid plugin;

    public PlayerConnectionListener(KnetRaid plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoinMessage(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().isModuleEnabled("bedrock") || !plugin.getBedrockManager().isAvailable()) {
            return;
        }
        if (!plugin.getBedrockManager().isBedrockPlayer(event.getPlayer().getUniqueId())) {
            return;
        }
        FileConfiguration config = plugin.getConfig();
        String prefix = config.getString("bedrock.display-prefix", "");
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        String colorizedPrefix = kr.knetsoft.knetraid.config.MessageManager.colorize(prefix);
        String original = LegacyComponentSerializer.legacySection().serialize(
                event.joinMessage() != null ? event.joinMessage() : net.kyori.adventure.text.Component.empty());
        event.joinMessage(LegacyComponentSerializer.legacySection().deserialize(colorizedPrefix + original));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String nickname = player.getName();

        plugin.getAsyncTaskTracker().begin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerData data = plugin.getPlayerRepository().upsertOnJoin(uuid, nickname);
                plugin.getPlayerDataCache().put(uuid, data);

                Optional<FactionData> faction = plugin.getFactionRepository().findFactionOfPlayer(uuid);
                plugin.getFactionManager().cacheFactionId(uuid, faction.map(FactionData::getId).orElse(null));
            } catch (SQLException exception) {
                plugin.getLogger().severe("플레이어 데이터를 불러오는 중 오류가 발생했습니다 (" + nickname + "): "
                        + exception.getMessage());
            } finally {
                plugin.getAsyncTaskTracker().end();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();

        plugin.getAsyncTaskTracker().begin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getPlayerRepository().updateLastQuit(uuid, now);
            } catch (SQLException exception) {
                plugin.getLogger().severe("플레이어 퇴장 정보를 저장하는 중 오류가 발생했습니다: " + exception.getMessage());
            } finally {
                plugin.getPlayerDataCache().remove(uuid);
                plugin.getFactionManager().clearPlayer(uuid);
                plugin.getAsyncTaskTracker().end();
            }
        });
    }
}
