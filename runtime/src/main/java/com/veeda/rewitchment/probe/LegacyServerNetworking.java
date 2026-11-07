package com.veeda.rewitchment.probe;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class LegacyServerNetworking {
    @FunctionalInterface public interface Handler {
        void receive(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler,
                PacketByteBuf buf, PacketSender responseSender);
    }
    private LegacyServerNetworking() {}
    public static boolean registerGlobalReceiver(Identifier id, Handler handler) {
        LegacyPacketPayloads.registerTypes();
        var channel = LegacyPacketPayloads.channel(id, LegacyPacketPayloads.Direction.C2S);
        return ServerPlayNetworking.registerGlobalReceiver(channel.id(), (payload, context) -> {

            var buf = LegacyPacketPayloads.open(payload, context.player().getRegistryManager());
            try {
                dispatch(id, handler, context.server(), context.player(), buf, context.responseSender());
            } finally { buf.release(); }
        });
    }

    static boolean dispatch(Identifier id, Handler receiver, MinecraftServer server, ServerPlayerEntity player,
            PacketByteBuf buf, PacketSender sender) {
        if (!player.isAlive() || player.isRemoved() || server.getPlayerManager().getPlayer(player.getUuid()) != player
                || !validBody(id, buf)) return false;
        if (id.getPath().equals("cauldron_teleport")) {
            int index = buf.readerIndex();
            var pos = buf.readBlockPos(); var name = buf.readString(Short.MAX_VALUE); buf.readerIndex(index);
            var world = player.getServerWorld();
            if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos)
                    || pos.getSquaredDistance(player.getPos()) > 64
                    || !net.minecraft.registry.Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).equals(Identifier.of("bewitchment", "witch_cauldron"))) return false;
            try {
                var type = Class.forName("moriyashiine.bewitchment.common.world.BWWorldState");
                var state = type.getMethod("get", net.minecraft.world.World.class).invoke(null, world);
                @SuppressWarnings("unchecked") var destinations = (java.util.Map<Long,String>) type.getField("witchCauldrons").get(state);
                net.minecraft.util.math.BlockPos nearest = null;
                for (var entry : destinations.entrySet()) if (name.equals(entry.getValue())) {
                    var candidate = net.minecraft.util.math.BlockPos.fromLong(entry.getKey());
                    if (nearest == null || candidate.getSquaredDistance(player.getPos()) < nearest.getSquaredDistance(player.getPos())) nearest = candidate;
                }
                if (nearest != null && (!world.isInBuildLimit(nearest) || !world.isChunkLoaded(nearest)
                        || !net.minecraft.registry.Registries.BLOCK.getId(world.getBlockState(nearest).getBlock()).equals(Identifier.of("bewitchment", "witch_cauldron")))) return false;
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cauldron validation unavailable", error); }
        }
        receiver.receive(server, player, player.networkHandler, buf, sender);
        return true;
    }
    static boolean validBody(Identifier id, PacketByteBuf buf) {
        int index = buf.readerIndex();
        try {
            if (!id.getNamespace().equals("bewitchment") || buf.readableBytes() > LegacyPacketPayloads.C2S_LIMIT) return false;
            return switch (id.getPath()) {
                case "transformation_ability" -> !buf.isReadable();
                case "toggle_pressing_forward" -> buf.readableBytes() == 1 && (buf.getUnsignedByte(index) == 0 || buf.getUnsignedByte(index) == 1);
                case "cauldron_teleport" -> {
                    buf.readBlockPos(); String name = buf.readString(Short.MAX_VALUE);
                    yield !name.isBlank() && !buf.isReadable();
                }
                default -> false;
            };
        } catch (RuntimeException malformed) { return false; }
        finally { buf.readerIndex(index); }
    }
    public static PacketByteBuf buffer(ServerPlayerEntity player) {
        return new RegistryByteBuf(Unpooled.buffer(), player.getRegistryManager());
    }
    public static void send(ServerPlayerEntity player, Identifier id, PacketByteBuf buf) {
        var payload = LegacyPacketPayloads.take(id, buf, LegacyPacketPayloads.Direction.S2C);
        LegacyPacketPayloads.registerTypes();
        ServerPlayNetworking.send(player, payload);
    }
}
