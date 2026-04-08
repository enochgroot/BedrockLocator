package com.bedrocklocator;

import net.fabricmc.api.ClientModInitializer;

// Stripped version - no screen, no keybind - just verifies basic compilation
public class BedrockLocatorMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("[BedrockLocator] Loaded v1.0.0");
    }
}
