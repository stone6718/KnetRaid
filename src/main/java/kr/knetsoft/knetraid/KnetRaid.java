package kr.knetsoft.knetraid;

import kr.knetsoft.knetraid.command.AdminCommand;
import kr.knetsoft.knetraid.command.BaseCommand;
import kr.knetsoft.knetraid.command.BountyCommand;
import kr.knetsoft.knetraid.command.CombatCommand;
import kr.knetsoft.knetraid.command.FactionCommand;
import kr.knetsoft.knetraid.command.HelpCommand;
import kr.knetsoft.knetraid.command.RaidCommand;
import kr.knetsoft.knetraid.command.SeasonCommand;
import kr.knetsoft.knetraid.command.WildernessCommand;
import kr.knetsoft.knetraid.bedrock.BedrockManager;
import kr.knetsoft.knetraid.bounty.BountyExpiryTask;
import kr.knetsoft.knetraid.bounty.BountyManager;
import kr.knetsoft.knetraid.combat.CombatActionBarTask;
import kr.knetsoft.knetraid.combat.CombatManager;
import kr.knetsoft.knetraid.config.ConfigManager;
import kr.knetsoft.knetraid.config.MessageManager;
import kr.knetsoft.knetraid.core.AsyncTaskTracker;
import kr.knetsoft.knetraid.core.PlayerDataCache;
import kr.knetsoft.knetraid.database.BaseRepository;
import kr.knetsoft.knetraid.database.BountyRepository;
import kr.knetsoft.knetraid.database.CombatLogRepository;
import kr.knetsoft.knetraid.database.CurrencyTransactionRepository;
import kr.knetsoft.knetraid.database.DatabaseManager;
import kr.knetsoft.knetraid.database.FactionRepository;
import kr.knetsoft.knetraid.database.PlayerRepository;
import kr.knetsoft.knetraid.database.RaidLogRepository;
import kr.knetsoft.knetraid.database.SchemaMigrator;
import kr.knetsoft.knetraid.economy.EconomyProvider;
import kr.knetsoft.knetraid.economy.NullEconomyProvider;
import kr.knetsoft.knetraid.economy.VaultEconomyProvider;
import kr.knetsoft.knetraid.faction.FactionManager;
import kr.knetsoft.knetraid.listener.BountyPayoutListener;
import kr.knetsoft.knetraid.listener.CombatListener;
import kr.knetsoft.knetraid.listener.FactionChatListener;
import kr.knetsoft.knetraid.listener.FactionCombatListener;
import kr.knetsoft.knetraid.listener.PlayerConnectionListener;
import kr.knetsoft.knetraid.listener.RaidProtectionListener;
import kr.knetsoft.knetraid.listener.SeasonStatListener;
import kr.knetsoft.knetraid.listener.TeleportWarmupListener;
import kr.knetsoft.knetraid.raid.BaseManager;
import kr.knetsoft.knetraid.raid.IntrusionAlertTask;
import kr.knetsoft.knetraid.database.SeasonRepository;
import kr.knetsoft.knetraid.season.SeasonAutoEndTask;
import kr.knetsoft.knetraid.season.SeasonEndService;
import kr.knetsoft.knetraid.season.SeasonManager;
import kr.knetsoft.knetraid.teleport.RandomTeleportService;
import kr.knetsoft.knetraid.teleport.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class KnetRaid extends JavaPlugin {

    private static final long SHUTDOWN_TASK_AWAIT_MS = 3000L;

    private static KnetRaid instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private PlayerRepository playerRepository;
    private PlayerDataCache playerDataCache;
    private FactionRepository factionRepository;
    private final FactionManager factionManager = new FactionManager();
    private BaseRepository baseRepository;
    private RaidLogRepository raidLogRepository;
    private final BaseManager baseManager = new BaseManager();
    private CombatLogRepository combatLogRepository;
    private final CombatManager combatManager = new CombatManager();
    private BountyRepository bountyRepository;
    private CurrencyTransactionRepository currencyTransactionRepository;
    private final BountyManager bountyManager = new BountyManager();
    private EconomyProvider economyProvider;
    private final TeleportManager teleportManager = new TeleportManager();
    private RandomTeleportService randomTeleportService;
    private SeasonRepository seasonRepository;
    private final SeasonManager seasonManager = new SeasonManager();
    private SeasonEndService seasonEndService;
    private BedrockManager bedrockManager;
    private final AsyncTaskTracker asyncTaskTracker = new AsyncTaskTracker();
    private BukkitTask intrusionAlertTask;
    private BukkitTask combatActionBarTask;
    private BukkitTask bountyExpiryTask;
    private BukkitTask seasonAutoEndTask;
    private volatile boolean serverStopping = false;

    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.messageManager = new MessageManager(this);
        this.messageManager.load();

        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("데이터베이스 연결에 실패하여 KnetRaid를 비활성화합니다. database.yml 설정을 확인해주세요.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SchemaMigrator schemaMigrator = new SchemaMigrator(databaseManager, getLogger());
        if (!schemaMigrator.migrate()) {
            getLogger().severe("데이터베이스 스키마 초기화에 실패하여 KnetRaid를 비활성화합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.playerRepository = new PlayerRepository(databaseManager);
        this.playerDataCache = new PlayerDataCache();
        this.factionRepository = new FactionRepository(databaseManager);
        this.baseRepository = new BaseRepository(databaseManager);
        this.raidLogRepository = new RaidLogRepository(databaseManager);
        this.combatLogRepository = new CombatLogRepository(databaseManager);
        this.bountyRepository = new BountyRepository(databaseManager);
        this.currencyTransactionRepository = new CurrencyTransactionRepository(databaseManager);
        this.economyProvider = resolveEconomyProvider();
        this.randomTeleportService = new RandomTeleportService(this);
        this.seasonRepository = new SeasonRepository(databaseManager);
        this.seasonEndService = new SeasonEndService(this);
        this.bedrockManager = BedrockManager.create(this);

        registerCommands();
        registerListeners();
        loadBasesIntoCache();
        loadActiveSeasonIntoCache();
        scheduleIntrusionAlertTask();
        scheduleCombatActionBarTask();
        scheduleBountyExpiryTask();
        scheduleSeasonAutoEndTask();
        warnIfCoreProtectRequested();

        getLogger().info("KnetRaid v" + getDescription().getVersion() + " 활성화 완료.");
    }

    @Override
    public void onDisable() {
        serverStopping = true;
        if (intrusionAlertTask != null) {
            intrusionAlertTask.cancel();
        }
        if (combatActionBarTask != null) {
            combatActionBarTask.cancel();
        }
        if (bountyExpiryTask != null) {
            bountyExpiryTask.cancel();
        }
        if (seasonAutoEndTask != null) {
            seasonAutoEndTask.cancel();
        }
        if (!asyncTaskTracker.awaitIdle(SHUTDOWN_TASK_AWAIT_MS)) {
            getLogger().warning("일부 비동기 작업이 종료 대기 시간 내에 끝나지 않았습니다. 일부 데이터가 저장되지 않았을 수 있습니다.");
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("KnetRaid가 비활성화되었습니다.");
        instance = null;
    }

    /**
     * config.yml 및 모든 보조 설정 파일, 메시지 파일을 다시 불러온다.
     * 데이터베이스 연결 및 진행 중인 비동기 작업에는 영향을 주지 않는다.
     *
     * @return 재로드 성공 여부
     */
    public boolean reload() {
        try {
            configManager.load();
            messageManager.load();
            return true;
        } catch (Exception exception) {
            getLogger().severe("설정 파일을 다시 불러오는 중 오류가 발생했습니다: " + exception.getMessage());
            return false;
        }
    }

    private void registerCommands() {
        registerCommand("세력", new FactionCommand(this));
        registerCommand("현상금", new BountyCommand(this));
        registerCommand("야생", new WildernessCommand(this));
        registerCommand("약탈", new RaidCommand(this));
        registerCommand("전투", new CombatCommand(this));
        registerCommand("시즌", new SeasonCommand(this));
        registerCommand("약탈도움말", new HelpCommand(this));
        registerCommand("약탈관리", new AdminCommand(this));
    }

    private void registerCommand(String name, BaseCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("plugin.yml에 등록되지 않은 명령어입니다: /" + name);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new FactionChatListener(this), this);
        getServer().getPluginManager().registerEvents(new FactionCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new RaidProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new BountyPayoutListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportWarmupListener(this), this);
        getServer().getPluginManager().registerEvents(new SeasonStatListener(this), this);
    }

    /**
     * 활성 시즌 id를 비동기로 불러와 SeasonManager 캐시를 채운다.
     * 킬/약탈/현상금 이벤트가 매번 DB를 조회하지 않도록 하기 위함이다.
     */
    private void loadActiveSeasonIntoCache() {
        asyncTaskTracker.begin();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                seasonRepository.findActiveSeason().ifPresent(season ->
                        seasonManager.setActiveSeason(season.id(), season.name()));
            } catch (java.sql.SQLException exception) {
                getLogger().severe("활성 시즌 정보를 불러오는 중 오류가 발생했습니다: " + exception.getMessage());
            } finally {
                asyncTaskTracker.end();
            }
        });
    }

    /**
     * Vault 플러그인과 Economy 서비스가 모두 준비된 경우에만 VaultEconomyProvider를 생성한다.
     * 그렇지 않으면 모든 요청에 명확한 실패를 반환하는 NullEconomyProvider로 안전하게 대체한다.
     */
    private EconomyProvider resolveEconomyProvider() {
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            java.util.Optional<EconomyProvider> vault = VaultEconomyProvider.tryCreate();
            if (vault.isPresent()) {
                getLogger().info("Vault 경제 연동에 성공했습니다. (" + vault.get().getName() + ")");
                return vault.get();
            }
        }
        getLogger().warning("Vault 플러그인을 찾을 수 없거나 Economy 서비스가 등록되지 않았습니다. "
                + "현상금 등 경제 관련 기능이 비활성화됩니다.");
        return new NullEconomyProvider();
    }

    /**
     * 등록된 모든 기지를 비동기로 불러와 BaseManager 캐시를 채운다.
     * 보호/침입감지 이벤트는 이 캐시만 사용하므로 매번 DB를 조회하지 않는다.
     */
    private void loadBasesIntoCache() {
        asyncTaskTracker.begin();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                var bases = baseRepository.listAll();
                baseManager.setAll(bases);
                getLogger().info("등록된 기지 " + bases.size() + "개를 불러왔습니다.");
            } catch (java.sql.SQLException exception) {
                getLogger().severe("기지 목록을 불러오는 중 오류가 발생했습니다: " + exception.getMessage());
            } finally {
                asyncTaskTracker.end();
            }
        });
    }

    private void warnIfCoreProtectRequested() {
        var raidConfig = configManager.getModuleConfig("raid.yml");
        if (raidConfig != null && "COREPROTECT".equalsIgnoreCase(raidConfig.getString("raid.logging.provider", "SELF"))) {
            getLogger().warning("raid.yml의 logging.provider가 COREPROTECT로 설정되어 있지만 아직 지원되지 않습니다. "
                    + "자체 로그(SELF)를 대신 사용합니다.");
        }
    }

    private void scheduleIntrusionAlertTask() {
        var raidConfig = configManager.getModuleConfig("raid.yml");
        long intervalSeconds = raidConfig != null ? raidConfig.getLong("raid.alert-check-interval-seconds", 5) : 5;
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);
        this.intrusionAlertTask = new IntrusionAlertTask(this).runTaskTimer(this, intervalTicks, intervalTicks);
    }

    private void scheduleCombatActionBarTask() {
        this.combatActionBarTask = new CombatActionBarTask(this).runTaskTimer(this, 20L, 20L);
    }

    private void scheduleBountyExpiryTask() {
        long intervalTicks = 5L * 60L * 20L; // 5분
        this.bountyExpiryTask = new BountyExpiryTask(this).runTaskTimer(this, intervalTicks, intervalTicks);
    }

    private void scheduleSeasonAutoEndTask() {
        long intervalTicks = 10L * 60L * 20L; // 10분
        this.seasonAutoEndTask = new SeasonAutoEndTask(this).runTaskTimer(this, intervalTicks, intervalTicks);
    }

    public void markServerStopping() {
        this.serverStopping = true;
    }

    public boolean isServerStopping() {
        return serverStopping;
    }

    public static KnetRaid getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }

    public PlayerDataCache getPlayerDataCache() {
        return playerDataCache;
    }

    public AsyncTaskTracker getAsyncTaskTracker() {
        return asyncTaskTracker;
    }

    public FactionRepository getFactionRepository() {
        return factionRepository;
    }

    public FactionManager getFactionManager() {
        return factionManager;
    }

    public BaseRepository getBaseRepository() {
        return baseRepository;
    }

    public RaidLogRepository getRaidLogRepository() {
        return raidLogRepository;
    }

    public BaseManager getBaseManager() {
        return baseManager;
    }

    public CombatLogRepository getCombatLogRepository() {
        return combatLogRepository;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public BountyRepository getBountyRepository() {
        return bountyRepository;
    }

    public CurrencyTransactionRepository getCurrencyTransactionRepository() {
        return currencyTransactionRepository;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public EconomyProvider getEconomyProvider() {
        return economyProvider;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public RandomTeleportService getRandomTeleportService() {
        return randomTeleportService;
    }

    public SeasonRepository getSeasonRepository() {
        return seasonRepository;
    }

    public SeasonManager getSeasonManager() {
        return seasonManager;
    }

    public SeasonEndService getSeasonEndService() {
        return seasonEndService;
    }

    public BedrockManager getBedrockManager() {
        return bedrockManager;
    }
}
