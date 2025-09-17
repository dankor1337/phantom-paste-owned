package dev.gambleclient.module.setting;

import net.minecraft.block.entity.BlockEntityType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple list setting for selecting block entity types.
 * Saves as indices (or names) depending on your ConfigManager’s JSON expectation.
 */
public class BlockEntityListSetting extends Setting {
    private final List<BlockEntityType<?>> value;
    private final List<BlockEntityType<?>> defaults;

    public BlockEntityListSetting(CharSequence name, List<BlockEntityType<?>> defaults) {
        super(name);
        this.defaults = new ArrayList<>(defaults);
        this.value = new ArrayList<>(defaults);
    }

    public List<BlockEntityType<?>> getValue() {
        return Collections.unmodifiableList(value);
    }

    public void setValue(List<BlockEntityType<?>> newList) {
        value.clear();
        value.addAll(newList);
    }

    public void reset() {
        value.clear();
        value.addAll(defaults);
    }

    public BlockEntityListSetting setDescription(CharSequence d) {
        super.setDescription(d);
        return this;
    }
}