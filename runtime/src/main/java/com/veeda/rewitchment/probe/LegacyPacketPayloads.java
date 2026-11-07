package com.veeda.rewitchment.probe;

import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;

public final class LegacyPacketPayloads {
    public enum Direction { C2S, S2C }
    public static final int C2S_LIMIT = 32767, S2C_LIMIT = 1048576;
    public record Channel(CustomPayload.Id<Payload> id, Direction direction) {}
    public record Payload(CustomPayload.Id<Payload> id, byte[] bytes) implements CustomPayload {
        public Payload { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        @Override public CustomPayload.Id<Payload> getId() { return id; }
    }
    private static final Map<Identifier, Channel> CHANNELS = new LinkedHashMap<>();
    static {
        add(Direction.C2S, "cauldron_teleport", "transformation_ability", "toggle_pressing_forward");
        add(Direction.S2C, "sync_contracts", "sync_demon_trades", "sync_poppet_shelf", "sync_horned_spear",
                "spawn_smoke_particles", "spawn_portal_particles", "spawn_explosion_particles", "spawn_specter_bangle_particles");
    }
    private static boolean registered;
    private LegacyPacketPayloads() {}
    private static void add(Direction direction, String... paths) {
        for (String path : paths) {
            Identifier id = Identifier.of("bewitchment", path);
            CHANNELS.put(id, new Channel(new CustomPayload.Id<>(id), direction));
        }
    }
    public static Map<Identifier, Channel> channels() { return Map.copyOf(CHANNELS); }
    public static Channel channel(Identifier id, Direction direction) {
        Channel channel = CHANNELS.get(id);
        if (channel == null || channel.direction != direction)
            throw new IllegalArgumentException("Unknown or wrong-direction Bewitchment channel: " + id);
        return channel;
    }
    public static synchronized void registerTypes() {
        if (registered) return;
        for (Channel channel : CHANNELS.values()) {
            var registry = channel.direction == Direction.C2S ? PayloadTypeRegistry.playC2S() : PayloadTypeRegistry.playS2C();
            registry.register(channel.id, codec(channel));
        }
        registered = true;
    }
    public static PacketCodec<RegistryByteBuf, Payload> codec(Channel channel) {
        int limit = channel.direction == Direction.C2S ? C2S_LIMIT : S2C_LIMIT;
        return new PacketCodec<>() {
            @Override public Payload decode(RegistryByteBuf buf) {
                checkLength(buf.readableBytes(), limit);
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new Payload(channel.id, bytes);
            }
            @Override public void encode(RegistryByteBuf buf, Payload payload) {
                if (!channel.id.equals(payload.id)) throw new IllegalArgumentException("Mismatched channel");
                checkLength(payload.bytes.length, limit);
                buf.writeBytes(payload.bytes);
            }
        };
    }
    private static void checkLength(int length, int limit) {
        if (length > limit) throw new IllegalArgumentException("Payload exceeds " + limit + " bytes");
    }
    public static Payload take(Identifier id, PacketByteBuf buf, Direction direction) {
        try {
            Channel channel = channel(id, direction);
            checkLength(buf.readableBytes(), direction == Direction.C2S ? C2S_LIMIT : S2C_LIMIT);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            return new Payload(channel.id, bytes);
        } finally {
            buf.release();
        }
    }
    public static RegistryByteBuf open(Payload payload, DynamicRegistryManager registries) {
        return new RegistryByteBuf(Unpooled.wrappedBuffer(payload.bytes()), registries);
    }
    public static ItemStack readStack(PacketByteBuf buf) {
        return ItemStack.OPTIONAL_PACKET_CODEC.decode(registryBuffer(buf));
    }
    public static PacketByteBuf writeStack(PacketByteBuf buf, ItemStack stack) {
        ItemStack.OPTIONAL_PACKET_CODEC.encode(registryBuffer(buf), stack);
        return buf;
    }
    private static RegistryByteBuf registryBuffer(PacketByteBuf buf) {
        if (!(buf instanceof RegistryByteBuf registry))
            throw new IllegalArgumentException("ItemStack packets require the connection's registries");
        return registry;
    }
    public static PacketByteBuf writeNbt(PacketByteBuf buf, NbtCompound nbt) {
        return buf.writeNbt(nbt);
    }
}
