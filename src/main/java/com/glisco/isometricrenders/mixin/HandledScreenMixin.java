package com.glisco.isometricrenders.mixin;

import com.glisco.isometricrenders.screen.SelectRenderTaskScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin<T extends AbstractContainerMenu> extends Screen {

    @Shadow
    @Final
    protected T handler;

    @Shadow
    public abstract void onClose();

    protected HandledScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    public void renderInventory(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode != GLFW.GLFW_KEY_F12 || !Screen.hasControlDown()) return;

        this.onClose();
        minecraft.setScreen(new SelectRenderTaskScreen(handler.slots.stream().map(Slot::getItem).filter(stack -> !stack.isEmpty()).toList()));

        cir.setReturnValue(false);
    }

}
