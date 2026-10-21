package com.shapearts.modelbrowser.mixin;

import com.shapearts.modelbrowser.catalog.ModelCatalog;
import com.shapearts.modelbrowser.ui.ModelGridWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Full-height model browser to the left of the vanilla anvil. */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin extends ItemCombinerScreen<AnvilMenu> {
    @Shadow private EditBox name;
    @Unique private EditBox shape$search;
    @Unique private ModelGridWidget shape$grid;
    @Unique private Button shape$toggle;
    @Unique private boolean shape$visible = true;
    @Unique private ItemStack shape$last = ItemStack.EMPTY;

    protected AnvilScreenMixin(AnvilMenu menu, Inventory inventory, Component title, net.minecraft.resources.Identifier texture) {
        super(menu, inventory, title, texture);
    }

    @Inject(method = "subInit", at = @At("TAIL"))
    private void shape$init(CallbackInfo ci) {
        // Vanilla's name box normally cannot lose focus, stealing Backspace from other fields.
        if (name != null) name.setCanLoseFocus(true);
        int panelWidth = Math.min(350, Math.max(220, leftPos - 8));
        int x = Math.max(2,leftPos-panelWidth-5);
        int panelHeight = Math.max(230,height-12);
        int top = Math.max(4,(height-panelHeight)/2);

        shape$grid = new ModelGridWidget(x,top,panelWidth,panelHeight,text -> {
            if (name != null) {
                name.setValue(text);
                name.setFocused(true);
                this.setFocused(name);
            }
        });
        addRenderableWidget(shape$grid);
        // Reference layout deliberately contains no search box or toggle.
        // The model browser occupies its own left panel; the anvil remains usable.
        shape$refresh();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // The vanilla inventory shortcut (E) must not close the screen while typing a search.
        if (shape$visible && shape$search != null && shape$search.isFocused()) {
            shape$search.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Handle this at the screen before vanilla routes wheel input to its focused name box.
        if (shape$visible && shape$grid != null
                && shape$grid.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void shape$tick(CallbackInfo ci) {
        ItemStack stack = menu.getSlot(0).getItem();
        if (!ItemStack.isSameItemSameComponents(stack,shape$last)) {
            shape$last=stack.copy();
            shape$refresh();
        }
    }
    @Unique private void shape$refresh() {
        if (shape$grid == null) return;
        ItemStack stack=menu.getSlot(0).getItem();
        shape$grid.setModels(stack,stack.isEmpty()?java.util.List.of():ModelCatalog.forItem(stack.getItem()));
    }
}
