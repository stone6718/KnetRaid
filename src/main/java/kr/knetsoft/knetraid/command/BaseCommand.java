package kr.knetsoft.knetraid.command;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 모든 KnetRaid 명령어가 공유하는 권한 검사, 메시지 전송,
 * 탭 완성 유틸리티를 제공하는 기반 클래스.
 */
public abstract class BaseCommand implements CommandExecutor, TabCompleter {

    protected final KnetRaid plugin;
    protected final MessageManager messages;

    protected BaseCommand(KnetRaid plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
    }

    protected boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.get("general.player-only"));
            return false;
        }
        return true;
    }

    protected boolean requirePermission(CommandSender sender, String node) {
        if (!sender.hasPermission(node)) {
            sender.sendMessage(messages.get("general.no-permission"));
            return false;
        }
        return true;
    }

    protected boolean requireModule(CommandSender sender, String moduleKey) {
        if (!plugin.getConfigManager().isModuleEnabled(moduleKey)) {
            sender.sendMessage(messages.get("general.module-disabled"));
            return false;
        }
        return true;
    }

    protected void sendFeaturePending(CommandSender sender) {
        sender.sendMessage(messages.get("general.feature-pending"));
    }

    protected void sendUsage(CommandSender sender, String usageKey, Object... replacements) {
        sender.sendMessage(messages.get(usageKey, replacements));
    }

    protected void sendUnknownSubCommand(CommandSender sender, String commandUsage) {
        sender.sendMessage(messages.get("general.unknown-command", "command", commandUsage));
    }

    /**
     * DB 접근 등 무거운 작업을 비동기 스레드에서 실행한다.
     * SQLException은 잡아 콘솔에 기록하고 사용자에게는 일반 오류 메시지를 보낸다.
     * Bukkit API 호출이 필요한 후속 처리는 syncCallback으로 넘겨 메인 스레드에서 실행한다.
     */
    protected void runAsync(CommandSender sender, DbWork work) {
        plugin.getAsyncTaskTracker().begin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Runnable syncCallback = work.run();
                if (syncCallback != null) {
                    Bukkit.getScheduler().runTask(plugin, syncCallback);
                }
            } catch (java.sql.SQLException exception) {
                plugin.getLogger().severe("데이터베이스 작업 중 오류가 발생했습니다: " + exception.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(messages.get("general.db-error")));
            } finally {
                plugin.getAsyncTaskTracker().end();
            }
        });
    }

    @FunctionalInterface
    protected interface DbWork {
        Runnable run() throws java.sql.SQLException;
    }

    protected List<String> partialMatches(String token, List<String> options) {
        List<String> result = new ArrayList<>();
        StringUtil.copyPartialMatches(token, options, result);
        Collections.sort(result);
        return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
