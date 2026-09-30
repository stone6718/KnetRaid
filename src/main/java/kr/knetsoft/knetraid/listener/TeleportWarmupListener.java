package kr.knetsoft.knetraid.listener;

import kr.knetsoft.knetraid.KnetRaid;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * 랜덤 텔레포트 대기(warmup) 중 이동하거나 피격당하면 이동을 취소한다.
 */
public class TeleportWarmupListener implements Listener {

    private final KnetRaid plugin;

    public TeleportWarmupListener(KnetRaid plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!plugin.getTeleportManager().hasPendingWarmup(uuid)) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Optional<Location> start = plugin.getTeleportManager().getWarmupStartLocation(uuid);
        if (start.isEmpty()) {
            return;
        }
        Location from = start.get();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            plugin.getTeleportManager().cancelWarmup(uuid);
            event.getPlayer().sendMessage(plugin.getMessageManager().get("teleport.warmup-cancelled-move"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!plugin.getTeleportManager().hasPendingWarmup(uuid)) {
            return;
        }
        FileConfiguration teleportConfig = plugin.getConfigManager().getModuleConfig("teleport.yml");
        boolean cancelOnDamage = teleportConfig == null || teleportConfig.getBoolean("teleport.cancel-on-damage", true);
        if (!cancelOnDamage) {
            return;
        }
        plugin.getTeleportManager().cancelWarmup(uuid);
        player.sendMessage(plugin.getMessageManager().get("teleport.warmup-cancelled-damage"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getTeleportManager().cancelWarmup(event.getPlayer().getUniqueId());
    }
}
