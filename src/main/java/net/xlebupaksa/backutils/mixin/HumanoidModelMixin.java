package net.xlebupaksa.backutils.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.xlebupaksa.backutils.item.ToolItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the two tools from swinging in other people's view.
 *
 * <p>A right-click makes the arm go through the motion of using what it holds. That is the second
 * tell left after the tool itself is hidden: the players around an operator see the gesture and read
 * it, while the operator's own first-person hand is drawn by a different renderer
 * ({@code ItemInHandRenderer}) which is left alone, so they still see exactly what they are doing.
 *
 * <p>The animation is skipped at its own source rather than by zeroing the model's swing time: the
 * time lives on {@code EntityModel}, and a shadow field declared against {@code HumanoidModel} cannot
 * see it — the mixin refuses to apply, which is how this was found. Cancelling here touches the
 * third-person render only; the holder's own view, the swing sound and whatever the server decides
 * are untouched.
 *
 * <p>Only the arm that is actually swinging, and only when that hand holds a tool: a sword swung in
 * the other hand still swings.
 */
@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {

    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void backutils$stillTheToolSwing(LivingEntity entity, float ageInTicks, CallbackInfo ci) {
        InteractionHand arm = entity.swingingArm;
        if (arm != null && ToolItems.isTool(entity.getItemInHand(arm))) {
            ci.cancel();
        }
    }
}
