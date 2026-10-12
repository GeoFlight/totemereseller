package dev.totemreseller;

import java.math.BigDecimal;

public record AuctionListing(
    String itemName,
    String itemId,
    String seller,
    BigDecimal price,
    int count
) {
    public boolean isTotem() {
        String normalizedName = itemName.toLowerCase();
        String normalizedId = itemId.toLowerCase();
        return normalizedName.contains("totem of undying")
            || normalizedId.contains("totem_of_undying");
    }

    public BigDecimal unitPrice() {
        return price.divide(BigDecimal.valueOf(count), 4, java.math.RoundingMode.HALF_UP);
    }
}
