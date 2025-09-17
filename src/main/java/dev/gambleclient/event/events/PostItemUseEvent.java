package dev.gambleclient.event.events;

import dev.gambleclient.event.CancellableEvent;

public class PostItemUseEvent extends CancellableEvent {
    public int cooldown;

    public PostItemUseEvent(final int cooldown) {
        this.cooldown = cooldown;
    }
}