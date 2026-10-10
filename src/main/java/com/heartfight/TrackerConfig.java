package com.heartfight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class TrackerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("heartfight.json");
    private static TrackerConfig instance;

    public int width = 150;      // GUI units, before scale
    public int height = 90;
    public float scale = 1.0f;
    public float xPct = 0.02f;   // 0..1 position across the free screen space
    public float yPct = 0.30f;
    public boolean popups = true; // floating +N / -N heart popups
    public int opacity = 70;     // background opacity, percent

    public static TrackerConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) instance = GSON.fromJson(Files.readString(FILE), TrackerConfig.class);
        } catch (Exception ignored) { }
        if (instance == null) instance = new TrackerConfig();
    }

    public static void save() {
        try { Files.writeString(FILE, GSON.toJson(get())); } catch (Exception ignored) { }
    }

    public void reset() {
        TrackerConfig d = new TrackerConfig();
        width = d.width; height = d.height; scale = d.scale;
        xPct = d.xPct; yPct = d.yPct; opacity = d.opacity; popups = d.popups;
    }
}
