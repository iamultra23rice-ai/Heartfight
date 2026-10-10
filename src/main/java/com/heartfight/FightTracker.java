package com.heartfight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
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

/** Damage is stored in health points internally and shown as hearts (HP / 2). */
public final class FightTracker {
    private static final int WINDOW = 10;        // ticks after my click in which opponent losses count as mine
    private static final int LOOSE_WINDOW = 40;  // ...or this long if the opponent is visibly flinching
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final String[] ARMOR_TAG = { "H", "C", "L", "B" };

    public record Popup(String text, boolean dealt, long tick, long nanos) { }

    public static boolean active = false;
    public static long startNanos = 0L;
    public static float takenHp = 0f;
    public static float totalDealtHp = 0f;
    public static int swings = 0, hits = 0, combo = 0, bestCombo = 0;
    public static float myHearts = 0f, oppHearts = -1f;
    public static final List<Popup> popups = new ArrayList<>();
    public static final List<String> debug = new ArrayList<>();

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
    private static long lastHitTick = -1000;

    private FightTracker() { }

    public static long tick() { return tick; }

    public static void start() { reset(); active = true; startNanos = System.nanoTime(); }

    /** Stopping wipes everything so the next fight starts fresh. */
    public static void stop() { active = false; reset(); }

    private static void reset() {
        takenHp = 0f; totalDealtHp = 0f;
        swings = hits = combo = bestCombo = 0;
        myHearts = 0f; oppHearts = -1f;
        dealtByOpponent.clear();
        armorDmgByOpponent.clear();
        popups.clear();
        debug.clear();
        Arrays.fill(lastDur, -1);
        targetId = null;
        targetName = null;
        lastTargetHp = -1f;
        lastSelfHp = -1f;
        lastAttackTick = -1000;
        lastHitTick = -1000;
    }

    // ---------- values for the HUD ----------
    public static String opponentName() { return targetName; }

    public static float dealtHearts() {
        if (targetId == null) return 0f;
        return dealtByOpponent.getOrDefault(targetId, 0f) / 2f;
    }

    public static float takenHearts() { return takenHp / 2f; }

    public static int[] armorDamage() {
        if (targetId == null) return new int[4];
        return armorDmgByOpponent.getOrDefault(targetId, new int[4]);
    }

    public static int swingCount() { return Math.max(swings, hits); }

    public static int accuracyPct() {
        int s = swingCount();
        return s == 0 ? 0 : Math.min(100, Math.round(hits * 100f / s));
    }

    public static float elapsedSeconds() {
        return active ? (System.nanoTime() - startNanos) / 1e9f : 0f;
    }

    /** Average damage per second, in hearts. */
    public static float dps() { return totalDealtHp / 2f / Math.max(1f, elapsedSeconds()); }

    // ---------- inputs ----------
    private static float hp(Player p) { return p.getHealth() + p.getAbsorptionAmount(); }

    private static int durability(Player p, EquipmentSlot slot) {
        ItemStack st = p.getItemBySlot(slot);
        if (st.isEmpty() || !st.isDamageableItem()) return -1;
        return st.getMaxDamage() - st.getDamageValue();
    }

    private static String durText(Player p, EquipmentSlot slot) {
        ItemStack st = p.getItemBySlot(slot);
        if (st.isEmpty()) return "none";
        if (!st.isDamageableItem()) return "unbreakable";
        return (st.getMaxDamage() - st.getDamageValue()) + "/" + st.getMaxDamage();
    }

    private static void snapshotArmor(Player p) {
        for (int i = 0; i < 4; i++) lastDur[i] = durability(p, ARMOR[i]);
    }

    /** Target selection (also called from the vanilla attack event as a backup). */
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

    /** Every left click that is not on a block. victim = the player under the crosshair, or null for an air swing. */
    public static void onClick(Player victim) {
        if (!active) return;
        swings++;
        if (victim != null) {
            hits++;
            combo++;
            bestCombo = Math.max(bestCombo, combo);
            lastHitTick = tick;
            onAttack(victim);
        }
    }

    private static String fmt(float hearts) {
        return Math.abs(hearts - Math.rint(hearts)) < 0.05f
                ? String.valueOf((int) Math.rint(hearts))
                : String.format(Locale.ROOT, "%.1f", hearts);
    }

    private static void popup(float hp, boolean dealt) {
        float hearts = hp / 2f;
        if (hearts < 0.25f) return;
        popups.add(new Popup((dealt ? "" : "-") + fmt(hearts), dealt, tick, System.nanoTime()));
        if (popups.size() > 12) popups.remove(0);
        if (TrackerConfig.get().sounds) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    dealt ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.AMETHYST_BLOCK_CHIME,
                    dealt ? 1.5f : 0.55f, dealt ? 0.4f : 0.5f));
        }
    }

    public static void onTick(Minecraft mc) {
        tick++;
        popups.removeIf(p -> tick - p.tick() > 30);
        if (!active || mc.player == null || mc.level == null) return;

        if (combo > 0 && tick - lastHitTick > 60) combo = 0; // combo drops after 3s without a hit

        // ---- damage taken (real hits only: ignores effects wearing off, absorption fading, etc.) ----
        Player me = mc.player;
        if (me.hurtTime > 0) lastSelfHurtTick = tick;
        float self = hp(me);
        if (lastSelfHp >= 0f && self < lastSelfHp && tick - lastSelfHurtTick <= 3) {
            float lost = lastSelfHp - self;
            takenHp += lost;
            combo = 0;                      // getting hit breaks my combo
            popup(lost, false);
        }
        lastSelfHp = self;
        myHearts = self / 2f;

        // ---- opponent: looked up by UUID every tick (the entity object is replaced when they re-enter view) ----
        Player target = targetId == null ? null : mc.level.getPlayerByUUID(targetId);
        oppHearts = lastTargetHp >= 0f ? lastTargetHp / 2f : -1f;

        boolean debugOn = TrackerConfig.get().debug;
        if (debugOn) {
            debug.clear();
            debug.add(String.format(Locale.ROOT, "entity: %s | opp hp: %s | hurt: %s",
                    target != null ? "found" : (targetId == null ? "no target yet" : "NOT FOUND"),
                    target != null ? String.format(Locale.ROOT, "%.1f", hp(target)) : "-",
                    target != null ? String.valueOf(target.hurtTime) : "-"));
            if (target != null) {
                debug.add("armor H " + durText(target, ARMOR[0]) + "  C " + durText(target, ARMOR[1]));
                debug.add("armor L " + durText(target, ARMOR[2]) + "  B " + durText(target, ARMOR[3]));
            }
            debug.add("clicks " + swings + " hits " + hits + " | last click " + (tick - lastAttackTick) + " ticks ago");
        }

        if (target == null) return;

        // ---- damage dealt ----
        float now = hp(target);
        boolean tight = tick - lastAttackTick <= WINDOW;
        boolean loose = tick - lastAttackTick <= LOOSE_WINDOW && target.hurtTime > 0;
        if (lastTargetHp >= 0f && now < lastTargetHp - 0.01f && (tight || loose)) {
            float lost = lastTargetHp - now;
            dealtByOpponent.merge(targetId, lost, Float::sum);
            totalDealtHp += lost;
            popup(lost, true);
        }
        lastTargetHp = now;
        oppHearts = now / 2f;

        // ---- armor durability: real loss as reported by the game (Unbreaking is already included) ----
        int[] dmg = armorDmgByOpponent.computeIfAbsent(targetId, k -> new int[4]);
        for (int i = 0; i < 4; i++) {
            int cur = durability(target, ARMOR[i]);
            int prev = lastDur[i];
            if (tight && prev > 0) {
                if (cur >= 0 && cur < prev) dmg[i] += prev - cur;
                else if (cur < 0 && target.isAlive()) dmg[i] += prev; // piece broke
            }
            lastDur[i] = cur;
        }
    }
}
