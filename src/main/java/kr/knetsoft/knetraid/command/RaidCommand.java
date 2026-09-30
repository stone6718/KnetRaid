package kr.knetsoft.knetraid.command;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.core.Permissions;
import kr.knetsoft.knetraid.database.BaseRepository;
import kr.knetsoft.knetraid.model.BaseData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * /약탈 - 약탈 시스템 정보 및 기지 관리 명령어. 4단계에서 실제 DB 기반 로직으로 구현되었다.
 */
public class RaidCommand extends BaseCommand {

    private static final List<String> TOP_SUB_COMMANDS = Arrays.asList("정보", "기지");
    private static final List<String> BASE_SUB_COMMANDS = Arrays.asList("등록", "정보", "삭제", "목록");

    private final BaseRepository baseRepository;

    public RaidCommand(KnetRaid plugin) {
        super(plugin);
        this.baseRepository = plugin.getBaseRepository();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!requireModule(sender, "raid")) {
            return true;
        }
        if (args.length == 0) {
            sendUnknownSubCommand(sender, "/약탈 정보");
            return true;
        }

        switch (args[0]) {
            case "정보" -> sendRaidInfo(sender);
            case "기지" -> handleBase(sender, args);
            default -> sendUnknownSubCommand(sender, "/약탈 정보");
        }
        return true;
    }

    private void sendRaidInfo(CommandSender sender) {
        FileConfiguration raidConfig = plugin.getConfigManager().getModuleConfig("raid.yml");
        String mode = raidConfig != null ? raidConfig.getString("raid.mode", "FREE") : "FREE";
        boolean protectSpawn = raidConfig != null && raidConfig.getBoolean("raid.protect-spawn", true);
        double protectionRadius = raidConfig != null ? raidConfig.getDouble("raid.protection-radius", 15) : 15;

        sender.sendMessage(messages.get("raid.info-header"));
        sender.sendMessage(messages.get("raid.info-mode", "mode", mode));
        sender.sendMessage(messages.get("raid.info-status", "status",
                messages.get(plugin.getConfigManager().isModuleEnabled("raid") ? "admin.status-enabled" : "admin.status-disabled")));
        sender.sendMessage(messages.get("raid.info-spawn-protect", "status",
                messages.get(protectSpawn ? "admin.status-enabled" : "admin.status-disabled")));
        sender.sendMessage(messages.get("raid.info-protection-radius", "radius", (int) protectionRadius));
    }

    private void handleBase(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;

        if (args.length < 2) {
            sendUsage(sender, "usage.raid-base");
            return;
        }

        switch (args[1]) {
            case "등록" -> handleBaseRegister(player, args);
            case "정보" -> handleBaseInfo(player, args);
            case "삭제" -> handleBaseDelete(player, args);
            case "목록" -> handleBaseListAll(player);
            default -> sendUsage(sender, "usage.raid-base");
        }
    }

    private void handleBaseRegister(Player player, String[] args) {
        if (args.length < 3) {
            sendUsage(player, "usage.raid-base-register");
            return;
        }
        String name = args[2];
        FileConfiguration raidConfig = plugin.getConfigManager().getModuleConfig("raid.yml");
        int minLength = 2;
        int maxLength = 24;
        if (name.length() < minLength || name.length() > maxLength) {
            player.sendMessage(messages.get("raid.base-name-invalid-length", "min", minLength, "max", maxLength));
            return;
        }

        World world = player.getWorld();
        if (raidConfig != null && !raidConfig.getBoolean("raid.worlds." + world.getName(), true)) {
            player.sendMessage(messages.get("raid.base-world-disabled"));
            return;
        }

        Location location = player.getLocation();
        UUID uuid = player.getUniqueId();

        runAsync(player, () -> {
            if (baseRepository.findByOwnerAndName(uuid, name).isPresent()) {
                return () -> player.sendMessage(messages.get("raid.base-name-taken", "name", name));
            }

            String factionId = plugin.getFactionManager().getCachedFactionId(uuid).orElse(null);
            String id = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            BaseData base = new BaseData(id, name, uuid, factionId, world.getName(),
                    location.getX(), location.getY(), location.getZ(), now, null, 0L, player.getName());
            baseRepository.create(base);

            Optional<BaseData> withOwnerInfo = baseRepository.findById(id);

            return () -> {
                withOwnerInfo.ifPresent(plugin.getBaseManager()::add);
                player.sendMessage(messages.get("raid.base-register-success", "name", name));
            };
        });
    }

    private void handleBaseInfo(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        boolean named = args.length >= 3;
        String targetName = named ? args[2] : null;

        runAsync(player, () -> {
            if (named) {
                Optional<BaseData> base = baseRepository.findByOwnerAndName(uuid, targetName);
                if (base.isEmpty()) {
                    String finalTargetName = targetName;
                    return () -> player.sendMessage(messages.get("raid.base-not-found", "name", finalTargetName));
                }
                return () -> sendBaseDetail(player, base.get());
            }

            List<BaseData> bases = baseRepository.listByOwner(uuid);
            return () -> {
                if (bases.isEmpty()) {
                    player.sendMessage(messages.get("raid.base-list-empty"));
                    return;
                }
                player.sendMessage(messages.get("raid.base-list-header", "count", bases.size()));
                for (BaseData base : bases) {
                    player.sendMessage(messages.get("raid.base-list-line",
                            "name", base.getName(), "world", base.getWorld(),
                            "x", (int) base.getX(), "y", (int) base.getY(), "z", (int) base.getZ()));
                }
            };
        });
    }

    private void sendBaseDetail(Player player, BaseData base) {
        player.sendMessage(messages.get("raid.base-info-header", "name", base.getName()));
        player.sendMessage(messages.get("raid.base-info-location",
                "world", base.getWorld(), "x", (int) base.getX(), "y", (int) base.getY(), "z", (int) base.getZ()));
        String registeredDate = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(base.getRegisteredAt()));
        player.sendMessage(messages.get("raid.base-info-registered", "date", registeredDate));
        String lastRaid = base.getLastRaidAt() != null
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(base.getLastRaidAt()))
                : messages.get("faction.none");
        player.sendMessage(messages.get("raid.base-info-last-raid", "date", lastRaid));
    }

    private void handleBaseDelete(Player player, String[] args) {
        if (args.length < 3) {
            sendUsage(player, "usage.raid-base-delete");
            return;
        }
        String name = args[2];
        UUID uuid = player.getUniqueId();

        runAsync(player, () -> {
            Optional<BaseData> base = baseRepository.findByOwnerAndName(uuid, name);
            if (base.isEmpty()) {
                return () -> player.sendMessage(messages.get("raid.base-not-found", "name", name));
            }
            baseRepository.delete(base.get().getId());

            return () -> {
                plugin.getBaseManager().remove(base.get().getId());
                player.sendMessage(messages.get("raid.base-delete-success", "name", name));
            };
        });
    }

    private void handleBaseListAll(Player player) {
        if (!requirePermission(player, Permissions.RAID_ADMIN)) {
            return;
        }
        runAsync(player, () -> {
            List<BaseData> bases = baseRepository.listAll();
            List<String> lines = new ArrayList<>();
            for (BaseData base : bases) {
                lines.add(messages.get("raid.base-admin-list-line",
                        "name", base.getName(), "owner", base.getOwnerNickname(),
                        "world", base.getWorld(), "x", (int) base.getX(), "y", (int) base.getY(), "z", (int) base.getZ()));
            }
            return () -> {
                if (lines.isEmpty()) {
                    player.sendMessage(messages.get("raid.base-list-empty"));
                    return;
                }
                player.sendMessage(messages.get("raid.base-list-header", "count", lines.size()));
                for (String line : lines) {
                    player.sendMessage(line);
                }
            };
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partialMatches(args[0], TOP_SUB_COMMANDS);
        }
        if (args.length == 2 && "기지".equals(args[0])) {
            return partialMatches(args[1], BASE_SUB_COMMANDS);
        }
        return List.of();
    }
}
