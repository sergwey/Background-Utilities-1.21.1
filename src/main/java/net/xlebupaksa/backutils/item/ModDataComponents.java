package net.xlebupaksa.backutils.item;

import net.minecraft.core.component.DataComponentType;

/**
 * The item components this mod adds.
 *
 * <p>Kept apart from {@code ModAttachments}, which registers data on <em>entities</em> rather than
 * data carried by an item.
 *
 * <p>A {@link DataComponentType} holds no value of its own, so it is built here as a constant and
 * given its registry id in {@code BackUtilsRegistries.onRegister}: a component registered with a
 * different instance than the one the item was built with would be a second, empty component.
 */
public final class ModDataComponents {

    /**
     * Everything one effect tool is set to. Persistent and synchronised, because the values go into
     * the item an operator hands out, and the client draws the effect the tool describes.
     *
     * <p>The stored shape does not have to match the item's id, which is deliberate: renaming the
     * item later must not orphan every tool already in a world.
     */
    public static final DataComponentType<EffectToolConfig> EFFECT_TOOL_CONFIG =
            DataComponentType.<EffectToolConfig>builder()
                    .persistent(EffectToolConfig.CODEC)
                    .networkSynchronized(EffectToolConfig.STREAM_CODEC)
                    .build();

    private ModDataComponents() {}
}
