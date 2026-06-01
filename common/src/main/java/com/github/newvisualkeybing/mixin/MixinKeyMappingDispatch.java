package com.github.newvisualkeybing.mixin;

import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.keyboard.KeybindPriorityEnforcer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;


/**
 * Full-key no-conflict dispatch. Vanilla routes a key event to a single {@code KeyMapping.MAP}
 * winner, so when several actions share a key only one fires. This mixin instead activates
 * <em>every</em> binding assigned to the pressed key, distinguishing single keys from chords:
 * <ul>
 *   <li>all plain single-key bindings on the key fire together;</li>
 *   <li>all chord bindings whose modifier is held fire together;</li>
 *   <li>when a chord on the key is active, that key's plain single-key bindings are suppressed
 *       (so e.g. {@code Ctrl+G} runs the chord, not plain {@code G}); the chord's modifier key is
 *       <em>not</em> suppressed, so the modifier's own single binding still fires.</li>
 * </ul>
 */
@Mixin(KeyMapping.class)
public class MixinKeyMappingDispatch {

    @Inject(method = "click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V",
            at = @At("HEAD"), cancellable = true)
    private static void newvisualkeybing$onClick(InputConstants.Key key, CallbackInfo ci) {
        KeybindComboStore store = KeybindComboStore.global();
        List<KeybindComboStore.Match> matches = store.triggerMatches(key);
        List<KeyMapping> singles = KeybindPriorityEnforcer.singleKeyMappings(key);

        // Nothing to multiplex: 0 or 1 binding and no chord — let vanilla handle it normally.
        if (matches.isEmpty() && singles.size() <= 1) {
            return;
        }

        if (newvisualkeybing$anyActive(matches)) {
            for (KeybindComboStore.Match match : matches) {
                if (match.active()) newvisualkeybing$incrementClick(match.mapping());
            }
        } else {
            for (KeyMapping single : singles) {
                newvisualkeybing$incrementClick(single);
            }
        }
        ci.cancel();
    }

    @Inject(method = "set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void newvisualkeybing$onSet(InputConstants.Key key, boolean held, CallbackInfo ci) {
        KeybindComboStore store = KeybindComboStore.global();
        List<KeybindComboStore.Match> matches = store.triggerMatches(key);
        List<KeyMapping> singles = KeybindPriorityEnforcer.singleKeyMappings(key);
        // Take over only when there is more than one binding to drive, or any chord is involved.
        // A lone single binding is left to vanilla so unrelated keys are untouched.
        boolean manage = !matches.isEmpty() || singles.size() > 1;

        if (manage) {
            newvisualkeybing$syncTrigger(matches, singles, held);
        }

        // The key may also be a chord modifier (firstKey). When it is pressed or released the
        // active state of chords keyed off any trigger that uses it changes without the trigger
        // itself receiving an event, so re-sync those triggers (and their single keys).
        Set<InputConstants.Key> firstKeyTriggers = store.triggersForFirstKey(key);
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
     * (Re)compute the {@code isDown} state for one trigger key: every chord whose modifier is held
     * is activated, and the key's plain single-key bindings are activated only when no chord
     * claimed the key.
     */
    private static void newvisualkeybing$syncTrigger(List<KeybindComboStore.Match> matches,
                                                     List<KeyMapping> singles, boolean held) {
        boolean comboActive = held && newvisualkeybing$anyActive(matches);
        for (KeybindComboStore.Match match : matches) {
            match.mapping().setDown(held && match.active());
        }
        for (KeyMapping single : singles) {
            single.setDown(held && !comboActive);
        }
    }

    private static boolean newvisualkeybing$anyActive(List<KeybindComboStore.Match> matches) {
        for (KeybindComboStore.Match match : matches) {
            if (match.active()) return true;
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
