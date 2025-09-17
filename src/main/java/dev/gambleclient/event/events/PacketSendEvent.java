package dev.gambleclient.event.events;

import net.minecraft.network.packet.Packet;
import dev.gambleclient.event.CancellableEvent;

public class PacketSendEvent extends CancellableEvent {
    public Packet<?> packet;

    public PacketSendEvent(final Packet<?> packet) {
        this.packet = packet;
    }
}