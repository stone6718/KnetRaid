package kr.knetsoft.knetraid.command;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.database.SeasonRepository;
import kr.knetsoft.knetraid.economy.EconomyFormat;
import kr.knetsoft.knetraid.model.FactionSeasonStatEntry;
import kr.knetsoft.knetraid.model.PlayerData;
import kr.knetsoft.knetraid.model.SeasonData;
import kr.knetsoft.knetraid.model.SeasonStatEntry;
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
 * /시즌 - 시즌 및 랭킹 정보 명령어. 8단계에서 실제 DB 기반 로직으로 구현되었다.
 */
public class SeasonCommand extends BaseCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList("정보", "순위");
    private static final List<String> RANK_CATEGORIES = Arrays.asList("킬", "약탈", "현상금", "세력");
    private static final int RANK_LIMIT = 10;

    private final SeasonRepository seasonRepository;

    public SeasonCommand(KnetRaid plugin) {
        super(plugin);
        this.seasonRepository = plugin.getSeasonRepository();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!requirePlayer(sender)) {
            return true;
        }
        if (!requireModule(sender, "season")) {
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0 || !(args[0].equals("정보") || args[0].equals("순위"))) {
            sendUnknownSubCommand(sender, "/시즌 정보");
            return true;
        }

        if (args[0].equals("정보")) {
            handleInfo(player);
        } else {
            handleRank(player, args);
        }
        return true;
    }

    private void handleInfo(Player player) {
        UUID uuid = player.getUniqueId();
        runAsync(player, () -> {
            Optional<SeasonData> active = seasonRepository.findActiveSeason();
            if (active.isEmpty()) {
                return () -> player.sendMessage(messages.get("season.no-active"));
            }
            SeasonData season = active.get();
            Optional<SeasonStatEntry> stat = seasonRepository.findStat(season.id(), uuid);

            return () -> {
                String startedDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date(season.startedAt()));
                long elapsedDays = (System.currentTimeMillis() - season.startedAt()) / 86_400_000L;
                player.sendMessage(messages.get("season.info-header", "name", season.name()));
                player.sendMessage(messages.get("season.info-started", "date", startedDate, "days", elapsedDays));
                if (stat.isPresent()) {
                    SeasonStatEntry entry = stat.get();
                    player.sendMessage(messages.get("season.info-my-stats",
                            "kills", entry.kills(), "deaths", entry.deaths(), "raids", entry.raids(),
                            "bounty", EconomyFormat.format(entry.bountyEarned(), getDecimalPlaces())));
                } else {
                    player.sendMessage(messages.get("season.info-no-stats"));
                }
            };
        });
    }

    private void handleRank(Player player, String[] args) {
        String category = args.length >= 2 ? args[1] : "킬";
        if (!RANK_CATEGORIES.contains(category)) {
            sendUnknownSubCommand(player, "/시즌 순위");
            return;
        }
        int decimalPlaces = getDecimalPlaces();

        runAsync(player, () -> {
            Optional<SeasonData> active = seasonRepository.findActiveSeason();
            if (active.isEmpty()) {
                return () -> player.sendMessage(messages.get("season.no-active"));
            }
            String seasonId = active.get().id();

            if (category.equals("세력")) {
                List<FactionSeasonStatEntry> entries = seasonRepository.topFactionsByKills(seasonId, RANK_LIMIT);
                List<String> lines = new ArrayList<>();
                int rank = 1;
                for (FactionSeasonStatEntry entry : entries) {
                    lines.add(messages.get("season.rank-faction-line", "rank", rank, "name", entry.factionName(), "kills", entry.totalKills()));
                    rank++;
                }
                return () -> sendRankLines(player, "세력 처치", lines);
            }

            List<SeasonStatEntry> entries = switch (category) {
                case "약탈" -> seasonRepository.topByRaids(seasonId, RANK_LIMIT);
                case "현상금" -> seasonRepository.topByBountyEarned(seasonId, RANK_LIMIT);
                default -> seasonRepository.topByKills(seasonId, RANK_LIMIT);
            };

            List<String> lines = new ArrayList<>();
            int rank = 1;
            for (SeasonStatEntry entry : entries) {
                String nickname = plugin.getPlayerRepository().find(entry.playerUuid())
                        .map(PlayerData::getNickname)
                        .orElse(entry.playerUuid().toString());
                String valueText = category.equals("현상금")
                        ? EconomyFormat.format(entry.bountyEarned(), decimalPlaces)
                        : String.valueOf(category.equals("약탈") ? entry.raids() : entry.kills());
                lines.add(messages.get("season.rank-line", "rank", rank, "player", nickname, "value", valueText));
                rank++;
            }
            return () -> sendRankLines(player, category, lines);
        });
    }

    private void sendRankLines(Player player, String categoryLabel, List<String> lines) {
        if (lines.isEmpty()) {
            player.sendMessage(messages.get("season.rank-empty"));
            return;
        }
        player.sendMessage(messages.get("season.rank-header", "category", categoryLabel));
        for (String line : lines) {
            player.sendMessage(line);
        }
    }

    private int getDecimalPlaces() {
        FileConfiguration economyConfig = plugin.getConfigManager().getModuleConfig("economy.yml");
        return economyConfig != null ? economyConfig.getInt("economy.decimal-places", 2) : 2;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partialMatches(args[0], SUB_COMMANDS);
        }
        if (args.length == 2 && "순위".equals(args[0])) {
            return partialMatches(args[1], RANK_CATEGORIES);
        }
        return List.of();
    }
}
