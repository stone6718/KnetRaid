package kr.knetsoft.knetraid.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;
import java.util.UUID;

/**
 * Vault의 Economy 서비스를 통해 실제 경제 플러그인(EssentialsX 등)과 연동한다.
 * Vault 플러그인이 로드되어 있고 Economy 서비스가 등록된 경우에만 생성된다.
 */
public class VaultEconomyProvider implements EconomyProvider {

    private final Economy economy;

    private VaultEconomyProvider(Economy economy) {
        this.economy = economy;
    }

    /**
     * Vault 플러그인과 Economy 서비스 등록 여부를 확인한 뒤에만 인스턴스를 생성한다.
     * 이 메서드를 호출하는 쪽에서 미리 Vault 존재 여부를 확인해야
     * Vault 미설치 서버에서 관련 클래스가 로드되지 않는다.
     */
    public static Optional<EconomyProvider> tryCreate() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return Optional.empty();
        }
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return Optional.empty();
        }
        return Optional.of(new VaultEconomyProvider(registration.getProvider()));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getName() {
        return "Vault (" + economy.getName() + ")";
    }

    @Override
    public double getBalance(UUID uuid) {
        return economy.getBalance(offline(uuid));
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        return economy.has(offline(uuid), amount);
    }

    @Override
    public EconomyResult withdraw(UUID uuid, double amount) {
        EconomyResponse response = economy.withdrawPlayer(offline(uuid), amount);
        return response.transactionSuccess() ? EconomyResult.ok() : EconomyResult.failure(response.errorMessage);
    }

    @Override
    public EconomyResult deposit(UUID uuid, double amount) {
        EconomyResponse response = economy.depositPlayer(offline(uuid), amount);
        return response.transactionSuccess() ? EconomyResult.ok() : EconomyResult.failure(response.errorMessage);
    }

    private OfflinePlayer offline(UUID uuid) {
        return Bukkit.getOfflinePlayer(uuid);
    }
}
