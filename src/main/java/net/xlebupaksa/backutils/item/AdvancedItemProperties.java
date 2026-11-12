package net.xlebupaksa.backutils.item;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.util.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The properties the item editor's Advanced screen changes, in one draft.
 *
 * <p>This is deliberately a plain model rather than a set of widgets: the editor's main screen and
 * the advanced screen both hold the same object, and the same object is what the message to the
 * server carries. Nothing here writes an item until {@link #apply} is called, which is what makes
 * the advanced screen's changes stay in the main editor's one Apply.
 *
 * <p>Every group is off until its checkbox is ticked. A group that is off removes the components it
 * owns; a group that is on writes them. The one component the editor never takes ownership of is a
 * potion's base potion and custom colour: when custom effects are written, those two are carried
 * over from the item rather than cleared. The effects themselves go into both FOOD and
 * POTION_CONTENTS, because eating reads the first and a potion-like use reads the second.
 */
public final class AdvancedItemProperties {

    /** One custom potion effect, as the advanced screen holds it. */
    public record Effect(String id, int durationTicks, int amplifier, boolean ambient,
                         boolean visible, boolean showIcon) {}

    /** One enchantment and its level, as the advanced screen holds it. */
    public record EnchantmentLevel(String id, int level) {}

    /** Consumable group. */
    public boolean consumable;
    public int nutrition;
    public float saturation;
    public boolean canAlwaysEat;
    public float eatSeconds = 1.6f;
    public final List<Effect> effects = new ArrayList<>();

    /** Durability group. */
    public boolean durability;
    public int maxDamage = 100;
    public int currentDurability = 100;
    public boolean unbreakable;

    /** Dyed colour group. */
    public boolean dyed;
    public int dyedColor = 0xFFFFFF;

    /** Enchantments group. */
    public boolean enchantments;
    public final List<EnchantmentLevel> enchantmentLevels = new ArrayList<>();

    /** Misc group. */
    public boolean fireResistant;
    public boolean glint;
    public boolean hideEnchants;
    public boolean cannotBePlaced;
    public boolean misc;
    public int maxStackSize = 64;

    /** {@return a draft read from the item, with a group ticked only when its component is there} */
    public static AdvancedItemProperties from(ItemStack stack) {
        AdvancedItemProperties out = new AdvancedItemProperties();
        if (stack == null || stack.isEmpty()) return out;

        FoodProperties food = stack.get(DataComponents.FOOD);
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        out.consumable = food != null || potion != null;
        if (food != null) {
            out.nutrition = food.nutrition();
            out.saturation = food.saturation();
            out.canAlwaysEat = food.canAlwaysEat();
            out.eatSeconds = food.eatSeconds() <= 0 ? 1.6f : food.eatSeconds();
        }
        if (potion != null) {
            for (MobEffectInstance effect : potion.customEffects()) {
                ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
                if (id == null) continue;
                out.effects.add(new Effect(id.toString(), effect.getDuration(),
                        effect.getAmplifier(), effect.isAmbient(), effect.isVisible(),
                        effect.showIcon()));
            }
        }

        Integer max = stack.get(DataComponents.MAX_DAMAGE);
        Integer damage = stack.get(DataComponents.DAMAGE);
        out.durability = max != null || damage != null;
        out.maxDamage = max != null ? max : Math.max(1, stack.getMaxDamage());
        int taken = damage != null ? damage : stack.getDamageValue();
        out.currentDurability = clamp(out.maxDamage - taken, 0, out.maxDamage);
        out.unbreakable = stack.get(DataComponents.UNBREAKABLE) != null;

        DyedItemColor color = stack.get(DataComponents.DYED_COLOR);
        out.dyed = color != null;
        if (color != null) out.dyedColor = color.rgb() & 0xFFFFFF;

        ItemEnchantments current = stack.get(DataComponents.ENCHANTMENTS);
        out.enchantments = current != null && !current.isEmpty();
        if (current != null) {
            for (var entry : current.entrySet()) {
                ResourceLocation id = entry.getKey().unwrapKey()
                        .map(ResourceKey::location).orElse(null);
                if (id == null) continue;
                out.enchantmentLevels.add(new EnchantmentLevel(id.toString(), entry.getIntValue()));
            }
        }

        out.fireResistant = stack.get(DataComponents.FIRE_RESISTANT) != null;
        Boolean glintOverride = stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        // The checkbox is the item's glint, not whether a glint override exists. An enchanted item
        // glints without an override, so it must open ticked and an untick must write "false"
        // rather than merely removing the override — removing it turns the enchantment glint back
        // on, which is the bug this distinction fixes.
        out.glint = glintOverride == null ? hasEnchantments(stack) : glintOverride;
        out.hideEnchants = HiddenEnchantments.hidden(stack);
        out.cannotBePlaced = Unplaceable.is(stack);
        out.misc = out.fireResistant || glintOverride != null || out.hideEnchants
                || out.cannotBePlaced;

        Integer stackSize = stack.get(DataComponents.MAX_STACK_SIZE);
        out.maxStackSize = stackSize != null ? stackSize : stack.getMaxStackSize();
        return out;
    }

    /**
     * Writes this draft onto an item.
     *
     * <p>Resolution is forgiving in the one direction that matters: an id the server does not have
     * is skipped rather than invented, and the rest of the edit still happens. The bounds that make
     * a malformed edit impossible are checked by {@link #problem()} before anything is written.
     */
    public void apply(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        // A potion's base and colour are not edited here; carry them over when the custom list is
        // written, so ticking the group does not silently turn a potion into an empty one.
        PotionContents oldPotion = stack.get(DataComponents.POTION_CONTENTS);
        Optional<Holder<Potion>> basePotion = oldPotion == null
                ? Optional.empty() : oldPotion.potion();
        Optional<Integer> customColor = oldPotion == null
                ? Optional.empty() : oldPotion.customColor();

        if (!consumable) {
            stack.remove(DataComponents.FOOD);
            stack.remove(DataComponents.POTION_CONTENTS);
        } else {
            FoodProperties previous = stack.get(DataComponents.FOOD);

            List<MobEffectInstance> resolved = new ArrayList<>();
            for (Effect effect : effects) {
                ResourceLocation id = parse(effect.id());
                Holder<MobEffect> mob = id == null ? null
                        : BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
                if (mob == null) continue;
                resolved.add(new MobEffectInstance(mob, Math.max(1, effect.durationTicks()),
                        clamp(effect.amplifier(), 0, 255), effect.ambient(), effect.visible(),
                        effect.showIcon()));
            }

            // Ordinary food reads its effects from FOOD, not from POTION_CONTENTS. Writing only the
            // potion component made a list that a potion item would use but that eating ignored, so
            // the same instances go into both lists. The potion component is still written because
            // a potion-like item's own use path reads it, and the old base potion and colour travel
            // through unchanged; the chance of a food effect is one, because the screen has no
            // probability field to lose.
            List<FoodProperties.PossibleEffect> foodEffects = new ArrayList<>();
            for (MobEffectInstance effect : resolved) {
                foodEffects.add(new FoodProperties.PossibleEffect(() -> effect, 1.0f));
            }
            stack.set(DataComponents.FOOD, new FoodProperties(
                    Math.max(0, nutrition), Math.max(0.0f, saturation), canAlwaysEat,
                    Math.max(0.05f, eatSeconds),
                    previous == null ? Optional.empty() : previous.usingConvertsTo(),
                    foodEffects));

            if (resolved.isEmpty() && basePotion.isEmpty() && customColor.isEmpty()) {
                stack.remove(DataComponents.POTION_CONTENTS);
            } else {
                stack.set(DataComponents.POTION_CONTENTS,
                        new PotionContents(basePotion, customColor, resolved));
            }
        }

        if (!durability) {
            stack.remove(DataComponents.MAX_DAMAGE);
            stack.remove(DataComponents.DAMAGE);
            stack.remove(DataComponents.UNBREAKABLE);
        } else {
            int maximum = Math.max(1, maxDamage);
            stack.set(DataComponents.MAX_DAMAGE, maximum);
            stack.set(DataComponents.DAMAGE,
                    clamp(maximum - clamp(currentDurability, 0, maximum), 0, maximum));
            if (unbreakable) {
                stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            } else {
                stack.remove(DataComponents.UNBREAKABLE);
            }
        }

        if (dyed) {
            stack.set(DataComponents.DYED_COLOR,
                    new DyedItemColor(dyedColor & 0xFFFFFF, true));
        } else {
            stack.remove(DataComponents.DYED_COLOR);
        }

        if (enchantments) {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            Registry<Enchantment> registry = server == null ? null
                    : server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            for (EnchantmentLevel line : enchantmentLevels) {
                ResourceLocation id = parse(line.id());
                Holder<Enchantment> enchantment = registry == null || id == null
                        ? null : registry.getHolder(id).orElse(null);
                if (enchantment == null || line.level() <= 0) continue;
                mutable.set(enchantment, clamp(line.level(), 1, 255));
            }
            stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        } else {
            stack.remove(DataComponents.ENCHANTMENTS);
        }

        if (fireResistant) {
            stack.set(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
        } else {
            stack.remove(DataComponents.FIRE_RESISTANT);
        }
        if (glint) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else if (hasEnchantments(stack)) {
            // Removing the override on an enchanted item falls back to the default glint, so
            // "off" has to be an explicit false. On an unenchanted item there is nothing to
            // suppress and the component can go.
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        } else {
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
        HiddenEnchantments.set(stack, hideEnchants);
        Unplaceable.set(stack, cannotBePlaced);
        // The old editor wrote this vanilla flag for the same checkbox. It does not hide
        // enchantments, but it does hide the item's own additional details; the filter in
        // HiddenEnchantments replaces it, so a stack edited now must not keep both behaviours.
        stack.remove(DataComponents.HIDE_ADDITIONAL_TOOLTIP);

        // A damageable item cannot stack. The screen sets the field to one when durability is
        // ticked, and this is the same rule on the way in rather than a second, softer one.
        int size = durability ? 1 : clamp(maxStackSize, 1, 99);
        if (misc || durability) {
            stack.set(DataComponents.MAX_STACK_SIZE, size);
        }
    }

    /** {@return true when either enchantment component on the stack has an entry} */
    private static boolean hasEnchantments(ItemStack stack) {
        ItemEnchantments normal = stack.get(DataComponents.ENCHANTMENTS);
        if (normal != null && !normal.isEmpty()) return true;
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        return stored != null && !stored.isEmpty();
    }

    /** {@return why this draft cannot be written, or null when it can} */
    public String problem() {
        if (consumable) {
            if (nutrition < 0) return "Nutrition cannot be negative.";
            if (saturation < 0.0f) return "Saturation cannot be negative.";
            if (eatSeconds <= 0.0f || eatSeconds > 60.0f) {
                return "Eating time must be between 0.05 and 60 seconds.";
            }
            if (effects.size() > 32) return "An item carries at most 32 custom effects.";
            for (Effect effect : effects) {
                if (effect.id() == null || effect.id().isBlank() || effect.id().length() > 128) {
                    return "Every effect needs a valid effect id.";
                }
                if (effect.durationTicks() <= 0) return "Every effect needs a duration.";
                if (effect.amplifier() < 0 || effect.amplifier() > 255) {
                    return "An effect level must be between 1 and 256.";
                }
            }
        }
        if (durability) {
            if (maxDamage <= 0 || maxDamage > 1_000_000) {
                return "Maximum durability must be between 1 and 1000000.";
            }
            if (currentDurability < 0 || currentDurability > maxDamage) {
                return "Current durability must be between 0 and the maximum.";
            }
        }
        if (dyedColor < 0 || dyedColor > 0xFFFFFF) return "The colour is not a 24-bit RGB value.";
        if (enchantments) {
            if (enchantmentLevels.size() > 32) return "An item carries at most 32 enchantments.";
            for (EnchantmentLevel line : enchantmentLevels) {
                if (line.id() == null || line.id().isBlank() || line.id().length() > 128) {
                    return "Every enchantment needs a valid enchantment id.";
                }
                if (line.level() < 1 || line.level() > 255) {
                    return "An enchantment level must be between 1 and 255.";
                }
            }
        }
        if (maxStackSize < 1 || maxStackSize > 99) {
            return "Maximum stack size must be between 1 and 99.";
        }
        if (durability && maxStackSize > 1) {
            return "An item with durability cannot have a maximum stack size above 1.";
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ResourceLocation parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ResourceLocation.parse(raw);
        } catch (RuntimeException malformed) {
            return null;
        }
    }
}
