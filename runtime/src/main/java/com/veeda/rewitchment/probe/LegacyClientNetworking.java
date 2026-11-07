package com.veeda.rewitchment.probe;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class LegacyClientNetworking {
    @FunctionalInterface public interface Handler {
        void receive(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender);
    }
    private LegacyClientNetworking() {}
    public static boolean registerGlobalReceiver(Identifier id, Handler handler) {
        LegacyPacketPayloads.registerTypes();
        var channel = LegacyPacketPayloads.channel(id, LegacyPacketPayloads.Direction.S2C);
        return ClientPlayNetworking.registerGlobalReceiver(channel.id(), (payload, context) -> {
            var connection = context.client().getNetworkHandler();
            if (connection == null) return;
            var buf = LegacyPacketPayloads.open(payload, connection.getRegistryManager());
            try {
                handler.receive(context.client(), connection, buf, context.responseSender());
            } finally { buf.release(); }
        });
    }
    public static void send(Identifier id, PacketByteBuf buf) {
        var payload = LegacyPacketPayloads.take(id, buf, LegacyPacketPayloads.Direction.C2S);
        LegacyPacketPayloads.registerTypes();
        ClientPlayNetworking.send(payload);
    }
}
