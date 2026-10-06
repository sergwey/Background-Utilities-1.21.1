package net.xlebupaksa.backutils.effect;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.EffectData;
import net.xlebupaksa.backutils.data.EffectPlacement;
import net.xlebupaksa.backutils.data.PlacedEffect;
import net.xlebupaksa.backutils.item.EffectToolConfig;
import net.xlebupaksa.backutils.network.EffectPlacedPayload;
import net.xlebupaksa.backutils.network.EffectToolNetwork;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells a player about the effects that were placed before they could hear about them.
 *
 * <p>This is the gap the spec names in as many words: the effect library's effects are sent to
 * whoever is watching at the time, so an effect placed while a player was elsewhere — or before they
 * logged in, or before the server was last restarted — does not exist for them. The row does, which
 * is why the table exists at all, and this is what turns a row back into an effect on one client.
 *
 * <p>Three moments bring a player to effects they have not been told about: arriving, changing
 * dimension, and respawning. All three are the same act here, because the question is the same —
 * which stored effects are in the level this player is now in, and does this client already have
 * them. A client that already has one replaces it rather than starting it twice: the row's id is what
 * the entry is kept by, so a re-send is the same effect rather than a second one.
 *
 * <p><b>A moment is not the same as a tick.</b> A player is announced as joining before their client
 * has a level to put anything in, and an effect sent into that window is dropped on the floor by the
 * client that could not use it yet. So the work is queued for a second rather than done on the event,
 * which is a delay nobody can perceive and the difference between this working and not.
 */
public class EffectReplay {

    /** How long after arriving the effects are sent, in ticks. */
    private static final int SETTLE_TICKS = 20;

    /** The players waiting to be told, and how many ticks each still has to wait. */
    private final Map<UUID, Integer> waiting = new HashMap<>();

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) queue(player);
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) queue(player);
    }

    /**
     * Queues a player again when they respawn.
     *
     * <p>A death and a respawn is a client that has thrown its level away and been given another one,
     * which is the same state as arriving: whatever it had is gone with the old level, and it has to
     * be told again. A respawn into the same dimension would otherwise be the one way to end up
     * standing in an effect that is not being drawn.
     */
    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) queue(player);
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onServerTick(ServerTickEvent.Post event) {
        if (waiting.isEmpty()) return;

        // Copied before walking, because sending removes from the map as it goes.
        for (UUID id : Map.copyOf(waiting).keySet()) {
            int left = waiting.get(id) - 1;
            if (left > 0) {
                waiting.put(id, left);
                continue;
            }
            waiting.remove(id);
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            // A player who left while waiting is not a failure: there is nobody to tell, and the
            // entry would otherwise wait for an identity that will never come back this session.
            if (player != null) send(player);
        }
    }

    /**
     * Sends the effects in a chunk the moment a player is sent that chunk.
     *
     * <p>This is the case that makes the whole class necessary, and it is worth being exact about: an
     * effect is not a thing the client keeps, it is a thing the client is <em>drawing</em>, and the
     * library gives an effect up as soon as it is out of range. Walking away and walking back is
     * therefore enough to lose one for the rest of the session — the chunk is sent again, the row is
     * still there, and nothing would ever say so. Every chunk a player is sent is asked about.
     */
    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onChunkWatch(ChunkWatchEvent.Watch event) {
        sendIn(event.getPlayer(), event.getLevel(), event.getChunk().getPos());
    }

    /**
     * Sends the effects that hang off an entity the moment a player is sent that entity.
     *
     * <p>An effect attached to a mob is not in a chunk the way a placed one is — it follows the mob —
     * so what brings it back is the entity being tracked again rather than the ground being loaded.
     * The owner's report is exactly this: it has to be re-sent every time it is loaded, entity or not.
     */
    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getTarget() == event.getEntity()) return;
        sendFor(player, event.getTarget().getUUID().toString());
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onServerStopping(ServerStoppingEvent event) {
        waiting.clear();
    }

    /** Puts a player in the queue, or moves them back to the end of it when they are already in. */
    private void queue(ServerPlayer player) {
        waiting.put(player.getUUID(), SETTLE_TICKS);
    }

    /**
     * Sends every stored effect in one chunk to one player, without waiting.
     *
     * <p>No delay here, unlike arriving: the player is already in the level and the client being told
     * is the one that just asked for this chunk, so there is a level to draw into.
     */
    private static void sendIn(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
        if (player == null || level == null || BackUtils.data() == null) return;
        EffectData store = BackUtils.data().effects();
        if (store == null) return;

        String dimension = level.dimension().location().toString();
        for (PlacedEffect effect : store.all()) {
            EffectPlacement where = effect.placement();
            if (where == null || !where.dimension().equals(dimension)) continue;
            if (where.blockX() >> 4 != chunk.x || where.blockZ() >> 4 != chunk.z) continue;
            sendOne(player, level, store, effect);
        }
    }

    /**
     * Sends every stored effect that hangs off one entity to one player.
     *
     * <p>Found by the identity the row kept when it was placed. A row that names no entity is not one
     * of these, and a row naming an entity the player cannot see is skipped by the level's own
     * lookup — which is the same question as "is this effect in front of them".
     */
    private static void sendFor(ServerPlayer player, String targetUuid) {
        if (player == null || targetUuid == null || targetUuid.isBlank()) return;
        if (BackUtils.data() == null) return;
        EffectData store = BackUtils.data().effects();
        if (store == null) return;

        ServerLevel level = player.serverLevel();
        String dimension = level.dimension().location().toString();
        for (PlacedEffect effect : store.all()) {
            EffectPlacement where = effect.placement();
            if (where == null || !where.dimension().equals(dimension)) continue;
            if (!targetUuid.equals(effect.targetUuid())) continue;
            sendOne(player, level, store, effect);
        }
    }

    /**
     * Sends one stored effect to one player, resolving what it hangs off first.
     *
     * <p>An accurate effect's display is looked for and, if it is gone, made again where the effect
     * is — the row is told about the new one. A row that names an entity is resolved by that identity,
     * because an entity id means nothing across sessions while an identity does.
     */
    private static void sendOne(ServerPlayer player, ServerLevel level, EffectData store,
                                PlacedEffect effect) {
        EffectPlacement where = effect.placement();
        int target = EffectPlacedPayload.NO_ENTITY;

        if (where.mode() == EffectToolConfig.Mode.ENTITY
                || where.mode() == EffectToolConfig.Mode.ENTITY_AUTO_ROTATE) {
            // An effect on a mob and no mob to name: a row written before entities were recorded by
            // identity, or one whose mob is not in this level. Sent as nothing it would be attached to
            // the player instead, which is a different effect in a different place — so it is not
            // sent at all, and the row stays as it is.
            if (effect.targetUuid() == null || effect.targetUuid().isBlank()) return;
            Entity entity = level.getEntity(UUID.fromString(effect.targetUuid()));
            if (entity == null) return;
            target = entity.getId();
        } else if (effect.anchorUuid() != null && !effect.anchorUuid().isBlank()) {
            Entity display = EffectAnchor.ensure(level, effect.anchorUuid(), where.centreX(),
                    where.centreY(), where.centreZ());
            if (display == null) return;
            if (!display.getUUID().toString().equals(effect.anchorUuid())) {
                store.updateAnchor(effect.id(), display.getUUID().toString());
            }
            target = display.getId();
        }

        EffectToolNetwork.sendPlaced(player, new EffectPlacedPayload(effect.id(),
                where.x(), where.y(), where.z(), where.yaw(), where.pitch(), target,
                where.face(), effect.config()));
    }

    /**
     * Sends one player every stored effect in the level they are now in.
     *
     * <p>Only their own dimension: an effect is a thing in a place, and a client draws what it is
     * told about in the level it is standing in. An effect from another dimension would be drawn in
     * this one, at coordinates that mean nothing here.
     *
     * <p>The display an accurate effect hangs off is looked for and, if it is gone, made again where
     * the effect is — and the row is told about the new one. That is what puts an accurate effect back
     * after a restart: the row survived, the display did not, and an effect with nothing to hang off
     * is an effect nobody sees.
     */
    public static void send(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || BackUtils.data() == null) return;
        EffectData store = BackUtils.data().effects();
        if (store == null) return;

        ServerLevel level = player.serverLevel();
        String dimension = level.dimension().location().toString();

        // Only what this player can see. Every effect in a dimension would be every effect in the
        // dimension *running* on this client — the library culls the drawing of one that is out of
        // sight, but it still holds and ticks it — so a world with hundreds of placed effects would
        // hand every player all of them. What is beyond this is not lost: a chunk is sent when the
        // player comes near it, and the watch event is what answers for those.
        double range = server.getPlayerList().getViewDistance() * 16.0D;
        double reach = range * range;
        Vec3 eye = player.getEyePosition();

        for (PlacedEffect effect : store.all()) {
            EffectPlacement where = effect.placement();
            if (where == null || !where.dimension().equals(dimension)) continue;

            double dx = where.centreX() - eye.x;
            double dy = where.centreY() - eye.y;
            double dz = where.centreZ() - eye.z;
            if (dx * dx + dy * dy + dz * dz > reach) continue;

            sendOne(player, level, store, effect);
        }
    }
}
