package net.xlebupaksa.backutils.mixin;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.xlebupaksa.backutils.item.DeleteToolItem;
import net.xlebupaksa.backutils.item.EffectToolItem;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the two tools out of the hand other people see.
 *
 * <p>Asked for in as many words: a tool is what an operator aims with, and the players around them
 * have no business seeing it. It is drawn in the holder's own first-person hand and nowhere else —
 * which is why the holder does not see it in their own third-person view either.
 *
 * <p>First person is a different renderer entirely ({@code ItemInHandRenderer}) and is untouched, so
 * the tool is still exactly where it was asked to be. This layer is the one that draws a held item on
 * a rendered body, and only that.
 *
 * <p>One hand at a time, deliberately: the stack is the argument, so a tool in the main hand does not
 * hide the sword in the other.
 */
@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void backutils$hideToolFromOthers(LivingEntity entity, ItemStack stack,
                                              ItemDisplayContext context, HumanoidArm arm,
                                              PoseStack poses, MultiBufferSource buffers, int light,
                                              CallbackInfo ci) {
        if (stack.getItem() instanceof EffectToolItem || stack.getItem() instanceof DeleteToolItem) {
            ci.cancel();
        }
    }
}
