package kr.knetsoft.knetraid.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import kr.knetsoft.knetraid.KnetRaid;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;
import java.util.UUID;

/**
 * /세력 채팅 모드가 켜진 플레이어의 채팅을 같은 세력원에게만 전달한다.
 * FactionManager의 온라인 세력 캐시만 참조하므로 DB 접근이 없다.
 */
public class FactionChatListener implements Listener {

    private final KnetRaid plugin;

    public FactionChatListener(KnetRaid plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.getFactionManager().isFactionChatEnabled(uuid)) {
            return;
        }

        Optional<String> factionId = plugin.getFactionManager().getCachedFactionId(uuid);
        if (factionId.isEmpty()) {
            // 세력 없이 채팅 모드만 켜진 예외 상황은 전체 채팅으로 흘려보낸다.
            return;
        }

        java.util.Set<Audience> viewers = event.viewers();
        viewers.clear();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (factionId.equals(plugin.getFactionManager().getCachedFactionId(online.getUniqueId()))) {
                viewers.add(online);
            }
        }
        viewers.add(Bukkit.getConsoleSender());

        event.renderer((source, sourceDisplayName, message, viewer) -> Component.text()
                .append(Component.text("[세력] ", NamedTextColor.DARK_GRAY))
                .append(sourceDisplayName)
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(message)
                .build());
    }
}
