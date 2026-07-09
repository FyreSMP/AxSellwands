package com.artillexstudios.axsellwands.sellwands;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Everything AxSellwands stores on a sellwand item. Read with {@link SellwandRenderer#read},
 * written back by {@link SellwandRenderer#render}.
 *
 * <p>{@code expiresAt} is an epoch millisecond deadline, or {@link SellwandRenderer#NO_EXPIRY}
 * for wands that never expire.
 */
public record SellwandState(String type, @Nullable UUID uuid, float multiplier, long lastUsed, int uses, int maxUses,
                            int soldAmount, double soldPrice, long expiresAt) {
}
