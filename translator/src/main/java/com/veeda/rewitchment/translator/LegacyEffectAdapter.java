package com.veeda.rewitchment.translator;

import java.util.LinkedHashMap;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class LegacyEffectAdapter {
    static final String EFFECT = "net/minecraft/class_1291";
    static final String INSTANCE = "net/minecraft/class_1293";
    static final String SHIM = "com/veeda/rewitchment/probe/LegacyStatusEffects";
    static final String OLD_GETTER = "()Lnet/minecraft/class_1291;";
    static final String NEW_GETTER = "(Lnet/minecraft/class_1293;)Lnet/minecraft/class_1291;";

    public record Outcome(int constructorsConverted, int constructorsRejected, int gettersConverted) {}

    private LegacyEffectAdapter() {}

    private static Map<String, IdentifierFactoryAdapter.Factory> constructorFactories() {
        Map<String, IdentifierFactoryAdapter.Factory> result = new LinkedHashMap<>();
        for (String suffix : new String[]{"I", "II", "IIZZ", "IIZZZ"}) {
            String old = "(L" + EFFECT + ";" + suffix + ")V";
            String modern = old.substring(0, old.length() - 1) + "L" + INSTANCE + ";";
            result.put(old, new IdentifierFactoryAdapter.Factory(SHIM, "create", modern));
        }
        return result;
    }

    public static Outcome rewrite(ClassNode classNode) {
        IdentifierFactoryAdapter.Outcome constructors =
                IdentifierFactoryAdapter.rewrite(classNode, INSTANCE, constructorFactories());
        int getters = 0;
        for (MethodNode method : classNode.methods) {
            for (var insn = method.instructions.getFirst(); insn != null;) {
                var next = insn.getNext();
                if (insn instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && INSTANCE.equals(call.owner)
                        && "method_5579".equals(call.name)
                        && OLD_GETTER.equals(call.desc)) {
                    method.instructions.set(call, new MethodInsnNode(
                            Opcodes.INVOKESTATIC, SHIM, "getEffectType", NEW_GETTER, false));
                    getters++;
                }
                insn = next;
            }
        }
        return new Outcome(constructors.converted(), constructors.rejected(), getters);
    }
}
