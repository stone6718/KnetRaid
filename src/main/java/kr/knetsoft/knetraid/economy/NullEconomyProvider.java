package kr.knetsoft.knetraid.economy;

import java.util.UUID;

/**
 * Vault가 설치되어 있지 않을 때 사용되는 대체 구현체.
 * 모든 요청에 명확한 실패 사유를 반환해, 경제 관련 기능이 조용히 오작동하지 않고
 * 안전하게 비활성화되도록 한다.
 */
public class NullEconomyProvider implements EconomyProvider {

    private static final String REASON = "Vault 플러그인이 설치되어 있지 않아 경제 기능을 사용할 수 없습니다.";

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getName() {
        return "없음 (Vault 미설치)";
    }

    @Override
    public double getBalance(UUID uuid) {
        return 0.0;
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        return false;
    }

    @Override
    public EconomyResult withdraw(UUID uuid, double amount) {
        return EconomyResult.failure(REASON);
    }

    @Override
    public EconomyResult deposit(UUID uuid, double amount) {
        return EconomyResult.failure(REASON);
    }
}
