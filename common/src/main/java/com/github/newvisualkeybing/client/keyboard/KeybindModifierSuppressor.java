package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the deferred-press/click queue used by the dispatch mixin to suppress a chord
 * modifier's own single-key action while it might still be the start of a chord. Lives
 * outside the mixin class so it can be safely referenced from both the dispatch and
 * tick mixins without depending on how Mixin merges static helpers.
 */
public final class KeybindModifierSuppressor {

    /** Window during which a modifier press is held back, in game ticks (~50 ms each). */
    public static final int LOOKAHEAD_TICKS = 3;

    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    private KeybindModifierSuppressor() {}

    /**
     * Mark the {@code setDown(true)} for {@code key} as deferred. Returns true when
     * vanilla should be cancelled; false when no target mapping exists and vanilla can
     * proceed.
     */
    public static boolean deferPress(InputConstants.Key key) {
        KeyMapping mapWinner = KeybindPriorityEnforcer.mapWinner(key);
        if (mapWinner == null) return false;
        Pending pending = PENDING.computeIfAbsent(key.getName(),
                k -> new Pending(mapWinner, LOOKAHEAD_TICKS));
        pending.pressDeferred = true;
        return true;
    }

    /**
     * Handle a release event for a key that may have a deferred press. Replays the
     * deferred press/click momentarily (so a quick tap behaves like a normal press)
     * and returns true so the caller cancels vanilla; false if no defer was active.
     */
    public static boolean handleRelease(InputConstants.Key key) {
        Pending pending = PENDING.remove(key.getName());
        if (pending == null) return false;
        replay(pending);
        if (pending.pressDeferred) {
            pending.mapping.setDown(false);
        }
        return true;
    }

    /** Mark the {@code click} for {@code key} as deferred. */
    public static boolean deferClick(InputConstants.Key key) {
        Set<InputConstants.Key> triggers = KeybindComboStore.global().triggersForFirstKey(key);
        if (triggers.isEmpty()) return false;
        KeyMapping mapWinner = KeybindPriorityEnforcer.mapWinner(key);
        if (mapWinner == null) return false;
        Pending pending = PENDING.computeIfAbsent(key.getName(),
                k -> new Pending(mapWinner, LOOKAHEAD_TICKS));
        pending.clickDeferred = true;
        return true;
    }

    /**
     * Cancel any pending defer for a modifier key. Called when a chord actually fires
     * so the modifier's own action is dropped.
     */
    public static void consume(String modifierName) {
        if (modifierName == null) return;
        PENDING.remove(modifierName);
    }

    /**
     * Age every pending entry by one tick. Entries whose key is no longer held are
     * replayed immediately (a quick tap that ended before the window closed); entries
     * whose window has expired are replayed and removed. Invoked from the client tick
     * mixin once per game tick.
     */
    public static void tick() {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<String, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Pending> entry = it.next();
            Pending pending = entry.getValue();
            if (!KeybindComboStore.isKeyHeld(entry.getKey())) {
                replay(pending);
                if (pending.pressDeferred) {
                    pending.mapping.setDown(false);
                }
                it.remove();
                continue;
            }
            pending.ticksLeft--;
            if (pending.ticksLeft <= 0) {
                replay(pending);
                it.remove();
            }
        }
    }

    private static void replay(Pending pending) {
        if (pending == null || pending.mapping == null) return;
        if (pending.pressDeferred) {
            pending.mapping.setDown(true);
        }
        if (pending.clickDeferred) {
            KeyMappingAccessor accessor = (KeyMappingAccessor) (Object) pending.mapping;
            accessor.newvisualkeybing$setClickCount(accessor.newvisualkeybing$getClickCount() + 1);
        }
    }

    private static final class Pending {
        final KeyMapping mapping;
        int ticksLeft;
        boolean pressDeferred;
        boolean clickDeferred;

        Pending(KeyMapping mapping, int ticks) {
            this.mapping = mapping;
            this.ticksLeft = ticks;
        }
    }
}
