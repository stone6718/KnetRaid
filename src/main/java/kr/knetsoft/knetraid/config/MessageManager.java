package kr.knetsoft.knetraid.config;

import kr.knetsoft.knetraid.KnetRaid;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * messages.yml에 정의된 모든 사용자 메시지를 로드하고
 * 색상 코드 변환 및 플레이스홀더 치환을 처리한다.
 */
public class MessageManager {

    private final KnetRaid plugin;
    private YamlConfiguration messages;
    private String prefix = "";

    public MessageManager(KnetRaid plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(file);

        try (InputStream defaultStream = plugin.getResource("messages.yml")) {
            if (defaultStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("messages.yml 기본값을 불러오지 못했습니다: " + exception.getMessage());
        }

        prefix = colorize(messages.getString("prefix", "&8[&cKnetRaid&8] "));
    }

    public String getPrefix() {
        return prefix;
    }

    public String get(String path) {
        String raw = messages.getString(path);
        if (raw == null) {
            return prefix + ChatColor.RED + "메시지를 찾을 수 없습니다: " + path;
        }
        return colorize(raw.replace("{prefix}", prefix));
    }

    /**
     * key-value 쌍으로 {key} 형태의 플레이스홀더를 치환한다.
     * 예: get("general.invalid-player", "player", nickname)
     */
    public String get(String path, Object... replacements) {
        String message = get(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        }
        return message;
    }

    public List<String> getList(String path) {
        List<String> raw = messages.getStringList(path);
        List<String> result = new ArrayList<>(raw.size());
        for (String line : raw) {
            result.add(colorize(line.replace("{prefix}", prefix)));
        }
        return result;
    }

    public static String colorize(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
