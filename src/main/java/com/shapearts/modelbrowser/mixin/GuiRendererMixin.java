package com.shapearts.modelbrowser.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Minecraft 26.3 normally caches item geometry at the native inventory-icon
 * resolution before it is displayed. Enlarging that cached icon makes it blocky.
 *
 * While an anvil is open, rasterize the actual item model into atlas slots at
 * triple the ordinary resolution instead. Minecraft still handles its native
 * baked geometry, lighting, textures and item-model name overrides.
 *
 * Scope the change to anvils so unrelated screens are not made more expensive.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @ModifyVariable(
        method = "prepareItemAtlas",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2
    )
    private int shape$higherResolutionItemModels(int original) {
        if (Minecraft.getInstance().gui.screen() instanceof AnvilScreen) {
            return Math.min(384, original * 3);
        }
        return original;
    }
}
