package kr.knetsoft.knetraid.bedrock;

import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/**
 * Floodgate가 설치되어 있을 때만 Bedrock 플레이어 여부를 식별한다.
 * Floodgate 미설치 서버에서도 플러그인이 정상 동작해야 하므로,
 * Floodgate 관련 클래스는 이 클래스 내부(플러그인 존재가 확인된 뒤)에서만 참조한다.
 */
public class BedrockManager {

    private final boolean available;

    private BedrockManager(boolean available) {
        this.available = available;
    }

    public static BedrockManager create(JavaPlugin plugin) {
        boolean floodgatePresent = plugin.getServer().getPluginManager().getPlugin("floodgate") != null;
        if (!floodgatePresent) {
            plugin.getLogger().info("Floodgate 플러그인을 찾을 수 없습니다. Bedrock 플레이어 식별 기능이 비활성화됩니다 "
                    + "(Java 플레이어 전용 서버라면 정상입니다).");
            return new BedrockManager(false);
        }
        try {
            FloodgateApi.getInstance();
            plugin.getLogger().info("Floodgate 연동에 성공했습니다. Bedrock 플레이어를 식별합니다.");
            return new BedrockManager(true);
        } catch (Exception exception) {
            plugin.getLogger().warning("Floodgate API 초기화에 실패했습니다. Bedrock 플레이어 식별 기능이 비활성화됩니다: "
                    + exception.getMessage());
            return new BedrockManager(false);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isBedrockPlayer(UUID uuid) {
        if (!available) {
            return false;
        }
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }
}
