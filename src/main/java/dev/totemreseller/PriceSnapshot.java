package dev.totemreseller;

import java.math.BigDecimal;

public record PriceSnapshot(
    BigDecimal averageUnitPrice,
    long roundedAverage,
    long resaleUnitPrice,
    BigDecimal lowestUnitPrice,
    int listingCount,
    AuctionListing bestBuy
) {
    public static PriceSnapshot empty() {
        return new PriceSnapshot(BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, 0, null);
    }

    public boolean hasPrices() {
        return listingCount > 0;
    }
}
