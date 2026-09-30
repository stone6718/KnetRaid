package kr.knetsoft.knetraid.model;

import java.util.UUID;

public record CurrencyTransaction(String id, UUID playerUuid, double amount, CurrencyReason reason, long occurredAt) {
}
