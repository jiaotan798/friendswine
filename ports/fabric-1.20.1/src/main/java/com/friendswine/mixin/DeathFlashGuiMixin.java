package com.friendswine.mixin;

import com.friendswine.client.ClientDeathFlash;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 捕获本帧 GUI，在最终提交前追加覆盖层；不依赖 HUD、菜单或本地变量表的其他内容。 */
@Mixin(GameRenderer.class)
public abstract class DeathFlashGuiMixin {
    @Unique private GuiGraphics friendswine$graphics;

    // Redirect.at 的注解形状在当前加载器组合中不一致，改用标准变量捕获与注入保持兼容。
    @ModifyVariable(method="render", at=@At("STORE"), ordinal=0)
    private GuiGraphics friendswine$captureGui(GuiGraphics graphics) {
        friendswine$graphics = graphics;
        return graphics;
    }

    @Inject(method="render", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    private void friendswine$deathFlash(CallbackInfo callback) {
        ClientDeathFlash.render(friendswine$graphics);
    }
}
