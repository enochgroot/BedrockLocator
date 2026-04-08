package com.bedrocklocator;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// Placeholder screen to verify compilation
public class BedrockLocatorScreen extends Screen {
    public BedrockLocatorScreen() { super(Component.literal("Bedrock Locator")); }
    @Override public boolean isPauseScreen() { return false; }
}
