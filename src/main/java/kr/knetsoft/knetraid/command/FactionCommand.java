package kr.knetsoft.knetraid.command;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.database.FactionRepository;
import kr.knetsoft.knetraid.database.PlayerRepository;
import kr.knetsoft.knetraid.faction.FactionInvite;
import kr.knetsoft.knetraid.faction.FactionManager;
import kr.knetsoft.knetraid.model.FactionData;
import kr.knetsoft.knetraid.model.FactionMember;
import kr.knetsoft.knetraid.model.FactionRelationType;
import kr.knetsoft.knetraid.model.FactionRelationView;
import kr.knetsoft.knetraid.model.FactionRole;
import kr.knetsoft.knetraid.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /세력 - 세력 시스템 명령어. 3단계에서 실제 DB 기반 로직으로 구현되었다.
 */
public class FactionCommand extends BaseCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "생성", "해체", "초대", "수락", "거절", "탈퇴", "추방",
            "승급", "강등", "정보", "목록", "채팅", "동맹", "적대", "순위");

    private final FactionRepository factionRepository;
    private final PlayerRepository playerRepository;
    private final FactionManager factionManager;

    public FactionCommand(KnetRaid plugin) {
        super(plugin);
        this.factionRepository = plugin.getFactionRepository();
        this.playerRepository = plugin.getPlayerRepository();
        this.factionManager = plugin.getFactionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!requirePlayer(sender)) {
            return true;
        }
        if (!requireModule(sender, "faction")) {
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            sendUnknownSubCommand(sender, "/약탈도움말");
            return true;
        }

        switch (args[0]) {
            case "생성" -> handleCreate(player, args);
            case "해체" -> handleDisband(player);
            case "초대" -> handleInvite(player, args);
            case "수락" -> handleAccept(player, args);
            case "거절" -> handleDeny(player, args);
            case "탈퇴" -> handleLeave(player);
            case "추방" -> handleKick(player, args);
            case "승급" -> handlePromote(player, args);
            case "강등" -> handleDemote(player, args);
            case "정보" -> handleInfo(player, args);
            case "목록" -> handleList(player);
            case "채팅" -> handleChatToggle(player);
            case "동맹" -> handleRelation(player, args, FactionRelationType.ALLY);
            case "적대" -> handleRelation(player, args, FactionRelationType.ENEMY);
            case "순위" -> handleRank(player);
            default -> sendUnknownSubCommand(sender, "/약탈도움말");
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "usage.faction-create");
            return;
        }
        String name = args[1];
        FileConfiguration factionConfig = plugin.getConfigManager().getModuleConfig("faction.yml");
        int minLength = factionConfig != null ? factionConfig.getInt("faction.name.min-length", 2) : 2;
        int maxLength = factionConfig != null ? factionConfig.getInt("faction.name.max-length", 16) : 16;
        if (name.length() < minLength || name.length() > maxLength) {
            player.sendMessage(messages.get("faction.name-invalid-length", "min", minLength, "max", maxLength));
            return;
        }

        UUID uuid = player.getUniqueId();
        runAsync(player, () -> {
            if (factionRepository.findMember(uuid).isPresent()) {
                return () -> player.sendMessage(messages.get("faction.already-in-faction"));
            }
            if (factionRepository.findByName(name).isPresent()) {
                return () -> player.sendMessage(messages.get("faction.name-taken", "name", name));
            }

            String id = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            FactionData faction = new FactionData(id, name, name, uuid, now);
            factionRepository.createFactionWithLeader(faction, now);
            factionManager.cacheFactionId(uuid, id);

            return () -> player.sendMessage(messages.get("faction.create-success", "name", name));
        });
    }

    private void handleDisband(Player player) {
        UUID uuid = player.getUniqueId();
        runAsync(player, () -> {
            Optional<FactionMember> member = factionRepository.findMember(uuid);
            if (member.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.not-in-faction"));
            }
            if (member.get().getRole() != FactionRole.LEADER) {
                return () -> player.sendMessage(messages.get("faction.not-leader"));
            }

            if (!factionManager.confirmDisband(uuid)) {
                return () -> player.sendMessage(messages.get("faction.disband-confirm"));
            }

            String factionId = member.get().getFactionId();
            Optional<FactionData> faction = factionRepository.findById(factionId);
            factionRepository.deleteFactionCascade(factionId);
            String factionName = faction.map(FactionData::getName).orElse(factionId);

            return () -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (factionManager.getCachedFactionId(online.getUniqueId()).map(factionId::equals).orElse(false)) {
                        factionManager.cacheFactionId(online.getUniqueId(), null);
                        factionManager.clearFactionChat(online.getUniqueId());
                    }
                }
                player.sendMessage(messages.get("faction.disband-success", "name", factionName));
            };
        });
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "usage.faction-nickname-target", "sub", "초대");
            return;
        }
        String targetName = args[1];
        UUID uuid = player.getUniqueId();

        runAsync(player, () -> {
            Optional<FactionMember> member = factionRepository.findMember(uuid);
            if (member.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.not-in-faction"));
            }
            if (member.get().getRole() == FactionRole.MEMBER) {
                return () -> player.sendMessage(messages.get("faction.not-officer-or-leader"));
            }

            Optional<UUID> targetUuid = playerRepository.findUuidByNickname(targetName);
            if (targetUuid.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.target-not-found", "player", targetName));
            }
            if (targetUuid.get().equals(uuid)) {
                return () -> player.sendMessage(messages.get("faction.target-self-blocked"));
            }
            if (factionRepository.findMember(targetUuid.get()).isPresent()) {
                return () -> player.sendMessage(messages.get("faction.invite-target-already-in-faction", "player", targetName));
            }

            Optional<FactionData> faction = factionRepository.findById(member.get().getFactionId());
            if (faction.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.not-in-faction"));
            }
            FileConfiguration factionConfig = plugin.getConfigManager().getModuleConfig("faction.yml");
            int max = factionConfig != null ? factionConfig.getInt("faction.max-members", 10) : 10;
            int current = factionRepository.countMembers(faction.get().getId());
            if (current >= max) {
                return () -> player.sendMessage(messages.get("faction.faction-full", "max", max));
            }

            factionManager.addInvite(targetUuid.get(),
                    new FactionInvite(faction.get().getId(), faction.get().getName(), System.currentTimeMillis()));

            return () -> {
                player.sendMessage(messages.get("faction.invite-sent", "player", targetName));
                Player targetPlayer = Bukkit.getPlayer(targetUuid.get());
                if (targetPlayer != null) {
                    targetPlayer.sendMessage(messages.get("faction.invite-received", "faction", faction.get().getName()));
                }
            };
        });
    }

    private void handleAccept(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "usage.faction-name-target", "sub", "수락");
            return;
        }
        String factionName = args[1];
        UUID uuid = player.getUniqueId();

        runAsync(player, () -> {
            if (factionRepository.findMember(uuid).isPresent()) {
                return () -> player.sendMessage(messages.get("faction.already-in-faction"));
            }

            FactionInvite invite = factionManager.takeInvite(uuid, factionName);
            if (invite == null) {
                return () -> player.sendMessage(messages.get("faction.invite-not-found", "faction", factionName));
            }

            Optional<FactionData> faction = factionRepository.findById(invite.factionId());
            if (faction.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.invite-not-found", "faction", factionName));
            }

            FileConfiguration factionConfig = plugin.getConfigManager().getModuleConfig("faction.yml");
            int max = factionConfig != null ? factionConfig.getInt("faction.max-members", 10) : 10;
            int current = factionRepository.countMembers(faction.get().getId());
            if (current >= max) {
                return () -> player.sendMessage(messages.get("faction.faction-full", "max", max));
            }

            factionRepository.addMember(faction.get().getId(), uuid, FactionRole.MEMBER, System.currentTimeMillis());
            factionManager.cacheFactionId(uuid, faction.get().getId());

            return () -> player.sendMessage(messages.get("faction.accept-success", "faction", faction.get().getName()));
        });
    }

    private void handleDeny(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "usage.faction-name-target", "sub", "거절");
            return;
        }
        String factionName = args[1];
        FactionInvite invite = factionManager.takeInvite(player.getUniqueId(), factionName);
        if (invite == null) {
            player.sendMessage(messages.get("faction.invite-not-found", "faction", factionName));
        } else {
            player.sendMessage(messages.get("faction.deny-success", "faction", invite.factionName()));
        }
    }

    private void handleLeave(Player player) {
        UUID uuid = player.getUniqueId();
        runAsync(player, () -> {
            Optional<FactionMember> member = factionRepository.findMember(uuid);
            if (member.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.not-in-faction"));
            }
            if (member.get().getRole() == FactionRole.LEADER) {
                return () -> player.sendMessage(messages.get("faction.leave-leader-blocked"));
            }

            Optional<FactionData> faction = factionRepository.findById(member.get().getFactionId());
            factionRepository.removeMember(uuid);
            factionManager.cacheFactionId(uuid, null);
            String factionName = faction.map(FactionData::getName).orElse("");

            return () -> {
                factionManager.clearFactionChat(uuid);
                player.sendMessage(messages.get("faction.leave-success", "faction", factionName));
            };
        });
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "usage.faction-nickname-target", "sub", "추방");
            return;
        }
        String targetName = args[1];
        UUID uuid = player.getUniqueId();

        runAsync(player, () -> {
            Optional<FactionMember> member = factionRepository.findMember(uuid);
            if (member.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.not-in-faction"));
            }
            if (member.get().getRole() == FactionRole.MEMBER) {
                return () -> player.sendMessage(messages.get("faction.not-officer-or-leader"));
            }

            Optional<UUID> targetUuid = playerRepository.findUuidByNickname(targetName);
            if (targetUuid.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.target-not-found", "player", targetName));
            }
            if (targetUuid.get().equals(uuid)) {
                return () -> player.sendMessage(messages.get("faction.kick-self-blocked"));
            }

            Optional<FactionMember> targetMember = factionRepository.findMember(targetUuid.get());
            if (targetMember.isEmpty() || !targetMember.get().getFactionId().equals(member.get().getFactionId())) {
                return () -> player.sendMessage(messages.get("faction.kick-not-member", "player", targetName));
            }
            if (targetMember.get().getRole() == FactionRole.LEADER) {
                return () -> player.sendMessage(messages.get("faction.kick-leader-blocked"));
            }
            if (member.get().getRole() == FactionRole.OFFICER && targetMember.get().getRole() == FactionRole.OFFICER) {
                return () -> player.sendMessage(messages.get("faction.officer-cannot-target-officer"));
            }

            factionRepository.removeMember(targetUuid.get());

            return () -> {
                factionManager.cacheFactionId(targetUuid.get(), null);
                factionManager.clearFactionChat(targetUuid.get());
                player.sendMessage(messages.get("faction.kick-success", "player", targetName));
                Player targetPlayer = Bukkit.getPlayer(targetUuid.get());
                if (targetPlayer != null) {
                    targetPlayer.sendMessage(messages.get("faction.kicked-notice"));
                }
            };
        });
    }

    private void handlePromote(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "usage.faction-nickname-target", "sub", "승급");
            return;
        }
        changeRole(player, args[1], true);
    }

    private void handleDemote(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "usage.faction-nickname-target", "sub", "강등");
            return;
        }
        changeRole(player, args[1], false);
    }

    private void changeRole(Player player, String targetName, boolean promote) {
        UUID uuid = player.getUniqueId();
        runAsync(player, () -> {
            Optional<FactionMember> member = factionRepository.findMember(uuid);
            if (member.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.not-in-faction"));
            }
            if (member.get().getRole() != FactionRole.LEADER) {
                return () -> player.sendMessage(messages.get("faction.not-leader"));
            }

            Optional<UUID> targetUuid = playerRepository.findUuidByNickname(targetName);
            if (targetUuid.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.target-not-found", "player", targetName));
            }
            if (targetUuid.get().equals(uuid)) {
                return () -> player.sendMessage(messages.get("faction.target-self-blocked"));
            }

            Optional<FactionMember> targetMember = factionRepository.findMember(targetUuid.get());
            if (targetMember.isEmpty() || !targetMember.get().getFactionId().equals(member.get().getFactionId())) {
                return () -> player.sendMessage(messages.get("faction.target-not-in-your-faction", "player", targetName));
            }
            if (targetMember.get().getRole() == FactionRole.LEADER) {
                return () -> player.sendMessage(messages.get("faction.target-is-leader"));
            }

            if (promote) {
                if (targetMember.get().getRole() == FactionRole.OFFICER) {
                    return () -> player.sendMessage(messages.get("faction.promote-max", "player", targetName));
                }
                factionRepository.updateMemberRole(targetUuid.get(), FactionRole.OFFICER);
                return () -> player.sendMessage(messages.get("faction.promote-success", "player", targetName, "role", roleDisplay(FactionRole.OFFICER)));
            } else {
                if (targetMember.get().getRole() == FactionRole.MEMBER) {
                    return () -> player.sendMessage(messages.get("faction.demote-min", "player", targetName));
                }
                factionRepository.updateMemberRole(targetUuid.get(), FactionRole.MEMBER);
                return () -> player.sendMessage(messages.get("faction.demote-success", "player", targetName, "role", roleDisplay(FactionRole.MEMBER)));
            }
        });
    }

    private void handleInfo(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        boolean named = args.length >= 2;
        String targetName = named ? args[1] : null;

        runAsync(player, () -> {
            FactionData faction;
            if (named) {
                Optional<FactionData> found = factionRepository.findByName(targetName);
                if (found.isEmpty()) {
                    String finalTargetName = targetName;
                    return () -> player.sendMessage(messages.get("faction.info-not-found", "name", finalTargetName));
                }
                faction = found.get();
            } else {
                Optional<FactionMember> member = factionRepository.findMember(uuid);
                if (member.isEmpty()) {
                    return () -> player.sendMessage(messages.get("faction.not-in-faction"));
                }
                Optional<FactionData> found = factionRepository.findById(member.get().getFactionId());
                if (found.isEmpty()) {
                    return () -> player.sendMessage(messages.get("faction.not-in-faction"));
                }
                faction = found.get();
            }

            List<FactionMember> members = factionRepository.listMembers(faction.getId());
            List<FactionRelationView> relations = factionRepository.listRelations(faction.getId());
            String leaderNickname = playerRepository.find(faction.getLeaderUuid())
                    .map(PlayerData::getNickname)
                    .orElse(faction.getLeaderUuid().toString());

            List<String> memberLines = new ArrayList<>();
            for (FactionMember memberEntry : members) {
                String nickname = playerRepository.find(memberEntry.getPlayerUuid())
                        .map(PlayerData::getNickname)
                        .orElse(memberEntry.getPlayerUuid().toString());
                memberLines.add(messages.get("faction.info-member-line", "player", nickname, "role", roleDisplay(memberEntry.getRole())));
            }

            String allies = relations.stream()
                    .filter(r -> r.type() == FactionRelationType.ALLY)
                    .map(FactionRelationView::relatedFactionName)
                    .collect(Collectors.joining(", "));
            String enemies = relations.stream()
                    .filter(r -> r.type() == FactionRelationType.ENEMY)
                    .map(FactionRelationView::relatedFactionName)
                    .collect(Collectors.joining(", "));

            FileConfiguration factionConfig = plugin.getConfigManager().getModuleConfig("faction.yml");
            int max = factionConfig != null ? factionConfig.getInt("faction.max-members", 10) : 10;
            String createdDate = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(faction.getCreatedAt()));
            FactionData finalFaction = faction;

            return () -> {
                player.sendMessage(messages.get("faction.info-header", "name", finalFaction.getName()));
                player.sendMessage(messages.get("faction.info-leader", "leader", leaderNickname));
                player.sendMessage(messages.get("faction.info-members", "count", members.size(), "max", max));
                player.sendMessage(messages.get("faction.info-created", "date", createdDate));
                player.sendMessage(messages.get("faction.info-relations",
                        "allies", allies.isEmpty() ? messages.get("faction.none") : allies,
                        "enemies", enemies.isEmpty() ? messages.get("faction.none") : enemies));
                for (String line : memberLines) {
                    player.sendMessage(line);
                }
            };
        });
    }

    private void handleList(Player player) {
        runAsync(player, () -> {
            List<FactionData> all = factionRepository.listAll();
            List<String> lines = new ArrayList<>();
            for (FactionData faction : all) {
                int count = factionRepository.countMembers(faction.getId());
                lines.add(messages.get("faction.list-line", "name", faction.getName(), "count", count));
            }

            return () -> {
                if (lines.isEmpty()) {
                    player.sendMessage(messages.get("faction.list-empty"));
                    return;
                }
                player.sendMessage(messages.get("faction.list-header", "count", lines.size()));
                for (String line : lines) {
                    player.sendMessage(line);
                }
            };
        });
    }

    private void handleChatToggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (!factionManager.isFactionChatEnabled(uuid) && factionManager.getCachedFactionId(uuid).isEmpty()) {
            player.sendMessage(messages.get("faction.not-in-faction"));
            return;
        }
        boolean now = factionManager.toggleFactionChat(uuid);
        player.sendMessage(messages.get(now ? "faction.chat-on" : "faction.chat-off"));
    }

    private void handleRelation(Player player, String[] args, FactionRelationType type) {
        String subLabel = type == FactionRelationType.ALLY ? "동맹" : "적대";
        if (args.length < 2) {
            sendUsage(player, "usage.faction-name-target", "sub", subLabel);
            return;
        }
        String targetName = args[1];
        UUID uuid = player.getUniqueId();

        runAsync(player, () -> {
            Optional<FactionMember> member = factionRepository.findMember(uuid);
            if (member.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.not-in-faction"));
            }
            if (member.get().getRole() == FactionRole.MEMBER) {
                return () -> player.sendMessage(messages.get("faction.not-officer-or-leader"));
            }

            Optional<FactionData> myFaction = factionRepository.findById(member.get().getFactionId());
            if (myFaction.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.not-in-faction"));
            }
            Optional<FactionData> target = factionRepository.findByName(targetName);
            if (target.isEmpty()) {
                return () -> player.sendMessage(messages.get("faction.relation-target-not-found", "name", targetName));
            }
            if (target.get().getId().equals(myFaction.get().getId())) {
                return () -> player.sendMessage(messages.get("faction.relation-target-self"));
            }

            Optional<FactionRelationType> existing = factionRepository.findRelation(myFaction.get().getId(), target.get().getId());
            if (existing.isPresent() && existing.get() == type) {
                String key = type == FactionRelationType.ALLY ? "faction.already-ally" : "faction.already-enemy";
                return () -> player.sendMessage(messages.get(key, "name", target.get().getName()));
            }

            factionRepository.setRelationMutual(myFaction.get().getId(), target.get().getId(), type);
            String successKey = type == FactionRelationType.ALLY ? "faction.ally-success" : "faction.enemy-success";

            return () -> player.sendMessage(messages.get(successKey, "name", target.get().getName()));
        });
    }

    private void handleRank(Player player) {
        runAsync(player, () -> {
            List<FactionData> all = factionRepository.listAll();
            List<String> lines = new ArrayList<>();

            record Entry(String name, int count) {
            }
            List<Entry> entries = new ArrayList<>();
            for (FactionData faction : all) {
                entries.add(new Entry(faction.getName(), factionRepository.countMembers(faction.getId())));
            }
            entries.sort(Comparator.comparingInt(Entry::count).reversed());

            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                lines.add(messages.get("faction.rank-line", "rank", i + 1, "name", entry.name(), "count", entry.count()));
            }

            return () -> {
                if (lines.isEmpty()) {
                    player.sendMessage(messages.get("faction.rank-empty"));
                    return;
                }
                player.sendMessage(messages.get("faction.rank-header"));
                for (String line : lines) {
                    player.sendMessage(line);
                }
            };
        });
    }

    private String roleDisplay(FactionRole role) {
        return switch (role) {
            case LEADER -> "세력장";
            case OFFICER -> "관리자";
            case MEMBER -> "일반";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partialMatches(args[0], SUB_COMMANDS);
        }
        if (args.length == 2) {
            return switch (args[0]) {
                case "초대", "추방", "승급", "강등" -> {
                    List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    yield partialMatches(args[1], names);
                }
                case "수락", "거절" -> {
                    if (sender instanceof Player player) {
                        yield partialMatches(args[1], factionManager.listInviteFactionNames(player.getUniqueId()));
                    }
                    yield List.of();
                }
                default -> List.of();
            };
        }
        return List.of();
    }
}
