package com.bedrocklocator;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class BedrockLocatorMod implements ClientModInitializer {

    public static final String MOD_ID = "bedrocklocator";
    public static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.bedrocklocator.open",
            GLFW.GLFW_KEY_B,
            "category.bedrocklocator"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.level != null) {
                    client.setScreen(new BedrockLocatorScreen());
                }
            }
        });

        System.out.println("[BedrockLocator] Loaded — Press B to open the coordinate finder");
    }
}
