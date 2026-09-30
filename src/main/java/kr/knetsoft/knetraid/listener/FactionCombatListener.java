package kr.knetsoft.knetraid.listener;

import kr.knetsoft.knetraid.KnetRaid;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Optional;

/**
 * faction.yml의 friendly-fire 설정이 꺼져 있으면 같은 세력원 간의 PvP 피해를 막는다.
 * FactionManager의 온라인 세력 캐시만 참조하므로 매 타격마다 DB에 접근하지 않는다.
 */
public class FactionCombatListener implements Listener {

    private final KnetRaid plugin;

    public FactionCombatListener(KnetRaid plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (!plugin.getConfigManager().isModuleEnabled("faction")) {
            return;
        }

        FileConfiguration factionConfig = plugin.getConfigManager().getModuleConfig("faction.yml");
        boolean friendlyFire = factionConfig != null && factionConfig.getBoolean("faction.friendly-fire", false);
        if (friendlyFire) {
            return;
        }

        Optional<String> attackerFaction = plugin.getFactionManager().getCachedFactionId(attacker.getUniqueId());
        Optional<String> victimFaction = plugin.getFactionManager().getCachedFactionId(victim.getUniqueId());
        if (attackerFaction.isPresent() && attackerFaction.equals(victimFaction)) {
            event.setCancelled(true);
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
