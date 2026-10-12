package dev.totemreseller;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class ConfigScreen extends Screen {
    private final Screen parent;
    private final TradeAutomation automation;
    private TextFieldWidget apiKeyField;

    public ConfigScreen(Screen parent, TradeAutomation automation) {
        super(Text.literal("Totem Reseller"));
        this.parent = parent;
        this.automation = automation;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int fieldWidth = Math.min(360, width - 40);
        int fieldLeft = centerX - fieldWidth / 2;

        apiKeyField = new TextFieldWidget(
            textRenderer,
            fieldLeft,
            height / 2 - 42,
            fieldWidth,
            20,
            Text.literal("DonutSMP API key")
        );
        apiKeyField.setMaxLength(256);
        apiKeyField.setText(automation.getApiKey());
        addDrawableChild(apiKeyField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Save API key"), button ->
            automation.saveApiKey(apiKeyField.getText()))
            .dimensions(centerX - 170, height / 2 - 10, 105, 20)
            .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Start"), button ->
            automation.start(apiKeyField.getText()))
            .dimensions(centerX - 55, height / 2 - 10, 70, 20)
            .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), button ->
            automation.stop())
            .dimensions(centerX + 25, height / 2 - 10, 70, 20)
            .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
            .dimensions(centerX - 50, height / 2 + 76, 100, 20)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        drawCentered(context, title.getString(), height / 2 - 94, 0xFFFFFF);
        context.drawText(textRenderer, "API key", width / 2 - Math.min(180, (width - 40) / 2),
            height / 2 - 58, 0xA0A0A0, false);
        drawCentered(context, automation.getStatus(), height / 2 + 22, 0xFFFFFF);

        PriceSnapshot snapshot = automation.getSnapshot();
        if (snapshot.hasPrices()) {
            drawCentered(context,
                "Listings: " + snapshot.listingCount()
                    + "   Average: " + formatCoins(snapshot.roundedAverage())
                    + "   Resale: " + formatCoins(snapshot.resaleUnitPrice()),
                height / 2 + 42, 0xD0D0D0);
            if (snapshot.bestBuy() != null) {
                drawCentered(context,
                    "Cheapest eligible buy: " + formatCoins(snapshot.bestBuy().unitPrice()
                        .setScale(0, java.math.RoundingMode.HALF_UP).longValue()),
                    height / 2 + 58, 0xD0D0D0);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static String formatCoins(long value) {
        return String.format("%,d", value);
    }

    private void drawCentered(DrawContext context, String text, int y, int color) {
        context.drawText(textRenderer, text, (width - textRenderer.getWidth(text)) / 2, y, color, false);
    }
}
