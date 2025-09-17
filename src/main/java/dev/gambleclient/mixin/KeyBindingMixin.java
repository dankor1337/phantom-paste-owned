package dev.gambleclient.mixin;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import dev.gambleclient.Gamble;
import dev.gambleclient.imixin.IKeybinding;

@Mixin({KeyBinding.class})
public abstract class KeyBindingMixin implements IKeybinding {
    @Shadow
    private InputUtil.Key boundKey;

    @Shadow public abstract void setPressed(boolean pressed);

    @Override
    public boolean krypton$isActuallyPressed() {
        return InputUtil.isKeyPressed(Gamble.mc.getWindow().getHandle(), this.boundKey.getCode());
    }

    @Override
    public void krypton$resetPressed() {
        this.setPressed(this.krypton$isActuallyPressed());
    }
}