package kr.knetsoft.knetraid.config;

import kr.knetsoft.knetraid.KnetRaid;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * config.yml 및 각 모듈별 보조 설정 파일(raid.yml, faction.yml 등)의
 * 생성, 로드, 재로드를 담당한다.
 */
public class ConfigManager {

    private static final String[] MODULE_CONFIG_FILES = {
            "database.yml",
            "raid.yml",
            "faction.yml",
            "combat.yml",
            "bounty.yml",
            "economy.yml",
            "season.yml",
            "teleport.yml"
    };

    private final KnetRaid plugin;
    private final java.util.Map<String, FileConfiguration> moduleConfigs = new java.util.HashMap<>();

    public ConfigManager(KnetRaid plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        for (String fileName : MODULE_CONFIG_FILES) {
            moduleConfigs.put(fileName, loadOrCreate(fileName));
        }
    }

    private FileConfiguration loadOrCreate(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration getModuleConfig(String fileName) {
        return moduleConfigs.get(fileName);
    }

    public boolean isModuleEnabled(String moduleKey) {
        return plugin.getConfig().getBoolean("modules." + moduleKey + ".enabled", true);
    }

    public void setModuleEnabled(String moduleKey, boolean enabled) throws IOException {
        plugin.getConfig().set("modules." + moduleKey + ".enabled", enabled);
        plugin.saveConfig();
    }

    public boolean isDebug() {
        return plugin.getConfig().getBoolean("general.debug", false);
    }

    public void setDebug(boolean debug) throws IOException {
        plugin.getConfig().set("general.debug", debug);
        plugin.saveConfig();
    }
}
