package com.bedrocklocator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import java.util.List;
import java.util.ArrayList;

// Test: .pos().size() builder, no shadow on drawString, no EditBox
public class BedrockLocatorScreen extends Screen {

    private long seed = 0L;
    private String status = "Press Scan to scan the Nether ceiling.";
    private int statusColor = 0xFFCCCCCC;
    private int[][] display;
    private int radius = 8;

    public BedrockLocatorScreen() {
        super(Component.literal("Bedrock Locator"));
    }

    @Override
    protected void init() {
        super.init();
        int s = radius * 2 + 1;
        display = new int[s][s];

        addRenderableWidget(
            Button.builder(Component.literal("Scan Nether"), b -> doScan())
                .pos(width / 2 - 100, height - 60).size(90, 20).build()
        );
        addRenderableWidget(
            Button.builder(Component.literal("Find Coords"), b -> doFind())
                .pos(width / 2 + 10, height - 60).size(90, 20).build()
        );
        addRenderableWidget(
            Button.builder(Component.literal("Close"), b -> onClose())
                .pos(width / 2 - 40, height - 35).size(80, 20).build()
        );
    }

    private void doScan() {
        if (!BedrockScanner.isInNether()) {
            status = "Must be in the Nether!"; statusColor = 0xFFFFAA00; return;
        }
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        int cx = (int) Math.floor(p.getX()), cz = (int) Math.floor(p.getZ());
        display = BedrockScanner.scan(cx, cz, radius);
        status = "Scanned at X=" + cx + " Z=" + cz; statusColor = 0xFF44FF44;
    }

    private void doFind() {
        status = "Searching with seed=" + seed + "..."; statusColor = 0xFFFFAA00;
        int[][] pat = display;
        if (pat == null || pat.length == 0) {
            status = "Scan first!"; return;
        }
        int[] res = BedrockAnalyzer.findCoordinates(seed, pat, -10000, 10000, -10000, 10000, radius);
        if (res != null) {
            status = "FOUND: X=" + res[0] + " Z=" + res[1]; statusColor = 0xFF44FF44;
        } else {
            status = "No match in -10000..10000 range."; statusColor = 0xFFFF4444;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xDD101018);
        drawGrid(g);
        // Info panel
        int px = width / 2 + (radius * 2 + 1) * getCellSize() / 2 + 20;
        g.drawString(font, Component.literal("Bedrock Locator v1.0"), px, 20, 0xFFFFD700);
        g.drawString(font, Component.literal("Seed: " + seed), px, 40, 0xFFCCCCCC);
        g.drawString(font, Component.literal("Press +/- to change seed"), px, 55, 0xFF888888);
        g.drawString(font, Component.literal(status), px, 75, statusColor);
        Player p = Minecraft.getInstance().player;
        if (p != null) {
            g.drawString(font, Component.literal("X=" + (int)Math.floor(p.getX())
                + " Y=" + (int)Math.floor(p.getY()) + " Z=" + (int)Math.floor(p.getZ())),
                px, 100, 0xFF44FF44);
        }
        super.render(g, mx, my, dt);
    }

    private int getCellSize() {
        int s = radius * 2 + 1;
        return Math.max(4, (height - 80) / s);
    }

    private void drawGrid(GuiGraphics g) {
        int s = radius * 2 + 1;
        int cs = getCellSize();
        int gx = (width / 2) - (cs * s / 2), gy = (height - cs * s) / 2;
        g.fill(gx - 2, gy - 2, gx + cs * s + 2, gy + cs * s + 2, 0xFF4444AA);
        for (int dx = 0; dx < s; dx++) {
            for (int dz = 0; dz < s; dz++) {
                int v = (display != null && display.length == s) ? display[dx][dz] : 0;
                int col = v == 123 ? 0xFF1133AA : v == 124 ? 0xFF2255CC : v == 125 ? 0xFF4477EE
                        : v == 126 ? 0xFF7799FF : v == 127 ? 0xFFBBDDFF : 0xFF222233;
                g.fill(gx + dx*cs + 1, gy + dz*cs + 1, gx + dx*cs + cs - 1, gy + dz*cs + cs - 1, col);
            }
        }
        // Centre dot
        g.fill(gx + (s/2)*cs + cs/2 - 2, gy + (s/2)*cs + cs/2 - 2,
               gx + (s/2)*cs + cs/2 + 2, gy + (s/2)*cs + cs/2 + 2, 0xFFFF3333);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256) { onClose(); return true; }
        if (key == 334) { seed += 1; return true; }  // numpad +
        if (key == 333) { seed -= 1; return true; }  // numpad -
        return super.keyPressed(key, scan, mod);
    }

    @Override public boolean isPauseScreen() { return false; }
}
