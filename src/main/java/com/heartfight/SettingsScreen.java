package com.heartfight;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SettingsScreen extends Screen {
    public SettingsScreen() { super(Component.literal("Heart Fight Tracker HUD")); }

    @Override
    protected void init() {
        TrackerConfig c = TrackerConfig.get();
        int w = 150, h = 20;
        int lx = this.width / 2 - w - 4;   // left column: sliders
        int rx = this.width / 2 + 4;       // right column: toggles + buttons
        int top = this.height - 8 - 22 * 7;

        int y = top;
        addRenderableWidget(new Slide(lx, y, w, h, "Width", 120, 320, c.width, v -> c.width = Math.round(v), "%.0f"));
        addRenderableWidget(new Slide(lx, y += 22, w, h, "Extra height", 0, 80, c.extra, v -> c.extra = Math.round(v), "%.0f"));
        addRenderableWidget(new Slide(lx, y += 22, w, h, "Scale", 0.5f, 3.0f, c.scale, v -> c.scale = v, "%.2f"));
        addRenderableWidget(new Slide(lx, y += 22, w, h, "Position X", 0, 100, c.xPct * 100f, v -> c.xPct = v / 100f, "%.0f%%"));
        addRenderableWidget(new Slide(lx, y += 22, w, h, "Position Y", 0, 100, c.yPct * 100f, v -> c.yPct = v / 100f, "%.0f%%"));
        addRenderableWidget(new Slide(lx, y += 22, w, h, "Opacity", 0, 100, c.opacity, v -> c.opacity = Math.round(v), "%.0f%%"));

        y = top;
        addRenderableWidget(toggle(rx, y, w, h, "Hit popups", () -> c.popups, v -> c.popups = v));
        addRenderableWidget(toggle(rx, y += 22, w, h, "Hit sounds", () -> c.sounds, v -> c.sounds = v));
        addRenderableWidget(toggle(rx, y += 22, w, h, "Stats + lead bar", () -> c.stats, v -> c.stats = v));
        addRenderableWidget(toggle(rx, y += 22, w, h, "Armor section", () -> c.armor, v -> c.armor = v));
        addRenderableWidget(toggle(rx, y += 22, w, h, "Debug readout", () -> c.debug, v -> c.debug = v));
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> { c.reset(); rebuildWidgets(); })
                .bounds(rx, y += 22, 73, h).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(rx + 77, y, 73, h).build());
    }

    private static Button toggle(int x, int y, int w, int h, String label, BooleanSupplier get, Consumer<Boolean> set) {
        return Button.builder(Component.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(Component.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")));
        }).bounds(x, y, w, h).build();
    }

    @Override
    public void onClose() {
        TrackerConfig.save();
        super.onClose();
    }

    private static final class Slide extends AbstractSliderButton {
        private final String label, fmt;
        private final float min, max;
        private final Consumer<Float> setter;

        Slide(int x, int y, int w, int h, String label, float min, float max, float cur,
              Consumer<Float> setter, String fmt) {
            super(x, y, w, h, Component.empty(), (cur - min) / (max - min));
            this.label = label; this.min = min; this.max = max; this.setter = setter; this.fmt = fmt;
            updateMessage();
        }

        private float real() { return min + (max - min) * (float) this.value; }

        @Override
        protected void updateMessage() {
            if (label == null) return;
            setMessage(Component.literal(label + ": " + String.format(fmt, real())));
        }

        @Override
        protected void applyValue() { setter.accept(real()); }
    }
}
