package net.xlebupaksa.backutils.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.xlebupaksa.backutils.item.Unplaceable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refuses an unplaceable block before the client can predict it.
 *
 * <p>Cancelling {@code BlockEvent.EntityPlaceEvent} on the server is too late for the client: it
 * has already drawn the predicted block for the frame between the use packet and the server's
 * rejection, which is the ghost the operator sees. Returning {@link InteractionResult#FAIL} from
 * the shared {@link BlockItem#place} path stops the prediction on the client as well as the
 * placement on the server.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void backutils$refuseUnplaceable(BlockPlaceContext context,
            CallbackInfoReturnable<InteractionResult> callback) {
        if (Unplaceable.is(context.getItemInHand())) {
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }
}
