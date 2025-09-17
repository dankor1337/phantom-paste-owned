package dev.gambleclient;

import net.fabricmc.api.ModInitializer;

public final class Main implements ModInitializer {
    public void onInitialize() {
        new Gamble();
    }
}