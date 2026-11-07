package com.veeda.rewitchment.translator;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class LegacyEntityStackAdapter {
    public static final String BROOM = "moriyashiine/bewitchment/api/entity/BroomEntity";
    public static final String SPEAR =
            "moriyashiine/bewitchment/common/entity/projectile/HornedSpearEntity";
    public static final String STACK = "net/minecraft/class_1799";
    public static final String NBT = "net/minecraft/class_2487";
    public static final String ENTITY = "net/minecraft/class_1297";
    public static final String HELPER = "com/veeda/rewitchment/probe/LegacyEntityStackPersistence";
    private static final String CUSTOM_NBT = "(L" + NBT + ";)V";
    public static final String OLD_READ = "(L" + NBT + ";)L" + STACK + ";";
    public static final String OLD_WRITE = "(L" + NBT + ";)L" + NBT + ";";
    public static final String NEW_READ = "(L" + NBT + ";L" + ENTITY + ";)L" + STACK + ";";
    public static final String NEW_WRITE =
            "(L" + STACK + ";L" + NBT + ";L" + ENTITY + ";)L" + NBT + ";";

    public record Outcome(int entityStackReads, int entityStackWrites) {}

    private LegacyEntityStackAdapter() {}

    public static Outcome rewrite(ClassNode node) {
        if (!BROOM.equals(node.name) && !SPEAR.equals(node.name)) {
            return new Outcome(0, 0);
        }
        int reads = 0, writes = 0;
        for (MethodNode method : node.methods) {
            boolean reading = "method_5749".equals(method.name) && CUSTOM_NBT.equals(method.desc);
            boolean writing = "method_5652".equals(method.name) && CUSTOM_NBT.equals(method.desc);
            if (!reading && !writing) continue;
            for (AbstractInsnNode it = method.instructions.getFirst(); it != null;
                 it = it.getNext()) {
                if (!(it instanceof MethodInsnNode call) || !STACK.equals(call.owner)) {
                    continue;
                }
                if (reading && call.getOpcode() == Opcodes.INVOKESTATIC
                        && "method_7915".equals(call.name) && OLD_READ.equals(call.desc)) {

                    method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = HELPER;
                    call.name = "readEntityStack";
                    call.desc = NEW_READ;
                    call.itf = false;
                    method.maxStack++;
                    reads++;
                } else if (writing && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && "method_7953".equals(call.name) && OLD_WRITE.equals(call.desc)) {

                    method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = HELPER;
                    call.name = "writeEntityStack";
                    call.desc = NEW_WRITE;
                    call.itf = false;
                    method.maxStack++;
                    writes++;
                }
            }
        }
        return new Outcome(reads, writes);
    }
}
