package kr.knetsoft.knetraid.command;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.bounty.SystemIssuer;
import kr.knetsoft.knetraid.core.Permissions;
import kr.knetsoft.knetraid.economy.EconomyFormat;
import kr.knetsoft.knetraid.economy.EconomyResult;
import kr.knetsoft.knetraid.model.BaseData;
import kr.knetsoft.knetraid.model.BountyData;
import kr.knetsoft.knetraid.model.CurrencyReason;
import kr.knetsoft.knetraid.model.PlayerData;
import kr.knetsoft.knetraid.model.RaidLogEntry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * /약탈관리 - 서버 관리자 전용 명령어.
 * 도움말/재로드/정보/디버그/백업/약탈 활성화·비활성화/현상금/세력/로그/시즌 관리 모두 실제로 동작한다.
 */
public class AdminCommand extends BaseCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "도움말", "재로드", "정보", "디버그", "백업", "약탈", "현상금", "세력", "시즌", "로그");
    private static final long CONFIRM_WINDOW_MS = 30_000L;
    private final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public AdminCommand(KnetRaid plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!requirePermission(sender, Permissions.ADMIN)) return true;
            sendHelp(sender);
            return true;
        }

        switch (args[0]) {
            case "도움말" -> {
                if (!requirePermission(sender, Permissions.ADMIN)) return true;
                sendHelp(sender);
            }
            case "재로드" -> handleReload(sender);
            case "정보" -> handleInfo(sender);
            case "디버그" -> handleDebug(sender);
            case "백업" -> handleBackup(sender);
            case "약탈" -> handleRaidToggle(sender, args);
            case "현상금" -> handleBountyAdmin(sender, args);
            case "세력" -> handleFactionAdmin(sender, args);
            case "시즌" -> handleSeasonAdmin(sender, args);
            case "로그" -> handleBaseLog(sender, args);
            default -> sendUnknownSubCommand(sender, "/약탈관리 도움말");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(messages.get("admin.help-header"));
        sender.sendMessage(messages.get("help.line", "command", "/약탈관리 도움말", "description", "이 도움말을 표시합니다"));
        sender.sendMessage(messages.get("help.line", "command", "/약탈관리 재로드", "description", "설정 파일을 다시 불러옵니다"));
        sender.sendMessage(messages.get("help.line", "command", "/약탈관리 정보", "description", "플러그인 및 모듈 상태를 확인합니다"));
        sender.sendMessage(messages.get("help.line", "command", "/약탈관리 디버그", "description", "디버그 로그 출력을 전환합니다"));
        sender.sendMessage(messages.get("help.line", "command", "/약탈관리 백업", "description", "설정 및 데이터 파일을 백업합니다"));
        sender.sendMessage(messages.get("help.line", "command", "/약탈관리 약탈 <활성화|비활성화>", "description", "약탈 시스템을 켜고 끕니다"));
        sender.sendMessage(messages.get("help.line", "command", "/약탈관리 현상금 <등록|삭제> <닉네임> [금액]", "description", "현상금을 관리합니다"));
        sender.sendMessage(messages.get("help.line", "command", "/약탈관리 세력 해체 <세력명>", "description", "세력을 강제 해체합니다"));
        sender.sendMessage(messages.get("help.line", "command", "/약탈관리 시즌 <시작|종료|초기화>", "description", "시즌을 관리합니다"));
        sender.sendMessage(messages.get("help.line", "command", "/약탈관리 로그 <기지명>", "description", "기지의 최근 약탈 기록을 확인합니다"));
        sender.sendMessage(messages.get("help.footer"));
    }

    private void handleReload(CommandSender sender) {
        if (!requirePermission(sender, Permissions.RELOAD)) return;
        long start = System.currentTimeMillis();
        boolean success = plugin.reload();
        long elapsed = System.currentTimeMillis() - start;
        if (success) {
            sender.sendMessage(messages.get("general.reload-success", "time", elapsed));
        } else {
            sender.sendMessage(messages.get("general.reload-fail"));
        }
    }

    private void handleInfo(CommandSender sender) {
        if (!requirePermission(sender, Permissions.ADMIN)) return;
        sender.sendMessage(messages.get("admin.info-header"));
        sender.sendMessage(messages.get("admin.info-version", "version", plugin.getDescription().getVersion()));
        sender.sendMessage(messages.get("admin.info-server", "server", Bukkit.getVersion()));

        boolean dbConnected = plugin.getDatabaseManager().isConnected();
        String dbStatus = messages.get(dbConnected ? "admin.status-enabled" : "admin.status-disabled");
        sender.sendMessage(messages.get("admin.info-storage", "type", plugin.getDatabaseManager().getStorageType(), "status", dbStatus));
        sender.sendMessage(messages.get("admin.info-online-cache", "count", plugin.getPlayerDataCache().size()));
        sender.sendMessage(messages.get("admin.info-economy", "provider", plugin.getEconomyProvider().getName()));

        for (String module : new String[] {"raid", "faction", "combat", "bounty", "teleport", "economy", "season", "bedrock"}) {
            boolean enabled = plugin.getConfigManager().isModuleEnabled(module);
            String status = messages.get(enabled ? "admin.status-enabled" : "admin.status-disabled");
            sender.sendMessage(messages.get("admin.info-module", "module", module, "status", status));
        }
    }

    private void handleDebug(CommandSender sender) {
        if (!requirePermission(sender, Permissions.ADMIN)) return;
        try {
            boolean next = !plugin.getConfigManager().isDebug();
            plugin.getConfigManager().setDebug(next);
            sender.sendMessage(messages.get(next ? "admin.debug-on" : "admin.debug-off"));
        } catch (IOException exception) {
            plugin.getLogger().severe("디버그 설정을 저장하는 중 오류가 발생했습니다: " + exception.getMessage());
            sender.sendMessage(messages.get("general.reload-fail"));
        }
    }

    private void handleRaidToggle(CommandSender sender, String[] args) {
        if (!requirePermission(sender, Permissions.RAID_ADMIN)) return;
        if (args.length < 2 || !(args[1].equals("활성화") || args[1].equals("비활성화"))) {
            sendUsage(sender, "usage.admin-toggle");
            return;
        }
        boolean enable = args[1].equals("활성화");
        try {
            plugin.getConfigManager().setModuleEnabled("raid", enable);
            sender.sendMessage(messages.get(enable ? "admin.raid-enabled" : "admin.raid-disabled"));
        } catch (IOException exception) {
            plugin.getLogger().severe("약탈 시스템 설정을 저장하는 중 오류가 발생했습니다: " + exception.getMessage());
            sender.sendMessage(messages.get("general.reload-fail"));
        }
    }

    private void handleBountyAdmin(CommandSender sender, String[] args) {
        if (!requirePermission(sender, Permissions.BOUNTY_ADMIN)) return;
        if (args.length < 2 || !(args[1].equals("등록") || args[1].equals("삭제"))) {
            sendUsage(sender, "usage.admin-bounty-add");
            return;
        }
        if (args[1].equals("등록")) {
            if (args.length < 4) {
                sendUsage(sender, "usage.admin-bounty-add");
                return;
            }
            handleAdminBountyRegister(sender, args[2], args[3]);
            return;
        }
        if (args.length < 3) {
            sendUsage(sender, "usage.admin-bounty-remove");
            return;
        }
        handleAdminBountyRemove(sender, args[2]);
    }

    private void handleAdminBountyRegister(CommandSender sender, String targetName, String amountRaw) {
        double amount;
        try {
            amount = Double.parseDouble(amountRaw);
        } catch (NumberFormatException exception) {
            sender.sendMessage(messages.get("general.invalid-number", "input", amountRaw));
            return;
        }
        if (amount <= 0) {
            sender.sendMessage(messages.get("general.invalid-number", "input", amountRaw));
            return;
        }

        int decimalPlaces = getEconomyDecimalPlaces();
        FileConfiguration bountyConfig = plugin.getConfigManager().getModuleConfig("bounty.yml");
        int expireDays = bountyConfig != null ? bountyConfig.getInt("bounty.expire-days", 7) : 7;

        runAsync(sender, () -> {
            Optional<UUID> targetUuid = plugin.getPlayerRepository().findUuidByNickname(targetName);
            if (targetUuid.isEmpty()) {
                return () -> sender.sendMessage(messages.get("bounty.target-not-found", "player", targetName));
            }
            long now = System.currentTimeMillis();
            Long expiresAt = expireDays > 0 ? now + (expireDays * 86_400_000L) : null;
            plugin.getBountyRepository().create(targetUuid.get(), SystemIssuer.UUID_VALUE, amount, now, expiresAt);

            return () -> sender.sendMessage(messages.get("bounty.admin-register-success",
                    "player", targetName, "amount", EconomyFormat.format(amount, decimalPlaces)));
        });
    }

    private void handleAdminBountyRemove(CommandSender sender, String targetName) {
        int decimalPlaces = getEconomyDecimalPlaces();

        runAsync(sender, () -> {
            Optional<UUID> targetUuid = plugin.getPlayerRepository().findUuidByNickname(targetName);
            if (targetUuid.isEmpty()) {
                return () -> sender.sendMessage(messages.get("bounty.target-not-found", "player", targetName));
            }

            List<BountyData> active = plugin.getBountyRepository().listActiveByTarget(targetUuid.get());
            if (active.isEmpty()) {
                return () -> sender.sendMessage(messages.get("bounty.admin-cancel-none", "player", targetName));
            }

            List<String> ids = new ArrayList<>();
            double total = 0;
            for (BountyData bounty : active) {
                ids.add(bounty.id());
                total += bounty.amount();
                if (!SystemIssuer.isSystem(bounty.issuerUuid())) {
                    EconomyResult refund = plugin.getEconomyProvider().deposit(bounty.issuerUuid(), bounty.amount());
                    if (refund.success()) {
                        plugin.getCurrencyTransactionRepository().log(bounty.issuerUuid(), bounty.amount(), CurrencyReason.BOUNTY_REFUND);
                    } else {
                        plugin.getLogger().severe("관리자 현상금 취소 환불 실패 (issuer: " + bounty.issuerUuid() + "): " + refund.errorMessage());
                    }
                }
            }
            plugin.getBountyRepository().markCancelled(ids);

            int count = active.size();
            double finalTotal = total;
            return () -> sender.sendMessage(messages.get("bounty.admin-cancel-success",
                    "player", targetName, "count", count, "amount", EconomyFormat.format(finalTotal, decimalPlaces)));
        });
    }

    private int getEconomyDecimalPlaces() {
        FileConfiguration economyConfig = plugin.getConfigManager().getModuleConfig("economy.yml");
        return economyConfig != null ? economyConfig.getInt("economy.decimal-places", 2) : 2;
    }

    private void handleFactionAdmin(CommandSender sender, String[] args) {
        if (!requirePermission(sender, Permissions.FACTION_ADMIN)) return;
        if (args.length < 3 || !args[1].equals("해체")) {
            sendUsage(sender, "usage.admin-faction-disband");
            return;
        }
        String factionName = args[2];
        if (!confirmAction(sender, "faction-disband:" + factionName.toLowerCase(java.util.Locale.ROOT))) {
            sender.sendMessage(messages.get("faction.admin-disband-confirm", "name", factionName));
            return;
        }

        runAsync(sender, () -> {
            Optional<kr.knetsoft.knetraid.model.FactionData> faction = plugin.getFactionRepository().findByName(factionName);
            if (faction.isEmpty()) {
                return () -> sender.sendMessage(messages.get("faction.info-not-found", "name", factionName));
            }
            String factionId = faction.get().getId();
            List<kr.knetsoft.knetraid.model.FactionMember> members = plugin.getFactionRepository().listMembers(factionId);
            plugin.getFactionRepository().deleteFactionCascade(factionId);

            return () -> {
                for (kr.knetsoft.knetraid.model.FactionMember member : members) {
                    plugin.getFactionManager().cacheFactionId(member.getPlayerUuid(), null);
                    plugin.getFactionManager().clearFactionChat(member.getPlayerUuid());
                    org.bukkit.entity.Player online = Bukkit.getPlayer(member.getPlayerUuid());
                    if (online != null) {
                        online.sendMessage(messages.get("faction.admin-disbanded-notice", "name", faction.get().getName()));
                    }
                }
                sender.sendMessage(messages.get("faction.admin-disband-success", "name", faction.get().getName()));
            };
        });
    }

    private void handleSeasonAdmin(CommandSender sender, String[] args) {
        if (!requirePermission(sender, Permissions.SEASON_ADMIN)) return;
        if (args.length < 2) {
            sendUsage(sender, "usage.admin-season");
            return;
        }
        switch (args[1]) {
            case "시작" -> handleSeasonStart(sender, args);
            case "종료" -> handleSeasonEnd(sender);
            case "초기화" -> handleSeasonReset(sender, args);
            default -> sendUsage(sender, "usage.admin-season");
        }
    }

    private void handleSeasonStart(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "usage.admin-season-start");
            return;
        }
        String name = args[2];
        runAsync(sender, () -> {
            Optional<kr.knetsoft.knetraid.model.SeasonData> active = plugin.getSeasonRepository().findActiveSeason();
            if (active.isPresent()) {
                return () -> sender.sendMessage(messages.get("season.already-active", "name", active.get().name()));
            }
            kr.knetsoft.knetraid.model.SeasonData season = plugin.getSeasonRepository().createSeason(name, System.currentTimeMillis());
            return () -> {
                plugin.getSeasonManager().setActiveSeason(season.id(), season.name());
                sender.sendMessage(messages.get("season.start-success", "name", name));
            };
        });
    }

    private void handleSeasonEnd(CommandSender sender) {
        if (!confirmAction(sender, "season-end")) {
            sender.sendMessage(messages.get("season.end-confirm"));
            return;
        }
        runAsync(sender, () -> {
            Optional<kr.knetsoft.knetraid.model.SeasonData> active = plugin.getSeasonRepository().findActiveSeason();
            if (active.isEmpty()) {
                return () -> sender.sendMessage(messages.get("season.no-active"));
            }
            plugin.getSeasonEndService().endSeason(active.get());
            return () -> sender.sendMessage(messages.get("season.end-success", "name", active.get().name()));
        });
    }

    private void handleSeasonReset(CommandSender sender, String[] args) {
        if (!confirmAction(sender, "season-reset")) {
            sender.sendMessage(messages.get("season.reset-confirm"));
            return;
        }
        String requestedName = args.length >= 3 ? args[2] : null;
        runAsync(sender, () -> {
            Optional<kr.knetsoft.knetraid.model.SeasonData> active = plugin.getSeasonRepository().findActiveSeason();
            if (active.isPresent()) {
                plugin.getSeasonEndService().endSeason(active.get());
            }
            String newName = requestedName != null
                    ? requestedName
                    : "시즌 " + (plugin.getSeasonRepository().countSeasons() + 1);
            kr.knetsoft.knetraid.model.SeasonData newSeason = plugin.getSeasonRepository().createSeason(newName, System.currentTimeMillis());
            return () -> {
                plugin.getSeasonManager().setActiveSeason(newSeason.id(), newSeason.name());
                sender.sendMessage(messages.get("season.reset-success", "name", newSeason.name()));
            };
        });
    }

    /**
     * 파괴적인 관리자 작업(시즌 종료/초기화)에 대한 30초 확인 절차.
     * 플레이어와 콘솔을 모두 구분해 키를 관리한다.
     */
    private boolean confirmAction(CommandSender sender, String actionKey) {
        String key = actionKey + ":" + (sender instanceof org.bukkit.entity.Player p ? p.getUniqueId() : "CONSOLE");
        long now = System.currentTimeMillis();
        Long requestedAt = pendingConfirmations.get(key);
        if (requestedAt != null && now - requestedAt <= CONFIRM_WINDOW_MS) {
            pendingConfirmations.remove(key);
            return true;
        }
        pendingConfirmations.put(key, now);
        return false;
    }

    private void handleBaseLog(CommandSender sender, String[] args) {
        if (!requirePermission(sender, Permissions.RAID_ADMIN)) return;
        if (args.length < 2) {
            sendUsage(sender, "usage.admin-log");
            return;
        }
        String baseName = args[1];
        Optional<BaseData> base = plugin.getBaseManager().all().stream()
                .filter(b -> b.getName().equalsIgnoreCase(baseName))
                .findFirst();
        if (base.isEmpty()) {
            sender.sendMessage(messages.get("raid.base-not-found", "name", baseName));
            return;
        }

        runAsync(sender, () -> {
            List<RaidLogEntry> entries = plugin.getRaidLogRepository().listRecentByBase(base.get().getId(), 10);
            List<String> lines = new ArrayList<>();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (RaidLogEntry entry : entries) {
                String actorName = plugin.getPlayerRepository().find(entry.actorUuid())
                        .map(PlayerData::getNickname)
                        .orElse(entry.actorUuid().toString());
                lines.add(messages.get("admin.log-line",
                        "date", format.format(new Date(entry.occurredAt())),
                        "player", actorName,
                        "action", entry.actionType().name(),
                        "x", entry.x(), "y", entry.y(), "z", entry.z()));
            }
            return () -> {
                if (lines.isEmpty()) {
                    sender.sendMessage(messages.get("admin.log-empty", "name", base.get().getName()));
                    return;
                }
                sender.sendMessage(messages.get("admin.log-header", "name", base.get().getName()));
                for (String line : lines) {
                    sender.sendMessage(line);
                }
            };
        });
    }

    private void handleBackup(CommandSender sender) {
        if (!requirePermission(sender, Permissions.ADMIN)) return;
        sender.sendMessage(messages.get("admin.backup-start"));

        File dataFolder = plugin.getDataFolder();
        File backupDir = new File(dataFolder, "backups");
        String fileName = "backup_" + LocalDateTime.now().format(BACKUP_FORMAT) + ".zip";
        File backupFile = new File(backupDir, fileName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success;
            try {
                Files.createDirectories(backupDir.toPath());
                zipDirectory(dataFolder.toPath(), backupDir.toPath(), backupFile);
                success = true;
            } catch (IOException exception) {
                plugin.getLogger().severe("백업 생성 중 오류가 발생했습니다: " + exception.getMessage());
                success = false;
            }

            boolean finalSuccess = success;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalSuccess) {
                    sender.sendMessage(messages.get("admin.backup-success", "file", fileName));
                } else {
                    sender.sendMessage(messages.get("admin.backup-fail"));
                }
            });
        });
    }

    private void zipDirectory(Path sourceRoot, Path backupDir, File targetZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetZip))) {
            try (var walk = Files.walk(sourceRoot)) {
                for (Path path : (Iterable<Path>) walk::iterator) {
                    if (Files.isDirectory(path) || path.startsWith(backupDir)) {
                        continue;
                    }
                    String entryName = sourceRoot.relativize(path).toString().replace(File.separatorChar, '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    try (FileInputStream fis = new FileInputStream(path.toFile())) {
                        fis.transferTo(zos);
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partialMatches(args[0], SUB_COMMANDS);
        }
        if (args.length == 2) {
            return switch (args[0]) {
                case "약탈" -> partialMatches(args[1], Arrays.asList("활성화", "비활성화"));
                case "현상금" -> partialMatches(args[1], Arrays.asList("등록", "삭제"));
                case "세력" -> partialMatches(args[1], List.of("해체"));
                case "시즌" -> partialMatches(args[1], Arrays.asList("시작", "종료", "초기화"));
                default -> List.of();
            };
        }
        return List.of();
    }
}
