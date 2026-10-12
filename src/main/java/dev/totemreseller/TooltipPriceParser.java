package dev.totemreseller;

import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TooltipPriceParser {
    private static final Pattern PRICE = Pattern.compile(
        "(?i)(?:price|cost|coins?)\\D{0,12}(\\$?\\s*\\d[\\d,]*(?:\\.\\d+)?\\s*[kmb]?)"
            + "|(\\$\\s*\\d[\\d,]*(?:\\.\\d+)?\\s*[kmb]?)"
            + "|(\\d[\\d,]*(?:\\.\\d+)?\\s*(?:[kmb]\\s*)?coins?)"
            + "|(\\d[\\d,]*(?:\\.\\d+)?\\s*[kmb])"
    );

    private TooltipPriceParser() {
    }

    public static Long findPrice(List<Text> tooltip) {
        for (Text line : tooltip) {
            String text = line.getString();
            Matcher matcher = PRICE.matcher(text);
            if (!matcher.find()) {
                continue;
            }

            String match = matcher.group(1);
            if (match == null) match = matcher.group(2);
            if (match == null) match = matcher.group(3);
            if (match == null) match = matcher.group(4);
            if (match == null) match = matcher.group(5);
            Long price = parseCoins(match);
            if (price != null) {
                return price;
            }
        }
        return null;
    }

    private static Long parseCoins(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
            .replace("$", "")
            .replace(",", "")
            .replace("coins", "")
            .replace("coin", "")
            .trim();
        if (normalized.isEmpty()) {
            return null;
        }

        BigDecimal multiplier = BigDecimal.ONE;
        char suffix = normalized.charAt(normalized.length() - 1);
        if (suffix == 'k' || suffix == 'm' || suffix == 'b') {
            multiplier = switch (suffix) {
                case 'k' -> BigDecimal.valueOf(1_000);
                case 'm' -> BigDecimal.valueOf(1_000_000);
                default -> BigDecimal.valueOf(1_000_000_000);
            };
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        try {
            return new BigDecimal(normalized)
                .multiply(multiplier)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }
}
