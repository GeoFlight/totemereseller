package dev.totemreseller;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TooltipPriceParserTest {
    @Test
    void parsesPriceWithCoinLabel() {
        assertEquals(60_000L, TooltipPriceParser.findPrice(
            List.of(Text.literal("Price: 60,000 coins"))));
    }

    @Test
    void parsesCompactPrice() {
        assertEquals(60_000L, TooltipPriceParser.findPrice(
            List.of(Text.literal("Price: 60k"))));
    }
}
