package com.heartfight;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** All damage values are stored in health points internally and shown as hearts (HP / 2) on a 1-10 scale. */
public final class FightTracker {
    private static final int ATTRIBUTION_WINDOW_TICKS = 10;
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    // durability points lost per piece, per opponent: [helmet, chestplate, leggings, boots]
    private static final Map<UUID, int[]> armorDmgByOpponent = new HashMap<>();
    private static final int[] lastDur = { -1, -1, -1, -1 };

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
        armorDmgByOpponent.clear();
        java.util.Arrays.fill(lastDur, -1);
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

    /** Durability lost by the current opponent's armor: [helmet, chestplate, leggings, boots]. */
    public static int[] armorDamage() {
        if (target == null) return new int[4];
        return armorDmgByOpponent.getOrDefault(target.getUUID(), new int[4]);
    }

    private static int durability(Player p, EquipmentSlot slot) {
        ItemStack st = p.getItemBySlot(slot);
        if (st.isEmpty() || !st.isDamageableItem()) return -1;
        return st.getMaxDamage() - st.getDamageValue();
    }

    private static void snapshotArmor(Player p) {
        for (int i = 0; i < 4; i++) lastDur[i] = durability(p, ARMOR[i]);
    }

    public static float takenHearts() { return takenHp / 2f; }

    private static float hp(Player p) { return p.getHealth() + p.getAbsorptionAmount(); }

    public static void onAttack(Player victim) {
        if (!active) return;
        if (victim != target) {
            target = victim;
            lastTargetHp = hp(victim);
            snapshotArmor(victim);
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

            // Armor durability: only the REAL loss reported by the game is counted,
            // so Unbreaking (which makes the loss smaller) is automatically accounted for.
            boolean inWindow = tick - lastAttackTick <= ATTRIBUTION_WINDOW_TICKS;
            int[] dmg = armorDmgByOpponent.computeIfAbsent(target.getUUID(), k -> new int[4]);
            for (int i = 0; i < 4; i++) {
                int cur = durability(target, ARMOR[i]);
                int prev = lastDur[i];
                if (inWindow && prev > 0) {
                    if (cur >= 0 && cur < prev) dmg[i] += prev - cur;   // lost durability
                    else if (cur < 0 && prev > 0 && target.isAlive()) dmg[i] += prev; // piece broke
                }
                lastDur[i] = cur;
            }
        }
    }
}
