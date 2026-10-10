package com.heartfight;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** All damage values are stored in health points internally and shown as hearts (HP / 2) on a 1-10 scale. */
public final class FightTracker {
    private static final int ATTRIBUTION_WINDOW_TICKS = 10;

    public static boolean active = false;
    public static float takenHp = 0f;

    private static final Map<UUID, Float> dealtByOpponent = new HashMap<>();
    private static Player target;
    private static String targetName = null;
    private static float lastTargetHp = -1f;
    private static float lastSelfHp = -1f;
    private static long tick = 0;
    private static long lastAttackTick = -1000;

    private FightTracker() { }

    public static void start() {
        reset();
        active = true;
    }

    /** Stopping wipes everything so the next fight starts fresh. */
    public static void stop() {
        active = false;
        reset();
    }

    private static void reset() {
        takenHp = 0f;
        dealtByOpponent.clear();
        target = null;
        targetName = null;
        lastTargetHp = -1f;
        lastSelfHp = -1f;
        lastAttackTick = -1000;
    }

    public static String opponentName() { return targetName; }

    public static float dealtHearts() {
        if (target == null) return 0f;
        return dealtByOpponent.getOrDefault(target.getUUID(), 0f) / 2f;
    }

    public static float takenHearts() { return takenHp / 2f; }

    private static float hp(Player p) { return p.getHealth() + p.getAbsorptionAmount(); }

    public static void onAttack(Player victim) {
        if (!active) return;
        if (victim != target) {
            target = victim;
            lastTargetHp = hp(victim);
        }
        targetName = victim.getName().getString();
        lastAttackTick = tick;
    }

    public static void onTick(Minecraft mc) {
        tick++;
        if (!active || mc.player == null) return;

        float self = hp(mc.player);
        if (lastSelfHp >= 0f && self < lastSelfHp) takenHp += lastSelfHp - self;
        lastSelfHp = self;

        if (target != null) {
            float now = hp(target);
            if (lastTargetHp >= 0f && now < lastTargetHp && tick - lastAttackTick <= ATTRIBUTION_WINDOW_TICKS) {
                dealtByOpponent.merge(target.getUUID(), lastTargetHp - now, Float::sum);
            }
            lastTargetHp = now;
        }
    }
}
