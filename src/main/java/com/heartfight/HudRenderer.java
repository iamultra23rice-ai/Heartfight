package com.heartfight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public final class HudRenderer {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA0A4B8;
    private static final int DEALT = 0xFF4ADE80;
    private static final int TAKEN = 0xFFF87171;
    private static final int BAR_BG = 0xFF2A2D3E;

    private HudRenderer() { }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        boolean preview = mc.screen instanceof SettingsScreen;
        if (!FightTracker.active && !preview) return;

        TrackerConfig c = TrackerConfig.get();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float px = c.xPct * Math.max(0f, sw - c.width * c.scale);
        float py = c.yPct * Math.max(0f, sh - c.height * c.scale);

        g.pose().pushMatrix();
        g.pose().translate(px, py);
        g.pose().scale(c.scale, c.scale);

        int w = c.width, h = c.height;
        int bgAlpha = Math.round(c.opacity / 100f * 255f) << 24;
        g.fill(0, 0, w, h, bgAlpha | 0x0E0F1A);
        g.fill(0, 0, w, 1, 0xFF7C6CFF);           // accent line

        int rowH = (h - 2) / 2;
        String name = FightTracker.opponentName();
        if (name == null) name = preview ? "Opponent" : "-";
        float dealt = preview && !FightTracker.active ? 3.5f : FightTracker.dealtHearts();
        float taken = preview && !FightTracker.active ? 5.0f : FightTracker.takenHearts();

        row(g, mc.font, 2, rowH, w, name, dealt, DEALT);
        row(g, mc.font, 2 + rowH, rowH, w, "Myself", taken, TAKEN);

        g.pose().popMatrix();
    }

    private static void row(GuiGraphics g, Font font, int y0, int rowH, int w, String label, float hearts, int color) {
        int pad = 6;
        String value = String.format(Locale.ROOT, "%.1f/10 hearts", hearts);
        int maxLabel = w - pad * 2 - font.width(value) - 6;
        String shown = label;
        while (shown.length() > 1 && font.width(shown) > maxLabel) shown = shown.substring(0, shown.length() - 1);

        int textY = y0 + 3;
        g.drawString(font, shown, pad, textY, TEXT, true);
        g.drawString(font, value, w - pad - font.width(value), textY, color, true);

        // 10-segment bar, one segment per heart
        int barY = y0 + rowH - 7;
        int gap = 1;
        int segW = Math.max(2, (w - pad * 2 - gap * 9) / 10);
        float filled = Math.min(10f, Math.max(0f, hearts));
        for (int i = 0; i < 10; i++) {
            int x = pad + i * (segW + gap);
            g.fill(x, barY, x + segW, barY + 4, BAR_BG);
            float part = Math.min(1f, Math.max(0f, filled - i));
            if (part > 0f) g.fill(x, barY, x + Math.max(1, Math.round(segW * part)), barY + 4, color);
        }
    }
}
