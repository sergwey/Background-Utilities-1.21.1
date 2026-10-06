package net.xlebupaksa.backutils.freeze;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.network.FreezeNetwork;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Holding a player still, the way a suspension does.
 *
 * <p>Asked for by an operator rather than chosen by the player, so it is <b>the server's answer that
 * counts</b>: a client that ignores the message is a client that is put back where it was, every tick,
 * by the code below. What the message and the attributes buy is that an ordinary client does not have
 * to be corrected — see {@link #hold}.
 *
 * <p>Movement is not the only thing a suspension is for. A player who cannot walk can still break the
 * block they are standing in, hit the player beside them and eat a golden apple, so each of those is
 * refused as well: the point of holding somebody is that nothing about the world changes while it
 * lasts.
 *
 * <p>The state is kept here, in memory, and deliberately not written down: a suspension is a decision
 * about a player who is on the server now, and a restart that outlives the operator who asked for it
 * would leave somebody stuck with nobody to ask. What it does outlive is the player's own session —
 * logging out and back in does not release them, which is the whole reason it is kept by identity.
 */
public final class FreezeState {

    /**
     * What is applied to stop a player moving themselves.
     *
     * <p>A modifier on the movement speed and the jump strength rather than a position pin alone,
     * because the client moves itself before the server is asked: it computes its own speed from its
     * own attribute map, and the map it holds is the one the server sent it. Multiplying the total by
     * zero therefore stops the player <em>at home</em>, with nothing to correct and nothing to see,
     * and a jump strength of zero stops the one movement that does not go through the speed.
     *
     * <p>Transient, so nothing is written to the player's data and removing the modifier is the whole
     * of the release.
     */
    private static final AttributeModifier STOP = new AttributeModifier(
            ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "frozen"),
            -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /** The players being held, by identity: an account is the thing that is suspended. */
    private static final Set<UUID> FROZEN = new HashSet<>();

    /** Where each of them was standing when they were, so nothing they send can move them. */
    private static final Map<UUID, Vec3> PINS = new HashMap<>();

    /** {@return true when this player is being held} */
    public static boolean frozen(UUID player) {
        return player != null && FROZEN.contains(player);
    }

    /** {@return how many players are being held}, for the command that says what it did */
    public static int held() {
        return FROZEN.size();
    }

    /** Holds a player where they stand, along with everything they could otherwise do. */
    public static void freeze(ServerPlayer player) {
        FROZEN.add(player.getUUID());
        PINS.put(player.getUUID(), player.position());
        hold(player);
        FreezeNetwork.send(player, true);
        player.displayClientMessage(Component.translatable("backutils.freeze.held"), false);
    }

    /** Lets a player go, leaving them exactly where they were held. */
    public static void unfreeze(ServerPlayer player) {
        FROZEN.remove(player.getUUID());
        PINS.remove(player.getUUID());
        release(player);
        FreezeNetwork.send(player, false);
        player.displayClientMessage(Component.translatable("backutils.freeze.released"), false);
    }

    /**
     * Holds every frozen player still, and puts back whatever they are missing.
     *
     * <p>Run before the player's own tick, so the position written here is the one the movement code
     * then works from rather than one it overwrites afterwards. The pin is the answer to a client that
     * is not ours or does not care; the attributes are what keep an ordinary client from ever needing
     * it.
     */
    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!FROZEN.contains(player.getUUID())) return;

        Vec3 pinned = PINS.get(player.getUUID());
        if (pinned == null) {
            // Held by a session that has since restarted: the position they are in now is the only
            // honest one to keep them at, and it is better than releasing them on a silence.
            PINS.put(player.getUUID(), player.position());
            hold(player);
            return;
        }

        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(pinned.x, pinned.y, pinned.z);
        player.resetFallDistance();
        // Asked for again every tick because a modifier can go missing without this class being told:
        // a death, a dimension change and a relog each hand the player a body whose attributes were
        // built from scratch. Checking is a lookup and putting it back is what makes the hold hold.
        hold(player);
    }

    /**
     * Puts a player who has just been given a new body or a new place to stand back under hold.
     *
     * <p>All three of the moments below are the same act: whatever was true a moment ago was true of a
     * different player object, and the pin has to be taken again from where they are now — a
     * suspension does not teleport somebody back to where they were before they died.
     */
    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        reapply(event.getEntity());
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        reapply(event.getEntity());
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        reapply(event.getEntity());
    }

    private static void reapply(Player player) {
        if (!(player instanceof ServerPlayer server)) return;
        if (!FROZEN.contains(player.getUUID())) return;
        PINS.put(player.getUUID(), player.position());
        hold(server);
        // Told again, because all three of these moments can hand the player a client that has never
        // heard of the hold: a relog is a new client outright, and the message is the only thing that
        // stops it predicting the fall of a player the server is not letting fall.
        FreezeNetwork.send(server, true);
    }

    /** Stops a player moving themselves, at the source they compute movement from. */
    private static void hold(ServerPlayer player) {
        stop(player.getAttribute(Attributes.MOVEMENT_SPEED));
        stop(player.getAttribute(Attributes.JUMP_STRENGTH));
        player.setNoGravity(true);
    }

    /** Gives a player their own movement back. */
    private static void release(ServerPlayer player) {
        player.setNoGravity(false);
        // Removed rather than replaced by the ordinary value: the modifier was the only change this
        // class made, and anything else that has added to these attributes since is not ours to undo.
        remove(player.getAttribute(Attributes.MOVEMENT_SPEED));
        remove(player.getAttribute(Attributes.JUMP_STRENGTH));
    }

    private static void stop(AttributeInstance attribute) {
        if (attribute == null || attribute.getModifier(STOP.id()) != null) return;
        attribute.addTransientModifier(STOP);
    }

    private static void remove(AttributeInstance attribute) {
        if (attribute != null) attribute.removeModifier(STOP.id());
    }

    // ------------------------------------------------------------------
    // What a held player may not do
    // ------------------------------------------------------------------

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        refuse(event);
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        refuse(event);
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        refuse(event);
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        refuse(event);
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        refuse(event);
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onAttack(AttackEntityEvent event) {
        if (frozen(event.getEntity().getUUID())) event.setCanceled(true);
    }

    /**
     * Refuses a broken block.
     *
     * <p>Separate from the left-click above because that is a click and this is the block actually
     * giving way: a client that has already sent the click, or one that started breaking a block
     * before it was held, arrives here with the block half gone.
     */
    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onBreak(BlockEvent.BreakEvent event) {
        if (frozen(event.getPlayer().getUUID())) event.setCanceled(true);
    }

    /**
     * Refuses one of the things a player does with their hands.
     *
     * <p>The event is asked whether it is one that can be refused rather than the type being repeated
     * in every handler: {@code PlayerInteractEvent} is the family, and only its members are
     * cancellable — the family itself is what all of them have in common and not a thing the game
     * posts.
     */
    private static void refuse(PlayerInteractEvent event) {
        if (!frozen(event.getEntity().getUUID())) return;
        if (event instanceof ICancellableEvent cancellable) cancellable.setCanceled(true);
    }
}
