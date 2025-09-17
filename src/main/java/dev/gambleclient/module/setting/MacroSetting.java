package dev.gambleclient.module.setting;

import java.util.ArrayList;
import java.util.List;

public class MacroSetting extends Setting {
    private List<String> commands;
    private final List<String> defaultCommands;

    public MacroSetting(final CharSequence name, final List<String> defaultCommands) {
        super(name);
        this.commands = new ArrayList<>(defaultCommands);
        this.defaultCommands = new ArrayList<>(defaultCommands);
    }

    public MacroSetting(final CharSequence name) {
        this(name, new ArrayList<>());
    }

    public List<String> getCommands() {
        return this.commands;
    }

    public void setCommands(final List<String> commands) {
        this.commands = new ArrayList<>(commands);
    }

    public void addCommand(final String command) {
        this.commands.add(command);
    }

    public void removeCommand(final int index) {
        if (index >= 0 && index < this.commands.size()) {
            this.commands.remove(index);
        }
    }

    public void clearCommands() {
        this.commands.clear();
    }

    public List<String> getDefaultCommands() {
        return new ArrayList<>(this.defaultCommands);
    }

    public void resetValue() {
        this.commands = new ArrayList<>(this.defaultCommands);
    }

    public MacroSetting setDescription(final CharSequence description) {
        super.setDescription(description);
        return this;
    }
}
