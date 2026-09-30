package kr.knetsoft.knetraid.listener;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.model.BaseData;
import kr.knetsoft.knetraid.model.RaidActionType;
import kr.knetsoft.knetraid.model.RaidLogEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 스폰 보호와 기지(PROTECTED 모드) 블록/컨테이너 보호를 처리하고,
 * 소유자가 아닌 플레이어의 상호작용을 knetraid_raid_logs에 기록한다.
 * 모든 판정은 BaseManager의 메모리 캐시만 사용하며 이벤트 경로에서 DB를 조회하지 않는다.
 */
public class RaidProtectionListener implements Listener {

    private final KnetRaid plugin;

    public RaidProtectionListener(KnetRaid plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        handleBlockEvent(event.getPlayer(), event.getBlock().getLocation(), RaidActionType.BLOCK_BREAK, "raid.block-protection", event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        handleBlockEvent(event.getPlayer(), event.getBlock().getLocation(), RaidActionType.BLOCK_PLACE, "raid.block-protection", event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        Location location = event.getInventory().getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        // 플레이어 인벤토리, 작업대 등 컨테이너가 아닌 경우는 대상에서 제외한다.
        if (holder == null || holder instanceof Player) {
            return;
        }
        handleBlockEvent(player, location, RaidActionType.CONTAINER_ACCESS, "raid.container-protection", event);
    }

    private void handleBlockEvent(Player player, Location location, RaidActionType actionType, String featureFlagPath,
                                   org.bukkit.event.Cancellable event) {
        if (!plugin.getConfigManager().isModuleEnabled("raid")) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        FileConfiguration raidConfig = plugin.getConfigManager().getModuleConfig("raid.yml");
        if (raidConfig == null || !raidConfig.getBoolean("raid.worlds." + world.getName(), true)) {
            return;
        }

        if (checkSpawnProtection(player, location, world, raidConfig)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().get("raid.spawn-protected"));
            return;
        }

        List<BaseData> candidates = plugin.getBaseManager().inWorld(world.getName());
        if (candidates.isEmpty()) {
            return;
        }

        double protectionRadius = raidConfig.getDouble("raid.protection-radius", 15);
        double radiusSquared = protectionRadius * protectionRadius;

        for (BaseData base : candidates) {
            double distanceSquared = base.distanceSquared(world.getName(), location.getX(), location.getY(), location.getZ());
            if (distanceSquared > radiusSquared) {
                continue;
            }
            if (player.getUniqueId().equals(base.getOwnerUuid())) {
                continue;
            }

            boolean protectionActive = isProtectionActive(base, raidConfig, featureFlagPath);
            boolean hasAccess = hasAccess(base, player, raidConfig, protectionActive);

            if (!hasAccess) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessageManager().get("raid.base-protected", "owner", ownerDisplay(base)));
            }

            logInteraction(base, player, actionType, location);
        }
    }

    private boolean checkSpawnProtection(Player player, Location location, World world, FileConfiguration raidConfig) {
        if (!raidConfig.getBoolean("raid.protect-spawn", true)) {
            return false;
        }
        if (player.hasPermission("knetraid.raid.admin")) {
            return false;
        }
        double radius = raidConfig.getDouble("raid.spawn-radius", 100);
        Location spawn = world.getSpawnLocation();
        double dx = spawn.getX() - location.getX();
        double dz = spawn.getZ() - location.getZ();
        return (dx * dx + dz * dz) <= (radius * radius);
    }

    private boolean isProtectionActive(BaseData base, FileConfiguration raidConfig, String featureFlagPath) {
        if (isWithinNewPlayerGrace(base, raidConfig)) {
            return true;
        }
        boolean modeProtected = "PROTECTED".equalsIgnoreCase(raidConfig.getString("raid.mode", "FREE"));
        if (!modeProtected || !raidConfig.getBoolean(featureFlagPath, true)) {
            return false;
        }
        // 약탈 가능 시간이 설정되어 있고 지금이 그 시간대라면, 그 시간 동안은 보호를 해제해 약탈을 허용한다.
        return !isWithinActiveRaidHours(raidConfig);
    }

    private boolean isWithinNewPlayerGrace(BaseData base, FileConfiguration raidConfig) {
        int minutes = raidConfig.getInt("raid.new-player-protection-minutes", 60);
        if (minutes <= 0) {
            return false;
        }
        return (System.currentTimeMillis() - base.getOwnerFirstJoinAt()) < minutes * 60_000L;
    }

    private boolean isWithinActiveRaidHours(FileConfiguration raidConfig) {
        if (!raidConfig.getBoolean("raid.active-hours.enabled", false)) {
            return false;
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(raidConfig.getString("raid.active-hours.start", "09:00"), formatter);
            LocalTime end = LocalTime.parse(raidConfig.getString("raid.active-hours.end", "23:00"), formatter);
            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            }
            // 자정을 걸치는 시간대(예: 22:00~06:00)
            return !now.isBefore(start) || now.isBefore(end);
        } catch (Exception exception) {
            plugin.getLogger().warning("raid.yml의 active-hours 시간 형식이 올바르지 않습니다 (HH:mm 형식 필요): " + exception.getMessage());
            return false;
        }
    }

    private boolean hasAccess(BaseData base, Player player, FileConfiguration raidConfig, boolean protectionActive) {
        if (!protectionActive) {
            return true;
        }
        if (player.getUniqueId().equals(base.getOwnerUuid())) {
            return true;
        }
        if (base.getFactionId() != null && raidConfig.getBoolean("raid.faction-member-raid-allowed", false)) {
            Optional<String> actorFaction = plugin.getFactionManager().getCachedFactionId(player.getUniqueId());
            if (actorFaction.isPresent() && actorFaction.get().equals(base.getFactionId())) {
                return true;
            }
        }
        return false;
    }

    private void logInteraction(BaseData base, Player actor, RaidActionType actionType, Location location) {
        UUID actorUuid = actor.getUniqueId();
        String baseId = base.getId();
        long now = System.currentTimeMillis();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        String world = location.getWorld() != null ? location.getWorld().getName() : base.getWorld();

        Optional<String> activeSeasonId = plugin.getConfigManager().isModuleEnabled("season")
                ? plugin.getSeasonManager().getActiveSeasonId()
                : Optional.empty();

        plugin.getAsyncTaskTracker().begin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                RaidLogEntry entry = new RaidLogEntry(UUID.randomUUID().toString(), baseId, actorUuid, actionType,
                        world, x, y, z, now);
                plugin.getRaidLogRepository().insert(entry);
                plugin.getBaseRepository().updateLastRaidAt(baseId, now);
                base.setLastRaidAt(now);
                if (activeSeasonId.isPresent()) {
                    plugin.getSeasonRepository().addRaid(activeSeasonId.get(), actorUuid);
                }
            } catch (java.sql.SQLException exception) {
                plugin.getLogger().severe("약탈 기록을 저장하는 중 오류가 발생했습니다: " + exception.getMessage());
            } finally {
                plugin.getAsyncTaskTracker().end();
            }
        });
    }

    private String ownerDisplay(BaseData base) {
        return base.getOwnerNickname();
    }
}
