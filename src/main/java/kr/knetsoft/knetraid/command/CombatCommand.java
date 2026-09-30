package kr.knetsoft.knetraid.command;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.model.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * /전투 - 전투 이탈 방지 시스템 명령어. 5단계에서 실제 전투 상태를 조회하도록 구현되었다.
 */
public class CombatCommand extends BaseCommand {

    public CombatCommand(KnetRaid plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!requirePlayer(sender)) {
            return true;
        }
        if (!requireModule(sender, "combat")) {
            return true;
        }
        if (args.length == 0 || !args[0].equals("상태")) {
            sendUnknownSubCommand(sender, "/전투 상태");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (plugin.getCombatManager().isInCombat(uuid)) {
            long remaining = plugin.getCombatManager().getRemainingSeconds(uuid);
            String opponentName = plugin.getCombatManager().getOpponent(uuid)
                    .flatMap(opponent -> plugin.getPlayerDataCache().get(opponent))
                    .map(PlayerData::getNickname)
                    .orElse("알 수 없음");
            player.sendMessage(messages.get("combat.status-in-combat", "seconds", remaining, "opponent", opponentName));
        } else {
            player.sendMessage(messages.get("combat.status-not-in-combat"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partialMatches(args[0], List.of("상태"));
        }
        return List.of();
    }
}
