package net.xlebupaksa.backutils.item;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/**
 * The commands an item runs when it is used as a weapon, used, or eaten.
 *
 * <p>The three triggers are kept apart because they have different meanings and only two of them
 * have a cooldown. The commands are a list rather than one string: order is part of the behaviour,
 * and a list is the thing both the editor and {@link #run} can preserve.
 *
 * <p>The data lives in {@code minecraft:custom_data}, next to the other item editor markers, so it
 * travels with the stack, survives a save and does not need a component of its own. Like those
 * markers it is written with a copy-on-write update, leaving every unrelated custom-data key alone.
 */
public final class ItemBehaviour {

    public static final String KEY = "backutils:item_behaviour";
    /** Ready-at game times for a stack's triggers. Not part of the behaviour payload. */
    public static final String COOLDOWN_KEY = "backutils:behaviour_cooldowns";
    public static final int MAX_COMMANDS = 32;
    public static final int MAX_COMMAND_LENGTH = 256;
    public static final float MAX_COOLDOWN_SECONDS = 3600.0f;

    private static final String ENABLED = "enabled";
    private static final String COOLDOWN = "cooldown";
    private static final String COMMANDS = "commands";
    private static final String TARGET_COMMANDS = "target_commands";

    public final Trigger attack = new Trigger(1.0f);
    public final Trigger use = new Trigger(1.0f);
    public final Trigger eaten = new Trigger(0.0f);

    /** {@return the triggers in the order the behaviour tab shows them} */
    public List<Trigger> all() {
        return List.of(attack, use, eaten);
    }

    /** {@return true when at least one trigger has anything to store} */
    public boolean isEmpty() {
        for (Trigger trigger : all()) {
            if (trigger.enabled || !trigger.commands.isEmpty()
                    || !trigger.targetCommands.isEmpty()) return false;
        }
        return true;
    }

    /** Copies another behaviour into this one, leaving this object's identity alone. */
    public void copyFrom(ItemBehaviour other) {
        if (other == null) return;
        for (int i = 0; i < all().size(); i++) {
            Trigger from = other.all().get(i);
            Trigger to = all().get(i);
            to.enabled = from.enabled;
            to.cooldownSeconds = from.cooldownSeconds;
            to.commands.clear();
            to.commands.addAll(from.commands);
            to.targetCommands.clear();
            to.targetCommands.addAll(from.targetCommands);
        }
    }

    /** {@return the behaviour stored on a stack, or an empty one when there is none} */
    public static ItemBehaviour from(ItemStack stack) {
        ItemBehaviour out = new ItemBehaviour();
        if (stack == null || stack.isEmpty()) return out;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return out;
        CompoundTag root = data.copyTag().getCompound(KEY);
        readTrigger(root, "attack", out.attack);
        readTrigger(root, "use", out.use);
        readTrigger(root, "eaten", out.eaten);
        return out;
    }

    /**
     * {@return true when a stack's trigger is ready at a game time}
     *
     * <p>The cooldown belongs to the stack, not to the item type. Vanilla's own cooldown is keyed by
     * {@link net.minecraft.world.item.Item}, so using one enchanted sword would put every sword of
     * that type on cooldown; this marker is in the stack's custom data and therefore travels with
     * that one stack. It is deliberately not part of {@link #write}/{@link #read}: a cooldown is
     * transient state, not an edit an operator made.
     */
    public static boolean ready(ItemStack stack, String trigger, long gameTime) {
        if (stack == null || stack.isEmpty()) return true;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return true;
        CompoundTag root = data.copyTag().getCompound(COOLDOWN_KEY);
        return root.getLong(trigger) <= gameTime;
    }

    /** Marks one trigger of one stack as ready again at a game time. */
    public static void setCooldown(ItemStack stack, String trigger, long readyAt) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag root = tag.getCompound(COOLDOWN_KEY);
            root.putLong(trigger, readyAt);
            tag.put(COOLDOWN_KEY, root);
        });
    }

    /** Writes this behaviour onto a stack, or removes the marker when there is nothing to write. */
    public void apply(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        boolean empty = isEmpty();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (empty) {
                tag.remove(KEY);
                return;
            }
            CompoundTag root = new CompoundTag();
            writeTrigger(root, "attack", attack);
            writeTrigger(root, "use", use);
            writeTrigger(root, "eaten", eaten);
            tag.put(KEY, root);
        });
    }

    /** {@return why this behaviour cannot be written, or null when it can} */
    public String problem() {
        for (Trigger trigger : all()) {
            if (Float.isNaN(trigger.cooldownSeconds) || Float.isInfinite(trigger.cooldownSeconds)
                    || trigger.cooldownSeconds < 0.0f
                    || trigger.cooldownSeconds > MAX_COOLDOWN_SECONDS) {
                return "A trigger cooldown must be between 0 and "
                        + (int) MAX_COOLDOWN_SECONDS + " seconds.";
            }
            if (trigger.commands.size() > MAX_COMMANDS) {
                return "A trigger runs at most " + MAX_COMMANDS + " commands.";
            }
            if (trigger.targetCommands.size() > MAX_COMMANDS) {
                return "A target trigger runs at most " + MAX_COMMANDS + " commands.";
            }
            for (String command : trigger.commands) {
                if (command != null && command.length() > MAX_COMMAND_LENGTH) {
                    return "A command is longer than " + MAX_COMMAND_LENGTH + " characters.";
                }
            }
            for (String command : trigger.targetCommands) {
                if (command != null && command.length() > MAX_COMMAND_LENGTH) {
                    return "A target command is longer than " + MAX_COMMAND_LENGTH
                            + " characters.";
                }
            }
        }
        return null;
    }

    /** Writes every trigger, including disabled ones so their commands survive an Apply. */
    public void write(FriendlyByteBuf buf) {
        for (Trigger trigger : all()) {
            buf.writeBoolean(trigger.enabled);
            buf.writeFloat(trigger.cooldownSeconds);
            int count = Math.min(trigger.commands.size(), MAX_COMMANDS);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                String command = trigger.commands.get(i);
                buf.writeUtf(command == null ? "" : command, MAX_COMMAND_LENGTH);
            }
            int targetCount = Math.min(trigger.targetCommands.size(), MAX_COMMANDS);
            buf.writeVarInt(targetCount);
            for (int i = 0; i < targetCount; i++) {
                String command = trigger.targetCommands.get(i);
                buf.writeUtf(command == null ? "" : command, MAX_COMMAND_LENGTH);
            }
        }
    }

    /** Reads the three triggers written by {@link #write}. */
    public static ItemBehaviour read(FriendlyByteBuf buf) {
        ItemBehaviour out = new ItemBehaviour();
        for (Trigger trigger : out.all()) {
            trigger.enabled = buf.readBoolean();
            trigger.cooldownSeconds = Math.max(0.0f,
                    Math.min(MAX_COOLDOWN_SECONDS, buf.readFloat()));
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_COMMANDS) {
                throw new DecoderException("An item behaviour announced " + count + " commands");
            }
            for (int i = 0; i < count; i++) {
                trigger.commands.add(buf.readUtf(MAX_COMMAND_LENGTH));
            }
            int targetCount = buf.readVarInt();
            if (targetCount < 0 || targetCount > MAX_COMMANDS) {
                throw new DecoderException(
                        "An item behaviour announced " + targetCount + " target commands");
            }
            for (int i = 0; i < targetCount; i++) {
                trigger.targetCommands.add(buf.readUtf(MAX_COMMAND_LENGTH));
            }
        }
        return out;
    }

    private static void readTrigger(CompoundTag root, String key, Trigger trigger) {
        if (!root.contains(key, Tag.TAG_COMPOUND)) return;
        CompoundTag tag = root.getCompound(key);
        trigger.enabled = tag.getBoolean(ENABLED);
        trigger.cooldownSeconds = Math.max(0.0f,
                Math.min(MAX_COOLDOWN_SECONDS, tag.getFloat(COOLDOWN)));
        ListTag commands = tag.getList(COMMANDS, Tag.TAG_STRING);
        int count = Math.min(commands.size(), MAX_COMMANDS);
        for (int i = 0; i < count; i++) {
            trigger.commands.add(commands.getString(i));
        }
        ListTag targetCommands = tag.getList(TARGET_COMMANDS, Tag.TAG_STRING);
        int targetCount = Math.min(targetCommands.size(), MAX_COMMANDS);
        for (int i = 0; i < targetCount; i++) {
            trigger.targetCommands.add(targetCommands.getString(i));
        }
    }

    private static void writeTrigger(CompoundTag root, String key, Trigger trigger) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(ENABLED, trigger.enabled);
        tag.putFloat(COOLDOWN, trigger.cooldownSeconds);
        ListTag commands = new ListTag();
        int count = Math.min(trigger.commands.size(), MAX_COMMANDS);
        for (int i = 0; i < count; i++) {
            String command = trigger.commands.get(i);
            commands.add(net.minecraft.nbt.StringTag.valueOf(
                    command == null ? "" : command.substring(0,
                            Math.min(command.length(), MAX_COMMAND_LENGTH))));
        }
        tag.put(COMMANDS, commands);
        ListTag targetCommands = new ListTag();
        int targetCount = Math.min(trigger.targetCommands.size(), MAX_COMMANDS);
        for (int i = 0; i < targetCount; i++) {
            String command = trigger.targetCommands.get(i);
            targetCommands.add(net.minecraft.nbt.StringTag.valueOf(
                    command == null ? "" : command.substring(0,
                            Math.min(command.length(), MAX_COMMAND_LENGTH))));
        }
        tag.put(TARGET_COMMANDS, targetCommands);
        root.put(key, tag);
    }

    /** One trigger's checkbox, cooldown and ordered command list. */
    public static final class Trigger {

        public boolean enabled;
        public float cooldownSeconds;
        public final List<String> commands = new ArrayList<>();
        /**
         * Commands run as the entity that was hit. Kept on every trigger so the data format is
         * uniform, but only the attack trigger exposes and uses it.
         */
        public final List<String> targetCommands = new ArrayList<>();

        private Trigger(float cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds;
        }
    }
}
