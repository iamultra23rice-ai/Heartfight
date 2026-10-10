package com.heartfight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public final class HudRenderer {
    private static final int GREEN = 0xFF4ADE80;
    private static final int RED = 0xFFF87171;
    private static final int WHITE = 0xFFF2F4FF;
    private static final int MUTED = 0xFF8D93B0;
    private static final int ACCENT = 0xFF7C6CFF;
    private static final int TRACK = 0xFF1B1D2C;
    private static final int GOLD = 0xFFC58A3A;

    // pixel icons ('#','b' = main colour, 'h' = hilt)
    private static final String[] SWORD = {
            "......bb", ".....bbb", "....bbb.", "h..bbb..", ".hbbb...", "..hh....", ".h.h....", "h......." };
    private static final String[] BROKEN_HEART = {
            ".##.##.", "#######", "###.###", ".###.#.", "..#.#..", "...#..." };

    // smooth animation state
    private static float dealtShown, takenShown, flashD, flashT, flashC, leadShown = 0.5f;
    private static int prevCombo = 0;
    private static int[] armorShown = new int[4];
    private static long lastNanos = 0L, seenStart = -1L;

    private HudRenderer() { }

    // ---------- helpers ----------
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static int al(int c, float m) {
        int a = Math.max(0, Math.min(255, (int) (((c >>> 24) & 0xFF) * m)));
        return (a << 24) | (c & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static void text(GuiGraphics g, Font f, String s, int x, int y, int color, float m) {
        int c = al(color, m);
        if ((c >>> 24) < 10) return; // tiny alpha would be drawn opaque by the game
        g.drawString(f, s, x, y, c, true);
    }

    private static void rounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + 1, color);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
    }

    private static void bitmap(GuiGraphics g, int x, int y, String[] rows, int main, int hilt, float m) {
        int cm = al(main, m), ch = al(hilt, m);
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                char k = row.charAt(c);
                if (k == '.') continue;
                g.fill(x + c, y + r, x + c + 1, y + r + 1, k == 'h' ? ch : cm);
            }
        }
    }

    private static void heart(GuiGraphics g, int x, int y, int color, float m) {
        int c = al(color, m);
        g.fill(x + 1, y, x + 3, y + 1, c);
        g.fill(x + 4, y, x + 6, y + 1, c);
        g.fill(x, y + 1, x + 7, y + 3, c);
        g.fill(x + 1, y + 3, x + 6, y + 4, c);
        g.fill(x + 2, y + 4, x + 5, y + 5, c);
        g.fill(x + 3, y + 5, x + 4, y + 6, c);
        g.fill(x + 1, y + 1, x + 2, y + 2, al(0x90FFFFFF, m));
    }

    private static String num(float v) { return String.format(Locale.ROOT, "%.1f", v); }

    private static String clock(float sec) {
        int s = (int) sec;
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    // ---------- main HUD ----------
    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        TrackerConfig c = TrackerConfig.get();
        boolean preview = mc.screen instanceof SettingsScreen;
        boolean live = FightTracker.active;

        if (live && c.popups) renderPopups(g, mc);
        if (!live && !preview) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;

        float tD, tT, tLead, me, opp, time, dps;
        int[] tA;
        int hits, swings, acc, combo, best;
        if (live) {
            tD = FightTracker.dealtHearts();
            tT = FightTracker.takenHearts();
            tA = FightTracker.armorDamage();
            me = FightTracker.myHearts;
            opp = FightTracker.oppHearts;
            tLead = opp < 0f ? -1f : me / Math.max(0.01f, me + opp);
            hits = FightTracker.hits; swings = FightTracker.swingCount(); acc = FightTracker.accuracyPct();
            combo = FightTracker.combo; best = FightTracker.bestCombo;
            time = FightTracker.elapsedSeconds(); dps = FightTracker.dps();
            if (seenStart != FightTracker.startNanos) {           // new fight -> restart animations
                seenStart = FightTracker.startNanos;
                dealtShown = takenShown = 0f;
                armorShown = new int[4];
                leadShown = 0.5f; prevCombo = 0;
            }
        } else { // sample numbers while the settings screen is open
            tD = 3.5f; tT = 5.0f; tA = new int[]{ 200, 150, 90, 60 };
            me = 8.5f; opp = 6.0f; tLead = me / (me + opp);
            hits = 12; swings = 20; acc = 60; combo = 3; best = 7; time = 42f; dps = 0.8f;
        }

        // glide displayed numbers toward the real ones; flash on increase
        float k = 1f - (float) Math.exp(-dt * 11f);
        if (tD > dealtShown + 0.15f) flashD = 1f;
        if (tT > takenShown + 0.15f) flashT = 1f;
        if (combo > prevCombo) flashC = 1f;
        prevCombo = combo;
        flashD = Math.max(0f, flashD - dt * 3.2f);
        flashT = Math.max(0f, flashT - dt * 3.2f);
        flashC = Math.max(0f, flashC - dt * 3.2f);
        dealtShown += (tD - dealtShown) * k;
        takenShown += (tT - takenShown) * k;
        if (Math.abs(tD - dealtShown) < 0.02f) dealtShown = tD;
        if (Math.abs(tT - takenShown) < 0.02f) takenShown = tT;
        if (tLead >= 0f) leadShown += (tLead - leadShown) * k;
        for (int i = 0; i < 4; i++) {
            armorShown[i] += Math.round((tA[i] - armorShown[i]) * k);
            if (Math.abs(tA[i] - armorShown[i]) <= 1) armorShown[i] = tA[i];
        }

        // fade / slide in when a fight starts
        float intro = live ? clamp01((now - FightTracker.startNanos) / 300e6f) : 1f;
        float e = 1f - (1f - intro) * (1f - intro) * (1f - intro);
        if (e < 0.03f) return;

        // layout (height is automatic; "Extra height" just adds breathing room to the two main rows)
        int w = c.width;
        int rowH = 24 + c.extra / 2;
        int leadH = c.stats ? 22 : 0;
        int statsH = c.stats ? 36 : 0;
        int armorH = c.armor ? 33 : 0;
        int h = 5 + rowH * 2 + leadH + statsH + armorH + 3;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float px = c.xPct * Math.max(0f, sw - w * c.scale) - (1f - e) * 14f;
        float py = c.yPct * Math.max(0f, sh - h * c.scale);

        g.pose().pushMatrix();
        g.pose().translate(px, py);
        g.pose().scale(c.scale, c.scale);

        // panel body
        int bgAlpha = Math.round(c.opacity / 100f * 255f);
        rounded(g, 0, 0, w, h, al((bgAlpha << 24) | 0x0B0C16, e));
        g.fillGradient(1, 2, w - 1, h - 2, al(0x16FFFFFF, e), al(0x00FFFFFF, e));
        int border = al(0x26FFFFFF, e);
        g.fill(3, 0, w - 3, 1, border);
        g.fill(3, h - 1, w - 3, h, border);
        g.fill(0, 3, 1, h - 3, border);
        g.fill(w - 1, 3, w, h - 3, border);
        g.fill(8, 1, w - 8, 2, al(ACCENT, e));
        g.fillGradient(8, 2, w - 8, 6, al(0x387C6CFF, e), al(0x007C6CFF, e));

        String name = live ? FightTracker.opponentName() : "Opponent";
        if (name == null) name = "-";

        int y = 5;
        row(g, mc.font, y, rowH, w, name, dealtShown, GREEN, flashD, e);
        y += rowH;
        row(g, mc.font, y, rowH, w, "Myself", takenShown, RED, flashT, e);
        y += rowH;
        if (c.stats) {
            lead(g, mc.font, y, w, leadShown, tLead >= 0f, me, opp, e);
            y += leadH;
            stats(g, mc.font, y, w, hits, swings, acc, combo, best, time, dps, flashC, e);
            y += statsH;
        }
        if (c.armor) armor(g, mc.font, y, w, armorShown, e);

        if (c.debug && live) {
            int dy = h + 3;
            for (String line : FightTracker.debug) {
                text(g, mc.font, line, 2, dy, MUTED, e);
                dy += 9;
            }
        }
        g.pose().popMatrix();
    }

    private static void row(GuiGraphics g, Font font, int y0, int rowH, int w, String label,
                            float hearts, int color, float flash, float m) {
        int pad = 8;
        int shownColor = mix(color, 0xFFFFFFFF, flash * 0.75f);
        String value = num(hearts);
        int valueW = font.width(value) + 2 + 7;

        String shown = label;
        int maxLabel = w - pad * 2 - valueW - 8;
        while (shown.length() > 1 && font.width(shown) > maxLabel) shown = shown.substring(0, shown.length() - 1);

        int ty = y0 + 3;
        text(g, font, shown, pad, ty, WHITE, m);
        int vx = w - pad - valueW;
        text(g, font, value, vx, ty, shownColor, m);
        heart(g, vx + font.width(value) + 2, ty + 1, shownColor, m);

        int barY = y0 + rowH - 8;
        int bx = pad, bw = w - pad * 2;
        g.fill(bx, barY, bx + bw, barY + 4, al(TRACK, m));
        int fillW = Math.round(bw * clamp01(hearts / 10f));
        if (fillW > 0) {
            g.fill(bx, barY, bx + fillW, barY + 4, al(shownColor, m));
            g.fill(bx, barY, bx + fillW, barY + 1, al(0x55FFFFFF, m));
        }
        for (int i = 1; i < 10; i++) {
            int dx = bx + Math.round(bw * i / 10f);
            g.fill(dx, barY, dx + 1, barY + 4, al(0xCC0B0C16, m));
        }
    }

    /** One bar showing who is ahead right now: green = you, red = them. */
    private static void lead(GuiGraphics g, Font font, int y0, int w, float frac, boolean known,
                             float me, float opp, float m) {
        int pad = 8;
        g.fill(pad, y0, w - pad, y0 + 1, al(0x1FFFFFFF, m));
        text(g, font, "You " + num(me), pad, y0 + 3, GREEN, m);
        String r = known ? num(opp) + " Them" : "- Them";
        text(g, font, r, w - pad - font.width(r), y0 + 3, RED, m);

        int barY = y0 + 14, bx = pad, bw = w - pad * 2;
        g.fill(bx, barY, bx + bw, barY + 5, al(TRACK, m));
        if (known) {
            int gw = Math.round(bw * clamp01(frac));
            g.fill(bx, barY, bx + gw, barY + 5, al(GREEN, m));
            g.fill(bx + gw, barY, bx + bw, barY + 5, al(RED, m));
            g.fill(bx, barY, bx + bw, barY + 1, al(0x40FFFFFF, m));
        }
        int mid = bx + bw / 2;
        g.fill(mid, barY - 1, mid + 1, barY + 6, al(0xCCFFFFFF, m));
    }

    private static void stats(GuiGraphics g, Font font, int y0, int w, int hits, int swings, int acc,
                              int combo, int best, float time, float dps, float flashC, float m) {
        int pad = 8, half = w / 2 + 2;
        g.fill(pad, y0, w - pad, y0 + 1, al(0x1FFFFFFF, m));
        int comboColor = mix(WHITE, GREEN, flashC);
        stat(g, font, "Hits", hits + "/" + swings, pad, y0 + 4, WHITE, m);
        stat(g, font, "Acc", acc + "%", half, y0 + 4, WHITE, m);
        stat(g, font, "Combo", String.valueOf(combo), pad, y0 + 14, comboColor, m);
        stat(g, font, "Best", String.valueOf(best), half, y0 + 14, WHITE, m);
        stat(g, font, "Time", clock(time), pad, y0 + 24, WHITE, m);
        stat(g, font, "DPS", num(dps), half, y0 + 24, WHITE, m);
    }

    private static void armor(GuiGraphics g, Font font, int y0, int w, int[] d, float m) {
        int pad = 8;
        g.fill(pad, y0, w - pad, y0 + 1, al(0x1FFFFFFF, m));
        int total = d[0] + d[1] + d[2] + d[3];
        text(g, font, "Armor durability dmg", pad, y0 + 4, MUTED, m);
        String t = String.valueOf(total);
        text(g, font, t, w - pad - font.width(t), y0 + 4, mix(ACCENT, 0xFFFFFFFF, 0.45f), m);

        int half = w / 2 + 2;
        stat(g, font, "Helm", String.valueOf(d[0]), pad, y0 + 14, WHITE, m);
        stat(g, font, "Chest", String.valueOf(d[1]), half, y0 + 14, WHITE, m);
        stat(g, font, "Legs", String.valueOf(d[2]), pad, y0 + 23, WHITE, m);
        stat(g, font, "Boots", String.valueOf(d[3]), half, y0 + 23, WHITE, m);
    }

    private static void stat(GuiGraphics g, Font font, String label, String value, int x, int y, int valueColor, float m) {
        text(g, font, label, x, y, MUTED, m);
        text(g, font, value, x + font.width(label) + 4, y, valueColor, m);
    }

    // ---------- floating popups: sword + N = damage you dealt, broken heart + -N = hearts you lost ----------
    private static void renderPopups(GuiGraphics g, Minecraft mc) {
        Font font = mc.font;
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;
        long now = System.nanoTime();
        final float dur = 1.3f;

        for (FightTracker.Popup p : FightTracker.popups) {
            float age = (now - p.nanos()) / 1e9f;
            if (age < 0f || age > dur) continue;
            float t = age / dur;

            float rise = 24f * (1f - (1f - t) * (1f - t));
            float pop = 1.1f + 0.55f * Math.max(0f, 1f - age / 0.14f);
            float fade = t < 0.65f ? 1f : 1f - (t - 0.65f) / 0.35f;
            int jx = (int) ((p.tick() * 7) % 9) - 4;
            int jy = (int) ((p.tick() * 5) % 7) * -2;

            int base = p.dealt() ? GREEN : RED;
            int col = mix(base, 0xFFFFFFFF, Math.max(0f, 1f - age / 0.2f) * 0.6f);
            int tw = font.width(p.text());

            g.pose().pushMatrix();
            g.pose().translate(cx + jx, cy - 10 - rise + jy);
            g.pose().scale(pop, pop);
            if (p.dealt()) {            // [sword] 1.5   (right of the crosshair)
                int x0 = 12;
                bitmap(g, x0, 0, SWORD, mix(col, 0xFFFFFFFF, 0.55f), GOLD, fade);
                text(g, font, p.text(), x0 + 11, 0, col, fade);
            } else {                    // -1.5 [broken heart]   (left of the crosshair)
                int x0 = -12 - (tw + 2 + 7);
                text(g, font, p.text(), x0, 0, col, fade);
                bitmap(g, x0 + tw + 2, 1, BROKEN_HEART, col, GOLD, fade);
            }
            g.pose().popMatrix();
        }
    }
}
