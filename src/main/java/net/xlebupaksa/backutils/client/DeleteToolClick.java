package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.xlebupaksa.backutils.item.DeleteToolItem;
import net.xlebupaksa.backutils.network.EffectDeletePayload;
import net.xlebupaksa.backutils.network.EffectToolNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;

/**
 * Using the delete tool removes what is already placed.
 *
 * <p>The click is taken before the game acts on it, exactly as the effect tool's is: what is deleted
 * is decided by the crosshair and not by what the crosshair happens to be over, so a chest or a
 * villager under it must not open or trade instead. The delete tool takes the click whatever is
 * under the crosshair rather than only when it has something to delete, which is the one difference
 * from the effect tool: a tool that would place nothing leaves the click to the game because a
 * half-configured tool is an ordinary item, where this one has no configuration to be missing and
 * looking at what is placed is already its work.
 *
 * <p>Holding the button deletes once, not a stream of times. The game repeats a held use every few
 * ticks, and each repeat would be another claim about a place that has already been emptied — and,
 * worse, a claim about whatever the crosshair had moved on to. So the button's own state is followed
 * and only the press that began the hold asks for anything, while every repeat is still taken, so
 * that a held button cannot fall through to the block underneath.
 *
 * <p>Every taken click fires the tool's shot, whether or not the crosshair named anything, for the
 * reason the click itself is taken either way: the shot is the sound of the tool being used rather
 * than a report of what was removed. It is heard by this player alone, and is the game's own choice
 * between two recordings — see {@link ToolShot}.
 *
 * <p>What a click names, in the order the two targets are asked about:
 *
 * <ul>
 *   <li><b>Alt</b> — everything the clicking player placed, wherever it is. It comes first because it
 *       is a statement about the whole world rather than about the crosshair, and a player holding
 *       alt is not aiming at anything in particular.
 *   <li><b>Whatever {@link EffectPick} answers</b>, which is the one decision the renderer takes as
 *       well: a placed effect the crosshair is on, or an entity an effect hangs off when that entity
 *       is what the ray meets first. It is asked once, here, rather than worked out again from the
 *       crosshair — a second reading is a second answer, and a cube drawn red that a click does not
 *       take away is the worst kind of lie for a tool whose whole job is removing things.
 *   <li><b>An entity</b> is removed by its row ids, which only this client holds: a row keeps where
 *       the entity stood and not which entity it was, so the server cannot be asked for "the effects
 *       on this entity" at all. The entity is also flashed, because the effect it had is about to
 *       stop being an anchor and the outline would otherwise vanish without saying that the click
 *       worked.
 *   <li><b>A place</b> is removed by its block, which is the spec's "all the placed effects in this
 *       place": the block is read from the effect's own anchor rather than from the crosshair's
 *       block, so an accurate effect in open air — which is in no block the crosshair is on — can be
 *       removed at all.
 * </ul>
 *
 * <p>The blink is started here, before the server has answered, because it is feedback for the click
 * rather than a report of what happened: a refusal is silent everywhere else in this feature, and an
 * operator whose red blink was held back until the answer arrived would see the flash arrive after
 * the effect had already gone.
 */
@OnlyIn(Dist.CLIENT)
public final class DeleteToolClick {

    /**
     * Whether the use button was down at the end of the last client tick.
     *
     * <p>This is what tells a press from the repeat the game sends while the button is held, and it
     * is the same field, written the same way, as the effect tool's: the button's state is read at
     * the end of a tick, so what the next tick's handler sees is the state the button was in before
     * that tick began — which is exactly the question "was this press already being held?".
     */
    private static boolean useWasDown;

    private DeleteToolClick() {}

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        useWasDown = minecraft.options != null && minecraft.options.keyUse.isDown();
        // The blink is aged whether or not the tool is held: it is the tail of an action already
        // taken, and a flash that stopped because the tool was put away would be a deletion that
        // never finished saying so.
        EffectBlink.tick();
    }

    /**
     * Forgets every blink when the level goes away.
     *
     * <p>A blink is a place in one world. Kept across a change of world it would blink at the same
     * coordinates in the next one, at a place nothing was ever deleted from — and the level a client
     * is standing in is the only level it can be drawn in.
     */
    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) EffectBlink.clear();
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onUseItem(InputEvent.InteractionKeyMappingTriggered event) {
        // The event is fired once per hand, and the main hand is the one the boxes are drawn for.
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        // A screen owns every key while it is open, and there is nothing to aim at outside a level.
        if (player == null || level == null || minecraft.screen != null) return;
        if (!(player.getMainHandItem().getItem() instanceof DeleteToolItem)) return;

        event.setCanceled(true);
        // A held button repeats the use, and the repeats are taken without deleting.
        if (useWasDown) return;

        // Fired whatever the crosshair is on, which is what this tool does with a click: it takes
        // one whether or not there is anything to remove, and the shot is the sound of that.
        ToolShot.deleteTool();

        if (Screen.hasAltDown()) {
            // Both readings of "all effects from self" are answered here. What the player *placed* is
            // in the table, so the server removes it. What is attached *to* them was never written
            // down — a self effect ends when the player does, so a row would outlive it — and the
            // client that made it is the only side that can take it away.
            for (long id : PlacedEffects.anchoredTo(player.getId())) {
                PlacedEffects.remove(id);
            }
            EffectToolNetwork.sendDeletion(EffectDeletePayload.mine());
            return;
        }

        // One answer for the whole click, taken from the picker the renderer reads as well, so the
        // red cube on screen and the effect a click asks for cannot come apart. The partial tick is
        // zero because a click has no fraction of a tick: the game's own pick, which this reads, was
        // taken at the end of the last one.
        EffectPick.Scene scene = EffectPick.scene(player, 0.0F, minecraft.hitResult);

        if (scene.onEntity() && deleteOn(scene)) return;

        if (!scene.onPlace()) return;
        BlockPos block = scene.placeBlock();
        if (block == null) return;

        EffectBlink.flashPlace(block.asLong());
        EffectToolNetwork.sendDeletion(EffectDeletePayload.atBlock(
                block.getX(), block.getY(), block.getZ()));
    }

    /**
     * {@return true when the entity had effects to remove}
     *
     * <p>An entity with no effect on it is not a target, and answering so rather than sending an
     * empty claim leaves the click to be read as a click on the block behind it — which is what it
     * is when the crosshair is on a cow with nothing attached to it and a placed effect is on the
     * block under its feet.
     */
    private static boolean deleteOn(EffectPick.Scene scene) {
        List<Long> attached = PlacedEffects.anchoredTo(scene.entityId());
        if (attached.isEmpty()) return false;

        EffectBlink.flashEntity(scene.entityId());
        EffectToolNetwork.sendDeletion(EffectDeletePayload.effects(attached));
        return true;
    }
}
