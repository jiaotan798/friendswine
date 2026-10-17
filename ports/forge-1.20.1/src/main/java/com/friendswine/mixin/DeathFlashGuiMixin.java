package com.friendswine.mixin;

import com.friendswine.client.ClientDeathFlash;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 在 GUI 最终提交前绘制，保持原版 Screen 和输入流程，不依赖 HUD 是否可见。 */
@Mixin(GameRenderer.class)
public abstract class DeathFlashGuiMixin {
    @Redirect(method="render", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    private void friendswine$deathFlash(GuiGraphics graphics) {
        ClientDeathFlash.render(graphics);
        graphics.flush();
    }
}
