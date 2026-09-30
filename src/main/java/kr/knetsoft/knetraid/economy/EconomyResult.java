package kr.knetsoft.knetraid.economy;

public record EconomyResult(boolean success, String errorMessage) {

    public static EconomyResult ok() {
        return new EconomyResult(true, null);
    }

    public static EconomyResult failure(String errorMessage) {
        return new EconomyResult(false, errorMessage);
    }
}
