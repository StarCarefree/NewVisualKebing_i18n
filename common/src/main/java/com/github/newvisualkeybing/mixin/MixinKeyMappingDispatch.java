package com.github.newvisualkeybing.mixin;

import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.keyboard.KeybindModifierSuppressor;
import com.github.newvisualkeybing.client.keyboard.KeybindPriorityEnforcer;
import com.github.newvisualkeybing.client.keyboard.KeybindProfileStore;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;


@Mixin(KeyMapping.class)
public class MixinKeyMappingDispatch {

    /**
     * Fully take over click dispatch when the trigger key participates in any chord:
     * pick the priority winner among active chord matches and increment its clickCount,
     * or fall through to the non-chord MAP winner. Vanilla is always cancelled in this
     * case so it cannot increment a chord mapping inadvertently.
     */
    @Inject(method = "click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V",
            at = @At("HEAD"), cancellable = true)
    private static void newvisualkeybing$onClick(InputConstants.Key key, CallbackInfo ci) {
        KeybindComboStore store = KeybindComboStore.global();
        List<KeybindComboStore.Match> matches = store.triggerMatches(key);

        if (!matches.isEmpty()) {
            KeybindComboStore.Match winner = newvisualkeybing$pickActiveWinner(matches);
            if (winner != null) {
                newvisualkeybing$incrementClick(winner.mapping());
                KeybindModifierSuppressor.consume(winner.combo().firstKey);
            } else {
                KeyMapping mapWinner = KeybindPriorityEnforcer.mapWinner(key);
                if (mapWinner != null && !newvisualkeybing$isMatched(mapWinner, matches)) {
                    newvisualkeybing$incrementClick(mapWinner);
                }
            }
            ci.cancel();
            return;
        }

        // Not a chord trigger. If this key is a chord modifier with a non-chord vanilla
        // mapping bound to it, defer the click so a follow-up chord can claim it.
        if (KeybindModifierSuppressor.deferClick(key)) {
            ci.cancel();
        }
    }

    /**
     * Atomically writes the {@code isDown} state of every chord mapping bound to this
     * trigger key, plus the non-chord {@code MAP} winner if any. Vanilla is always cancelled
     * for chord trigger keys so it can never overwrite our writes with a one-frame stale
     * state — this is the fix for the previous TAIL {@code syncComboStates} race.
     */
    @Inject(method = "set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void newvisualkeybing$onSet(InputConstants.Key key, boolean held, CallbackInfo ci) {
        KeybindComboStore store = KeybindComboStore.global();
        List<KeybindComboStore.Match> matches = store.triggerMatches(key);
        boolean isTrigger = !matches.isEmpty();

        if (isTrigger) {
            KeybindComboStore.Match winner = held ? newvisualkeybing$pickActiveWinner(matches) : null;
            for (KeybindComboStore.Match match : matches) {
                match.mapping().setDown(match == winner);
            }
            KeyMapping mapWinner = KeybindPriorityEnforcer.mapWinner(key);
            if (mapWinner != null && !newvisualkeybing$isMatched(mapWinner, matches)) {
                mapWinner.setDown(held);
            }
            if (winner != null) {
                KeybindModifierSuppressor.consume(winner.combo().firstKey);
            }
        }

        // The key may also be a chord modifier (firstKey). When it is pressed or released
        // we must re-sync chords keyed off any trigger that uses it, because the chord's
        // active state changed without the trigger itself receiving an event.
        Set<InputConstants.Key> firstKeyTriggers = store.triggersForFirstKey(key);
        for (InputConstants.Key trigger : firstKeyTriggers) {
            if (isTrigger && newvisualkeybing$sameKey(trigger, key)) continue;
            List<KeybindComboStore.Match> triggerMatches = store.triggerMatches(trigger);
            if (triggerMatches.isEmpty()) continue;
            boolean triggerHeld = KeybindComboStore.isKeyHeld(trigger.getName());
            KeybindComboStore.Match winner = triggerHeld
                    ? newvisualkeybing$pickActiveWinner(triggerMatches) : null;
            for (KeybindComboStore.Match match : triggerMatches) {
                match.mapping().setDown(match == winner);
            }
        }

        if (isTrigger) {
            ci.cancel();
            return;
        }

        // Modifier-suppression: if this key participates in any chord as firstKey, defer
        // vanilla's setDown so a follow-up chord trigger has a chance to claim it.
        if (!firstKeyTriggers.isEmpty()) {
            boolean consumed = held
                    ? KeybindModifierSuppressor.deferPress(key)
                    : KeybindModifierSuppressor.handleRelease(key);
            if (consumed) {
                ci.cancel();
            }
        }
    }

    /**
     * Pick a single active match by global priority. When several chord bindings share the same
     * trigger and are all held, only the highest-priority one fires; this prevents simultaneous
     * activation of conflicting chords.
     */
    private static KeybindComboStore.Match newvisualkeybing$pickActiveWinner(List<KeybindComboStore.Match> matches) {
        KeybindComboStore.Match best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (KeybindComboStore.Match match : matches) {
            if (!match.active()) continue;
            int priority = KeybindProfileStore.globalPriorityOf(match.mapping().getName());
            if (best == null || priority > bestPriority) {
                best = match;
                bestPriority = priority;
            }
        }
        return best;
    }

    private static boolean newvisualkeybing$isMatched(KeyMapping mapping, List<KeybindComboStore.Match> matches) {
        for (KeybindComboStore.Match match : matches) {
            if (match.mapping() == mapping) return true;
        }
        return false;
    }

    private static boolean newvisualkeybing$sameKey(InputConstants.Key a, InputConstants.Key b) {
        return a != null && b != null && a.getType() == b.getType() && a.getValue() == b.getValue();
    }

    private static void newvisualkeybing$incrementClick(KeyMapping mapping) {
        if (mapping == null) return;
        KeyMappingAccessor accessor = (KeyMappingAccessor) (Object) mapping;
        accessor.newvisualkeybing$setClickCount(accessor.newvisualkeybing$getClickCount() + 1);
    }
}
