package net.xlebupaksa.backutils.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.xlebupaksa.backutils.item.ToolItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hangs the arm that carries a tool the way an empty hand hangs.
 *
 * <p>{@code ArmPose.ITEM} bends the arm about eighteen degrees forward and pulls the elbow in. That is
 * the right shape for a held item and it is not a problem while the item is there to explain it — but
 * the item is hidden (see {@code ItemInHandLayerMixin}), and a hand held out round nothing is a louder
 * answer to "what is that player holding" than the tool was. Asked for as "the hand slightly rotates
 * away from the player".
 *
 * <p>The pose is chosen here and nowhere else, so this is the whole of the fix: an empty-handed arm is
 * what a player who is holding nothing has, and it needs no second transform to undo.
 *
 * <p>Only the hand that carries a tool. A tool in the main hand must not change how a sword in the
 * other hand is held.
 */
@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void backutils$hangTheToolHandEmpty(AbstractClientPlayer player, InteractionHand hand,
                                                       CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (ToolItems.isTool(player.getItemInHand(hand))) {
            cir.setReturnValue(HumanoidModel.ArmPose.EMPTY);
        }
    }
}
