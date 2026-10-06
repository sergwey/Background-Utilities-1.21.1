package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.xlebupaksa.backutils.client.EffectToolAim.Look;
import net.xlebupaksa.backutils.item.EffectToolConfig;
import net.xlebupaksa.backutils.item.EffectToolConfig.Mode;
import net.xlebupaksa.backutils.item.EffectToolItem;
import net.xlebupaksa.backutils.item.EffectToolPlacement;
import net.xlebupaksa.backutils.item.EffectToolPlacement.Point;
import net.xlebupaksa.backutils.network.EffectPlacePayload;
import net.xlebupaksa.backutils.network.EffectToolNetwork;

/**
 * Using the effect tool places the effect its preview is showing.
 *
 * <p>The click is taken before the game acts on it. What is placed is decided by the crosshair and
 * not by what the crosshair happens to be over, so a chest or a villager under it must not open
 * instead of the effect being placed: the interaction is cancelled rather than passed on, and
 * nothing but this mod's own message is sent to the server.
 *
 * <p>Holding the button places one effect, not a stream of them. The game repeats a held use every
 * few ticks, and each repeat would otherwise be another row in the database at the same spot — the
 * very pile the delete tool exists to clear. So the button's own state is followed and only the
 * press that began the hold places anything, while every repeat is still taken, so that a held
 * button cannot fall through to the block underneath.
 *
 * <p>The press also fires the tool's shot, before what is aimed at has been worked out: it answers
 * the trigger rather than the target, so the entity modes, which place nothing when the crosshair
 * holds nothing, are a shot that missed rather than a click that said nothing had happened. The
 * sound is heard by this player alone, and is the game's own choice between two recordings — see
 * {@link ToolShot}.
 *
 * <p>The main hand only, which is the hand the preview reads. A tool held in the other hand is
 * neither drawn nor placed, and the two agreeing matters more than either one being generous.
 *
 * <p>Whether the placement is allowed is not decided here. The server settles that, on the message
 * this sends, from the tool it holds for the player; a refusal is silent, and the operator sees
 * nothing appear.
 */
@OnlyIn(Dist.CLIENT)
public final class EffectToolClick {

    /**
     * Whether the use button was down at the end of the last client tick.
     *
     * <p>This is what tells a press from the repeat the game sends while the button is held. It is
     * written at the end of a tick and read while the next one handles its keys, so what the handler
     * sees is the state the button was in before this tick began — which is exactly the question
     * "was this press already being held?".
     */
    private static boolean useWasDown;

    private EffectToolClick() {}

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        useWasDown = minecraft.options != null && minecraft.options.keyUse.isDown();
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onUseItem(InputEvent.InteractionKeyMappingTriggered event) {
        // The event is fired once per hand, and the main hand is the one that answers for the tool.
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // A screen owns every key while it is open, and there is nothing to aim at outside a level.
        if (player == null || minecraft.level == null || minecraft.screen != null) return;

        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof EffectToolItem)) return;

        EffectToolConfig config = EffectToolItem.configOf(tool);
        // A tool that has not been set up behaves like any other item: the click is left to the game
        // rather than eaten by a tool that would place nothing.
        if (!EffectToolPlacement.places(config)) return;

        event.setCanceled(true);
        // A held button repeats the use, and the repeats are taken without placing.
        if (useWasDown) return;

        // Fired here rather than inside send: the shot answers the press, not what the press found.
        ToolShot.effectTool();

        send(minecraft, player, config);
    }

    /**
     * Works out what the tool is aimed at and claims it.
     *
     * <p>The claim is the position, the look as the direction it was aimed in, the entity if the mode
     * names one, the face if the mode attaches to one, and the tool's own settings as they stand.
     * Everything the effect is placed with is in there, so the effect the server settles is the one
     * being previewed rather than one worked out again at the far end.
     *
     * @return true when a claim was sent
     */
    private static boolean send(Minecraft minecraft, LocalPlayer player, EffectToolConfig config) {
        HitResult crosshair = minecraft.hitResult;
        Look look = EffectToolAim.of(player, TICK_BOUNDARY, config, crosshair);
        double reach = player.blockInteractionRange();

        int target = EffectPlacePayload.NO_ENTITY;
        double x;
        double y;
        double z;

        switch (config.mode()) {
            case Mode.SELF, Mode.SELF_AUTO_ROTATE -> {
                // A self effect attaches to the player, so the point is only where they are standing
                // when they ask for it; the effect follows them from there.
                x = player.getX();
                y = player.getY();
                z = player.getZ();
                target = player.getId();
            }
            case Mode.ENTITY, Mode.ENTITY_AUTO_ROTATE -> {
                // The entity the crosshair is on, as the game itself picked it. An entity mode with
                // nothing under the crosshair has nothing to attach to and places nothing.
                Entity aimed = look.target();
                if (aimed == null) return false;
                x = aimed.getX();
                y = aimed.getY();
                z = aimed.getZ();
                target = aimed.getId();
            }
            case Mode.ACCURATE -> {
                // Blocks are ignored outright: the point is the eye carried along its own direction,
                // which is off the grid and is where the blank item-display will stand.
                Point at = look.accurate(config, reach);
                x = at.x();
                y = at.y();
                z = at.z();
            }
            default -> {
                // The two block modes, and anything that reaches here unclassified: a position on
                // the block grid, which is what the cube in the preview is drawn in.
                Point block = look.placement(config, reach);
                x = block.x();
                y = block.y();
                z = block.z();
            }
        }

        // The face is the aim's own: it is which surface was met for a block-side placement, and up
        // for every other mode, which is the direction none of them turns away from.
        EffectToolNetwork.sendPlacement(new EffectPlacePayload(x, y, z, player.getYRot(),
                player.getXRot(), target, look.face(), config));
        return true;
    }

    /**
     * The point within a tick that a click happens at, as the partial tick that names it.
     *
     * <p>A click is handled while the tick is being taken, so the position wanted is the one the
     * player is at rather than a point interpolated towards the next tick, which is what the end of
     * a tick is.
     */
    private static final float TICK_BOUNDARY = 1.0F;
}
