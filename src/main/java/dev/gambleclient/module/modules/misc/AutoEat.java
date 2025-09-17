package dev.gambleclient.module.modules.misc;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.mixin.MinecraftClientAccessor;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.EncryptedString;
import dev.gambleclient.utils.InventoryUtil;

public final class AutoEat extends Module {
    private final NumberSetting healthThreshold = new NumberSetting(EncryptedString.of("Health Threshold"), 0.0, 19.0, 17.0, 1.0);
    private final NumberSetting hungerThreshold = new NumberSetting(EncryptedString.of("Hunger Threshold"), 0.0, 19.0, 19.0, 1.0);
    public boolean isEa;
    private int selectedFoodSlot;
    private int previousSelectedSlot;

    public AutoEat() {
        super(EncryptedString.of("Auto Eat"), EncryptedString.of(" It detects whenever the hungerbar/health falls a certain threshold, selects food in your hotbar, and starts eating."), -1, Category.MISC);
        this.addSettings(this.healthThreshold, this.hungerThreshold);
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventListener
    public void onTick(final TickEvent tickEvent) {
        if (this.isEa) {
            if (this.shouldEat()) {
                if (this.mc.player.getInventory().getStack(this.selectedFoodSlot).get(DataComponentTypes.FOOD) != null) {
                    final int k = this.findBestFoodSlot();
                    if (k == -1) {
                        this.stopEating();
                        return;
                    }
                    this.selectSlot(k);
                }
                this.startEating();
            } else {
                this.stopEating();
            }
        } else if (this.shouldEat()) {
            this.selectedFoodSlot = this.findBestFoodSlot();
            if (this.selectedFoodSlot != -1) {
                this.saveCurrentSlot();
            }
        }
    }

    public boolean shouldEat() {
        final boolean b = this.mc.player.getHealth() <= this.healthThreshold.getIntValue();
        final boolean b2 = this.mc.player.getHungerManager().getFoodLevel() <= this.hungerThreshold.getIntValue();
        return this.findBestFoodSlot() != -1 && (b || b2);
    }

    private int findBestFoodSlot() {
        int n = -1;
        int n2 = -1;
        for (int i = 0; i < 9; ++i) {
            final Object value = this.mc.player.getInventory().getStack(i).getItem().getComponents().get(DataComponentTypes.FOOD);
            if (value != null) {
                final int nutrition = ((FoodComponent) value).nutrition();
                if (nutrition > n2) {
                    n = i;
                    n2 = nutrition;
                }
            }
        }
        return n;
    }

    private void saveCurrentSlot() {
        this.previousSelectedSlot = this.mc.player.getInventory().selectedSlot;
        this.startEating();
    }

    private void startEating() {
        this.selectSlot(this.selectedFoodSlot);
        this.setUseKeyPressed(true);
        if (!this.mc.player.isUsingItem()) {
            ((MinecraftClientAccessor) this.mc).invokeDoItemUse();
        }
        this.isEa = true;
    }

    private void stopEating() {
        this.selectSlot(this.previousSelectedSlot);
        this.setUseKeyPressed(false);
        this.isEa = false;
    }

    private void setUseKeyPressed(final boolean pressed) {
        this.mc.options.useKey.setPressed(pressed);
    }

    private void selectSlot(final int f) {
        InventoryUtil.swap(f);
        this.selectedFoodSlot = f;
    }
}
