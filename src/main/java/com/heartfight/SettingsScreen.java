package com.heartfight;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class SettingsScreen extends Screen {
    public SettingsScreen() { super(Component.literal("Heart Fight Tracker HUD")); }

    @Override
    protected void init() {
        TrackerConfig c = TrackerConfig.get();
        int w = 200, h = 20, x = this.width / 2 - w / 2;
        int y = this.height - 8 - 22 * 8;

        addRenderableWidget(new Slide(x, y, w, h, "Width", 100, 300, c.width, v -> c.width = Math.round(v), "%.0f"));
        addRenderableWidget(new Slide(x, y += 22, w, h, "Height", 72, 140, c.height, v -> c.height = Math.round(v), "%.0f"));
        addRenderableWidget(new Slide(x, y += 22, w, h, "Scale", 0.5f, 3.0f, c.scale, v -> c.scale = v, "%.2f"));
        addRenderableWidget(new Slide(x, y += 22, w, h, "Position X", 0, 100, c.xPct * 100f, v -> c.xPct = v / 100f, "%.0f%%"));
        addRenderableWidget(new Slide(x, y += 22, w, h, "Position Y", 0, 100, c.yPct * 100f, v -> c.yPct = v / 100f, "%.0f%%"));
        addRenderableWidget(new Slide(x, y += 22, w, h, "Opacity", 0, 100, c.opacity, v -> c.opacity = Math.round(v), "%.0f%%"));

        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> { c.reset(); rebuildWidgets(); })
                .bounds(x, y += 22, 98, h).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(x + 102, y, 98, h).build());
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
