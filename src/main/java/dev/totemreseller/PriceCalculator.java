package dev.totemreseller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

public final class PriceCalculator {
    public static final long MIN_PROFIT_PER_TOTEM = 5_000;
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000);

    private PriceCalculator() {
    }

    public static PriceSnapshot calculate(List<AuctionListing> listings) {
        List<AuctionListing> totems = listings.stream()
            .filter(AuctionListing::isTotem)
            .filter(listing -> listing.count() > 0)
            .filter(listing -> listing.price().signum() > 0)
            .toList();

        if (totems.isEmpty()) {
            return PriceSnapshot.empty();
        }

        BigDecimal averageUnitPrice = totems.stream()
            .map(AuctionListing::unitPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(totems.size()), 4, RoundingMode.HALF_UP);

        long roundedAverage = averageUnitPrice
            .divide(THOUSAND, 0, RoundingMode.HALF_UP)
            .multiply(THOUSAND)
            .longValueExact();
        long resaleUnitPrice = Math.max(0, roundedAverage - 1_000);

        AuctionListing bestBuy = totems.stream()
            .filter(listing -> BigDecimal.valueOf(resaleUnitPrice)
                .subtract(listing.unitPrice())
                .compareTo(BigDecimal.valueOf(MIN_PROFIT_PER_TOTEM)) >= 0)
            .min(Comparator.comparing(AuctionListing::unitPrice))
            .orElse(null);

        return new PriceSnapshot(
            averageUnitPrice,
            roundedAverage,
            resaleUnitPrice,
            totems.stream().map(AuctionListing::unitPrice).min(BigDecimal::compareTo).orElseThrow(),
            totems.size(),
            bestBuy
        );
    }
}
