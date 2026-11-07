package com.veeda.rewitchment.translator;

import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public final class LegacyAttributeAndRandomAdapter {
    private static final String MODIFIER = "net/minecraft/class_1322";
    private static final String UUID_NAME_CTOR =
            "(Ljava/util/UUID;Ljava/lang/String;DLnet/minecraft/class_1322$class_1323;)V";
    private static final String STATIC_FACTORY =
            "(Ljava/util/UUID;Ljava/lang/String;DLnet/minecraft/class_1322$class_1323;)L"
                    + MODIFIER + ";";
    private static final String HELPER =
            "com/veeda/rewitchment/probe/LegacyAttributeModifierIds";
    private static final String ENTITY = "net/minecraft/class_1297";
    private static final String RANDOM = "()Lnet/minecraft/class_5819;";
    private static final Set<String> ORIGINAL_ATTR_OWNERS = Set.of(
            "moriyashiine/bewitchment/api/component/TransformationComponent",
            "moriyashiine/bewitchment/common/item/AthameItem",
            "moriyashiine/bewitchment/common/item/HornedSpearItem",
            "moriyashiine/bewitchment/common/item/ZephyrHarnessItem",
            "moriyashiine/bewitchment/mixin/familiar/PlayerEntityMixin"
    );
    private static final Set<String> OLD_RANDOM_OWNERS = Set.of(
            "net/minecraft/class_1309",  
            "net/minecraft/class_1308",  
            "net/minecraft/class_1657",  
            "net/minecraft/class_1646"   
    );

    public record Outcome(int uuidModifierConstructors, int inheritedRandomCalls) {}

    private LegacyAttributeAndRandomAdapter() {}

    public static Outcome rewrite(ClassNode cls) {
        if (!cls.name.startsWith("moriyashiine/bewitchment/")) {
            return new Outcome(0, 0);
        }
        int mods = 0, rng = 0;
        for (MethodNode method : cls.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                    insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode call)) continue;
                if (ORIGINAL_ATTR_OWNERS.contains(cls.name)
                        && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && MODIFIER.equals(call.owner)
                        && "<init>".equals(call.name)
                        && UUID_NAME_CTOR.equals(call.desc)) {
                    TypeInsnNode allocation = null;
                    for (AbstractInsnNode before = call.getPrevious(); before != null;
                            before = before.getPrevious()) {
                        if (before.getOpcode() == Opcodes.NEW
                                && before instanceof TypeInsnNode n
                                && MODIFIER.equals(n.desc)) {
                            allocation = n;
                            break;
                        }
                    }
                    if (allocation == null || allocation.getNext() == null
                            || allocation.getNext().getOpcode() != Opcodes.DUP) {
                        throw new IllegalStateException(
                                "Expected original new/dup attribute modifier pattern in " + cls.name);
                    }

                    method.instructions.remove(allocation.getNext());
                    method.instructions.remove(allocation);
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = HELPER;
                    call.name = "create";
                    call.desc = STATIC_FACTORY;
                    call.itf = false;
                    mods++;
                } else if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && OLD_RANDOM_OWNERS.contains(call.owner)
                        && "method_6051".equals(call.name)
                        && RANDOM.equals(call.desc)) {
                    call.owner = ENTITY;
                    call.name = "method_59922";
                    rng++;
                }
            }
        }
        return new Outcome(mods, rng);
    }
}
