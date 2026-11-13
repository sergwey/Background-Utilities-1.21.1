package net.xlebupaksa.backutils.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs an item's behaviour commands on the server.
 *
 * <p>All three triggers execute on the server as the player who owns the action, not as the server
 * console: the command source is the player's own, so its entity, position and rotation are what
 * the command sees. The source is lifted to operator permission because the commands were written
 * by an operator in the item editor; this is what lets an ordinary player use an item that an
 * operator configured without every command failing on its own permissions.
 *
 * <p>The attack and use triggers use the item cooldown, which is per player and per item type. That
 * is the same cooldown the client draws over the hotbar slot, so the operator sees why the trigger
 * is not firing. Eating has no cooldown, as requested.
 */
public final class ItemBehaviourEvents {

    /**
     * The last tick each player's use trigger ran.
     *
     * <p>Holding right-click sends an item-use packet every tick. A trigger with a zero cooldown
     * would otherwise run every tick for one held button, so the same tick and the one after are
     * treated as the same use.
     */
    private static final Map<UUID, Long> LAST_USE_TICK = new HashMap<>();

    /**
     * Behaviour commands are configured by an operator in the item editor, so they run with
     * operator permission even when the player holding the item is not an operator. The source is
     * still that player's own: their entity, position and rotation are what the command sees. This
     * is what lets an item be handed to an ordinary player without every trigger failing on
     * permission.
     */
    private static final int BEHAVIOUR_PERMISSION = 4;

    private ItemBehaviourEvents() {}

    public static void onAttack(AttackEntityEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = player.getMainHandItem();
        run(player, stack, ItemBehaviour.from(stack).attack, true, event.getTarget());
    }

    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled()) return;
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        // A block item's use is its placement, which is handled by onPlace. Running both would
        // execute the same trigger twice for one block.
        if (stack.getItem() instanceof BlockItem) return;
        // An item with a use duration sends a RightClickItem every tick while the button is held.
        // Start handles those once, when the use actually begins.
        if (stack.getUseDuration(player) > 0) return;
        long now = player.level().getGameTime();
        Long last = LAST_USE_TICK.get(player.getUUID());
        if (last != null && now - last < 2) return;
        LAST_USE_TICK.put(player.getUUID(), now);
        run(player, stack, ItemBehaviour.from(stack).use, true, null);
    }

    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        ItemStack stack = event.getItem();
        if (stack.getItem() instanceof BlockItem) return;
        run(player, stack, ItemBehaviour.from(stack).use, true, null);
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem)) stack = player.getOffhandItem();
        if (!(stack.getItem() instanceof BlockItem)) return;
        run(player, stack, ItemBehaviour.from(stack).use, true, null);
    }

    public static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItem();
        // Finish is also fired for things that are used rather than eaten. This trigger is for
        // food, so an item with no food component is left alone.
        if (stack.get(DataComponents.FOOD) == null) return;
        run(player, stack, ItemBehaviour.from(stack).eaten, false, null);
    }

    private static void run(ServerPlayer player, ItemStack stack, ItemBehaviour.Trigger trigger,
            boolean useCooldown, Entity target) {
        boolean hasCommands = !trigger.commands.isEmpty();
        boolean hasTargetCommands = target != null && !trigger.targetCommands.isEmpty();
        if (!trigger.enabled || (!hasCommands && !hasTargetCommands)) return;
        if (useCooldown && player.getCooldowns().isOnCooldown(stack.getItem())) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (hasCommands) {
            execute(server, player.createCommandSourceStack().withPermission(BEHAVIOUR_PERMISSION),
                    trigger.commands);
        }
        if (hasTargetCommands && !target.isRemoved()) {
            execute(server, target.createCommandSourceStack().withPermission(BEHAVIOUR_PERMISSION),
                    trigger.targetCommands);
        }

        if (useCooldown && trigger.cooldownSeconds > 0.0f) {
            int ticks = Math.max(1, Math.round(trigger.cooldownSeconds * 20.0f));
            player.getCooldowns().addCooldown(stack.getItem(), ticks);
        }
    }

    private static void execute(MinecraftServer server,
            net.minecraft.commands.CommandSourceStack source, java.util.List<String> commands) {
        for (String raw : commands) {
            String command = raw == null ? "" : raw.trim();
            if (command.isEmpty()) continue;
            if (command.startsWith("/")) command = command.substring(1);
            server.getCommands().performPrefixedCommand(source, command);
        }
    }
}
