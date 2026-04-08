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

    // Colours
    private static final int COL_BG       = 0xDD101018;
    private static final int COL_PANEL    = 0xFF1A1A2E;
    private static final int COL_BORDER   = 0xFF4444AA;
    private static final int COL_TITLE    = 0xFFFFD700;
    private static final int COL_TEXT     = 0xFFCCCCCC;
    private static final int COL_SUCCESS  = 0xFF44FF44;
    private static final int COL_WARN     = 0xFFFFAA00;
    private static final int COL_ERROR    = 0xFFFF4444;
    private static final int COL_H123     = 0xFF1133AA;
    private static final int COL_H124     = 0xFF2255CC;
    private static final int COL_H125     = 0xFF4477EE;
    private static final int COL_H126     = 0xFF7799FF;
    private static final int COL_H127     = 0xFFBBDDFF;
    private static final int COL_UNKNOWN  = 0xFF222233;
    private static final int COL_PLAYER   = 0xFFFF3333;

    // State
    private int[][] scannedPattern;
    private int[][] manualPattern;
    private int[][] displayPattern;
    private boolean useManual = false;
    private long worldSeed = 0L;
    private String resultText = "Enter seed and press Scan or draw a pattern.";
    private int resultColor = COL_TEXT;
    private boolean searching = false;
    private int[] foundCoords = null;
    private int SCAN_RADIUS = 8;
    private int searchMinX = -100000, searchMaxX = 100000;
    private int searchMinZ = -100000, searchMaxZ = 100000;

    // Widgets
    private EditBox seedBox;
    private EditBox radiusBox;
    private EditBox searchRangeBox;

    // Layout
    private int gridLeft, gridTop, cellSize;
    private int panelLeft, panelTop, panelW;

    public BedrockLocatorScreen() {
        super(Component.literal("Bedrock Locator"));
    }

    @Override
    protected void init() {
        super.init();
        int size = SCAN_RADIUS * 2 + 1;
        scannedPattern = new int[size][size];
        manualPattern  = new int[size][size];
        displayPattern = new int[size][size];
        calcLayout();

        // World seed input
        seedBox = new EditBox(font, panelLeft + 8, panelTop + 48, panelW - 16, 18,
            Component.literal("Seed"));
        seedBox.setMaxLength(24);
        seedBox.setValue("0");
        seedBox.setSuggestion("Enter world seed...");
        seedBox.setResponder(s -> {
            try { worldSeed = Long.parseLong(s.trim()); }
            catch (NumberFormatException e) {
                if (!s.isBlank()) worldSeed = (long) s.trim().hashCode();
            }
        });
        addRenderableWidget(seedBox);

        // Radius input
        radiusBox = new EditBox(font, panelLeft + 8, panelTop + 96, 60, 18,
            Component.literal("Radius"));
        radiusBox.setMaxLength(2);
        radiusBox.setValue("8");
        radiusBox.setSuggestion("8");
        addRenderableWidget(radiusBox);

        // Search range input
        searchRangeBox = new EditBox(font, panelLeft + 8, panelTop + 148, panelW - 16, 18,
            Component.literal("Range"));
        searchRangeBox.setMaxLength(50);
        searchRangeBox.setValue("-100000,100000,-100000,100000");
        addRenderableWidget(searchRangeBox);

        // Scan button
        addRenderableWidget(Button.builder(Component.literal("Scan Nether"), b -> doScan())
            .bounds(panelLeft + 8, panelTop + 175, (panelW - 20) / 2, 20).build());

        // Find coords button
        addRenderableWidget(Button.builder(Component.literal("Find Coords"), b -> doSearch())
            .bounds(panelLeft + 12 + (panelW - 20) / 2, panelTop + 175, (panelW - 20) / 2, 20).build());

        // Clear / Mode toggle
        addRenderableWidget(Button.builder(Component.literal("Clear Draw"), b -> clearManual())
            .bounds(panelLeft + 8, panelTop + 200, (panelW - 20) / 2, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Toggle Mode"), b -> {
            useManual = !useManual;
            updateDisplay();
        }).bounds(panelLeft + 12 + (panelW - 20) / 2, panelTop + 200, (panelW - 20) / 2, 20).build());

        // Close
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(panelLeft + 8, height - 35, panelW - 16, 20).build());
    }

    private void calcLayout() {
        int size = SCAN_RADIUS * 2 + 1;
        int gridH = height - 60;
        cellSize  = Math.max(4, gridH / size);
        int gridW = cellSize * size;
        gridLeft  = 15;
        gridTop   = (height - cellSize * size) / 2;
        panelLeft = gridLeft + gridW + 18;
        panelTop  = 15;
        panelW    = Math.max(200, width - panelLeft - 15);
    }

    private void doScan() {
        if (!BedrockScanner.isInNether()) {
            resultText  = "Not in Nether! Travel to the Nether first.";
            resultColor = COL_WARN;
            return;
        }
        try {
            int r = Integer.parseInt(radiusBox.getValue().trim());
            if (r >= 2 && r <= 20) {
                SCAN_RADIUS = r;
                int s = r * 2 + 1;
                scannedPattern = new int[s][s];
                manualPattern  = new int[s][s];
                displayPattern = new int[s][s];
                calcLayout();
            }
        } catch (NumberFormatException ignored) {}

        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        int cx = (int) Math.floor(p.getX());
        int cz = (int) Math.floor(p.getZ());
        scannedPattern = BedrockScanner.scan(cx, cz, SCAN_RADIUS);
        useManual = false;
        updateDisplay();
        int loaded = 0;
        for (int[] row : scannedPattern) for (int v : row) if (v > 0) loaded++;
        resultText  = "Scanned (" + cx + ", " + cz + ") — " + loaded + " cells loaded";
        resultColor = COL_SUCCESS;
        foundCoords = null;
    }

    private void doSearch() {
        int[][] pat = useManual ? manualPattern : scannedPattern;
        if (pat == null || pat.length == 0) {
            resultText = "No pattern — scan the Nether or draw manually.";
            resultColor = COL_WARN;
            return;
        }
        try {
            String[] p = searchRangeBox.getValue().split(",");
            if (p.length == 4) {
                searchMinX = Integer.parseInt(p[0].trim());
                searchMaxX = Integer.parseInt(p[1].trim());
                searchMinZ = Integer.parseInt(p[2].trim());
                searchMaxZ = Integer.parseInt(p[3].trim());
            }
        } catch (NumberFormatException ignored) {}

        resultText  = "Searching... please wait";
        resultColor = COL_WARN;
        searching   = true;
        foundCoords = null;
        final long seed = worldSeed;
        final int[][] pattern = pat;
        final int radius = SCAN_RADIUS;
        final int x0 = searchMinX, x1 = searchMaxX, z0 = searchMinZ, z1 = searchMaxZ;
        CompletableFuture.runAsync(() -> {
            int[] res = BedrockAnalyzer.findCoordinates(seed, pattern, x0, x1, z0, z1, radius);
            Minecraft.getInstance().execute(() -> {
                searching = false;
                if (res != null) {
                    foundCoords = res;
                    resultText  = "FOUND: X=" + res[0] + "  Z=" + res[1];
                    resultColor = COL_SUCCESS;
                } else {
                    resultText  = "No match in range " + x0 + "," + x1 + " / " + z0 + "," + z1;
                    resultColor = COL_ERROR;
                }
            });
        });
    }

    private void clearManual() {
        int s = SCAN_RADIUS * 2 + 1;
        manualPattern = new int[s][s];
        useManual = true;
        updateDisplay();
        resultText  = "Draw mode — click cells to cycle height 123-127";
        resultColor = COL_TEXT;
        foundCoords = null;
    }

    private void updateDisplay() {
        int s = SCAN_RADIUS * 2 + 1;
        int[][] src = useManual ? manualPattern : scannedPattern;
        if (src == null || src.length != s) return;
        for (int i = 0; i < s; i++) System.arraycopy(src[i], 0, displayPattern[i], 0, s);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int size = SCAN_RADIUS * 2 + 1;
        int gridW = cellSize * size;
        int gridH = cellSize * size;
        if (mx >= gridLeft && mx < gridLeft + gridW && my >= gridTop && my < gridTop + gridH) {
            int dx = (int)(mx - gridLeft) / cellSize;
            int dz = (int)(my - gridTop)  / cellSize;
            if (dx >= 0 && dx < size && dz >= 0 && dz < size) {
                useManual = true;
                int cur = manualPattern[dx][dz];
                manualPattern[dx][dz] = (cur == 0) ? 123 : (cur >= 127) ? 0 : cur + 1;
                updateDisplay();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, COL_BG);
        renderGrid(g);
        renderPanel(g);
        super.render(g, mouseX, mouseY, delta);
    }

    private void renderGrid(GuiGraphics g) {
        int size = SCAN_RADIUS * 2 + 1;
        int gw = cellSize * size;
        int gh = cellSize * size;
        g.fill(gridLeft - 2, gridTop - 2, gridLeft + gw + 2, gridTop + gh + 2, COL_BORDER);
        g.fill(gridLeft, gridTop, gridLeft + gw, gridTop + gh, 0xFF080810);
        if (displayPattern != null && displayPattern.length == size) {
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    int x0 = gridLeft + dx * cellSize;
                    int y0 = gridTop  + dz * cellSize;
                    g.fill(x0 + 1, y0 + 1, x0 + cellSize - 1, y0 + cellSize - 1,
                        heightColor(displayPattern[dx][dz]));
                    if (cellSize >= 16 && displayPattern[dx][dz] > 0) {
                        g.drawString(font, String.valueOf(displayPattern[dx][dz]),
                            x0 + 2, y0 + (cellSize / 2) - 4, 0xFFFFFFFF, false);
                    }
                }
            }
        }
        // Player dot
        int px = gridLeft + (size / 2) * cellSize + cellSize / 2;
        int pz = gridTop  + (size / 2) * cellSize + cellSize / 2;
        g.fill(px - 2, pz - 2, px + 2, pz + 2, COL_PLAYER);
        // Label
        String modeLabel = useManual ? "DRAW — click grid cells" : "SCAN — live Nether data";
        g.drawString(font, modeLabel, gridLeft, gridTop - 12,
            useManual ? COL_WARN : COL_SUCCESS, false);
        // Legend
        g.drawString(font, "123  124  125  126  127",
            gridLeft, gridTop + gh + 6, COL_TEXT, false);
        int[] hs = {123, 124, 125, 126, 127};
        for (int i = 0; i < 5; i++) {
            g.fill(gridLeft + i * 40, gridTop + gh + 18,
                   gridLeft + i * 40 + 32, gridTop + gh + 28, heightColor(hs[i]));
        }
    }

    private int heightColor(int h) {
        return switch (h) {
            case 123 -> COL_H123;
            case 124 -> COL_H124;
            case 125 -> COL_H125;
            case 126 -> COL_H126;
            case 127 -> COL_H127;
            default  -> COL_UNKNOWN;
        };
    }

    private void renderPanel(GuiGraphics g) {
        g.fill(panelLeft - 3, panelTop - 3, panelLeft + panelW + 3, height - 10, COL_BORDER);
        g.fill(panelLeft, panelTop, panelLeft + panelW, height - 13, COL_PANEL);

        int tx = panelLeft + 8;
        int ty = panelTop + 8;
        g.drawString(font, "Bedrock Locator v1.0", tx, ty, COL_TITLE, false);
        ty += 12;
        g.drawString(font, "Find coords from Nether ceiling", tx, ty, 0xFF8888BB, false);

        ty += 18;
        g.drawString(font, "World Seed:", tx, ty, COL_TEXT, false);
        // seedBox at ty+12 (panelTop+48)

        ty += 30; // panelTop+78 approx
        g.drawString(font, "Scan Radius:", tx, ty, COL_TEXT, false);
        // radiusBox at ty+12

        ty += 30;
        g.drawString(font, "Search Range (minX,maxX,minZ,maxZ):", tx, ty, COL_TEXT, false);
        // searchRangeBox at ty+12

        // Result box
        int ry = panelTop + 230;
        g.fill(panelLeft + 4, ry, panelLeft + panelW - 4, ry + 55, 0xFF0A0A18);
        if (searching) {
            g.drawString(font, "Searching...", panelLeft + 8, ry + 5, COL_WARN, false);
        } else {
            List<String> lines = wrap(resultText, panelW - 16);
            for (int i = 0; i < Math.min(lines.size(), 4); i++) {
                g.drawString(font, lines.get(i), panelLeft + 8, ry + 5 + i * 12, resultColor, false);
            }
        }

        // Player position
        Player p = Minecraft.getInstance().player;
        if (p != null) {
            int iy = panelTop + 295;
            g.drawString(font, "Your position:", tx, iy, COL_TEXT, false);
            g.drawString(font,
                "X " + (int)Math.floor(p.getX()) +
                "  Y " + (int)Math.floor(p.getY()) +
                "  Z " + (int)Math.floor(p.getZ()),
                tx, iy + 12, COL_SUCCESS, false);
            g.drawString(font,
                BedrockScanner.isInNether() ? "NETHER" : "NOT IN NETHER",
                tx, iy + 26,
                BedrockScanner.isInNether() ? COL_SUCCESS : COL_WARN, false);
        }

        // Tips
        int tiy = panelTop + 340;
        g.drawString(font, "Usage:", tx, tiy, COL_TITLE, false);
        String[] tips = {"1. Go near Nether ceiling",
            "2. Enter world seed", "3. Click Scan Nether",
            "4. Click Find Coords", "OR: Draw pattern manually"};
        for (int i = 0; i < tips.length; i++) {
            g.drawString(font, tips[i], tx, tiy + 12 + i * 11, COL_TEXT, false);
        }
    }

    private List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        StringBuilder cur = new StringBuilder();
        for (String w : text.split(" ")) {
            String t = cur.isEmpty() ? w : cur + " " + w;
            if (font.width(t) > maxW && !cur.isEmpty()) {
                out.add(cur.toString()); cur = new StringBuilder(w);
            } else { cur = new StringBuilder(t); }
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256) { onClose(); return true; }
        return super.keyPressed(key, scan, mod);
    }
}
