package com.bedrocklocator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BedrockLocatorScreen extends Screen {

    private static final int BG      = 0xDD101018;
    private static final int PANEL   = 0xFF1A1A2E;
    private static final int BORDER  = 0xFF4444AA;
    private static final int GOLD    = 0xFFFFD700;
    private static final int GRAY    = 0xFFCCCCCC;
    private static final int GREEN   = 0xFF44FF44;
    private static final int ORANGE  = 0xFFFFAA00;
    private static final int RED     = 0xFFFF4444;
    private static final int H123    = 0xFF1133AA;
    private static final int H124    = 0xFF2255CC;
    private static final int H125    = 0xFF4477EE;
    private static final int H126    = 0xFF7799FF;
    private static final int H127    = 0xFFBBDDFF;
    private static final int EMPTY   = 0xFF222233;
    private static final int PDOT    = 0xFFFF3333;

    private int[][] scanned, manual, display;
    private boolean useManual = false;
    private long seed = 0L;
    private String status = "Enter seed and Scan, or draw a pattern.";
    private int statusColor = GRAY;
    private boolean busy = false;
    private int[] found = null;
    private int radius = 8;

    private EditBox seedBox, radiusBox, rangeBox;
    private int gx, gy, cs, pw, px, py;

    public BedrockLocatorScreen() {
        super(Component.literal("Bedrock Locator"));
    }

    @Override
    protected void init() {
        super.init();
        initArrays();
        layout();

        seedBox = new EditBox(font, px + 8, py + 48, pw - 16, 18, Component.literal("Seed"));
        seedBox.setMaxLength(24);
        seedBox.setValue("0");
        seedBox.setResponder(s -> {
            try { seed = Long.parseLong(s.trim()); }
            catch (NumberFormatException ignored) {
                if (!s.isBlank()) seed = (long) s.trim().hashCode();
            }
        });
        addRenderableWidget(seedBox);

        radiusBox = new EditBox(font, px + 8, py + 96, 55, 18, Component.literal("R"));
        radiusBox.setMaxLength(2);
        radiusBox.setValue(String.valueOf(radius));
        addRenderableWidget(radiusBox);

        rangeBox = new EditBox(font, px + 8, py + 145, pw - 16, 18, Component.literal("Range"));
        rangeBox.setMaxLength(50);
        rangeBox.setValue("-100000,100000,-100000,100000");
        addRenderableWidget(rangeBox);

        int hw = (pw - 20) / 2;
        addRenderableWidget(Button.builder(Component.literal("Scan Nether"),
            b -> doScan()).bounds(px + 8, py + 172, hw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Find Coords"),
            b -> doFind()).bounds(px + 12 + hw, py + 172, hw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear Draw"),
            b -> clearDraw()).bounds(px + 8, py + 197, hw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Toggle Mode"),
            b -> { useManual = !useManual; updateDisplay(); })
            .bounds(px + 12 + hw, py + 197, hw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"),
            b -> onClose()).bounds(px + 8, height - 32, pw - 16, 20).build());
    }

    private void initArrays() {
        int s = radius * 2 + 1;
        scanned = new int[s][s];
        manual  = new int[s][s];
        display = new int[s][s];
    }

    private void layout() {
        int s = radius * 2 + 1;
        int gh = height - 60;
        cs = Math.max(4, gh / s);
        gx = 14; gy = (height - cs * s) / 2;
        px = gx + cs * s + 16; py = 12;
        pw = Math.max(200, width - px - 12);
    }

    private void doScan() {
        if (!BedrockScanner.isInNether()) {
            status = "Need to be in the Nether!"; statusColor = ORANGE; return;
        }
        try {
            int r = Integer.parseInt(radiusBox.getValue().trim());
            if (r >= 2 && r <= 20) { radius = r; initArrays(); layout(); }
        } catch (NumberFormatException ignored) {}
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        int cx = (int)Math.floor(p.getX()), cz = (int)Math.floor(p.getZ());
        scanned = BedrockScanner.scan(cx, cz, radius);
        useManual = false; updateDisplay();
        int n = 0; for (int[] row : scanned) for (int v : row) if (v > 0) n++;
        status = "Scanned (" + cx + "," + cz + ") — " + n + " cells"; statusColor = GREEN;
        found = null;
    }

    private void doFind() {
        int[][] pat = useManual ? manual : scanned;
        if (pat == null || pat.length == 0) {
            status = "Scan first or draw a pattern."; statusColor = ORANGE; return;
        }
        int x0 = -100000, x1 = 100000, z0 = -100000, z1 = 100000;
        try {
            String[] p = rangeBox.getValue().split(",");
            if (p.length == 4) {
                x0 = Integer.parseInt(p[0].trim()); x1 = Integer.parseInt(p[1].trim());
                z0 = Integer.parseInt(p[2].trim()); z1 = Integer.parseInt(p[3].trim());
            }
        } catch (NumberFormatException ignored) {}
        status = "Searching..."; statusColor = ORANGE; busy = true; found = null;
        final long s = seed; final int[][] pp = pat; final int r = radius;
        final int fx0=x0, fx1=x1, fz0=z0, fz1=z1;
        CompletableFuture.runAsync(() -> {
            int[] res = BedrockAnalyzer.findCoordinates(s, pp, fx0, fx1, fz0, fz1, r);
            Minecraft.getInstance().execute(() -> {
                busy = false;
                if (res != null) {
                    found = res;
                    status = "FOUND: X=" + res[0] + "  Z=" + res[1]; statusColor = GREEN;
                } else {
                    status = "No match in that range."; statusColor = RED;
                }
            });
        });
    }

    private void clearDraw() {
        int s = radius * 2 + 1;
        manual = new int[s][s];
        useManual = true; updateDisplay();
        status = "Draw — click cells to cycle 123..127"; statusColor = GRAY; found = null;
    }

    private void updateDisplay() {
        int s = radius * 2 + 1;
        int[][] src = useManual ? manual : scanned;
        if (src == null || src.length != s) return;
        for (int i = 0; i < s; i++) System.arraycopy(src[i], 0, display[i], 0, s);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int s = radius * 2 + 1, gw = cs * s, gh = cs * s;
        if (mx >= gx && mx < gx + gw && my >= gy && my < gy + gh) {
            int dx = (int)(mx - gx) / cs, dz = (int)(my - gy) / cs;
            if (dx >= 0 && dx < s && dz >= 0 && dz < s) {
                useManual = true;
                int cur = manual[dx][dz];
                manual[dx][dz] = (cur == 0) ? 123 : (cur >= 127) ? 0 : cur + 1;
                updateDisplay(); return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, BG);
        drawGrid(g);
        drawPanel(g);
        super.render(g, mx, my, dt);
    }

    private void drawGrid(GuiGraphics g) {
        int s = radius * 2 + 1, gw = cs * s, gh = cs * s;
        g.fill(gx - 2, gy - 2, gx + gw + 2, gy + gh + 2, BORDER);
        g.fill(gx, gy, gx + gw, gy + gh, 0xFF080810);
        if (display != null && display.length == s) {
            for (int dx = 0; dx < s; dx++) {
                for (int dz = 0; dz < s; dz++) {
                    int x0 = gx + dx * cs, y0 = gy + dz * cs;
                    g.fill(x0 + 1, y0 + 1, x0 + cs - 1, y0 + cs - 1, hcol(display[dx][dz]));
                    if (cs >= 16 && display[dx][dz] > 0) {
                        g.drawString(font, Component.literal(String.valueOf(display[dx][dz])),
                            x0 + 2, y0 + cs / 2 - 4, 0xFFFFFFFF, false);
                    }
                }
            }
        }
        // Player dot
        g.fill(gx + (s/2)*cs + cs/2 - 2, gy + (s/2)*cs + cs/2 - 2,
               gx + (s/2)*cs + cs/2 + 2, gy + (s/2)*cs + cs/2 + 2, PDOT);
        g.drawString(font, Component.literal(useManual ? "DRAW MODE" : "SCAN MODE"),
            gx, gy - 12, useManual ? ORANGE : GREEN, false);
    }

    private int hcol(int h) {
        return switch (h) {
            case 123 -> H123; case 124 -> H124; case 125 -> H125;
            case 126 -> H126; case 127 -> H127; default -> EMPTY;
        };
    }

    private void drawPanel(GuiGraphics g) {
        g.fill(px - 3, py - 3, px + pw + 3, height - 8, BORDER);
        g.fill(px, py, px + pw, height - 11, PANEL);
        int tx = px + 8, ty = py + 8;
        g.drawString(font, Component.literal("Bedrock Locator v1.0"), tx, ty, GOLD, false);
        ty += 14;
        g.drawString(font, Component.literal("Nether ceiling coord finder"), tx, ty, 0xFF8888BB, false);
        ty += 16;
        g.drawString(font, Component.literal("World Seed:"), tx, ty, GRAY, false);
        ty += 30; g.drawString(font, Component.literal("Radius (2-20):"), tx, ty, GRAY, false);
        ty += 30; g.drawString(font, Component.literal("Search range:"), tx, ty, GRAY, false);
        ty += 12; g.drawString(font, Component.literal("minX,maxX,minZ,maxZ"), tx, ty, 0xFF666688, false);
        int ry = py + 228;
        g.fill(px + 4, ry, px + pw - 4, ry + 56, 0xFF080815);
        if (busy) {
            g.drawString(font, Component.literal("Searching..."), px + 8, ry + 5, ORANGE, false);
        } else {
            List<String> lines = wrap(status, pw - 16);
            for (int i = 0; i < Math.min(lines.size(), 4); i++)
                g.drawString(font, Component.literal(lines.get(i)), px + 8, ry + 5 + i * 12, statusColor, false);
        }
        Player pl = Minecraft.getInstance().player;
        if (pl != null) {
            int iy = py + 296;
            g.drawString(font, Component.literal("Position:"), tx, iy, GRAY, false);
            g.drawString(font, Component.literal(
                "X " + (int)Math.floor(pl.getX()) + "  Y " + (int)Math.floor(pl.getY())
                + "  Z " + (int)Math.floor(pl.getZ())), tx, iy + 13, GREEN, false);
            boolean nether = BedrockScanner.isInNether();
            g.drawString(font, Component.literal(nether ? "IN NETHER ✓" : "NOT IN NETHER"),
                tx, iy + 27, nether ? GREEN : ORANGE, false);
        }
        int tiy = py + 342;
        g.drawString(font, Component.literal("How to use:"), tx, tiy, GOLD, false);
        String[] tips = {"1. Go near y=127 in Nether",
            "2. Enter world seed", "3. Click Scan Nether",
            "4. Click Find Coords", "OR draw pattern manually"};
        for (int i = 0; i < tips.length; i++)
            g.drawString(font, Component.literal(tips[i]), tx, tiy + 13 + i * 11, GRAY, false);
    }

    private List<String> wrap(String t, int maxW) {
        List<String> out = new ArrayList<>();
        if (t == null || t.isEmpty()) return out;
        StringBuilder cur = new StringBuilder();
        for (String w : t.split(" ")) {
            String test = cur.isEmpty() ? w : cur + " " + w;
            if (font.width(test) > maxW && !cur.isEmpty()) {
                out.add(cur.toString()); cur = new StringBuilder(w);
            } else cur = new StringBuilder(test);
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean keyPressed(int k, int s, int m) {
        if (k == 256) { onClose(); return true; }
        return super.keyPressed(k, s, m);
    }
}
