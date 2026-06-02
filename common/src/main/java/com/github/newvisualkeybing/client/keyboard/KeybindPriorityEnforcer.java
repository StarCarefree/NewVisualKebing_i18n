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

    /**
     * Lazily-built index of {@code boundKey -> mappings bound to it}, so {@link #singleKeyMappings}
     * is an O(1) lookup instead of a full scan of every key mapping on each key event. The bound
     * key of a mapping only ever changes through {@link KeyMapping#resetMapping()} (both vanilla
     * rebinds and this mod's edits funnel through it), so that is the single invalidation point —
     * see {@code MixinKeyMappingDispatch#newvisualkeybing$onResetMapping}. {@code null} means "rebuild
     * on next access". Combo state is intentionally NOT part of the index; it is applied afterwards.
     */
    private static volatile Map<InputConstants.Key, List<KeyMapping>> cachedKeyIndex;

    private KeybindPriorityEnforcer() {}

    /** Drop the cached key index; rebuilt lazily on the next {@link #singleKeyMappings} call. */
    public static void invalidateKeyIndex() {
        cachedKeyIndex = null;
    }


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
        if (key == null || key == InputConstants.UNKNOWN) return new ArrayList<>();
        // O(1) lookup against the bound-key index instead of scanning every mapping per key event.
        List<KeyMapping> bound = keyIndex().get(key);
        if (bound == null || bound.isEmpty()) return new ArrayList<>();
        KeybindComboStore combos = KeybindComboStore.global();
        // When no chords are configured (the common case) the per-mapping combo check is a pure
        // no-op, so just hand back a defensive copy of the (typically 1-element) index bucket.
        if (!combos.hasCombos()) return new ArrayList<>(bound);
        List<KeyMapping> result = new ArrayList<>(bound.size());
        for (KeyMapping mapping : bound) {
            if (combos.matchesCurrentCombo(mapping)) continue;
            result.add(mapping);
        }
        return result;
    }

    /**
     * The bound-key index, rebuilt lazily after {@link #invalidateKeyIndex()}. Returns an empty
     * (uncached) map while the client/options are not yet available, so the real index is built
     * on the first real lookup rather than being frozen empty during early startup.
     */
    private static Map<InputConstants.Key, List<KeyMapping>> keyIndex() {
        Map<InputConstants.Key, List<KeyMapping>> index = cachedKeyIndex;
        if (index != null) return index;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return Collections.emptyMap();
        index = buildKeyIndex(mc);
        cachedKeyIndex = index;
        return index;
    }

    private static Map<InputConstants.Key, List<KeyMapping>> buildKeyIndex(Minecraft mc) {
        Map<InputConstants.Key, List<KeyMapping>> index = new HashMap<>();
        for (KeyMapping mapping : mc.options.keyMappings) {
            InputConstants.Key bound = ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
            if (bound == null || bound == InputConstants.UNKNOWN) continue;
            // InputConstants.Key instances are interned singletons (vanilla itself keys
            // KeyMapping.MAP by them), so they are sound HashMap keys.
            index.computeIfAbsent(bound, k -> new ArrayList<>(2)).add(mapping);
        }
        return index;
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
