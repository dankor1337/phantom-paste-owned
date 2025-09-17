package dev.gambleclient.event.events;

import net.minecraft.network.packet.Packet;
import dev.gambleclient.event.CancellableEvent;

public class PacketReceiveEvent extends CancellableEvent {
    public Packet<?> packet;

    public PacketReceiveEvent(final Packet<?> packet) {
        this.packet = packet;
    }
}