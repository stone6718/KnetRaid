package kr.knetsoft.knetraid.listener;

import kr.knetsoft.knetraid.KnetRaid;
import kr.knetsoft.knetraid.core.Permissions;
import kr.knetsoft.knetraid.model.CombatEventType;
import kr.knetsoft.knetraid.model.CombatLogEntry;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 전투 이탈 방지 시스템. PvP 피해로 전투 상태를 부여하고,
 * 전투 중 텔레포트·명령어를 제한하며, 전투 중 로그아웃 시 페널티를 적용한다.
 */
public class CombatListener implements Listener {

    private static final Set<String> ALWAYS_ALLOWED_COMMAND_PREFIXES = Set.of("/전투", "/약탈도움말");

    private final KnetRaid plugin;

    public CombatListener(KnetRaid plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigManager().isModuleEnabled("combat")) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        FileConfiguration combatConfig = plugin.getConfigManager().getModuleConfig("combat.yml");
        if (combatConfig == null || !combatConfig.getBoolean("combat.enabled", true)) {
            return;
        }
        long durationMillis = combatConfig.getLong("combat.duration-seconds", 15) * 1000L;

        boolean attackerEntered = plugin.getCombatManager().tagCombat(attacker.getUniqueId(), victim.getUniqueId(), durationMillis);
        boolean victimEntered = plugin.getCombatManager().tagCombat(victim.getUniqueId(), attacker.getUniqueId(), durationMillis);

        logCombatEnter(attacker.getUniqueId(), victim.getUniqueId(), attackerEntered);
        logCombatEnter(victim.getUniqueId(), attacker.getUniqueId(), victimEntered);
    }

    private void logCombatEnter(UUID player, UUID opponent, boolean isNewEntry) {
        if (!isNewEntry) {
            return;
        }
        plugin.getAsyncTaskTracker().begin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getCombatLogRepository().insert(new CombatLogEntry(
                        UUID.randomUUID().toString(), player, opponent, CombatEventType.ENTER_COMBAT, System.currentTimeMillis()));
            } catch (java.sql.SQLException exception) {
                plugin.getLogger().severe("전투 기록을 저장하는 중 오류가 발생했습니다: " + exception.getMessage());
            } finally {
                plugin.getAsyncTaskTracker().end();
            }
        });
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

    @EventHandler(priority = EventPriority.LOW)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfigManager().isModuleEnabled("combat")) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(Permissions.ADMIN)) {
            return;
        }
        FileConfiguration combatConfig = plugin.getConfigManager().getModuleConfig("combat.yml");
        if (combatConfig == null || !combatConfig.getBoolean("combat.prevent-teleport", true)) {
            return;
        }
        if (plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().get("combat.teleport-blocked"));
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String lower = event.getMessage().toLowerCase(Locale.ROOT).trim();
        // 실제로 명령어를 실행할 권한이 있는 경우에만 종료 신호로 인정한다.
        // 그렇지 않으면 권한 없는 플레이어가 채팅으로 "/stop"만 입력해도
        // 서버 전체의 전투 이탈 페널티가 영구히 무력화되는 취약점이 생긴다.
        if (lower.equals("/stop") && event.getPlayer().isOp()) {
            plugin.markServerStopping();
        }

        if (!plugin.getConfigManager().isModuleEnabled("combat")) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(Permissions.ADMIN)) {
            return;
        }
        FileConfiguration combatConfig = plugin.getConfigManager().getModuleConfig("combat.yml");
        if (combatConfig == null || !combatConfig.getBoolean("combat.prevent-commands", true)) {
            return;
        }
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            return;
        }
        for (String allowedPrefix : ALWAYS_ALLOWED_COMMAND_PREFIXES) {
            if (lower.startsWith(allowedPrefix)) {
                return;
            }
        }

        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().get("combat.command-blocked"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand().trim().toLowerCase(Locale.ROOT);
        if (command.equals("stop") || command.startsWith("restart")) {
            plugin.markServerStopping();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        boolean wasInCombat = plugin.getCombatManager().isInCombat(uuid);
        UUID opponent = plugin.getCombatManager().getOpponent(uuid).orElse(null);
        plugin.getCombatManager().clear(uuid);

        if (!wasInCombat || plugin.isServerStopping()) {
            return;
        }
        if (!plugin.getConfigManager().isModuleEnabled("combat")) {
            return;
        }
        FileConfiguration combatConfig = plugin.getConfigManager().getModuleConfig("combat.yml");
        if (combatConfig == null || !combatConfig.getBoolean("combat.logout-penalty", true)) {
            return;
        }

        applyLogoutPenalty(player, opponent);
    }

    /**
     * 전투 중 로그아웃 페널티: 자연 사망 이벤트에 의존하지 않고
     * 직접 아이템을 떨어뜨린 뒤 인벤토리를 비우고 체력을 0으로 만든다.
     * 인벤토리를 먼저 비우므로 사망 이벤트가 뒤이어 처리되더라도 아이템이 중복 드롭되지 않는다.
     */
    private void applyLogoutPenalty(Player player, UUID opponent) {
        if (player.getHealth() <= 0) {
            return;
        }
        Location location = player.getLocation();
        var inventory = player.getInventory();

        for (ItemStack item : inventory.getContents()) {
            dropIfPresent(location, item);
        }
        for (ItemStack item : inventory.getArmorContents()) {
            dropIfPresent(location, item);
        }
        dropIfPresent(location, inventory.getItemInOffHand());

        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);

        player.setHealth(0.0);

        String broadcastText = plugin.getMessageManager().get("combat.logout-penalty-broadcast", "player", player.getName());
        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(broadcastText));

        plugin.getAsyncTaskTracker().begin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getCombatLogRepository().insert(new CombatLogEntry(
                        UUID.randomUUID().toString(), player.getUniqueId(), opponent,
                        CombatEventType.LOGOUT_PENALTY, System.currentTimeMillis()));
            } catch (java.sql.SQLException exception) {
                plugin.getLogger().severe("전투 기록을 저장하는 중 오류가 발생했습니다: " + exception.getMessage());
            } finally {
                plugin.getAsyncTaskTracker().end();
            }
        });
    }

    private void dropIfPresent(Location location, ItemStack item) {
        if (item != null && item.getType() != org.bukkit.Material.AIR && location.getWorld() != null) {
            location.getWorld().dropItemNaturally(location, item);
        }
    }
}
