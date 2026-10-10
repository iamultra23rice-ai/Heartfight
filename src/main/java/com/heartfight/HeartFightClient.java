package com.heartfight;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

public class HeartFightClient implements ClientModInitializer {
    public static final String MOD_ID = "heartfight";

    private static KeyMapping startKey, stopKey, settingsKey;

    @Override
    public void onInitializeClient() {
        TrackerConfig.load();

        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        startKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.heartfight.start", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, cat));
        stopKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.heartfight.stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, cat));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.heartfight.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, cat));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                (graphics, delta) -> HudRenderer.render(graphics));

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && entity instanceof Player victim
                    && player == Minecraft.getInstance().player) {
                FightTracker.onAttack(victim);
            }
            return InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (startKey.consumeClick()) {
                FightTracker.start();
                msg(mc, "Fight tracker STARTED");
            }
            while (stopKey.consumeClick()) {
                FightTracker.stop();
                msg(mc, "Fight tracker STOPPED & reset");
            }
            while (settingsKey.consumeClick()) {
                mc.setScreen(new SettingsScreen());
            }
            FightTracker.onTick(mc);
        });
    }

    private static void msg(Minecraft mc, String text) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
    }
}
