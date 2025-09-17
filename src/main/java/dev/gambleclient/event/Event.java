package dev.gambleclient.event;

public interface Event {
    default boolean isCancelled() {
        return false;
    }
}