package net.xlebupaksa.backutils.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChatComponent.class)
public class ChatBackgroundRemovalMixin {
    @Redirect(
            method = "render", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V",
            ordinal = 0
        )
    )

    private void removeBackground(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int color){}
}
