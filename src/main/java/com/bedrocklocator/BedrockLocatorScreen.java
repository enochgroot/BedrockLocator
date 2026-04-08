package com.bedrocklocator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;

public class BedrockLocatorScreen extends Screen {

    private static final int BG     = 0xDD101018;
    private static final int PANEL  = 0xFF1A1A2E;
    private static final int BORDER = 0xFF4444AA;
    private static final int GOLD   = 0xFFFFD700;
    private static final int LGRAY  = 0xFFCCCCCC;
    private static final int GREEN  = 0xFF44FF44;
    private static final int ORANGE = 0xFFFFAA00;
    private static final int LRED   = 0xFFFF4444;

    private int[][] scanned, manual, display;
    private boolean useManual = false;
    private long seed = 0L;
    private String status = "Enter seed and Scan, or draw a pattern manually.";
    private int statusColor = LGRAY;
    private boolean busy = false;
    private int[] found = null;
    private int radius = 8;

    private EditBox seedBox, radiusBox, rangeBox;
    private int gx, gy, cs, panelX, panelW;

    public BedrockLocatorScreen() {
        super(Component.literal("Bedrock Locator"));
    }

    @Override
    protected void init() {
        super.init();
        initArrays(); calcLayout();

        seedBox = new EditBox(font, panelX + 8, 48, panelW - 16, 18, Component.literal("Seed"));
        seedBox.setMaxLength(24); seedBox.setValue("0");
        seedBox.setResponder(s -> {
            try { seed = Long.parseLong(s.trim()); }
            catch (NumberFormatException ignored) {
                if (!s.isBlank()) seed = (long) s.trim().hashCode();
            }
        });
        addRenderableWidget(seedBox);

        radiusBox = new EditBox(font, panelX + 8, 98, 55, 18, Component.literal("R"));
        radiusBox.setMaxLength(2); radiusBox.setValue(String.valueOf(radius));
        addRenderableWidget(radiusBox);

        rangeBox = new EditBox(font, panelX + 8, 148, panelW - 16, 18, Component.literal("Range"));
        rangeBox.setMaxLength(50); rangeBox.setValue("-10000,10000,-10000,10000");
        addRenderableWidget(rangeBox);

        int hw = (panelW - 20) / 2;
        addRenderableWidget(Button.builder(Component.literal("Scan Nether"), b -> doScan())
            .pos(panelX + 8, 175).size(hw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Find Coords"), b -> doFind())
            .pos(panelX + 12 + hw, 175).size(hw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear Draw"), b -> clearDraw())
            .pos(panelX + 8, 200).size(hw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Toggle Mode"), b -> {
            useManual = !useManual; updateDisplay();
        }).pos(panelX + 12 + hw, 200).size(hw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
            .pos(panelX + 8, height - 30).size(panelW - 16, 20).build());
    }

    private void initArrays() {
        int s = radius * 2 + 1;
        scanned = new int[s][s]; manual = new int[s][s]; display = new int[s][s];
    }

    private void calcLayout() {
        int s = radius * 2 + 1;
        cs = Math.max(4, (height - 60) / s);
        gx = 14; gy = (height - cs * s) / 2;
        panelX = gx + cs * s + 16;
        panelW = Math.max(200, width - panelX - 12);
    }

    private void doScan() {
        if (!BedrockScanner.isInNether()) {
            status = "Must be in the Nether!"; statusColor = ORANGE; return;
        }
        try {
            int r = Integer.parseInt(radiusBox.getValue().trim());
            if (r >= 2 && r <= 20) { radius = r; initArrays(); calcLayout(); }
        } catch (NumberFormatException ignored) {}
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        int cx = (int)Math.floor(p.getX()), cz = (int)Math.floor(p.getZ());
        scanned = BedrockScanner.scan(cx, cz, radius);
        useManual = false; updateDisplay();
        int n = 0; for (int[] row : scanned) for (int v : row) if (v > 0) n++;
        status = "Scanned (" + cx + "," + cz + ") — " + n + " cells loaded";
        statusColor = GREEN; found = null;
    }

    private void doFind() {
        int[][] pat = useManual ? manual : scanned;
        if (pat == null || pat.length == 0) {
            status = "Scan first or draw a pattern."; statusColor = ORANGE; return;
        }
        int x0=-10000,x1=10000,z0=-10000,z1=10000;
        try {
            String[] p = rangeBox.getValue().split(",");
            if (p.length == 4) {
                x0=Integer.parseInt(p[0].trim()); x1=Integer.parseInt(p[1].trim());
                z0=Integer.parseInt(p[2].trim()); z1=Integer.parseInt(p[3].trim());
            }
        } catch (NumberFormatException ignored) {}
        status="Searching..."; statusColor=ORANGE; busy=true; found=null;
        final long s=seed; final int[][] pp=pat; final int r=radius;
        final int fx0=x0,fx1=x1,fz0=z0,fz1=z1;
        CompletableFuture.runAsync(() -> {
            int[] res = BedrockAnalyzer.findCoordinates(s,pp,fx0,fx1,fz0,fz1,r);
            Minecraft.getInstance().execute(() -> {
                busy=false;
                if (res!=null) {
                    found=res; status="FOUND: X="+res[0]+"  Z="+res[1]; statusColor=GREEN;
                } else {
                    status="No match in that range."; statusColor=LRED;
                }
            });
        });
    }

    private void clearDraw() {
        int s = radius * 2 + 1; manual = new int[s][s];
        useManual=true; updateDisplay();
        status="Draw mode — click grid cells to cycle 123..127"; statusColor=LGRAY; found=null;
    }

    private void updateDisplay() {
        int s = radius*2+1;
        int[][] src = useManual ? manual : scanned;
        if (src==null||src.length!=s) return;
        for (int i=0;i<s;i++) System.arraycopy(src[i],0,display[i],0,s);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int s=radius*2+1;
        if (mx>=gx&&mx<gx+cs*s&&my>=gy&&my<gy+cs*s) {
            int dx=(int)(mx-gx)/cs, dz=(int)(my-gy)/cs;
            if (dx>=0&&dx<s&&dz>=0&&dz<s) {
                useManual=true;
                int cur=manual[dx][dz];
                manual[dx][dz]=(cur==0)?123:(cur>=127)?0:cur+1;
                updateDisplay(); return true;
            }
        }
        return super.mouseClicked(mx,my,btn);
    }

    // 1.21.11 render signature: (GuiGraphics, float) — no mouse params
    @Override
    public void render(GuiGraphics g, float delta) {
        g.fill(0,0,width,height,BG);
        drawGrid(g); drawPanel(g);
        super.render(g,delta);
    }

    private void drawGrid(GuiGraphics g) {
        int s=radius*2+1, gw=cs*s, gh=cs*s;
        g.fill(gx-2,gy-2,gx+gw+2,gy+gh+2,BORDER);
        g.fill(gx,gy,gx+gw,gy+gh,0xFF080810);
        if (display!=null&&display.length==s) {
            for (int dx=0;dx<s;dx++) for (int dz=0;dz<s;dz++) {
                int x0=gx+dx*cs, y0=gy+dz*cs;
                g.fill(x0+1,y0+1,x0+cs-1,y0+cs-1,hcol(display[dx][dz]));
                if (cs>=16&&display[dx][dz]>0)
                    g.drawString(font,Component.literal(String.valueOf(display[dx][dz])),
                        x0+2,y0+cs/2-4,0xFFFFFFFF);
            }
        }
        g.fill(gx+(s/2)*cs+cs/2-2,gy+(s/2)*cs+cs/2-2,
               gx+(s/2)*cs+cs/2+2,gy+(s/2)*cs+cs/2+2,0xFFFF3333);
        g.drawString(font,Component.literal(useManual?"DRAW MODE":"SCAN MODE"),
            gx,gy-12,useManual?ORANGE:GREEN);
    }

    private int hcol(int h) {
        return switch(h){case 123->0xFF1133AA;case 124->0xFF2255CC;case 125->0xFF4477EE;
            case 126->0xFF7799FF;case 127->0xFFBBDDFF;default->0xFF222233;};
    }

    private void drawPanel(GuiGraphics g) {
        g.fill(panelX-3,8,panelX+panelW+3,height-8,BORDER);
        g.fill(panelX,11,panelX+panelW,height-11,PANEL);
        int tx=panelX+8;
        g.drawString(font,Component.literal("Bedrock Locator v1.0"),tx,14,GOLD);
        g.drawString(font,Component.literal("Nether ceiling coord finder"),tx,27,0xFF8888BB);
        g.drawString(font,Component.literal("World Seed:"),tx,37,LGRAY);
        g.drawString(font,Component.literal("Radius (2-20):"),tx,87,LGRAY);
        g.drawString(font,Component.literal("Search range (minX,maxX,minZ,maxZ):"),tx,137,LGRAY);
        // Status
        int ry=228;
        g.fill(panelX+4,ry,panelX+panelW-4,ry+55,0xFF0A0A18);
        if (busy) {
            g.drawString(font,Component.literal("Searching..."),panelX+8,ry+5,ORANGE);
        } else {
            List<String> lines=wrap(status,panelW-16);
            for (int i=0;i<Math.min(lines.size(),4);i++)
                g.drawString(font,Component.literal(lines.get(i)),panelX+8,ry+5+i*12,statusColor);
        }
        // Player pos
        Player p=Minecraft.getInstance().player;
        if (p!=null) {
            int iy=295;
            g.drawString(font,Component.literal("Position:"),tx,iy,LGRAY);
            g.drawString(font,Component.literal("X "+(int)Math.floor(p.getX())
                +"  Y "+(int)Math.floor(p.getY())+"  Z "+(int)Math.floor(p.getZ())),tx,iy+13,GREEN);
            boolean n=BedrockScanner.isInNether();
            g.drawString(font,Component.literal(n?"IN NETHER":"NOT IN NETHER"),tx,iy+27,n?GREEN:ORANGE);
        }
        // Tips
        g.drawString(font,Component.literal("How to use:"),tx,340,GOLD);
        String[] tips={"1. Go near Nether ceiling (y~127)",
            "2. Enter world seed above","3. Click Scan Nether",
            "4. Click Find Coords","OR: draw pattern, then Find Coords"};
        for (int i=0;i<tips.length;i++)
            g.drawString(font,Component.literal(tips[i]),tx,353+i*11,LGRAY);
    }

    private List<String> wrap(String t, int maxW) {
        List<String> out=new ArrayList<>();
        if (t==null||t.isEmpty()) return out;
        StringBuilder cur=new StringBuilder();
        for (String w:t.split(" ")) {
            String test=cur.isEmpty()?w:cur+" "+w;
            if (font.width(test)>maxW&&!cur.isEmpty()){out.add(cur.toString());cur=new StringBuilder(w);}
            else cur=new StringBuilder(test);
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    @Override public boolean isPauseScreen() { return false; }
}
