package net.xlebupaksa.backutils.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.EffectData;
import net.xlebupaksa.backutils.data.EffectPlacement;
import net.xlebupaksa.backutils.data.PlacedEffect;
import net.xlebupaksa.backutils.effect.EffectAnchor;
import net.xlebupaksa.backutils.item.EffectToolConfig.Mode;
import net.xlebupaksa.backutils.item.EffectToolItem;
import net.xlebupaksa.backutils.item.EffectToolPlacement;
import net.xlebupaksa.backutils.profile.ProfileLoader;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Both ends of the effect tool's configuration traffic.
 *
 * <p>The server is the side that owns a stack, so every check that decides whether an edit happens
 * is made there, on the way in. The sender is read from the connection, so a payload cannot name
 * somebody else's tool, and the slot it names is looked up in the menu the player has open rather
 * than in anything the payload carries.
 *
 * <p>Registered optional like the profile and log traffic, so a client without these payloads still
 * connects and simply cannot see the screen's edits reach the server.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class EffectToolNetwork {

    /**
     * The level an operator needs to configure a tool: a player who may not be handed one is not one
     * who may rewrite one either.
     */
    public static final int REQUIRED_LEVEL = ProfileLoader.PROFILE_PERMISSION_LEVEL;

    /**
     * How far past the reach an operator's own settings allow a claimed placement may land, in
     * blocks.
     *
     * <p>The server cannot know what the client's crosshair met, so the position it is sent is a
     * claim and this is what keeps it honest. The furthest a tool could have aimed is the longer of
     * its two ways of aiming — a distance into the air, or the range an entity may be attached at —
     * and a claim beyond that is a claim about somewhere the tool could not have been pointed. The
     * slack is generous on purpose: the two ends measure from their own ticks, a block placement is
     * named by the block's corner rather than by the point inside it that was aimed at, and a
     * placement refused for being a fraction of a block too far would read as the tool randomly not
     * working.
     *
     * <p>Public because the delete tool picks within the same allowance. What the client offers to
     * remove has to be what this server will agree it could have been looking at, and a second,
     * narrower figure on the client would leave a cube drawn red that a click cannot take away.
     */
    public static final double REACH_SLACK = 4.0D;

    /**
     * How near an effect has to be for a client's word that it ended to be acted on, in blocks.
     *
     * <p>A client reports an ending because it can see one, and an effect is drawn by the clients
     * within sight of it: this is that distance, generously taken. It is a bound rather than a
     * permission because the report is housekeeping — an operator is not always watching when an
     * effect plays out, and a row that outlives its effect is replayed to every player who arrives
     * afterwards, which is the same effect playing again for ever.
     */
    private static final double ENDED_REPORT_RANGE = 96.0D;

    /** How many milliseconds a tick is, for turning a configured lifetime into an instant. */
    private static final long MILLIS_PER_TICK = 50L;

    private EffectToolNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();

        registrar.playToServer(
                EffectToolConfigPayload.TYPE,
                EffectToolConfigPayload.STREAM_CODEC,
                EffectToolNetwork::onConfig);

        registrar.playToServer(
                EffectPlacePayload.TYPE,
                EffectPlacePayload.STREAM_CODEC,
                EffectToolNetwork::onPlace);

        registrar.playToServer(
                EffectDeletePayload.TYPE,
                EffectDeletePayload.STREAM_CODEC,
                EffectToolNetwork::onDelete);

        registrar.playToClient(
                EffectToolFeedbackPayload.TYPE,
                EffectToolFeedbackPayload.STREAM_CODEC,
                EffectToolNetwork::onFeedback);

        registrar.playToClient(
                EffectPlacedPayload.TYPE,
                EffectPlacedPayload.STREAM_CODEC,
                EffectToolNetwork::onPlaced);

        registrar.playToClient(
                EffectRemovedPayload.TYPE,
                EffectRemovedPayload.STREAM_CODEC,
                EffectToolNetwork::onRemoved);

        registrar.playToServer(
                EffectEndedPayload.TYPE,
                EffectEndedPayload.STREAM_CODEC,
                EffectToolNetwork::onEnded);
    }

    // The client's side

    /** Sends one edit. The server decides whether it happens. */
    public static void send(EffectToolConfigPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /**
     * Sends one placement claim. The server decides whether anything is placed.
     *
     * <p>Named apart from {@link #send} rather than overloaded onto it, because the two carry
     * different things: an edit is about the tool in a slot, and a claim is about the world.
     */
    public static void sendPlacement(EffectPlacePayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /**
     * Sends one deletion claim. The server decides what it removes.
     *
     * <p>Named apart from the other two senders for the same reason they are named apart from each
     * other: an edit is about the tool in a slot, a placement is about the world, and a deletion is
     * about what is already in it.
     */
    public static void sendDeletion(EffectDeletePayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /**
     * Reports that an effect this client was drawing has ended on its own.
     *
     * <p>Sent rather than kept quiet: the effect is over, so the row that described it describes
     * nothing and the display it hung off is the server's to take away. A server that never hears
     * this would hold an effect nobody can see for as long as the world lasts.
     */
    public static void sendEnded(EffectEndedPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Hands the answer to the open screen, which is the only thing that asked for one. */
    private static void onFeedback(EffectToolFeedbackPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        net.xlebupaksa.backutils.ui.EffectToolScreen.onServerAnswer(payload.reason());
    }

    // The server's side

    private static void onConfig(EffectToolConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        if (!player.hasPermissions(REQUIRED_LEVEL)) {
            answer(player, EffectToolFeedbackPayload.Reason.NOT_PERMITTED);
            return;
        }

        Slot slot = addressedSlot(player, payload);
        if (slot == null || !(slot.getItem().getItem() instanceof EffectToolItem)) {
            // The numbers are worth logging: this refusal is the only symptom of a wrong address, and the
            // client's screen can say no more than that the server refused. A menu the client had and the
            // server no longer has, an index left over from that menu, and a slot that has since been
            // emptied all arrive here looking identical.
            BackUtils.LOGGER.info("[effecttool] slot check failed for {}: payload said where={} container={} slot={}, "
                            + "the server has containerMenu={} inventoryMenu={}, addressed={}, which holds={}",
                    player.getGameProfile().getName(), payload.where(), payload.containerId(), payload.slot(),
                    player.containerMenu.containerId, player.inventoryMenu.containerId,
                    slot == null ? "nothing" : slot.index,
                    slot == null ? "nothing" : slot.getItem().getItem());
            answer(player, EffectToolFeedbackPayload.Reason.SLOT_CHANGED);
            return;
        }

        // The item's own clamp is the border every value goes through, whether it arrived from a
        // world, from a server operator or from a client that wrote its own numbers.
        ItemStack stored = slot.getItem().copy();
        EffectToolItem.setConfig(stored, payload.config());
        slot.set(stored);
        slot.setChanged();

        // The slot is sent before the answer, so a screen that reloads when it hears one reads the
        // values the server holds rather than the ones it sent.
        player.containerMenu.broadcastChanges();
        if (player.inventoryMenu != player.containerMenu) player.inventoryMenu.broadcastChanges();
        answer(player, EffectToolFeedbackPayload.Reason.STORED);
    }

    /**
     * {@return the slot an edit names, or null when this server cannot be told about it}
     *
     * <p>Which menu the index counts in is what the payload says, and the id is then checked against
     * that menu's own: an index is only meaningful against the container it was read from, so a menu
     * the player no longer has open means the slot cannot be found.
     *
     * <p>The player's own inventory menu is the one address that always works, because both sides
     * always have it. That is what makes a tool configurable from the creative screen, whose menu is
     * built on the client and never exists here — and which takes the same id as this one, so being
     * told which of the two is meant is the whole of what makes the index readable.
     */
    private static Slot addressedSlot(ServerPlayer player, EffectToolConfigPayload payload) {
        return AddressedSlots.of(player, payload.where(), payload.containerId(), payload.slot());
    }

    /**
     * Settles one placement: the server writes it down and tells everyone who should see it.
     *
     * <p>A self effect is the one placement that is not written down. It attaches to the player who
     * made it, so their absence is what ends it, and a row would outlive the thing it described. It
     * is still sent, and only to that player: nobody else's client could attach an effect to them.
     */
    private static void onPlace(EffectPlacePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(REQUIRED_LEVEL)) return;

        Mode mode = payload.mode();
        boolean self = mode == Mode.SELF || mode == Mode.SELF_AUTO_ROTATE;
        if (!EffectPlacement.storable(mode) && !self) return;
        if (payload.config().effectPath().isBlank()) return;
        if (outOfReach(player, payload)) return;

        String dimension = player.level().dimension().location().toString();
        EffectPlacement where = self ? null
                : new EffectPlacement(mode, dimension, payload.x(), payload.y(),
                        payload.z(), payload.yaw(), payload.pitch(), payload.face());

        int targetId = payload.targetEntityId();
        if (self) {
            // Only the placer's own client: an effect attached to them is nobody else's to draw.
            send(player, new EffectPlacedPayload(0L, payload.x(), payload.y(), payload.z(),
                    payload.yaw(), payload.pitch(), player.getId(), payload.face(),
                    payload.config()));
            return;
        }

        EffectData store = BackUtils.data() == null ? null : BackUtils.data().effects();
        if (store == null) return;

        // An accurate placement hangs off a blank display this server puts in the world, which is
        // what the spec asks for and what makes it the same display for everybody: one a client made
        // would be that client's alone, would not survive a reload, and could not be found or moved
        // by anything else. A server that cannot make one places nothing rather than an effect with
        // nothing to hang off.
        String anchor = "";
        if (mode == Mode.ACCURATE) {
            UUID placed = EffectAnchor.place(player.serverLevel(), payload.x(), payload.y(),
                    payload.z());
            if (placed == null) return;
            anchor = placed.toString();
        }

        long now = System.currentTimeMillis();
        long expiresAt = payload.config().isInfinite()
                ? PlacedEffect.PERMANENT
                : now + (long) payload.config().lifetimeTicks() * MILLIS_PER_TICK;

        // Which entity the effect is on, written down by identity. An entity id is handed out per
        // session and means nothing after a restart, and the position a row records is only where the
        // entity stood — so without this there is no way to answer "which effects are on this mob",
        // and an effect attached to one could never be sent again when the mob came back into view.
        String target = "";
        if (mode == Mode.ENTITY || mode == Mode.ENTITY_AUTO_ROTATE) {
            Entity aimed = player.serverLevel().getEntity(payload.targetEntityId());
            if (aimed == null) return;
            target = aimed.getUUID().toString();
            targetId = aimed.getId();
        }

        PlacedEffect stored = store.add(where, player.getUUID().toString(),
                player.getName().getString(), payload.config().effectPath(), payload.config(),
                anchor, target, now, expiresAt);

        // The display, not the point, is what the effect is attached to, so what the clients are
        // told is the entity rather than a position they would each have to find something to hang
        // it on.
        if (!anchor.isEmpty()) {
            Entity display = EffectAnchor.find(player.serverLevel(), anchor);
            if (display != null) targetId = display.getId();
        }

        EffectPlacedPayload out = new EffectPlacedPayload(stored.id(), payload.x(), payload.y(),
                payload.z(), payload.yaw(), payload.pitch(), targetId, payload.face(),
                payload.config());
        for (ServerPlayer witness : player.serverLevel().players()) {
            send(witness, out);
        }
    }

    /**
     * Settles one deletion: everything the claim names goes, row by row through {@link #forget}.
     *
     * <p>Gated at the level that may configure a tool, for the reason the placement is: taking an
     * effect away is as much a change to the world as putting one there, and the level that may be
     * handed a tool is the one that may use it. The gate is on the sender's own permission and not on
     * the tool, because a client can claim to be holding anything.
     *
     * <p>A refusal is silent, as every other refusal here is: the operator sees the effects stay
     * where they are, and nothing is said to a player who has just tried something the server does
     * not permit.
     */
    private static void onDelete(EffectDeletePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(REQUIRED_LEVEL)) return;

        EffectData store = BackUtils.data() == null ? null : BackUtils.data().effects();
        if (store == null) return;

        MinecraftServer server = player.getServer();
        switch (payload.kind()) {
            case BLOCK -> {
                EffectPlacement where = EffectPlacement.atBlock(
                        player.level().dimension().location().toString(),
                        payload.x(), payload.y(), payload.z());
                // A place is named by coordinates, which is the one thing here the server cannot
                // derive: the mode, the settings and the rows all come from the table. What bounds it
                // is the same thing that bounds a placement — the crosshair a click was made with
                // only reaches as far as the player can — so a block named from further off than
                // that is not one the tool could have been pointed at.
                if (!withinReach(player, payload.x(), payload.y(), payload.z())) return;
                forgetWhere(server, store, effect -> effect.sameBlockAs(where));
            }
            case MINE -> forgetWhere(server, store,
                    effect -> effect.ownedBy(player.getUUID().toString()));
            case EFFECTS -> {
                // Named by row, because a row records where an entity stood and not which entity it
                // was: the client's own registry is the only thing that knows an effect hangs off an
                // entity, so the rows it is drawing are the only ones it can name. Only rows of the
                // level the sender is standing in are removed — that is the level the tool draws,
                // and therefore the only one a click can be about — and an id that names nothing is
                // ignored rather than refused, since an effect already gone is gone.
                String dimension = player.level().dimension().location().toString();
                for (PlacedEffect effect : store.all()) {
                    if (effect.placement().dimension().equals(dimension)
                            && payload.effectIds().contains(effect.id())) {
                        forget(server, store, effect.id());
                    }
                }
            }
            default -> {
                // Nothing was named, which is what a kind this build does not know reads as.
            }
        }
    }

    /**
     * Removes every effect of a store that a test picks out, one row at a time through
     * {@link #forget}.
     *
     * <p>Walked and removed one by one rather than deleted with a single statement, because a
     * placement is three things at once — a row, the display it hung off, and the effect every
     * client drawing it has — and {@link #forget} is the only thing that takes all three apart
     * together. A statement that emptied a block outright would leave the displays standing in the
     * world and the effects running on every client that could see them.
     *
     * <p>{@code all()} is read once, into a list, before the walk begins: it answers with a copy of
     * the table, so removing rows while walking that list cannot disturb the walk.
     */
    private static void forgetWhere(MinecraftServer server, EffectData store,
                                    Predicate<PlacedEffect> match) {
        for (PlacedEffect effect : store.all()) {
            if (match.test(effect)) forget(server, store, effect.id());
        }
    }

    /**
     * Takes away an effect whose own lifetime has run out.
     *
     * <p>Reported by a client, because only a client can see that an effect's timeline has finished.
     * The row and the display go together: the display existed to give the effect somewhere to be,
     * and a display left behind would be a prop nobody asked for.
     *
     * <p><b>Gated at the level every other removal is.</b> Nothing in the message says the sender
     * has any business with the effect it names — the id is a claim like any other, and a payload
     * can be sent by a client that has been modified — so without this gate any player could empty
     * the world of effects one id at a time. The cost is that a world where no operator is watching
     * keeps a row for an effect that has already played out; that row is replayed once to a player
     * who arrives later and reported by them in turn, which is a waste rather than a loss, and it is
     * the better side of the trade by a long way.
     */
    private static void onEnded(EffectEndedPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        EffectData store = BackUtils.data() == null ? null : BackUtils.data().effects();
        if (store == null) return;

        // The claim is bounded instead of gated: a report is worth acting on only for an effect the
        // reporter could be watching, so the row has to be in this player's own dimension and near
        // enough to be on their screen. That is what stops any client emptying the world of effects
        // one id at a time, without the cost a permission gate had — an operator is not always
        // watching when an effect finishes, and a row that outlives its effect is replayed to every
        // player who arrives afterwards, which is an effect that plays again and again for ever.
        for (PlacedEffect effect : store.all()) {
            if (effect.id() != payload.effectId()) continue;
            if (!effect.placement().dimension()
                    .equals(player.level().dimension().location().toString())) {
                return;
            }
            Vec3 eye = player.getEyePosition();
            double dx = effect.placement().x() - eye.x;
            double dy = effect.placement().y() - eye.y;
            double dz = effect.placement().z() - eye.z;
            if (dx * dx + dy * dy + dz * dz > ENDED_REPORT_RANGE * ENDED_REPORT_RANGE) return;

            forget(player.getServer(), store, payload.effectId());
            return;
        }
    }

    /**
     * Removes one effect, its row and whatever it hung off, and tells the clients to stop drawing it.
     *
     * <p>The one place a placement stops existing, so the three things a placement is — a row, a
     * display in the world, and an effect on every client that can see it — cannot come apart.
     */
    public static void forget(MinecraftServer server, EffectData store, long effectId) {
        for (PlacedEffect effect : store.all()) {
            if (effect.id() != effectId) continue;
            store.remove(effectId);
            release(server, effect);
            broadcastRemoval(server, effectId);
            return;
        }
    }

    /** Takes away the display a removed effect hung off, if it had one and it is still there. */
    private static void release(MinecraftServer server, PlacedEffect effect) {
        if (server == null || effect.anchorUuid() == null || effect.anchorUuid().isBlank()) return;
        ServerLevel level = EffectAnchor.levelOf(server, effect.placement().dimension());
        if (level == null) return;
        // The effect's own position, because the display's ground may not be loaded: the lookup that
        // takes it away loads that ground first, and it has to be told where to look.
        EffectAnchor.remove(level, effect.anchorUuid(), effect.placement().centreX(),
                effect.placement().centreY(), effect.placement().centreZ());
    }

    /**
     * Tells every client drawing an effect that it is over.
     *
     * <p>The dimension is not asked about: an effect is only ever drawn by a client that was sent it
     * in the first place, and a client that never had it answers an id it does not know with
     * nothing. Narrowing it to one dimension would be a guess about which clients have it, and a
     * wrong guess leaves an effect being drawn by somebody forever.
     */
    private static void broadcastRemoval(MinecraftServer server, long effectId) {
        if (server == null) return;
        EffectRemovedPayload payload = new EffectRemovedPayload(effectId);
        for (ServerPlayer witness : server.getPlayerList().getPlayers()) {
            if (!OptionalChannels.negotiated(witness, EffectRemovedPayload.TYPE)) continue;
            PacketDistributor.sendToPlayer(witness, payload);
        }
    }

    /**
     * {@return true when a claimed position is within the reach a click could have had}
     *
     * <p>Measured from the eye to the block's own corner, with the slack a placement is given and
     * for the same reason: the two ends read their own ticks, a block is named by its corner rather
     * than by the point on it that was aimed at, and a claim refused for being a fraction of a block
     * too far would read as the tool randomly not working.
     */
    private static boolean withinReach(ServerPlayer player, double x, double y, double z) {
        double allowed = player.blockInteractionRange() + REACH_SLACK;
        Vec3 eye = player.getEyePosition();
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
        return dx * dx + dy * dy + dz * dz <= allowed * allowed;
    }

    /**
     * {@return true when a claimed placement is further off than the tool could have reached}
     *
     * <p>Measured from the eye, which is where both ways of aiming start. The claim is the one thing
     * a client could lie about that the server cannot check directly: the mode, the settings and the
     * face all come from the tool the server itself holds for the player, and the dimension is not
     * sent at all. What is left is a distance, and the distance an honest tool could have named is
     * bounded by the player's own reach rather than by anything the client said.
     */
    private static boolean outOfReach(ServerPlayer player, EffectPlacePayload payload) {
        double air = EffectToolPlacement.midAirDistance(payload.config(),
                player.blockInteractionRange());
        double entity = EffectToolPlacement.entityRange(payload.config(),
                player.entityInteractionRange());
        double allowed = Math.max(air, entity) + REACH_SLACK;

        Vec3 eye = player.getEyePosition();
        double dx = payload.x() - eye.x;
        double dy = payload.y() - eye.y;
        double dz = payload.z() - eye.z;
        return dx * dx + dy * dy + dz * dz > allowed * allowed;
    }

    /** Hands one placement to the client that draws it, if that client can hear this channel. */
    private static void send(ServerPlayer player, EffectPlacedPayload payload) {
        if (!OptionalChannels.negotiated(player, EffectPlacedPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * Hands one already-stored placement to one client, which is how a player is brought up to date.
     *
     * <p>Public because a replay is a different act from placing: nothing is written down and nothing
     * is checked, because the row it describes is one this server already holds and the player is
     * simply the one being told about it.
     */
    public static void sendPlaced(ServerPlayer player, EffectPlacedPayload payload) {
        send(player, payload);
    }

    /**
     * Stops one client drawing an effect.
     *
     * <p>Reached only on a client, and only does anything when Photon is present: everything that
     * names a Photon class lives behind {@link net.xlebupaksa.backutils.client.PlacedEffects}, which
     * a dedicated server never loads.
     */
    private static void onRemoved(EffectRemovedPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        net.xlebupaksa.backutils.client.PlacedEffects.remove(payload.effectId());
    }

    /**
     * Draws one placed effect.
     *
     * <p>A placement is not reported anywhere when it cannot be placed — the owner's decision,
     * because an effect path is typed by hand and a typo should cost the effect rather than fill a
     * log.
     */
    private static void onPlaced(EffectPlacedPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        net.xlebupaksa.backutils.client.PlacedEffects.accept(payload);
    }

    /** Records the answer and, when the client can hear it, sends it to the screen that asked. */
    private static void answer(ServerPlayer player, EffectToolFeedbackPayload.Reason reason) {
        if (reason.stored()) {
            BackUtils.LOGGER.info("[effecttool] {}: {}", player.getName().getString(), reason);
        } else {
            BackUtils.LOGGER.info("[effecttool] refused for {}: {}",
                    player.getName().getString(), reason);
        }
        if (!canReceive(player)) return;
        PacketDistributor.sendToPlayer(player, new EffectToolFeedbackPayload(reason));
    }

    /** {@return true when this client negotiated the effect tool channel} */
    public static boolean canReceive(ServerPlayer player) {
        return OptionalChannels.negotiated(player, EffectToolFeedbackPayload.TYPE);
    }
}
