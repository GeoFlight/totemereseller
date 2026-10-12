package dev.totemreseller;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class TotemResellerClient implements ClientModInitializer {
    public static final TradeAutomation AUTOMATION = new TradeAutomation();

    private static KeyBinding openConfigKey;

    @Override
    public void onInitializeClient() {
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.totemreseller.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.wasPressed()) {
                client.setScreen(new ConfigScreen(client.currentScreen, AUTOMATION));
            }
            AUTOMATION.tick(client);
        });
    }
}
