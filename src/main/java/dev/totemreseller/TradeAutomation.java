package dev.totemreseller;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TradeAutomation {
    private static final int SCAN_INTERVAL_TICKS = 1_200;
    private static final int ACTION_TIMEOUT_TICKS = 100;
    private static final int SALE_TIMEOUT_TICKS = 60;

    private final DonutApiClient apiClient = new DonutApiClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "totem-reseller-api");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentLinkedQueue<ScanResult> completedScans = new ConcurrentLinkedQueue<>();

    private volatile String status = "Stopped";
    private volatile PriceSnapshot snapshot = PriceSnapshot.empty();
    private boolean running;
    private boolean scanInFlight;
    private long generation;
    private String apiKey = "";
    private ActionState actionState = ActionState.READY;
    private AuctionListing candidate;
    private long resaleUnitPrice;
    private int ticksInState;
    private int nextScanTick;
    private int inventoryBeforePurchase;
    private int inventoryBeforeSale;
    private int expectedSaleCount;

    public TradeAutomation() {
        apiKey = loadApiKey();
    }

    public String getStatus() {
        return status;
    }

    public PriceSnapshot getSnapshot() {
        return snapshot;
    }

    public boolean isRunning() {
        return running;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void saveApiKey(String value) {
        apiKey = value.trim();
        Path keyFile = getKeyFile();
        try {
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, apiKey);
            status = apiKey.isEmpty() ? "API key cleared" : "API key saved locally";
        } catch (IOException exception) {
            status = "Could not save API key: " + exception.getMessage();
        }
    }

    public void start(String value) {
        saveApiKey(value);
        if (status.startsWith("Could not save API key:")) {
            running = false;
            return;
        }
        if (apiKey.isBlank()) {
            status = "Enter an API key before starting";
            return;
        }

        generation++;
        completedScans.clear();
        running = true;
        actionState = ActionState.READY;
        candidate = null;
        scanInFlight = false;
        nextScanTick = 0;
        status = "Starting";
    }

    public void stop() {
        generation++;
        running = false;
        scanInFlight = false;
        candidate = null;
        actionState = ActionState.READY;
        status = "Stopped";
    }

    public void tick(MinecraftClient client) {
        if (!running) {
            return;
        }

        if (client.player == null || client.world == null) {
            status = "Waiting for a DonutSMP world";
            return;
        }
        if (!isDonutServer(client)) {
            status = "Join a DonutSMP server to enable trading";
            return;
        }

        ScanResult scanResult;
        while ((scanResult = completedScans.poll()) != null) {
            if (scanResult.generation() == generation) {
                scanInFlight = false;
                handleScanResult(client, scanResult);
            }
        }

        switch (actionState) {
            case READY -> {
                if (!scanInFlight && client.player.age >= nextScanTick) {
                    requestScan();
                }
            }
            case OPEN_AUCTION -> tickOpenAuction(client);
            case WAIT_FOR_PURCHASE -> tickWaitForPurchase(client);
            case MOVE_TO_HAND -> tickMoveToHand(client);
            case WAIT_FOR_HAND -> tickWaitForHand(client);
            case WAIT_FOR_SALE -> tickWaitForSale(client);
        }
    }

    private void requestScan() {
        scanInFlight = true;
        status = "Reading DonutSMP auction listings";
        long scanGeneration = generation;
        worker.submit(() -> {
            try {
                List<AuctionListing> listings = apiClient.fetchTotemListings(apiKey);
                completedScans.add(new ScanResult(
                    scanGeneration, PriceCalculator.calculate(listings), null));
            } catch (IOException exception) {
                completedScans.add(new ScanResult(scanGeneration, null, exception.getMessage()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                completedScans.add(new ScanResult(
                    scanGeneration, null, "Auction scan was interrupted"));
            } catch (ArithmeticException exception) {
                completedScans.add(new ScanResult(
                    scanGeneration, null, "Auction price exceeded the supported numeric range"));
            }
        });
    }

    private void handleScanResult(MinecraftClient client, ScanResult result) {
        if (!running || result.generation() != generation) {
            return;
        }
        if (result.error() != null) {
            status = result.error();
            nextScanTick = client.player == null ? SCAN_INTERVAL_TICKS
                : client.player.age + SCAN_INTERVAL_TICKS;
            return;
        }

        snapshot = result.snapshot();
        if (!snapshot.hasPrices()) {
            status = "No totem listings found in the scanned pages";
            nextScanTick = client.player == null ? SCAN_INTERVAL_TICKS
                : client.player.age + SCAN_INTERVAL_TICKS;
            return;
        }

        candidate = snapshot.bestBuy();
        if (candidate == null) {
            status = "No listing meets the 5,000-coin minimum profit";
            nextScanTick = client.player == null ? SCAN_INTERVAL_TICKS
                : client.player.age + SCAN_INTERVAL_TICKS;
            return;
        }

        resaleUnitPrice = snapshot.resaleUnitPrice();
        if (client.player == null || client.world == null) {
            status = "Price updated; waiting to connect before buying";
            nextScanTick = SCAN_INTERVAL_TICKS;
            return;
        }
        if (freeInventorySlots(client.player) < candidate.count()) {
            finishAttempt(client, "Not enough free inventory slots for this listing");
            return;
        }

        inventoryBeforePurchase = countTotems(client.player);
        if (client.currentScreen instanceof HandledScreen<?>) {
            client.player.closeHandledScreen();
            client.setScreen(null);
        }
        client.player.networkHandler.sendChatCommand("ah totem");
        actionState = ActionState.OPEN_AUCTION;
        ticksInState = 0;
        status = "Looking for the qualifying totem in /ah totem";
    }

    private void tickOpenAuction(MinecraftClient client) {
        ticksInState++;
        if (client.currentScreen instanceof HandledScreen<?> screen) {
            int slot = findCandidateSlot(client, screen.getScreenHandler(), candidate);
            if (slot >= 0 && client.interactionManager != null) {
                client.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId,
                    slot,
                    0,
                    SlotActionType.PICKUP,
                    client.player
                );
                actionState = ActionState.WAIT_FOR_PURCHASE;
                ticksInState = 0;
                status = "Clicked the matching listing; confirming purchase";
                return;
            }
        }

        if (ticksInState > ACTION_TIMEOUT_TICKS) {
            finishAttempt(client, "Could not find that exact totem listing in the Auction House");
        }
    }

    private void tickWaitForPurchase(MinecraftClient client) {
        ticksInState++;
        if (countTotems(client.player) > inventoryBeforePurchase) {
            if (client.currentScreen instanceof HandledScreen<?>) {
                client.player.closeHandledScreen();
            }
            actionState = ActionState.MOVE_TO_HAND;
            ticksInState = 0;
            status = "Purchased; preparing the totem for relisting";
            return;
        }

        if (ticksInState > ACTION_TIMEOUT_TICKS) {
            finishAttempt(client, "Purchase was not confirmed; no item was added");
        }
    }

    private void tickMoveToHand(MinecraftClient client) {
        ticksInState++;
        ClientPlayerEntity player = client.player;
        if (player.currentScreenHandler.syncId != 0) {
            if (ticksInState > ACTION_TIMEOUT_TICKS) {
                finishAttempt(client, "Could not close the Auction House before relisting");
            }
            return;
        }

        if (player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            actionState = ActionState.WAIT_FOR_HAND;
            ticksInState = 0;
            return;
        }

        int inventorySlot = findTotemInventorySlot(player);
        if (inventorySlot < 0 || client.interactionManager == null) {
            finishAttempt(client, "Purchased totem was not found in the inventory");
            return;
        }

        int handlerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        int selectedHotbarSlot = player.getInventory().getSelectedSlot();
        ScreenHandler handler = player.currentScreenHandler;
        client.interactionManager.clickSlot(
            handler.syncId,
            handlerSlot,
            selectedHotbarSlot,
            SlotActionType.SWAP,
            player
        );
        actionState = ActionState.WAIT_FOR_HAND;
        ticksInState = 0;
    }

    private void tickWaitForHand(MinecraftClient client) {
        ticksInState++;
        ItemStack held = client.player.getMainHandStack();
        if (held.isOf(Items.TOTEM_OF_UNDYING)) {
            int count = held.getCount();
            long totalPrice;
            try {
                totalPrice = Math.multiplyExact(resaleUnitPrice, count);
            } catch (ArithmeticException exception) {
                finishAttempt(client, "Calculated listing price is too large");
                return;
            }

            inventoryBeforeSale = countTotems(client.player);
            expectedSaleCount = count;
            client.player.networkHandler.sendChatCommand("ah sell " + totalPrice);
            actionState = ActionState.WAIT_FOR_SALE;
            ticksInState = 0;
            status = "Sent /ah sell " + totalPrice;
            return;
        }

        if (ticksInState > ACTION_TIMEOUT_TICKS) {
            finishAttempt(client, "Could not move a totem into your selected hand");
        }
    }

    private void tickWaitForSale(MinecraftClient client) {
        ticksInState++;
        if (countTotems(client.player) <= inventoryBeforeSale - expectedSaleCount) {
            finishAttempt(client, "Totem listed for " + formatCoins(resaleUnitPrice)
                + " each; minimum projected profit met");
            return;
        }

        if (ticksInState > SALE_TIMEOUT_TICKS) {
            stop();
            status = "Could not confirm the listing. Automation stopped; check your hand and Auction House";
        }
    }

    private int findCandidateSlot(
        MinecraftClient client,
        ScreenHandler handler,
        AuctionListing wanted
    ) {
        if (wanted == null) {
            return -1;
        }

        long wantedPrice;
        try {
            wantedPrice = wanted.price().setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException exception) {
            return -1;
        }

        for (int slotIndex = 0; slotIndex < handler.slots.size(); slotIndex++) {
            ItemStack stack = handler.slots.get(slotIndex).getStack();
            if (!stack.isOf(Items.TOTEM_OF_UNDYING) || stack.getCount() != wanted.count()) {
                continue;
            }

            List<Text> tooltip = stack.getTooltip(
                Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC);
            Long shownPrice = TooltipPriceParser.findPrice(tooltip);
            if (shownPrice != null && shownPrice == wantedPrice) {
                return slotIndex;
            }
        }
        return -1;
    }

    private int findTotemInventorySlot(ClientPlayerEntity player) {
        for (int slot = 0; slot < 36; slot++) {
            if (player.getInventory().getStack(slot).isOf(Items.TOTEM_OF_UNDYING)) {
                return slot;
            }
        }
        return -1;
    }

    private int freeInventorySlots(ClientPlayerEntity player) {
        int freeSlots = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (player.getInventory().getStack(slot).isEmpty()) {
                freeSlots++;
            }
        }
        return freeSlots;
    }

    private int countTotems(ClientPlayerEntity player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void finishAttempt(MinecraftClient client, String message) {
        status = message;
        candidate = null;
        actionState = ActionState.READY;
        ticksInState = 0;
        nextScanTick = client.player == null ? SCAN_INTERVAL_TICKS
            : client.player.age + SCAN_INTERVAL_TICKS;
    }

    private String loadApiKey() {
        Path keyFile = getKeyFile();
        try {
            return Files.exists(keyFile) ? Files.readString(keyFile).trim() : "";
        } catch (IOException exception) {
            status = "Could not read saved API key: " + exception.getMessage();
            return "";
        }
    }

    private Path getKeyFile() {
        return FabricLoader.getInstance()
            .getConfigDir()
            .resolve("totemreseller")
            .resolve("api-key.txt");
    }

    private static String formatCoins(long value) {
        return String.format("%,d coins", value);
    }

    private static boolean isDonutServer(MinecraftClient client) {
        if (client.getCurrentServerEntry() == null) {
            return false;
        }
        String address = client.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
        int portSeparator = address.indexOf(':');
        String host = portSeparator < 0 ? address : address.substring(0, portSeparator);
        return host.equals("donutsmp.net") || host.endsWith(".donutsmp.net");
    }

    private enum ActionState {
        READY,
        OPEN_AUCTION,
        WAIT_FOR_PURCHASE,
        MOVE_TO_HAND,
        WAIT_FOR_HAND,
        WAIT_FOR_SALE
    }

    private record ScanResult(long generation, PriceSnapshot snapshot, String error) {
    }
}
