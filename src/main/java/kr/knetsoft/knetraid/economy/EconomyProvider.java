package kr.knetsoft.knetraid.economy;

import java.util.UUID;

/**
 * 경제 연동 추상화. 기본 구현체는 Vault이며, Vault가 없는 서버에서는
 * NullEconomyProvider가 모든 요청에 명확한 실패를 반환해 안전하게 비활성화된다.
 */
public interface EconomyProvider {

    boolean isAvailable();

    String getName();

    double getBalance(UUID uuid);

    boolean has(UUID uuid, double amount);

    EconomyResult withdraw(UUID uuid, double amount);

    EconomyResult deposit(UUID uuid, double amount);
}
