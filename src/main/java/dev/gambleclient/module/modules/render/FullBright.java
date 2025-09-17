package dev.gambleclient.module.modules.render;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.TickEvent;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.module.setting.ModeSetting;
import dev.gambleclient.utils.EncryptedString;

public final class FullBright extends Module {

    public enum FullBrightMode {
        GAMMA,
        POTION
    }

    private final ModeSetting<FullBrightMode> mode = new ModeSetting<>(EncryptedString.of("Mode"), FullBrightMode.GAMMA, FullBrightMode.class).setDescription(EncryptedString.of("The way that will be used to change the game's brightness"));
    private double originalGamma = 1.0;

    public FullBright() {
        super(EncryptedString.of("FullBright"), EncryptedString.of("Gives you the ability to clearly see even when in the dark"), -1, Category.RENDER);
        addSettings(this.mode);
    }

    @Override
    public void onEnable() {
        super.onEnable();

        if (this.mc.player == null) return;

        // Store original gamma for gamma mode
        if (this.mode.getValue() == FullBrightMode.GAMMA) {
            try {
                java.lang.reflect.Field gammaField = this.mc.options.getClass().getDeclaredField("gamma");
                gammaField.setAccessible(true);
                Object gammaOption = gammaField.get(this.mc.options);
                if (gammaOption != null) {
                    java.lang.reflect.Method getValueMethod = gammaOption.getClass().getMethod("getValue");
                    this.originalGamma = (Double) getValueMethod.invoke(gammaOption);
                }
            } catch (Exception e) {
                this.originalGamma = 1.0;
            }
        }

        // Add night vision for potion mode
        if (this.mode.getValue() == FullBrightMode.POTION) {
            if (!this.mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                this.mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE));
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();

        if (this.mc.player == null) return;

        // Restore original gamma for gamma mode
        if (this.mode.getValue() == FullBrightMode.GAMMA) {
            try {
                java.lang.reflect.Field gammaField = this.mc.options.getClass().getDeclaredField("gamma");
                gammaField.setAccessible(true);
                Object gammaOption = gammaField.get(this.mc.options);
                if (gammaOption != null) {
                    java.lang.reflect.Method setValueMethod = gammaOption.getClass().getMethod("setValue", double.class);
                    setValueMethod.invoke(gammaOption, this.originalGamma);
                }
            } catch (Exception e) {
                // Fallback if gamma setting doesn't exist
            }
        }

        // Remove night vision for potion mode
        if (this.mode.getValue() == FullBrightMode.POTION) {
            if (this.mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                this.mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
        }
    }

    @EventListener
    public void onTick(final TickEvent event) {
        if (this.mc.player == null) return;

        // Handle potion mode
        if (this.mode.getValue() == FullBrightMode.POTION) {
            if (!this.mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                this.mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE));
            }
            return;
        }

        // Handle gamma mode
        if (this.mode.getValue() == FullBrightMode.GAMMA) {
            if (this.mc.options == null) return;

            try {
                // MC 1.20+ Options has SimpleOption<Double> gamma with accessor getGamma()/setGamma or field "gamma"
                // 1) Look for method setGamma(double)
                try {
                    java.lang.reflect.Method setGamma = this.mc.options.getClass().getMethod("setGamma", double.class);
                    setGamma.invoke(this.mc.options, 100.0);
                    return;
                } catch (NoSuchMethodException ignored) {
                }

                // 2) Try getGamma()/setValue on returned option
                try {
                    java.lang.reflect.Method getGamma = this.mc.options.getClass().getMethod("getGamma");
                    Object gammaOption = getGamma.invoke(this.mc.options);
                    if (gammaOption != null) {
                        java.lang.reflect.Method setValueMethod = gammaOption.getClass().getMethod("setValue", double.class);
                        setValueMethod.invoke(gammaOption, 100.0);
                        return;
                    }
                } catch (NoSuchMethodException ignored) {
                }

                // 3) Fall back to field named "gamma"
                try {
                    java.lang.reflect.Field gammaField = this.mc.options.getClass().getDeclaredField("gamma");
                    gammaField.setAccessible(true);
                    Object gammaOption = gammaField.get(this.mc.options);
                    if (gammaOption != null) {
                        java.lang.reflect.Method setValueMethod = gammaOption.getClass().getMethod("setValue", double.class);
                        setValueMethod.invoke(gammaOption, 100.0);
                        return;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            } catch (Exception e) {
                // Try alternative field names
                try {
                    java.lang.reflect.Field brightnessField = this.mc.options.getClass().getDeclaredField("brightness");
                    brightnessField.setAccessible(true);
                    Object brightnessOption = brightnessField.get(this.mc.options);

                    if (brightnessOption != null) {
                        java.lang.reflect.Method setValueMethod = brightnessOption.getClass().getMethod("setValue", double.class);
                        setValueMethod.invoke(brightnessOption, 1.0);
                    }
                } catch (Exception ex) {
                    // Try to find any brightness-related field
                    try {
                        java.lang.reflect.Field[] fields = this.mc.options.getClass().getDeclaredFields();
                        for (java.lang.reflect.Field field : fields) {
                            if (field.getName().toLowerCase().contains("bright") ||
                                    field.getName().toLowerCase().contains("gamma") ||
                                    field.getName().toLowerCase().contains("light")) {
                                field.setAccessible(true);
                                Object option = field.get(this.mc.options);
                                if (option != null) {
                                    try {
                                        java.lang.reflect.Method setValueMethod = option.getClass().getMethod("setValue", double.class);
                                        setValueMethod.invoke(option, 100.0);
                                        break;
                                    } catch (Exception ex2) {
                                        // Continue to next field
                                    }
                                }
                            }
                        }
                    } catch (Exception ex3) {
                        // As a last resort, apply night vision to guarantee effect
                        if (!this.mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                            this.mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE));
                        }
                    }
                }
            }
        }
    }
}