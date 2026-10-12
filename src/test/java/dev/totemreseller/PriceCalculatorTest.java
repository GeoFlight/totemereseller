package dev.totemreseller;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PriceCalculatorTest {
    @Test
    void roundsAverageToNearestThousandThenUndercutsByOneThousand() {
        PriceSnapshot snapshot = PriceCalculator.calculate(List.of(
            listing("60000", 1),
            listing("65000", 1),
            listing("70000", 1),
            listing("80000", 1)
        ));

        assertEquals(new BigDecimal("68750.0000"), snapshot.averageUnitPrice());
        assertEquals(69_000, snapshot.roundedAverage());
        assertEquals(68_000, snapshot.resaleUnitPrice());
    }

    @Test
    void buysCheapestListingOnlyWhenProfitIsAtLeastFiveThousandPerItem() {
        AuctionListing eligible = listing("50000", 1);
        PriceSnapshot snapshot = PriceCalculator.calculate(List.of(
            eligible,
            listing("60000", 1),
            listing("65000", 1),
            listing("70000", 1),
            listing("80000", 1)
        ));

        assertSame(eligible, snapshot.bestBuy());
    }

    @Test
    void excludesListingsThatCannotMeetMinimumProfit() {
        PriceSnapshot snapshot = PriceCalculator.calculate(List.of(
            listing("66000", 1),
            listing("68000", 1),
            listing("70000", 1),
            listing("80000", 1)
        ));

        assertNull(snapshot.bestBuy());
    }

    @Test
    void acceptsExactlyFiveThousandCoinsOfProjectedProfit() {
        AuctionListing eligible = listing("50000", 1);
        PriceSnapshot snapshot = PriceCalculator.calculate(List.of(
            eligible,
            listing("61000", 1)
        ));

        assertEquals(55_000, snapshot.resaleUnitPrice());
        assertSame(eligible, snapshot.bestBuy());
    }

    @Test
    void normalizesStackPricesToPerTotemPrices() {
        PriceSnapshot snapshot = PriceCalculator.calculate(List.of(
            listing("100000", 2),
            listing("60000", 1)
        ));

        assertEquals(new BigDecimal("55000.0000"), snapshot.averageUnitPrice());
    }

    private static AuctionListing listing(String price, int count) {
        return new AuctionListing(
            "Totem of Undying",
            "minecraft:totem_of_undying",
            "seller",
            new BigDecimal(price),
            count
        );
    }
}
