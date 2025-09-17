package dev.gambleclient.module.setting;

import java.awt.Color;

public final class ColorSetting extends Setting {
    private Color value;
    private final Color defaultValue;

    public ColorSetting(final CharSequence name, final Color defaultValue) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public Color getValue() {
        return this.value;
    }

    public void setValue(final Color value) {
        this.value = value;
    }

    public Color getDefaultValue() {
        return this.defaultValue;
    }

    public void resetValue() {
        this.value = this.defaultValue;
    }

    public ColorSetting setDescription(final CharSequence description) {
        super.setDescription(description);
        return this;
    }
}