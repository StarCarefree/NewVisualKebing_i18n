package com.github.newvisualkeybing.mixin;

import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.keyboard.KeybindPriorityEnforcer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Full-key no-conflict dispatch with priority tiers. Vanilla routes a key event to a single
 * {@code KeyMapping.MAP} winner, so when several actions share a key only one fires. This mixin
 * instead activates the bindings assigned to the pressed key, distinguishing single keys from
 * chords and honouring per-binding priority:
 * <ul>
 *   <li>chords whose modifier is held are considered first; if any is active, the key's plain
 *       single-key bindings are suppressed (so {@code Ctrl+G} runs the chord, not plain {@code G});
 *       the chord's modifier key is <em>not</em> suppressed, so the modifier's own binding fires;</li>
 *   <li>within the chosen category, bindings of equal priority all fire together; only the highest
 *       priority tier fires, but a tier that cannot trigger in the current scene is skipped so a
 *       lower tier that can fires instead (see {@link KeybindPriorityEnforcer#resolveByPriority}).</li>
 * </ul>
 */
@Mixin(KeyMapping.class)
public class MixinKeyMappingDispatch {

    /**
     * Invalidate the priority enforcer's bound-key index whenever mappings are reset. Both vanilla
     * rebinds ({@code Options#setKey}) and this mod's edits ({@code resetAndEnforce}) call
     * {@code resetMapping()}, so this is the single, complete invalidation point.
     */
    @Inject(method = "resetMapping()V", at = @At("TAIL"))
    private static void newvisualkeybing$onResetMapping(CallbackInfo ci) {
        KeybindPriorityEnforcer.invalidateKeyIndex();
        KeybindComboStore.invalidateMappingCache();
    }

    @Inject(method = "click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V",
            at = @At("HEAD"), cancellable = true)
    private static void newvisualkeybing$onClick(InputConstants.Key key, CallbackInfo ci) {
        KeybindComboStore store = KeybindComboStore.global();
        boolean hasCombos = store.hasCombos();
        List<KeybindComboStore.Match> matches = hasCombos
                ? store.triggerMatches(key) : java.util.Collections.emptyList();
        List<KeyMapping> singles = KeybindPriorityEnforcer.singleKeyMappings(key);

        // Nothing to multiplex: 0 or 1 binding and no chord — let vanilla handle it normally.
        if (matches.isEmpty() && singles.size() <= 1) {
            return;
        }

        List<KeyMapping> activeCombos = newvisualkeybing$activeComboMappings(matches);
        List<KeyMapping> winners = activeCombos.isEmpty()
                ? KeybindPriorityEnforcer.resolveByPriority(singles)
                : KeybindPriorityEnforcer.resolveByPriority(activeCombos);
        for (KeyMapping winner : winners) {
            newvisualkeybing$incrementClick(winner);
        }
        ci.cancel();
    }

    @Inject(method = "set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void newvisualkeybing$onSet(InputConstants.Key key, boolean held, CallbackInfo ci) {
        KeybindComboStore store = KeybindComboStore.global();
        boolean hasCombos = store.hasCombos();
        List<KeybindComboStore.Match> matches = hasCombos
                ? store.triggerMatches(key) : java.util.Collections.emptyList();
        List<KeyMapping> singles = KeybindPriorityEnforcer.singleKeyMappings(key);
        // Take over only when there is more than one binding to drive, or any chord is involved.
        // A lone single binding is left to vanilla so unrelated keys are untouched.
        boolean manage = !matches.isEmpty() || singles.size() > 1;

        if (manage) {
            newvisualkeybing$syncTrigger(matches, singles, held);
        }

        // The key may also be a chord modifier (firstKey). When it is pressed or released the
        // active state of chords keyed off any trigger that uses it changes without the trigger
        // itself receiving an event, so re-sync those triggers (and their single keys). With no
        // chords configured there is nothing to re-sync, so skip the store lookups entirely.
        Set<InputConstants.Key> firstKeyTriggers = hasCombos
                ? store.triggersForFirstKey(key) : java.util.Collections.emptySet();
        for (InputConstants.Key trigger : firstKeyTriggers) {
            if (newvisualkeybing$sameKey(trigger, key)) continue;
            List<KeybindComboStore.Match> triggerMatches = store.triggerMatches(trigger);
            if (triggerMatches.isEmpty()) continue;
            boolean triggerHeld = KeybindComboStore.isKeyHeld(trigger.getName());
            newvisualkeybing$syncTrigger(triggerMatches,
                    KeybindPriorityEnforcer.singleKeyMappings(trigger), triggerHeld);
        }

        // When we own the key we have written every relevant mapping's state, so cancel vanilla to
        // stop it overwriting them with its single-winner result. The modifier key itself is left
        // to vanilla (manage is false for a lone single binding) so its own action still fires.
        if (manage) {
            ci.cancel();
        }
    }

    /**
     * (Re)compute the {@code isDown} state for one trigger key. When a chord is active it claims the
     * key (single keys suppressed); otherwise the single keys take it. Within whichever category
     * wins, {@link KeybindPriorityEnforcer#resolveByPriority} decides the firing set by priority
     * tier (same tier all fire, higher tier wins, scene-aware fall-through).
     */
    private static void newvisualkeybing$syncTrigger(List<KeybindComboStore.Match> matches,
                                                     List<KeyMapping> singles, boolean held) {
        if (!held) {
            for (KeybindComboStore.Match match : matches) match.mapping().setDown(false);
            for (KeyMapping single : singles) single.setDown(false);
            return;
        }
        List<KeyMapping> activeCombos = newvisualkeybing$activeComboMappings(matches);
        if (!activeCombos.isEmpty()) {
            Set<KeyMapping> winners = new HashSet<>(KeybindPriorityEnforcer.resolveByPriority(activeCombos));
            for (KeybindComboStore.Match match : matches) {
                match.mapping().setDown(winners.contains(match.mapping()));
            }
            for (KeyMapping single : singles) single.setDown(false);
        } else {
            for (KeybindComboStore.Match match : matches) match.mapping().setDown(false);
            Set<KeyMapping> winners = new HashSet<>(KeybindPriorityEnforcer.resolveByPriority(singles));
            for (KeyMapping single : singles) single.setDown(winners.contains(single));
        }
    }

    /** Mappings of every chord on this trigger whose modifier is currently held. */
    private static List<KeyMapping> newvisualkeybing$activeComboMappings(List<KeybindComboStore.Match> matches) {
        List<KeyMapping> result = new ArrayList<>();
        for (KeybindComboStore.Match match : matches) {
            if (match.active()) result.add(match.mapping());
        }
        return result;
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
