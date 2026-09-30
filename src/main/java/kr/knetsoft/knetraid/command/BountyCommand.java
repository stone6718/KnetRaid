package kr.knetsoft.knetraid.command;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.database.BountyRepository;
import kr.knetsoft.knetraid.database.PlayerRepository;
import kr.knetsoft.knetraid.economy.EconomyFormat;
import kr.knetsoft.knetraid.economy.EconomyProvider;
import kr.knetsoft.knetraid.economy.EconomyResult;
import kr.knetsoft.knetraid.model.BountyData;
import kr.knetsoft.knetraid.model.BountyRankEntry;
import kr.knetsoft.knetraid.model.CurrencyReason;
import kr.knetsoft.knetraid.model.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * /현상금 - 현상금 시스템 명령어. 6단계에서 실제 DB·경제 연동 로직으로 구현되었다.
 */
public class BountyCommand extends BaseCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList("등록", "순위", "확인", "취소");
    private static final int RANK_LIMIT = 10;

    private final BountyRepository bountyRepository;
    private final PlayerRepository playerRepository;

    public BountyCommand(KnetRaid plugin) {
        super(plugin);
        this.bountyRepository = plugin.getBountyRepository();
        this.playerRepository = plugin.getPlayerRepository();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!requirePlayer(sender)) {
            return true;
        }
        if (!requireModule(sender, "bounty")) {
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            handleSelfCheck(player);
            return true;
        }

        switch (args[0]) {
            case "등록" -> handleRegister(player, args);
            case "순위" -> handleRank(player);
            case "확인" -> handleCheck(player, args);
            case "취소" -> handleCancel(player, args);
            default -> sendUnknownSubCommand(sender, "/약탈도움말");
        }
        return true;
    }

    private void handleSelfCheck(Player player) {
        checkTarget(player, player.getUniqueId(), player.getName(), true);
    }

    private void handleRegister(Player player, String[] args) {
        if (args.length < 3) {
            sendUsage(player, "usage.bounty-register");
            return;
        }
        String targetName = args[1];

        EconomyProvider economy = plugin.getEconomyProvider();
        if (!economy.isAvailable()) {
            player.sendMessage(messages.get("bounty.economy-unavailable"));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException exception) {
            player.sendMessage(messages.get("general.invalid-number", "input", args[2]));
            return;
        }

        FileConfiguration bountyConfig = plugin.getConfigManager().getModuleConfig("bounty.yml");
        double minAmount = bountyConfig != null ? bountyConfig.getDouble("bounty.min-amount", 100) : 100;
        double maxAmount = bountyConfig != null ? bountyConfig.getDouble("bounty.max-amount", 1000000) : 1000000;
        boolean selfBountyAllowed = bountyConfig != null && bountyConfig.getBoolean("bounty.self-bounty-allowed", false);
        int expireDays = bountyConfig != null ? bountyConfig.getInt("bounty.expire-days", 7) : 7;

        if (amount < minAmount) {
            player.sendMessage(messages.get("bounty.amount-too-low", "min", EconomyFormat.format(minAmount, getDecimalPlaces())));
            return;
        }
        if (amount > maxAmount) {
            player.sendMessage(messages.get("bounty.amount-too-high", "max", EconomyFormat.format(maxAmount, getDecimalPlaces())));
            return;
        }

        UUID senderUuid = player.getUniqueId();

        runAsync(player, () -> {
            Optional<UUID> targetUuid = playerRepository.findUuidByNickname(targetName);
            if (targetUuid.isEmpty()) {
                return () -> player.sendMessage(messages.get("bounty.target-not-found", "player", targetName));
            }
            if (!selfBountyAllowed && targetUuid.get().equals(senderUuid)) {
                return () -> player.sendMessage(messages.get("bounty.self-bounty-blocked"));
            }
            if (!economy.has(senderUuid, amount)) {
                return () -> player.sendMessage(messages.get("bounty.insufficient-funds",
                        "amount", EconomyFormat.format(amount, getDecimalPlaces())));
            }

            EconomyResult withdrawResult = economy.withdraw(senderUuid, amount);
            if (!withdrawResult.success()) {
                return () -> player.sendMessage(messages.get("bounty.insufficient-funds",
                        "amount", EconomyFormat.format(amount, getDecimalPlaces())));
            }

            long now = System.currentTimeMillis();
            Long expiresAt = expireDays > 0 ? now + (expireDays * 86_400_000L) : null;
            bountyRepository.create(targetUuid.get(), senderUuid, amount, now, expiresAt);
            plugin.getCurrencyTransactionRepository().log(senderUuid, -amount, CurrencyReason.BOUNTY_REGISTER);

            return () -> player.sendMessage(messages.get("bounty.register-success",
                    "player", targetName, "amount", EconomyFormat.format(amount, getDecimalPlaces())));
        });
    }

    private void handleCheck(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "usage.bounty-target", "sub", "확인");
            return;
        }
        String targetName = args[1];
        runAsync(player, () -> {
            Optional<UUID> targetUuid = playerRepository.findUuidByNickname(targetName);
            if (targetUuid.isEmpty()) {
                return () -> player.sendMessage(messages.get("bounty.target-not-found", "player", targetName));
            }
            return () -> checkTarget(player, targetUuid.get(), targetName, false);
        });
    }

    private void checkTarget(Player player, UUID targetUuid, String targetName, boolean self) {
        runAsync(player, () -> {
            double total = bountyRepository.sumActiveByTarget(targetUuid);
            int contributors = total > 0 ? bountyRepository.countActiveContributors(targetUuid) : 0;
            return () -> {
                if (total <= 0) {
                    player.sendMessage(messages.get(self ? "bounty.none-on-self" : "bounty.check-none", "player", targetName));
                    return;
                }
                String amountText = EconomyFormat.format(total, getDecimalPlaces());
                player.sendMessage(messages.get("bounty.check-header", "player", targetName));
                player.sendMessage(messages.get("bounty.check-amount", "amount", amountText));
                player.sendMessage(messages.get("bounty.check-contributors", "count", contributors));
            };
        });
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "usage.bounty-target", "sub", "취소");
            return;
        }
        String targetName = args[1];
        UUID senderUuid = player.getUniqueId();
        int decimalPlaces = getDecimalPlaces();

        runAsync(player, () -> {
            Optional<UUID> targetUuid = playerRepository.findUuidByNickname(targetName);
            if (targetUuid.isEmpty()) {
                return () -> player.sendMessage(messages.get("bounty.target-not-found", "player", targetName));
            }

            List<BountyData> mine = bountyRepository.listActiveByIssuerAndTarget(senderUuid, targetUuid.get());
            if (mine.isEmpty()) {
                return () -> player.sendMessage(messages.get("bounty.cancel-none", "player", targetName));
            }

            List<String> ids = new ArrayList<>();
            double total = 0;
            for (BountyData bounty : mine) {
                ids.add(bounty.id());
                total += bounty.amount();
            }
            bountyRepository.markCancelled(ids);

            double refundAmount = total;
            EconomyResult refundResult = plugin.getEconomyProvider().deposit(senderUuid, refundAmount);
            if (refundResult.success()) {
                plugin.getCurrencyTransactionRepository().log(senderUuid, refundAmount, CurrencyReason.BOUNTY_REFUND);
            } else {
                plugin.getLogger().severe("현상금 취소 환불에 실패했습니다 (player: " + senderUuid + ", amount: " + refundAmount + "): "
                        + refundResult.errorMessage());
            }

            return () -> player.sendMessage(messages.get("bounty.cancel-success",
                    "player", targetName, "amount", EconomyFormat.format(refundAmount, decimalPlaces)));
        });
    }

    private void handleRank(Player player) {
        int decimalPlaces = getDecimalPlaces();
        runAsync(player, () -> {
            List<BountyRankEntry> entries = bountyRepository.topTargetsByActiveTotal(RANK_LIMIT);
            List<String> lines = new ArrayList<>();
            int rank = 1;
            for (BountyRankEntry entry : entries) {
                String nickname = playerRepository.find(entry.targetUuid())
                        .map(PlayerData::getNickname)
                        .orElse(entry.targetUuid().toString());
                lines.add(messages.get("bounty.rank-line", "rank", rank,
                        "player", nickname, "amount", EconomyFormat.format(entry.total(), decimalPlaces),
                        "count", entry.contributorCount()));
                rank++;
            }
            return () -> {
                if (lines.isEmpty()) {
                    player.sendMessage(messages.get("bounty.rank-empty"));
                    return;
                }
                player.sendMessage(messages.get("bounty.rank-header"));
                for (String line : lines) {
                    player.sendMessage(line);
                }
            };
        });
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
        return List.of();
    }
}
