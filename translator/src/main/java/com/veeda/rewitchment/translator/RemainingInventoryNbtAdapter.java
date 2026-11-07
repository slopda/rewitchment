package com.veeda.rewitchment.translator;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class RemainingInventoryNbtAdapter {
    public static final String TAGLOCK =
            "moriyashiine/bewitchment/common/block/entity/interfaces/TaglockHolder";
    public static final String TAGLOCK_BE =
            "moriyashiine/bewitchment/common/block/entity/TaglockHolderBlockEntity";
    public static final String JUNIPER =
            "moriyashiine/bewitchment/common/block/entity/JuniperChestBlockEntity";
    public static final String PACKET =
            "moriyashiine/bewitchment/client/packet/SyncPoppetShelfPacket";
    public static final String RECEIVER = PACKET + "$Receiver";
    private static final String MC_PLAYER = "net/minecraft/class_3222";
    private static final String MC_WORLD = "net/minecraft/class_1937";
    private static final String MC_HANDLER = "net/minecraft/class_634";
    private static final String MC_MANAGER = "net/minecraft/class_5455";
    private static final String NETWORK_MANAGER = "net/minecraft/class_5455$class_6890";
    private static final String OLD_RW = BlockEntitySerializationAdapter.OLD_RW;
    private static final String NEW_RW = BlockEntitySerializationAdapter.NEW_RW;

    public record Outcome(int taglockMethodDeclarations, int taglockInventoryCalls,
                          int taglockBlockEntityCalls, int packetInventoryCalls) {}

    private RemainingInventoryNbtAdapter() {}

    public static boolean usesExpandedFrames(String name) {
        return TAGLOCK.equals(name);
    }

    public static Outcome rewrite(ClassNode node) {
        int methods = 0, inventory = 0, beCalls = 0, packets = 0;
        if (TAGLOCK.equals(node.name)) {
            for (MethodNode method : node.methods) {
                if ((!method.name.equals("fromNbtTaglock")
                        && !method.name.equals("toNbtTaglock"))
                        || !OLD_RW.equals(method.desc)) continue;
                if ((method.access & Opcodes.ACC_STATIC) != 0) {
                    throw new IllegalStateException("Expected TaglockHolder default method");
                }

                for (AbstractInsnNode it = method.instructions.getFirst(); it != null;
                     it = it.getNext()) {
                    if (it instanceof VarInsnNode var && var.var >= 2) {
                        var.var++;
                    } else if (it instanceof IincInsnNode inc && inc.var >= 2) {
                        inc.var++;
                    } else if (it instanceof FrameNode frame) {
                        if (frame.type != Opcodes.F_NEW && frame.type != Opcodes.F_FULL) {
                            throw new IllegalStateException("TaglockHolder requires expanded frames");
                        }
                        if (frame.local == null || frame.local.size() < 2) {
                            throw new IllegalStateException("Incomplete TaglockHolder method frame");
                        }
                        frame.local.add(2, BlockEntitySerializationAdapter.REGISTRY);
                    }
                }
                if (method.localVariables != null) {
                    method.localVariables.forEach(v -> {
                        if (v.index >= 2) v.index++;
                    });
                }
                method.maxLocals++;
                method.maxStack++;
                for (AbstractInsnNode it = method.instructions.getFirst(); it != null;
                     it = it.getNext()) {
                    if (!(it instanceof MethodInsnNode call)) continue;
                    if (!BlockEntitySerializationAdapter.INVENTORIES.equals(call.owner)
                            || call.getOpcode() != Opcodes.INVOKESTATIC) continue;
                    boolean reading = "method_5429".equals(call.name)
                            && BlockEntitySerializationAdapter.OLD_INVENTORY_READ.equals(call.desc);
                    boolean writing = "method_5426".equals(call.name)
                            && BlockEntitySerializationAdapter.OLD_INVENTORY_WRITE.equals(call.desc);
                    if (!reading && !writing) continue;
                    method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 2));
                    call.desc = reading ? BlockEntitySerializationAdapter.NEW_INVENTORY_READ
                            : BlockEntitySerializationAdapter.NEW_INVENTORY_WRITE;
                    inventory++;
                }
                method.desc = NEW_RW;
                methods++;
            }
        }
        if (TAGLOCK_BE.equals(node.name) || JUNIPER.equals(node.name)) {
            for (MethodNode method : node.methods) {
                if (!(method.name.equals("method_11014") || method.name.equals("method_11007"))
                        || !NEW_RW.equals(method.desc)) continue;
                for (AbstractInsnNode it = method.instructions.getFirst(); it != null;
                     it = it.getNext()) {
                    if (!(it instanceof MethodInsnNode call)
                            || (!TAGLOCK.equals(call.owner) && !node.name.equals(call.owner))
                            || !OLD_RW.equals(call.desc)
                            || (call.getOpcode() != Opcodes.INVOKEINTERFACE
                                    && call.getOpcode() != Opcodes.INVOKEVIRTUAL)) continue;
                    if (!call.name.equals("fromNbtTaglock")
                            && !call.name.equals("toNbtTaglock")) continue;
                    method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 2));
                    call.desc = NEW_RW;
                    method.maxStack++;
                    beCalls++;
                }
            }
        }
        if (PACKET.equals(node.name) || RECEIVER.equals(node.name)) {
            for (MethodNode method : node.methods) {
                boolean send = PACKET.equals(node.name) && "send".equals(method.name)
                        && method.desc.equals("(Lnet/minecraft/class_3222;Lnet/minecraft/class_2338;)V");
                boolean receive = RECEIVER.equals(node.name) && "receive".equals(method.name)
                        && method.desc.startsWith("(Lnet/minecraft/class_310;Lnet/minecraft/class_634;");
                if (!send && !receive) continue;
                for (AbstractInsnNode it = method.instructions.getFirst(); it != null;
                     it = it.getNext()) {
                    if (!(it instanceof MethodInsnNode call)
                            || !BlockEntitySerializationAdapter.INVENTORIES.equals(call.owner)
                            || call.getOpcode() != Opcodes.INVOKESTATIC) continue;
                    boolean writing = send && "method_5426".equals(call.name)
                            && BlockEntitySerializationAdapter.OLD_INVENTORY_WRITE.equals(call.desc);
                    boolean reading = receive && "method_5429".equals(call.name)
                            && BlockEntitySerializationAdapter.OLD_INVENTORY_READ.equals(call.desc);
                    if (!writing && !reading) continue;
                    if (writing) {
                        method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
                        method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                                MC_PLAYER, "method_37908", "()L" + MC_WORLD + ";", false));
                        method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                                MC_WORLD, "method_30349", "()L" + MC_MANAGER + ";", false));
                    } else {
                        method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 2));
                        method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                                MC_HANDLER, "method_29091", "()L" + NETWORK_MANAGER + ";", false));
                    }
                    call.desc = reading ? BlockEntitySerializationAdapter.NEW_INVENTORY_READ
                            : BlockEntitySerializationAdapter.NEW_INVENTORY_WRITE;
                    method.maxStack++;
                    packets++;
                }
            }
        }
        return new Outcome(methods, inventory, beCalls, packets);
    }
}
