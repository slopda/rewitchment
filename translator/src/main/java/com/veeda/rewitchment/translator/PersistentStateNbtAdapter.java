package com.veeda.rewitchment.translator;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class PersistentStateNbtAdapter {
    private static final String PREFIX = "moriyashiine/bewitchment/common/world/";
    public static final String BW_WORLD = PREFIX + "BWWorldState";
    public static final String BW_UNIVERSAL = PREFIX + "BWUniversalWorldState";
    private static final String NBT = BlockEntitySerializationAdapter.NBT;
    private static final String REGISTRY = BlockEntitySerializationAdapter.REGISTRY;
    private static final String WRITER_OLD = "(L" + NBT + ";)L" + NBT + ";";
    private static final String WRITER_NEW = "(L" + NBT + ";L" + REGISTRY + ";)L" + NBT + ";";
    private static final String PERSISTENT = "net/minecraft/class_18";
    private static final String TYPE = "net/minecraft/class_18$class_8645";
    private static final String DATA_FIX_TYPE = "net/minecraft/class_4284";
    private static final String MANAGER = "net/minecraft/class_26";
    private static final String OLD_GET = "(Ljava/util/function/Function;Ljava/util/function/Supplier;"
            + "Ljava/lang/String;)L" + PERSISTENT + ";";
    private static final String NEW_GET = "(L" + TYPE + ";Ljava/lang/String;)L" + PERSISTENT + ";";
    private static final String STATE_TYPE_CONSTRUCTOR =
            "(Ljava/util/function/Supplier;Ljava/util/function/BiFunction;L"
                    + DATA_FIX_TYPE + ";)V";
    private static final String P_FOR_EACH = "net/minecraft/class_2499";
    private static final String INVENTORY_WRITER =
            "(L" + P_FOR_EACH + ";Ljava/lang/Long;Lnet/minecraft/class_2371;)V";
    private static final String INVENTORY_WRITER_NEW =
            "(L" + P_FOR_EACH + ";L" + REGISTRY + ";Ljava/lang/Long;Lnet/minecraft/class_2371;)V";

    public record Outcome(int readerMethods, int writerOverrides, int persistenceTypeRegistrations,
                          int readInventoryCalls, int capturedInventoryWriterCalls,
                          int registryAwareLambdas) {}

    private PersistentStateNbtAdapter() {}

    public static boolean usesExpandedFrames(String name) {
        return BW_WORLD.equals(name) || BW_UNIVERSAL.equals(name);
    }

    private static AbstractInsnNode previousOperation(AbstractInsnNode instruction) {
        AbstractInsnNode prev = instruction.getPrevious();
        while (prev != null && (prev.getOpcode() < 0)) prev = prev.getPrevious();
        return prev;
    }

    private static void insertRegistryLocal(MethodNode method, int position) {
        for (AbstractInsnNode it = method.instructions.getFirst(); it != null;
             it = it.getNext()) {
            if (it instanceof VarInsnNode var && var.var >= position) {
                var.var++;
            } else if (it instanceof IincInsnNode inc && inc.var >= position) {
                inc.var++;
            } else if (it instanceof FrameNode frame) {
                if (frame.type != Opcodes.F_NEW && frame.type != Opcodes.F_FULL) {
                    throw new IllegalStateException("Expected expanded PersistentState stack map");
                }
                if (frame.local == null || frame.local.size() < position) {
                    throw new IllegalStateException("PersistentState has incomplete frame locals");
                }
                frame.local.add(position, REGISTRY);
            }
        }
        if (method.localVariables != null) {
            method.localVariables.forEach(local -> {
                if (local.index >= position) local.index++;
            });
        }
        method.maxLocals++;
        method.maxStack += 3;
    }

    public static Outcome rewrite(ClassNode node) {
        if (!usesExpandedFrames(node.name)) return new Outcome(0, 0, 0, 0, 0, 0);
        final boolean hasInventory = BW_WORLD.equals(node.name);
        String readerOld = "(L" + NBT + ";)L" + node.name + ";";
        String readerNew = "(L" + NBT + ";L" + REGISTRY + ";)L" + node.name + ";";
        int readers = 0, writers = 0, registrations = 0, reads = 0, lambdaInventory = 0, lambdas = 0;

        for (MethodNode method : node.methods) {
            if (method.name.equals("readNbt") && method.desc.equals(readerOld)
                    && (method.access & Opcodes.ACC_STATIC) != 0) {
                insertRegistryLocal(method, 1);
                for (AbstractInsnNode it = method.instructions.getFirst(); it != null;
                     it = it.getNext()) {
                    if (!(it instanceof MethodInsnNode call)
                            || !hasInventory
                            || !BlockEntitySerializationAdapter.INVENTORIES.equals(call.owner)
                            || !"method_5429".equals(call.name)
                            || !BlockEntitySerializationAdapter.OLD_INVENTORY_READ.equals(call.desc)
                            || call.getOpcode() != Opcodes.INVOKESTATIC) continue;
                    method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 1));
                    call.desc = BlockEntitySerializationAdapter.NEW_INVENTORY_READ;
                    reads++;
                }
                method.desc = readerNew;
                readers++;
            }
            if ("method_75".equals(method.name) && WRITER_OLD.equals(method.desc)
                    && (method.access & Opcodes.ACC_STATIC) == 0) {
                insertRegistryLocal(method, 2);
                if (hasInventory) {
                    for (AbstractInsnNode it = method.instructions.getFirst(); it != null;
                         it = it.getNext()) {
                        if (!(it instanceof InvokeDynamicInsnNode dyn)
                                || !"accept".equals(dyn.name)
                                || !("(L" + P_FOR_EACH + ";)Ljava/util/function/BiConsumer;")
                                        .equals(dyn.desc)
                                || dyn.bsmArgs.length < 3
                                || !(dyn.bsmArgs[1] instanceof Handle implementation)
                                || !node.name.equals(implementation.getOwner())
                                || !"lambda$writeNbt$0".equals(implementation.getName())
                                || !INVENTORY_WRITER.equals(implementation.getDesc())) continue;
                        method.instructions.insertBefore(dyn, new VarInsnNode(Opcodes.ALOAD, 2));
                        dyn.desc = "(L" + P_FOR_EACH + ";L" + REGISTRY
                                + ";)Ljava/util/function/BiConsumer;";
                        dyn.bsmArgs[1] = new Handle(implementation.getTag(), implementation.getOwner(),
                                implementation.getName(), INVENTORY_WRITER_NEW,
                                implementation.isInterface());
                        lambdas++;
                    }
                }
                method.desc = WRITER_NEW;
                writers++;
            }
            if ("lambda$writeNbt$0".equals(method.name)
                    && INVENTORY_WRITER.equals(method.desc) && hasInventory) {
                insertRegistryLocal(method, 1);
                for (AbstractInsnNode it = method.instructions.getFirst(); it != null;
                     it = it.getNext()) {
                    if (!(it instanceof MethodInsnNode call)
                            || !BlockEntitySerializationAdapter.INVENTORIES.equals(call.owner)
                            || !"method_5426".equals(call.name)
                            || !BlockEntitySerializationAdapter.OLD_INVENTORY_WRITE.equals(call.desc)
                            || call.getOpcode() != Opcodes.INVOKESTATIC) continue;
                    method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 1));
                    call.desc = BlockEntitySerializationAdapter.NEW_INVENTORY_WRITE;
                    lambdaInventory++;
                }
                method.desc = INVENTORY_WRITER_NEW;
            }
            if ("get".equals(method.name)
                    && method.desc.equals("(Lnet/minecraft/class_1937;)L" + node.name + ";")
                    && (method.access & Opcodes.ACC_STATIC) != 0) {
                for (AbstractInsnNode it = method.instructions.getFirst(); it != null;
                     it = it.getNext()) {
                    if (!(it instanceof MethodInsnNode call)
                            || !MANAGER.equals(call.owner)
                            || !"method_17924".equals(call.name)
                            || !OLD_GET.equals(call.desc)) continue;
                    AbstractInsnNode supplierNode = previousOperation(previousOperation(call));

                    AbstractInsnNode idNode = previousOperation(call);
                    if (!(idNode instanceof org.objectweb.asm.tree.LdcInsnNode)
                            || !(supplierNode instanceof InvokeDynamicInsnNode supplier)
                            || !"get".equals(supplier.name)
                            || !"()Ljava/util/function/Supplier;".equals(supplier.desc)) {
                        throw new IllegalStateException("Unexpected Bewitchment PersistentState supplier");
                    }
                    AbstractInsnNode readerNode = previousOperation(supplier);
                    if (!(readerNode instanceof InvokeDynamicInsnNode read)
                            || !"apply".equals(read.name)
                            || !"()Ljava/util/function/Function;".equals(read.desc)
                            || read.bsmArgs.length < 3
                            || !(read.bsmArgs[1] instanceof Handle impl)
                            || !node.name.equals(impl.getOwner())
                            || !"readNbt".equals(impl.getName())
                            || !readerOld.equals(impl.getDesc())) {
                        throw new IllegalStateException("Unexpected original PersistentState NBT reader reference");
                    }
                    var readerArgs = read.bsmArgs.clone();
                    readerArgs[0] = Type.getMethodType(
                            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
                    readerArgs[1] = new Handle(impl.getTag(), impl.getOwner(),
                            impl.getName(), readerNew, impl.isInterface());
                    readerArgs[2] = Type.getMethodType(readerNew);
                    var replacement = new InsnList();
                    replacement.add(new TypeInsnNode(Opcodes.NEW, TYPE));
                    replacement.add(new InsnNode(Opcodes.DUP));
                    replacement.add(new InvokeDynamicInsnNode(supplier.name, supplier.desc,
                            supplier.bsm, supplier.bsmArgs.clone()));
                    replacement.add(new InvokeDynamicInsnNode(read.name,
                            "()Ljava/util/function/BiFunction;", read.bsm, readerArgs));

                    replacement.add(new FieldInsnNode(Opcodes.GETSTATIC,
                            DATA_FIX_TYPE, "field_45077", "L" + DATA_FIX_TYPE + ";"));
                    replacement.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TYPE,
                            "<init>", STATE_TYPE_CONSTRUCTOR, false));
                    method.instructions.insertBefore(read, replacement);
                    method.instructions.remove(read);
                    method.instructions.remove(supplier);
                    call.desc = NEW_GET;
                    method.maxStack += 3;
                    registrations++;
                }
            }
        }
        return new Outcome(readers, writers, registrations, reads, lambdaInventory, lambdas);
    }
}
