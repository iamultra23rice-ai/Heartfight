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
        if (FightTracker.active && TrackerConfig.get().popups) renderPopups(g, mc);
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

        int w = c.width, h = Math.max(c.height, 72);
        int bgAlpha = Math.round(c.opacity / 100f * 255f) << 24;
        g.fill(0, 0, w, h, bgAlpha | 0x0E0F1A);
        g.fill(0, 0, w, 1, 0xFF7C6CFF);           // accent line

        int armorH = 30;
        int rowH = (h - 2 - armorH) / 2;
        String name = FightTracker.opponentName();
        if (name == null) name = preview ? "Opponent" : "-";
        float dealt = preview && !FightTracker.active ? 3.5f : FightTracker.dealtHearts();
        float taken = preview && !FightTracker.active ? 5.0f : FightTracker.takenHearts();

        row(g, mc.font, 2, rowH, w, name, dealt, DEALT);
        row(g, mc.font, 2 + rowH, rowH, w, "Myself", taken, TAKEN);

        int[] ad = preview && !FightTracker.active ? new int[]{ 200, 150, 90, 60 } : FightTracker.armorDamage();
        armor(g, mc.font, 2 + rowH * 2, w, ad);

        g.pose().popMatrix();
    }

    /** Floating "+N hearts" (green, right of crosshair) and "-N hearts" (red, left of crosshair). */
    private static void renderPopups(GuiGraphics g, Minecraft mc) {
        Font font = mc.font;
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;
        long now = FightTracker.tick();
        for (FightTracker.Popup p : FightTracker.popups) {
            long age = now - p.tick();
            if (age < 0 || age > 30) continue;
            int alpha = (int) (255 * (1f - age / 30f));
            if (alpha < 12) continue;
            int rgb = p.dealt() ? 0x4ADE80 : 0xF87171;
            float y = cy - 14 - age * 0.9f;
            g.pose().pushMatrix();
            g.pose().translate(cx, y);
            g.pose().scale(1.25f, 1.25f);
            int x = p.dealt() ? 12 : -12 - font.width(p.text());
            g.drawString(font, p.text(), x, 0, (alpha << 24) | rgb, true);
            g.pose().popMatrix();
        }
    }

    private static void armor(GuiGraphics g, Font font, int y0, int w, int[] d) {
        int pad = 6;
        g.fill(pad, y0, w - pad, y0 + 1, 0xFF2A2D3E);
        int total = d[0] + d[1] + d[2] + d[3];
        String head = "Armor durability dmg: " + total;
        g.drawString(font, head, pad, y0 + 3, MUTED, true);
        int half = w / 2;
        g.drawString(font, "Helm: " + d[0], pad, y0 + 12, TEXT, true);
        g.drawString(font, "Chest: " + d[1], half, y0 + 12, TEXT, true);
        g.drawString(font, "Legs: " + d[2], pad, y0 + 21, TEXT, true);
        g.drawString(font, "Boots: " + d[3], half, y0 + 21, TEXT, true);
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
