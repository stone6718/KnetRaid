package kr.knetsoft.knetraid.command;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.economy.EconomyFormat;
import kr.knetsoft.knetraid.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /야생 - 랜덤 텔레포트 명령어. 7단계에서 실제 안전 위치 탐색·이동 로직으로 구현되었다.
 */
public class WildernessCommand extends BaseCommand {

    public WildernessCommand(KnetRaid plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!requirePlayer(sender)) {
            return true;
        }
        if (!requireModule(sender, "teleport")) {
            return true;
        }
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        World world = player.getWorld();
        if (args.length >= 1) {
            World specified = Bukkit.getWorld(args[0]);
            if (specified == null) {
                sender.sendMessage(messages.get("general.invalid-world", "world", args[0]));
                return true;
            }
            world = specified;
        }

        FileConfiguration teleportConfig = plugin.getConfigManager().getModuleConfig("teleport.yml");
        if (teleportConfig == null) {
            sender.sendMessage(messages.get("general.db-error"));
            return true;
        }
        if (!teleportConfig.getBoolean("teleport.worlds." + world.getName(), true)) {
            player.sendMessage(messages.get("teleport.world-disabled"));
            return true;
        }
        if (plugin.getCombatManager().isInCombat(uuid)) {
            player.sendMessage(messages.get("teleport.combat-blocked"));
            return true;
        }
        if (plugin.getTeleportManager().hasPendingWarmup(uuid)) {
            player.sendMessage(messages.get("teleport.already-warming-up"));
            return true;
        }

        long cooldownMillis = teleportConfig.getLong("teleport.cooldown-seconds", 60) * 1000L;
        if (plugin.getTeleportManager().isOnCooldown(uuid, cooldownMillis)) {
            long remaining = plugin.getTeleportManager().getRemainingCooldownSeconds(uuid, cooldownMillis);
            player.sendMessage(messages.get("teleport.cooldown", "seconds", remaining));
            return true;
        }

        double cost = teleportConfig.getDouble("teleport.cost", 0);
        int decimalPlaces = getDecimalPlaces();
        if (cost > 0) {
            EconomyProvider economy = plugin.getEconomyProvider();
            if (!economy.isAvailable()) {
                player.sendMessage(messages.get("bounty.economy-unavailable"));
                return true;
            }
            if (!economy.has(uuid, cost)) {
                player.sendMessage(messages.get("bounty.insufficient-funds", "amount", EconomyFormat.format(cost, decimalPlaces)));
                return true;
            }
        }

        World finalWorld = world;
        int warmupSeconds = teleportConfig.getInt("teleport.warmup-seconds", 3);
        if (warmupSeconds <= 0) {
            player.sendMessage(messages.get("teleport.searching"));
            plugin.getRandomTeleportService().teleport(player, finalWorld, teleportConfig, cost);
            return true;
        }

        player.sendMessage(messages.get("teleport.warmup-start", "seconds", warmupSeconds));
        Location startLocation = player.getLocation();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getTeleportManager().completeWarmup(uuid);
            player.sendMessage(messages.get("teleport.searching"));
            plugin.getRandomTeleportService().teleport(player, finalWorld, teleportConfig, cost);
        }, warmupSeconds * 20L);
        plugin.getTeleportManager().startWarmup(uuid, startLocation, task);

        return true;
    }

    private int getDecimalPlaces() {
        FileConfiguration economyConfig = plugin.getConfigManager().getModuleConfig("economy.yml");
        return economyConfig != null ? economyConfig.getInt("economy.decimal-places", 2) : 2;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> worldNames = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
            return partialMatches(args[0], worldNames);
        }
        return List.of();
    }
}
