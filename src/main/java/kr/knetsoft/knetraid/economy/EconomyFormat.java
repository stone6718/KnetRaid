package kr.knetsoft.knetraid.economy;

public final class EconomyFormat {

    private EconomyFormat() {
    }

    public static String format(double amount, int decimalPlaces) {
        int safeDecimals = Math.max(0, decimalPlaces);
        return String.format("%,." + safeDecimals + "f", amount);
    }
}
