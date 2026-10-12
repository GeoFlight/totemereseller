package dev.totemreseller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class DonutApiClient {
    private static final String AUCTION_URL =
        "https://api.donutsmp.net/v1/auction/list/%d";
    private static final int MAX_PAGES = 10;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    public List<AuctionListing> fetchTotemListings(String apiKey)
        throws IOException, InterruptedException {
        List<AuctionListing> listings = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AUCTION_URL.formatted(page)))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new IOException("DonutSMP rejected the API key or denied access.");
            }
            if (response.statusCode() == 429) {
                throw new IOException("DonutSMP rate-limited the request. The next scan will retry later.");
            }
            if (response.statusCode() != 200) {
                throw new IOException("DonutSMP API returned HTTP " + response.statusCode() + ".");
            }

            JsonObject root;
            try {
                root = JsonParser.parseString(response.body()).getAsJsonObject();
            } catch (RuntimeException exception) {
                throw new IOException("DonutSMP returned invalid JSON.", exception);
            }

            JsonElement resultElement = root.get("result");
            if (resultElement == null || !resultElement.isJsonArray()) {
                throw new IOException("DonutSMP response did not contain an auction result array.");
            }

            JsonArray results = resultElement.getAsJsonArray();
            if (results.isEmpty()) {
                break;
            }

            for (JsonElement entry : results) {
                if (!entry.isJsonObject()) {
                    continue;
                }
                AuctionListing listing = parseListing(entry.getAsJsonObject());
                if (listing != null && listing.isTotem()) {
                    listings.add(listing);
                }
            }

            if (page < MAX_PAGES) {
                Thread.sleep(250);
            }
        }

        return List.copyOf(listings);
    }

    private static AuctionListing parseListing(JsonObject listing) {
        JsonObject item = object(listing, "item");
        if (item == null) {
            return null;
        }

        String name = string(item, "display_name");
        String id = string(item, "id");
        BigDecimal price = decimal(listing, "price");
        int count = integer(item, "count", 1);
        JsonObject seller = object(listing, "seller");
        String sellerName = seller == null ? "" : string(seller, "name");

        if (price == null || price.signum() <= 0 || count <= 0) {
            return null;
        }

        return new AuctionListing(name, id, sellerName, price, count);
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String string(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static BigDecimal decimal(JsonObject parent, String key) {
        try {
            JsonElement value = parent.get(key);
            return value == null || value.isJsonNull() ? null : value.getAsBigDecimal();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static int integer(JsonObject parent, String key, int fallback) {
        try {
            JsonElement value = parent.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }
}
