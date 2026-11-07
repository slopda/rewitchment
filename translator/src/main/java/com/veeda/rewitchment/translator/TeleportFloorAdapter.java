package com.veeda.rewitchment.translator;

import java.util.ArrayList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

final class TeleportFloorAdapter implements Opcodes {
    static final String OWNER = "moriyashiine/bewitchment/common/misc/BWUtil";
    static final String DESCRIPTOR = "(Lnet/minecraft/class_1297;Lnet/minecraft/class_2338;IZ)V";
    static final String HELPER_DESCRIPTOR = "(Lnet/minecraft/class_2338;Lnet/minecraft/class_1297;)Z";

    private TeleportFloorAdapter() {}

    static int rewrite(ClassNode node) {
        if (!node.name.equals(OWNER)) return 0;
        var method = node.methods.stream().filter(m -> m.name.equals("attemptTeleport") && m.desc.equals(DESCRIPTOR))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing pinned teleport method"));
        var guards = new ArrayList<MethodInsnNode>();
        var bridges = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
            if (call.getOpcode() == INVOKEVIRTUAL && call.owner.equals("net/minecraft/class_2338$class_2339") &&
                    call.name.equals("method_10264") && call.desc.equals("()I") &&
                    GameplayCompletionAdapter.next(call) instanceof JumpInsnNode jump && jump.getOpcode() == IFLE)
                guards.add(call);
            if (call.owner.equals(GameplayCompletionAdapter.HELPER) && call.name.equals("aboveTeleportFloor"))
                bridges.add(call);
        }
        if (guards.isEmpty() && bridges.size() == 1) {
            var bridge = bridges.getFirst();
            if (bridge.getOpcode() == INVOKESTATIC && bridge.desc.equals(HELPER_DESCRIPTOR) &&
                    GameplayCompletionAdapter.previous(bridge) instanceof VarInsnNode entity && entity.getOpcode() == ALOAD && entity.var == 0 &&
                    GameplayCompletionAdapter.next(bridge) instanceof JumpInsnNode jump && jump.getOpcode() == IFEQ)
                return 0;
            throw new IllegalStateException("Unexpected teleport floor bridge");
        }
        if (guards.size() != 1 || !bridges.isEmpty())
            throw new IllegalStateException("Expected exactly one zero-height teleport guard");
        var guard = guards.getFirst();
        var jump = (JumpInsnNode) GameplayCompletionAdapter.next(guard);
        if (!(GameplayCompletionAdapter.previous(guard) instanceof VarInsnNode position) || position.getOpcode() != ALOAD || position.var != 5)
            throw new IllegalStateException("Unexpected teleport position local");

        method.instructions.insertBefore(guard, new VarInsnNode(ALOAD, 0));
        method.instructions.set(guard, new MethodInsnNode(INVOKESTATIC, GameplayCompletionAdapter.HELPER,
                "aboveTeleportFloor", HELPER_DESCRIPTOR, false));
        jump.setOpcode(IFEQ);
        return 1;
    }
}
