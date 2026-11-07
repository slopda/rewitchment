package com.veeda.rewitchment.translator;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

public final class PacketTransportAdapter implements Opcodes {
    static final String PREFIX = "moriyashiine/bewitchment/";
    static final String CLIENT = "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking";
    static final String SERVER = "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking";
    static final String HELPER = "com/veeda/rewitchment/probe/";
    static final String BUF = "net/minecraft/class_2540";
    static final String ID = "Lnet/minecraft/class_2960;";
    static final String PLAYER = "Lnet/minecraft/class_3222;";
    static final String B = "L" + BUF + ";";
    static final Set<String> S2C = Set.of("SyncContractsPacket", "SyncDemonTradesPacket", "SyncPoppetShelfPacket",
            "SyncHornedSpearPacket", "SpawnSmokeParticlesPacket", "SpawnPortalParticlesPacket",
            "SpawnExplosionParticlesPacket", "SpawnSpecterBangleParticlesPacket");
    static final Set<String> C2S = Set.of("CauldronTeleportPacket", "TransformationAbilityPacket", "TogglePressingForwardPacket");
    public static final Map<String, Integer> EXPECTED_COUNTS = Map.of(
            "handler_interfaces", 11, "receiver_registrations", 11, "packet_sends", 11,
            "registry_buffer_allocations", 8, "item_stack_reads", 1, "item_stack_writes", 1, "nbt_writes", 2);
    public record Result(ClassNode node, Map<String, Integer> counts) {}
    private PacketTransportAdapter() {}
    static boolean packet(String owner) {
        return S2C.stream().anyMatch(n -> owner.equals(PREFIX + "client/packet/" + n) || owner.startsWith(PREFIX + "client/packet/" + n + "$"))
                || C2S.stream().anyMatch(n -> owner.equals(PREFIX + "common/packet/" + n) || owner.startsWith(PREFIX + "common/packet/" + n + "$"));
    }
    public static Result rewrite(ClassNode node) {
        var counts = new TreeMap<String, Integer>();
        boolean packet = packet(node.name);
        boolean entrypoint = node.name.equals(PREFIX + "common/Bewitchment") || node.name.equals(PREFIX + "client/BewitchmentClient");
        if (!packet && !entrypoint) return new Result(node, counts);
        for (String itf : node.interfaces) {
            if (itf.equals(CLIENT + "$PlayChannelHandler") || itf.equals(SERVER + "$PlayChannelHandler")) {
                if (!packet || !node.name.endsWith("$Receiver")) throw new IllegalStateException("Unexpected packet handler " + node.name);
                count(counts, "handler_interfaces");
            }
        }
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode call)) continue;
                if (call.owner.equals(CLIENT) || call.owner.equals(SERVER)) {
                    boolean client = call.owner.equals(CLIENT);
                    String helper = HELPER + (client ? "LegacyClientNetworking" : "LegacyServerNetworking");
                    String expected;
                    if (call.name.equals("registerGlobalReceiver")) {
                        if (!entrypoint) throw new IllegalStateException("Receiver registration outside audited entrypoint");
                        expected = "(" + ID + "L" + call.owner + "$PlayChannelHandler;)Z";
                        count(counts, "receiver_registrations");
                    } else if (call.name.equals("send")) {
                        if (!packet) throw new IllegalStateException("Send outside audited packets");
                        expected = "(" + (client ? "" : PLAYER) + ID + B + ")V";
                        count(counts, "packet_sends");
                    } else throw new IllegalStateException("Unaudited networking call " + call.name);
                    if (call.getOpcode() != INVOKESTATIC || !call.desc.equals(expected))
                        throw new IllegalStateException("Unexpected networking descriptor " + call.desc);
                    call.owner = helper;
                } else if (packet && call.owner.equals(BUF)) {
                    if (call.name.equals("<init>") && node.name.startsWith(PREFIX + "client/packet/")) {
                        if (!method.name.equals("send") || (method.access & ACC_STATIC) == 0 || !method.desc.startsWith("(" + PLAYER))
                            throw new IllegalStateException("Missing server player context");
                        var allocation = previous(call, 3);
                        var duplicate = previous(call, 2);
                        var buffer = previous(call, 1);
                        if (!call.desc.equals("(Lio/netty/buffer/ByteBuf;)V")
                                || !(allocation instanceof TypeInsnNode n) || n.getOpcode() != NEW || !n.desc.equals(BUF)
                                || duplicate.getOpcode() != DUP || !(buffer instanceof MethodInsnNode b)
                                || b.getOpcode() != INVOKESTATIC || !b.owner.equals("io/netty/buffer/Unpooled")
                                || !b.name.equals("buffer") || !b.desc.equals("()Lio/netty/buffer/ByteBuf;"))
                            throw new IllegalStateException("Unexpected packet buffer construction");
                        method.instructions.set(allocation, new VarInsnNode(ALOAD, 0));
                        method.instructions.remove(duplicate);
                        method.instructions.remove(buffer);
                        call.setOpcode(INVOKESTATIC);
                        call.owner = HELPER + "LegacyServerNetworking";
                        call.name = "buffer";
                        call.desc = "(" + PLAYER + ")" + B;
                        count(counts, "registry_buffer_allocations");
                    } else if (call.name.equals("method_10819") && call.desc.equals("()Lnet/minecraft/class_1799;")) {
                        if (!node.name.equals(PREFIX + "client/packet/SyncHornedSpearPacket$Receiver"))
                            throw new IllegalStateException("Unexpected packet stack reader");
                        adapt(call, "readStack", "(" + B + ")Lnet/minecraft/class_1799;");
                        count(counts, "item_stack_reads");
                    } else if (call.name.equals("method_10793") && call.desc.equals("(Lnet/minecraft/class_1799;)" + B)) {
                        if (!node.name.equals(PREFIX + "client/packet/SyncHornedSpearPacket"))
                            throw new IllegalStateException("Unexpected packet stack writer");
                        adapt(call, "writeStack", "(" + B + "Lnet/minecraft/class_1799;)" + B);
                        count(counts, "item_stack_writes");
                    } else if (call.name.equals("method_10794") && call.desc.equals("(Lnet/minecraft/class_2487;)" + B)) {
                        adapt(call, "writeNbt", "(" + B + "Lnet/minecraft/class_2487;)" + B);
                        count(counts, "nbt_writes");
                    }
                }
            }
        }
        ClassNode mapped = new ClassNode();
        node.accept(new ClassRemapper(mapped, new SimpleRemapper(Map.of(
                CLIENT + "$PlayChannelHandler", HELPER + "LegacyClientNetworking$Handler",
                SERVER + "$PlayChannelHandler", HELPER + "LegacyServerNetworking$Handler"))));
        return new Result(mapped, counts);
    }
    private static AbstractInsnNode previous(AbstractInsnNode n, int count) {
        while (count-- > 0) {
            if (n == null) throw new IllegalStateException("Incomplete packet buffer construction");
            do { n = n.getPrevious(); } while (n != null && n.getOpcode() < 0);
        }
        if (n == null) throw new IllegalStateException("Incomplete packet buffer construction");
        return n;
    }
    private static void adapt(MethodInsnNode call, String name, String desc) {
        call.setOpcode(INVOKESTATIC); call.itf = false;
        call.owner = HELPER + "LegacyPacketPayloads"; call.name = name; call.desc = desc;
    }
    private static void count(Map<String, Integer> counts, String key) { counts.merge(key, 1, Integer::sum); }
}
