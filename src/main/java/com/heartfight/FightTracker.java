package com.heartfight;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Damage is stored in health points internally and shown as hearts (HP / 2) on a 1-10 scale. */
public final class FightTracker {
    private static final int WINDOW = 10; // ticks after one of my hits in which opponent losses count as mine
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    public record Popup(String text, boolean dealt, long tick) { }

    public static boolean active = false;
    public static float takenHp = 0f;
    public static final List<Popup> popups = new ArrayList<>();

    private static final Map<UUID, Float> dealtByOpponent = new HashMap<>();
    private static final Map<UUID, int[]> armorDmgByOpponent = new HashMap<>();
    private static final int[] lastDur = { -1, -1, -1, -1 };

    private static UUID targetId;
    private static String targetName;
    private static float lastTargetHp = -1f;
    private static float lastSelfHp = -1f;
    private static long tick = 0;
    private static long lastAttackTick = -1000;
    private static long lastSelfHurtTick = -1000;

    private FightTracker() { }

    public static long tick() { return tick; }

    public static void start() { reset(); active = true; }

    /** Stopping wipes everything so the next fight starts fresh. */
    public static void stop() { active = false; reset(); }

    private static void reset() {
        takenHp = 0f;
        dealtByOpponent.clear();
        armorDmgByOpponent.clear();
        popups.clear();
        Arrays.fill(lastDur, -1);
        targetId = null;
        targetName = null;
        lastTargetHp = -1f;
        lastSelfHp = -1f;
        lastAttackTick = -1000;
    }

    public static String opponentName() { return targetName; }

    public static float dealtHearts() {
        if (targetId == null) return 0f;
        return dealtByOpponent.getOrDefault(targetId, 0f) / 2f;
    }

    public static float takenHearts() { return takenHp / 2f; }

    /** Durability lost by the current opponent's armor: [helmet, chestplate, leggings, boots]. */
    public static int[] armorDamage() {
        if (targetId == null) return new int[4];
        return armorDmgByOpponent.getOrDefault(targetId, new int[4]);
    }

    private static float hp(Player p) { return p.getHealth() + p.getAbsorptionAmount(); }

    private static int durability(Player p, EquipmentSlot slot) {
        ItemStack st = p.getItemBySlot(slot);
        if (st.isEmpty() || !st.isDamageableItem()) return -1;
        return st.getMaxDamage() - st.getDamageValue();
    }

    private static void snapshotArmor(Player p) {
        for (int i = 0; i < 4; i++) lastDur[i] = durability(p, ARMOR[i]);
    }

    public static void onAttack(Player victim) {
        if (!active) return;
        UUID id = victim.getUUID();
        if (!id.equals(targetId)) {
            targetId = id;
            lastTargetHp = hp(victim);
            snapshotArmor(victim);
        }
        targetName = victim.getName().getString();
        lastAttackTick = tick;
    }

    private static String fmt(float hearts) {
        String n = Math.abs(hearts - Math.rint(hearts)) < 0.05f
                ? String.valueOf((int) Math.rint(hearts))
                : String.format(Locale.ROOT, "%.1f", hearts);
        return n + (Math.abs(hearts - 1f) < 0.05f ? " heart" : " hearts");
    }

    private static void popup(float hp, boolean dealt) {
        float hearts = hp / 2f;
        if (hearts < 0.25f) return;
        popups.add(new Popup((dealt ? "+" : "-") + fmt(hearts), dealt, tick));
        if (popups.size() > 12) popups.remove(0);
    }

    public static void onTick(Minecraft mc) {
        tick++;
        popups.removeIf(p -> tick - p.tick() > 30);
        if (!active || mc.player == null || mc.level == null) return;

        // ---- damage taken (only real hits: ignores effect expiry, absorption fading, etc.) ----
        Player me = mc.player;
        if (me.hurtTime > 0) lastSelfHurtTick = tick;
        float self = hp(me);
        if (lastSelfHp >= 0f && self < lastSelfHp && tick - lastSelfHurtTick <= 2) {
            float lost = lastSelfHp - self;
            takenHp += lost;
            popup(lost, false);
        }
        lastSelfHp = self;

        // ---- damage dealt + armor durability ----
        if (targetId == null) return;
        // Look the opponent up every tick: the entity object is replaced when they leave and re-enter render distance.
        Player target = mc.level.getPlayerByUUID(targetId);
        if (target == null) return;

        float now = hp(target);
        boolean inWindow = tick - lastAttackTick <= WINDOW;
        if (lastTargetHp >= 0f && now < lastTargetHp && inWindow && target.hurtTime > 0) {
            float lost = lastTargetHp - now;
            dealtByOpponent.merge(targetId, lost, Float::sum);
            popup(lost, true);
        }
        lastTargetHp = now;

        // Real durability loss as reported by the game (Unbreaking is already included in that).
        int[] dmg = armorDmgByOpponent.computeIfAbsent(targetId, k -> new int[4]);
        for (int i = 0; i < 4; i++) {
            int cur = durability(target, ARMOR[i]);
            int prev = lastDur[i];
            if (inWindow && prev > 0) {
                if (cur >= 0 && cur < prev) dmg[i] += prev - cur;
                else if (cur < 0 && target.isAlive()) dmg[i] += prev; // piece broke
            }
            lastDur[i] = cur;
        }
    }
}
