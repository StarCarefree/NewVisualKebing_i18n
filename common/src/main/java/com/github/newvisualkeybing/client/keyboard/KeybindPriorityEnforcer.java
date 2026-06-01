package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.github.newvisualkeybing.platform.Services;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class KeybindPriorityEnforcer {

    private static volatile Field cachedMapField;
    private static volatile boolean lookupFailed;

    private KeybindPriorityEnforcer() {}


    public static void resetAndEnforce() {
        KeyMapping.resetMapping();
        applyPriority();
    }

    /**
     * Read the live {@code KeyMapping.MAP} winner for the given key, or {@code null} if
     * the field cannot be located (older mappings) or no mapping is bound. Used by the
     * dispatch mixin to decide whether vanilla {@code set}/{@code click} would touch a
     * chord mapping that we want to override.
     */
    public static KeyMapping mapWinner(InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) return null;
        Field field = cachedMapField;
        if (field == null && !lookupFailed) {
            field = locateMapField();
            if (field == null) {
                lookupFailed = true;
                return null;
            }
            cachedMapField = field;
        }
        if (field == null) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<InputConstants.Key, KeyMapping> liveMap = (Map<InputConstants.Key, KeyMapping>) field.get(null);
            return liveMap == null ? null : liveMap.get(key);
        } catch (IllegalAccessException ignored) {
            lookupFailed = true;
            return null;
        }
    }

    /**
     * Collect every NON-chord mapping currently bound to {@code key} by scanning the live
     * key-mapping list directly. This is the basis of the full-key no-conflict dispatch: when a key
     * is pressed, <em>all</em> plain single-key bindings on it are activated together, instead of
     * letting only the single {@code KeyMapping.MAP} winner through.
     */
    public static List<KeyMapping> singleKeyMappings(InputConstants.Key key) {
        List<KeyMapping> result = new ArrayList<>();
        if (key == null || key == InputConstants.UNKNOWN) return result;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return result;
        KeybindComboStore combos = KeybindComboStore.global();
        for (KeyMapping mapping : mc.options.keyMappings) {
            InputConstants.Key bound = ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
            if (bound == null || bound == InputConstants.UNKNOWN) continue;
            if (bound.getType() != key.getType() || bound.getValue() != key.getValue()) continue;
            if (combos.matchesCurrentCombo(mapping)) continue;
            result.add(mapping);
        }
        return result;
    }

    /**
     * Resolve which of {@code candidates} (all bound to the same key) should actually fire, by
     * priority tier with scene-aware fall-through:
     * <ul>
     *   <li>bindings of the <em>same</em> priority all fire together;</li>
     *   <li>only the highest priority tier fires; lower tiers yield to it;</li>
     *   <li>but a higher tier that cannot trigger in the current scene (its conflict context is
     *       inactive) is skipped so a lower tier that <em>can</em> trigger fires instead — the
     *       higher tier never silently blocks a usable lower one.</li>
     * </ul>
     * Returns the subset of {@code candidates} to activate (every member of the winning tier).
     */
    public static List<KeyMapping> resolveByPriority(List<KeyMapping> candidates) {
        if (candidates.size() <= 1) return candidates;
        Integer best = null;
        for (KeyMapping km : candidates) {
            if (!Services.PLATFORM.isContextActive(km)) continue;
            int priority = KeybindProfileStore.globalPriorityOf(km.getName());
            if (best == null || priority > best) best = priority;
        }
        if (best == null) return Collections.emptyList();
        List<KeyMapping> result = new ArrayList<>();
        for (KeyMapping km : candidates) {
            if (KeybindProfileStore.globalPriorityOf(km.getName()) == best) result.add(km);
        }
        return result;
    }

    public static void applyPriority() {
        if (lookupFailed) return;
        Field field = cachedMapField;
        if (field == null) {
            field = locateMapField();
            if (field == null) {
                lookupFailed = true;
                return;
            }
            cachedMapField = field;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;

        try {
            @SuppressWarnings("unchecked")
            Map<InputConstants.Key, KeyMapping> liveMap = (Map<InputConstants.Key, KeyMapping>) field.get(null);
            if (liveMap == null) return;

            KeybindComboStore combos = KeybindComboStore.global();
            Map<InputConstants.Key, KeyMapping> winners = new HashMap<>();
            for (KeyMapping mapping : mc.options.keyMappings) {
                InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
                if (key == null || key == InputConstants.UNKNOWN) continue;
                KeyMapping current = winners.get(key);
                if (current == null || beats(combos, mapping, current)) {
                    winners.put(key, mapping);
                }
            }
            for (Map.Entry<InputConstants.Key, KeyMapping> entry : winners.entrySet()) {
                liveMap.put(entry.getKey(), entry.getValue());
            }
        } catch (IllegalAccessException ignored) {
            lookupFailed = true;
        }
    }

    /**
     * Pick the better of two same-key candidates. Mappings whose current key is the trigger
     * of a complete chord are demoted: a non-chord mapping always wins over a chord one,
     * so single-key presses fall through to the non-chord mapping while the chord is still
     * dispatched manually by {@link com.github.newvisualkeybing.mixin.MixinKeyMappingDispatch}.
     */
    private static boolean beats(KeybindComboStore combos, KeyMapping candidate, KeyMapping current) {
        boolean candidateChord = combos.matchesCurrentCombo(candidate);
        boolean currentChord = combos.matchesCurrentCombo(current);
        if (currentChord != candidateChord) return currentChord;
        return KeybindProfileStore.globalPriorityOf(candidate.getName())
                > KeybindProfileStore.globalPriorityOf(current.getName());
    }


    private static Field locateMapField() {
        String[] candidates = { "MAP", "f_90810_", "field_1665" };
        for (String name : candidates) {
            try {
                Field f = KeyMapping.class.getDeclaredField(name);
                f.setAccessible(true);
                if (Modifier.isStatic(f.getModifiers()) && Map.class.isAssignableFrom(f.getType())) {
                    return f;
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
        try {
            for (Field f : KeyMapping.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object value = f.get(null);
                if (!(value instanceof Map<?, ?> m) || m.isEmpty()) continue;
                Object firstKey = m.keySet().iterator().next();
                if (firstKey instanceof InputConstants.Key) {
                    return f;
                }
            }
        } catch (IllegalAccessException e) {
            Constants.LOG.warn("Failed to locate KeyMapping MAP field via scan: {}", e.toString());
        }
        Constants.LOG.warn("[{}] Could not locate KeyMapping MAP field; priority will not affect runtime dispatch.",
                Constants.MOD_NAME);
        return null;
    }
}
