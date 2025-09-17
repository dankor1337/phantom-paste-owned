package dev.gambleclient.module.modules.render;

import net.minecraft.client.util.math.MatrixStack;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.BooleanSetting;
import dev.gambleclient.module.setting.NumberSetting;
import dev.gambleclient.utils.EncryptedString;

public final class Animations extends Module {
    
    private final BooleanSetting enabled = new BooleanSetting(EncryptedString.of("Enabled"), true).setDescription(EncryptedString.of("Enable custom item animations"));
    private final NumberSetting swingSpeed = (NumberSetting) new NumberSetting(EncryptedString.of("Swing Speed"), 1.0, 10.0, 1.0, 0.1).setDescription(EncryptedString.of("Speed of item swinging animation"));
    private final NumberSetting scale = (NumberSetting) new NumberSetting(EncryptedString.of("Scale"), 0.5, 2.0, 1.0, 0.1).setDescription(EncryptedString.of("Scale of held items"));
    private final NumberSetting xOffset = (NumberSetting) new NumberSetting(EncryptedString.of("X Offset"), -1.0, 1.0, 0.0, 0.1).setDescription(EncryptedString.of("Horizontal offset of held items"));
    private final NumberSetting yOffset = (NumberSetting) new NumberSetting(EncryptedString.of("Y Offset"), -1.0, 1.0, 0.0, 0.1).setDescription(EncryptedString.of("Vertical offset of held items"));
    private final NumberSetting zOffset = (NumberSetting) new NumberSetting(EncryptedString.of("Z Offset"), -1.0, 1.0, 0.0, 0.1).setDescription(EncryptedString.of("Depth offset of held items"));

    public Animations() {
        super(EncryptedString.of("Animations"), EncryptedString.of("Custom item animations and transformations"), -1, Category.RENDER);
        addSettings(this.enabled, this.swingSpeed, this.scale, this.xOffset, this.yOffset, this.zOffset);
    }

    public boolean shouldAnimate() {
        return this.isEnabled() && this.enabled.getValue();
    }

    public void applyTransformations(MatrixStack matrices, float swingProgress) {
        // Apply custom transformations
        matrices.push();
        
        // Apply scale
        float scaleValue = this.scale.getFloatValue();
        matrices.scale(scaleValue, scaleValue, scaleValue);
        
        // Apply offsets
        matrices.translate(this.xOffset.getFloatValue(), this.yOffset.getFloatValue(), this.zOffset.getFloatValue());
        
        // Apply swing animation
        float swingSpeedValue = this.swingSpeed.getFloatValue();
        float swingAngle = swingProgress * swingSpeedValue * 90.0f;
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(swingAngle));
        
        // Don't pop the matrix - let the original rendering use our transformations
    }
}
