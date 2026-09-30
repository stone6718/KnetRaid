package kr.knetsoft.knetraid.teleport;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.economy.EconomyResult;
import kr.knetsoft.knetraid.model.BaseData;
import kr.knetsoft.knetraid.model.CurrencyReason;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 월드 경계·기지 보호구역·스폰을 피해 안전한 무작위 위치를 비동기로 찾아 이동시킨다.
 * 각 시도마다 대상 청크를 {@link World#getChunkAtAsync(int, int)}로 미리 불러와
 * 메인 스레드를 오래 점유하지 않도록 한다.
 */
public class RandomTeleportService {

    private static final Set<Material> UNSAFE_GROUND = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK,
            Material.CACTUS, Material.WATER, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.POWDER_SNOW, Material.VOID_AIR, Material.BUBBLE_COLUMN);

    private final KnetRaid plugin;

    public RandomTeleportService(KnetRaid plugin) {
        this.plugin = plugin;
    }

    public void teleport(Player player, World world, FileConfiguration teleportConfig, double cost) {
        attempt(player, world, teleportConfig, cost, 1);
    }

    private void attempt(Player player, World world, FileConfiguration teleportConfig, double cost, int attemptNumber) {
        if (!player.isOnline()) {
            return;
        }
        int maxAttempts = teleportConfig.getInt("teleport.max-attempts", 20);
        if (attemptNumber > maxAttempts) {
            player.sendMessage(plugin.getMessageManager().get("teleport.search-failed"));
            return;
        }

        int[] candidate = pickCandidate(world, teleportConfig);
        int x = candidate[0];
        int z = candidate[1];

        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            if (Bukkit.isPrimaryThread()) {
                onChunkReady(player, world, teleportConfig, cost, attemptNumber, x, z);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> onChunkReady(player, world, teleportConfig, cost, attemptNumber, x, z));
            }
        });
    }

    private void onChunkReady(Player player, World world, FileConfiguration teleportConfig, double cost, int attemptNumber, int x, int z) {
        Location safe = checkSafe(world, teleportConfig, x, z);
        if (safe == null) {
            attempt(player, world, teleportConfig, cost, attemptNumber + 1);
            return;
        }
        finish(player, safe, cost);
    }

    private int[] pickCandidate(World world, FileConfiguration teleportConfig) {
        double minRadius = Math.max(0, teleportConfig.getDouble("teleport.min-radius", 100));
        double maxRadius = Math.max(minRadius + 1, teleportConfig.getDouble("teleport.max-radius", 5000));

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(0, Math.PI * 2);
        double distance = random.nextDouble(minRadius, maxRadius);
        int x = (int) Math.round(Math.cos(angle) * distance);
        int z = (int) Math.round(Math.sin(angle) * distance);

        WorldBorder border = world.getWorldBorder();
        double half = (border.getSize() / 2.0) - 16;
        Location center = border.getCenter();
        double minX = center.getX() - half;
        double maxX = center.getX() + half;
        double minZ = center.getZ() - half;
        double maxZ = center.getZ() + half;

        x = (int) Math.max(minX, Math.min(maxX, x));
        z = (int) Math.max(minZ, Math.min(maxZ, z));
        return new int[] {x, z};
    }

    private Location checkSafe(World world, FileConfiguration teleportConfig, int x, int z) {
        int highestY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (highestY <= world.getMinHeight() + 1 || highestY >= world.getMaxHeight() - 2) {
            return null;
        }

        Block ground = world.getBlockAt(x, highestY - 1, z);
        Material groundType = ground.getType();
        if (!groundType.isSolid() || UNSAFE_GROUND.contains(groundType)) {
            return null;
        }

        Block feet = world.getBlockAt(x, highestY, z);
        Block head = world.getBlockAt(x, highestY + 1, z);
        if (!feet.getType().isAir() || !head.getType().isAir()) {
            return null;
        }

        if (isNearProtectedSpawn(world, teleportConfig, x, z) || isInsideProtectedBase(world, x, z, highestY)) {
            return null;
        }

        return new Location(world, x + 0.5, highestY, z + 0.5);
    }

    private boolean isNearProtectedSpawn(World world, FileConfiguration teleportConfig, int x, int z) {
        FileConfiguration raidConfig = plugin.getConfigManager().getModuleConfig("raid.yml");
        if (raidConfig == null || !raidConfig.getBoolean("raid.protect-spawn", true)) {
            return false;
        }
        double radius = raidConfig.getDouble("raid.spawn-radius", 100);
        Location spawn = world.getSpawnLocation();
        double dx = spawn.getX() - x;
        double dz = spawn.getZ() - z;
        return (dx * dx + dz * dz) <= (radius * radius);
    }

    private boolean isInsideProtectedBase(World world, int x, int z, int y) {
        FileConfiguration raidConfig = plugin.getConfigManager().getModuleConfig("raid.yml");
        double protectionRadius = raidConfig != null ? raidConfig.getDouble("raid.protection-radius", 15) : 15;
        double radiusSquared = protectionRadius * protectionRadius;

        for (BaseData base : plugin.getBaseManager().inWorld(world.getName())) {
            if (base.distanceSquared(world.getName(), x, y, z) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private void finish(Player player, Location location, double cost) {
        player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
            Runnable postTeleport = () -> {
                if (!Boolean.TRUE.equals(success)) {
                    player.sendMessage(plugin.getMessageManager().get("teleport.search-failed"));
                    return;
                }
                plugin.getTeleportManager().recordTeleport(player.getUniqueId());
                player.sendMessage(plugin.getMessageManager().get("teleport.success"));
                chargeCostIfNeeded(player, cost);
            };
            if (Bukkit.isPrimaryThread()) {
                postTeleport.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, postTeleport);
            }
        });
    }

    private void chargeCostIfNeeded(Player player, double cost) {
        if (cost <= 0) {
            return;
        }
        EconomyResult result = plugin.getEconomyProvider().withdraw(player.getUniqueId(), cost);
        if (!result.success()) {
            return;
        }
        plugin.getAsyncTaskTracker().begin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getCurrencyTransactionRepository().log(player.getUniqueId(), -cost, CurrencyReason.TELEPORT_COST);
            } catch (java.sql.SQLException exception) {
                plugin.getLogger().severe("텔레포트 비용 기록 중 오류가 발생했습니다: " + exception.getMessage());
            } finally {
                plugin.getAsyncTaskTracker().end();
            }
        });
    }
}
