package com.bedrocklocator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BedrockLocatorScreen extends Screen {

    // ── Colours ─────────────────────────────────────────────────────────────
    private static final int COL_BG        = 0xDD101018;
    private static final int COL_PANEL     = 0xFF1A1A2E;
    private static final int COL_BORDER    = 0xFF4444AA;
    private static final int COL_TITLE     = 0xFFFFD700;
    private static final int COL_TEXT      = 0xFFCCCCCC;
    private static final int COL_SUCCESS   = 0xFF44FF44;
    private static final int COL_WARN      = 0xFFFFAA00;
    private static final int COL_ERROR     = 0xFFFF4444;
    private static final int COL_BUTTON    = 0xFF2244AA;
    private static final int COL_BTN_HOV   = 0xFF3366CC;
    private static final int COL_SCAN_123  = 0xFF2255CC;   // low ceiling
    private static final int COL_SCAN_124  = 0xFF3377DD;
    private static final int COL_SCAN_125  = 0xFF5599EE;
    private static final int COL_SCAN_126  = 0xFF88BBFF;
    private static final int COL_SCAN_127  = 0xFFCCEEFF;   // high ceiling
    private static final int COL_UNKNOWN   = 0xFF333333;
    private static final int COL_PLAYER    = 0xFFFF4444;
    private static final int COL_GRID_LINE = 0xFF333355;

    // ── State ────────────────────────────────────────────────────────────────
    private int[][] scannedPattern;   // live scan
    private int[][] manualPattern;    // manually drawn
    private int[][] displayPattern;   // what we're showing
    private boolean useManual = false;

    private long worldSeed = 0L;
    private boolean seedValid = false;
    private String resultText = "";
    private int resultColor = COL_TEXT;
    private boolean searching = false;
    private int[] foundCoords = null;

    // Search range
    private int searchMinX = -100000, searchMaxX = 100000;
    private int searchMinZ = -100000, searchMaxZ = 100000;
    private int SCAN_RADIUS = 8;  // 17x17 grid default

    // Widgets
    private EditBox seedBox;
    private EditBox searchRangeBox;   // "minX,maxX,minZ,maxZ"
    private EditBox radiusBox;

    // Layout
    private int gridLeft, gridTop, gridCellSize;
    private int panelLeft, panelTop, panelW, panelH;

    public BedrockLocatorScreen() {
        super(Component.literal("Bedrock Locator"));
    }

    @Override
    protected void init() {
        super.init();
        recalcLayout();

        // Seed box
        seedBox = new EditBox(font, panelLeft + 10, panelTop + 60, panelW - 20, 18,
            Component.literal("World Seed"));
        seedBox.setMaxLength(25);
        seedBox.setHint(Component.literal("Enter world seed..."));
        seedBox.setValue("0");
        seedBox.setResponder(this::onSeedChanged);
        addRenderableWidget(seedBox);
        onSeedChanged("0");

        // Radius box
        radiusBox = new EditBox(font, panelLeft + 10, panelTop + 110, 60, 18,
            Component.literal("Radius"));
        radiusBox.setMaxLength(3);
        radiusBox.setValue(String.valueOf(SCAN_RADIUS));
        radiusBox.setHint(Component.literal("8"));
        addRenderableWidget(radiusBox);

        // Search range box
        searchRangeBox = new EditBox(font, panelLeft + 10, panelTop + 155, panelW - 20, 18,
            Component.literal("Search range"));
        searchRangeBox.setMaxLength(50);
        searchRangeBox.setValue("-100000,100000,-100000,100000");
        addRenderableWidget(searchRangeBox);

        // Scan button
        addRenderableWidget(Button.builder(Component.literal("Scan Nether"), btn -> doScan())
            .bounds(panelLeft + 10, panelTop + 190, (panelW - 25) / 2, 20)
            .build());

        // Find coords button
        addRenderableWidget(Button.builder(Component.literal("Find Coords"), btn -> doSearch())
            .bounds(panelLeft + 10 + (panelW - 25) / 2 + 5, panelTop + 190, (panelW - 25) / 2, 20)
            .build());

        // Clear manual button
        addRenderableWidget(Button.builder(Component.literal("Clear Draw"), btn -> clearManual())
            .bounds(panelLeft + 10, panelTop + 215, (panelW - 25) / 2, 20)
            .build());

        // Toggle mode button
        addRenderableWidget(Button.builder(Component.literal(useManual ? "Using: Draw" : "Using: Scan"),
            btn -> {
                useManual = !useManual;
                btn.setMessage(Component.literal(useManual ? "Using: Draw" : "Using: Scan"));
                updateDisplay();
            })
            .bounds(panelLeft + 10 + (panelW - 25) / 2 + 5, panelTop + 215, (panelW - 25) / 2, 20)
            .build());

        // Close
        addRenderableWidget(Button.builder(Component.literal("Close [ESC]"), btn -> onClose())
            .bounds(panelLeft + 10, panelTop + panelH - 30, panelW - 20, 20)
            .build());

        // Init empty patterns
        int size = SCAN_RADIUS * 2 + 1;
        scannedPattern = new int[size][size];
        manualPattern  = new int[size][size];
        displayPattern = new int[size][size];
        recalcLayout();
    }

    private void recalcLayout() {
        int gridSize = Math.min(width - 320, height - 40);
        gridSize = Math.max(200, gridSize);
        gridLeft = 20;
        gridTop  = (height - gridSize) / 2;

        int size = SCAN_RADIUS * 2 + 1;
        gridCellSize = Math.max(4, gridSize / size);
        int actualGridW = gridCellSize * size;

        panelW = Math.min(280, width - actualGridW - 50);
        panelH = height - 40;
        panelLeft = gridLeft + actualGridW + 20;
        panelTop  = 20;
    }

    private void onSeedChanged(String val) {
        try {
            // Handle named seeds like "2b2t" — use string's hashCode
            worldSeed = parseSeed(val.trim());
            seedValid = true;
            if (displayPattern != null && displayPattern.length > 0) {
                updateDisplay();
            }
        } catch (NumberFormatException e) {
            seedValid = false;
        }
    }

    private long parseSeed(String s) {
        if (s.isEmpty()) return 0L;
        try { return Long.parseLong(s); }
        catch (NumberFormatException e) { return (long) s.hashCode(); }
    }

    private void doScan() {
        if (!BedrockScanner.isInNether()) {
            resultText = "Not in Nether! Scan only works in the Nether.";
            resultColor = COL_WARN;
            return;
        }

        // Update radius from box
        try {
            int r = Integer.parseInt(radiusBox.getValue().trim());
            if (r >= 2 && r <= 24) {
                SCAN_RADIUS = r;
                scannedPattern = new int[SCAN_RADIUS * 2 + 1][SCAN_RADIUS * 2 + 1];
                manualPattern  = new int[SCAN_RADIUS * 2 + 1][SCAN_RADIUS * 2 + 1];
                recalcLayout();
            }
        } catch (NumberFormatException ignored) {}

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        int cx = (int) Math.floor(player.getX());
        int cz = (int) Math.floor(player.getZ());

        scannedPattern = BedrockScanner.scan(cx, cz, SCAN_RADIUS);
        useManual = false;
        updateDisplay();

        int nonZero = 0;
        for (int[] row : scannedPattern) for (int v : row) if (v > 0) nonZero++;
        resultText = "Scanned at (" + cx + ", " + cz + ") — " + nonZero + " cells loaded";
        resultColor = COL_SUCCESS;
        foundCoords = null;
    }

    private void doSearch() {
        if (!seedValid) {
            resultText = "Invalid seed!";
            resultColor = COL_ERROR;
            return;
        }
        int[][] pattern = useManual ? manualPattern : scannedPattern;
        if (pattern == null || pattern.length == 0) {
            resultText = "No pattern to search! Scan first or draw a pattern.";
            resultColor = COL_WARN;
            return;
        }

        // Parse search range
        try {
            String[] parts = searchRangeBox.getValue().split(",");
            if (parts.length == 4) {
                searchMinX = Integer.parseInt(parts[0].trim());
                searchMaxX = Integer.parseInt(parts[1].trim());
                searchMinZ = Integer.parseInt(parts[2].trim());
                searchMaxZ = Integer.parseInt(parts[3].trim());
            }
        } catch (NumberFormatException ignored) {}

        resultText = "Searching... (this may take a moment)";
        resultColor = COL_WARN;
        searching = true;
        foundCoords = null;

        final long seed = worldSeed;
        final int[][] pat = pattern;
        final int radius = SCAN_RADIUS;
        final int minX = searchMinX, maxX = searchMaxX;
        final int minZ = searchMinZ, maxZ = searchMaxZ;

        CompletableFuture.runAsync(() -> {
            int[] result = BedrockAnalyzer.findCoordinates(seed, pat, minX, maxX, minZ, maxZ, radius);
            Minecraft.getInstance().execute(() -> {
                searching = false;
                if (result != null) {
                    foundCoords = result;
                    resultText = "FOUND: X=" + result[0] + "  Z=" + result[1];
                    resultColor = COL_SUCCESS;
                } else {
                    resultText = "No match in range [" + minX + ".." + maxX + "] x [" + minZ + ".." + maxZ + "]";
                    resultColor = COL_ERROR;
                }
            });
        });
    }

    private void clearManual() {
        int size = SCAN_RADIUS * 2 + 1;
        manualPattern = new int[size][size];
        useManual = true;
        updateDisplay();
        resultText = "Draw mode — click grid cells to cycle height (123-127)";
        resultColor = COL_TEXT;
        foundCoords = null;
    }

    private void updateDisplay() {
        if (displayPattern == null) return;
        int size = SCAN_RADIUS * 2 + 1;
        if (displayPattern.length != size) {
            displayPattern = new int[size][size];
        }
        int[][] src = useManual ? manualPattern : scannedPattern;
        if (src != null && src.length == size) {
            for (int i = 0; i < size; i++)
                System.arraycopy(src[i], 0, displayPattern[i], 0, size);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle grid clicks for manual drawing
        if (isMouseOnGrid(mouseX, mouseY)) {
            int size = SCAN_RADIUS * 2 + 1;
            int dx = ((int) mouseX - gridLeft) / gridCellSize;
            int dz = ((int) mouseY - gridTop)  / gridCellSize;
            if (dx >= 0 && dx < size && dz >= 0 && dz < size) {
                useManual = true;
                int cur = manualPattern[dx][dz];
                // Cycle: 0 (unknown) -> 123 -> 124 -> 125 -> 126 -> 127 -> 0
                manualPattern[dx][dz] = (cur == 0) ? 123 : (cur >= 127) ? 0 : cur + 1;
                updateDisplay();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseOnGrid(double mx, double my) {
        int size = SCAN_RADIUS * 2 + 1;
        int gridW = gridCellSize * size;
        int gridH = gridCellSize * size;
        return mx >= gridLeft && mx < gridLeft + gridW && my >= gridTop && my < gridTop + gridH;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Background
        g.fill(0, 0, width, height, COL_BG);

        // Draw grid
        renderGrid(g, mouseX, mouseY);

        // Draw right panel
        renderPanel(g);

        // Draw widgets on top
        super.render(g, mouseX, mouseY, delta);
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        int size = SCAN_RADIUS * 2 + 1;
        int gridW = gridCellSize * size;
        int gridH = gridCellSize * size;

        // Grid background
        g.fill(gridLeft - 2, gridTop - 2, gridLeft + gridW + 2, gridTop + gridH + 2, COL_BORDER);
        g.fill(gridLeft, gridTop, gridLeft + gridW, gridTop + gridH, 0xFF0A0A14);

        // Cells
        if (displayPattern != null && displayPattern.length == size) {
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    int x0 = gridLeft + dx * gridCellSize;
                    int y0 = gridTop  + dz * gridCellSize;
                    int val = displayPattern[dx][dz];
                    int col = heightToColor(val);
                    g.fill(x0 + 1, y0 + 1, x0 + gridCellSize - 1, y0 + gridCellSize - 1, col);

                    // Height label (if cell big enough)
                    if (gridCellSize >= 14 && val > 0) {
                        g.drawString(font, String.valueOf(val),
                            x0 + gridCellSize / 2 - 7, y0 + gridCellSize / 2 - 4,
                            0xFFFFFFFF, false);
                    }
                }
            }
        }

        // Grid lines every 8 cells
        for (int i = 0; i <= size; i++) {
            if (i % 8 == 0) {
                g.fill(gridLeft + i * gridCellSize, gridTop,
                       gridLeft + i * gridCellSize + 1, gridTop + gridH, COL_GRID_LINE);
                g.fill(gridLeft, gridTop + i * gridCellSize,
                       gridLeft + gridW, gridTop + i * gridCellSize + 1, COL_GRID_LINE);
            }
        }

        // Player dot at centre
        int cx = gridLeft + (size / 2) * gridCellSize + gridCellSize / 2;
        int cy = gridTop  + (size / 2) * gridCellSize + gridCellSize / 2;
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, COL_PLAYER);

        // Found coords marker
        if (foundCoords != null && !useManual && !searching) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                int px = (int) Math.floor(player.getX());
                int pz = (int) Math.floor(player.getZ());
                int relX = foundCoords[0] - px;
                int relZ = foundCoords[1] - pz;
                int mx2 = cx + relX * gridCellSize;
                int mz2 = cy + relZ * gridCellSize;
                g.fill(mx2 - 3, mz2 - 3, mx2 + 3, mz2 + 3, 0xFFFFFF00);
            }
        }

        // Legend
        int lx = gridLeft;
        int ly = gridTop + gridH + 6;
        g.drawString(font, "Height:", lx, ly, COL_TEXT, false);
        int[] heights = {123, 124, 125, 126, 127};
        for (int i = 0; i < 5; i++) {
            g.fill(lx + 45 + i * 20, ly, lx + 45 + i * 20 + 14, ly + 10, heightToColor(heights[i]));
            g.drawString(font, String.valueOf(heights[i]), lx + 45 + i * 20, ly + 12, COL_TEXT, false);
        }

        // Grid title
        g.drawString(font,
            useManual ? "DRAW MODE — click cells to set height" : "SCAN MODE — live Nether data",
            gridLeft, gridTop - 14, useManual ? COL_WARN : COL_SUCCESS, false);
    }

    private int heightToColor(int h) {
        return switch (h) {
            case 123 -> COL_SCAN_123;
            case 124 -> COL_SCAN_124;
            case 125 -> COL_SCAN_125;
            case 126 -> COL_SCAN_126;
            case 127 -> COL_SCAN_127;
            default  -> COL_UNKNOWN;
        };
    }

    private void renderPanel(GuiGraphics g) {
        // Panel background
        g.fill(panelLeft - 4, panelTop - 4, panelLeft + panelW + 4, panelTop + panelH + 4, COL_BORDER);
        g.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, COL_PANEL);

        int tx = panelLeft + 10;
        int ty = panelTop + 10;

        // Title
        g.drawString(font, "Bedrock Locator", tx, ty, COL_TITLE, false);
        g.drawString(font, "v1.0.0", tx + 110, ty, COL_TEXT, false);

        ty += 14;
        g.drawString(font, "Uses Nether ceiling pattern to find coords", tx, ty, 0xFF8888AA, false);

        ty += 20;
        g.drawString(font, "World Seed:", tx, ty, COL_TEXT, false);
        ty += 12;
        // seedBox rendered by super.render

        ty += 30;
        g.drawString(font, "Scan radius (2-24):", tx, ty, COL_TEXT, false);
        ty += 12;
        // radiusBox rendered by super

        ty += 30;
        g.drawString(font, "Search range (minX,maxX,minZ,maxZ):", tx, ty, COL_TEXT, false);
        ty += 12;

        ty += 30;
        // Buttons at panelTop+190 and panelTop+215

        // Status / Result
        int ry = panelTop + 245;
        g.fill(panelLeft + 5, ry, panelLeft + panelW - 5, ry + 60, 0xFF0D0D1A);

        if (searching) {
            g.drawString(font, "Searching...", panelLeft + 10, ry + 5, COL_WARN, false);
        } else {
            // Word-wrap result text
            List<String> lines = wrapText(resultText, panelW - 20);
            for (int i = 0; i < lines.size() && i < 4; i++) {
                g.drawString(font, lines.get(i), panelLeft + 10, ry + 5 + i * 11, resultColor, false);
            }
        }

        // Player info
        Player player = Minecraft.getInstance().player;
        int py = panelTop + 315;
        if (player != null) {
            g.drawString(font, "Your pos:", tx, py, COL_TEXT, false);
            g.drawString(font,
                "X=" + (int)Math.floor(player.getX()) +
                " Y=" + (int)Math.floor(player.getY()) +
                " Z=" + (int)Math.floor(player.getZ()),
                tx, py + 12, COL_SUCCESS, false);
            g.drawString(font,
                BedrockScanner.isInNether() ? "Dimension: NETHER" : "Dimension: NOT NETHER",
                tx, py + 26,
                BedrockScanner.isInNether() ? COL_SUCCESS : COL_WARN, false);
        }

        // Usage tips
        int uty = panelTop + 365;
        g.drawString(font, "How to use:", tx, uty, COL_TITLE, false);
        String[] tips = {
            "1. Go to Nether ceiling (y~127)",
            "2. Enter world seed above",
            "3. Press Scan Nether to capture",
            "4. Press Find Coords to search",
            "--- OR ---",
            "5. Draw pattern manually (click grid)",
            "6. Then Find Coords to match"
        };
        for (int i = 0; i < tips.length; i++) {
            if (uty + 14 + i * 11 < panelTop + panelH - 35) {
                g.drawString(font, tips[i], tx, uty + 14 + i * 11, COL_TEXT, false);
            }
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (font.width(test) > maxWidth && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(w);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256) { onClose(); return true; } // ESC
        return super.keyPressed(key, scan, mod);
    }
}
