package kr.knetsoft.knetraid.command;

import kr.knetsoft.knetraid.KnetRaid;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * /약탈도움말 - 사용 가능한 모든 명령어를 안내한다.
 */
public class HelpCommand extends BaseCommand {

    private static final String[][] ENTRIES = {
            {"/세력 <생성|해체|초대|수락|거절|탈퇴|추방|승급|강등|정보|목록|채팅|동맹|적대|순위>", "세력 시스템 명령어"},
            {"/현상금 [등록|순위|확인|취소] [닉네임] [금액]", "현상금 시스템 명령어"},
            {"/야생 [월드]", "무작위 위치로 이동합니다"},
            {"/약탈 <정보|기지>", "약탈 시스템 정보를 확인합니다"},
            {"/전투 상태", "현재 전투 상태를 확인합니다"},
            {"/시즌 <정보|순위>", "현재 시즌 정보를 확인합니다"},
            {"/약탈관리 도움말", "관리자 전용 명령어 목록을 확인합니다"}
    };

    public HelpCommand(KnetRaid plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(messages.get("help.header"));
        for (String[] entry : ENTRIES) {
            sender.sendMessage(messages.get("help.line", "command", entry[0], "description", entry[1]));
        }
        sender.sendMessage(messages.get("help.footer"));
        return true;
    }
}
